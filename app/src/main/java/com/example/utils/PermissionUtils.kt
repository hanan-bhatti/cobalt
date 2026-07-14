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

        try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            if (appOps != null) {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpRaw(
                        AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                        android.os.Process.myUid(),
                        context.packageName
                    )
                } else {
                    val checkOpNoThrowMethod = AppOpsManager::class.java.getMethod(
                        "checkOpNoThrow",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        String::class.java
                    )
                    checkOpNoThrowMethod.invoke(
                        appOps,
                        24, // AppOpsManager.OP_SYSTEM_ALERT_WINDOW / OP_SYSTEM_ALERT_WINDOW constant is 24
                        android.os.Process.myUid(),
                        context.packageName
                    ) as Int
                }
                return mode == AppOpsManager.MODE_ALLOWED
            }
        } catch (e: Exception) {
            // Fallback to Settings.canDrawOverlays if AppOps check fails or throws
        }

        return android.provider.Settings.canDrawOverlays(context)
    }
}
