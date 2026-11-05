package com.example.links

import org.junit.Assert.*
import org.junit.Test

class LinkDescriptionPolicyTest {
    @Test fun manualDescriptionWinsAutomaticRetries() {
        assertEquals("Mine", LinkDescriptionPolicy.automatic("Mine", "Page", false))
    }
    @Test fun clearingDescriptionIsAnExplicitChoice() {
        assertEquals("", LinkDescriptionPolicy.automatic("", "Page", false))
    }
    @Test fun pageReplacesOnlyMissingTextOrUntouchedAiFallback() {
        assertEquals("Page", LinkDescriptionPolicy.automatic(null, "Page", false))
        assertEquals("Page", LinkDescriptionPolicy.automatic("AI", "Page", true))
        assertEquals("AI", LinkDescriptionPolicy.automatic("AI", "  ", true))
        assertNull(LinkDescriptionPolicy.automatic(null, null, false))
    }
}
