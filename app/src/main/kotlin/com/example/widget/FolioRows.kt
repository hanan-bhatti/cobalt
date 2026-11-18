package com.example.widget

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.LruCache
import androidx.core.content.ContextCompat
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.util.displayTitle
import kotlinx.coroutines.*

/** Only five small, cache-only thumbnails are considered. Scroll rendering never fetches a network URL. */
object FolioRows {
    private val thumbnails = object : LruCache<String, Bitmap>(1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private fun source(item: SavedItem): String? = when (item.type) {
        SavedItemType.IMAGE -> item.thumbnailPath?.takeIf { it.isNotBlank() } ?: item.content
        SavedItemType.VIDEO -> item.thumbnailPath?.takeIf { it.isNotBlank() } ?: item.content
        SavedItemType.LINK -> item.linkImage?.takeIf { it.isNotBlank() } ?: item.thumbnailPath
        else -> null
    }?.takeIf { it.isNotBlank() }
    private fun key(item: SavedItem) = "${item.id}:${source(item)}:${item.updatedAt}"
    suspend fun prepare(context: Context, items: List<SavedItem>) = withContext(Dispatchers.IO) {
        coroutineScope {
            items.take(5).map { item -> async {
                val uri = source(item) ?: return@async
                if (thumbnails.get(key(item)) != null) return@async
                try {
                    withTimeoutOrNull(750) {
                        if (item.type == SavedItemType.VIDEO && uri == item.content && !uri.startsWith("http", true)) {
                            val bitmap = videoFrame(context, uri)
                            if (bitmap != null) thumbnails.put(key(item), bitmap)
                        } else {
                            val result = Coil.imageLoader(context).execute(ImageRequest.Builder(context).data(uri).size(80)
                                .allowHardware(false).networkCachePolicy(CachePolicy.DISABLED).build())
                            if (result is SuccessResult) thumbnails.put(key(item), result.drawable.toBitmap(80, 80))
                        }
                    }
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { /* A missing thumbnail must never block the folder list. */ }
            } }.awaitAll()
        }
    }
    @Suppress("DEPRECATION") // API 24–26 has no scaled-frame API; use Android's bounded MINI thumbnail.
    private fun videoFrame(context: Context, path: String): Bitmap? {
        if (android.os.Build.VERSION.SDK_INT < 27) {
            if (path.startsWith("content:")) return null
            return android.media.ThumbnailUtils.createVideoThumbnail(path.removePrefix("file://"), android.provider.MediaStore.Video.Thumbnails.MINI_KIND)?.let {
                val small = Bitmap.createScaledBitmap(it, 80, 80, true)
                if (small !== it) it.recycle()
                small
            }
        }
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            if (path.startsWith("content:") || path.startsWith("file:")) retriever.setDataSource(context, android.net.Uri.parse(path))
            else retriever.setDataSource(path)
            retriever.getScaledFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 80, 80)
        } finally { retriever.release() }
    }
    fun excerpt(item: SavedItem): String {
        val source = when (item.type) {
            SavedItemType.TEXT, SavedItemType.CODE -> item.content.take(240)
            SavedItemType.LINK -> item.linkDescription.orEmpty().take(240)
            else -> ""
        }
        val lines = source.lines().map { it.trim().trimStart('#', ' ', '*', '-') }.filter { it.isNotBlank() }
        val title = item.title.trim()
        return lines.dropWhile { it == title }.joinToString(" ").replace(Regex("\\s+"), " ").take(180)
    }
    fun render(context: Context, item: SavedItem, width: Int, colors: ColorScheme = WidgetAppearance.colors(context)): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), (width * 56f / 280).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(width / 280f, width / 280f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.surfaceContainerHigh.toArgb() }
        canvas.drawRoundRect(RectF(0f, 0f, 280f, 50f), 12f, 12f, paint)
        val thumb = thumbnails.get(key(item))
        if (thumb != null) {
            val side = minOf(thumb.width, thumb.height)
            val src = Rect((thumb.width - side) / 2, (thumb.height - side) / 2, (thumb.width + side) / 2, (thumb.height + side) / 2)
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(RectF(8f, 8f, 42f, 42f), 8f, 8f, Path.Direction.CW) })
            canvas.drawBitmap(thumb, src, RectF(8f, 8f, 42f, 42f), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
        } else {
            val icon = when (item.type) {
                SavedItemType.IMAGE, SavedItemType.VIDEO -> R.drawable.ic_custom_image
                SavedItemType.LINK -> R.drawable.ic_custom_link
                SavedItemType.AUDIO -> R.drawable.ic_custom_voice
                SavedItemType.CODE -> R.drawable.ic_custom_code
                SavedItemType.MEDIA -> R.drawable.ic_custom_movie
                else -> R.drawable.ic_custom_text
            }
            ContextCompat.getDrawable(context, icon)?.mutate()?.let { it.setTint(colors.primary.toArgb()); it.setBounds(14, 14, 36, 36); it.draw(canvas) }
        }
        val snippet = excerpt(item)
        fun text(value: String, y: Float, size: Float, color: Int, weight: Int) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = (size * context.resources.configuration.fontScale.coerceIn(1f, 1.6f)).coerceAtMost(18f)
                this.color = color; typeface = WidgetAppearance.typeface(context, false, weight)
            }
            val layout = StaticLayout.Builder.obtain(value, 0, value.length, tp, 218).setMaxLines(1)
                .setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            canvas.save(); canvas.clipRect(50f, y, 270f, y + 21f); canvas.translate(50f, y); layout.draw(canvas); canvas.restore()
        }
        text(item.displayTitle(context), if (snippet.isEmpty()) 16f else 7f, 13f, colors.onSurface.toArgb(), 500)
        if (snippet.isNotEmpty()) text(snippet, 29f, 11f, colors.onSurfaceVariant.toArgb(), 400)
        return bitmap
    }
}
