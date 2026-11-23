package com.totemoflol.tottools

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

internal data class DecodeReport(
    val sourceName: String, val sourceEntry: String?, val originalBytes: Int,
    val decodedXml: ByteArray, val rootName: String, val directChildren: Int,
    val roundTripVerified: Boolean, val hashLength: Long?, val seed: Long?,
    val capacity: Int?, val transformedBytes: Int, val ignoredPaddingBytes: Int,
    val usedLz4: Boolean, val notes: List<String>
)

internal object TownshipDecoder {
    private const val MASK = 0xFFFF_FFFFL
    private const val M = 0x5BD1E995L
    private const val TABLE_SIZE = 0x2D7
    private const val HEADER_XOR = 0x396A8L
    private const val CAPACITY_XOR = 0xC5EEDL
    private val LZ4_MAGIC = byteArrayOf(0x04, 0x22, 0x4D, 0x18)

    fun decode(input: ByteArray, name: String): DecodeReport {
        require(input.isNotEmpty()) { "Selected file is empty." }
        require(input.size <= 64 * 1024 * 1024) { "File exceeds 64 MB limit." }
        val unwrapped = unwrapZip(input, name)
        val data = unwrapped.first
        if (data.size >= 8 && (data[0].toInt() and 0xFF) == 0x79) {
            return decode079(data, name, unwrapped.second, input.size)
        }
        require(looksLikeXml(data)) { "Expected a Township 0x79 save or a decoded XML file." }
        val parsed = parseXml(data)
        return DecodeReport(name, unwrapped.second, input.size, data, parsed.first, parsed.second,
            false, null, null, null, data.size, 0, false,
            listOf("Input was already XML; 0x79 decoding was not needed."))
    }

    private fun unwrapZip(input: ByteArray, name: String): Pair<ByteArray, String?> {
        if (input.size < 4 || input[0] != 0x50.toByte() || input[1] != 0x4B.toByte()) return input to null
        val entries = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.lowercase().endsWith(".xml")) {
                    val bytes = zip.readBytes()
                    require(bytes.size <= 64 * 1024 * 1024) { "ZIP XML entry exceeds 64 MB." }
                    entries += entry.name to bytes
                }
                zip.closeEntry()
            }
        }
        require(entries.isNotEmpty()) { "No XML entry found inside ZIP." }
        val expected = name.substringBefore(".txt.zip").substringBeforeLast(".zip")
            .substringAfterLast('/').substringAfterLast('\\')
        val selected = entries.firstOrNull { it.first.substringAfterLast('/').equals(expected + ".xml", true) }
            ?: entries.singleOrNull()
            ?: throw IllegalArgumentException("ZIP has multiple XML entries; select a ZIP with one XML file.")
        return selected.second to selected.first
    }

    private fun decode079(data: ByteArray, name: String, entry: String?, originalSize: Int): DecodeReport {
        val marker = data[0].toInt() and 0xFF
        require((marker xor 0x3C) == 0x45) { "Unsupported type marker: 0x" + marker.toString(16) }
        val hashLength = u24(data, 1)
        val seed = u32(data, 4)
        val capacity = data.size - 8
        val available = data.size - 8
        val rhs = ((capacity.toLong() + 8L) xor CAPACITY_XOR) and MASK
        val size = (((hashLength - rhs) and MASK) xor HEADER_XOR) and MASK
        require(size <= available.toLong() && size <= 64L * 1024 * 1024) {
            "Header implies " + size + " bytes but payload has " + available + "; capacity may differ."
        }
        val table = hashTable(hashLength, (seed + 4L) and MASK)
        val transformed = data.copyOfRange(8, 8 + size.toInt())
        for (i in transformed.indices) {
            if (i > 0) transformed[i] =
                ((transformed[i].toInt() and 255) - (transformed[i - 1].toInt() and 255)).toByte()
            transformed[i] = (transformed[i].toInt() xor (table[i % TABLE_SIZE].toInt() and 255)).toByte()
        }

        val regenerated = encode079(transformed, seed, capacity)
        require(regenerated.contentEquals(data.copyOfRange(0, 8 + transformed.size))) {
            "0x79 round-trip verification failed; refusing decoded output."
        }
        val padding = data.copyOfRange(8 + transformed.size, data.size)
        require(padding.all { it == 0.toByte() }) { "Unused tail has non-zero bytes; refusing to discard it." }

        val notes = mutableListOf("0x79 transform passed exact byte-for-byte round-trip verification.")
        var final = transformed
        var lz4 = false
        if (transformed.size >= 8 && transformed.copyOfRange(0, 4).contentEquals(LZ4_MAGIC)) {
            val expectedSize = u32(transformed, 4)
            require(expectedSize <= 64L * 1024 * 1024) { "LZ4 output exceeds 64 MB." }
            final = decompressLz4(transformed.copyOfRange(8, transformed.size), expectedSize.toInt())
            lz4 = true
            notes += "LZ4 raw block decompressed (" + expectedSize + " bytes)."
        }

        var parsed = tryParseXml(final)
        if (parsed == null) {
            var end = final.size
            while (end > 0 && final[end - 1] == 0.toByte()) end--
            if (end < final.size) {
                val candidate = final.copyOf(end)
                parsed = tryParseXml(candidate)
                if (parsed != null) {
                    val removed = final.size - end
                    final = candidate
                    notes += "Removed " + removed + " terminal NUL byte(s) after XML validation."
                }
            }
        }
        require(parsed != null) { "Decoded data did not pass XML parsing." }
        notes += "XML validation passed: root <" + parsed.first + ">; direct children: " + parsed.second + "."
        return DecodeReport(name, entry, originalSize, final, parsed.first, parsed.second, true,
            hashLength, seed, capacity, transformed.size, padding.size, lz4, notes)
    }

    private fun hashTable(hashLength: Long, initialSeed: Long): ByteArray {
        var value = initialSeed and MASK
        val table = ByteArray(TABLE_SIZE)
        var offset = 0
        while (offset < table.size) {
            value = murmur(le32(value), hashLength)
            for (b in le32(value)) if (offset < table.size) table[offset++] = b
        }
        return table
    }

    private fun murmur(data: ByteArray, seed: Long): Long {
        var h = (seed xor data.size.toLong()) and MASK
        var pos = 0
        var remaining = data.size
        while (remaining >= 4) {
            var k = u32(data, pos)
            k = (k * M) and MASK
            k = k xor (k ushr 24)
            k = (k * M) and MASK
            h = (h * M) and MASK
            h = h xor k
            pos += 4
            remaining -= 4
        }
        when (remaining) {
            3 -> {
                h = h xor ((data[pos + 2].toInt() and 255).toLong() shl 16)
                h = h xor ((data[pos + 1].toInt() and 255).toLong() shl 8)
                h = h xor (data[pos].toInt() and 255).toLong()
                h = (h * M) and MASK
            }
            2 -> {
                h = h xor ((data[pos + 1].toInt() and 255).toLong() shl 8)
                h = h xor (data[pos].toInt() and 255).toLong()
                h = (h * M) and MASK
            }
            1 -> {
                h = h xor (data[pos].toInt() and 255).toLong()
                h = (h * M) and MASK
            }
        }
        h = h xor (h ushr 13)
        h = (h * M) and MASK
        h = h xor (h ushr 15)
        return h and MASK
    }

    private fun encode079(plain: ByteArray, seed: Long, capacity: Int): ByteArray {
        val h = ((plain.size.toLong() xor HEADER_XOR) +
            (((capacity.toLong() + 8L) xor CAPACITY_XOR) and MASK)) and MASK
        require(h <= 0xFFFFFFL) { "hash_length does not fit 24-bit header." }
        val table = hashTable(h, (seed + 4L) and MASK)
        val out = ByteArray(8 + plain.size)
        out[0] = 0x79
        out[1] = h.toByte()
        out[2] = (h ushr 8).toByte()
        out[3] = (h ushr 16).toByte()
        le32(seed).copyInto(out, 4)
        var previous = 0
        for (i in plain.indices) {
            val current = plain[i].toInt() and 255
            val transformed = current xor (table[i % TABLE_SIZE].toInt() and 255)
            out[8 + i] = ((transformed + previous) and 255).toByte()
            previous = current
        }
        return out
    }

    private fun decompressLz4(block: ByteArray, expectedSize: Int): ByteArray {
        val output = ByteArray(expectedSize)
        var src = 0
        var dst = 0
        while (src < block.size) {
            val token = block[src++].toInt() and 255
            var literals = token ushr 4
            if (literals == 15) {
                while (true) {
                    require(src < block.size) { "Truncated LZ4 literal extension." }
                    val extra = block[src++].toInt() and 255
                    literals += extra
                    if (extra != 255) break
                }
            }
            require(src + literals <= block.size && dst + literals <= expectedSize) { "LZ4 literal run exceeds bounds." }
            block.copyInto(output, dst, src, src + literals)
            src += literals
            dst += literals
            if (src == block.size) break
            require(src + 2 <= block.size) { "Truncated LZ4 match offset." }
            val offset = (block[src].toInt() and 255) or ((block[src + 1].toInt() and 255) shl 8)
            src += 2
            require(offset > 0 && offset <= dst) { "Invalid LZ4 match offset: " + offset }
            var match = token and 15
            if (match == 15) {
                while (true) {
                    require(src < block.size) { "Truncated LZ4 match extension." }
                    val extra = block[src++].toInt() and 255
                    match += extra
                    if (extra != 255) break
                }
            }
            match += 4
            require(dst + match <= expectedSize) { "LZ4 match exceeds output bounds." }
            repeat(match) {
                output[dst] = output[dst - offset]
                dst++
            }
        }
        require(dst == expectedSize) { "LZ4 size mismatch: expected " + expectedSize + ", got " + dst }
        return output
    }

    private fun parseXml(xml: ByteArray): Pair<String, Int> =
        tryParseXml(xml) ?: throw IllegalArgumentException("XML parsing failed.")

    private fun tryParseXml(xml: ByteArray): Pair<String, Int>? = try {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(xml), null)
        var root = ""
        var children = 0
        var depth = 0
        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (depth == 1) root = parser.name else if (depth == 2) children++
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        if (root.isBlank() || depth != 0) null else root to children
    } catch (_: Exception) { null }

    private fun looksLikeXml(data: ByteArray): Boolean {
        var i = 0
        if (data.size >= 3 && data[0] == 0xEF.toByte() && data[1] == 0xBB.toByte() && data[2] == 0xBF.toByte()) i = 3
        while (i < data.size && data[i].toInt().toChar().isWhitespace()) i++
        return i < data.size && data[i] == '<'.code.toByte()
    }

    private fun u24(b: ByteArray, i: Int): Long =
        (b[i].toInt() and 255).toLong() or
        (((b[i + 1].toInt() and 255).toLong()) shl 8) or
        (((b[i + 2].toInt() and 255).toLong()) shl 16)

    private fun u32(b: ByteArray, i: Int): Long =
        (b[i].toInt() and 255).toLong() or
        (((b[i + 1].toInt() and 255).toLong()) shl 8) or
        (((b[i + 2].toInt() and 255).toLong()) shl 16) or
        (((b[i + 3].toInt() and 255).toLong()) shl 24)

    private fun le32(v: Long): ByteArray = byteArrayOf(
        v.toByte(), (v ushr 8).toByte(), (v ushr 16).toByte(), (v ushr 24).toByte()
    )
}
