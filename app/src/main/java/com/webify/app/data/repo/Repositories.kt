package com.webify.app.data.repo

import android.content.Context
import android.net.Uri
import com.webify.app.data.db.BuildRecordDao
import com.webify.app.data.db.BuildRecordEntity
import com.webify.app.data.db.ChatDao
import com.webify.app.data.db.ChatMessageEntity
import com.webify.app.data.db.KeystoreDao
import com.webify.app.data.db.KeystoreEntity
import com.webify.app.data.db.WebProjectDao
import com.webify.app.data.db.WebProjectEntity
import com.webify.app.domain.Validators
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ProjectRepository(
    private val context: Context,
    private val dao: WebProjectDao,
    private val buildDao: BuildRecordDao
) {
    fun observeAll(): Flow<List<WebProjectEntity>> = dao.observeAll()
    fun observe(id: String): Flow<WebProjectEntity?> = dao.observe(id)
    suspend fun get(id: String) = dao.get(id)

    suspend fun create(appName: String, url: String, packageName: String): WebProjectEntity {
        Validators.packageNameError(packageName)?.let { throw IllegalArgumentException(it) }
        val p = WebProjectEntity(
            appName = appName.trim().ifBlank { "Aplikasi Saya" },
            targetUrl = url.trim(),
            packageName = packageName.trim()
        )
        dao.upsert(p)
        return p
    }

    suspend fun save(project: WebProjectEntity) {
        dao.upsert(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun findByPackage(pkg: String) = dao.findByPackage(pkg)
    suspend fun isPackageTaken(pkg: String, excludeId: String) = dao.countPackage(pkg, excludeId) > 0

    suspend fun clone(project: WebProjectEntity): WebProjectEntity = withContext(Dispatchers.IO) {
        val newId = UUID.randomUUID().toString()
        var pkg = project.packageName + "_copy"
        var n = 2
        while (dao.countPackage(pkg, newId) > 0) pkg = project.packageName + "_copy$n".also { n++ }
        val copy = project.copy(
            id = newId,
            appName = project.appName + " (Salinan)",
            packageName = pkg,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            iconUri = project.iconUri?.let { copyPrivateAsset(it, newId, "icon") },
            localZipPath = project.localZipPath?.let { copyPrivateAsset(it, newId, "site.zip") }
        )
        dao.upsert(copy)
        copy
    }

    suspend fun delete(project: WebProjectEntity) = withContext(Dispatchers.IO) {
        dao.delete(project)
        File(context.filesDir, "webify_workspaces/${project.id}").deleteRecursively()
        File(context.filesDir, "project_assets/${project.id}").deleteRecursively()
        File(context.filesDir, "webify_outputs/${project.id}").deleteRecursively()
    }

    suspend fun updateCss(id: String, css: String) = dao.updateCss(id, css)
    suspend fun updateJsFinished(id: String, js: String) = dao.updateJsFinished(id, js)

    /** Menyalin ikon pilihan pengguna ke penyimpanan privat agar tetap tersedia saat build. */
    suspend fun importIcon(projectId: String, uri: Uri): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "project_assets/$projectId").apply { mkdirs() }
        val f = File(dir, "icon_${System.currentTimeMillis()}")
        context.contentResolver.openInputStream(uri)?.use { ins -> f.outputStream().use { ins.copyTo(it) } }
            ?: throw IllegalStateException("Tidak bisa membaca gambar")
        dir.listFiles()?.filter { it.name.startsWith("icon_") && it != f }?.forEach { it.delete() }
        f.absolutePath
    }

    /** Menyalin file sembarang (google-services.json, .aar, .jar) ke project_assets/<id>/<fileName>. */
    suspend fun importAsset(projectId: String, uri: Uri, fileName: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "project_assets/$projectId").apply { mkdirs() }
        val safe = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val f = File(dir, safe)
        context.contentResolver.openInputStream(uri)?.use { ins -> f.outputStream().use { ins.copyTo(it) } }
            ?: throw IllegalStateException("Tidak bisa membaca file")
        f
    }

    fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) { null }

    fun projectAssetFile(projectId: String, name: String) = File(context.filesDir, "project_assets/$projectId/$name")

    /** Menyalin ZIP proyek web lokal (HTML/CSS/JS) ke penyimpanan privat. */
    suspend fun importSiteZip(projectId: String, uri: Uri): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "project_assets/$projectId").apply { mkdirs() }
        val f = File(dir, "site.zip")
        context.contentResolver.openInputStream(uri)?.use { ins -> f.outputStream().use { ins.copyTo(it) } }
            ?: throw IllegalStateException("Tidak bisa membaca ZIP")
        java.util.zip.ZipFile(f).use { zip ->
            val names = zip.entries().toList().map { it.name }
            if (names.none { it.endsWith("index.html", ignoreCase = true) })
                throw IllegalArgumentException("ZIP harus berisi index.html")
        }
        f.absolutePath
    }

    private fun copyPrivateAsset(path: String, newId: String, name: String): String? = try {
        val src = File(path)
        if (!src.exists()) null else {
            val dir = File(context.filesDir, "project_assets/$newId").apply { mkdirs() }
            val dst = File(dir, if (name == "icon") "icon_${System.currentTimeMillis()}" else name)
            src.copyTo(dst, overwrite = true).absolutePath
        }
    } catch (_: Exception) { null }

    fun observeBuilds(projectId: String) = buildDao.observeForProject(projectId)
    fun observeRecentBuilds() = buildDao.observeRecent()
    fun observeSuccessCount() = buildDao.observeSuccessCount()
    suspend fun insertBuild(record: BuildRecordEntity) = buildDao.insert(record)
}

class KeystoreRepository(private val dao: KeystoreDao) {
    fun observeAll(): Flow<List<KeystoreEntity>> = dao.observeAll()
    suspend fun get(id: String) = dao.get(id)
}

class ChatRepository(private val dao: ChatDao) {
    fun observe(conversationId: String) = dao.observe(conversationId)
    suspend fun recent(conversationId: String, limit: Int) = dao.recent(conversationId, limit).reversed()
    suspend fun add(message: ChatMessageEntity) = dao.insert(message)
    suspend fun clear(conversationId: String) = dao.clear(conversationId)
}
