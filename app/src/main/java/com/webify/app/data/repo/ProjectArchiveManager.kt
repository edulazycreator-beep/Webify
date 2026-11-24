package com.webify.app.data.repo

import android.content.Context
import android.net.Uri
import com.webify.app.data.db.WebProjectDao
import com.webify.app.data.db.WebProjectEntity
import com.webify.app.domain.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Format bundle `.webify` (ZIP):
 *   manifest.json        — versi format & info pembuat
 *   project.json         — seluruh konfigurasi WebProjectEntity (kotlinx.serialization)
 *   icon.png             — ikon proyek (opsional)
 *   web_assets.zip       — situs HTML/CSS/JS lokal (opsional, LOCAL_HTML_ZIP)
 *   google-services.json — konfigurasi Firebase (opsional)
 */
class ProjectArchiveManager(private val context: Context, private val dao: WebProjectDao) {

    @Serializable
    data class ArchiveInfo(val format: Int = FORMAT, val createdBy: String = "Webify", val exportedAt: Long = System.currentTimeMillis())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    /** Ekspor ke cacheDir/exports/<nama>.webify lalu kembalikan file-nya (dibagikan via FileProvider). */
    suspend fun export(project: WebProjectEntity): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = project.appName.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { "project" }
        val out = File(dir, "$safe-v${project.versionName}.webify")
        ZipOutputStream(out.outputStream().buffered()).use { zos ->
            fun put(name: String, bytes: ByteArray) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
            put("manifest.json", json.encodeToString(ArchiveInfo.serializer(), ArchiveInfo()).toByteArray())
            // Path lokal tidak dibawa; diganti file di dalam arsip
            val portable = project.copy(iconUri = null, localZipPath = null, googleServicesJsonPath = null, selectedKeystoreId = null, localLibraries = "")
            put("project.json", json.encodeToString(WebProjectEntity.serializer(), portable).toByteArray())
            project.iconUri?.let { File(it) }?.takeIf { it.exists() }?.let { put("icon.png", it.readBytes()) }
            project.localZipPath?.let { File(it) }?.takeIf { it.exists() }?.let { put("web_assets.zip", it.readBytes()) }
            project.googleServicesJsonPath?.let { File(it) }?.takeIf { it.exists() }?.let { put("google-services.json", it.readBytes()) }
        }
        out
    }

    /** Impor bundle .webify sebagai proyek baru (ID baru, package dibuat unik bila bentrok). */
    suspend fun importBundle(uri: Uri): WebProjectEntity = withContext(Dispatchers.IO) {
        val entries = HashMap<String, ByteArray>()
        val input = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("File tidak dapat dibaca")
        ZipInputStream(input.buffered()).use { zis ->
            var total = 0L
            while (true) {
                val e = zis.nextEntry ?: break
                if (e.isDirectory) continue
                val name = e.name.substringAfterLast('/')
                if (name !in ALLOWED) continue
                val bytes = zis.readBytes()
                total += bytes.size
                if (total > MAX_SIZE) throw IllegalArgumentException("Arsip terlalu besar (maks ${MAX_SIZE / 1024 / 1024} MB)")
                entries[name] = bytes
            }
        }
        val raw = entries["project.json"] ?: throw IllegalArgumentException("Bukan file .webify yang valid (project.json tidak ada)")
        entries["manifest.json"]?.let {
            val info = runCatching { json.decodeFromString(ArchiveInfo.serializer(), String(it)) }.getOrNull()
            if (info != null && info.format > FORMAT) throw IllegalArgumentException("Bundle dibuat oleh Webify versi lebih baru (format ${info.format})")
        }
        val parsed = try {
            json.decodeFromString(WebProjectEntity.serializer(), String(raw, Charsets.UTF_8))
        } catch (e: Exception) {
            throw IllegalArgumentException("project.json rusak: ${e.message}", e)
        }

        val newId = UUID.randomUUID().toString()
        val assetDir = File(context.filesDir, "project_assets/$newId").apply { mkdirs() }
        val icon = entries["icon.png"]?.let { b -> File(assetDir, "icon_${System.currentTimeMillis()}").also { it.writeBytes(b) }.absolutePath }
        val site = entries["web_assets.zip"]?.let { b -> File(assetDir, "site.zip").also { it.writeBytes(b) }.absolutePath }
        val gs = entries["google-services.json"]?.let { b -> File(assetDir, "google-services.json").also { it.writeBytes(b) }.absolutePath }

        var pkg = parsed.packageName
        var n = 1
        while (dao.countPackage(pkg, newId) > 0) {
            pkg = parsed.packageName + "_import" + (if (n == 1) "" else n.toString())
            n++
        }

        val project = parsed.copy(
            id = newId,
            packageName = pkg,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            iconUri = icon,
            localZipPath = site,
            sourceType = if (parsed.sourceType == SourceType.LOCAL_HTML_ZIP && site == null) SourceType.URL else parsed.sourceType,
            googleServicesJsonPath = gs,
            fcmEnabled = parsed.fcmEnabled && gs != null,
            selectedKeystoreId = null,
            localLibraries = ""
        )
        dao.upsert(project)
        project
    }

    companion object {
        const val FORMAT = 1
        private const val MAX_SIZE = 200L * 1024 * 1024
        private val ALLOWED = setOf("manifest.json", "project.json", "icon.png", "web_assets.zip", "google-services.json")
    }
}
