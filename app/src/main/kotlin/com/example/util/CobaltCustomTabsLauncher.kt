/*
 * Cobalt - A universal capture and personal knowledge archive
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

package com.example.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.example.R

/**
 * Dedicated manager for opening web links in high-performance, branded Chrome Custom Tabs.
 * Matches Cobalt's Material 3 surface theme (Light, Dark, and AMOLED), provides seamless
 * return to feeds, custom action buttons, and graceful fallback to standard browsers.
 */
object CobaltCustomTabsLauncher {

    private const val TAG = "CobaltCustomTabs"

    /**
     * Launch a URL in Chrome Custom Tabs with Cobalt branding and actions.
     */
    fun launch(
        context: Context,
        url: String,
        isDarkTheme: Boolean = false,
        isAmoled: Boolean = false,
        itemId: String? = null,
        itemTitle: String? = null
    ) {
        val cleanUrl = normalizeUrl(url)
        val uri = try {
            Uri.parse(cleanUrl)
        } catch (e: Exception) {
            Toast.makeText(context, "Invalid URL format", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val builder = CustomTabsIntent.Builder()
            builder.setShowTitle(true)
            builder.setUrlBarHidingEnabled(true)
            builder.setShareState(CustomTabsIntent.SHARE_STATE_ON)

            // Setup Theme Color Schemes (Light, Dark, AMOLED)
            val lightToolbarColor = 0xFFFDFBF9.toInt() // CreamPaper
            val darkToolbarColor = if (isAmoled) 0xFF000000.toInt() else 0xFF1A120E.toInt() // TrueBlack or SurfaceDark
            val activeToolbarColor = if (isDarkTheme) darkToolbarColor else lightToolbarColor

            val lightParams = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(lightToolbarColor)
                .setNavigationBarColor(lightToolbarColor)
                .build()

            val darkParams = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(darkToolbarColor)
                .setNavigationBarColor(darkToolbarColor)
                .build()

            val defaultParams = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(activeToolbarColor)
                .setNavigationBarColor(activeToolbarColor)
                .build()

            builder.setDefaultColorSchemeParams(defaultParams)
            builder.setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_LIGHT, lightParams)
            builder.setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_DARK, darkParams)
            builder.setColorScheme(
                if (isDarkTheme) CustomTabsIntent.COLOR_SCHEME_DARK
                else CustomTabsIntent.COLOR_SCHEME_LIGHT
            )

            // Custom Close Button Icon (Cobalt back navigation arrow)
            val closeIcon = getBitmapFromVectorDrawable(context, R.drawable.ic_custom_back)
            if (closeIcon != null) {
                builder.setCloseButtonIcon(closeIcon)
            }

            // Custom Toolbar Action Button: Copy Link
            val copyIcon = getBitmapFromVectorDrawable(context, R.drawable.ic_custom_copy)
            if (copyIcon != null) {
                val copyIntent = Intent(context, CobaltCustomTabsActionReceiver::class.java).apply {
                    action = CobaltCustomTabsActionReceiver.ACTION_COPY_LINK
                    putExtra(CobaltCustomTabsActionReceiver.EXTRA_URL, cleanUrl)
                    setPackage(context.packageName)
                }
                val copyPendingIntent = PendingIntent.getBroadcast(
                    context,
                    1001,
                    copyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                builder.setActionButton(copyIcon, "Copy Link", copyPendingIntent, true)
            }

            // Custom Overflow Menu Item: Share Link
            val shareIntent = Intent(context, CobaltCustomTabsActionReceiver::class.java).apply {
                action = CobaltCustomTabsActionReceiver.ACTION_SHARE_LINK
                putExtra(CobaltCustomTabsActionReceiver.EXTRA_URL, cleanUrl)
                setPackage(context.packageName)
            }
            val sharePendingIntent = PendingIntent.getBroadcast(
                context,
                1002,
                shareIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            builder.addMenuItem("Share Link", sharePendingIntent)

            // Custom Overflow Menu Item: Star / Favorite
            if (!itemId.isNullOrBlank()) {
                val starIntent = Intent(context, CobaltCustomTabsActionReceiver::class.java).apply {
                    action = CobaltCustomTabsActionReceiver.ACTION_STAR_ITEM
                    putExtra(CobaltCustomTabsActionReceiver.EXTRA_ITEM_ID, itemId)
                    putExtra(CobaltCustomTabsActionReceiver.EXTRA_URL, cleanUrl)
                    setPackage(context.packageName)
                }
                val starPendingIntent = PendingIntent.getBroadcast(
                    context,
                    1003,
                    starIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                builder.addMenuItem("Star / Favorite Item", starPendingIntent)
            }

            // Match Settings page shared axis slide transitions
            builder.setStartAnimations(context, R.anim.sud_slide_next_in, R.anim.sud_slide_next_out)
            builder.setExitAnimations(context, R.anim.sud_slide_back_in, R.anim.sud_slide_back_out)

            val customTabsIntent = builder.build()

            // Resolve CCT package if available
            val cctPackage = CustomTabsClient.getPackageName(context, null)
            if (!cctPackage.isNullOrBlank()) {
                customTabsIntent.intent.setPackage(cctPackage)
            }

            if (context !is android.app.Activity) {
                customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            customTabsIntent.launchUrl(context, uri)

        } catch (e: Exception) {
            android.util.Log.w(TAG, "Chrome Custom Tabs failed to launch, falling back to ACTION_VIEW: ${e.message}")
            fallbackToStandardBrowser(context, uri)
        }
    }

    /**
     * Checks whether a browser supporting Chrome Custom Tabs is installed on the device.
     */
    fun isCustomTabsSupported(context: Context): Boolean {
        return !CustomTabsClient.getPackageName(context, null).isNullOrBlank()
    }

    /**
     * Gracefully fallback to standard ACTION_VIEW if CCT cannot be launched.
     */
    fun fallbackToStandardBrowser(context: Context, uri: Uri) {
        try {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "No web browser found to open link", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Normalizes a URL to ensure it has an explicit http/https scheme.
     */
    fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        return if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }

    /**
     * Safely converts a vector drawable resource to a tinted Bitmap for CCT toolbar buttons.
     */
    private fun getBitmapFromVectorDrawable(
        context: Context,
        @DrawableRes drawableId: Int,
        sizeDp: Int = 24
    ): Bitmap? {
        return try {
            val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
            val density = context.resources.displayMetrics.density
            val px = (sizeDp * density).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to render vector drawable to bitmap: ${e.message}")
            null
        }
    }
}
