package com.a17.assist.ffvn.data

import com.a17.assist.ffvn.engine.SafetyGuard
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

object ConfigSchemaValidator {

    val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        prettyPrint = true
        isLenient = false
    }

    private val requiredRootKeys = listOf(
        "assist_strength",
        "decision_logic",
        "distance_profiles",
        "behavior_simulation",
        "finger_feedback",
        "controls_config",
        "anti_cheat_stealth",
        "safety_caps",
        "calibration"
    )

    sealed class ValidationResult {
        data class Valid(val config: AppConfig, val warnings: List<String> = emptyList()) : ValidationResult()
        data class Invalid(val errorMessage: String) : ValidationResult()
    }

    fun validateAndParse(rawJson: String): ValidationResult {
        if (rawJson.isBlank()) {
            return ValidationResult.Invalid("JSON trống, không thể nạp cấu hình.")
        }
        return try {
            val element = json.parseToJsonElement(rawJson)
            if (element !is JsonObject) {
                return ValidationResult.Invalid("Cấu trúc JSON gốc phải là một object { ... }.")
            }
            val missing = requiredRootKeys.filter { !element.containsKey(it) }
            if (missing.isNotEmpty()) {
                return ValidationResult.Invalid("Thiếu khóa bắt buộc: ${missing.joinToString(", ")}")
            }
            val parsed = json.decodeFromString(AppConfig.serializer(), rawJson)
            val clamped = SafetyGuard.enforceConfigCaps(parsed)
            val warnings = buildList {
                if (clamped != parsed) {
                    add("Một số thông số vượt giới hạn safety_caps đã được tự động hạ về mức an toàn.")
                }
            }
            ValidationResult.Valid(clamped, warnings)
        } catch (e: SerializationException) {
            ValidationResult.Invalid("Sai định dạng schema JSON: ${e.message?.take(120) ?: "Không hợp lệ"}")
        } catch (e: IllegalArgumentException) {
            ValidationResult.Invalid("Giá trị cấu hình không hợp lệ: ${e.message?.take(120) ?: "Lỗi"}")
        }
    }
}
