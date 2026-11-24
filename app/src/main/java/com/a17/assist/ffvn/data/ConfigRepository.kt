package com.a17.assist.ffvn.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.a17.assist.ffvn.engine.SafetyGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "a17_assist_prefs")

class ConfigRepository private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val keyConfigJson = stringPreferencesKey("config_json_v1")

    private val defaultRawJson: String by lazy {
        runCatching {
            appContext.assets.open("config.json").bufferedReader().use { it.readText() }
        }.getOrElse {
            ConfigSchemaValidator.json.encodeToString(AppConfig.serializer(), AppConfig())
        }
    }

    private val defaultConfig: AppConfig by lazy {
        when (val result = ConfigSchemaValidator.validateAndParse(defaultRawJson)) {
            is ConfigSchemaValidator.ValidationResult.Valid -> result.config
            is ConfigSchemaValidator.ValidationResult.Invalid -> SafetyGuard.enforceConfigCaps(AppConfig())
        }
    }

    private val _configFlow = MutableStateFlow(defaultConfig)
    val configFlow: StateFlow<AppConfig> = _configFlow.asStateFlow()

    init {
        scope.launch {
            val prefs = appContext.dataStore.data.first()
            val savedJson = prefs[keyConfigJson]
            if (!savedJson.isNullOrBlank()) {
                val parsed = ConfigSchemaValidator.validateAndParse(savedJson)
                if (parsed is ConfigSchemaValidator.ValidationResult.Valid) {
                    _configFlow.value = parsed.config
                } else {
                    persistConfig(defaultConfig)
                }
            } else {
                persistConfig(defaultConfig)
            }
        }
    }

    fun current(): AppConfig = _configFlow.value

    fun update(transform: (AppConfig) -> AppConfig) {
        val next = SafetyGuard.enforceConfigCaps(transform(_configFlow.value))
        _configFlow.value = next
        scope.launch { persistConfig(next) }
    }

    fun setMaster(enabled: Boolean) {
        update { it.copy(master = enabled) }
    }

    fun saveBubblePosition(x: Int, y: Int) {
        update { cfg ->
            cfg.copy(calibration = cfg.calibration.copy(bubbleX = x, bubbleY = y))
        }
    }

    fun exportJson(): String {
        return ConfigSchemaValidator.json.encodeToString(AppConfig.serializer(), _configFlow.value)
    }

    fun importJson(rawJson: String): ConfigSchemaValidator.ValidationResult {
        val result = ConfigSchemaValidator.validateAndParse(rawJson)
        if (result is ConfigSchemaValidator.ValidationResult.Valid) {
            _configFlow.value = result.config
            scope.launch { persistConfig(result.config) }
        }
        return result
    }

    fun resetToDefault() {
        val fresh = SafetyGuard.enforceConfigCaps(defaultConfig)
        _configFlow.value = fresh
        scope.launch { persistConfig(fresh) }
    }

    private suspend fun persistConfig(config: AppConfig) {
        val encoded = ConfigSchemaValidator.json.encodeToString(AppConfig.serializer(), config)
        appContext.dataStore.edit { prefs ->
            prefs[keyConfigJson] = encoded
        }
    }

    companion object {
        @Volatile
        private var instance: ConfigRepository? = null

        fun getInstance(context: Context): ConfigRepository {
            return instance ?: synchronized(this) {
                instance ?: ConfigRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
