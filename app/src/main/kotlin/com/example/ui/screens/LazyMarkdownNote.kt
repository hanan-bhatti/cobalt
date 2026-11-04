package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notes.*
import com.example.ui.components.MarkdownText
import com.example.ui.components.NoteBodySkeleton
import com.example.ui.components.SafeMarkdownDocument
import com.example.ui.components.SearchHighlight
import com.example.ui.components.parseSafeMarkdown
import com.example.ui.components.withSearchHighlight
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class PreparedNoteBlock(val text: String, val document: SafeMarkdownDocument?)

/** One serialized reader, bounded IO batches and parsed-block LRU; never a job per link. */
internal class MarkdownReadSession(private val store: NoteBodyStore, private val id: String,
    private val hash: String, private val byteLimit: Int, private val lineLimit: Int) {
    val blocks = mutableStateListOf<MarkdownBlocks.Block>()
    var finished by mutableStateOf(false)
        private set
    private val mutex = Mutex()
    private var cursor: MarkdownBlocks.Cursor? = null
    private var reader: NoteRangeReader? = null
    private var pending: MarkdownBlocks.Block? = null
    private var closed = false
    private val decorated = HashSet<Long>()
    private var linkCount = 0
    private var cacheBytes = 0L
    private data class Cached(val value: PreparedNoteBlock, val bytes: Long)
    private val cache = LinkedHashMap<Long, Cached>(256, 0.75f, true)
    private val documents = object : LinkedHashMap<String, SafeMarkdownDocument>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SafeMarkdownDocument>?) = size > 64
    }
    val estimatedCacheBytes: Long get() = synchronized(cache) { cacheBytes }
    fun cached(block: MarkdownBlocks.Block): PreparedNoteBlock? = synchronized(cache) { cache[block.start]?.value }
    private fun parsed(block: MarkdownBlocks.Block, text: String): PreparedNoteBlock {
        val source = block.renderSource(text)
        val document = documents[source] ?: parseSafeMarkdown(source).also { documents[source] = it }
        val decorate = document.links == 0 || block.start in decorated ||
            linkCount + document.links <= NoteSizePolicy.DECORATED_LINK_LIMIT
        if (decorate && document.links > 0 && decorated.add(block.start)) linkCount += document.links
        val value = PreparedNoteBlock(text, document.copy(decorateLinks = decorate))
        val cost = 128L + text.length * 2L + source.length * 2L + document.nodeCount * 96L
        synchronized(cache) {
            cache.remove(block.start)?.let { cacheBytes -= it.bytes }
            cache[block.start] = Cached(value, cost); cacheBytes += cost
            while (cache.size > 1 && (cacheBytes > NoteSizePolicy.MARKDOWN_CACHE_BYTES || cache.size > NoteSizePolicy.MARKDOWN_CACHE_BLOCKS)) {
                val oldest = cache.entries.iterator().next()
                cacheBytes -= oldest.value.bytes; cache.remove(oldest.key)
            }
        }
        return value
    }
    private suspend fun range(start: Long, count: Int): ByteArray = withContext(Dispatchers.IO) {
        val input = reader ?: store.openRangeReader(id, hash).also { reader = it }
        input.read(start, count)
    }
    private suspend fun prepareBatch(batch: List<MarkdownBlocks.Block>) {
        var index = 0
        while (index < batch.size) {
            currentCoroutineContext().ensureActive()
            val first = index
            while (index + 1 < batch.size && batch[index + 1].end - batch[first].start <= NoteSizePolicy.IO_CHUNK_BYTES) index++
            val selected = batch.subList(first, index + 1)
            val start = selected.first().start
            val bytes = range(start, (selected.last().end - start).toInt())
            withContext(Dispatchers.Default) {
                for (block in selected) {
                    currentCoroutineContext().ensureActive()
                    parsed(block, String(bytes, (block.start - start).toInt(), (block.end - block.start).toInt(), Charsets.UTF_8))
                }
            }
            index++
        }
    }
    suspend fun append(prepare: Boolean = true) = mutex.withLock {
        if (finished || closed) return@withLock
        // Publish every consumed range. Cancellation is observed between bounded scanner batches.
        withContext(NonCancellable) {
            var eof = false
            val batch = withContext(Dispatchers.IO) {
                val source = cursor ?: MarkdownBlocks.Cursor(store.open(id, hash), byteLimit, lineLimit).also { cursor = it }
                val blockLimit = if (prepare) 128 else 512
                val spanLimit = if (prepare) NoteSizePolicy.IO_CHUNK_BYTES else 128 * 1024
                buildList<MarkdownBlocks.Block> {
                    var visited = 0
                    while (visited++ < blockLimit) {
                        val next = pending?.also { pending = null } ?: source.next()
                        if (next == null) { eof = true; break }
                        if (isNotEmpty() && next.end - first().start > spanLimit) { pending = next; break }
                        if (next.visible) add(next)
                    }
                }
            }
            try { if (prepare && batch.isNotEmpty()) prepareBatch(batch) }
            finally {
                withContext(Dispatchers.Main.immediate) { blocks.addAll(batch); finished = eof }
            }
        }
    }
    suspend fun warm(first: Int, last: Int) {
        val requested = mutex.withLock {
            if (closed || blocks.isEmpty() || last < first || last < 0 || first > blocks.lastIndex) return
            blocks.subList(first.coerceAtLeast(0), last.coerceAtMost(blocks.lastIndex) + 1).toList()
        }
        // Yield the reader between small speculative batches. A newly visible row must not
        // wait behind hundreds of offscreen parses or a complete warm range.
        for (batch in requested.chunked(8)) {
            currentCoroutineContext().ensureActive()
            mutex.withLock {
                if (closed) return
                prepareBatch(batch.filter { cached(it) == null })
            }
            yield()
        }
    }
    suspend fun warmVisible(first: Int, last: Int) {
        warm(first, last)
        warm(last + 1, last + NoteSizePolicy.MARKDOWN_PREFETCH_BLOCKS)
        warm(first - 64, first - 1)
    }
    suspend fun prepare(block: MarkdownBlocks.Block): PreparedNoteBlock = mutex.withLock {
        cached(block)?.let { return@withLock it }
        check(!closed)
        val text = range(block.start, (block.end - block.start).toInt()).toString(Charsets.UTF_8)
        withContext(Dispatchers.Default) { parsed(block, text) }
    }
    suspend fun close() = mutex.withLock {
        closed = true
        withContext(Dispatchers.IO) { try { cursor?.close() } finally { reader?.close() } }
        synchronized(cache) { cache.clear(); cacheBytes = 0 }; documents.clear()
    }
}

/** First content is prepared from a small prefix; indexing and parsing advance with the viewport. */
@Composable
fun LazyMarkdownNote(id: String, hash: String, bytes: Long, jumpByte: Long, modifier: Modifier,
    onBlockChange: ((Long, Long, String) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    searchQuery: String? = null,
    activeMatchByte: Long? = null,
    state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()) {
    val context = LocalContext.current
    val store = remember { PrivateMarkdownStore(context) }
    val fontScale = LocalDensity.current.fontScale
    val typo = com.example.ui.theme.LocalCobaltTypography.current
    val typographyKey = remember(typo.prefs) {
        "${typo.prefs.fontSize}_${typo.prefs.lineHeight}_${typo.prefs.fontWeight}_${typo.prefs.textFont.key}"
    }
    BoxWithConstraints(modifier) {
        val lineBaseDp = (typo.body.fontSize.value * typo.lineHeightMultiplier)
        val lineLimit = (maxHeight.value / (lineBaseDp * fontScale)).toInt().coerceIn(1, 16)
        val columns = (maxWidth.value / (typo.body.fontSize.value * fontScale)).toInt().coerceAtLeast(1)
        val byteLimit = (columns * lineLimit * 2).coerceIn(16, 1024)
        val session = remember(id, hash, bytes, byteLimit, lineLimit, typographyKey) { MarkdownReadSession(store, id, hash, byteLimit, lineLimit) }
        var failure by remember(session) { mutableStateOf(false) }
        var jumping by remember(session) { mutableStateOf(false) }
        val headerItems = if (header == null) 0 else 1
        LaunchedEffect(session) {
            try {
                session.append()
                while (!session.finished) { ensureActive(); session.append(prepare = false); yield() }
                awaitCancellation()
            }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { failure = true }
            finally { withContext(NonCancellable) { runCatching { session.close() } } }
        }
        LaunchedEffect(session, state) {
            snapshotFlow {
                val first = state.firstVisibleItemIndex
                val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                // Index growth beyond the warm range must not cancel a useful prefetch repeatedly.
                if (jumping || failure) null else
                    Triple(first, last, minOf(session.blocks.size, last + NoteSizePolicy.MARKDOWN_PREFETCH_BLOCKS + 1))
            }.distinctUntilChanged().collectLatest { visible ->
                val (first, last, count) = visible ?: return@collectLatest
                if (count == 0) return@collectLatest
                try {
                    session.warmVisible((first - headerItems).coerceAtLeast(0),
                        (last - headerItems).coerceAtMost(count - 1))
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
                    session.warm(target, (target + 8).coerceAtMost(session.blocks.lastIndex))
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
                val isBlockActive = activeMatchByte != null && (activeMatchByte in block.start until block.end || (block.start == block.end && activeMatchByte == block.start))
                val ready by produceState(session.cached(block), session, block.start) {
                    if (value == null) try { value = session.prepare(block) }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { value = PreparedNoteBlock("This part could not be read. The saved note is retained.", null) }
                }
                val loaded = ready
                val blockModifier = if (isBlockActive) {
                    Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                } else {
                    Modifier.fillMaxWidth().padding(top = if (index > 0 && !block.codeContinuesBefore && session.blocks[index - 1].markdown) 6.dp else 0.dp)
                }

                Box(modifier = blockModifier) {
                    if (loaded == null) {
                        // Warm ranges normally hit the cache. Reserve bounded space on a cold jump;
                        // do not show scrolling shimmer/skeleton rows or render raw Markdown.
                        val wraps = ((block.end - block.start + columns - 1) / columns).toInt()
                        val lines = maxOf(block.lines, wraps).coerceIn(1, lineLimit * 2)
                        Spacer(Modifier.height((lines * lineBaseDp * fontScale).dp))
                    }
                    else if (loaded.document != null) MarkdownText(
                        markdown = loaded.text,
                        prepared = loaded.document,
                        onMarkdownChange = onBlockChange?.let { change -> { updated -> change(block.start, block.end, updated) } },
                        highlightQuery = searchQuery,
                        isHighlightActive = isBlockActive,
                        codeContinuesBefore = block.codeContinuesBefore,
                        codeContinuesAfter = block.codeContinuesAfter
                    )
                    else SelectionContainer {
                        val highlight = if (!searchQuery.isNullOrBlank()) SearchHighlight(searchQuery, isBlockActive) else null
                        val primary = MaterialTheme.colorScheme.primary
                        Text(
                            text = AnnotatedString(loaded.text).withSearchHighlight(highlight, primary),
                            style = typo.body
                        )
                    }
                }
            }
            if (!session.finished && !failure) item(key = "note_loading") {
                if (session.blocks.isEmpty()) NoteBodySkeleton() else Spacer(Modifier.height(maxHeight))
            }
            if (failure) item(key = "note_error") { Text("This note could not be rendered. Use plain text or Export; its body is retained.") }
        }
    }
}
