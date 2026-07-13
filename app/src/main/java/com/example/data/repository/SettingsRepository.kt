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

    private val _selectedModel = MutableStateFlow(prefs.getString(KEY_SELECTED_MODEL, "gemini-flash-lite-latest") ?: "gemini-flash-lite-latest")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _ocrSensitivity = MutableStateFlow(prefs.getString(KEY_OCR_SENSITIVITY, "Medium") ?: "Medium")
    val ocrSensitivity: StateFlow<String> = _ocrSensitivity.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getString(KEY_THEME_MODE, "System Default") ?: "System Default")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

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

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_OCR_SENSITIVITY = "ocr_sensitivity"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
