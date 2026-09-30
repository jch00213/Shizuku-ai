package com.jeremy.shizukuai

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class UiElementNode(
    val className: String,
    val resourceId: String,
    val text: String,
    val contentDescription: String,
    val bounds: String,
    val isClickable: Boolean,
    val children: List<UiElementNode> = emptyList()
)

object UiNodeParser {

    fun parseXmlDump(xmlString: String): List<UiElementNode> {
        if (xmlString.isBlank() || !xmlString.contains("<hierarchy")) return emptyList()

        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val input = ByteArrayInputStream(xmlString.toByteArray(Charsets.UTF_8))
            val doc = builder.parse(input)
            doc.documentElement.normalize()

            val rootNodes = mutableListOf<UiElementNode>()
            val nodeList = doc.documentElement.childNodes

            for (i in 0 until nodeList.length) {
                val node = nodeList.item(i)
                if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == "node") {
                    parseElement(node as Element)?.let { rootNodes.add(it) }
                }
            }
            rootNodes
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseElement(element: Element): UiElementNode? {
        val className = element.getAttribute("class").substringAfterLast('.')
        val resourceId = element.getAttribute("resource-id").substringAfterLast('/')
        val text = element.getAttribute("text")
        val desc = element.getAttribute("content-desc")
        val bounds = element.getAttribute("bounds")
        val isClickable = element.getAttribute("clickable") == "true"

        val children = mutableListOf<UiElementNode>()
        val childNodes = element.childNodes

        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == "node") {
                parseElement(child as Element)?.let { children.add(it) }
            }
        }

        // Filter out empty non-interactive container nodes to optimize AI token budget
        if (!isClickable && text.isBlank() && desc.isBlank() && children.isEmpty()) {
            return null
        }

        return UiElementNode(
            className = className,
            resourceId = resourceId,
            text = text,
            contentDescription = desc,
            bounds = bounds,
            isClickable = isClickable,
            children = children
        )
    }

    fun toCompactPrompt(nodes: List<UiElementNode>, depth: Int = 0): String {
        val sb = StringBuilder()
        val indent = "  ".repeat(depth)

        for (node in nodes) {
            val attributes = mutableListOf<String>()
            if (node.resourceId.isNotBlank()) attributes.add("id=\"${node.resourceId}\"")
            if (node.text.isNotBlank()) attributes.add("text=\"${node.text}\"")
            if (node.contentDescription.isNotBlank()) attributes.add("desc=\"${node.contentDescription}\"")
            if (node.isClickable) attributes.add("clickable=true")
            attributes.add("bounds=\"${node.bounds}\"")

            sb.append("$indent[${node.className}] ${attributes.joinToString(" ")}\n")
            if (node.children.isNotEmpty()) {
                sb.append(toCompactPrompt(node.children, depth + 1))
            }
        }
        return sb.toString()
    }
}
