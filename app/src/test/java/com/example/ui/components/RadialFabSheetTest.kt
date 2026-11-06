package com.example.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w280dp-h300dp", application = android.app.Application::class)
class RadialFabSheetTest {
    @get:Rule val compose = createComposeRule()
    @Test fun allSixActionsRemainReachableInAShortWindow() {
        var picked = -1
        var dismissals = 0
        val names = listOf("Note", "Link", "Image", "Voice", "Import", "More")
        compose.setContent { MaterialTheme {
            FabActionSheet("Add to Cobalt", names.mapIndexed { index, name ->
                RadialMenuItem(R.drawable.ic_custom_plus, name, Color.Blue) { picked = index }
            }, onDismiss = { dismissals++ })
        } }
        compose.onNodeWithText("More").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(5, picked); assertEquals(1, dismissals) }
    }
}
