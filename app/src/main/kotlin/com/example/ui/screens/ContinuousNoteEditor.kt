package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notes.*
import com.example.ui.viewmodel.CobaltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** One frameless scrolling surface. Only visible ranges are read; the focused range is editable. */
@Composable
internal fun ContinuousNoteEditor(viewModel: CobaltViewModel, modifier: Modifier,
    header: @Composable () -> Unit, editor: @Composable () -> Unit, footer: @Composable () -> Unit) {
    val state by viewModel.fileEditorState.collectAsState()
    val ref = state.generation
    val context = LocalContext.current
    val store = remember { PrivateMarkdownStore(context) }
    var failure by remember(state.id, ref?.hash) { mutableStateOf(false) }
    val blocks by produceState<List<MarkdownBlocks.Block>?>(null, state.id, ref?.hash) {
        if (ref != null) try {
            value = withContext(Dispatchers.IO) {
                val cancellation = kotlin.coroutines.coroutineContext
                store.open(state.id!!, ref.hash).use {
                    MarkdownBlocks.scan(it, NoteEditorWindows.MAX_CHARACTERS, NoteEditorWindows.MAX_LINES) { cancellation.ensureActive() }
                }.ifEmpty { listOf(MarkdownBlocks.Block(0, 0, false)) }
            }
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { failure = true }
    }
    val listState = rememberLazyListState()
    val current = blocks.orEmpty()
    LaunchedEffect(ref?.hash, blocks) {
        // Match the first native field to the index, without showing page controls.
        if (!state.busy && !state.dirty && state.window.start == 0L && current.isNotEmpty()) {
            val first = current.first()
            if (state.window.end != first.end) viewModel.focusFileWindow(first.start, first.end, ref!!.hash)
        }
    }
    LazyColumn(modifier.testTag("capture_continuous_editor"), state = listState) {
        item(key = "capture_header") { header() }
        state.error?.let { message -> item(key = "session_error") { Text(message, color = MaterialTheme.colorScheme.error) } }
        if (state.busy) item(key = "working") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (blocks == null && !failure && state.error == null) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (failure) item(key = "error") { Text("Could not read this body. Original and draft files are retained.", color = MaterialTheme.colorScheme.error) }
        items(current.size, key = { it }) { index ->
            val block = current[index]
            val focused = state.window.start == block.start && state.window.end == block.end
            if (focused) editor()
            else {
                val text by produceState<String?>(null, state.id, ref?.hash, block.start, block.end) {
                    try { value = withContext(Dispatchers.IO) {
                        store.readRange(state.id!!, ref!!.hash, block.start, (block.end - block.start).toInt()).toString(Charsets.UTF_8)
                    } } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                    catch (_: Exception) { value = null }
                }
                Text(text ?: "…", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
                    modifier = Modifier.fillMaxWidth().clickable(enabled = text != null && !state.busy) {
                        viewModel.focusFileWindow(block.start, block.end, ref!!.hash)
                    }.testTag("capture_text_range_$index"))
            }
        }
        item(key = "capture_footer") { Spacer(Modifier.height(16.dp)); footer() }
    }
}
