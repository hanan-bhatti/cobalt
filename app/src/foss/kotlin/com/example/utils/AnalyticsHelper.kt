/*
 * Cobalt - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 *
 * FOSS flavor: all analytics calls are no-ops (no Firebase Analytics dependency).
 */

package com.example.utils

import android.content.Context

object AnalyticsHelper {
    fun logEvent(context: Context, eventName: String, params: android.os.Bundle? = null) = Unit
    fun logNoteCreated(context: Context, itemId: String, itemType: String) = Unit
    fun logNoteDeleted(context: Context, itemId: String, itemType: String) = Unit
    fun logNoteEdited(context: Context, itemId: String, itemType: String) = Unit
    fun logSearchPerformed(context: Context) = Unit
    fun logWidgetAdded(context: Context) = Unit
    fun logSignInSuccess(context: Context, method: String) = Unit
    fun logSignInFailed(context: Context, method: String, error: String) = Unit
}
