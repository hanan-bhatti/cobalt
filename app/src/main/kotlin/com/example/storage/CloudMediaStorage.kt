package com.example.storage

interface CloudMediaStorage {
    val isAvailable: Boolean

    suspend fun uploadMedia(userId: String, itemId: String, bytes: ByteArray, mimeType: String): Result<String>
    suspend fun downloadMedia(remoteUrl: String): Result<ByteArray>
    suspend fun deleteMedia(remoteUrl: String): Result<Unit>
}
