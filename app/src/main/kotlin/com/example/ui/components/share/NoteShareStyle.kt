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

package com.example.ui.components.share

/**
 * Visual styles for exporting note cards.
 */
enum class NoteShareStyle(val displayName: String, val analyticsName: String) {
    /**
     * Classic Editorial Document style:
     * Category, date, word count, formatted Markdown body with headings, code blocks, compact tables.
     */
    EDITORIAL("Document", "editorial_document"),

    /**
     * Minimalist Thought / Quote style:
     * Expressive large-text presentation for brief thoughts, key quotes, or code highlights.
     */
    MINIMAL("Quote", "minimal_quote");

    companion object {
        fun fromString(value: String?): NoteShareStyle =
            entries.find { it.name.equals(value, ignoreCase = true) || it.analyticsName.equals(value, ignoreCase = true) }
                ?: EDITORIAL
    }
}
