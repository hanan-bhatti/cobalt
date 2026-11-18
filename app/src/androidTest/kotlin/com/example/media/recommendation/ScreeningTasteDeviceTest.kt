package com.example.media.recommendation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.ui.screens.ScreeningTasteContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScreeningTasteDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sixtyGenresStayAccessibleThroughOthersAtLargeText() {
        val now = System.currentTimeMillis()
        val item = SavedItem("genre-device-fixture", SavedItemType.MEDIA, "Genre evidence fixture", "", mediaType = "movie",
            watchStatus = "Plan to Watch", userRating = 9, genres = (1..60).map { "Genre ${it.toString().padStart(2, '0')}" })
        val signals = ScreeningSignals()
        val profile = ScreeningTaste.profile(listOf(item), signals, now)
        val data = ScreeningInsights(listOf(item), signals, profile,
            ScreeningRecommendationEngine.rank(listOf(item), signals, now, includeCooldowns = false, profile = profile),
            emptyList(), emptyList())
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                MaterialTheme(colorScheme = darkColorScheme(), typography = com.example.ui.theme.Typography) {
                    Surface { Box(Modifier.width(320.dp)) { ScreeningTasteContent(data, onBack = {}) } }
                }
            }
        }
        compose.onNodeWithText("Genre 06").assertDoesNotExist()
        scrollTo("Others")
        compose.onNodeWithText("55").assertIsDisplayed()
        compose.onNodeWithText("Others").performClick()
        compose.onNodeWithText("Other genres").assertIsDisplayed()
        scrollTo("Genre 60")
        compose.onNodeWithText("Genre 60").assertIsDisplayed().performClick()
        compose.onNodeWithText("Learned weight").assertDoesNotExist()
        compose.onNodeWithText("Learning details").performScrollTo().performClick()
        compose.onNodeWithText("Learned weight").assertExists()
        compose.onNodeWithText("Your rating 9/10").assertExists()
        compose.onNodeWithTag("taste_evidence_rating:${ScreeningRecommendationEngine.key(item)}").performClick()
        compose.onNodeWithText("Date not recorded", substring = true).assertExists()
        compose.onNodeWithText("Ignore rating").assertExists()
        compose.onNodeWithText("Ignore rating").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("What this changes").assertExists()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "screening-taste-device-evidence.png").outputStream().use {
            compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun scrollTo(text: String) = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        .performScrollToNode(hasText(text))
}
