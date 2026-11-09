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

package com.example.web.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Current lock state of a note.
 */
sealed interface NoteLockState {
    /** The note is not actively locked and can be edited. */
    object Unlocked : NoteLockState

    /** The note is currently open and being edited in the Android mobile app. */
    data class LockedByMobile(val noteId: String) : NoteLockState

    /** The note is currently locked by a web companion session. */
    data class LockedByWeb(val noteId: String, val clientIp: String) : NoteLockState
}

/**
 * Result of attempting to acquire a write lock on a note.
 */
sealed interface LockAcquisitionResult {
    /** Lock was successfully acquired. */
    object Granted : LockAcquisitionResult

    /** Denied because the note is currently open in the Android editor. */
    data class ConflictMobile(val noteId: String) : LockAcquisitionResult

    /** Denied because another web client is currently editing this note. */
    data class ConflictWeb(val noteId: String, val holderIp: String) : LockAcquisitionResult
}

/**
 * Metadata for a web client's active edit lock.
 */
data class WebClientLock(
    val noteId: String,
    val sessionToken: String,
    val clientIp: String,
    val acquiredAt: Long,
    var lastHeartbeatAt: Long
)

/**
 * Thread-safe registry that tracks active note editing sessions across mobile and web.
 *
 * Ensures:
 * 1. Notes open in the Android app editor cannot be overwritten by web companion requests.
 * 2. Web companion sessions can lock notes with heartbeat expiry (preventing abandoned locks).
 * 3. Reactive state flows allow the UI and server to observe lock status in real time.
 */
object EditorSessionRegistry {

    private val lock = Any()

    /** Heartbeat timeout for web client locks (60 seconds). */
    var webLockHeartbeatTimeoutMs: Long = 60_000L

    /** Time provider function (configurable for deterministic testing). */
    var timeProvider: () -> Long = System::currentTimeMillis

    private val _mobileActiveNoteId = MutableStateFlow<String?>(null)

    /** StateFlow observing the note ID currently open in the Android mobile editor (or null). */
    val mobileActiveNoteId: StateFlow<String?> = _mobileActiveNoteId.asStateFlow()

    private val webLocksByNoteId = ConcurrentHashMap<String, WebClientLock>()

    /**
     * Registers a note as actively being edited on the mobile device.
     */
    fun acquireMobileLock(noteId: String) = synchronized(lock) {
        if (noteId.isNotBlank()) {
            _mobileActiveNoteId.value = noteId
        }
    }

    /**
     * Releases the mobile edit lock. If [noteId] is specified, only releases if it matches.
     */
    fun releaseMobileLock(noteId: String? = null) = synchronized(lock) {
        if (noteId == null || _mobileActiveNoteId.value == noteId) {
            _mobileActiveNoteId.value = null
        }
    }

    /**
     * Returns true if the note with [noteId] is currently being edited on the phone.
     */
    fun isNoteLockedOnMobile(noteId: String): Boolean {
        if (noteId.isBlank()) return false
        return _mobileActiveNoteId.value == noteId
    }

    /**
     * Attempts to acquire an exclusive edit lock for a web companion session.
     */
    fun acquireWebLock(noteId: String, sessionToken: String, clientIp: String): LockAcquisitionResult = synchronized(lock) {
        if (noteId.isBlank()) return LockAcquisitionResult.ConflictMobile(noteId)

        // 1. Mobile app takes absolute precedence
        if (_mobileActiveNoteId.value == noteId) {
            return LockAcquisitionResult.ConflictMobile(noteId)
        }

        val now = timeProvider()
        val existing = webLocksByNoteId[noteId]

        // 2. Check if existing web lock is still active
        if (existing != null) {
            val isExpired = (now - existing.lastHeartbeatAt) > webLockHeartbeatTimeoutMs
            if (!isExpired && existing.sessionToken != sessionToken) {
                return LockAcquisitionResult.ConflictWeb(noteId = noteId, holderIp = existing.clientIp)
            }
        }

        // 3. Grant lock to this web session
        val newLock = WebClientLock(
            noteId = noteId,
            sessionToken = sessionToken,
            clientIp = clientIp,
            acquiredAt = now,
            lastHeartbeatAt = now
        )
        webLocksByNoteId[noteId] = newLock
        return LockAcquisitionResult.Granted
    }

    /**
     * Renews the heartbeat on an existing web lock so it doesn't expire.
     */
    fun refreshWebHeartbeat(noteId: String, sessionToken: String): Boolean = synchronized(lock) {
        val current = webLocksByNoteId[noteId] ?: return false
        if (_mobileActiveNoteId.value != noteId && current.sessionToken == sessionToken &&
            timeProvider() - current.lastHeartbeatAt <= webLockHeartbeatTimeoutMs) {
            current.lastHeartbeatAt = timeProvider()
            return true
        }
        return false
    }

    /**
     * Releases a web client edit lock.
     */
    fun releaseWebLock(noteId: String, sessionToken: String): Boolean = synchronized(lock) {
        val current = webLocksByNoteId[noteId] ?: return false
        if (current.sessionToken == sessionToken) {
            webLocksByNoteId.remove(noteId)
            return true
        }
        return false
    }

    /**
     * Determines whether a given note can currently be written to by [sessionToken].
     */
    fun canWriteNote(noteId: String, sessionToken: String? = null): LockAcquisitionResult = synchronized(lock) {
        if (noteId.isBlank()) return LockAcquisitionResult.ConflictMobile(noteId)

        // Mobile lock active -> always reject web writes
        if (_mobileActiveNoteId.value == noteId) {
            return LockAcquisitionResult.ConflictMobile(noteId)
        }

        val now = timeProvider()
        val existing = webLocksByNoteId[noteId] ?: return LockAcquisitionResult.Granted
        val isExpired = (now - existing.lastHeartbeatAt) > webLockHeartbeatTimeoutMs

        if (isExpired) {
            webLocksByNoteId.remove(noteId)
            return LockAcquisitionResult.Granted
        }

        return if (sessionToken != null && existing.sessionToken == sessionToken) {
            LockAcquisitionResult.Granted
        } else {
            LockAcquisitionResult.ConflictWeb(noteId, existing.clientIp)
        }
    }

    /**
     * Returns the current lock state of [noteId].
     */
    fun getNoteLockState(noteId: String): NoteLockState = synchronized(lock) {
        if (_mobileActiveNoteId.value == noteId) {
            return NoteLockState.LockedByMobile(noteId)
        }

        val now = timeProvider()
        val webLock = webLocksByNoteId[noteId]
        if (webLock != null) {
            val isExpired = (now - webLock.lastHeartbeatAt) > webLockHeartbeatTimeoutMs
            if (!isExpired) {
                return NoteLockState.LockedByWeb(noteId, webLock.clientIp)
            } else {
                webLocksByNoteId.remove(noteId)
            }
        }

        return NoteLockState.Unlocked
    }

    /**
     * Clears all locks (e.g. when server stops or for testing).
     */
    fun clear() = synchronized(lock) {
        _mobileActiveNoteId.value = null
        webLocksByNoteId.clear()
    }

    fun releaseSession(sessionToken: String) = synchronized(lock) {
        webLocksByNoteId.entries.removeIf { it.value.sessionToken == sessionToken }
    }

    fun clearWebLocks() = synchronized(lock) { webLocksByNoteId.clear() }
}
