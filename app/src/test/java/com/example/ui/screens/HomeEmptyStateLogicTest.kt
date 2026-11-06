/*
 * Cobalt - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 */

package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeEmptyStateLogicTest {

    @Test
    fun `when loading is true, returns LOADING regardless of counts`() {
        val state = resolveHomeEmptyState(
            isLoading = true,
            itemCount = 0,
            totalArchiveCount = 0,
            searchQuery = "",
            hasDismissedOnboarding = false
        )
        assertEquals(HomeEmptyState.LOADING, state)
    }

    @Test
    fun `when items are present, returns CONTENT`() {
        val state = resolveHomeEmptyState(
            isLoading = false,
            itemCount = 5,
            totalArchiveCount = 5,
            searchQuery = "",
            hasDismissedOnboarding = true
        )
        assertEquals(HomeEmptyState.CONTENT, state)
    }

    @Test
    fun `when search query is active and 0 matches, returns SEARCH_EMPTY`() {
        val state = resolveHomeEmptyState(
            isLoading = false,
            itemCount = 0,
            totalArchiveCount = 10,
            searchQuery = "swiftui",
            hasDismissedOnboarding = true
        )
        assertEquals(HomeEmptyState.SEARCH_EMPTY, state)
    }

    @Test
    fun `when total archive has items but current filter has 0 items, returns FILTER_EMPTY`() {
        val state = resolveHomeEmptyState(
            isLoading = false,
            itemCount = 0,
            totalArchiveCount = 4,
            searchQuery = "",
            hasDismissedOnboarding = true
        )
        assertEquals(HomeEmptyState.FILTER_EMPTY, state)
    }

    @Test
    fun `when total archive has items and not yet dismissed onboarding, returns FILTER_EMPTY`() {
        // Even if onboarding dismissal preference hasn't caught up, if total archive has items,
        // we must never show the first-time welcome onboarding.
        val state = resolveHomeEmptyState(
            isLoading = false,
            itemCount = 0,
            totalArchiveCount = 2,
            searchQuery = "",
            hasDismissedOnboarding = false
        )
        assertEquals(HomeEmptyState.FILTER_EMPTY, state)
    }

    @Test
    fun `when archive is entirely empty and user has dismissed onboarding, returns RETURNING_EMPTY`() {
        val state = resolveHomeEmptyState(
            isLoading = false,
            itemCount = 0,
            totalArchiveCount = 0,
            searchQuery = "",
            hasDismissedOnboarding = true
        )
        assertEquals(HomeEmptyState.RETURNING_EMPTY, state)
    }

    @Test
    fun `when archive is entirely empty and first time user, returns FIRST_TIME_WELCOME`() {
        val state = resolveHomeEmptyState(
            isLoading = false,
            itemCount = 0,
            totalArchiveCount = 0,
            searchQuery = "",
            hasDismissedOnboarding = false
        )
        assertEquals(HomeEmptyState.FIRST_TIME_WELCOME, state)
    }
}
