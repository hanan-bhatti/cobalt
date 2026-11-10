/*
 * Cobalt - A universal capture and personal knowledge archive
 * Copyright (C) 2026 Hanan Bhatti
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.web.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class WebCompanionSecurityManagerTest {

    @Test
    fun pinFormatIsAlwaysExactlyFourDigits() {
        val manager = WebCompanionSecurityManager()
        for (i in 0 until 50) {
            val pin = manager.rotatePin()
            assertEquals("PIN must be exactly 4 characters long", 4, pin.length)
            assertTrue("PIN must consist only of digits", pin.all { it.isDigit() })
        }
    }

    @Test
    fun successfulPinVerificationIssuesToken() {
        val manager = WebCompanionSecurityManager()
        val pin = manager.currentPin
        val result = manager.verifyPin(pin, "192.168.1.100")

        assertTrue("Verification should succeed with correct PIN", result is AuthResult.Success)
        val success = result as AuthResult.Success
        assertEquals("Token should be a 64-character hex string (256 bits)", 64, success.token.length)
        assertTrue("Session should be valid immediately after issue", manager.isValidSession(success.token, "192.168.1.100"))
    }

    @Test
    fun incorrectPinReturnsInvalidPinWithRemainingAttempts() {
        val manager = WebCompanionSecurityManager(maxAttemptsPerIp = 5)
        val wrongPin = "9999"
        val realPin = if (manager.currentPin == wrongPin) "0000" else wrongPin

        val result1 = manager.verifyPin(realPin, "192.168.1.50")
        assertTrue(result1 is AuthResult.InvalidPin)
        assertEquals(4, (result1 as AuthResult.InvalidPin).remainingAttempts)

        val result2 = manager.verifyPin(realPin, "192.168.1.50")
        assertTrue(result2 is AuthResult.InvalidPin)
        assertEquals(3, (result2 as AuthResult.InvalidPin).remainingAttempts)
    }

    @Test
    fun fiveFailedAttemptsTriggersLockout() {
        val manager = WebCompanionSecurityManager(maxAttemptsPerIp = 5, lockoutDurationMs = 60_000L)
        val wrongPin = "wrong"

        repeat(4) {
            val res = manager.verifyPin(wrongPin, "192.168.1.25")
            assertTrue(res is AuthResult.InvalidPin)
        }

        val fifthAttempt = manager.verifyPin(wrongPin, "192.168.1.25")
        assertTrue("5th failed attempt must trigger lockout", fifthAttempt is AuthResult.LockedOut)

        // Subsequent attempts during lockout should be rejected immediately
        val duringLockout = manager.verifyPin(manager.currentPin, "192.168.1.25")
        assertTrue("Even correct PIN should be rejected during lockout", duringLockout is AuthResult.LockedOut)
    }

    @Test
    fun lockoutExpiresAfterConfiguredDuration() {
        val fakeTime = AtomicLong(100_000L)
        val manager = WebCompanionSecurityManager(
            maxAttemptsPerIp = 3,
            lockoutDurationMs = 10_000L,
            timeProvider = { fakeTime.get() }
        )

        repeat(3) { manager.verifyPin("wrong", "192.168.1.30") }
        assertTrue(manager.verifyPin("wrong", "192.168.1.30") is AuthResult.LockedOut)

        // Advance time past the lockout duration
        fakeTime.addAndGet(11_000L)

        // Now attempt with correct PIN should succeed
        val result = manager.verifyPin(manager.currentPin, "192.168.1.30")
        assertTrue("Attempt after lockout expiration should succeed with correct PIN", result is AuthResult.Success)
    }

    @Test
    fun globalTripwireRotatesPinWhenThresholdExceeded() {
        val manager = WebCompanionSecurityManager(
            maxAttemptsPerIp = 5,
            globalTripwireThreshold = 6
        )
        val originalPin = manager.currentPin

        // 3 failed attempts from IP 1
        repeat(3) { manager.verifyPin("bad", "10.0.0.1") }
        // 2 failed attempts from IP 2
        repeat(2) { manager.verifyPin("bad", "10.0.0.2") }
        // 6th global attempt triggers tripwire
        val tripwireResult = manager.verifyPin("bad", "10.0.0.3")

        assertTrue("Global threshold must trigger tripwire", tripwireResult is AuthResult.TripwireTriggered)
        assertNotEquals("PIN must be rotated when tripwire triggers", originalPin, manager.currentPin)
    }

    @Test
    fun sessionExpiresAfterInactivity() {
        val fakeTime = AtomicLong(1_000_000L)
        val manager = WebCompanionSecurityManager(
            sessionInactivityTimeoutMs = 5_000L,
            timeProvider = { fakeTime.get() }
        )

        val auth = manager.verifyPin(manager.currentPin, "192.168.1.1") as AuthResult.Success
        val token = auth.token

        assertTrue("Session should be valid immediately", manager.isValidSession(token))

        // Advance time within timeout
        fakeTime.addAndGet(3_000L)
        assertTrue("Session should remain valid when refreshed within timeout", manager.isValidSession(token))

        // Advance time past inactivity timeout
        fakeTime.addAndGet(6_000L)
        assertFalse("Session must expire after inactivity", manager.isValidSession(token))
    }

    @Test
    fun hostHeaderValidationBlocksDnsRebinding() {
        val manager = WebCompanionSecurityManager()
        val localIps = listOf("192.168.1.15", "10.0.0.5")

        assertTrue("localhost is allowed", manager.isValidHost("localhost:8080", localIps))
        assertTrue("127.0.0.1 is allowed", manager.isValidHost("127.0.0.1:8080", localIps))
        assertTrue("Local LAN IP is allowed", manager.isValidHost("192.168.1.15:8080", localIps))

        assertFalse("Arbitrary domain must be rejected", manager.isValidHost("attacker.evil.com:8080", localIps))
        assertFalse("Null host must be rejected", manager.isValidHost(null, localIps))
        assertFalse("Empty host must be rejected", manager.isValidHost("", localIps))
    }

    @Test fun numericPinIgnoresDeviceLocaleAndRotationPublishesState() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar"))
            var resets = 0
            val manager = WebCompanionSecurityManager(onSessionsRevoked = { resets++ })
            val first = manager.currentPin
            val next = manager.rotatePin()
            assertTrue(next.matches(Regex("[0-9]{4}")))
            assertNotEquals(first, next)
            assertEquals(next, manager.pinState.value)
            assertEquals(1, resets)
        } finally { java.util.Locale.setDefault(previous) }
    }
    @Test fun requestBudgetAllowsNormalEditBurstButStillLimitsFloods() {
        val time = AtomicLong(1000)
        val manager = WebCompanionSecurityManager(timeProvider = { time.get() })
        repeat(60) { assertFalse(manager.isRateLimited("local")) }
        assertTrue(manager.isRateLimited("local"))
        time.addAndGet(500)
        assertFalse(manager.isRateLimited("local"))
    }
    @Test fun ipv6LoopbackAndMalformedHostsAreHandled() {
        val manager = WebCompanionSecurityManager()
        assertTrue(manager.isValidHost("[::1]:8080", emptyList()))
        assertFalse(manager.isValidHost("localhost@evil.example", emptyList()))
        assertFalse(manager.isValidHost("localhost/path", emptyList()))
    }
}
