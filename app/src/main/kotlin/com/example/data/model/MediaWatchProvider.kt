/*
 * Cobalt - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.data.model

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class MediaWatchProvider(
    val name: String,
    val type: String = "Stream", // "Stream", "ADS Stream", "Buy", "Rent"
    val price: String? = null,
    val logoUrl: String? = null,
    val providerId: Int? = null,
    val url: String? = null
) {
    companion object {
        private val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        private val listType = Types.newParameterizedType(List::class.java, MediaWatchProvider::class.java)
        private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
        private val listAdapter = moshi.adapter<List<MediaWatchProvider>>(listType)
        private val stringListAdapter = moshi.adapter<List<String>>(stringListType)

        fun listToJson(providers: List<MediaWatchProvider>): String {
            return listAdapter.toJson(providers)
        }

        fun listFromJson(json: String?): List<MediaWatchProvider> {
            if (json.isNullOrBlank() || json.trim() == "[]") return emptyList()
            // Try parsing as list of MediaWatchProvider objects
            try {
                val parsed = listAdapter.fromJson(json)
                if (!parsed.isNullOrEmpty() && parsed.first().name.isNotBlank()) {
                    return parsed
                }
            } catch (_: Exception) {}

            // Fallback: Try parsing as legacy string list
            try {
                val stringList = stringListAdapter.fromJson(json)
                if (!stringList.isNullOrEmpty()) {
                    return stringList.filter { it.isNotBlank() }.map { MediaWatchProvider(name = it, type = "Stream") }
                }
            } catch (_: Exception) {}

            return emptyList()
        }
    }
}
