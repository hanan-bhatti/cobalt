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
import com.example.notes.*
import com.example.ui.components.MarkdownText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** A bounded reader. Oversized structural blocks retain literal text rather than partial Markdown. */
@Composable
fun LazyMarkdownNote(id: String, hash: String, bytes: Long, jumpByte: Long, modifier: Modifier,
    onBlockChange: ((Long, Long, String) -> Unit)? = null) {
    val context = LocalContext.current
    val store = remember { PrivateMarkdownStore(context) }
    val fontScale = LocalDensity.current.fontScale
    var failure by remember(id, hash) { mutableStateOf(false) }
    val cache = remember(id, hash) { object : LinkedHashMap<Long, String>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, String>?) = size > 32
    } }
    BoxWithConstraints(modifier) {
        val lineLimit = (maxHeight.value / (24 * fontScale)).toInt().coerceIn(1, 16)
        val columns = (maxWidth.value / (16 * fontScale)).toInt().coerceAtLeast(1)
        val byteLimit = (columns * lineLimit * 2).coerceIn(16, 1024)
        val blocks by produceState<List<MarkdownBlocks.Block>?>(null, id, hash, byteLimit, lineLimit) {
            try { value = withContext(Dispatchers.IO) {
                val cancellation = kotlin.coroutines.coroutineContext
                val indexed = store.open(id, hash).use { MarkdownBlocks.scan(it, byteLimit, lineLimit) { cancellation.ensureActive() } }
                var decoratedLinks = 0
                indexed.map { block ->
                    cancellation.ensureActive()
                    if (!block.markdown || decoratedLinks >= NoteSizePolicy.DECORATED_LINK_LIMIT) block.copy(markdown = false)
                    else {
                        val text = store.readRange(id, hash, block.start, (block.end - block.start).toInt()).toString(Charsets.UTF_8)
                        val parsed = com.example.ui.components.parseSafeMarkdown(text)
                        val decorate = parsed.node != null && decoratedLinks + parsed.links <= NoteSizePolicy.DECORATED_LINK_LIMIT
                        if (decorate) decoratedLinks += parsed.links
                        block.copy(markdown = decorate)
                    }
                }
            } } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
            catch (_: Exception) { failure = true; value = emptyList() }
        }
        val current = blocks
        if (current == null) { CircularProgressIndicator(); return@BoxWithConstraints }
        if (failure) { Text("This note could not be rendered. Use plain-text mode or Export; its body is retained."); return@BoxWithConstraints }
        val state = rememberLazyListState()
        LaunchedEffect(jumpByte, current) {
            if (current.isNotEmpty()) {
                val match = current.binarySearch { block -> when {
                    block.end <= jumpByte -> -1
                    block.start > jumpByte -> 1
                    else -> 0
                } }
                state.scrollToItem(if (match >= 0) match else (-match - 1).coerceIn(0, current.lastIndex))
            }
        }
        LazyColumn(Modifier.fillMaxSize(), state = state) {
            items(current.size, key = { current[it].start }) { index ->
                val block = current[index]
                val text by produceState<String?>(null, id, hash, block.start) {
                    try { value = withContext(Dispatchers.IO) {
                        synchronized(cache) { cache[block.start] } ?: store.readRange(id, hash, block.start,
                            (block.end - block.start).toInt()).toString(Charsets.UTF_8).also { synchronized(cache) { cache[block.start] = it } }
                    } } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                    catch (_: Exception) { value = "[Section unavailable. Saved note retained.]" }
                }
                val loaded = text
                if (loaded == null) Text("…")
                else if (block.markdown && bytes < NoteSizePolicy.TIER_B_MAX_BYTES) {
                    MarkdownText(loaded, onMarkdownChange = onBlockChange?.let { change ->
                        { updated -> change(block.start, block.end, updated) }
                    })
                } else SelectionContainer { Text(loaded, lineHeight = 24.sp) }
            }
        }
    }
}
