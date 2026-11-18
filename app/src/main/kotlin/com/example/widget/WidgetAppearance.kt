package com.example.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.model.TextFontFamily
import com.example.ui.theme.DarkColorScheme
import com.example.ui.theme.LightColorScheme

/** The same defaults, color roles and UI-font gate used by Cobalt's app theme. */
object WidgetAppearance {
    private val typefaces = android.util.LruCache<String, Typeface>(16)

    fun isDark(context: Context): Boolean = when (
        context.getSharedPreferences("cobalt_settings", Context.MODE_PRIVATE).getString("theme_mode", "Light")
    ) {
        "Dark" -> true
        "Light" -> false
        else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    fun colors(context: Context): ColorScheme {
        val dark = isDark(context)
        val dynamic = context.getSharedPreferences("cobalt_settings", Context.MODE_PRIVATE).getBoolean("dynamic_color", true)
        return if (dynamic && Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) DarkColorScheme else LightColorScheme
    }

    fun typeface(context: Context, serif: Boolean, weight: Int): Typeface {
        val prefs = context.getSharedPreferences("cobalt_settings", Context.MODE_PRIVATE)
        val customUi = prefs.getBoolean("pref_use_custom_font", true) && prefs.getBoolean("pref_apply_to_app_ui", false)
        val family = TextFontFamily.fromKey(prefs.getString("pref_text_font", "inter"))
        val res = if (!customUi) {
            if (serif) R.font.source_serif_4_variable else null
        } else when (family) {
            TextFontFamily.INTER -> R.font.inter_variable
            TextFontFamily.LITERATA -> R.font.literata_variable
            TextFontFamily.SOURCE_SERIF_4 -> R.font.source_serif_4_variable
            TextFontFamily.LEXEND -> R.font.lexend_variable
            TextFontFamily.ATKINSON_HYPERLEGIBLE -> if (weight >= 600) R.font.atkinson_hyperlegible_bold else R.font.atkinson_hyperlegible_regular
            TextFontFamily.SYSTEM -> null
        }
        val key = "$res:$weight"
        typefaces.get(key)?.let { return it }
        val base = res?.let { runCatching { ResourcesCompat.getFont(context, it) }.getOrNull() }
            ?: Typeface.create("sans-serif", Typeface.NORMAL)
        val face = if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight, false)
            else Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
        typefaces.put(key, face)
        return face
    }
}
