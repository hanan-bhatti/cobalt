package com.example.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.LayoutDirection

class RtlUtilsTest {

    @Test
    fun testIsRtlText_emptyString() {
        assertFalse(isRtlText(""))
    }

    @Test
    fun testIsRtlText_blankString() {
        assertFalse(isRtlText("   "))
    }

    @Test
    fun testIsRtlText_pureLtr() {
        assertFalse(isRtlText("Hello World"))
    }

    @Test
    fun testIsRtlText_pureRtl_arabic() {
        assertTrue(isRtlText("مرحبا بالعالم"))
    }

    @Test
    fun testIsRtlText_pureRtl_hebrew() {
        assertTrue(isRtlText("שלום עולם"))
    }

    @Test
    fun testIsRtlText_mixed_startsWithRtl() {
        assertTrue(isRtlText("مرحبا Hello"))
    }

    @Test
    fun testIsRtlText_mixed_startsWithLtr() {
        assertFalse(isRtlText("Hello مرحبا"))
    }

    @Test
    fun testIsRtlText_neutralCharacters() {
        assertFalse(isRtlText("12345 !@#$"))
    }

    @Test
    fun testGetLayoutDirectionForText_ltr() {
        assertEquals(LayoutDirection.Ltr, getLayoutDirectionForText("Hello"))
    }

    @Test
    fun testGetLayoutDirectionForText_rtl() {
        assertEquals(LayoutDirection.Rtl, getLayoutDirectionForText("مرحبا"))
    }
}
