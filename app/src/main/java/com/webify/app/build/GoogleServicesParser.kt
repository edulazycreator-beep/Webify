package com.webify.app.build

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pengganti plugin Gradle `com.google.gms.google-services`: mem-parsing google-services.json
 * untuk package tertentu dan menghasilkan res/values/google_services.xml.
 */
object GoogleServicesParser {

    data class Config(
        val googleAppId: String,
        val gcmDefaultSenderId: String,
        val apiKey: String,
        val projectId: String,
        val databaseUrl: String?,
        val storageBucket: String?,
        val defaultWebClientId: String?
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String, packageName: String): Config {
        val root = try { json.parseToJsonElement(raw).jsonObject } catch (e: Exception) {
            throw IllegalArgumentException("Bukan file JSON yang valid")
        }
        val info = root["project_info"]?.jsonObject ?: throw IllegalArgumentException("project_info tidak ditemukan")
        val clients = root["client"]?.jsonArray ?: throw IllegalArgumentException("Daftar client kosong")
        val available = mutableListOf<String>()
        val client = clients.map { it.jsonObject }.firstOrNull { c ->
            val pkg = c["client_info"]?.jsonObject?.get("android_client_info")?.jsonObject
                ?.get("package_name")?.jsonPrimitive?.contentOrNull
            if (pkg != null) available += pkg
            pkg == packageName
        } ?: throw IllegalArgumentException(
            "Package '$packageName' tidak terdaftar. Package di file: ${available.joinToString().ifEmpty { "-" }}. " +
                "Tambahkan aplikasi Android dengan package ini di Firebase Console lalu unduh ulang."
        )

        val appId = client["client_info"]?.jsonObject?.get("mobilesdk_app_id")?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("mobilesdk_app_id tidak ditemukan")
        val apiKey = (client["api_key"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("current_key")?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("api_key tidak ditemukan")
        val webClient = (client["oauth_client"] as? JsonArray)?.map { it.jsonObject }
            ?.firstOrNull { it["client_type"]?.jsonPrimitive?.intOrNull == 3 }
            ?.get("client_id")?.jsonPrimitive?.contentOrNull
            ?: (client["services"] as? JsonObject)?.get("appinvite_service")?.jsonObject?.get("other_platform_oauth_client")
                ?.jsonArray?.map { it.jsonObject }?.firstOrNull { it["client_type"]?.jsonPrimitive?.intOrNull == 3 }
                ?.get("client_id")?.jsonPrimitive?.contentOrNull

        return Config(
            googleAppId = appId,
            gcmDefaultSenderId = info["project_number"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException("project_number tidak ditemukan"),
            apiKey = apiKey,
            projectId = info["project_id"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("project_id tidak ditemukan"),
            databaseUrl = info["firebase_url"]?.jsonPrimitive?.contentOrNull,
            storageBucket = info["storage_bucket"]?.jsonPrimitive?.contentOrNull,
            defaultWebClientId = webClient
        )
    }

    fun toValuesXml(c: Config): String = buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("""<resources>""")
        fun item(name: String, v: String?) {
            if (!v.isNullOrBlank()) appendLine("""    <string name="$name" translatable="false">${esc(v)}</string>""")
        }
        item("google_app_id", c.googleAppId)
        item("gcm_defaultSenderId", c.gcmDefaultSenderId)
        item("google_api_key", c.apiKey)
        item("google_crash_reporting_api_key", c.apiKey)
        item("project_id", c.projectId)
        item("firebase_database_url", c.databaseUrl)
        item("google_storage_bucket", c.storageBucket)
        item("default_web_client_id", c.defaultWebClientId)
        appendLine("""</resources>""")
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "\\\"").replace("'", "\\'")
}
