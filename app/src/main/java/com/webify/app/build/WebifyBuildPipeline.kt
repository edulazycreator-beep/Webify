package com.webify.app.build

import android.content.Context
import android.os.Build
import com.webify.app.crypto.KeystoreManager
import com.webify.app.data.db.WebProjectEntity
import com.webify.app.domain.BuildType
import com.webify.app.domain.SourceType
import com.webify.app.domain.Validators
import com.webify.app.template.JavaTemplateGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Orkestrator pipeline kompilasi lokal 7 tahap. Memancarkan progres, tahap aktif, dan log real-time.
 */
class WebifyBuildPipeline(
    private val context: Context,
    private val toolchain: Toolchain,
    private val keystoreManager: KeystoreManager
) {
    private val resourceGenerator = ResourceGenerator(context)
    private val javaGenerator = JavaTemplateGenerator(context)

    fun build(project: WebProjectEntity, buildType: BuildType): Flow<BuildProgressEvent> = channelFlow {
        val started = System.currentTimeMillis()
        val logger = BuildLogger { level, message -> trySend(BuildProgressEvent.Log(LogLine(level, message))) }
        var currentStage: BuildStage? = null

        suspend fun <T> stage(stage: BuildStage, block: suspend () -> T): T {
            ensureActive()
            currentStage = stage
            val t0 = System.currentTimeMillis()
            send(BuildProgressEvent.StageStarted(stage, stage.startPercent))
            logger.info("━━ Tahap ${stage.ordinal + 1}/${BuildStage.entries.size}: ${stage.label} ━━")
            val r = block()
            val dt = System.currentTimeMillis() - t0
            send(BuildProgressEvent.StageCompleted(stage, stage.endPercent, dt))
            logger.success("${stage.label} selesai (${dt} ms)")
            return r
        }

        try {
            logger.info("Webify Build • ${project.appName} (${project.packageName}) • ${buildType.label}")
            logger.info("Perangkat: ${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • ABI ${Build.SUPPORTED_ABIS.firstOrNull()}")
            preflight(project, logger)

            val ws = Workspace(File(context.filesDir, "webify_workspaces/${project.id}"))

            stage(BuildStage.RESOURCES) { resourceGenerator.generate(project, ws, logger) }
            stage(BuildStage.MANIFEST) { ManifestGenerator.generate(project, ws, logger) }

            val aapt2 = Aapt2Wrapper(toolchain.aapt2, toolchain.androidJar)
            val deps = stage(BuildStage.DEPENDENCIES) { prepareDependencies(project, ws, aapt2, logger) }

            stage(BuildStage.AAPT2) {
                aapt2.compile(ws, logger)
                send(BuildProgressEvent.Progress(BuildStage.AAPT2.startPercent + 8))
                aapt2.link(project, ws, protoFormat = buildType.isBundle, log = logger, deps = deps)
            }

            stage(BuildStage.JAVA_GEN) { javaGenerator.generate(project, ws, logger) }
            stage(BuildStage.ECJ) { EcjCompilerWrapper(toolchain.androidJar).compile(ws, logger, deps.jars) }
            val dexFiles = stage(BuildStage.D8) {
                D8CompilerWrapper(toolchain.androidJar).dex(ws, project.minSdk, buildType.isRelease, logger, deps.jars)
            }

            val output = stage(BuildStage.PACKAGE) {
                val keystoreId = if (buildType == BuildType.APK_DEBUG) null else project.selectedKeystoreId
                if (buildType != BuildType.APK_DEBUG && keystoreId == null)
                    logger.warn("Keystore rilis belum dipilih — memakai debug key")
                val key = try {
                    keystoreManager.loadSigningKey(keystoreId)
                } catch (e: Exception) {
                    throw BuildStageException(BuildStage.PACKAGE, "Gagal memuat keystore: ${e.message}", e)
                }
                logger.info("Signer: ${key.keystoreName} • ${key.certificate.subjectX500Principal.name}")
                val outFile = outputFileFor(project, buildType)
                if (buildType.isBundle) AabPackager.packageAab(ws, dexFiles, key, outFile, logger)
                else ApkPackager.packageApk(ws, dexFiles, key, project.minSdk, outFile, logger)
            }

            val duration = System.currentTimeMillis() - started
            logger.success("BUILD SUCCESSFUL dalam ${"%.1f".format(Locale.US, duration / 1000.0)} s • ${output.length() / 1024} KB")
            send(BuildProgressEvent.Progress(100))
            send(BuildProgressEvent.Finished(BuildResult.Success(output, buildType, output.length(), duration)))
        } catch (e: CancellationException) {
            logger.warn("Build dibatalkan oleh pengguna")
            trySend(BuildProgressEvent.Finished(BuildResult.Failure(currentStage, "Dibatalkan", System.currentTimeMillis() - started)))
            throw e
        } catch (e: BuildStageException) {
            logger.error("BUILD FAILED pada tahap ${e.stage.label}: ${e.message}")
            e.cause?.let { logger.debug(it.stackTraceToString().lines().take(12).joinToString("\n")) }
            send(BuildProgressEvent.Finished(BuildResult.Failure(e.stage, e.message ?: "Error", System.currentTimeMillis() - started, e.compileErrors)))
        } catch (e: OutOfMemoryError) {
            logger.error("BUILD FAILED: memori tidak cukup (${e.message})")
            send(BuildProgressEvent.Finished(BuildResult.Failure(currentStage, "OutOfMemoryError: tutup aplikasi lain lalu coba lagi", System.currentTimeMillis() - started)))
        } catch (e: Throwable) {
            logger.error("BUILD FAILED: ${e.javaClass.simpleName}: ${e.message}")
            logger.debug(e.stackTraceToString().lines().take(15).joinToString("\n"))
            send(BuildProgressEvent.Finished(BuildResult.Failure(currentStage, "${e.javaClass.simpleName}: ${e.message}", System.currentTimeMillis() - started)))
        }
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    /**
     * Tahap SDK & AAR: resolusi Maven + library lokal, ekstraksi AAR, kompilasi resource library,
     * manifest merger, serta penyalinan assets / native lib / resource Java ke workspace.
     */
    private suspend fun prepareDependencies(p: WebProjectEntity, ws: Workspace, aapt2: Aapt2Wrapper, log: BuildLogger): DependencySet {
        val coords = buildList {
            if (p.fcmEnabled) add(PluginCatalog.FCM)
            if (p.admobEnabled) add(PluginCatalog.ADMOB)
            if (p.qrScannerEnabled) add(PluginCatalog.ZXING)
            p.extraDependencies.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { add(it) }
        }.mapNotNull { raw ->
            MavenCoordinate.parse(raw) ?: run { log.warn("Koordinat Maven tidak valid dilewati: $raw"); null }
        }.distinctBy { it.key }
        val locals = p.localLibraries.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { File(it) }
        if (coords.isEmpty() && locals.isEmpty()) {
            log.info("Tidak ada SDK/AAR eksternal — jalur cepat (android.jar saja)")
            return DependencySet.EMPTY
        }
        coords.forEach { log.info("• $it") }
        locals.forEach { log.info("• lokal: ${it.name}") }

        val manager = AarDependencyManager(File(context.filesDir, "webify_sdk_repo"), aapt2)
        val deps = manager.prepare(coords, locals, p.minSdk, log)

        // Manifest merger
        try {
            AarManifestMerger.mergeFile(ws.manifest, deps.libraries, p.packageName, log)
        } catch (e: Exception) {
            throw BuildStageException(BuildStage.DEPENDENCIES, "Manifest merger gagal: ${e.message}", e)
        }

        // Assets library → assets/ (aset proyek tidak ditimpa)
        deps.libraries.mapNotNull { it.assetsDir }.forEach { dir ->
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                val target = File(ws.assets, f.relativeTo(dir).path)
                if (!target.exists()) { target.parentFile?.mkdirs(); f.copyTo(target) }
            }
        }
        // Native lib (jni/<abi>/*.so) → native_libs/<abi>/
        var soCount = 0
        deps.libraries.mapNotNull { it.jniDir }.forEach { dir ->
            dir.walkTopDown().filter { it.isFile && it.extension == "so" }.forEach { f ->
                val target = File(ws.nativeLibs, f.relativeTo(dir).path)
                if (!target.exists()) { target.parentFile?.mkdirs(); f.copyTo(target); soCount++ }
            }
        }
        // Resource Java (META-INF/services digabung, file lain non-class disalin)
        val services = HashMap<String, StringBuilder>()
        var resCount = 0
        for (jar in deps.jars) {
            java.util.zip.ZipFile(jar).use { zip ->
                for (e in zip.entries().toList()) {
                    if (e.isDirectory) continue
                    val n = e.name
                    if (n.endsWith(".class") || n.endsWith(".kotlin_module") || n.endsWith("module-info.class")) continue
                    val upper = n.uppercase()
                    if (upper.startsWith("META-INF/") && (upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA") ||
                            upper.endsWith(".EC") || upper == "META-INF/MANIFEST.MF" || upper.startsWith("META-INF/VERSIONS/") ||
                            upper.endsWith(".VERSION") || upper.startsWith("META-INF/MAVEN/") || upper.startsWith("META-INF/PROGUARD/") ||
                            upper.startsWith("META-INF/COM.ANDROID.TOOLS/"))) continue
                    if (n.startsWith("META-INF/services/")) {
                        val text = zip.getInputStream(e).use { it.readBytes().toString(Charsets.UTF_8) }
                        services.getOrPut(n) { StringBuilder() }.append(text.trimEnd()).append('\n')
                    } else if (!upper.startsWith("META-INF/") && !n.contains("..")) {
                        val target = File(ws.javaResources, n)
                        if (!target.exists()) {
                            target.parentFile?.mkdirs()
                            zip.getInputStream(e).use { ins -> target.outputStream().use { ins.copyTo(it) } }
                            resCount++
                        }
                    }
                }
            }
        }
        services.forEach { (name, content) ->
            val f = File(ws.javaResources, name)
            f.parentFile?.mkdirs()
            f.writeText(content.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.distinct().joinToString("\n") + "\n")
        }
        if (soCount + resCount + services.size > 0) {
            log.info("Disalin: $soCount native lib, $resCount resource Java, ${services.size} service loader")
        }
        return deps
    }

    private suspend fun ProducerScope<BuildProgressEvent>.preflight(p: WebProjectEntity, log: BuildLogger) {
        val status = toolchain.prepare()
        if (!status.ready) throw BuildStageException(BuildStage.RESOURCES, "Toolchain belum siap: ${status.message}")
        Validators.packageNameError(p.packageName)?.let { throw BuildStageException(BuildStage.MANIFEST, "Package name: $it") }
        if (p.sourceType == SourceType.URL) Validators.urlError(p.targetUrl)?.let { throw BuildStageException(BuildStage.RESOURCES, it) }
        if (p.versionCode < 1) throw BuildStageException(BuildStage.MANIFEST, "versionCode harus ≥ 1")
        if (p.versionName.isBlank()) throw BuildStageException(BuildStage.MANIFEST, "versionName wajib diisi")
        if (p.minSdk !in 21..p.targetSdk) throw BuildStageException(BuildStage.MANIFEST, "minSdk harus 21..targetSdk")
        if (p.admobEnabled && !p.admobAppId.trim().matches(Regex("^ca-app-pub-\\d+~\\d+$")))
            throw BuildStageException(BuildStage.MANIFEST, "AdMob App ID tidak valid (format ca-app-pub-XXXXXXXX~YYYYYYYY)")
        if (p.fcmEnabled && p.googleServicesJsonPath == null)
            throw BuildStageException(BuildStage.RESOURCES, "FCM aktif tetapi google-services.json belum diunggah")
        val free = context.filesDir.usableSpace / (1024 * 1024)
        if (free < 50) throw BuildStageException(BuildStage.RESOURCES, "Ruang penyimpanan tidak cukup ($free MB tersisa)")
        log.info("Preflight OK • android.jar ${toolchain.androidJar.length() / 1024} KB • aapt2 ${toolchain.aapt2.length() / 1024} KB")
        send(BuildProgressEvent.Progress(1))
    }

    private fun outputFileFor(p: WebProjectEntity, type: BuildType): File {
        val safe = p.appName.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { "app" }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val suffix = when (type) {
            BuildType.APK_DEBUG -> "debug"
            BuildType.APK_RELEASE -> "release"
            BuildType.AAB_PLAYSTORE -> "playstore"
        }
        return File(context.filesDir, "webify_outputs/${p.id}/$safe-v${p.versionName}-$suffix-$stamp.${type.extension}")
    }
}
