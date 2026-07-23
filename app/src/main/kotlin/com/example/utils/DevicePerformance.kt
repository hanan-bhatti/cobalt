/*
 * Second Brain - A universal capture and personal knowledge archive
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

package com.example.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * ponytail: simple utility to determine device performance class.
 * Checks API version, low RAM flag, and total RAM to decide if blur is supported.
 */
object DevicePerformance {
    private var cachedBlurTier: Boolean? = null

    fun isDeviceCapableOfBlur(context: Context): Boolean {
        // 1. Return in-memory cached value if already computed
        cachedBlurTier?.let { return it }

        // 2. SharedPreferences persistent cache
        val perfPrefs = context.getSharedPreferences("device_performance", Context.MODE_PRIVATE)
        if (perfPrefs.contains("device_blur_tier")) {
            val cachedValue = perfPrefs.getBoolean("device_blur_tier", false)
            cachedBlurTier = cachedValue
            return cachedValue
        }

        // 3. Compute once
        val computedValue = computeBlurTier(context)
        
        // Save to cache
        perfPrefs.edit().putBoolean("device_blur_tier", computedValue).apply()
        cachedBlurTier = computedValue
        
        return computedValue
    }

    fun shouldUseBlur(context: Context): Boolean {
        // 1. Check manual override first
        val prefs = context.getSharedPreferences("second_brain_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("force_disable_blur", false)) {
            return false
        }

        // 2. Delegate to capability check
        return isDeviceCapableOfBlur(context)
    }

    fun getDeviceType(context: Context): String {
        val metrics = context.resources.displayMetrics
        val widthDp = metrics.widthPixels / metrics.density
        val heightDp = metrics.heightPixels / metrics.density
        val smallestWidthDp = minOf(widthDp, heightDp)
        return if (smallestWidthDp >= 600) "TABLET" else "MOBILE"
    }

    private fun computeBlurTier(context: Context): Boolean {
        // a. API level gate: Android 12+ (API 31) minimum
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false

        // b. Low RAM device flag check
        if (activityManager.isLowRamDevice) {
            return false
        }

        // c. Total memory limit check (minimum 3GB RAM proxy)
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        if (memoryInfo.totalMem < 3_000_000_000L) {
            return false
        }

        return true
    }
}
