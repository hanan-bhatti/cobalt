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

package com.example.util

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object GoogleApiErrorMapper {

    /**
     * Maps an HTTP status code or Google API error reason to a concise, friendly 1-line message.
     * Grounded in official Google documentation:
     * - https://ai.google.dev/gemini-api/docs/troubleshooting
     * - https://cloud.google.com/apis/design/errors
     */
    fun getMessageForHttpCode(code: Int): String {
        return when (code) {
            400 -> "Invalid request or region. Please select the area again."
            401 -> "Invalid or expired Gemini API key. Please update it in Settings."
            402 -> "API credit balance depleted. Please add credits to your AI Studio account."
            403 -> "Access denied. Check your Gemini API key permissions in AI Studio."
            404 -> "Selected AI model was not found. Try choosing another model in Settings."
            408 -> "Request timed out. Please check your network and try again."
            409 -> "Request was interrupted. Please try scanning again."
            413 -> "Selected image area is too large. Try selecting a smaller region."
            429 -> "Usage limit reached. Please wait a moment before scanning again."
            499 -> "Extraction was cancelled."
            500 -> "Google AI encountered a temporary glitch. Please try again."
            502 -> "Network gateway error. Please try again in a few seconds."
            503 -> "Gemini servers are temporarily overloaded. Please try again shortly."
            504 -> "Server response deadline exceeded. Try selecting a smaller region."
            else -> "Google AI service error (code $code). Please try again shortly."
        }
    }

    /**
     * Parses an arbitrary raw error string (e.g. "Error: HTTP 402", "HTTP 429", "RESOURCE_EXHAUSTED", etc.)
     * and maps it to a concise, user-friendly 1-line message.
     */
    fun formatUserFriendlyError(rawError: String?): String {
        if (rawError.isNullOrBlank()) {
            return "Unable to read this region. Please try again."
        }

        val text = rawError.trim()
        val stripped = text.removePrefix("Error:").removePrefix("Error :").trim()

        // 1. Direct check for HTTP status codes embedded in error messages (e.g. "Error: HTTP 402", "HTTP 429 Too Many Requests")
        val httpMatch = Regex("""\b(?:HTTP\s*|code\s*[:=]?\s*)(\d{3})\b""", RegexOption.IGNORE_CASE).find(text)
        if (httpMatch != null) {
            val code = httpMatch.groupValues[1].toIntOrNull()
            if (code != null) {
                return getMessageForHttpCode(code)
            }
        }

        // Standalone numbers that match HTTP error codes (e.g. "402", "429", "403")
        val standaloneCode = text.filter { it.isDigit() }.toIntOrNull()
        if (standaloneCode != null && standaloneCode in 400..599) {
            return getMessageForHttpCode(standaloneCode)
        }

        // If it was already formatted as one of our friendly messages, return it directly without the "Error:" prefix
        if (stripped.endsWith(".") && !stripped.contains("Exception") && !stripped.contains("HTTP") && !stripped.contains("{") && stripped.length > 20) {
            return stripped
        }

        // 2. Google Canonical / gRPC status reason names
        val upper = text.uppercase()
        return when {
            upper.contains("API KEY MISSING") || upper.contains("PLEASE SAVE A VALID GEMINI API KEY") ->
                "Gemini API key is missing. Please configure your key in Settings."
            upper.contains("FAILED_PRECONDITION") ->
                "Gemini is unavailable in this region without an enabled billing account."
            upper.contains("UNAUTHENTICATED") || upper.contains("INVALID_API_KEY") ->
                "Invalid or expired Gemini API key. Please update it in Settings."
            upper.contains("PAYMENT_REQUIRED") || upper.contains("CREDIT") ->
                "API credit balance depleted. Please add credits to your AI Studio account."
            upper.contains("PERMISSION_DENIED") ->
                "Access denied. Check your Gemini API key permissions in AI Studio."
            upper.contains("RESOURCE_EXHAUSTED") || upper.contains("RATE_LIMIT") || upper.contains("QUOTA") ->
                "Usage limit reached. Please wait a moment before scanning again."
            upper.contains("NOT_FOUND") || (upper.contains("MODEL") && upper.contains("NOT FOUND")) ->
                "Selected AI model was not found. Try choosing another model in Settings."
            upper.contains("DEADLINE_EXCEEDED") || upper.contains("TIMEOUT") ->
                "Connection timed out. Please check your network and try again."
            upper.contains("UNAVAILABLE") || upper.contains("OVERLOADED") ->
                "Gemini servers are temporarily overloaded. Please try again shortly."
            upper.contains("INTERNAL") ->
                "Google AI encountered a temporary glitch. Please try again."
            upper.contains("SAFETY") || upper.contains("BLOCKED") || upper.contains("HARM_CATEGORY") ->
                "Selected content could not be read due to safety policy filters."
            upper.contains("EMPTY") || upper.contains("UNABLE TO READ") ->
                "No readable text or links found. Try selecting a clearer region."
            upper.contains("UNKNOWNHOST") || upper.contains("NO INTERNET") || upper.contains("UNABLE TO RESOLVE HOST") ->
                "No internet connection. Please check your Wi-Fi or mobile data."
            upper.contains("CONNECT") && upper.contains("FAIL") ->
                "Failed to connect to Google servers. Please check your connection."
            upper.contains("SSL") || upper.contains("CERTIFICATE") ->
                "Secure connection failed. Please check your network and device date."
            else ->
                "Unable to extract text from this area. Try selecting a clearer region."
        }
    }

    /**
     * Formats an exception thrown during Gemini API operations into a 1-line friendly message.
     */
    fun formatException(throwable: Throwable): String {
        return when (throwable) {
            is retrofit2.HttpException -> getMessageForHttpCode(throwable.code())
            is UnknownHostException -> "No internet connection. Please check your Wi-Fi or mobile data."
            is SocketTimeoutException -> "Connection timed out. Please check your network and try again."
            is ConnectException -> "Unable to reach Google servers. Please check your internet connection."
            is SSLException -> "Secure connection failed. Please check your network and device date."
            else -> formatUserFriendlyError(throwable.message ?: throwable.localizedMessage)
        }
    }
}
