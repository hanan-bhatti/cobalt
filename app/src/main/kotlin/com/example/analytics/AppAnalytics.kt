package com.example.analytics

import android.os.Bundle

interface AppAnalytics {
    fun logEvent(eventName: String, params: Bundle? = null)
    fun logScreenView(screenName: String, screenClass: String)
    fun logNoteCreated(itemId: String, itemType: String)
    fun logNoteDeleted(itemId: String, itemType: String)
    fun logNoteEdited(itemId: String, itemType: String)
    fun logSearchPerformed()
    fun logWidgetAdded()
    fun logSignInSuccess(provider: String)
    fun logSignInFailed(provider: String, error: String)
}
