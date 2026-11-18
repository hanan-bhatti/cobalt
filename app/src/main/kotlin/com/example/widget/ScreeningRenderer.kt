package com.example.widget

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.R

/** Selected Artwork A (Figma 224:2210): full artwork, floating 48 dp thumbs, optional 3 dp edge. */
object ScreeningRenderer {
    const val WIDTH = 336f
    const val HEIGHT = 264f

    fun render(context: Context, entry: ScreeningWidgetEntry, width: Int, height: Int,
        artwork: Bitmap? = null, status: String? = null, colors: ColorScheme = WidgetAppearance.colors(context),
        layoutWidth: Float = WIDTH): Bitmap {
        val w = width.coerceIn(1, 900)
        val h = height.coerceIn(1, 900)
        val logicalW = layoutWidth.coerceAtLeast(100f)
        val logicalH = logicalW * h / w
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(w / logicalW, h / logicalH)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val primary = colors.primary.toArgb()
        val feedbackInk = Color.rgb(25, 26, 33)
        // Artwork always has a dark footer. Light app palettes need a lighter accent there.
        val accent = if (artwork == null || ColorUtils.calculateContrast(primary, feedbackInk) >= 4.5) primary
            else ColorUtils.blendARGB(primary, Color.WHITE, .55f)
        canvas.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, logicalW, logicalH), 28f, 28f, Path.Direction.CW) })
        canvas.drawColor(colors.surfaceContainer.toArgb())
        fun rect(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Int) {
            paint.shader = null; paint.style = Paint.Style.FILL; paint.color = color
            canvas.drawRoundRect(RectF(x, y, x + width, y + height), radius, radius, paint)
        }
        fun glyph(res: Int, x: Float, y: Float, color: Int) {
            val drawable = ContextCompat.getDrawable(context, res)?.mutate() ?: return
            drawable.setTint(color); drawable.setBounds(x.toInt(), y.toInt(), x.toInt() + 22, y.toInt() + 22); drawable.draw(canvas)
        }
        fun text(value: String, x: Float, y: Float, width: Int, height: Int, size: Float, color: Int, serif: Boolean = false) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; textSize = (size * context.resources.configuration.fontScale.coerceIn(1f, 1.4f)).coerceAtMost(if (serif) 32f else 16f)
                typeface = WidgetAppearance.typeface(context, serif, if (serif) 500 else 400)
            }
            val layout = StaticLayout.Builder.obtain(value, 0, value.length, tp, width.coerceAtLeast(1)).setIncludePad(false)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(1).setEllipsize(TextUtils.TruncateAt.END).build()
            canvas.save(); canvas.clipRect(x, y, x + width, y + height)
            canvas.translate(x, y + (height - layout.height).coerceAtLeast(0) / 2f); layout.draw(canvas); canvas.restore()
        }
        if (artwork != null) {
            val scale = maxOf(logicalW / artwork.width, logicalH / artwork.height)
            val imageW = artwork.width * scale; val imageH = artwork.height * scale
            canvas.drawBitmap(artwork, null, RectF((logicalW - imageW) / 2, (logicalH - imageH) / 2,
                (logicalW + imageW) / 2, (logicalH + imageH) / 2), paint)
            paint.shader = LinearGradient(0f, logicalH * .4f, 0f, logicalH,
                intArrayOf(Color.TRANSPARENT, 0x44000000, 0xED101116.toInt()), floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, logicalW, logicalH, paint); paint.shader = null
        }
        val title = entry.pick?.title ?: when (entry.config.source) {
            ScreeningSource.ALL -> "Your next watch"
            ScreeningSource.MOVIES -> "Your next movie"
            ScreeningSource.SHOWS -> "Your next show"
            ScreeningSource.ANIME -> "Your next anime"
            ScreeningSource.MANGA -> "Your next read"
        }
        val hasPick = entry.pick != null
        val copyWidth = (logicalW - 32f - if (hasPick) 124f else 0f).toInt()
        val ink = if (artwork != null) Color.WHITE else colors.onSurface.toArgb()
        val secondaryInk = if (artwork != null) 0xDDFFFFFF.toInt() else colors.onSurfaceVariant.toArgb()
        text(title, 16f, logicalH - 70f, copyWidth, 34, if (logicalW < 300f) 23f else 27f, ink, serif = true)
        text(status ?: entry.pick?.metadata ?: "Save titles in Media to get started", 16f, logicalH - 32f, copyWidth, 16, 11f, secondaryInk)
        if (hasPick) {
            val x = logicalW - 124f; val y = logicalH - 72f
            val background = if (artwork != null) 0xFA191A21.toInt() else colors.surfaceContainerHighest.toArgb()
            rect(x, y, 108f, 56f, 28f, background)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
            paint.color = if (artwork != null) 0x667F8396 else colors.outlineVariant.toArgb()
            canvas.drawRoundRect(RectF(x + .5f, y + .5f, x + 107.5f, y + 55.5f), 28f, 28f, paint)
            paint.style = Paint.Style.FILL
            listOf(ScreeningThumb.LIKED to R.drawable.ic_custom_helpful, ScreeningThumb.DISLIKED to R.drawable.ic_custom_unhelpful).forEachIndexed { index, (choice, res) ->
                val bx = x + 4f + index * 52f
                val selected = entry.thumb == choice
                if (selected) rect(bx, y + 4f, 48f, 48f, 24f, accent)
                val color = if (selected) { if (artwork != null) feedbackInk else colors.onPrimary.toArgb() }
                    else if (artwork != null) accent else colors.onSurfaceVariant.toArgb()
                glyph(res, bx + 13f, y + 17f, color)
            }
        }
        entry.pick?.playbackProgress?.takeIf { it.isFinite() && it > 0.0 && it < 100.0 }?.let { progress ->
            // One accent line, no track, labels, percentages or inferred elapsed-time progress.
            paint.color = accent; paint.shader = null
            // Use the straight bottom edge so very small positions remain visible inside rounded corners.
            canvas.drawRect(28f, logicalH - 3f, 28f + ((logicalW - 56f) * progress / 100.0).toFloat(), logicalH, paint)
        }
        return bitmap
    }
}
