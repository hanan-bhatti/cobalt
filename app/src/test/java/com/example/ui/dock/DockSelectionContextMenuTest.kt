package com.example.ui.dock

import androidx.compose.foundation.text.contextmenu.data.*
import androidx.compose.foundation.text.contextmenu.provider.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DockSelectionContextMenuTest {
    @get:Rule val compose = createComposeRule()
    @Test fun overlayMenuRunsTheActualSelectionCallbackAndCloses() {
        var copies = 0
        compose.setContent { MaterialTheme {
            DockSelectionContextMenu {
                val provider = requireNotNull(LocalTextContextMenuToolbarProvider.current)
                val scope = rememberCoroutineScope()
                Button(onClick = { scope.launch {
                    provider.showTextContextMenu(object : TextContextMenuDataProvider {
                        override fun position(destinationCoordinates: LayoutCoordinates) = Offset(50f,150f)
                        override fun contentBounds(destinationCoordinates: LayoutCoordinates) = Rect(20f,100f,200f,180f)
                        override fun data() = TextContextMenuData(listOf(
                            TextContextMenuItem(TextContextMenuKeys.CopyKey,"Copy") { copies++; close() },
                            TextContextMenuItem("platform-read-aloud", "Read aloud") { error("Must be filtered out") }
                        ))
                    })
                } }) { Text("Select text") }
            }
        } }
        compose.onNodeWithText("Select text").performClick()
        compose.onNodeWithText("Read aloud").assertDoesNotExist()
        compose.onNodeWithContentDescription("Copy").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1,copies) }
        compose.onNodeWithContentDescription("Copy").assertDoesNotExist()
    }
}
