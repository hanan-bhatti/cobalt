package com.example.update

import com.example.util.ReleaseNote

data class UpdateCheckResult(
    val isUpdateAvailable: Boolean,
    val latestVersionName: String,
    val downloadUrl: String?,
    val releaseNotes: String?
)

interface UpdateChecker {
    suspend fun checkForUpdates(): Result<UpdateCheckResult>
    fun getLatestRelease(): ReleaseNote
    fun isUpdateAvailable(): Boolean
}
