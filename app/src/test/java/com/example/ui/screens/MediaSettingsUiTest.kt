package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.media.MediaProviderSettings
import com.example.data.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaSettingsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pendingChangesHasItsOwnNavigationAndJikanRemainsReachable() {
        var pending = 0
        var account = 0
        compose.setContent { MaterialTheme { MediaSettingsScreen({}, { account++ }, {}, { pending++ }) } }
        scrollTo("Pending Trakt changes")
        compose.onNodeWithText("Pending Trakt changes").performClick()
        compose.runOnIdle { assertEquals(1, pending); assertEquals(0, account) }
        scrollTo("Save server")
        compose.onNodeWithText("Save server").assertIsDisplayed()
        scrollTo("Trakt account & sync")
        compose.onNodeWithText("Trakt account & sync").performClick()
        compose.runOnIdle { assertEquals(1, account) }
    }

    @Test fun invalidServerIsNotSavedAndImeDoneCommitsAValidUrlInAShortViewport() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("cobalt_media_providers", 0).edit().clear().commit()
        val settings = MediaProviderSettings(context)
        compose.setContent { MaterialTheme { Column(Modifier.height(330.dp).verticalScroll(rememberScrollState()).padding(16.dp)) { JikanServerSettings(settings) } } }
        compose.onNode(hasSetTextAction()).performTextReplacement("http://public.example/v4/")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithText("Use HTTPS", substring = true).assertExists()
        assertEquals(MediaProviderSettings.DEFAULT_JIKAN, settings.jikanUrl)
        compose.onNode(hasSetTextAction()).performTextReplacement("http://192.168.1.20:8080/v4")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertEquals("http://192.168.1.20:8080/v4/", settings.jikanUrl) }
        compose.onNodeWithText("Save server").performScrollTo().assertIsDisplayed()
    }

    @Test fun trailerTapCreatesAnInlinePlayerAndDoesNotStartAnExternalActivity() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        compose.setContent { MaterialTheme { com.example.ui.components.InAppTrailerPlayer("https://youtu.be/abcdefghijk") } }
        val app = org.robolectric.Shadows.shadowOf(context)
        while (app.nextStartedActivity != null) { /* Clear the test host launch. */ }
        compose.onNodeWithContentDescription("Play trailer").performClick()
        compose.onNodeWithText("Loading trailer…").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play trailer").assertIsNotEnabled()
        compose.runOnIdle { assertNull(app.nextStartedActivity) }
    }

    @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
    @Test fun settingsAndMangaCardsRenderInLightAndDarkThemes() {
        var dark by mutableStateOf(false)
        var manga by mutableStateOf(false)
        val item = SavedItem(type = SavedItemType.MEDIA, title = "Fixture Manga", content = "", mediaType = "manga",
            watchStatus = "Watching", mediaFacts = MediaFacts("Jikan", chapterCount = 42, volumeCount = 8, publicationStatus = "Publishing"))
        compose.setContent { MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme(), typography = com.example.ui.theme.Typography) {
            Surface { if (manga) Column(Modifier.padding(16.dp)) {
                MediaItemCard(item, {})
            } else MediaSettingsScreen({}, {}, {}, {}) }
        } }
        for (name in listOf("light", "dark")) {
            compose.runOnIdle { dark = name == "dark" }
            capture("settings-$name")
            scrollTo("Save server")
            capture("jikan-$name")
            compose.runOnIdle { manga = true }
            compose.onNodeWithText("Manga").assertIsDisplayed()
            compose.onNodeWithText("Reading").assertIsDisplayed()
            capture("manga-$name")
            compose.runOnIdle { manga = false }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val file = java.io.File("build/reports/media-readiness/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun scrollTo(text: String) {
        compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText(text))
    }
}
