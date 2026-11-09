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

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class EditorSessionRegistryTest {

    private val fakeTime = AtomicLong(100_000L)

    @Before
    fun setUp() {
        EditorSessionRegistry.clear()
        EditorSessionRegistry.timeProvider = { fakeTime.get() }
        EditorSessionRegistry.webLockHeartbeatTimeoutMs = 60_000L
    }

    @After
    fun tearDown() {
        EditorSessionRegistry.clear()
        EditorSessionRegistry.timeProvider = System::currentTimeMillis
        EditorSessionRegistry.webLockHeartbeatTimeoutMs = 60_000L
    }

    @Test
    fun mobileLockPreemptsWebWritesAndWebLockAcquisition() {
        EditorSessionRegistry.acquireMobileLock("note-123")

        assertEquals("note-123", EditorSessionRegistry.mobileActiveNoteId.value)
        assertTrue(EditorSessionRegistry.isNoteLockedOnMobile("note-123"))
        assertFalse(EditorSessionRegistry.isNoteLockedOnMobile("note-999"))

        val lockState = EditorSessionRegistry.getNoteLockState("note-123")
        assertTrue(lockState is NoteLockState.LockedByMobile)
        assertEquals("note-123", (lockState as NoteLockState.LockedByMobile).noteId)

        // Web write check must be denied
        val writeResult = EditorSessionRegistry.canWriteNote("note-123", "session-token-abc")
        assertTrue("Web write must be denied when mobile has note open", writeResult is LockAcquisitionResult.ConflictMobile)

        // Web lock acquisition must be denied
        val acquireResult = EditorSessionRegistry.acquireWebLock("note-123", "session-token-abc", "192.168.1.50")
        assertTrue("Web lock acquisition must be denied when mobile has note open", acquireResult is LockAcquisitionResult.ConflictMobile)
    }

    @Test
    fun releasingMobileLockRestoresUnlockedState() {
        EditorSessionRegistry.acquireMobileLock("note-456")
        assertTrue(EditorSessionRegistry.isNoteLockedOnMobile("note-456"))

        EditorSessionRegistry.releaseMobileLock("note-456")
        assertNull(EditorSessionRegistry.mobileActiveNoteId.value)
        assertFalse(EditorSessionRegistry.isNoteLockedOnMobile("note-456"))

        val lockState = EditorSessionRegistry.getNoteLockState("note-456")
        assertEquals(NoteLockState.Unlocked, lockState)

        val writeResult = EditorSessionRegistry.canWriteNote("note-456", "session-token-abc")
        assertEquals(LockAcquisitionResult.Granted, writeResult)
    }

    @Test
    fun webLockExclusivityAcrossMultipleClients() {
        val acquireA = EditorSessionRegistry.acquireWebLock("note-789", "token-client-a", "192.168.1.10")
        assertEquals(LockAcquisitionResult.Granted, acquireA)

        val lockState = EditorSessionRegistry.getNoteLockState("note-789")
        assertTrue(lockState is NoteLockState.LockedByWeb)
        assertEquals("192.168.1.10", (lockState as NoteLockState.LockedByWeb).clientIp)

        // Client A can write
        val writeA = EditorSessionRegistry.canWriteNote("note-789", "token-client-a")
        assertEquals(LockAcquisitionResult.Granted, writeA)

        // Client B is denied
        val writeB = EditorSessionRegistry.canWriteNote("note-789", "token-client-b")
        assertTrue(writeB is LockAcquisitionResult.ConflictWeb)
        assertEquals("192.168.1.10", (writeB as LockAcquisitionResult.ConflictWeb).holderIp)

        val acquireB = EditorSessionRegistry.acquireWebLock("note-789", "token-client-b", "192.168.1.20")
        assertTrue(acquireB is LockAcquisitionResult.ConflictWeb)
    }

    @Test
    fun webLockExpiresAutomaticallyWhenHeartbeatStales() {
        EditorSessionRegistry.acquireWebLock("note-stale", "token-abandoned", "192.168.1.10")

        // Advance time past heartbeat timeout (60 seconds)
        fakeTime.addAndGet(65_000L)

        // Lock should now be considered expired and cleared
        val lockState = EditorSessionRegistry.getNoteLockState("note-stale")
        assertEquals(NoteLockState.Unlocked, lockState)

        // New client can now acquire the lock
        val acquireNew = EditorSessionRegistry.acquireWebLock("note-stale", "token-new", "192.168.1.20")
        assertEquals(LockAcquisitionResult.Granted, acquireNew)
    }

    @Test
    fun webHeartbeatRenewalExtendsLock() {
        EditorSessionRegistry.acquireWebLock("note-renew", "token-active", "192.168.1.10")

        // Advance 40 seconds (within timeout)
        fakeTime.addAndGet(40_000L)
        assertTrue(EditorSessionRegistry.refreshWebHeartbeat("note-renew", "token-active"))

        // Advance another 40 seconds (total 80s from initial, but only 40s from renewal)
        fakeTime.addAndGet(40_000L)

        // Should still be locked because heartbeat renewed it
        val lockState = EditorSessionRegistry.getNoteLockState("note-renew")
        assertTrue("Lock must remain active after heartbeat renewal", lockState is NoteLockState.LockedByWeb)
    }

    @Test
    fun explicitReleaseWebLockUnlocksImmediately() {
        EditorSessionRegistry.acquireWebLock("note-release", "token-release", "192.168.1.10")
        assertTrue(EditorSessionRegistry.getNoteLockState("note-release") is NoteLockState.LockedByWeb)

        assertTrue(EditorSessionRegistry.releaseWebLock("note-release", "token-release"))
        assertEquals(NoteLockState.Unlocked, EditorSessionRegistry.getNoteLockState("note-release"))
    }
}
