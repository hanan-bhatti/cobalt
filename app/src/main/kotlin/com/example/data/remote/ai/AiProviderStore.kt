package com.example.data.remote.ai

import android.content.Context
import com.example.util.SecretPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Presets describe transport and setup links, never imply model capabilities. */
enum class AiProvider(val label: String, val baseUrl: String, val keyUrl: String?) {
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/", "https://aistudio.google.com/apikey"),
    OPENAI("OpenAI", "https://api.openai.com/v1/", "https://platform.openai.com/api-keys"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1/", "https://openrouter.ai/settings/keys"),
    GROQ("Groq", "https://api.groq.com/openai/v1/", "https://console.groq.com/keys"),
    CUSTOM("OpenAI-compatible", "", null);
    companion object {
        fun from(value: String?) = entries.firstOrNull { it.name == value } ?: GEMINI
    }
}

/** Deliberately not a data class: never include credentials in generated toString(). */
class AiConnection(val provider: AiProvider, val baseUrl: String, val key: String, val model: String) {
    fun requireReady() {
        require(baseUrl.isNotBlank()) { "Save the provider’s API base URL in AI models." }
        require(key.isNotBlank() || provider == AiProvider.CUSTOM) { "Save an API key for ${provider.label} in AI models." }
        require(model.isNotBlank()) { "Choose a model in AI models first." }
    }
}

class AiProviderStore(context: Context) {
    private val prefs = context.getSharedPreferences("cobalt_settings", Context.MODE_PRIVATE)
    private val secrets = SecretPreferences(prefs, "cobalt_settings")
    fun provider(audio: Boolean = false) = AiProvider.from(prefs.getString(if (audio) "ai_audio_provider" else "ai_image_provider", null))
    fun select(provider: AiProvider, audio: Boolean = false) {
        prefs.edit().putString(if (audio) "ai_audio_provider" else "ai_image_provider", provider.name).apply()
        changed()
    }
    fun key(provider: AiProvider): String = secrets.get(if (provider == AiProvider.GEMINI) "gemini_api_key" else "ai_key_${provider.name}").orEmpty()
    fun endpoint(provider: AiProvider) = if (provider == AiProvider.CUSTOM) prefs.getString("ai_custom_url", "").orEmpty() else provider.baseUrl
    fun model(provider: AiProvider, audio: Boolean = false): String {
        val name = if (provider == AiProvider.GEMINI) {
            if (audio) "selected_audio_model" else "selected_model"
        } else "ai_model_${provider.name}_${if (audio) "audio" else "image"}"
        return prefs.getString(name, "").orEmpty()
    }
    fun saveModel(provider: AiProvider, model: String, audio: Boolean) {
        require(model.isNotBlank() && model.length <= 256) { "Enter a model ID with at most 256 characters." }
        val name = if (provider == AiProvider.GEMINI) {
            if (audio) "selected_audio_model" else "selected_model"
        } else "ai_model_${provider.name}_${if (audio) "audio" else "image"}"
        prefs.edit().putString(name, model.trim()).apply()
        changed()
    }
    fun saveConnection(provider: AiProvider, key: String, endpoint: String, keyPage: String) {
        val normalized = if (provider == AiProvider.CUSTOM) validateUrl(endpoint, allowLocalHttp = true) else provider.baseUrl
        val page = keyPage.trim().takeIf { it.isNotEmpty() }?.let { validateUrl(it, allowLocalHttp = false) }
        secrets.putAll(mapOf((if (provider == AiProvider.GEMINI) "gemini_api_key" else "ai_key_${provider.name}") to key.trim()))
        if (provider == AiProvider.CUSTOM) prefs.edit().putString("ai_custom_url", normalized).putString("ai_custom_key_page", page.orEmpty()).apply()
        prefs.edit().remove("ai_models_${provider.name}").apply()
        changed()
    }
    fun keyPage(provider: AiProvider) = provider.keyUrl ?: prefs.getString("ai_custom_key_page", "").orEmpty().ifBlank { null }
    fun connection(audio: Boolean = false): AiConnection {
        val p = provider(audio)
        return AiConnection(p, endpoint(p), key(p), model(p, audio))
    }
    fun legacyPrompt() = prefs.getString("custom_ocr_prompt", "").orEmpty().takeIf { !prefs.getBoolean("ai_prompt_additive", false) }.orEmpty()
    fun prompt() = prefs.getString("custom_ocr_prompt", "").orEmpty()
    fun savePrompt(value: String) {
        prefs.edit().putString("custom_ocr_prompt", value).putBoolean("ai_prompt_additive", true).apply()
        changed()
    }
    fun cachedModels(provider: AiProvider): List<AiModel> = runCatching {
        val array = JSONArray(prefs.getString("ai_models_${provider.name}", "[]"))
        (0 until array.length()).map { i -> array.getJSONObject(i).let { AiModel(it.getString("id"), it.optString("label"), it.optString("support", "Not checked")) } }
    }.getOrDefault(emptyList())
    fun cacheModels(provider: AiProvider, values: List<AiModel>) {
        val array = JSONArray()
        values.forEach { array.put(JSONObject().put("id", it.id).put("label", it.label).put("support", it.support)) }
        prefs.edit().putString("ai_models_${provider.name}", array.toString()).apply()
    }
    companion object {
        private val revision = MutableStateFlow(0L)
        val changes = revision.asStateFlow()
        private fun changed() { revision.value += 1 }
        fun validateUrl(value: String, allowLocalHttp: Boolean): String {
            val url = value.trim().toHttpUrl()
            require(url.username.isBlank() && url.password.isBlank() && url.query == null && url.fragment == null) { "Use a URL without credentials, query parameters or a fragment." }
            val host = url.host
            val octets = host.split('.').map { it.toIntOrNull() ?: -1 }
            val ipv4 = octets.size == 4 && octets.all { it in 0..255 }
            val privateHost = host == "localhost" || host == "::1" || host.endsWith(".local") ||
                (ipv4 && (octets[0] == 127 || octets[0] == 10 ||
                    (octets[0] == 192 && octets[1] == 168) || (octets[0] == 172 && octets[1] in 16..31)))
            require(url.isHttps || (allowLocalHttp && privateHost)) { "Use HTTPS. HTTP is supported only for a local server." }
            return url.toString().let { if (it.endsWith('/')) it else "$it/" }
        }
    }
}

data class AiModel(val id: String, val label: String = id, val support: String = "Not checked")
