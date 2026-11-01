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

package com.example.data.remote.trakt

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit HTTP client configured for Trakt.tv API v2.
 * Supports official Trakt API as well as self-hosted / proxy endpoints.
 * Injects required API version, client ID, and dynamic Bearer tokens.
 */
object TraktClient {
    const val DEFAULT_BASE_URL = "https://api.trakt.tv/"
    private const val HTTP_TAG = "TraktHttp"

    /**
     * Optional provider for the active user's OAuth access token.
     * Supplied by [TraktAuthManager].
     */
    var tokenProvider: (() -> String?)? = null

    /**
     * Dynamic provider for Client ID (supports custom/self-hosted keys).
     */
    var clientIdProvider: (() -> String)? = null

    /**
     * Dynamic provider for Base URL (supports self-hosted mirrors/proxies).
     */
    var baseUrlProvider: (() -> String)? = null

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val apiKey = clientIdProvider?.invoke()?.takeIf { it.isNotBlank() } ?: TraktAuthManager.DEFAULT_CLIENT_ID
            val requestBuilder = original.newBuilder()
                .header("Content-Type", "application/json")
                .header("User-Agent", "Cobalt/2.2.3 (Android)")
                .header("trakt-api-version", "2")
                .header("trakt-api-key", apiKey)

            val token = tokenProvider?.invoke()
            if (!token.isNullOrBlank() && original.header("Authorization") == null) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            val request = requestBuilder.build()
            val startMs = System.currentTimeMillis()
            Log.d(HTTP_TAG, "--> ${request.method} ${request.url}")

            val response = try {
                chain.proceed(request)
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startMs
                Log.e(HTTP_TAG, "<-- HTTP FAILED: ${request.method} ${request.url} after ${elapsed}ms: ${e.message}", e)
                throw e
            }

            val durationMs = System.currentTimeMillis() - startMs
            val peekedBody = try {
                response.peekBody(2048).string()
            } catch (_: Exception) {
                "<body peek unavailable>"
            }

            if (response.isSuccessful) {
                Log.d(HTTP_TAG, "<-- ${response.code} ${request.method} ${request.url} (${durationMs}ms) [body: ${peekedBody.take(200)}]")
            } else {
                Log.w(HTTP_TAG, "<-- ${response.code} ${request.method} ${request.url} (${durationMs}ms) [error: ${peekedBody.take(300)}]")
            }

            response
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedApiService: TraktApiService? = null

    val apiService: TraktApiService
        get() {
            return cachedApiService ?: synchronized(this) {
                cachedApiService ?: buildRetrofit().create(TraktApiService::class.java).also {
                    cachedApiService = it
                }
            }
        }

    fun resetClient() {
        synchronized(this) {
            cachedApiService = null
        }
    }

    private fun buildRetrofit(): Retrofit {
        val base = baseUrlProvider?.invoke()?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
        val safeBase = if (base.endsWith("/")) base else "$base/"
        return Retrofit.Builder()
            .baseUrl(safeBase)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
}
