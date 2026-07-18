/*
 * Second Brain - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 */

package com.example.utils

object AnalyticsEvents {
    // Custom Events
    const val NOTE_CREATED = "note_created"
    const val NOTE_DELETED = "note_deleted"
    const val NOTE_EDITED = "note_edited"
    const val SEARCH_PERFORMED = "search_performed"
    const val WIDGET_ADDED = "widget_added"
    const val SIGN_IN_SUCCESS = "sign_in_success"
    const val SIGN_IN_FAILED = "sign_in_failed"

    // Custom Parameters
    const val PARAM_ITEM_ID = "item_id"
    const val PARAM_ITEM_TYPE = "item_type"
    const val PARAM_SIGN_IN_METHOD = "sign_in_method"
    const val PARAM_ERROR_MESSAGE = "error_message"
}
