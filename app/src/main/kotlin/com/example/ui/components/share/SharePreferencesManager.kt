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

package com.example.ui.components.share

import android.content.Context

/**
 * Manages persisted user preferences for the Cobalt sharing system.
 */
object SharePreferencesManager {
    private const val PREFS_NAME = "cobalt_settings"
    private const val KEY_SHARE_ATTRIBUTION = "share_show_attribution"
    private const val KEY_SHARE_STYLE = "share_link_style"
    private const val KEY_IMAGE_SHARE_STYLE = "share_image_style"
    private const val KEY_MOVIE_SHARE_STYLE = "share_movie_style"

    fun isAttributionEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHARE_ATTRIBUTION, true)

    fun setAttributionEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHARE_ATTRIBUTION, enabled)
            .apply()
    }

    fun getLinkShareStyle(context: Context): LinkShareStyle {
        val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SHARE_STYLE, LinkShareStyle.CARD.name)
        return runCatching { LinkShareStyle.valueOf(name ?: LinkShareStyle.CARD.name) }
            .getOrDefault(LinkShareStyle.CARD)
    }

    fun setLinkShareStyle(context: Context, style: LinkShareStyle) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SHARE_STYLE, style.name)
            .apply()
    }

    fun getImageShareStyle(context: Context): ImageShareStyle {
        val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_IMAGE_SHARE_STYLE, ImageShareStyle.POLAROID.name)
        return runCatching { ImageShareStyle.valueOf(name ?: ImageShareStyle.POLAROID.name) }
            .getOrDefault(ImageShareStyle.POLAROID)
    }

    fun setImageShareStyle(context: Context, style: ImageShareStyle) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_IMAGE_SHARE_STYLE, style.name)
            .apply()
    }

    fun getMovieShareStyle(context: Context): MovieShareStyle {
        val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MOVIE_SHARE_STYLE, MovieShareStyle.THEATRICAL.name)
        return runCatching { MovieShareStyle.valueOf(name ?: MovieShareStyle.THEATRICAL.name) }
            .getOrDefault(MovieShareStyle.THEATRICAL)
    }

    fun setMovieShareStyle(context: Context, style: MovieShareStyle) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MOVIE_SHARE_STYLE, style.name)
            .apply()
    }
}
