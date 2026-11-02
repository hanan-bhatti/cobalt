package com.example.notes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.util.SecretPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialStorageTest {
    @Test fun migratesPlaintextAndRetainsTamperedCiphertextForRecovery() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.endsWith(".debug"))
        val name = "credential-test-${java.util.UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        try {
            prefs.edit().putString("token", "test-secret").commit()
            val store = SecretPreferences(prefs, name)
            assertEquals("test-secret", store.get("token"))
            val encrypted = prefs.getString("token", null)!!
            assertTrue(encrypted.startsWith("cobalt-enc-v1:"))
            assertFalse(encrypted.contains("test-secret"))
            store.putAll(mapOf("token" to "test-secret"))
            assertNotEquals(encrypted, prefs.getString("token", null)) // Fresh IV per encryption.
            val damaged = "cobalt-enc-v1:invalid"
            prefs.edit().putString("token", damaged).commit()
            assertNull(store.get("token"))
            assertEquals(damaged, prefs.getString("token", null))
            assertTrue(prefs.getBoolean("credential_storage_attention", false))
        } finally { context.deleteSharedPreferences(name) }
    }
}
