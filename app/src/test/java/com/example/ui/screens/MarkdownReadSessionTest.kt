package com.example.ui.screens

import com.example.notes.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Node
import org.commonmark.node.StrongEmphasis
import org.commonmark.ext.gfm.tables.TableBlock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MarkdownReadSessionTest {
    private class Store(val bytes: ByteArray) : NoteBodyStore {
        var opens = 0
        var closed = 0
        var reads = 0
        override fun open(id: String, hash: String): InputStream = ByteArrayInputStream(bytes)
        override fun readRange(id: String, hash: String, offset: Long, count: Int): ByteArray =
            bytes.copyOfRange(offset.toInt(), minOf(bytes.size, offset.toInt() + count))
        override fun openRangeReader(id: String, hash: String): NoteRangeReader {
            opens++
            return object : NoteRangeReader {
                override fun read(offset: Long, count: Int): ByteArray { reads++; return readRange(id, hash, offset, count) }
                override fun close() { closed++ }
            }
        }
        override fun writeAtomic(id: String, input: InputStream, allowOversize: Boolean, progress: (Long) -> Unit): NoteBodyRef = error("Read only")
        override fun verify(id: String, hash: String, bytes: Long) = true
        override fun delete(id: String, hash: String) = Unit
        override fun size(id: String, hash: String): Long = bytes.size.toLong()
        override fun deleteAll(id: String) = Unit
    }
    private fun nodes(root: Node): List<Node> = buildList {
        val pending = ArrayDeque<Node>(); pending.add(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst(); add(node)
            var child = node.firstChild
            while (child != null) { pending.add(child); child = child.next }
        }
    }
    @Test fun exceedingTheLinkCapKeepsMarkdownFormattingAndUsesOneRangeHandle() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val body = (1..1000).joinToString("\n\n") { "**Bold $it** [Link $it](https://example.org/$it)" }
        val store = Store(body.toByteArray())
        val session = MarkdownReadSession(store, "note", "hash", 1024, 16)
        try {
            while (!session.finished) session.append(prepare = false)
            session.warm(0, session.blocks.lastIndex)
            val last = session.cached(session.blocks.last())!!
            assertNotNull(last.document!!.node)
            assertFalse(last.document.decorateLinks)
            assertTrue(nodes(last.document.node!!).any { it is StrongEmphasis })
            assertEquals(1, store.opens)
            assertTrue("Reads should be batched, not one per paragraph", store.reads < session.blocks.size)
            assertTrue(session.estimatedCacheBytes <= NoteSizePolicy.MARKDOWN_CACHE_BYTES)
        } finally { session.close(); Dispatchers.resetMain() }
        assertEquals(1, store.closed)
    }
    @Test fun longFencesRemainCodeAndDoNotDowngradeToRawMarkdown() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val body = "```kotlin\n" + "val item = **literal**\n".repeat(100) + "```"
        val store = Store(body.toByteArray())
        val session = MarkdownReadSession(store, "note", "hash", 128, 8)
        try {
            while (!session.finished) session.append(prepare = false)
            session.warm(0, session.blocks.lastIndex)
            for (block in session.blocks) {
                val prepared = session.cached(block)!!
                assertNotNull(prepared.document!!.node)
                assertTrue(nodes(prepared.document.node!!).any { it is FencedCodeBlock })
            }
            assertArrayEquals(store.bytes, session.blocks.flatMap { store.bytes.copyOfRange(it.start.toInt(), it.end.toInt()).toList() }.toByteArray())
        } finally { session.close(); Dispatchers.resetMain() }
    }
    @Test fun crAndCrLfFenceContinuationsPreserveEverySourceByte() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            for (ending in listOf("\r", "\r\n", "\n")) {
                val body = "```text" + ending + ("a😀b" + ending).repeat(100) + "```"
                val store = Store(body.toByteArray())
                val session = MarkdownReadSession(store, "note", "hash", 128, 8)
                try {
                    while (!session.finished) session.append(prepare = false)
                    session.warm(0, session.blocks.lastIndex)
                    assertArrayEquals(store.bytes, session.blocks.flatMap {
                        store.bytes.copyOfRange(it.start.toInt(), it.end.toInt()).toList()
                    }.toByteArray())
                    assertTrue(session.blocks.all { block -> nodes(session.cached(block)!!.document!!.node!!).any { it is FencedCodeBlock } })
                } finally { session.close() }
            }
        } finally { Dispatchers.resetMain() }
    }
    @Test fun unicodeTableContinuationHeadersRemainTables() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val body = "| 名称 | Value |\n| --- | --- |\n" + (1..200).joinToString("\n") { "| item$it | text |" }
        val store = Store(body.toByteArray())
        val session = MarkdownReadSession(store, "note", "hash", 256, 8)
        try {
            while (!session.finished) session.append(prepare = false)
            session.warm(0, session.blocks.lastIndex)
            val continuations = session.blocks.filter { it.tableHeader != null }
            assertTrue(continuations.isNotEmpty())
            for (block in continuations) {
                assertTrue(block.tableHeader!!.contains("名称"))
                assertTrue(nodes(session.cached(block)!!.document!!.node!!).any { it is TableBlock })
            }
        } finally { session.close(); Dispatchers.resetMain() }
    }
}
