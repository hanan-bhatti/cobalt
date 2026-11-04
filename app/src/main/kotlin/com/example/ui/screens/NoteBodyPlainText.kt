package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notes.LegacyNoteRecovery
import com.example.notes.RecoveryNote
import com.example.notes.NoteBodyMetadata
import com.example.notes.NoteSizePolicy
import com.example.ui.components.NoteBodySkeleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.text.AnnotatedString
import com.example.ui.components.SearchHighlight
import com.example.ui.components.withSearchHighlight
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun NoteBodyPlainText(reader: LegacyNoteRecovery, note: RecoveryNote, modifier: Modifier, jumpByte: Long = 0,
    header: (@Composable () -> Unit)? = null,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp), body: NoteBodyMetadata? = null,
    searchQuery: String? = null, activeMatchByte: Long? = null) {
    val fontScale = LocalDensity.current.fontScale
    val typo = com.example.ui.theme.LocalCobaltTypography.current
    val lineBaseDp = (typo.body.fontSize.value * typo.lineHeightMultiplier)
    var unavailable by remember(reader, note.id, note.byteLength, body?.contentHash) { mutableStateOf(false) }
    val metadata by produceState<NoteBodyMetadata?>(null, reader, note.id, note.byteLength, body?.contentHash) {
        try {
            val snapshot = body ?: reader.bodyMetadata(note.id)
            reader.prefetchDisplay(note, snapshot, 0)
            value = snapshot
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { unavailable = true }
    }
    BoxWithConstraints(modifier) {
        // Stream text in bounded 16 KB chunks allowing natural full-width paragraph wrapping
        val chunkBytes = NoteSizePolicy.IO_CHUNK_BYTES
        val length = metadata?.byteLength ?: 0
        val count = if (length == 0L) 0 else (length - 1) / chunkBytes + 1
        LaunchedEffect(reader, metadata, chunkBytes, listState) {
            val snapshot = metadata ?: return@LaunchedEffect
            snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { index ->
                try { reader.prefetchDisplay(note, snapshot, (index - if (header == null) 0 else 1).coerceAtLeast(0).toLong() * chunkBytes) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { /* A visible row reports a failed read; prefetch does not replace note text. */ }
            }
        }
        LaunchedEffect(jumpByte, chunkBytes, count) {
            if (jumpByte > 0 && count in 1..Int.MAX_VALUE.toLong()) {
                metadata?.let { reader.prefetchDisplay(note, it, jumpByte) }
                listState.scrollToItem((jumpByte / chunkBytes).coerceIn(0, count - 1).toInt() + if (header == null) 0 else 1)
            }
        }
        if (count > Int.MAX_VALUE) {
            Text("This note is too large for recovery display. Use Export to preserve the complete body.")
        } else LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
            if (header != null) item { header(); Spacer(Modifier.height(28.dp)) }
            if (metadata == null && !unavailable) item { NoteBodySkeleton() }
            if (unavailable) item { Text("This note could not be read. Its saved record is retained.") }
            val snapshot = metadata
            if (snapshot != null) items(count.toInt(), key = { it }, contentType = { "plain_note_chunk" }) { index ->
                val chunk by produceState(reader.cachedChunk(note, index, chunkBytes, snapshot), reader, snapshot, index, chunkBytes) {
                    if (value == null) try { value = reader.displayChunk(note, index, chunkBytes, snapshot) }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { value = "[This part could not be read. The original note is unchanged.]" }
                }
                val text = chunk
                val isChunkActive = activeMatchByte != null && (index.toLong() * chunkBytes <= activeMatchByte && activeMatchByte < (index + 1).toLong() * chunkBytes)
                if (text == null) NoteBodySkeleton(lines = 1)
                else SelectionContainer {
                    val highlight = if (!searchQuery.isNullOrBlank()) SearchHighlight(searchQuery, isChunkActive) else null
                    val primary = MaterialTheme.colorScheme.primary
                    val displayText = if (highlight != null) {
                        AnnotatedString(text).withSearchHighlight(highlight, primary)
                    } else {
                        AnnotatedString(text)
                    }
                    Text(
                        text = displayText,
                        modifier = Modifier.fillMaxWidth(),
                        style = typo.body.copy(lineHeight = (typo.body.fontSize.value * typo.lineHeightMultiplier).sp)
                    )
                }
            }
        }
    }
}
