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

package com.example.utils

import android.net.Uri

object ClipboardUrlHelper {
    /**
     * Parses the string to check if it's a valid web URL or web domain.
     * Rejects multi-line text, markdown, text with spaces, etc.
     * Returns the normalized URL (e.g. "https://example.com/...") or null if invalid.
     */
    fun parseUrl(rawText: String): String? {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty() || trimmed.contains(" ") || trimmed.contains("\n") || trimmed.contains("\r") || trimmed.contains("\t")) {
            return null
        }
        val lower = trimmed.lowercase()
        val candidate = if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            "https://$trimmed"
        } else trimmed

        val uri = try {
            Uri.parse(candidate)
        } catch (_: Exception) {
            null
        } ?: return null

        val host = uri.host ?: return null
        // Ensure host has a valid dot and at least 2 alpha characters in TLD
        val tld = host.substringAfterLast(".", "")
        if (host.contains(".") && tld.length >= 2 && tld.all { it.isLetter() }) {
            return candidate
        }
        return null
    }

    /**
     * Extracts the clean domain name (e.g. "example.com" or "github.com") without "www."
     */
    fun extractDomain(url: String): String {
        return try {
            val formatted = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else url
            val uri = Uri.parse(formatted)
            val host = uri.host.orEmpty()
            if (host.startsWith("www.")) host.removePrefix("www.") else host.ifEmpty { url.take(30) }
        } catch (_: Exception) {
            url.take(30)
        }
    }
}
