package com.example.utils

import android.app.AppOpsManager
import android.content.Context
import android.os.Build

object PermissionUtils {
    /**
     * Safely checks if the app has the overlay permission (draw over other apps).
     * By using AppOpsManager checkOp first, we avoid generating the "Operation not started: op=SYSTEM_ALERT_WINDOW"
     * system alert error log when the permission is not granted.
     */
    fun hasOverlayPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }
        return android.provider.Settings.canDrawOverlays(context)
    }
}
