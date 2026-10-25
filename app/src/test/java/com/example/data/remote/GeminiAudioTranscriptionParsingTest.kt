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

package com.example.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GeminiAudioTranscriptionParsingTest {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun testParseGemini35TranscribeResponse() {
        val json = """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  {
                    "audioTranscription": {
                      "text": "And so, my fellow Americans, ask not what your country can do for you, ask what you can do for your country."
                    }
                  }
                ],
                "role": "model"
              },
              "finishReason": "STOP",
              "index": 0
            }
          ],
          "usageMetadata": {
            "promptTokenCount": 276,
            "candidatesTokenCount": 26,
            "totalTokenCount": 302
          },
          "modelVersion": "gemini-3.5-transcribe"
        }
        """.trimIndent()

        val adapter = moshi.adapter(GenerateContentResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        val candidate = response?.candidates?.firstOrNull()
        assertNotNull(candidate)

        val candidateText = candidate?.content?.parts
            ?.mapNotNull { it.text ?: it.audioTranscription?.text }
            ?.joinToString("")
            ?.trim()

        assertEquals(
            "And so, my fellow Americans, ask not what your country can do for you, ask what you can do for your country.",
            candidateText
        )
    }

    @Test
    fun testParseMultimodalFlashResponse() {
        val json = """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  {
                    "text": "# Spoken Thought\n\n- Remember to buy groceries."
                  }
                ],
                "role": "model"
              },
              "finishReason": "STOP",
              "index": 0
            }
          ]
        }
        """.trimIndent()

        val adapter = moshi.adapter(GenerateContentResponse::class.java)
        val response = adapter.fromJson(json)

        val candidate = response?.candidates?.firstOrNull()
        val candidateText = candidate?.content?.parts
            ?.mapNotNull { it.text ?: it.audioTranscription?.text }
            ?.joinToString("")
            ?.trim()

        assertEquals(
            "# Spoken Thought\n\n- Remember to buy groceries.",
            candidateText
        )
    }
}
