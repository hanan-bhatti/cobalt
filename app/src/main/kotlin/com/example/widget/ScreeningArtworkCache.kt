package com.example.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.MessageDigest
import kotlin.math.max

/** Artwork is optional. Bounded local copies keep a published pick useful offline. */
object ScreeningArtworkCache {
    private val memory = object : android.util.LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val writes = Mutex()
    private fun key(data: String) = MessageDigest.getInstance("SHA-256").digest(data.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private fun directory(context: Context) = File(context.cacheDir, "screening-widget-artwork")

    /** Safe during composition: memory only, with no file decoding or network access. */
    fun peek(data: String?): Bitmap? = data?.takeIf { it.isNotBlank() }?.let { memory.get(key(it)) }

    fun cached(context: Context, data: String?): Bitmap? {
        if (data.isNullOrBlank()) return null
        val key = key(data)
        memory.get(key)?.let { return it }
        val file = File(directory(context), "$key.png")
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth !in 1..632 || bounds.outHeight !in 1..632) return@runCatching null
            BitmapFactory.decodeFile(file.path)?.also { memory.put(key, it) }
        }.getOrNull()
    }

    suspend fun load(context: Context, data: String?, timeoutMillis: Long = 4_000): Bitmap? = withContext(Dispatchers.IO) {
        if (data.isNullOrBlank()) return@withContext null
        cached(context, data)?.let { return@withContext it }
        try {
            withTimeoutOrNull(timeoutMillis) {
                val result = context.imageLoader.execute(ImageRequest.Builder(context).data(data).size(632, 528).allowHardware(false).build())
                val drawable = (result as? SuccessResult)?.drawable ?: return@withTimeoutOrNull null
                val largest = max(drawable.intrinsicWidth, drawable.intrinsicHeight).coerceAtLeast(1)
                val scale = minOf(1f, 632f / largest)
                val bitmap = drawable.toBitmap((drawable.intrinsicWidth * scale).toInt().coerceIn(1, 632),
                    (drawable.intrinsicHeight * scale).toInt().coerceIn(1, 632), Bitmap.Config.ARGB_8888)
                val key = key(data)
                memory.put(key, bitmap)
                writes.withLock {
                    val dir = directory(context).apply { mkdirs() }
                    val atomic = AtomicFile(File(dir, "$key.png"))
                    val stream = atomic.startWrite()
                    try {
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                        atomic.finishWrite(stream)
                    } catch (error: Exception) { atomic.failWrite(stream); throw error }
                    val files = dir.listFiles()?.filter { it.extension == "png" }?.sortedBy { it.lastModified() }.orEmpty().toMutableList()
                    var size = files.sumOf { it.length() }
                    while (files.size > 16 || size > 8 * 1024 * 1024) { val oldest = files.removeAt(0); size -= oldest.length(); oldest.delete() }
                }
                bitmap
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { cached(context, data) }
    }
}
