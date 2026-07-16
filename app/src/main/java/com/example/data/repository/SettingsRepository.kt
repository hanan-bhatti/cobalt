package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("second_brain_settings", Context.MODE_PRIVATE)

    private val _geminiApiKey = MutableStateFlow(prefs.getString(KEY_GEMINI_API_KEY, "") ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _selectedModel =
        MutableStateFlow(prefs.getString(KEY_SELECTED_MODEL, "gemini-1.5-flash") ?: "gemini-1.5-flash")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _ocrSensitivity = MutableStateFlow(prefs.getString(KEY_OCR_SENSITIVITY, "Medium") ?: "Medium")
    val ocrSensitivity: StateFlow<String> = _ocrSensitivity.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getString(KEY_THEME_MODE, "Light") ?: "Light")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _isFloatingOcrEnabled = MutableStateFlow(prefs.getBoolean(KEY_FLOATING_OCR, false))
    val isFloatingOcrEnabled: StateFlow<Boolean> = _isFloatingOcrEnabled.asStateFlow()

    private val _edgePanelHeight = MutableStateFlow(prefs.getInt(KEY_EDGE_HEIGHT, 100))
    val edgePanelHeight: StateFlow<Int> = _edgePanelHeight.asStateFlow()

    private val _edgePanelThickness = MutableStateFlow(prefs.getInt(KEY_EDGE_THICKNESS, 6))
    val edgePanelThickness: StateFlow<Int> = _edgePanelThickness.asStateFlow()

    private val _edgePanelOpacity = MutableStateFlow(prefs.getFloat(KEY_EDGE_OPACITY, 0.7f))
    val edgePanelOpacity: StateFlow<Float> = _edgePanelOpacity.asStateFlow()

    private val _edgePanelSide = MutableStateFlow(prefs.getString(KEY_EDGE_SIDE, "Right") ?: "Right")
    val edgePanelSide: StateFlow<String> = _edgePanelSide.asStateFlow()

    private val _edgePanelYPercent = MutableStateFlow(prefs.getFloat(KEY_EDGE_Y_PERCENT, 0.4f))
    val edgePanelYPercent: StateFlow<Float> = _edgePanelYPercent.asStateFlow()

    private val _hasDismissedOnboarding = MutableStateFlow(prefs.getBoolean(KEY_HAS_DISMISSED_ONBOARDING, false))
    val hasDismissedOnboarding: StateFlow<Boolean> = _hasDismissedOnboarding.asStateFlow()

    private val _isRecentCapturesExpanded = MutableStateFlow(prefs.getBoolean(KEY_RECENT_CAPTURES_EXPANDED, true))
    val isRecentCapturesExpanded: StateFlow<Boolean> = _isRecentCapturesExpanded.asStateFlow()

    private val _isListView = MutableStateFlow(prefs.getBoolean(KEY_IS_LIST_VIEW, true))
    val isListView: StateFlow<Boolean> = _isListView.asStateFlow()

    fun setGeminiApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, key).apply()
        _geminiApiKey.value = key
    }

    fun setSelectedModel(model: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model).apply()
        _selectedModel.value = model
    }

    fun setOcrSensitivity(sensitivity: String) {
        prefs.edit().putString(KEY_OCR_SENSITIVITY, sensitivity).apply()
        _ocrSensitivity.value = sensitivity
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeMode.value = mode
    }

    fun setFloatingOcrEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLOATING_OCR, enabled).apply()
        _isFloatingOcrEnabled.value = enabled
    }

    fun setEdgePanelHeight(height: Int) {
        prefs.edit().putInt(KEY_EDGE_HEIGHT, height).apply()
        _edgePanelHeight.value = height
    }

    fun setEdgePanelThickness(thickness: Int) {
        prefs.edit().putInt(KEY_EDGE_THICKNESS, thickness).apply()
        _edgePanelThickness.value = thickness
    }

    fun setEdgePanelOpacity(opacity: Float) {
        prefs.edit().putFloat(KEY_EDGE_OPACITY, opacity).apply()
        _edgePanelOpacity.value = opacity
    }

    fun setEdgePanelSide(side: String) {
        prefs.edit().putString(KEY_EDGE_SIDE, side).apply()
        _edgePanelSide.value = side
    }

    fun setEdgePanelYPercent(yPercent: Float) {
        prefs.edit().putFloat(KEY_EDGE_Y_PERCENT, yPercent).apply()
        _edgePanelYPercent.value = yPercent
    }

    fun setHasDismissedOnboarding(dismissed: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_DISMISSED_ONBOARDING, dismissed).apply()
        _hasDismissedOnboarding.value = dismissed
    }

    fun setRecentCapturesExpanded(expanded: Boolean) {
        prefs.edit().putBoolean(KEY_RECENT_CAPTURES_EXPANDED, expanded).apply()
        _isRecentCapturesExpanded.value = expanded
    }

    fun setIsListView(isList: Boolean) {
        prefs.edit().putBoolean(KEY_IS_LIST_VIEW, isList).apply()
        _isListView.value = isList
    }

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            KEY_GEMINI_API_KEY -> _geminiApiKey.value = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
            KEY_SELECTED_MODEL -> _selectedModel.value =
                prefs.getString(KEY_SELECTED_MODEL, "gemini-1.5-flash") ?: "gemini-1.5-flash"

            KEY_OCR_SENSITIVITY -> _ocrSensitivity.value = prefs.getString(KEY_OCR_SENSITIVITY, "Medium") ?: "Medium"
            KEY_THEME_MODE -> _themeMode.value = prefs.getString(KEY_THEME_MODE, "System Default") ?: "System Default"
            KEY_FLOATING_OCR -> _isFloatingOcrEnabled.value = prefs.getBoolean(KEY_FLOATING_OCR, false)
            KEY_EDGE_HEIGHT -> _edgePanelHeight.value = prefs.getInt(KEY_EDGE_HEIGHT, 100)
            KEY_EDGE_THICKNESS -> _edgePanelThickness.value = prefs.getInt(KEY_EDGE_THICKNESS, 12)
            KEY_EDGE_OPACITY -> _edgePanelOpacity.value = prefs.getFloat(KEY_EDGE_OPACITY, 0.7f)
            KEY_EDGE_SIDE -> _edgePanelSide.value = prefs.getString(KEY_EDGE_SIDE, "Right") ?: "Right"
            KEY_EDGE_Y_PERCENT -> _edgePanelYPercent.value = prefs.getFloat(KEY_EDGE_Y_PERCENT, 0.4f)
            KEY_HAS_DISMISSED_ONBOARDING -> _hasDismissedOnboarding.value =
                prefs.getBoolean(KEY_HAS_DISMISSED_ONBOARDING, false)

            KEY_RECENT_CAPTURES_EXPANDED -> _isRecentCapturesExpanded.value =
                prefs.getBoolean(KEY_RECENT_CAPTURES_EXPANDED, true)

            KEY_IS_LIST_VIEW -> _isListView.value = prefs.getBoolean(KEY_IS_LIST_VIEW, true)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_OCR_SENSITIVITY = "ocr_sensitivity"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_FLOATING_OCR = "floating_ocr_enabled"
        private const val KEY_EDGE_HEIGHT = "edge_panel_height"
        private const val KEY_EDGE_THICKNESS = "edge_panel_thickness"
        private const val KEY_EDGE_OPACITY = "edge_panel_opacity"
        private const val KEY_EDGE_SIDE = "edge_panel_side"
        private const val KEY_EDGE_Y_PERCENT = "edge_panel_y_percent"
        private const val KEY_HAS_DISMISSED_ONBOARDING = "has_dismissed_onboarding"
        private const val KEY_RECENT_CAPTURES_EXPANDED = "recent_captures_expanded"
        private const val KEY_IS_LIST_VIEW = "is_list_view"
    }
}
