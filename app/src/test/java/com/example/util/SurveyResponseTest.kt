package com.example.util

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SurveyResponseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun unansweredQuestionsStayUnansweredInJsonAndEmail() {
        val response = SurveyResponse.fromJson(SurveyResponse().toJson())
        assertNull(response.overall)
        assertNull(response.recommendation)
        val text = FeedbackSubmissionManager.formatSurveyEmail(response, "device", "OS", "2.2.3")
        assertEquals(6, Regex("^[1-6]\\.", RegexOption.MULTILINE).findAll(text).count())
        assertFalse(text.contains("Loving It"))
        assertFalse(text.contains("10 out of 10"))
        assertFalse(text.contains("No problems noticed"))
        assertTrue(text.contains("Not answered"))
        assertFalse(text.contains("##"))
        assertFalse(text.contains("_Not answered_"))
        assertFalse(text.contains("**"))
        assertFalse(text.contains("|"))
    }

    @Test fun lowestRatingIsExplicitAndAnswersRoundTripWithoutDefaults() {
        val response = SurveyResponse("Mixed", listOf("Notes / Markdown", "Links"),
            "Update / migration trouble", "Storage / migration", "Expected my note to open", 1, reactionScore = 1)
        assertEquals(response, SurveyResponse.fromJson(response.toJson()))
        val text = FeedbackSubmissionManager.formatSurveyEmail(response, "device", "OS", "2.2.3")
        assertTrue(text.contains("1 out of 10"))
        assertTrue(text.contains("Notes / Markdown, Links"))
        assertTrue(text.contains("Expected my note to open"))
    }

    @Test fun emailActionDirectlyAddressesTheExistingDeveloperEmailWithPlainText() {
        var launched: Intent? = null
        val capture = object : android.content.ContextWrapper(context) {
            override fun startActivity(intent: Intent) { launched = intent }
        }
        val text = FeedbackSubmissionManager.formatSurveyEmail(
            SurveyResponse(details = "Unicode 😀 & symbols +, plus a second line\nSecond line"), "Pixel", "17", "2.2.3")
        assertTrue(FeedbackSubmissionManager.launchSurveyEmail(capture, "2.2.3", text))
        val intent = requireNotNull(launched)
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto", intent.data!!.scheme)
        assertTrue(intent.data.toString().startsWith("mailto:hannanbhatti2006@gmail.com?"))
        assertArrayEquals(arrayOf("hannanbhatti2006@gmail.com"), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals(text, intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("Cobalt v2.2.3 experience survey", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        val parameters = android.net.Uri.parse("https://placeholder.invalid/?" + intent.data.toString().substringAfter('?'))
        assertEquals(text, parameters.getQueryParameter("body"))
        assertFalse(intent.hasExtra(Intent.EXTRA_STREAM))
    }

    @Test fun launchingAnEmailDoesNotCreateALocalSurveyOrClaimSubmission() {
        val prefs = context.getSharedPreferences("cobalt_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove("survey_submitted").commit()
        val capture = object : android.content.ContextWrapper(context) {
            override fun startActivity(intent: Intent) {}
        }
        assertTrue(FeedbackSubmissionManager.launchSurveyEmail(capture, "2.2.3", "response"))
        assertFalse(prefs.getBoolean("survey_submitted", false))
        assertFalse(java.io.File(context.filesDir, "feedback/survey-response.json").exists())
    }

    @Test fun missingEmailAppReturnsFailureAndDoesNotMarkTheSurveySent() {
        val failing = object : android.content.ContextWrapper(context) {
            override fun startActivity(intent: Intent) { throw android.content.ActivityNotFoundException() }
        }
        assertFalse(FeedbackSubmissionManager.launchSurveyEmail(failing, "2.2.3", "response"))
    }

    @Test fun missingJsonFieldsRemainOptional() {
        assertEquals(SurveyResponse(), SurveyResponse.fromJson(JSONObject()))
    }
}
