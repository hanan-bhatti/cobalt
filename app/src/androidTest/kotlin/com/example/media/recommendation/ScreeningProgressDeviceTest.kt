package com.example.media.recommendation

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.model.*
import com.example.ui.components.MediaActivityContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ScreeningProgressDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun movieActivityDisplaysRealFractionalPlaybackAndRemovesUnknownProgress() {
        var progress by mutableStateOf<Double?>(35.5)
        val item = SavedItem(id = "isolated-movie-ui-fixture",type = SavedItemType.MEDIA,title = "Movie details",content = "",
            mediaType = "movie",traktId = 42,watchStatus = "Watching")
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(),typography = com.example.ui.theme.Typography) {
                Surface { Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("Movie details",style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(24.dp))
                    MediaActivityContent(item,false,{}, {}, {}, {}, {},playbackProgress = progress)
                } }
            }
        }
        compose.onNodeWithText("35.5%").assertIsDisplayed()
        compose.onNodeWithText("Saved playback position from Trakt").assertIsDisplayed()
        compose.onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
            androidx.compose.ui.semantics.ProgressBarRangeInfo(.355f,0f..1f))).assertExists()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir,"movie-details-progress.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
        compose.runOnIdle { progress = null }; compose.waitForIdle()
        compose.onNodeWithText("Watch progress").assertDoesNotExist()
        compose.onNodeWithText("Watching").assertIsDisplayed()
    }
}
