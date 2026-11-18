package com.example.ui.dock

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SideDockPreferencesTest {
    private lateinit var context: Context

    @Before
    fun isolatePreferences() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("cobalt_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun presetWritesOneCoherentConfigurationAndSurvivesANewRepository() {
        val prefs = context.getSharedPreferences("cobalt_settings", Context.MODE_PRIVATE)
        val snapshots = mutableListOf<Triple<String?, Float, Float>>()
        val observer = android.content.SharedPreferences.OnSharedPreferenceChangeListener { changed, key ->
            if (key?.startsWith("edge_panel_anim_") == true) snapshots += Triple(
                changed.getString("edge_panel_anim_preset", null),
                changed.getFloat("edge_panel_anim_stiffness", -1f), changed.getFloat("edge_panel_anim_damping", -1f)
            )
        }
        prefs.registerOnSharedPreferenceChangeListener(observer)
        SettingsRepository(context).applyEdgePanelAnimPreset("Bouncy")
        assertEquals(setOf(Triple("Bouncy", 380f, 0.52f)), snapshots.toSet())
        val restored = SettingsRepository(context)
        assertEquals("Bouncy", restored.edgePanelAnimPreset.value)
        assertEquals(380f, restored.edgePanelAnimStiffness.value, 0f)
        assertEquals(0.52f, restored.edgePanelAnimDamping.value, 0f)
        prefs.unregisterOnSharedPreferenceChangeListener(observer)
    }

    @Test
    fun customEditsPreserveBothValuesAndTheCustomSelection() {
        SettingsRepository(context).setCustomEdgePanelAnimation(510f, 0.79f)
        val restored = SettingsRepository(context)
        assertEquals("Custom", restored.edgePanelAnimPreset.value)
        assertEquals(510f, restored.edgePanelAnimStiffness.value, 0f)
        assertEquals(0.79f, restored.edgePanelAnimDamping.value, 0f)
    }
}
