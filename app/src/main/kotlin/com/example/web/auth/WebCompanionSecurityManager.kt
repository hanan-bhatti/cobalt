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

import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Result of a PIN verification attempt.
 */
sealed interface AuthResult {
    /** PIN was verified successfully, session token issued. */
    data class Success(val token: String, val expiresInSeconds: Long) : AuthResult

    /** Incorrect PIN entered, client has not yet exceeded maximum attempts. */
    data class InvalidPin(val remainingAttempts: Int, val penaltyDelayMs: Long) : AuthResult

    /** Client IP is temporarily blocked due to repeated failures. */
    data class LockedOut(val remainingLockoutSeconds: Long) : AuthResult

    /** Global brute-force threshold exceeded; PIN was rotated for security. */
    data class TripwireTriggered(val newPinGenerated: Boolean) : AuthResult
}

/**
 * Metadata for active browser session tokens.
 */
data class WebSession(
    val token: String,
    val clientIp: String,
    val createdAt: Long,
    var lastActiveAt: Long
)

/**
 * Tracks failed authentication attempts from a specific client IP.
 */
private data class IpAttemptRecord(
    var failureCount: Int = 0,
    var lockedUntil: Long = 0L,
    var lastAttemptTime: Long = 0L
)

/**
 * Token bucket implementation for API rate limiting per IP.
 */
private class TokenBucket(
    val maxTokens: Double,
    val refillRatePerMs: Double,
    var currentTokens: Double,
    var lastRefillTime: Long
) {
    fun tryConsume(tokens: Double, now: Long): Boolean {
        val elapsed = (now - lastRefillTime).coerceAtLeast(0L)
        currentTokens = (currentTokens + elapsed * refillRatePerMs).coerceAtMost(maxTokens)
        lastRefillTime = now
        return if (currentTokens >= tokens) {
            currentTokens -= tokens
            true
        } else {
            false
        }
    }
}

/**
 * Security and authentication manager for the Cobalt Local Web Companion.
 *
 * Handles:
 * 1. 4-digit ephemeral PIN generation and rotation.
 * 2. Progressive lockout and brute-force mitigation per IP.
 * 3. Global tripwire protection against distributed attacks.
 * 4. In-memory session token issuance and validation with inactivity expiry.
 * 5. Token-bucket rate limiting for API requests.
 * 6. Host-header anti-DNS-rebinding protection.
 */
class WebCompanionSecurityManager(
    private val maxAttemptsPerIp: Int = 5,
    private val lockoutDurationMs: Long = 10 * 60 * 1000L, // 10 minutes
    private val sessionInactivityTimeoutMs: Long = 2 * 60 * 60 * 1000L, // 2 hours
    private val globalTripwireThreshold: Int = 12,
    private val requestsPerMinute: Int = 60,
    private val burstCapacity: Int = 20,
    private val timeProvider: () -> Long = System::currentTimeMillis,
    private val secureRandom: SecureRandom = SecureRandom()
) {

    private val lock = Any()

    @Volatile
    private var _currentPin: String = ""

    private val sessionsByToken = ConcurrentHashMap<String, WebSession>()
    private val attemptsByIp = ConcurrentHashMap<String, IpAttemptRecord>()
    private val rateBucketsByIp = ConcurrentHashMap<String, TokenBucket>()
    private val cumulativeFailedAttempts = AtomicInteger(0)

    val currentPin: String
        get() = synchronized(lock) {
            if (_currentPin.isEmpty()) {
                _currentPin = generate4DigitPin()
            }
            _currentPin
        }

    /**
     * Rotates the 4-digit PIN immediately, invalidates all existing sessions,
     * and resets lockout tracking.
     */
    fun rotatePin(): String = synchronized(lock) {
        _currentPin = generate4DigitPin()
        sessionsByToken.clear()
        attemptsByIp.clear()
        cumulativeFailedAttempts.set(0)
        _currentPin
    }

    /**
     * Verifies the submitted PIN against the active PIN.
     * Enforces rate limiting, progressive delays, and temporary lockouts.
     */
    fun verifyPin(candidate: String, clientIp: String): AuthResult = synchronized(lock) {
        val now = timeProvider()
        val record = attemptsByIp.getOrPut(clientIp) { IpAttemptRecord() }

        // Check if IP is currently locked out
        if (record.lockedUntil > now) {
            val remainingSec = ((record.lockedUntil - now) / 1000L).coerceAtLeast(1L)
            return AuthResult.LockedOut(remainingSec)
        } else if (record.lockedUntil in 1 until now) {
            // Lockout expired, reset attempts for this IP
            record.failureCount = 0
            record.lockedUntil = 0L
        }

        // Check global tripwire
        if (cumulativeFailedAttempts.get() >= globalTripwireThreshold) {
            rotatePin()
            return AuthResult.TripwireTriggered(newPinGenerated = true)
        }

        val activePin = currentPin
        val isMatch = candidate.isNotBlank() && candidate.trim() == activePin

        return if (isMatch) {
            // Successful verification: clear failures for this IP and issue session token
            record.failureCount = 0
            record.lockedUntil = 0L

            val token = generateSessionToken()
            val session = WebSession(
                token = token,
                clientIp = clientIp,
                createdAt = now,
                lastActiveAt = now
            )
            sessionsByToken[token] = session
            val expiresInSec = sessionInactivityTimeoutMs / 1000L
            AuthResult.Success(token = token, expiresInSeconds = expiresInSec)
        } else {
            // Failed verification
            record.failureCount++
            record.lastAttemptTime = now
            val totalFails = cumulativeFailedAttempts.incrementAndGet()

            if (totalFails >= globalTripwireThreshold) {
                rotatePin()
                AuthResult.TripwireTriggered(newPinGenerated = true)
            } else if (record.failureCount >= maxAttemptsPerIp) {
                record.lockedUntil = now + lockoutDurationMs
                val lockoutSec = lockoutDurationMs / 1000L
                AuthResult.LockedOut(lockoutSec)
            } else {
                val remaining = maxAttemptsPerIp - record.failureCount
                val penaltyDelay = 500L * record.failureCount // progressive penalty delay
                AuthResult.InvalidPin(remainingAttempts = remaining, penaltyDelayMs = penaltyDelay)
            }
        }
    }

    /**
     * Checks if a session token is valid and active. Updates its last-active timestamp.
     */
    fun isValidSession(token: String, clientIp: String? = null): Boolean {
        if (token.isBlank()) return false
        val session = sessionsByToken[token] ?: return false
        val now = timeProvider()

        // Check inactivity expiry
        if (now - session.lastActiveAt > sessionInactivityTimeoutMs) {
            sessionsByToken.remove(token)
            return false
        }

        // Optional IP pinning validation
        if (clientIp != null && session.clientIp != clientIp) {
            return false
        }

        session.lastActiveAt = now
        return true
    }

    /**
     * Revokes a specific session token.
     */
    fun revokeSession(token: String) {
        sessionsByToken.remove(token)
    }

    /**
     * Revokes all active session tokens.
     */
    fun revokeAllSessions() {
        sessionsByToken.clear()
    }

    /**
     * Checks if a client IP is currently rate-limited for general API requests.
     */
    fun isRateLimited(clientIp: String): Boolean {
        val now = timeProvider()
        val refillRatePerMs = requestsPerMinute.toDouble() / 60_000.0
        val bucket = rateBucketsByIp.getOrPut(clientIp) {
            TokenBucket(
                maxTokens = burstCapacity.toDouble(),
                refillRatePerMs = refillRatePerMs,
                currentTokens = burstCapacity.toDouble(),
                lastRefillTime = now
            )
        }
        return synchronized(bucket) {
            !bucket.tryConsume(1.0, now)
        }
    }

    /**
     * Anti-DNS-rebinding protection: checks if the Host header is an allowed host/IP.
     */
    fun isValidHost(hostHeader: String?, allowedAddresses: Collection<String>): Boolean {
        if (hostHeader.isNullOrBlank()) return false
        val hostOnly = hostHeader.substringBefore(':').trim().lowercase()

        if (hostOnly == "localhost" || hostOnly == "127.0.0.1" || hostOnly == "::1") {
            return true
        }

        return allowedAddresses.any { allowed ->
            val cleanAllowed = allowed.substringBefore(':').trim().lowercase()
            cleanAllowed == hostOnly
        }
    }

    /**
     * Generates a 4-digit PIN between 0000 and 9999.
     */
    private fun generate4DigitPin(): String {
        val code = secureRandom.nextInt(10000)
        return "%04d".format(code)
    }

    /**
     * Generates a 256-bit cryptographically secure hex session token.
     */
    private fun generateSessionToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Resets all internal state (used when server shuts down).
     */
    fun reset() = synchronized(lock) {
        _currentPin = ""
        sessionsByToken.clear()
        attemptsByIp.clear()
        rateBucketsByIp.clear()
        cumulativeFailedAttempts.set(0)
    }
}
