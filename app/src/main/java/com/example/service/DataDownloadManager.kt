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
