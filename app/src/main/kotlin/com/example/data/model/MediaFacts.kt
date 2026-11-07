package com.example.data.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

/** Small locally cached catalogue facts, distinct from personal watch progress. */
@JsonClass(generateAdapter = true)
data class MediaFacts(val source: String, val sourceUrl: String? = null, val episodeCount: Int? = null,
    val chapterCount: Int? = null, val volumeCount: Int? = null, val publicationStatus: String? = null) {
    fun toJson(): String = adapter.toJson(this)
    companion object {
        private val adapter by lazy { Moshi.Builder().build().adapter(MediaFacts::class.java) }
        fun fromJson(json: String?): MediaFacts? = try { json?.let(adapter::fromJson) } catch (_: Exception) { null }
    }
}
