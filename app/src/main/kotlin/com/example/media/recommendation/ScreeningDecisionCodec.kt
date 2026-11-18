package com.example.media.recommendation

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Small decision records contain ranking facts, never synopsis, notes, or provider credentials. */
object ScreeningDecisionCodec {
    fun encode(trace: ScreeningScoreTrace): JSONObject = JSONObject().apply {
        put("features", JSONObject(trace.features)); put("raw", trace.rawPoints)
        put("variety", trace.varietyAdjustment); put("utility", trace.selectionPoints)
        put("shortlist", JSONArray(trace.shortlist.map { JSONObject().put("key", it.key).put("weight", it.weight) }))
        put("pool", trace.poolSize); put("at", trace.generatedAt); put("version", trace.modelVersion)
    }

    fun decode(value: JSONObject?): ScreeningScoreTrace? = runCatching {
        val json = value ?: return null
        val data = json.getJSONObject("features")
        require(data.length() in 1..20)
        val features = data.keys().asSequence().associateWith { data.getDouble(it) }
        require(features.values.all(Double::isFinite))
        val raw = json.getDouble("raw"); val variety = json.getDouble("variety"); val utility = json.getDouble("utility")
        require(raw.isFinite() && variety.isFinite() && utility.isFinite())
        require(abs(features.values.sum() - raw) < .000001 && abs(raw + variety - utility) < .000001)
        val array = json.optJSONArray("shortlist") ?: JSONArray()
        require(array.length() <= 8)
        val shortlist = (0 until array.length()).map { index -> array.getJSONObject(index).let {
            ScreeningSelectionWeight(it.getString("key"), it.getDouble("weight"))
        } }
        require(shortlist.all { it.weight.isFinite() && it.weight >= 1.0 })
        ScreeningScoreTrace(features, raw, variety, utility, shortlist, json.optInt("pool").coerceIn(0, 5000),
            json.getLong("at"), json.getString("version"))
    }.getOrNull()
}
