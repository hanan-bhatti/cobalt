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

    fun shouldUseBlur(context: Context): Boolean {
        // 1. Check manual override first
        val prefs = context.getSharedPreferences("second_brain_settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean("force_disable_blur", false)) {
            return false
        }

        // 2. Return in-memory cached value if already computed
        cachedBlurTier?.let { return it }

        // 3. SharedPreferences persistent cache
        val perfPrefs = context.getSharedPreferences("device_performance", Context.MODE_PRIVATE)
        if (perfPrefs.contains("device_blur_tier")) {
            val cachedValue = perfPrefs.getBoolean("device_blur_tier", false)
            cachedBlurTier = cachedValue
            return cachedValue
        }

        // 4. Compute once
        val computedValue = computeBlurTier(context)
        
        // Save to cache
        perfPrefs.edit().putBoolean("device_blur_tier", computedValue).apply()
        cachedBlurTier = computedValue
        
        return computedValue
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
