package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.example.notes.*
import com.example.ui.components.MarkdownText
import com.example.ui.components.NoteBodySkeleton
import com.example.ui.components.SafeMarkdownDocument
import com.example.ui.components.parseSafeMarkdown
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private data class PreparedNoteBlock(val text: String, val document: SafeMarkdownDocument?)

/** One serialized reader, bounded IO batches and parsed-block LRU; never a job per link. */
private class MarkdownReadSession(private val store: NoteBodyStore, private val id: String,
    private val hash: String, private val byteLimit: Int, private val lineLimit: Int) {
    val blocks = mutableStateListOf<MarkdownBlocks.Block>()
    var finished by mutableStateOf(false)
        private set
    private val mutex = Mutex()
    private var cursor: MarkdownBlocks.Cursor? = null
    private var pending: MarkdownBlocks.Block? = null
    private var closed = false
    private val decorated = HashSet<Long>()
    private var linkCount = 0
    private val cache = object : LinkedHashMap<Long, PreparedNoteBlock>(96, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, PreparedNoteBlock>?) = size > 96
    }
    private val documents = object : LinkedHashMap<String, SafeMarkdownDocument>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SafeMarkdownDocument>?) = size > 32
    }
    fun cached(block: MarkdownBlocks.Block): PreparedNoteBlock? = synchronized(cache) { cache[block.start] }
    private fun parsed(block: MarkdownBlocks.Block, text: String): PreparedNoteBlock {
        val document = if (block.markdown) documents[text] ?: parseSafeMarkdown(text).also { documents[text] = it } else null
        val allowed = document?.node != null && (document.links == 0 || block.start in decorated ||
            linkCount + document.links <= NoteSizePolicy.DECORATED_LINK_LIMIT)
        if (allowed && document != null && document.links > 0 && decorated.add(block.start)) linkCount += document.links
        return PreparedNoteBlock(text, document.takeIf { allowed }).also { synchronized(cache) { cache[block.start] = it } }
    }
    suspend fun append(prepare: Boolean = true) = mutex.withLock {
        if (finished || closed) return@withLock
        // Complete one bounded batch before observing cancellation: a consumed range must be published.
        withContext(NonCancellable) {
            var eof = false
            val batch = withContext(Dispatchers.IO) {
                val source = cursor ?: MarkdownBlocks.Cursor(store.open(id, hash), byteLimit, lineLimit).also { cursor = it }
                buildList<MarkdownBlocks.Block> {
                    while (size < 64) {
                        currentCoroutineContext().ensureActive()
                        val next = pending?.also { pending = null } ?: source.next()
                        if (next == null) { eof = true; break }
                        if (isNotEmpty() && next.end - first().start > NoteSizePolicy.IO_CHUNK_BYTES) { pending = next; break }
                        add(next)
                    }
                }
            }
            try {
                if (prepare && batch.isNotEmpty()) {
                    val first = batch.first().start
                    val bytes = withContext(Dispatchers.IO) { store.readRange(id, hash, first, (batch.last().end - first).toInt()) }
                    withContext(Dispatchers.Default) {
                        for (block in batch) {
                            currentCoroutineContext().ensureActive()
                            val from = (block.start - first).toInt()
                            val count = (block.end - block.start).toInt()
                            parsed(block, String(bytes, from, count, Charsets.UTF_8))
                        }
                    }
                }
            } finally {
                // The scanner has advanced. Publish even if prefetch/jump was cancelled so no ranges disappear.
                withContext(NonCancellable + Dispatchers.Main.immediate) { blocks.addAll(batch); finished = eof }
            }
        }
    }
    suspend fun prepare(block: MarkdownBlocks.Block): PreparedNoteBlock = mutex.withLock {
        cached(block)?.let { return@withLock it }
        check(!closed)
        val text = withContext(Dispatchers.IO) { store.readRange(id, hash, block.start, (block.end - block.start).toInt()).toString(Charsets.UTF_8) }
        withContext(Dispatchers.Default) { parsed(block, text) }
    }
    suspend fun close() = mutex.withLock {
        closed = true
        withContext(Dispatchers.IO) { cursor?.close() }
        synchronized(cache) { cache.clear() }; documents.clear()
    }
}

/** First content is prepared from a small prefix; indexing and parsing advance with the viewport. */
@Composable
fun LazyMarkdownNote(id: String, hash: String, bytes: Long, jumpByte: Long, modifier: Modifier,
    onBlockChange: ((Long, Long, String) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null) {
    val context = LocalContext.current
    val store = remember { PrivateMarkdownStore(context) }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier) {
        val lineLimit = (maxHeight.value / (24 * fontScale)).toInt().coerceIn(1, 16)
        val columns = (maxWidth.value / (16 * fontScale)).toInt().coerceAtLeast(1)
        val byteLimit = (columns * lineLimit * 2).coerceIn(16, 1024)
        val session = remember(id, hash, bytes, byteLimit, lineLimit) { MarkdownReadSession(store, id, hash, byteLimit, lineLimit) }
        var failure by remember(session) { mutableStateOf(false) }
        var jumping by remember(session) { mutableStateOf(false) }
        val state = rememberLazyListState()
        val headerItems = if (header == null) 0 else 1
        LaunchedEffect(session) {
            try { session.append(); awaitCancellation() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { failure = true }
            finally { withContext(NonCancellable) { runCatching { session.close() } } }
        }
        LaunchedEffect(session, state) {
            snapshotFlow {
                Triple(state.firstVisibleItemIndex, state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0, session.blocks.size)
            }.distinctUntilChanged().collect { (first, last, count) ->
                if (count == 0 || jumping || failure) return@collect
                try {
                    // Existing visible rows retain their prepared values while the next range warms.
                    for (index in (first - headerItems - 8).coerceAtLeast(0)..(last - headerItems + 24).coerceAtMost(count - 1)) {
                        session.prepare(session.blocks[index])
                    }
                    if (!session.finished && last - headerItems >= count - 32) session.append()
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { failure = true }
            }
        }
        LaunchedEffect(session, jumpByte) {
            if (jumpByte <= 0) return@LaunchedEffect
            jumping = true
            try {
                // A distant jump indexes offsets only; it does not parse skipped content.
                while (!session.finished && (session.blocks.lastOrNull()?.end ?: 0) <= jumpByte) session.append(prepare = false)
                val index = session.blocks.binarySearch { block -> when {
                    block.end <= jumpByte -> -1
                    block.start > jumpByte -> 1
                    else -> 0
                } }
                if (session.blocks.isNotEmpty()) {
                    val target = (if (index >= 0) index else -index - 1).coerceIn(0, session.blocks.lastIndex)
                    for (nearby in target..(target + 24).coerceAtMost(session.blocks.lastIndex)) session.prepare(session.blocks[nearby])
                    state.scrollToItem(target + headerItems)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { failure = true }
            finally { jumping = false }
        }
        LazyColumn(Modifier.fillMaxSize(), state = state) {
            if (header != null) item(key = "note_header") { header(); Spacer(Modifier.height(28.dp)) }
            items(session.blocks.size, key = { session.blocks[it].start }, contentType = { "note_block" }) { index ->
                val block = session.blocks[index]
                val ready by produceState(session.cached(block), session, block.start) {
                    if (value == null) try { value = session.prepare(block) }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { value = PreparedNoteBlock("This part could not be read. The saved note is retained.", null) }
                }
                val loaded = ready
                if (loaded == null) NoteBodySkeleton(lines = 2)
                else if (loaded.document != null) MarkdownText(loaded.text, prepared = loaded.document,
                    onMarkdownChange = onBlockChange?.let { change -> { updated -> change(block.start, block.end, updated) } })
                else SelectionContainer { Text(loaded.text, lineHeight = 24.sp) }
            }
            if (!session.finished && !failure) item(key = "note_loading") { NoteBodySkeleton() }
            if (failure) item(key = "note_error") { Text("This note could not be rendered. Use plain text or Export; its body is retained.") }
        }
    }
}
