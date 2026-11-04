package com.example.ui.components

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import com.example.notes.ContinuousNoteDocument
import com.example.notes.ContinuousTextSearch
import com.example.notes.ContinuousEditorCapacity
import com.example.notes.EditorCapacityException
import com.example.notes.TextUtf8InputStream
import com.example.notes.NoteEditorWindows
import com.example.notes.NoteSizePolicy
import com.example.notes.NoteTextSlice
import com.example.ui.screens.FormatType
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.*

/** One fixed-height native editing surface: one IME connection and selection for the whole note. */
internal class ContinuousMarkdownView(context: Context) : CodeEditor(context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var document: ContinuousNoteDocument? = null
    private var spans: ViewportMarkdownSpans? = null
    private var styling: Job? = null
    private var paste: Job? = null
    private var finding: Job? = null
    private var binding = false
    private var changed: () -> Unit = {}
    var message: (String) -> Unit = {}
    var pasteProgress: (Float?) -> Unit = {}
    var onTool: (String) -> Unit = {}
    private val freeze: (Boolean) -> Unit = { isEditable = !it && paste?.isActive != true }
    private val format: (String) -> Unit = { command ->
        if (document?.pendingEdit?.isActive != true) when (command) {
            "undo" -> if (canUndo() && isEditable) undo()
            "redo" -> if (canRedo() && isEditable) redo()
            "find", "outline" -> onTool(command)
            "paste" -> pasteText()
            else -> if (isEditable) formatSelection(command)
        }
    }

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        isLineNumberEnabled = false
        isHighlightCurrentLine = false
        isHighlightCurrentBlock = false
        isHighlightBracketPair = false
        isDisplayLnPanel = false
        isCursorAnimationEnabled = false
        setDividerWidth(0f)
        setTextSize(16f)
        setTypefaceText(Typeface.DEFAULT)
        setWordwrap(true, false, true)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        props.apply {
            autoIndent = false
            symbolPairAutoCompletion = false
            formatPastedText = false
            stickyScroll = false
            drawSideBlockLine = false
            maxIPCTextLength = 16_384
            maxAccessibilityTextLength = 16_384
            clipboardTextLengthLimit = 512 * 1024
        }
        getComponent(EditorAutoCompletion::class.java).isEnabled = false
        subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
            if (event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT && document != null) {
                val doc = document!!
                val previous = doc.byteLength
                val part = event.changedText
                val splitSurrogate = part.isNotEmpty() && (Character.isLowSurrogate(part[0]) || Character.isHighSurrogate(part[part.length - 1]))
                doc.byteLength = if (previous != null && part.length <= 8192 && !splitSurrogate) {
                    val count = NoteEditorWindows.utf8Bytes(event.changedText)
                    previous + if (event.action == ContentChangeEvent.ACTION_INSERT) count else -count
                } else null // A large deletion is counted in a worker, never by scanning it on Main.
                if (!event.isCausedByUndoManager) {
                    doc.undoPressure += part.length.toLong() * 2
                    if (doc.undoPressure > NoteSizePolicy.EDITOR_UNDO_PRESSURE_BYTES) {
                        // Keep the active undo transaction, discard older retained character buffers.
                        // A single very large transaction can still exceed this pressure threshold.
                        text.setMaxUndoStackSize(1)
                        text.setMaxUndoStackSize(com.example.notes.PieceTable.MAX_UNDO_OPERATIONS)
                        doc.undoPressure = 0
                        if (!doc.undoTrimNotified) {
                            doc.undoTrimNotified = true
                            message("Older undo steps were cleared to limit memory. Your note is unchanged.")
                        }
                    }
                }
                doc.recordEdit(event.changeStart.index,
                    if (event.action == ContentChangeEvent.ACTION_DELETE) part.length else 0,
                    if (event.action == ContentChangeEvent.ACTION_INSERT) part else "")
                changed()
                warmStyles()
            }
        }
        subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
            if (!binding) document?.let { it.selectionStart = event.left.index; it.selectionEnd = event.right.index }
        }
        subscribeEvent(ScrollEvent::class.java) { _, _ ->
            if (!binding) document?.let { it.scrollX = offsetX; it.scrollY = offsetY }
            warmStyles()
        }
    }

    fun bind(value: ContinuousNoteDocument, onChange: () -> Unit) {
        changed = onChange
        if (document === value) return
        val start = value.selectionStart
        val end = value.selectionEnd
        val x = value.scrollX
        val y = value.scrollY
        val previousDocument = document
        value.attachView()
        document = value
        binding = true
        try {
            setText(value.content, true, null)
            spans = ViewportMarkdownSpans(value.content)
            setStyles(Styles(spans))
            if (start != 0 || end != 0) {
                val left = value.content.indexer.getCharPosition(start.coerceIn(0, value.content.length))
                val right = value.content.indexer.getCharPosition(end.coerceIn(0, value.content.length))
                setSelectionRegion(left.line, left.column, right.line, right.column, false)
            }
        } finally { binding = false }
        previousDocument?.detachView()
        value.onFreeze = freeze
        value.onFormat = format
        freeze(value.frozen)
        // Restore cursor/selection without forcing the keyboard open when returning from another screen.
        post {
            if (!isReleased && document === value) {
                if (x == 0 && y == 0 && end > 0 && value.revision == 0L) revealInitialSelection(value)
                else restoreViewport(x, y, value)
                warmStyles()
            }
        }
    }

    private fun revealInitialSelection(expected: ContinuousNoteDocument, attempt: Int = 0) {
        if (isReleased || document !== expected || expected.frozen) return
        if (isEditable) ensureSelectionVisible()
        else if (attempt < 120) postDelayed({ revealInitialSelection(expected, attempt + 1) }, 16)
    }

    private fun restoreViewport(x: Int, y: Int, expected: ContinuousNoteDocument, attempt: Int = 0) {
        if (isReleased || document !== expected) return
        if (layout.layoutHeight < y && attempt < 60) {
            postDelayed({ restoreViewport(x, y, expected, attempt + 1) }, 16)
            return
        }
        scroller.forceFinished(true)
        scroller.startScroll(x.coerceIn(0, scrollMaxX), y.coerceIn(0, scrollMaxY), 0, 0, 0)
        scroller.abortAnimation()
        invalidate()
    }

    fun codeTypeface(code: Boolean) {
        val expected = if (code) Typeface.MONOSPACE else Typeface.DEFAULT
        if (typefaceText != expected) setTypefaceText(expected)
    }

    fun applyColors(background: Int, foreground: Int, accent: Int, muted: Int, selection: Int) {
        colorScheme.apply {
            setColor(EditorColorScheme.WHOLE_BACKGROUND, background)
            setColor(EditorColorScheme.TEXT_NORMAL, foreground)
            setColor(EditorColorScheme.TEXT_SELECTED, foreground)
            setColor(EditorColorScheme.SELECTION_INSERT, accent)
            setColor(EditorColorScheme.SELECTION_HANDLE, accent)
            setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, selection)
            setColor(EditorColorScheme.KEYWORD, accent)
            setColor(EditorColorScheme.LITERAL, muted)
            setColor(EditorColorScheme.SCROLL_BAR_THUMB, muted)
            setColor(EditorColorScheme.SCROLL_BAR_TRACK, android.graphics.Color.TRANSPARENT)
            setColor(EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND, background)
            setColor(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR, foreground)
        }
    }

    private fun warmStyles() {
        val map = spans ?: return
        val doc = document ?: return
        val version = doc.revision
        val first = (firstVisibleLine - 16).coerceAtLeast(0)
        val last = (lastVisibleLine + 32).coerceAtLeast(first)
        styling?.cancel()
        styling = scope.launch {
            delay(24)
            withContext(Dispatchers.Default) { map.prepare(first, last) }
            if (!isReleased && document === doc && version == doc.revision) {
                setStyles(Styles(map))
            } else map.clear()
        }
    }

    private fun formatSelection(command: String) {
        val type = FormatType.entries.firstOrNull { it.name == command } ?: return
        val from = cursor.left
        val until = cursor.right
        // Add wrappers at endpoints, rather than copying a potentially multi-megabyte selection.
        val pair = when (type) {
            FormatType.Bold -> "**" to "**"
            FormatType.Italic -> "_" to "_"
            FormatType.Strikethrough -> "~~" to "~~"
            FormatType.CodeInline -> "`" to "`"
            FormatType.CodeBlock -> "```\n" to "\n```"
            FormatType.Link -> "[" to "](https://example.com)"
            else -> null
        }
        val known = document?.byteLength
        if (known == null) { message("Updating note size. Try formatting again after the draft checkpoint."); return }
        // Formatting inserts only a few Markdown delimiters, without duplicating the selection.
        val overhead = if (pair != null) pair.first.length + pair.second.length +
            (if (from == until && type == FormatType.Link) 9 else 0) else when (type) {
            FormatType.Heading1, FormatType.Quote, FormatType.BulletList -> 2
            FormatType.Heading2, FormatType.NumberedList -> 3
            FormatType.Heading3 -> 4
            FormatType.Checklist -> 6
            else -> 0
        }
        if (known + overhead > (document?.permittedBytes ?: NoteSizePolicy.IMPORT_MAX_BYTES)) {
            message("Note is at its size limit. Remove some text before adding formatting."); return
        }
        text.beginBatchEdit()
        try {
            if (pair != null) {
                if (from == until && type == FormatType.Link) {
                    commitText("[link text](https://example.com)", false, false)
                    select(from + 1, from + 10)
                } else {
                    insertAt(until, pair.second)
                    insertAt(from, pair.first)
                    select(from + pair.first.length, until + pair.first.length)
                }
            } else {
                val prefix = when (type) {
                    FormatType.Heading1 -> "# "
                    FormatType.Heading2 -> "## "
                    FormatType.Heading3 -> "### "
                    FormatType.Quote -> "> "
                    FormatType.BulletList -> "- "
                    FormatType.NumberedList -> "1. "
                    FormatType.Checklist -> "- [ ] "
                    else -> ""
                }
                val position = text.indexer.getCharPosition(from)
                val start = from - position.column
                insertAt(start, prefix)
                select(from + prefix.length, until + prefix.length)
            }
        } finally { text.endBatchEdit() }
        requestFocus()
    }

    fun select(start: Int, end: Int) {
        val left = text.indexer.getCharPosition(start.coerceIn(0, text.length))
        val right = text.indexer.getCharPosition(end.coerceIn(0, text.length))
        setSelectionRegion(left.line, left.column, right.line, right.column)
    }

    private fun insertAt(index: Int, value: CharSequence) {
        val position = text.indexer.getCharPosition(index)
        text.insert(position.line, position.column, value)
    }

    private fun beginStandaloneEdit() {
        // Finish an older IME batch before creating an independent cancellable transaction.
        restartInput()
        while (text.nestedBatchEdit > 0) text.endBatchEdit()
        text.beginBatchEdit(); text.endBatchEdit()
        text.beginBatchEdit()
    }

    /** Paste preprocessing is off Main; insertion yields between small native edits and is one undo step. */
    override fun pasteText(value: CharSequence?) {
        val doc = document ?: return
        if (value == null || doc.frozen || doc.pendingEdit?.isActive == true) return
        // Clipboard usually supplies String/SpannedString, both immutable. Copy mutable sources once.
        val incoming = if (value is String || value is android.text.SpannedString) value else value.toString()
        val left = cursor.left
        val right = cursor.right
        val version = doc.revision
        isEditable = false
        pasteProgress(0f)
        paste = scope.launch {
            var batch = false
            var modified = false
            var originalBytes: Long? = null
            var resultingBytes: Long? = null
            try {
                withContext(Dispatchers.Default) {
                    val cancellation = currentCoroutineContext()
                    val inserted = NoteEditorWindows.utf8Bytes(incoming) { cancellation.ensureActive() }
                    // Count only bounded snapshots in the worker, including multibyte selections.
                    val frozen = doc.snapshot()
                    try {
                        val original = ContinuousNoteDocument.stream(frozen).use { source ->
                            val bytes = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
                            var total = 0L
                            while (true) { cancellation.ensureActive(); val count = source.read(bytes); if (count < 0) break; total += count }
                            total
                        }
                        val selected = NoteEditorWindows.utf8Bytes(NoteTextSlice(frozen, left, right)) { cancellation.ensureActive() }
                        originalBytes = original
                        resultingBytes = original - selected + inserted
                        check(resultingBytes!! <= doc.permittedBytes) { "Note exceeds 55 MB. Split the paste or import smaller files." }
                        val runtime = Runtime.getRuntime()
                        val modelBudget = ContinuousEditorCapacity.replacementBudget(
                            ContinuousEditorCapacity.estimate(original, frozen.lineCount.toLong()),
                            runtime.maxMemory(), runtime.totalMemory() - runtime.freeMemory())
                        if (incoming.length > 8192 || right - left > 8192) {
                            val parts = listOf(
                                TextUtf8InputStream(NoteTextSlice(frozen, 0, left)),
                                TextUtf8InputStream(incoming),
                                TextUtf8InputStream(NoteTextSlice(frozen, right, frozen.length)))
                            java.io.SequenceInputStream(java.util.Collections.enumeration(parts)).use { proposed ->
                                ContinuousEditorCapacity.check(proposed, modelBudget) { cancellation.ensureActive() }
                            }
                        } else {
                            val proposedLines = frozen.lineCount.toLong() + ContinuousEditorCapacity.lineBreaks(incoming) -
                                ContinuousEditorCapacity.lineBreaks(NoteTextSlice(frozen, left, right))
                            if (ContinuousEditorCapacity.estimate(resultingBytes!!, proposedLines.coerceAtLeast(1)) > modelBudget) throw EditorCapacityException()
                        }
                    } finally { frozen.release() }
                }
                check(doc.revision == version) { "The note changed. Retry the paste." }
                beginStandaloneEdit(); batch = true
                if (right > left) { text.delete(left, right); modified = true }
                var offset = 0
                var chunks = 0
                while (offset < incoming.length) {
                    ensureActive()
                    var end = minOf(offset + 2048, incoming.length)
                    if (end < incoming.length && Character.isHighSurrogate(incoming[end - 1]) && Character.isLowSurrogate(incoming[end])) end--
                    insertAt(left + offset, incoming.subSequence(offset, end)); modified = true
                    offset = end
                    pasteProgress(offset.toFloat() / incoming.length.coerceAtLeast(1))
                    if (++chunks % 8 == 0) doc.awaitRecovery?.invoke()
                    yield()
                }
                select(left + incoming.length, left + incoming.length)
                doc.byteLength = resultingBytes
            } catch (cancel: CancellationException) {
                if (batch) { text.endBatchEdit(); batch = false }
                if (modified) text.undo()
                if (originalBytes != null) doc.byteLength = originalBytes
                throw cancel
            } catch (error: Exception) {
                if (batch) { text.endBatchEdit(); batch = false }
                if (modified) text.undo()
                if (originalBytes != null) doc.byteLength = originalBytes
                message(error.message ?: "Paste failed; original text retained.")
            } finally {
                if (batch) text.endBatchEdit()
                isEditable = !doc.frozen
                pasteProgress(null)
                if (doc.pendingEdit === currentCoroutineContext()[Job]) doc.pendingEdit = null
            }
        }.also { doc.pendingEdit = it }
    }

    fun cancelPaste() { paste?.cancel(); finding?.cancel() }

    /** Never delete a selection when Android refuses to put it on the clipboard. */
    override fun cutText() {
        if (!isEditable || document?.pendingEdit?.isActive == true) return
        var start = cursor.left
        var end = cursor.right
        if (start == end) {
            val line = cursor.leftLine
            start -= cursor.leftColumn
            end = if (line + 1 < text.lineCount) text.getCharIndex(line + 1, 0) else text.length
        }
        if (end - start > props.clipboardTextLengthLimit) { message("Selection is too large for the clipboard. Use Export to keep the complete note."); return }
        try {
            val selected = text.substring(start, end)
            clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("Cobalt", selected))
        } catch (_: RuntimeException) {
            message("Android could not copy this selection. No text was removed."); return
        }
        text.delete(start, end)
        select(start, start)
    }

    /** Stock search copies the entire document; this literal search uses a COW snapshot instead. */
    fun find(query: String, backwards: Boolean = false, replacement: String? = null, all: Boolean = false) {
        val doc = document ?: return
        if (query.isEmpty() || query.length > ContinuousTextSearch.MAX_QUERY || doc.pendingEdit?.isActive == true) return
        if (replacement != null && !isEditable) return
        if (replacement == query) {
            if (all) message("No changes needed") else find(query, backwards)
            return
        }
        finding?.cancel()
        val version = doc.revision
        val selectedMatch = cursor.right - cursor.left == query.length && query.indices.all { text[cursor.left + it] == query[it] }
        val start = if (all) 0 else if (backwards || (replacement != null && selectedMatch)) cursor.left else cursor.right
        finding = scope.launch {
            var batch = false
            var modified = false
            var applying = false
            try {
                val matches = withContext(Dispatchers.Default) {
                    val cancellation = currentCoroutineContext()
                    val frozen = doc.snapshot()
                    try {
                        val positions = if (all) ContinuousTextSearch.matches(frozen, query, checkCancelled = { cancellation.ensureActive() })
                        else if (backwards) {
                            var offset = 0
                            var found = -1
                            while (offset < minOf(start, frozen.length)) {
                                val batch = ContinuousTextSearch.matches(frozen, query, offset, 256) { cancellation.ensureActive() }
                                for (position in batch) if (position < start) found = position
                                if (batch.isEmpty() || batch.last() >= start) break
                                offset = batch.last() + query.length
                            }
                            if (found < 0) {
                                offset = 0
                                while (offset < frozen.length) {
                                    val batch = ContinuousTextSearch.matches(frozen, query, offset, 256) { cancellation.ensureActive() }
                                    if (batch.isEmpty()) break
                                    found = batch.last(); offset = found + query.length
                                }
                            }
                            if (found >= 0) listOf(found) else emptyList()
                        } else {
                            ContinuousTextSearch.matches(frozen, query, start.coerceAtMost(frozen.length), 1) { cancellation.ensureActive() }
                                .ifEmpty { ContinuousTextSearch.matches(frozen, query, 0, 1) { cancellation.ensureActive() } }
                        }
                        if (replacement != null && positions.size <= ContinuousTextSearch.MAX_REPLACEMENTS) {
                            val original = ContinuousNoteDocument.stream(frozen).use { input ->
                                val buffer = ByteArray(NoteSizePolicy.IO_CHUNK_BYTES)
                                var bytes = 0L
                                while (true) { cancellation.ensureActive(); val count = input.read(buffer); if (count < 0) break; bytes += count }
                                bytes
                            }
                            val delta = NoteEditorWindows.utf8Bytes(replacement) - NoteEditorWindows.utf8Bytes(query)
                            val proposedBytes = original + delta * positions.size
                            check(proposedBytes <= doc.permittedBytes) { "Replacement exceeds 55 MB. Narrow the search or shorten the replacement." }
                            val runtime = Runtime.getRuntime()
                            val lines = frozen.lineCount.toLong() + positions.size *
                                (ContinuousEditorCapacity.lineBreaks(replacement) - ContinuousEditorCapacity.lineBreaks(query))
                            val budget = ContinuousEditorCapacity.replacementBudget(
                                ContinuousEditorCapacity.estimate(original, frozen.lineCount.toLong()),
                                runtime.maxMemory(), runtime.totalMemory() - runtime.freeMemory())
                            if (ContinuousEditorCapacity.estimate(proposedBytes, lines.coerceAtLeast(1)) > budget) throw EditorCapacityException()
                        }
                        positions
                    } finally { frozen.release() }
                }
                if (doc.revision != version || isReleased) { message("The note changed. Run the search again."); return@launch }
                if (matches.isEmpty()) { message("No matches"); return@launch }
                if (all && matches.size > ContinuousTextSearch.MAX_REPLACEMENTS) {
                    message("More than 10,000 matches. Narrow the search or use Replace next."); return@launch
                }
                if (replacement == null) { select(matches.first(), matches.first() + query.length); return@launch }
                if (doc.frozen || paste?.isActive == true) return@launch
                isEditable = false
                applying = true
                pasteProgress(0f)
                beginStandaloneEdit(); batch = true
                for ((index, position) in matches.asReversed().withIndex()) {
                    text.replace(position, position + query.length, replacement); modified = true
                    if (index % 16 == 0) {
                        pasteProgress(index.toFloat() / matches.size)
                        doc.awaitRecovery?.invoke(); yield()
                    }
                }
                text.endBatchEdit(); batch = false
                select(matches.first(), matches.first() + replacement.length)
                message(if (all) "${matches.size} matches replaced" else "Match replaced")
            } catch (cancel: CancellationException) {
                if (batch) { text.endBatchEdit(); batch = false }
                if (modified) text.undo()
                throw cancel
            } catch (error: Exception) {
                if (batch) { text.endBatchEdit(); batch = false }
                if (modified) text.undo()
                message(error.message ?: "Search could not finish. The saved note is retained.")
            } finally {
                if (batch) text.endBatchEdit()
                if (applying) { isEditable = !doc.frozen; pasteProgress(null) }
                if (doc.pendingEdit === currentCoroutineContext()[Job]) doc.pendingEdit = null
            }
        }.also { if (replacement != null) doc.pendingEdit = it }
    }

    fun jumpToHeading(line: Int) { jumpToLine(line.coerceIn(0, text.lineCount - 1)); requestFocus() }
    override fun beginSearchMode() { onTool("find") }

    /** null means the selection needs a worker count; false means the size limit refuses it. */
    private fun acceptsInput(value: CharSequence): Boolean? {
        val doc = document ?: return false
        val bytes = doc.byteLength ?: return null
        if (cursor.right - cursor.left > 8192 || value.length > 2048) return null
        val removed = NoteEditorWindows.utf8Bytes(NoteTextSlice(text, cursor.left, cursor.right))
        val proposedBytes = bytes - removed + NoteEditorWindows.utf8Bytes(value)
        val accepted = proposedBytes <= doc.permittedBytes
        if (!accepted) message("Note exceeds 55 MB. Remove some text or split the paste.")
        val lines = text.lineCount.toLong() + ContinuousEditorCapacity.lineBreaks(value) -
            ContinuousEditorCapacity.lineBreaks(NoteTextSlice(text, cursor.left, cursor.right))
        if (ContinuousEditorCapacity.estimate(proposedBytes, lines.coerceAtLeast(1)) > ContinuousEditorCapacity.MAX_MODEL_BUDGET) {
            message("This note reached the editor memory budget. Remove text or export it; the complete note is retained.")
            return false
        }
        return accepted
    }

    override fun commitText(text: CharSequence, applyAutoIndent: Boolean, applySymbolCompletion: Boolean) {
        if (document?.frozen == true || document?.pendingEdit?.isActive == true) return
        when (acceptsInput(text)) {
            null -> pasteText(text)
            true -> super.commitText(text, applyAutoIndent, applySymbolCompletion)
            false -> Unit
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val target = super.onCreateInputConnection(outAttrs) ?: return null
        val guarded = object : InputConnectionWrapper(target, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (document?.frozen == true || document?.pendingEdit?.isActive == true) return false
                if (text != null) when (acceptsInput(text)) {
                    null -> { pasteText(text); return true }
                    false -> return false
                    true -> Unit
                }
                return super.commitText(text, newCursorPosition)
            }
            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (document?.frozen == true || document?.pendingEdit?.isActive == true) return false
                if (text != null && text.length > 4096) { message("Composition is too large. Insert it using Paste instead."); return false }
                // Composition can replace an existing composing span. This conservative bound only
                // refuses at the document ceiling; it never changes or truncates composing text.
                val known = document?.byteLength
                if (text != null && known != null && known + NoteEditorWindows.utf8Bytes(text) > document!!.permittedBytes) {
                    message("Note is at its size limit. Remove some text before composing more."); return false
                }
                return super.setComposingText(text, newCursorPosition)
            }
            override fun setComposingRegion(start: Int, end: Int): Boolean {
                if (document?.frozen == true || document?.pendingEdit?.isActive == true || kotlin.math.abs(end.toLong() - start) > 4096) return false
                return super.setComposingRegion(start, end)
            }
            override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence? =
                super.getTextBeforeCursor(length.coerceIn(0, 16_384), flags)
            override fun getTextAfterCursor(length: Int, flags: Int): CharSequence? =
                super.getTextAfterCursor(length.coerceIn(0, 16_384), flags)
        }
        return if (android.os.Build.VERSION.SDK_INT >= 33) ModernInputConnection(guarded) else guarded
    }

    @androidx.annotation.RequiresApi(33)
    private class ModernInputConnection(target: InputConnection) : InputConnectionWrapper(target, false) {
        override fun commitText(text: CharSequence, newCursorPosition: Int, textAttribute: android.view.inputmethod.TextAttribute?): Boolean =
            super.commitText(text, newCursorPosition)
        override fun setComposingText(text: CharSequence, newCursorPosition: Int, textAttribute: android.view.inputmethod.TextAttribute?): Boolean =
            super.setComposingText(text, newCursorPosition)
        @androidx.annotation.RequiresApi(34)
        override fun replaceText(start: Int, end: Int, text: CharSequence, newCursorPosition: Int,
            textAttribute: android.view.inputmethod.TextAttribute?): Boolean {
            if (!super.setSelection(start, end)) return false
            return super.commitText(text, newCursorPosition)
        }
    }
    fun dispose() {
        document?.let { doc ->
            if (doc.onFreeze === freeze) doc.onFreeze = null
            if (doc.onFormat === format) doc.onFormat = null
        }
        scope.cancel()
        release()
        document?.detachView()
        document = null
    }
}
