package com.example.util

/** Do not emit supplied messages/throwables: they may contain notes, URLs or credentials. */
@Suppress("UNUSED_PARAMETER")
object SafeLogger {
    private fun safeTag(value: String) = value.take(40).replace(Regex("[^A-Za-z0-9_.-]"), "_")
    fun d(tag: String, message: String?, error: Throwable? = null): Int =
        if (com.example.BuildConfig.DEBUG) android.util.Log.d(safeTag(tag), "Debug event") else 0
    fun i(tag: String, message: String?, error: Throwable? = null): Int =
        if (com.example.BuildConfig.DEBUG) android.util.Log.i(safeTag(tag), "Application event") else 0
    fun w(tag: String, message: String?, error: Throwable? = null): Int = android.util.Log.w(safeTag(tag), "Operation needs attention")
    fun e(tag: String, message: String?, error: Throwable? = null): Int = android.util.Log.e(safeTag(tag), "Operation failed; inspect in-app recovery")
}
