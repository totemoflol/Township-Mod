package com.totemoflol.tottools

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.xml.sax.InputSource

internal data class EditableItem(
    val key: String,
    val label: String,
    val category: String,
    val value: String
)

internal object TownshipSaveEditor {
    private data class Definition(
        val key: String,
        val label: String,
        val category: String
    )

    private val definitions = listOf(
        Definition("money", "Coins", "Currency"),
        Definition("moneyCash", "Cash", "Currency"),
        Definition("gem1", "Gem 1", "Gems"),
        Definition("gem2", "Gem 2", "Gems"),
        Definition("gem3", "Gem 3", "Gems"),
        Definition("axe", "Axe", "Tools"),
        Definition("pick", "Pickaxe", "Tools"),
        Definition("nailMat", "Nails", "Materials"),
        Definition("paintRedMat", "Red Paint", "Materials"),
        Definition("hammerMat", "Hammers", "Materials"),
        Definition("Brick", "Bricks", "Materials"),
        Definition("Glass", "Glass", "Materials")
    )

    private val byKey = definitions.associateBy { it.key }

    fun findItems(xml: ByteArray): List<EditableItem> {
        val document = parse(xml)
        val global = findGlobal(document) ?: return emptyList()
        val values = mutableMapOf<String, String>()
        val nodes = global.childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node !is Element || node.tagName != "Var") continue
            val key = node.getAttribute("name")
            if (key !in byKey || values.containsKey(key)) continue
            val value = node.getAttribute("v")
            if (value.toLongOrNull() != null) values[key] = value
        }
        return definitions.mapNotNull { definition ->
            values[definition.key]?.let { value ->
                EditableItem(definition.key, definition.label, definition.category, value)
            }
        }
    }

    fun applyEdits(xml: ByteArray, edits: Map<String, String>): ByteArray {
        require(edits.keys.all { it in byKey }) { "One or more selected fields are not supported." }
        require(edits.isNotEmpty()) { "There are no changes to export." }

        val document = parse(xml)
        val global = findGlobal(document)
            ?: throw IllegalArgumentException("This save does not contain editable town values.")
        val updated = mutableSetOf<String>()
        val nodes = global.childNodes

        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node !is Element || node.tagName != "Var") continue
            val key = node.getAttribute("name")
            val newValue = edits[key] ?: continue
            val numeric = newValue.toLongOrNull()
                ?: throw IllegalArgumentException(byKey[key]?.label + " must be a whole number.")
            require(numeric in 0L..2_000_000_000L) {
                (byKey[key]?.label ?: key) + " must be between 0 and 2,000,000,000."
            }
            node.setAttribute("v", numeric.toString())
            updated += key
        }

        val missing = edits.keys - updated
        require(missing.isEmpty()) {
            "Could not find these fields in the selected save: " + missing.joinToString(", ")
        }

        val output = ByteArrayOutputStream()
        val transformerFactory = TransformerFactory.newInstance()
        try {
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        } catch (_: Exception) {
            // The parser remains hardened independently below.
        }
        val transformer = transformerFactory.newTransformer()
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        transformer.setOutputProperty(OutputKeys.INDENT, "no")
        transformer.transform(DOMSource(document), StreamResult(output))
        return output.toByteArray()
    }

    private fun findGlobal(document: Document): Element? {
        val root = document.documentElement ?: return null
        val children = root.childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node is Element && node.tagName == "Global") return node
        }
        return null
    }

    private fun parse(xml: ByteArray): Document {
        require(xml.isNotEmpty() && xml.size <= 64 * 1024 * 1024) {
            "Save XML is empty or exceeds the supported size."
        }
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isXIncludeAware = false
        factory.isExpandEntityReferences = false
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        val builder = factory.newDocumentBuilder()
        val input = InputSource(ByteArrayInputStream(xml))
        return builder.parse(input)
    }
}
