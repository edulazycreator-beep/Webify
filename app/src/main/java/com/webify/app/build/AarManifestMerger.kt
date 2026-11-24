package com.webify.app.build

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Manifest merger ringan untuk library AAR.
 * Menggabungkan <uses-permission>, <uses-feature>, <queries>, dan anak <application>
 * (activity, activity-alias, service, receiver, provider, meta-data, uses-library, profileable)
 * ke manifest utama. Placeholder ${applicationId} diganti package proyek, atribut tools:* dibuang,
 * elemen bertanda tools:node="remove" dilewati, dan elemen dengan android:name yang sama
 * tidak diduplikasi (milik aplikasi utama selalu menang).
 */
object AarManifestMerger {

    const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    const val TOOLS_NS = "http://schemas.android.com/tools"

    private val TOP_LEVEL = setOf("uses-permission", "uses-permission-sdk-23", "uses-feature", "permission")
    private val APP_CHILDREN = setOf("activity", "activity-alias", "service", "receiver", "provider", "meta-data", "uses-library", "profileable")

    data class Result(val xml: String, val added: Int, val permissions: List<String>)

    fun merge(mainManifest: String, libraryManifests: List<Pair<String, String>>, applicationId: String): Result {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val main = factory.newDocumentBuilder().parse(mainManifest.byteInputStream())
        val mainRoot = main.documentElement
        val app = firstChild(mainRoot, "application") ?: main.createElement("application").also { mainRoot.appendChild(it) }

        val existingTop = HashSet<String>()
        childElements(mainRoot).filter { it.tagName in TOP_LEVEL }.forEach { existingTop += it.tagName + "|" + androidName(it) }
        val existingApp = HashSet<String>()
        childElements(app).forEach { existingApp += it.tagName + "|" + androidName(it) }
        var queries: Element? = firstChild(mainRoot, "queries")
        val queryKeys = HashSet<String>()
        queries?.let { q -> childElements(q).forEach { queryKeys += serializeKey(it) } }

        var added = 0
        val newPerms = ArrayList<String>()
        for ((libName, raw) in libraryManifests) {
            val libDoc = try {
                factory.newDocumentBuilder().parse(raw.replace("\${applicationId}", applicationId).byteInputStream())
            } catch (e: Exception) {
                throw IllegalArgumentException("Manifest $libName tidak valid: ${e.message}", e)
            }
            val libRoot = libDoc.documentElement

            for (el in childElements(libRoot)) {
                when {
                    el.tagName in TOP_LEVEL -> {
                        if (isRemoved(el)) continue
                        val key = el.tagName + "|" + androidName(el)
                        if (existingTop.add(key)) {
                            insertBefore(mainRoot, importClean(main, el), app)
                            if (el.tagName.startsWith("uses-permission")) newPerms += androidName(el)
                            added++
                        }
                    }
                    el.tagName == "queries" -> {
                        if (queries == null) {
                            queries = main.createElement("queries")
                            insertBefore(mainRoot, queries!!, app)
                        }
                        for (q in childElements(el)) {
                            val k = serializeKey(q)
                            if (queryKeys.add(k)) { queries!!.appendChild(importClean(main, q)); added++ }
                        }
                    }
                }
            }

            val libApp = firstChild(libRoot, "application") ?: continue
            for (el in childElements(libApp)) {
                if (el.tagName !in APP_CHILDREN || isRemoved(el)) continue
                val key = el.tagName + "|" + androidName(el)
                if (existingApp.add(key)) {
                    app.appendChild(importClean(main, el))
                    added++
                }
            }
        }
        return Result(serialize(main), added, newPerms)
    }

    fun mergeFile(manifest: File, libs: List<ExtractedLibrary>, applicationId: String, log: BuildLogger) {
        val inputs = libs.mapNotNull { lib -> lib.manifest?.let { lib.name to it.readText() } }
        if (inputs.isEmpty()) return
        val r = merge(manifest.readText(), inputs, applicationId)
        manifest.writeText(r.xml)
        log.info("Manifest merger: ${inputs.size} manifest library, +${r.added} elemen" +
            if (r.permissions.isNotEmpty()) " (izin: ${r.permissions.joinToString { it.substringAfterLast('.') }})" else "")
    }

    // ------------------------------------------------------------------ helpers
    private fun isRemoved(e: Element): Boolean {
        val node = e.getAttributeNS(TOOLS_NS, "node")
        return node == "remove" || node == "removeAll"
    }

    private fun androidName(e: Element): String = e.getAttributeNS(ANDROID_NS, "name")

    /** Import ke dokumen utama sambil membuang atribut namespace tools secara rekursif. */
    private fun importClean(doc: Document, el: Element): Element {
        val imported = doc.importNode(el, true) as Element
        stripTools(imported)
        return imported
    }

    private fun stripTools(e: Element) {
        val attrs = e.attributes
        val toRemove = ArrayList<Node>()
        for (i in 0 until attrs.length) {
            val a = attrs.item(i)
            if (a.namespaceURI == TOOLS_NS || a.nodeName == "xmlns:tools") toRemove += a
        }
        toRemove.forEach { e.removeAttributeNode(it as org.w3c.dom.Attr) }
        val kids = e.childNodes
        val removeKids = ArrayList<Node>()
        for (i in 0 until kids.length) {
            val k = kids.item(i)
            if (k is Element) {
                if (isRemoved(k)) removeKids += k else stripTools(k)
            }
        }
        removeKids.forEach { e.removeChild(it) }
    }

    private fun serializeKey(e: Element): String {
        val sb = StringBuilder(e.tagName)
        val attrs = e.attributes
        for (i in 0 until attrs.length) sb.append('|').append(attrs.item(i).nodeName).append('=').append(attrs.item(i).nodeValue)
        childElements(e).forEach { sb.append('[').append(serializeKey(it)).append(']') }
        return sb.toString()
    }

    private fun insertBefore(parent: Element, node: Node, ref: Node) {
        parent.insertBefore(node, ref)
    }

    private fun firstChild(e: Element, name: String): Element? = childElements(e).firstOrNull { it.tagName == name }

    private fun childElements(e: Element): List<Element> {
        val out = ArrayList<Element>()
        val n = e.childNodes
        for (i in 0 until n.length) {
            val c = n.item(i)
            if (c is Element) out += c
        }
        return out
    }

    private fun serialize(doc: Document): String {
        val t = TransformerFactory.newInstance().newTransformer()
        t.setOutputProperty(OutputKeys.INDENT, "yes")
        t.setOutputProperty(OutputKeys.ENCODING, "utf-8")
        val w = StringWriter()
        t.transform(DOMSource(doc), StreamResult(w))
        return w.toString()
    }
}
