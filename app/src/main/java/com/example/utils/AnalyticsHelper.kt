/*
 * Second Brain - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 */

package com.example.utils

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

object AnalyticsHelper {
    private const val TAG = "AnalyticsHelper"

    fun logEvent(context: Context, eventName: String, params: Bundle? = null) {
        try {
            FirebaseAnalytics.getInstance(context).logEvent(eventName, params)
            Log.d(TAG, "Logged event: $eventName, params: $params")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log event $eventName: ${e.message}")
        }
    }

    fun logNoteCreated(context: Context, itemId: String, itemType: String) {
        val params = Bundle().apply {
            putString(AnalyticsEvents.PARAM_ITEM_ID, itemId)
            putString(AnalyticsEvents.PARAM_ITEM_TYPE, itemType)
        }
        logEvent(context, AnalyticsEvents.NOTE_CREATED, params)
    }

    fun logNoteDeleted(context: Context, itemId: String, itemType: String) {
        val params = Bundle().apply {
            putString(AnalyticsEvents.PARAM_ITEM_ID, itemId)
            putString(AnalyticsEvents.PARAM_ITEM_TYPE, itemType)
        }
        logEvent(context, AnalyticsEvents.NOTE_DELETED, params)
    }

    fun logNoteEdited(context: Context, itemId: String, itemType: String) {
        val params = Bundle().apply {
            putString(AnalyticsEvents.PARAM_ITEM_ID, itemId)
            putString(AnalyticsEvents.PARAM_ITEM_TYPE, itemType)
        }
        logEvent(context, AnalyticsEvents.NOTE_EDITED, params)
    }

    fun logSearchPerformed(context: Context) {
        logEvent(context, AnalyticsEvents.SEARCH_PERFORMED)
    }

    fun logWidgetAdded(context: Context) {
        logEvent(context, AnalyticsEvents.WIDGET_ADDED)
    }

    fun logSignInSuccess(context: Context, method: String) {
        val params = Bundle().apply {
            putString(AnalyticsEvents.PARAM_SIGN_IN_METHOD, method)
        }
        logEvent(context, AnalyticsEvents.SIGN_IN_SUCCESS, params)
    }

    fun logSignInFailed(context: Context, method: String, error: String) {
        val params = Bundle().apply {
            putString(AnalyticsEvents.PARAM_SIGN_IN_METHOD, method)
            putString(AnalyticsEvents.PARAM_ERROR_MESSAGE, error)
        }
        logEvent(context, AnalyticsEvents.SIGN_IN_FAILED, params)
    }
}
