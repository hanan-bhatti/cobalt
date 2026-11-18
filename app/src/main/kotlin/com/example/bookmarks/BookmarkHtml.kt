package com.example.bookmarks

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.DocumentType
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

/** Browser interchange only: this format does not contain a complete Cobalt backup. */
object BookmarkHtml {
    const val MAX_BYTES = 10 * 1024 * 1024
    const val MAX_LINKS = 50_000
    data class Entry(val url: String, val title: String, val path: List<String>, val added: Long?, val description: String? = null)
    data class Parsed(val entries: List<Entry>, val folders: List<List<String>>, val skipped: Int)

    /** Only space + slash + space denotes hierarchy. Other slashes are literal label text. */
    fun exportPath(folder: String): List<String> {
        val parts = folder.split(" / ")
        if (parts.size == 1) return listOf(folder)
        // Keep malformed/overly deep paths intact rather than discarding names or inventing folders.
        return if (parts.size <= 64 && parts.all { it.isNotBlank() }) parts.map { it.trim() }
            else listOf(folder)
    }

    private class FolderNode {
        val children = linkedMapOf<String, FolderNode>()
        val bookmarks = mutableListOf<Entry>()
    }

    fun key(url: String): String? {
        if (url.length > 16_384) return null
        val parsed = url.trim().toHttpUrlOrNull() ?: return null
        return parsed.toString()
    }

    fun read(input: InputStream, checkCancelled: () -> Unit): Parsed {
        // Bound input before building a tolerant HTML tree; no renderer, scripts or network access.
        val bytes = input.use { source ->
            val buffer = java.io.ByteArrayOutputStream()
            val block = ByteArray(8192)
            while (true) {
                checkCancelled()
                val count = source.read(block)
                if (count < 0) break
                require(buffer.size() + count <= MAX_BYTES) { "Choose a bookmark file smaller than 10 MB." }
                buffer.write(block, 0, count)
            }
            buffer.toByteArray()
        }
        val document = Jsoup.parse(bytes.inputStream(), null, "")
        val bookmarkDoctype = document.childNodes().filterIsInstance<DocumentType>()
            .any { it.name().equals("NETSCAPE-Bookmark-file-1", ignoreCase = true) }
        require(document.selectFirst("dl") != null && (bookmarkDoctype || document.selectFirst("dt a[href], dt h3") != null)) { "This file isn't a browser bookmark export. Choose a bookmarks HTML file." }
        val entries = mutableListOf<Entry>()
        val folders = linkedSetOf<List<String>>()
        val stack = mutableListOf<List<String>>()
        var pending: String? = null
        var skipped = 0
        var anchors = 0
        var nodes = 0
        var lastEntry = -1
        NodeTraversor.traverse(object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                checkCancelled()
                require(++nodes <= 400_000 && depth <= 256) { "This bookmark file is too complex. Export a smaller selection and try again." }
                val element = node as? Element ?: return
                when (element.normalName()) {
                    "h3" -> { pending = element.text().trim().take(200).ifBlank { "Untitled folder" }; lastEntry = -1 }
                    "dl" -> {
                        val path = stack.lastOrNull().orEmpty() + listOfNotNull(pending)
                        require(path.size <= 64) { "This file has too many nested folders." }
                        stack.add(path)
                        if (path.isNotEmpty()) folders.add(path)
                        pending = null
                    }
                    "a" -> {
                        require(++anchors <= MAX_LINKS) { "Choose a file with no more than 50,000 bookmarks." }
                        val url = element.attr("href").trim()
                        if (stack.isEmpty() || key(url) == null) { skipped++; lastEntry = -1; return }
                        val added = element.attr("add_date").toLongOrNull()?.takeIf { it in 1..(System.currentTimeMillis() / 1000 + 86400) }?.times(1000)
                        entries.add(Entry(url, element.text().trim().take(1000).ifBlank { url }, stack.last(), added))
                        lastEntry = entries.lastIndex
                    }
                    "dd" -> if (lastEntry >= 0) {
                        val text = element.ownText().trim().take(4000).ifBlank { null }
                        entries[lastEntry] = entries[lastEntry].copy(description = text)
                        lastEntry = -1
                    }
                }
            }
            override fun tail(node: Node, depth: Int) {
                if (node is Element && node.normalName() == "dl") {
                    if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                    pending = null
                    lastEntry = -1
                }
            }
        }, document)
        return Parsed(entries, folders.toList(), skipped)
    }

    fun write(output: OutputStream, entries: List<Entry>, checkCancelled: () -> Unit) {
        fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            fun line(text: String) { checkCancelled(); writer.write(text); writer.write("\n") }
            line("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
            line("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
            line("<TITLE>Cobalt bookmarks</TITLE><H1>Cobalt bookmarks</H1>")
            line("<DL><p>")
            fun bookmark(entry: Entry) {
                val date = entry.added?.takeIf { it > 0 }?.let { " ADD_DATE=\"${Instant.ofEpochMilli(it).epochSecond}\"" }.orEmpty()
                line("<DT><A HREF=\"${escape(entry.url)}\"$date>${escape(entry.title)}</A>")
                entry.description?.takeIf { it.isNotBlank() }?.let { line("<DD>${escape(it)}") }
            }
            val root = FolderNode()
            entries.forEach { entry ->
                checkCancelled()
                var destination = root
                entry.path.forEach { name -> destination = destination.children.getOrPut(name) { FolderNode() } }
                destination.bookmarks.add(entry)
            }
            fun writeFolder(node: FolderNode) {
                node.bookmarks.forEach(::bookmark)
                node.children.forEach { (name, child) ->
                    line("<DT><H3>${escape(name)}</H3>")
                    line("<DL><p>")
                    writeFolder(child)
                    line("</DL><p>")
                }
            }
            writeFolder(root)
            line("</DL><p>")
        }
    }
}
