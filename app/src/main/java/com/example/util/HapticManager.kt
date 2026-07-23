package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * High-performance, tuned Haptic Feedback Engine for Second Brain.
 * Follows Android 12+ API 31+ VibrationEffect Composition primitives for premium tactile feel.
 * Default state: Disabled by default (user selectable via Settings).
 */
object HapticManager {
    private const val PREFS_NAME = "second_brain_haptics_prefs"
    private const val KEY_HAPTICS_ENABLED = "haptics_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Check if haptic feedback is enabled. Default is FALSE.
     */
    fun isHapticsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HAPTICS_ENABLED, false)
    }

    /**
     * Set whether haptic feedback is enabled.
     */
    fun setHapticsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HAPTICS_ENABLED, enabled).apply()
    }

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Standard Button Tap / Click Haptic Sensation.
     */
    fun performClick(context: Context, fallbackHaptic: HapticFeedback? = null) {
        if (!isHapticsEnabled(context)) return

        val vibrator = getVibrator(context) ?: run {
            fallbackHaptic?.performHapticFeedback(HapticFeedbackType.LongPress)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f)
                .compose()
            vibrator.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(12, 100)
            vibrator.vibrate(effect)
        } else {
            fallbackHaptic?.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /**
     * Heavy, Grounded Premium Long Press Sensation.
     */
    fun performLongPress(context: Context, fallbackHaptic: HapticFeedback? = null) {
        if (!isHapticsEnabled(context)) return

        val vibrator = getVibrator(context) ?: run {
            fallbackHaptic?.performHapticFeedback(HapticFeedbackType.LongPress)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_THUD
            )
        ) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.8f, 25)
                .compose()
            vibrator.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(35, 180)
            vibrator.vibrate(effect)
        } else {
            fallbackHaptic?.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /**
     * Light Swipe / Drag Threshold Tick Sensation.
     */
    fun performSwipeTick(context: Context, fallbackHaptic: HapticFeedback? = null) {
        if (!isHapticsEnabled(context)) return

        val vibrator = getVibrator(context) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.35f)
                .compose()
            vibrator.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(6, 60)
            vibrator.vibrate(effect)
        }
    }

    /**
     * Rewarding Upward Success Sensation (Saving item, submitting survey, unlocking badge).
     */
    fun performSuccess(context: Context, fallbackHaptic: HapticFeedback? = null) {
        if (!isHapticsEnabled(context)) return

        val vibrator = getVibrator(context) ?: run {
            fallbackHaptic?.performHapticFeedback(HapticFeedbackType.LongPress)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
            )
        ) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.9f, 30)
                .compose()
            vibrator.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0, 15, 30, 45),
                intArrayOf(0, 80, 160, 240),
                -1
            )
            vibrator.vibrate(effect)
        } else {
            fallbackHaptic?.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /**
     * Warning Double Thud Sensation for errors or destructive alerts.
     */
    fun performError(context: Context, fallbackHaptic: HapticFeedback? = null) {
        if (!isHapticsEnabled(context)) return

        val vibrator = getVibrator(context) ?: run {
            fallbackHaptic?.performHapticFeedback(HapticFeedbackType.LongPress)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
            val effect = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.8f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.5f, 40)
                .compose()
            vibrator.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(
                longArrayOf(0, 25, 40, 25),
                intArrayOf(0, 200, 0, 140),
                -1
            )
            vibrator.vibrate(effect)
        }
    }
}
