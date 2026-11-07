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
    DOCUMENT("Document", "editorial_document"),

    /**
     * Clean, distraction-free presentation:
     * Removes the top metadata pill and divider, presenting title and markdown content with generous breathing room.
     */
    MINIMAL("Minimal", "minimal_note"),

    /**
     * Tighter, punchy card format optimized for sharing to Twitter/X, Instagram, or WhatsApp.
     */
    COMPACT("Compact", "compact_card");

    companion object {
        fun fromString(value: String?): NoteShareStyle = when (value?.uppercase()) {
            "EDITORIAL", "DOCUMENT", "EDITORIAL_DOCUMENT" -> DOCUMENT
            "MINIMAL", "MINIMAL_NOTE", "QUOTE", "MINIMAL_QUOTE" -> MINIMAL
            "COMPACT", "COMPACT_CARD" -> COMPACT
            else -> DOCUMENT
        }
    }
}
