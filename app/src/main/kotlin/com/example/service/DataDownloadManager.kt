/*
 * Second Brain - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 */

package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DataDownloadManager {
    data class FailedItem(val id: String, val title: String, val reason: String)

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
        val isCompleted: Boolean = false,
        val failedItems: List<FailedItem> = emptyList()
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
