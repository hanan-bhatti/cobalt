/*
 * Second Brain - A universal capture and personal knowledge archive
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

import androidx.compose.ui.unit.LayoutDirection

/**
 * Detects whether a string is primarily Right-to-Left (RTL),
 * such as Urdu, Arabic, Hebrew, or Persian.
 */
fun isRtlText(text: String): Boolean {
    val trimmed = text.trim()
    for (i in 0 until trimmed.length) {
        val c = trimmed.codePointAt(i)
        val dir = Character.getDirectionality(c)
        if (dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
            dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) {
            return true
        }
        if (dir == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
            return false
        }
    }
    return false
}

/**
 * Returns LayoutDirection.Rtl if the text is RTL, otherwise LayoutDirection.Ltr.
 */
fun getLayoutDirectionForText(text: String): LayoutDirection {
    return if (isRtlText(text)) LayoutDirection.Rtl else LayoutDirection.Ltr
}
