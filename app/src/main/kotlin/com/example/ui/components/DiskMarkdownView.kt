package com.example.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import com.example.notes.*
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.*
import java.io.Reader

/** One native editing surface with a bounded, invisible materialization window and global selection. */
internal class DiskMarkdownView(context: Context) : CodeEditor(context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var document: ContinuousNoteDocument? = null
    private val session get() = requireNotNull(document?.disk)
    private var start = 0
    private var total = 0
    private var anchor = 0
    private var caret = 0
    private var oldLeft = 0
    private var oldRight = 0
    private var windowEpoch = 0L
    private var binding = false
    private var composing = false
    private var loading = false
    private var loadFailed = false
    private var blocked = false
    private var backpressure = false
    private var hostEditable = true
    private var isRestoringScroll = false
    private var targetAnchorChar = 0
    private var scrollbarAlpha = 0f
    private var scrollbarFadeJob: Job? = null
    private var isDraggingScrollbar = false
    private var scrollbarDragTouchY = 0f
    private var scrubTargetChar = 0
    private var scrubJob: Job? = null
    private val scrollbarThumbRect = RectF()
    private val scrollbarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var pendingBefore = DiskTextModel.Position(0, 0)
    private val pending = ArrayList<DiskTextModel.Replacement>()
    private var delivery: Job? = null
    private var rewindow: Job? = null
    private var spans: ViewportMarkdownSpans? = null
    private var styling: Job? = null
    private var released = false
    var message: (String) -> Unit = {}
    var progress: (Float?) -> Unit = {}
    var tool: (String) -> Unit = {}
    private val freeze: (Boolean) -> Unit = { isEditable = hostEditable && !it && !loading && !loadFailed && !blocked && !backpressure }
    private val format: (String) -> Unit = { command ->
        when (command) {
            "undo" -> undo()
            "redo" -> redo()
            "paste" -> pasteText()
            "find", "outline" -> tool(command)
            else -> formatGlobal(command)
        }
    }
    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isLineNumberEnabled = false; isHighlightCurrentLine = false; isHighlightCurrentBlock = false
        isHighlightBracketPair = false; isDisplayLnPanel = false; isCursorAnimationEnabled = false
        setDividerWidth(0f); setTextSize(16f); setWordwrap(true, false, true)
        isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        props.apply { autoIndent = false; symbolPairAutoCompletion = false; formatPastedText = false
            stickyScroll = false; drawSideBlockLine = false; maxIPCTextLength = 8192
            maxAccessibilityTextLength = 16_384; clipboardTextLengthLimit = 512 * 1024 }
        getComponent(EditorAutoCompletion::class.java).isEnabled = false
        subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
            if (binding || event.action == ContentChangeEvent.ACTION_SET_NEW_TEXT || document == null) return@subscribeEvent
            if (pending.isEmpty()) pendingBefore = DiskTextModel.Position(anchor, caret)
            val position = start + event.changeStart.index
            val removed = if (event.action == ContentChangeEvent.ACTION_DELETE) event.changedText.length else 0
            val inserted = if (event.action == ContentChangeEvent.ACTION_INSERT) event.changedText.toString() else ""
            pending.add(DiskTextModel.Replacement(position, position + removed, inserted))
            total += inserted.length - removed
            anchor = position + inserted.length; caret = anchor
            syncDocumentSelection()
            if (pending.size >= 48 || text.length > DiskTextModel.MAX_VIEW_CHARACTERS + 8192) { backpressure = true; isEditable = false }
            if (delivery?.isActive != true) delivery = scope.launch { yield(); deliver() }
            warmStyles()
        }
        subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
            if (binding || document == null) return@subscribeEvent
            // An endpoint outside the window remains global until the user actually moves it.
            if (event.left.index != oldLeft) anchor = start + event.left.index
            if (event.right.index != oldRight) caret = start + event.right.index
            oldLeft = event.left.index; oldRight = event.right.index
            syncDocumentSelection()
        }
        subscribeEvent(ScrollEvent::class.java) { _, _ ->
            if (!binding && !loading && !isRestoringScroll) {
                saveAnchor()
                maybeRewindow()
                warmStyles()
            }
            triggerScrollbar()
        }
    }
    fun bind(value: ContinuousNoteDocument) {
        if (document === value) return
        check(value.disk != null)
        document?.detachView(); value.attachView(); document = value
        anchor = value.selectionStart; caret = value.selectionEnd; total = value.disk.state.value.characters
        value.onFreeze = freeze; value.onFormat = format
        value.disk.captureForExport = { deliverNow(final = true) }
        value.disk.deliverPending = { check(deliverNow()) { "Native edits are waiting for recovery" } }
        value.disk.onBackpressure = { value ->
            backpressure = value
            if (!value && pending.isNotEmpty()) deliverNow()
            freeze(document?.frozen != false)
        }
        rewindow = scope.launch { load(if (value.viewportStart == 0 && caret > 0) caret else value.viewportStart, false, caret) }
    }
    private fun syncDocumentSelection() { document?.let { it.selectionStart = anchor; it.selectionEnd = caret } }
    private suspend fun deliver() { deliverNow() }
    private fun deliverNow(final: Boolean = false): Boolean {
        if (pending.isEmpty()) return true
        val batch = pending.toList()
        val accepted = session.record(batch, pendingBefore, DiskTextModel.Position(anchor, caret), final)
        if (accepted) { pending.clear(); backpressure = false; freeze(document?.frozen != false) }
        else { backpressure = true; isEditable = false }
        return accepted
    }
    private fun visiblePosition(): Int {
        if (isRestoringScroll) return targetAnchorChar
        if (layout.rowCount == 0) return start
        val row = layout.getRowAt(firstVisibleRow.coerceIn(0, layout.rowCount - 1))
        return start + text.getCharIndex(row.lineIndex, row.startColumn)
    }
    private fun saveAnchor() {
        if (isRestoringScroll) return
        document?.let { it.viewportStart = visiblePosition(); it.scrollY = offsetY % rowHeight }
    }
    private fun maybeRewindow() {
        if (composing || loading || isRestoringScroll || isDraggingScrollbar || pending.isNotEmpty() || delivery?.isActive == true || document?.frozen != false) return
        val visible = visiblePosition()
        val margin = minOf(4096, maxOf(32, text.length / 5))
        if ((start > 0 && visible - start < margin) || (start + text.length < total && start + text.length - visible < margin * 2)) {
            if (rewindow?.isActive != true) rewindow = scope.launch { load(visible, true) }
        }
    }
    private suspend fun load(target: Int, keepScroll: Boolean, reveal: Int? = null) {
        if (released) return
        loading = true
        isRestoringScroll = keepScroll
        targetAnchorChar = target
        isEditable = false
        try {
            delivery?.join(); check(pending.isEmpty()) { "Native edits need recovery before changing the viewport" }
            val previousVelocity = if (keepScroll && !scroller.isFinished) {
                val currV = scroller.currVelocity.toInt()
                if (scroller.finalY >= offsetY) currV else -currV
            } else 0
            val pixel = if (keepScroll) offsetY % rowHeight else document?.scrollY ?: 0
            val oldX = offsetX
            val data = session.execute { model ->
                val size = model.status.characters
                var from = (target - 8192).coerceIn(0, size)
                var source = model.text(from, DiskTextModel.MAX_VIEW_CHARACTERS)
                if (from > 0 && source.isNotEmpty() && Character.isLowSurrogate(source[0])) { from++; source = source.drop(1) }
                // Prefer an existing line boundary outside the visible region, preserving native wrap layout.
                if (from > 0) {
                    val boundary = source.indexOf('\n').takeIf { it in 0..2048 && from + it + 1 < target }
                    if (boundary != null) { from += boundary + 1; source = source.substring(boundary + 1) }
                }
                val targetInSource = (target - from).coerceIn(0, source.length)
                var index = targetInSource; var breaks = 0
                while (index > 0 && breaks < 512) { index--; if (source[index] == '\n' || source[index] == '\r') breaks++ }
                if (breaks == 512) { index++; from += index; source = source.substring(index) }
                index = (target - from).coerceIn(0, source.length); breaks = 0
                while (index < source.length && breaks < 512) { if (source[index] == '\n' || source[index] == '\r') breaks++; index++ }
                if (breaks == 512) source = source.substring(0, index)
                if (from + source.length < size) {
                    val end = source.lastIndexOf('\n')
                    if (end >= source.length - 2048 && from + end > target) source = source.substring(0, end + 1)
                    if (source.isNotEmpty() && Character.isHighSurrogate(source.last())) source = source.dropLast(1)
                }
                Triple(from, source, size)
            }
            if (released) return
            binding = true
            try {
                windowEpoch++
                start = data.first; total = data.third
                setText(Content(data.second).also { it.setUndoEnabled(false) }, true, null)
                spans = ViewportMarkdownSpans(text); setStyles(Styles(spans))
                val a = (minOf(anchor, caret) - start).coerceIn(0, text.length)
                val b = (maxOf(anchor, caret) - start).coerceIn(0, text.length)
                val left = text.indexer.getCharPosition(a); val right = text.indexer.getCharPosition(b)
                setSelectionRegion(left.line, left.column, right.line, right.column, false)
                oldLeft = a; oldRight = b
                loadFailed = false
            } finally { binding = false }

            if (keepScroll) {
                val local = (target - start).coerceIn(0, text.length)
                val pos = text.indexer.getCharPosition(local)
                val estimatedY = (pos.line * rowHeight + pixel).coerceIn(0, scrollMaxY)
                scroller.forceFinished(true)
                scroller.startScroll(oldX.coerceIn(0, scrollMaxX), estimatedY, 0, 0, 0)
                scroller.abortAnimation()
            }

            restoreAnchor(target, pixel, oldX, previousVelocity, reveal)
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).restartInput(this)
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) {
            loadFailed = true
            isRestoringScroll = false
            message("The editor could not load this position. Saved text and pending edits are retained.")
        }
        finally {
            loading = false
            freeze(document?.frozen != false)
            warmStyles()
            triggerScrollbar()
        }
    }
    private fun restoreAnchor(target: Int, pixel: Int, x: Int, velocity: Int, reveal: Int?, attempt: Int = 0) {
        if (released) return
        val local = (target - start).coerceIn(0, text.length)
        val position = text.indexer.getCharPosition(local)
        if ((layout.rowCount == 0 || layout.getLineNumberForRow(layout.rowCount - 1) + 1 != text.lineCount) && attempt < 120) {
            postDelayed({ restoreAnchor(target, pixel, x, velocity, reveal, attempt + 1) }, 16); return
        }
        val y = (layout.getCharLayoutOffset(position.line, position.column)[0].toInt() - rowHeight + pixel).coerceIn(0, scrollMaxY)
        scroller.forceFinished(true)
        scroller.startScroll(x.coerceIn(0, scrollMaxX), y, 0, 0, 0); scroller.abortAnimation()
        if (velocity != 0) scroller.fling(offsetX, y, 0, velocity, 0, scrollMaxX, 0, scrollMaxY, 0, 0)
        if (reveal != null) ensureSelectionVisible()
        isRestoringScroll = false
        invalidate()
    }
    private fun action(block: suspend () -> Unit) {
        if (loading || blocked || document?.frozen != false) return
        val owner = document ?: return
        owner.pendingEdit = scope.launch {
            blocked = true; isEditable = false
            try { delivery?.join(); session.flush(); session.select(anchor, caret); block() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { message("This action did not finish. Original text and recoverable edits are retained.") }
            finally { blocked = false; progress(null); freeze(owner.frozen) }
        }
    }
    override fun undo() { action { session.undo(); restoreModelSelection() } }
    override fun redo() { action { session.redo(); restoreModelSelection() } }
    override fun canUndo(): Boolean = document?.disk?.state?.value?.canUndo == true
    override fun canRedo(): Boolean = document?.disk?.state?.value?.canRedo == true
    private suspend fun restoreModelSelection() {
        session.state.value.let { anchor = it.anchor; caret = it.caret; total = it.characters }
        syncDocumentSelection(); load(caret, false, caret)
    }
    override fun selectAll() {
        anchor = 0; caret = total; syncDocumentSelection()
        binding = true
        try { super.selectAll(); oldLeft = cursor.left; oldRight = cursor.right } finally { binding = false }
    }
    private fun globalSelection() = minOf(anchor, caret) to maxOf(anchor, caret)
    private fun outsideSelection(): Boolean = globalSelection().let { it.first < start || it.second > start + text.length }
    private fun deleteAcrossWindow(before: Int, after: Int, codePoints: Boolean): Boolean {
        // Reject invalid/unbounded IME requests rather than deleting a different range.
        if (before !in 0..8192 || after !in 0..8192) return false
        val (left, right) = globalSelection()
        action {
            val range = session.execute { model ->
                if (left != right) left to right else {
                    val multiplier = if (codePoints) 2 else 1
                    val preceding = model.text((left - before * multiplier).coerceAtLeast(0), minOf(left, before * multiplier))
                    val following = model.text(right, minOf(model.status.characters - right, after * multiplier))
                    val from = if (codePoints) preceding.offsetByCodePoints(preceding.length,
                        -minOf(before, preceding.codePointCount(0, preceding.length))) else (preceding.length - before).coerceAtLeast(0)
                    val until = if (codePoints) following.offsetByCodePoints(0,
                        minOf(after, following.codePointCount(0, following.length))) else minOf(after, following.length)
                    (left - preceding.length + from) to (right + until)
                }
            }
            session.replace(range.first, range.second, java.io.StringReader("")); restoreModelSelection()
        }
        return true
    }
    private fun crossesWindow(before: Int, after: Int, codePoints: Boolean = false): Boolean {
        val (left, right) = globalSelection()
        val scale = if (codePoints) 2L else 1L
        return outsideSelection() || left.toLong() - before * scale < start || right.toLong() + after * scale > start + text.length
    }
    override fun copyText() = copyText(false)
    override fun copyText(shouldCopyLine: Boolean) {
        val (from, until) = globalSelection()
        if (from == until) { super.copyText(shouldCopyLine); return }
        if (until - from > DiskTextModel.MAX_VIEW_CHARACTERS) { message("This selection is too large for Android's clipboard. Export the note to keep all its text."); return }
        action {
            val selected = session.execute { it.text(from, until - from) }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Cobalt", selected))
            message("Copied")
        }
    }
    override fun cutText() {
        val (from, until) = globalSelection()
        if (from == until) return
        if (until - from > DiskTextModel.MAX_VIEW_CHARACTERS) { message("Export large selections before removing them."); return }
        action {
            val selected = session.execute { it.text(from, until - from) }
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                .setPrimaryClip(android.content.ClipData.newPlainText("Cobalt", selected))
            session.replace(from, until, java.io.StringReader("")); restoreModelSelection()
        }
    }
    override fun pasteText() {
        action {
            val context = currentCoroutineContext()
            val value = withContext(Dispatchers.IO) {
                val clip = (this@DiskMarkdownView.context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).primaryClip
                clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this@DiskMarkdownView.context)
            } ?: return@action
            replaceGlobal(value, context)
        }
    }
    override fun pasteText(value: CharSequence?) { if (value != null) action { replaceGlobal(value, currentCoroutineContext()) } }
    private suspend fun replaceGlobal(value: CharSequence, cancellation: kotlin.coroutines.CoroutineContext) {
        val (from, until) = globalSelection()
        progress(0f)
        val reader = object : Reader() {
            var position = 0
            override fun read(chars: CharArray, offset: Int, length: Int): Int {
                cancellation.ensureActive()
                if (position == value.length) return -1
                val count = minOf(length, value.length - position)
                repeat(count) { chars[offset + it] = value[position + it] }; position += count
                if (position % 65_536 < count) post { progress(position.toFloat() / value.length.coerceAtLeast(1)) }
                return count
            }
            override fun close() = Unit
        }
        session.replace(from, until, reader); restoreModelSelection()
    }
    fun highlightMatches(query: String) {
        if (query.isBlank()) {
            clearSearch()
            return
        }
        try {
            searcher.search(query, io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions(true, false))
        } catch (_: Exception) {}
    }

    fun clearSearch() {
        try {
            if (searcher.hasQuery()) {
                searcher.stopSearch()
            }
        } catch (_: Exception) {}
        postInvalidate()
    }

    fun jumpToCharacter(position: Int) { action { session.select(position, position); restoreModelSelection() } }
    fun find(query: String, backwards: Boolean = false, replacement: String? = null, all: Boolean = false) {
        if (query.isEmpty()) return
        highlightMatches(query)
        action {
            val cancellation = currentCoroutineContext()
            if (all && replacement != null) {
                val count = session.execute { it.replaceAll(query, replacement) { cancellation.ensureActive() } }
                message("$count matches replaced")
                restoreModelSelection()
            } else {
                val from = if (backwards) minOf(anchor, caret) else maxOf(anchor, caret)
                val match = session.execute { it.find(query, from, backwards) { cancellation.ensureActive() } }
                if (match == null) message("No matches")
                else {
                    if (replacement != null) session.replace(match, match + query.length, java.io.StringReader(replacement))
                    else session.select(match, match + query.length)
                    restoreModelSelection()
                }
            }
        }
    }
    private fun formatGlobal(command: String) {
        if (command.startsWith("table:")) {
            action {
                val position = globalSelection().first
                val cancellation = kotlinx.coroutines.currentCoroutineContext()
                try {
                    session.execute { model ->
                        val edit = com.example.notes.MarkdownTableEdits.plan(model.status.characters, position, command,
                            model::text, { cancellation.ensureActive() })
                        model.replace(edit.start, edit.end, edit.text,
                            DiskTextModel.Position(edit.caret, edit.caret))
                    }
                    restoreModelSelection()
                } catch (failure: IllegalArgumentException) { message(failure.message ?: "The table was not changed.") }
            }
            return
        }
        val pair = when(command) { "Bold" -> "**" to "**"; "Italic" -> "_" to "_"; "Strikethrough" -> "~~" to "~~"
            "CodeInline" -> "`" to "`"; "CodeBlock" -> "\n```\n" to "\n```\n"; "Link" -> "[" to "](https://)"; else -> null }
        if (pair != null) action {
            val (from, until) = globalSelection()
            session.execute { it.replaceBatch(listOf(DiskTextModel.Replacement(until, until, pair.second), DiskTextModel.Replacement(from, from, pair.first))) }
            restoreModelSelection()
        } else {
            val prefix = when(command) { "Heading1" -> "# "; "Heading2" -> "## "; "Heading3" -> "### "; "Quote" -> "> "; "BulletList" -> "- "; "NumberedList" -> "1. "; "Checklist" -> "- [ ] "; else -> return }
            action {
                val from = globalSelection().first
                val preceding = session.execute { if (from == 0) "" else it.text(from - 1, 1) }
                session.execute { it.replace(from, from, (if (from > 0 && preceding != "\n" && preceding != "\r") "\n" else "") + prefix) }
                restoreModelSelection()
            }
        }
    }
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val delegate = super.onCreateInputConnection(outAttrs) ?: return null
        val epoch = windowEpoch
        return object : InputConnectionWrapper(delegate, false) {
            override fun commitText(value: CharSequence?, newCursorPosition: Int): Boolean {
                if (released || epoch != windowEpoch || !isEditable) return false
                composing = false
                if (value != null && (outsideSelection() || value.length > 8192)) { pasteText(value); return true }
                return super.commitText(value, newCursorPosition)
            }
            override fun setComposingText(value: CharSequence?, newCursorPosition: Int): Boolean {
                if (released || epoch != windowEpoch || !isEditable) return false
                if (outsideSelection()) { if (value != null) pasteText(value); return value != null }
                composing = true; return super.setComposingText(value, newCursorPosition)
            }
            override fun finishComposingText(): Boolean { if (released || epoch != windowEpoch) return false; composing = false; val result = super.finishComposingText(); post { maybeRewindow() }; return result }
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (released || epoch != windowEpoch || !isEditable) return false
                if (beforeLength < 0 || afterLength < 0) return false
                if (crossesWindow(beforeLength, afterLength)) return deleteAcrossWindow(beforeLength, afterLength, false)
                return super.deleteSurroundingText(beforeLength, afterLength)
            }
            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                if (released || epoch != windowEpoch || !isEditable) return false
                if (beforeLength < 0 || afterLength < 0) return false
                if (crossesWindow(beforeLength, afterLength, true)) return deleteAcrossWindow(beforeLength, afterLength, true)
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
            }
            override fun setSelection(start: Int, end: Int): Boolean {
                if (released || epoch != windowEpoch || !isEditable) return false
                if (start !in 0..text.length || end !in 0..text.length) return false
                val result = super.setSelection(start, end)
                if (result) { anchor = this@DiskMarkdownView.start + start; caret = this@DiskMarkdownView.start + end; syncDocumentSelection() }
                return result
            }
            override fun getSelectedText(flags: Int): CharSequence? = if (released || epoch != windowEpoch || outsideSelection()) null else super.getSelectedText(flags)
            override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence? =
                if (released || epoch != windowEpoch || globalSelection().first < start) null else super.getTextBeforeCursor(length.coerceIn(0, 8192), flags)
            override fun getTextAfterCursor(length: Int, flags: Int): CharSequence? =
                if (released || epoch != windowEpoch || globalSelection().second > start + text.length) null else super.getTextAfterCursor(length.coerceIn(0, 8192), flags)
        }
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!released && isEditable && !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed &&
            (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            val backwards = keyCode == KeyEvent.KEYCODE_DPAD_LEFT
            if (outsideSelection() || (backwards && caret <= start && start > 0) ||
                (!backwards && caret >= start + text.length && caret < total)) {
                val previousAnchor = anchor
                val previousCaret = caret
                val (left, right) = globalSelection()
                action {
                    val position = session.execute { model ->
                        when {
                            !event.isShiftPressed && left != right -> if (backwards) left else right
                            backwards -> {
                                val chars = model.text((previousCaret - 2).coerceAtLeast(0), minOf(previousCaret, 2))
                                previousCaret - if (chars.isEmpty()) 0 else Character.charCount(chars.codePointBefore(chars.length))
                            }
                            else -> {
                                val chars = model.text(previousCaret, minOf(2, model.status.characters - previousCaret))
                                previousCaret + if (chars.isEmpty()) 0 else Character.charCount(chars.codePointAt(0))
                            }
                        }
                    }
                    session.select(if (event.isShiftPressed) previousAnchor else position, position)
                    restoreModelSelection()
                }
                return true
            }
        }
        if (!released && isEditable && (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL)) {
            val before = if (keyCode == KeyEvent.KEYCODE_DEL) 1 else 0
            val after = 1 - before
            if (crossesWindow(before, after, true)) return deleteAcrossWindow(before, after, true)
        }
        if (!released && isEditable && outsideSelection() && !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed &&
            event.unicodeChar != 0 && Character.isValidCodePoint(event.unicodeChar)) {
            pasteText(String(Character.toChars(event.unicodeChar))); return true
        }
        return super.onKeyDown(keyCode, event)
    }
    private fun warmStyles() {
        val map = spans ?: return
        styling?.cancel()
        styling = scope.launch { delay(24); val from = (firstVisibleLine - 16).coerceAtLeast(0); val until = lastVisibleLine + 32
            withContext(Dispatchers.Default) { map.prepare(from, until) }; if (!released && spans === map) setStyles(Styles(map)) }
    }
    private fun triggerScrollbar() {
        scrollbarFadeJob?.cancel()
        scrollbarAlpha = 1f
        invalidate()
        if (!isDraggingScrollbar) {
            scrollbarFadeJob = scope.launch {
                delay(1500)
                val steps = 8
                for (i in (steps - 1) downTo 0) {
                    delay(25)
                    scrollbarAlpha = i.toFloat() / steps
                    invalidate()
                }
                scrollbarAlpha = 0f
                invalidate()
            }
        }
    }

    private fun updateScrollbarThumbRect(): RectF {
        val viewHeight = height.toFloat()
        val viewWidth = width.toFloat()
        if (viewHeight <= 0f || viewWidth <= 0f || total <= 0) {
            scrollbarThumbRect.setEmpty()
            return scrollbarThumbRect
        }

        val minThumbHeight = dpUnit * 48f
        val trackWidth = dpUnit * if (isDraggingScrollbar) 8f else 4f
        val marginRight = dpUnit * 3f

        val linesOnScreen = (viewHeight / rowHeight.coerceAtLeast(1)).toInt().coerceAtLeast(1)
        val avgCharsPerLine = (text.length / maxOf(1, text.lineCount)).coerceAtLeast(20)
        val visibleChars = minOf(total, linesOnScreen * avgCharsPerLine)

        val docRatio = (visibleChars.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
        val thumbHeight = (viewHeight * docRatio).coerceIn(minThumbHeight, viewHeight)

        val scrollableChars = maxOf(1, total - visibleChars)
        val globalPos = if (isRestoringScroll) targetAnchorChar else visiblePosition()
        val progress = if (isDraggingScrollbar) {
            (scrubTargetChar.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
        } else {
            (globalPos.toFloat() / scrollableChars).coerceIn(0f, 1f)
        }

        val trackHeight = viewHeight - thumbHeight
        val thumbTop = progress * trackHeight
        val thumbRight = viewWidth - marginRight
        val thumbLeft = thumbRight - trackWidth

        scrollbarThumbRect.set(thumbLeft, thumbTop, thumbRight, thumbTop + thumbHeight)
        return scrollbarThumbRect
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawGlobalScrollBar(canvas)
    }

    private fun drawGlobalScrollBar(canvas: Canvas) {
        if (scrollbarAlpha <= 0f || total <= 0) return
        val rect = updateScrollbarThumbRect()
        if (rect.isEmpty) return

        val thumbColor = if (isDraggingScrollbar) {
            colorScheme.getColor(EditorColorScheme.SELECTION_HANDLE)
        } else {
            colorScheme.getColor(EditorColorScheme.SCROLL_BAR_THUMB)
        }

        val baseAlpha = Color.alpha(thumbColor).takeIf { it > 0 } ?: 200
        val alphaInt = (baseAlpha * scrollbarAlpha).toInt().coerceIn(0, 255)
        scrollbarPaint.color = (thumbColor and 0x00FFFFFF) or (alphaInt shl 24)

        val save = canvas.save()
        canvas.translate(offsetX.toFloat(), offsetY.toFloat())
        try {
            val radius = rect.width() / 2f
            canvas.drawRoundRect(rect, radius, radius, scrollbarPaint)
        } finally {
            canvas.restoreToCount(save)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (released) return super.onTouchEvent(event)
        val rect = scrollbarThumbRect

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val isNearTrack = scrollbarAlpha > 0.05f && event.x >= width - dpUnit * 36f
                val isNearThumb = isNearTrack && event.y >= rect.top - dpUnit * 20f && event.y <= rect.bottom + dpUnit * 20f

                if (isNearThumb || (isNearTrack && !rect.isEmpty)) {
                    isDraggingScrollbar = true
                    scrollbarDragTouchY = if (isNearThumb) event.y - rect.top else rect.height() / 2f
                    parent?.requestDisallowInterceptTouchEvent(true)
                    triggerScrollbar()
                    handleScrollbarDrag(event.y)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDraggingScrollbar) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    triggerScrollbar()
                    handleScrollbarDrag(event.y)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDraggingScrollbar) {
                    isDraggingScrollbar = false
                    triggerScrollbar()
                    val finalTarget = scrubTargetChar
                    scope.launch { load(finalTarget, keepScroll = false) }
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleScrollbarDrag(touchY: Float) {
        val viewHeight = height.toFloat()
        val thumbHeight = scrollbarThumbRect.height()
        val trackHeight = viewHeight - thumbHeight
        if (trackHeight <= 0f || total <= 0) return

        val newThumbTop = (touchY - scrollbarDragTouchY).coerceIn(0f, trackHeight)
        val progress = (newThumbTop / trackHeight).coerceIn(0f, 1f)
        val target = (progress * total).toInt().coerceIn(0, total)
        scrubTargetChar = target
        invalidate()

        if (scrubJob?.isActive != true) {
            scrubJob = scope.launch {
                while (isDraggingScrollbar) {
                    val current = scrubTargetChar
                    load(current, keepScroll = false)
                    if (scrubTargetChar == current) break
                }
            }
        }
    }

    fun setHostEditable(value: Boolean) { hostEditable = value; freeze(document?.frozen != false) }
    fun cancelPaste() { document?.pendingEdit?.cancel() }
    fun applyColors(background: Int, foreground: Int, accent: Int, muted: Int, selection: Int) {
        val matchedDim = android.graphics.Color.argb(
            40,
            android.graphics.Color.red(accent),
            android.graphics.Color.green(accent),
            android.graphics.Color.blue(accent)
        )
        val activeBright = android.graphics.Color.argb(
            120,
            android.graphics.Color.red(accent),
            android.graphics.Color.green(accent),
            android.graphics.Color.blue(accent)
        )
        colorScheme.apply {
            setColor(EditorColorScheme.WHOLE_BACKGROUND, background)
            setColor(EditorColorScheme.TEXT_NORMAL, foreground)
            setColor(EditorColorScheme.TEXT_SELECTED, foreground)
            setColor(EditorColorScheme.SELECTION_INSERT, accent)
            setColor(EditorColorScheme.SELECTION_HANDLE, accent)
            setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, activeBright)
            setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, matchedDim)
            setColor(EditorColorScheme.MATCHED_TEXT_BORDER, android.graphics.Color.TRANSPARENT)
            setColor(EditorColorScheme.KEYWORD, accent)
            setColor(EditorColorScheme.LITERAL, muted)
            setColor(EditorColorScheme.SCROLL_BAR_THUMB, muted)
            setColor(EditorColorScheme.SCROLL_BAR_TRACK, android.graphics.Color.TRANSPARENT)
            setColor(EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND, background)
            setColor(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR, foreground)
        }
        invalidate()
    }
    fun dispose() {
        if (released) return
        released = true
        isDraggingScrollbar = false
        scrollbarFadeJob?.cancel()
        scrubJob?.cancel()
        saveAnchor()
        document?.let {
            deliverNow(final = true)
            it.onFormat = null; it.onFreeze = null; it.disk?.deliverPending = null; it.disk?.captureForExport = null; it.disk?.onBackpressure = null; it.detachView()
        }
        document = null; scope.cancel(); release()
    }
}
