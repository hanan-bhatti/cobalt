package com.example.util

import org.json.JSONArray
import org.json.JSONObject

/** Missing answers remain missing, including the optional recommendation score. */
data class SurveyResponse(
    val overall: String? = null,
    val areasUsed: List<String> = emptyList(),
    val updateExperience: String? = null,
    val improvementArea: String? = null,
    val details: String = "",
    val recommendation: Int? = null,
    val contact: String = "",
    val reactionScore: Int? = null
) {
    init {
        require(recommendation == null || recommendation in 1..10)
        require(reactionScore == null || reactionScore in 1..10)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", 2)
        put("overall", overall ?: JSONObject.NULL)
        put("reactionScore", reactionScore ?: JSONObject.NULL)
        put("areasUsed", JSONArray(areasUsed))
        put("updateExperience", updateExperience ?: JSONObject.NULL)
        put("improvementArea", improvementArea ?: JSONObject.NULL)
        put("details", details)
        put("recommendation", recommendation ?: JSONObject.NULL)
        put("contact", contact)
    }

    companion object {
        fun fromJson(json: JSONObject): SurveyResponse = SurveyResponse(
            overall = json.nullableString("overall"),
            areasUsed = json.optJSONArray("areasUsed")?.let { array ->
                (0 until array.length()).map { array.getString(it) }
            }.orEmpty(),
            updateExperience = json.nullableString("updateExperience"),
            improvementArea = json.nullableString("improvementArea"),
            details = json.optString("details"),
            recommendation = if (json.isNull("recommendation")) null else json.getInt("recommendation").takeIf { it in 1..10 },
            contact = json.optString("contact"),
            reactionScore = if (json.isNull("reactionScore")) null else json.getInt("reactionScore").takeIf { it in 1..10 }
        )

        private fun JSONObject.nullableString(key: String): String? =
            if (isNull(key)) null else getString(key)
    }
}

internal fun surveyReactionLabel(score: Int): String = when (score) {
    in 1..2 -> "Very poorly"
    in 3..4 -> "Poorly"
    in 5..6 -> "Mixed"
    in 7..8 -> "Well"
    else -> "Very well"
}

internal fun surveyReactionEmoji(score: Int): String = when (score) {
    in 1..2 -> "😞"
    in 3..4 -> "😕"
    in 5..6 -> "😐"
    in 7..8 -> "🙂"
    else -> "😊"
}
