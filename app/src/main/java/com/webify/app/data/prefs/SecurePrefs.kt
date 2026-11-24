package com.webify.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class AiProvider(val label: String, val defaultBaseUrl: String, val defaultModel: String) {
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash"),
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    CUSTOM("Custom (OpenAI-compatible)", "http://localhost:11434/v1", "llama3.1")
}

data class AiSettings(
    val provider: AiProvider = AiProvider.GEMINI,
    val apiKey: String = "",
    val baseUrl: String = AiProvider.GEMINI.defaultBaseUrl,
    val model: String = AiProvider.GEMINI.defaultModel
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank() || provider == AiProvider.CUSTOM
}

data class UiSettings(val darkMode: Boolean? = null, val dynamicColor: Boolean = false)

/**
 * Penyimpanan rahasia berbasis EncryptedSharedPreferences (kunci master di Android Keystore).
 * Menyimpan password keystore per-keystoreId dan konfigurasi/API key AI.
 */
class SecurePrefs(private val context: Context) {

    private val prefs: SharedPreferences = openEncrypted()
    private val plain: SharedPreferences = context.getSharedPreferences("webify_ui", Context.MODE_PRIVATE)

    private val _ai = MutableStateFlow(readAi())
    val aiSettings: StateFlow<AiSettings> = _ai.asStateFlow()

    private val _ui = MutableStateFlow(readUi())
    val uiSettings: StateFlow<UiSettings> = _ui.asStateFlow()

    private fun openEncrypted(): SharedPreferences {
        fun create(): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context, FILE_NAME, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
        return try {
            create()
        } catch (e: Exception) {
            // Kunci master hilang/rusak (mis. setelah restore backup). Reset file terenkripsi.
            Log.e(TAG, "EncryptedSharedPreferences rusak, membuat ulang", e)
            try {
                context.deleteSharedPreferences(FILE_NAME)
                File(context.applicationInfo.dataDir, "shared_prefs/$FILE_NAME.xml").delete()
                create()
            } catch (e2: Exception) {
                Log.e(TAG, "Gagal membuat ulang prefs terenkripsi; fallback ke private prefs", e2)
                context.getSharedPreferences("${FILE_NAME}_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    // ---------- Keystore secrets ----------
    fun saveKeystorePasswords(keystoreId: String, storePassword: String, keyPassword: String) {
        prefs.edit()
            .putString("ks_store_$keystoreId", storePassword)
            .putString("ks_key_$keystoreId", keyPassword)
            .apply()
    }

    fun storePassword(keystoreId: String): String? = prefs.getString("ks_store_$keystoreId", null)
    fun keyPassword(keystoreId: String): String? = prefs.getString("ks_key_$keystoreId", null)

    fun removeKeystore(keystoreId: String) {
        prefs.edit().remove("ks_store_$keystoreId").remove("ks_key_$keystoreId").apply()
    }

    // ---------- AI ----------
    private fun readAi(): AiSettings {
        val provider = runCatching { AiProvider.valueOf(prefs.getString("ai_provider", AiProvider.GEMINI.name)!!) }
            .getOrDefault(AiProvider.GEMINI)
        return AiSettings(
            provider = provider,
            apiKey = prefs.getString("ai_key_${provider.name}", "") ?: "",
            baseUrl = prefs.getString("ai_base_${provider.name}", provider.defaultBaseUrl) ?: provider.defaultBaseUrl,
            model = prefs.getString("ai_model_${provider.name}", provider.defaultModel) ?: provider.defaultModel
        )
    }

    fun aiSettingsFor(provider: AiProvider) = AiSettings(
        provider = provider,
        apiKey = prefs.getString("ai_key_${provider.name}", "") ?: "",
        baseUrl = prefs.getString("ai_base_${provider.name}", provider.defaultBaseUrl) ?: provider.defaultBaseUrl,
        model = prefs.getString("ai_model_${provider.name}", provider.defaultModel) ?: provider.defaultModel
    )

    fun saveAi(settings: AiSettings) {
        prefs.edit()
            .putString("ai_provider", settings.provider.name)
            .putString("ai_key_${settings.provider.name}", settings.apiKey.trim())
            .putString("ai_base_${settings.provider.name}", settings.baseUrl.trim().trimEnd('/'))
            .putString("ai_model_${settings.provider.name}", settings.model.trim())
            .apply()
        _ai.value = readAi()
    }

    // ---------- UI ----------
    private fun readUi(): UiSettings {
        val mode = plain.getInt("dark_mode", -1)
        return UiSettings(
            darkMode = when (mode) { 0 -> false; 1 -> true; else -> null },
            dynamicColor = plain.getBoolean("dynamic_color", false)
        )
    }

    fun saveUi(settings: UiSettings) {
        plain.edit()
            .putInt("dark_mode", when (settings.darkMode) { false -> 0; true -> 1; null -> -1 })
            .putBoolean("dynamic_color", settings.dynamicColor)
            .apply()
        _ui.value = settings
    }

    companion object {
        private const val TAG = "SecurePrefs"
        private const val FILE_NAME = "webify_secure_prefs"
    }
}
