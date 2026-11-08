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

import com.example.util.SafeLogger as Log
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

    var retryNotBeforeProvider: (() -> Long)? = null
    var saveRetryNotBefore: ((Long) -> Unit)? = null
    private val pacingLock = Any()
    private var lastRequestNanos = 0L
    private fun waitForRequest(method: String) {
        val remaining = (retryNotBeforeProvider?.invoke() ?: 0L) - System.currentTimeMillis()
        if (remaining > 20_000) throw java.io.IOException("Trakt rate limit: sync will resume later.")
        fun pause(millis: Long) {
            if (millis <= 0) return
            try { Thread.sleep(millis) }
            catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw java.io.InterruptedIOException("Trakt request cancelled")
            }
        }
        pause(remaining)
        synchronized(pacingLock) {
            val spacing = if (method == "GET") 650L else 1100L
            val elapsed = (System.nanoTime() - lastRequestNanos) / 1_000_000
            pause(spacing - elapsed)
            lastRequestNanos = System.nanoTime()
        }
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        // A lost POST response must not be retried blindly: watch-history adds are not idempotent.
        .retryOnConnectionFailure(false)
        .addInterceptor { chain ->
            val original = chain.request()
            val oauth = original.url.encodedPath.startsWith("/oauth/")
            val url = if (oauth && original.url.host == "api.trakt.tv")
                original.url.newBuilder().host("auth.trakt.tv").build() else original.url
            val builder = original.newBuilder().url(url)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Cobalt/2.2.3 (Android)")
                .header("trakt-api-version", "2")
                .header("trakt-api-key", clientIdProvider?.invoke()?.takeIf { it.isNotBlank() } ?: TraktAuthManager.DEFAULT_CLIENT_ID)
            val token = tokenProvider?.invoke()
            if (!oauth && !token.isNullOrBlank() && original.header("Authorization") == null) builder.header("Authorization", "Bearer $token")
            val request = builder.build()
            var attempt = 0
            while (true) {
                if (chain.call().isCanceled()) throw java.io.InterruptedIOException("Trakt request cancelled")
                waitForRequest(request.method)
                val response = try { chain.proceed(request) }
                catch (failure: java.io.IOException) {
                    if (request.method != "GET" || ++attempt >= 3 || chain.call().isCanceled()) throw failure
                    continue
                }
                // Do not log URLs, OAuth response bodies, user history or tokens.
                Log.d(HTTP_TAG, "Trakt HTTP status ${response.code}")
                if (response.code == 429) {
                    val seconds = response.header("Retry-After")?.toLongOrNull()?.coerceAtLeast(1)
                        ?: (2L shl attempt.coerceAtMost(4)) + kotlin.random.Random.nextLong(1, 4)
                    val until = System.currentTimeMillis() + seconds.coerceAtMost(Long.MAX_VALUE / 2000) * 1000
                    saveRetryNotBefore?.invoke(until)
                    if (request.method == "GET" && seconds <= 20 && ++attempt < 3) {
                        response.close()
                        continue
                    }
                }
                // Return the final response OPEN, including 429, so Retrofit preserves its error.
                return@addInterceptor response
            }
            @Suppress("UNREACHABLE_CODE")
            throw java.io.IOException("Trakt request did not finish")
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
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
