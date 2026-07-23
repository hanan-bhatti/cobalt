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

import android.content.Intent
import android.media.projection.MediaProjection

object MediaProjectionCache {
    var resultCode: Int = 0
    var resultData: Intent? = null
    var activeMediaProjection: MediaProjection? = null

    fun clear() {
        try {
            activeMediaProjection?.stop()
        } catch (e: Exception) {
            // Ignore
        }
        activeMediaProjection = null
        resultData = null
        resultCode = 0
    }
}
