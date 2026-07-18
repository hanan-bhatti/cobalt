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

package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DataDownloadManager {
    data class DownloadProgress(
        val isDownloading: Boolean = false,
        val totalFiles: Int = 0,
        val downloadedFiles: Int = 0,
        val currentFileName: String = "",
        val totalBytes: Long = 0L,
        val downloadedBytes: Long = 0L,
        val speedBytesPerSec: Long = 0L,
        val estRemainingSeconds: Long = -1L,
        val error: String? = null,
        val isCompleted: Boolean = false
    )

    private val _progress = MutableStateFlow(DownloadProgress())
    val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()

    fun updateProgress(update: DownloadProgress) {
        _progress.value = update
    }

    fun reset() {
        _progress.value = DownloadProgress()
    }
}
