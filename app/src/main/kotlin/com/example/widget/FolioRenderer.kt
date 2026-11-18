package com.example.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.example.R
import com.example.data.local.CustomFolderEntity
import com.example.data.model.SavedItem
import com.example.data.model.SavedItemType
import com.example.ui.screens.getFolderIconRes
import com.example.ui.screens.parseHexColor
import com.example.util.displayTitle

/** One renderer for the launcher and the live preview; geometry comes from Figma 77:1112. */
object FolioRenderer {
    const val WIDTH = 316f
    const val HEIGHT = 248f

    fun isDark(context: Context): Boolean = WidgetAppearance.isDark(context)

    fun render(context: Context, config: FolioConfig, folder: CustomFolderEntity?, items: List<SavedItem>, width: Int, height: Int, itemCount: Int = items.size, status: String? = null, colors: ColorScheme = WidgetAppearance.colors(context), drawItems: Boolean = true): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(width / WIDTH, height / HEIGHT)
        val dark = isDark(context)
        val scheme = colors
        val color = folder?.colorHex?.takeIf { it.isNotBlank() } ?: config.colorHex
        val accent = parseHexColor(color, androidx.compose.ui.graphics.Color(0xFFF3D986), dark).toArgb()
        val cover = if (dark) ColorUtils.blendARGB(scheme.background.toArgb(), accent, .22f) else accent
        val coverInk = if (dark) accent else if (ColorUtils.calculateLuminance(cover) > .45) scheme.onSurface.toArgb() else android.graphics.Color.WHITE
        val paper = scheme.surfaceContainerHigh.toArgb()
        val ink = scheme.onSurface.toArgb()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun rect(x: Float, y: Float, w: Float, h: Float, radius: Float, fill: Int) {
            paint.color = fill; canvas.drawRoundRect(RectF(x, y, x+w, y+h), radius, radius, paint)
        }
        canvas.save(); canvas.rotate(2f, 158f, 139f)
        rect(9f,36f,298f,206f,24f,ColorUtils.blendARGB(cover, paper,.4f))
        canvas.restore()
        rect(18f,0f,130f,40f,16f,cover)
        rect(0f,24f,316f,224f,24f,cover)

        fun label(value: String, x: Float, y: Float, w: Int, h: Int, size: Float, line: Float, color: Int, serif: Boolean=false, weight: Int=400, lines: Int=1) {
            val scale = context.resources.configuration.fontScale.coerceIn(1f, 1.6f)
            val actual = (size * scale).coerceAtMost(if(serif) 30f else 20f)
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color; textSize=actual; typeface=WidgetAppearance.typeface(context,serif,weight) }
            val maxLines = if(actual > size * 1.2f) 1 else lines
            val layout = StaticLayout.Builder.obtain(value,0,value.length,tp,w).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false).setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END)
                .setLineSpacing((line - actual).coerceAtLeast(0f),1f).build()
            canvas.save(); canvas.clipRect(x,y,x+w,y+h);canvas.translate(x,y+(h-layout.height).coerceAtLeast(0)/2f);layout.draw(canvas);canvas.restore()
        }
        fun glyph(res: Int,x: Int,y: Int,size: Int,color: Int) {
            val d=ContextCompat.getDrawable(context,res)?.mutate() ?: return
            d.setTint(color);d.setBounds(x,y,x+size,y+size);d.draw(canvas)
        }
        label("PINNED FOLDER",28f,10f,110,14,10f,14f,coverInk,weight=500)
        glyph(if(folder?.iconName.isNullOrBlank() || folder?.iconName=="folder") R.drawable.ic_custom_folder_open else getFolderIconRes(folder?.iconName),18,47,24,coverInk)
        label(folder?.name ?: if(config.removedFolder!=null) "Folder removed" else "Choose folder",50f,42f,174,34,27f,34f,coverInk,true,500)
        label(if(folder!=null) itemCount.toString().padStart(2,'0') else "—",232f,44f,66,30,23f,30f,coverInk,true)
        if(folder==null || items.isEmpty()) {
            rect(18f,88f,280f,108f,12f,paper)
            label(if(folder==null) status ?: "Choose a folder in Cobalt" else "Your next good idea goes here.",36f,100f,244,72,13f,18f,ink,weight=500,lines=2)
        } else if (drawItems) items.take(2).forEachIndexed { index,item ->
            val y=88+60*index
            rect(18f,y.toFloat(),280f,48f,12f,paper)
            val res=when(item.type) {
                SavedItemType.IMAGE, SavedItemType.VIDEO -> R.drawable.ic_custom_image
                SavedItemType.AUDIO -> R.drawable.ic_custom_voice
                SavedItemType.LINK -> R.drawable.ic_custom_link
                SavedItemType.CODE -> R.drawable.ic_custom_code
                SavedItemType.MEDIA -> R.drawable.ic_custom_movie
                else -> R.drawable.ic_custom_text
            }
            glyph(res,26,y+14,20,scheme.primary.toArgb())
            label(item.displayTitle(context),56f,y+8f,224,32,13f,16f,ink,weight=500,lines=2)
        }
        label(status ?: if(folder==null) "Tap to customize your Folio." else if(items.isEmpty()) "Add something to this folder." else if (items.size > 2) "Scroll for more · tap an item to open" else "Your little collection of good things.",18f,208f,248,22,11f,16f,coverInk)
        glyph(R.drawable.ic_custom_arrow_right,278,209,20,coverInk)
        return bitmap
    }
}
