package com.example.ui.dock

import androidx.compose.runtime.Composable
import com.example.ui.components.CobaltSelectionMenuHost

/** A service overlay owns its menu window; it cannot depend on Activity ActionMode. */
@Composable
internal fun DockSelectionContextMenu(content: @Composable () -> Unit) {
    CobaltSelectionMenuHost(content)
}
