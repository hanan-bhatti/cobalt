package com.example.ui.components

import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.ui.theme.MyApplicationTheme
import com.example.util.SurveyResponse
import com.example.util.FeedbackSubmissionManager
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp", application = android.app.Application::class)
class SurveySheetContentTest {
    @get:Rule val rule = createComposeRule()
    private var answer = SurveyResponse()
    private var emailRequests = 0

    private fun content(dark: Boolean = false, step: Int = 1) {
        rule.setContent {
            var response by remember { mutableStateOf(SurveyResponse()) }
            MyApplicationTheme(darkTheme = dark, dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                SurveySheetContent(response, { response = it; answer = it }, "2.2.3",
                    FeedbackSubmissionManager.formatSurveyEmail(response, "Pixel", "17", "2.2.3"),
                    onDismissRequest = {}, onSendEmail = { emailRequests++ }, onCopy = { true }, initialStep = step)
                }
            }
        }
    }

    @Test fun reactionSliderStartsUnansweredAndCanBeAdjustedAndCleared() {
        content()
        assertNull(answer.reactionScore)
        rule.onNodeWithTag("survey_reaction_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
        assertEquals(10, answer.reactionScore)
        assertEquals("Very well", answer.overall)
        rule.onNodeWithTag("survey_reaction_slider_clear").performClick()
        assertNull(answer.reactionScore)
        assertNull(answer.overall)
    }

    @Test fun tappingTheFirstSliderPositionIsAnExplicitAnswer() {
        content()
        rule.onNodeWithTag("survey_reaction_slider").performTouchInput { click(Offset(1f, centerY)) }
        assertEquals(1, answer.reactionScore)
        assertEquals("Very poorly", answer.overall)
    }

    @Test fun usedAreasAllowMultipleAnswersAndSurviveStepNavigation() {
        content()
        rule.onNodeWithText("Notes").performScrollTo().performClick()
        rule.onNodeWithText("Links").performClick()
        assertEquals(listOf("Notes / Markdown", "Links"), answer.areasUsed)
        rule.onNodeWithTag("survey_next_step_button").performClick()
        rule.onNodeWithTag("survey_send_email_button").assertIsDisplayed()
        rule.onNodeWithContentDescription("Previous step").performClick()
        rule.onNodeWithText("Notes").performScrollTo().assertIsSelected()
        rule.onNodeWithText("Links").assertIsSelected()
    }

    @Test fun emailIsDirectAndDoesNotRequireSavingOrARating() {
        content(step = 2)
        rule.onNodeWithTag("survey_send_email_button").assertIsDisplayed().performClick()
        assertEquals(1, emailRequests)
        assertNull(answer.recommendation)
        rule.onNodeWithText("Save response on this device").assertDoesNotExist()
    }

    @Test fun recommendationSliderUsesOneToTenAndCanBeCleared() {
        content(step = 2)
        rule.onNodeWithTag("survey_recommendation_slider").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        assertEquals(1, answer.recommendation)
        rule.onNodeWithTag("survey_recommendation_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
        assertEquals(10, answer.recommendation)
        rule.onNodeWithTag("survey_recommendation_slider_clear").performClick()
        assertNull(answer.recommendation)
    }

    @Test fun feedbackTextAndSinglePriorityArePreserved() {
        content(dark = true, step = 2)
        rule.onNodeWithText("Editing & saving").performClick()
        rule.onNodeWithText("Speed & stability").performClick()
        assertEquals("Speed / stability", answer.improvementArea)
        rule.onNodeWithTag("survey_details_input").performScrollTo().performTextInput("Synthetic UI test feedback")
        assertEquals("Synthetic UI test feedback", answer.details)
        rule.onNodeWithTag("survey_send_email_button").assertIsDisplayed()
    }
    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String) {
        val file = File("build/reports/survey-ui/$name.png").absoluteFile
        file.parentFile!!.mkdirs()
        rule.onRoot().captureRoboImage(file = file, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        assertTrue("The survey must produce a real review image", file.length() > 1000)
    }

    @Test fun lightExperiencePageRender() {
        content()
        capture("light-experience")
    }

    @Test fun darkIdeasPageRender() {
        content(dark = true, step = 2)
        rule.onNodeWithTag("survey_send_email_button").assertIsDisplayed()
        capture("dark-ideas")
    }

}
