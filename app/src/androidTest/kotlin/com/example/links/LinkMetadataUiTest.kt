package com.example.links

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.MainActivity
import com.example.data.model.SavedItemType
import com.example.ui.viewmodel.CobaltViewModel
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Explicit live UI check: replace the URL through the field's semantics, without using clipboard. */
@RunWith(AndroidJUnit4::class)
class LinkMetadataUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun publicPreviewsInCapture() {
        assumeTrue("Explicit opt-in UI probe", InstrumentationRegistry.getArguments().getString("links_ui") == "yes")
        lateinit var model: CobaltViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[CobaltViewModel::class.java] }
        val samples = linkedMapOf(
            "instagram" to "https://www.instagram.com/reel/DdRyQxKteC1/",
            "tiktok" to "https://www.tiktok.com/@scout2015/video/6718335390845095173",
            "youtube" to "https://www.youtube.com/shorts/k5Z7sFwFF7Q"
        )
        val report = JSONArray()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            for ((name, url) in samples) {
                compose.activityRule.scenario.onActivity { model.startManualCapture(SavedItemType.LINK) }
                compose.waitUntil(10_000) { compose.onAllNodesWithTag("capture_content_input").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("capture_content_input").performTextReplacement(url)
                compose.onNodeWithTag("capture_content_input").performImeAction()
                compose.waitUntil(25_000) { !model.isMetadataExtracting.value && !model.activeCaptureItem.value?.linkTitle.isNullOrBlank() }
                val item = requireNotNull(model.activeCaptureItem.value)
                report.put(JSONObject().put("type", name).put("url", item.content).put("title", item.linkTitle)
                    .put("description", item.linkDescription).put("image", item.linkImage))
                File(instrumentation.targetContext.filesDir, "metadata-ui-results.json").writeText(report.toString(2))
                // These photo/video covers must paint, rather than merely supply an image URL.
                compose.waitUntil(30_000) {
                    val pixels = compose.onNodeWithContentDescription(requireNotNull(item.linkTitle), useUnmergedTree = true)
                        .captureToImage().toPixelMap()
                    val middle = pixels[pixels.width / 2, pixels.height / 2]
                    (1..5).any { x -> (1..5).any { y -> pixels[x * pixels.width / 6, y * pixels.height / 6] != middle } }
                }
                compose.waitForIdle()
                val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                File(instrumentation.targetContext.filesDir, "metadata-ui-$name.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
                assertEquals(url, item.content)
                assertFalse("Missing $name thumbnail (${item.linkTitle})", item.linkImage.isNullOrBlank())
                assertFalse("Missing $name description (${item.linkTitle})", item.linkDescription.isNullOrBlank())
                compose.activityRule.scenario.onActivity { model.cancelCapture() }
                compose.waitForIdle()
            }
        } finally { compose.activityRule.scenario.onActivity { model.cancelCapture() } }
    }
}
