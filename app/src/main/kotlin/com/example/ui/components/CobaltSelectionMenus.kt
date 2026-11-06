package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.contextmenu.data.*
import androidx.compose.foundation.text.contextmenu.provider.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.R
import com.example.ui.screens.FormatType
import com.example.ui.screens.applyMarkdownFormat
import kotlinx.coroutines.*
import androidx.compose.ui.platform.LocalConfiguration
import com.example.notes.MarkdownTableEdits

internal data class SelectionAction(val label: String, val icon: Int?, val formatting: Boolean = false, val run: () -> Unit)

/** Stores callbacks and a small selection rectangle, never the selected document. */
internal class CobaltSelectionController {
    internal class Menu(val owner: Any, val bounds: (LayoutCoordinates) -> Rect,
        val actions: () -> List<SelectionAction>, val onDismiss: () -> Unit)
    internal var menu by mutableStateOf<Menu?>(null)
    var createTable by mutableStateOf<((Int, Int) -> Unit)?>(null)
    var error by mutableStateOf<String?>(null)
    fun tableCreator(apply: (String) -> Unit) {
        dismiss()
        createTable = { rows, columns -> apply("table:new:$rows:$columns") }
    }
    fun show(owner: Any, bounds: (LayoutCoordinates) -> Rect,
        actions: () -> List<SelectionAction>, onDismiss: () -> Unit = {}) {
        if (menu?.owner !== owner) dismiss()
        menu = Menu(owner, bounds, actions, onDismiss)
    }
    fun owns(owner: Any) = menu?.owner === owner
    fun hide(owner: Any) { if (owns(owner)) dismiss() }
    fun dismiss() { val old = menu; menu = null; old?.onDismiss?.invoke() }
}
internal val LocalCobaltSelectionController = staticCompositionLocalOf<CobaltSelectionController?> { null }

private fun iconFor(key: Any): Int? = when (key) {
    TextContextMenuKeys.CopyKey -> R.drawable.ic_custom_copy
    TextContextMenuKeys.CutKey -> R.drawable.ic_custom_cut
    TextContextMenuKeys.PasteKey -> R.drawable.ic_custom_paste
    TextContextMenuKeys.SelectAllKey -> R.drawable.ic_custom_select_all
    else -> null
}

/** Both Compose menu paths are supported: older text fields still use LocalTextToolbar. */
private class CobaltTextToolbar(private val controller: CobaltSelectionController,
    private val extra: () -> List<SelectionAction> = { emptyList() }) : TextToolbar {
    override val status get() = if (controller.owns(this)) TextToolbarStatus.Shown else TextToolbarStatus.Hidden
    override fun hide() = controller.hide(this)
    override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {
        val actions = listOfNotNull(
            onCutRequested?.let { SelectionAction("Cut", R.drawable.ic_custom_cut) { it(); hide() } },
            onCopyRequested?.let { SelectionAction("Copy", R.drawable.ic_custom_copy) { it(); hide() } },
            onPasteRequested?.let { SelectionAction("Paste", R.drawable.ic_custom_paste) { it(); hide() } },
            onSelectAllRequested?.let { SelectionAction("Select all", R.drawable.ic_custom_select_all, run = it) })
        controller.show(this, { rect.translate(-it.positionInRoot()) }, { actions + extra() })
    }
}
private class MenuSession : TextContextMenuSession {
    val closed = CompletableDeferred<Unit>()
    override fun close() { closed.complete(Unit) }
}
private class CobaltContextProvider(private val controller: CobaltSelectionController,
    private val extra: () -> List<SelectionAction> = { emptyList() }) : TextContextMenuProvider {
    override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
        val session = MenuSession()
        controller.show(session, dataProvider::contentBounds, {
            dataProvider.data().components.filterIsInstance<TextContextMenuItem>()
                .filter { iconFor(it.key) != null }.map { item ->
                SelectionAction(item.label, iconFor(item.key)) { item.onClick(session) }
            } + extra()
        }, session::close)
        try { session.closed.await() } finally { controller.hide(session) }
    }
}

@Composable
private fun SelectionProviders(controller: CobaltSelectionController,
    extra: () -> List<SelectionAction> = { emptyList() }, content: @Composable () -> Unit) {
    val currentExtra by rememberUpdatedState(extra)
    val toolbar = remember(controller) { CobaltTextToolbar(controller) { currentExtra() } }
    val provider = remember(controller) { CobaltContextProvider(controller) { currentExtra() } }
    CompositionLocalProvider(LocalTextToolbar provides toolbar,
        LocalTextContextMenuToolbarProvider provides provider,
        LocalTextContextMenuDropdownProvider provides provider, content = content)
}

/** Add formatting only to note bodies; titles/search fields keep clipboard actions. */
@Composable
internal fun CobaltSelectionFormattingScope(value: TextFieldValue, onChange: (TextFieldValue) -> Unit,
    content: @Composable () -> Unit) {
    val controller = LocalCobaltSelectionController.current
    if (controller == null) { content(); return }
    val apply = rememberNoteFormattingCommand(value, onChange)
    SelectionProviders(controller, extra = { noteFormattingActions(controller, apply) }, content = content)
}

@Composable
private fun rememberNoteFormattingCommand(value: TextFieldValue, onChange: (TextFieldValue) -> Unit): (String) -> Unit {
    val controller = LocalCobaltSelectionController.current
    val scope = rememberCoroutineScope()
    val latestValue by rememberUpdatedState(value)
    val latestChange by rememberUpdatedState(onChange)
    var busy by remember { mutableStateOf(false) }
    return { command ->
            val original = latestValue
            if (command.startsWith("table:")) {
                if (!busy) {
                    busy = true
                    scope.launch {
                        try {
                            val edit = withContext(Dispatchers.Default) {
                                MarkdownTableEdits.plan(original.text.length, original.selection.min, command,
                                    { start, count -> original.text.substring(start, start + count) })
                            }
                            check(latestValue.text == original.text) { "The note changed. Try the table action again." }
                            latestChange(TextFieldValue(original.text.replaceRange(edit.start, edit.end, edit.text), TextRange(edit.caret)))
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (failure: Exception) { controller?.error = failure.message ?: "Table action could not finish. No text was changed." }
                        finally { busy = false }
                    }
                }
            } else {
                val normalized = original.copy(selection = TextRange(original.selection.min, original.selection.max))
                applyMarkdownFormat(normalized, FormatType.valueOf(command), latestChange)
            }
            controller?.dismiss()
    }
}

internal fun noteFormattingActions(controller: CobaltSelectionController, apply: (String) -> Unit): List<SelectionAction> {
    val formats = listOf(
        Triple("Bold", R.drawable.ic_format_bold, FormatType.Bold),
        Triple("Italic", R.drawable.ic_format_italic, FormatType.Italic),
        Triple("Strikethrough", R.drawable.ic_format_strikethrough, FormatType.Strikethrough),
        Triple("Heading 1", R.drawable.ic_format_h1, FormatType.Heading1),
        Triple("Heading 2", R.drawable.ic_format_h2, FormatType.Heading2),
        Triple("Heading 3", R.drawable.ic_format_h3, FormatType.Heading3),
        Triple("Bulleted list", R.drawable.ic_format_list_bullet, FormatType.BulletList),
        Triple("Numbered list", R.drawable.ic_format_list_numbered, FormatType.NumberedList),
        Triple("Checklist", R.drawable.ic_format_checklist, FormatType.Checklist),
        Triple("Quote", R.drawable.ic_format_quote, FormatType.Quote),
        Triple("Inline code", R.drawable.ic_format_code_inline, FormatType.CodeInline),
        Triple("Code block", R.drawable.ic_format_code_block, FormatType.CodeBlock),
        Triple("Link", R.drawable.ic_format_link, FormatType.Link)
    ).map { (label, icon, format) -> SelectionAction(label, icon, formatting = true) { apply(format.name) } }
    return formats + listOf(
        SelectionAction("Insert table", R.drawable.ic_format_table, formatting = true) { controller.tableCreator(apply) },
        SelectionAction("Add row below", R.drawable.ic_format_table_row, formatting = true) { apply("table:row") },
        SelectionAction("Add column right", R.drawable.ic_format_table_column, formatting = true) { apply("table:column") },
        SelectionAction("Next cell", null, formatting = true) { apply("table:next-cell") },
        SelectionAction("Align column left", null, formatting = true) { apply("table:left") },
        SelectionAction("Align column center", null, formatting = true) { apply("table:center") },
        SelectionAction("Align column right", null, formatting = true) { apply("table:right") },
        SelectionAction("Remove row", R.drawable.ic_format_table_row, formatting = true) { apply("table:remove-row") },
        SelectionAction("Remove column", R.drawable.ic_format_table_column, formatting = true) { apply("table:remove-column") }
    )
}

@Composable
internal fun CobaltSelectionMenuHost(content: @Composable () -> Unit) {
    val controller = remember { CobaltSelectionController() }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    DisposableEffect(controller) { onDispose { controller.dismiss() } }
    CompositionLocalProvider(LocalCobaltSelectionController provides controller) {
        SelectionProviders(controller) {
            Box(Modifier.onGloballyPositioned { coordinates = it }, propagateMinConstraints = true) {
                content()
                val menu = controller.menu
                val anchor = coordinates?.takeIf { it.isAttached }
                if (menu != null && anchor != null) {
                    val selection = menu.bounds(anchor)
                    val position = remember(selection, margin) {
                        object : PopupPositionProvider {
                            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                                layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                                val x = anchorBounds.left + selection.center.x.toInt() - popupContentSize.width / 2
                                val above = anchorBounds.top + selection.top.toInt() - popupContentSize.height - margin
                                val y = if (above >= margin) above else anchorBounds.top + selection.bottom.toInt() + margin
                                return IntOffset(x.coerceIn(margin, (windowSize.width-popupContentSize.width-margin).coerceAtLeast(margin)),
                                    y.coerceIn(margin, (windowSize.height-popupContentSize.height-margin).coerceAtLeast(margin)))
                            }
                        }
                    }
                    Popup(popupPositionProvider = position, onDismissRequest = controller::dismiss,
                        properties = PopupProperties(focusable = false)) {
                        SelectionMenuContent(menu.actions())
                    }
                }
            }
        }
    }
    BackHandler(enabled = controller.menu != null, onBack = controller::dismiss)
    controller.createTable?.let { create ->
        var rows by remember(create) { mutableIntStateOf(3) }
        var columns by remember(create) { mutableIntStateOf(3) }
        AlertDialog(onDismissRequest = { controller.createTable = null },
            title = { Text("Insert table") },
            text = { Column {
                Text("Choose the number of body rows and columns. You can add more later.")
                TableDimension("Rows", rows, 20) { rows = it }
                TableDimension("Columns", columns, MarkdownTableEdits.MAX_COLUMNS) { columns = it }
            } },
            confirmButton = { TextButton(onClick = { controller.createTable = null; create(rows, columns) }) { Text("Insert") } },
            dismissButton = { TextButton(onClick = { controller.createTable = null }) { Text("Cancel") } })
    }
    controller.error?.let { error -> AlertDialog(onDismissRequest = { controller.error = null },
        title = { Text("Table unchanged") }, text = { Text(error) },
        confirmButton = { TextButton(onClick = { controller.error = null }) { Text("OK") } }) }
}

@Composable
private fun TableDimension(label: String, value: Int, maximum: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        TextButton(onClick = { onChange(value - 1) }, enabled = value > 1) { Text("−") }
        Text(value.toString())
        TextButton(onClick = { onChange(value + 1) }, enabled = value < maximum) { Text("+") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionActionIcon(action: SelectionAction) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
        positioning = TooltipAnchorPosition.Above), tooltip = { PlainTooltip { Text(action.label) } },
        state = rememberTooltipState()) {
        IconButton(onClick = action.run, modifier = Modifier.size(48.dp)) {
            Icon(painterResource(requireNotNull(action.icon)), contentDescription = action.label, modifier = Modifier.size(21.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactTableAction(action: SelectionAction, enabled: Boolean) {
    TooltipBox(positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
        positioning = TooltipAnchorPosition.Above), tooltip = { PlainTooltip { Text(action.label) } },
        state = rememberTooltipState()) {
        Surface(onClick = action.run, enabled = enabled, shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                Icon(painterResource(requireNotNull(action.icon)), action.label,
                    Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Shared by the keyboard accessory bars and the floating selection menu. */
@Composable
internal fun CobaltTableToolbarActions(value: TextFieldValue, onChange: (TextFieldValue) -> Unit,
    onNativeCommand: ((String) -> Unit)? = null, enabled: Boolean = true) {
    val controller = LocalCobaltSelectionController.current ?: return
    val composeCommand = rememberNoteFormattingCommand(value, onChange)
    val apply = onNativeCommand ?: composeCommand
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            SelectionAction("Insert table", R.drawable.ic_format_table) { controller.tableCreator(apply) },
            SelectionAction("Add row below", R.drawable.ic_format_table_row) { apply("table:row") },
            SelectionAction("Add column right", R.drawable.ic_format_table_column) { apply("table:column") }
        ).forEach { action -> CompactTableAction(action, enabled) }
    }
}

@Composable
internal fun SelectionMenuContent(actions: List<SelectionAction>) {
    val maximumWidth = (LocalConfiguration.current.screenWidthDp - 24).coerceIn(120, 400)
    val slots = ((maximumWidth - 8) / 48).coerceAtLeast(2)
    val preferred = actions.filter { !it.formatting || it.label == "Bold" || it.label == "Italic" }
    val needsMore = actions.size > slots || actions.any { it !in preferred }
    val primary = preferred.take(if (needsMore) slots - 1 else slots)
    val additional = actions.filter { it !in primary }
    var expanded by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Row(Modifier.padding(4.dp)) {
            primary.forEach { action -> SelectionActionIcon(action) }
            if (additional.isNotEmpty()) Box {
                SelectionActionIcon(SelectionAction("More", R.drawable.ic_custom_more_vert) { expanded = !expanded })
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = false),
                    shape = RoundedCornerShape(20.dp), modifier = Modifier.heightIn(max = 360.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    additional.forEach { action ->
                        DropdownMenuItem(text = { Text(action.label) },
                            leadingIcon = action.icon?.let { icon -> { Icon(painterResource(icon), null, Modifier.size(21.dp)) } },
                            onClick = { expanded = false; action.run() })
                    }
                }
            }
        }
    }
}
