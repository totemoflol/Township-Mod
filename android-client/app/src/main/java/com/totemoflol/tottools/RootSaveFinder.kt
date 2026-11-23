package com.totemoflol.tottools

import android.util.Base64

internal object RootSaveFinder {
    fun find(): Pair<String, ByteArray> {
        val scan = "find /data/data/com.playrix.township /data/user/0/com.playrix.township -type f \\( -iname '*mGameInfo*' -o -iname '*LocalInfo*' \\) 2>/dev/null | head -20"
        val paths = runRoot(scan).lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        val selected = paths.firstOrNull { it.contains("mGameInfo", true) }
            ?: paths.firstOrNull { it.contains("LocalInfo", true) }
            ?: throw IllegalStateException("No Township save found. Check root access and launch Township first.")
        val encoded = runRoot("base64 '$selected'").filter { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
        val bytes = try { Base64.decode(encoded, Base64.DEFAULT) }
            catch (_: Exception) { throw IllegalStateException("Found a save file but could not read it.") }
        if (bytes.isEmpty()) throw IllegalStateException("The detected save file was empty.")
        return selected to bytes
    }

    private fun runRoot(command: String): String {
        val process = try { ProcessBuilder("su", "-c", command).redirectErrorStream(true).start() }
            catch (_: Exception) { throw IllegalStateException("Root access is required for automatic detection.") }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (process.waitFor() != 0) throw IllegalStateException("Root access was denied or Township's save folder could not be read.")
        return output
    }
}
