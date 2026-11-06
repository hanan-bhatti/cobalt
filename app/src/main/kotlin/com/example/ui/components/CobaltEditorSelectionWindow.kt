package com.example.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.positionInWindow
import com.example.R
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow

/** Keep Sora's selection/handle lifecycle, but render actions in the app's shared menu. */
internal class CobaltEditorSelectionWindow(
    private val source: CodeEditor,
    controller: CobaltSelectionController,
    var format: (String) -> Unit
) : EditorTextActionWindow(source) {
    // The superclass can call virtual methods during construction.
    private var host: CobaltSelectionController? = null
    init { host = controller }
    override fun isShowing(): Boolean = host?.owns(this) == true
    override fun dismiss() { host?.hide(this) }
    override fun displayWindow() {
        val controller = host ?: return
        if (!isEnabled || !source.isAttachedToWindow || !source.hasFocus()) return
        val location = IntArray(2)
        source.getLocationInWindow(location)
        val cursor = source.cursor
        val left = if (cursor.isSelected) source.leftHandleDescriptor.position else source.insertHandleDescriptor.position
        val right = if (cursor.isSelected) source.rightHandleDescriptor.position else left
        val top = minOf(left.top, right.top).coerceIn(0f, source.height.toFloat())
        val bottom = maxOf(left.bottom, right.bottom).coerceIn(top, source.height.toFloat())
        val bounds = Rect(location[0].toFloat(), location[1] + top,
            location[0] + source.width.toFloat(), location[1] + bottom)
        controller.show(this, { bounds.translate(-it.positionInWindow()) }, {
            buildList {
                fun action(label: String, icon: Int, run: () -> Unit) {
                    add(SelectionAction(label, icon, formatting = label == "Undo" || label == "Redo") { run(); dismiss() })
                }
                if (source.isEditable && source.cursor.isSelected) action("Cut", R.drawable.ic_custom_cut, source::cutText)
                if (source.cursor.isSelected) action("Copy", R.drawable.ic_custom_copy, source::copyText)
                if (source.isEditable) action("Paste", R.drawable.ic_custom_paste, source::pasteText)
                add(SelectionAction("Select all", R.drawable.ic_custom_select_all, run = source::selectAll))
                if (source.isEditable) {
                    addAll(noteFormattingActions(controller) { format(it); dismiss() })
                    if (source.canUndo()) action("Undo", R.drawable.ic_custom_undo) { format("undo") }
                    if (source.canRedo()) action("Redo", R.drawable.ic_custom_redo) { format("redo") }
                }
            }
        })
    }
}

internal fun CodeEditor.useCobaltSelectionMenu(controller: CobaltSelectionController?, format: (String) -> Unit) {
    if (controller == null) return
    val existing = getComponent(EditorTextActionWindow::class.java)
    if (existing is CobaltEditorSelectionWindow) existing.format = format
    else replaceComponent(EditorTextActionWindow::class.java, CobaltEditorSelectionWindow(this, controller, format))
}
