package com.webify.app.build

import com.android.tools.build.bundletool.commands.BuildBundleCommand
import com.google.common.collect.ImmutableList
import com.webify.app.crypto.PackageSigner
import com.webify.app.crypto.SigningKey
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Tahap 7 (AAB): proto_resources.ap_ → bundle_staging/base → base.zip → bundletool build-bundle
 * → tanda tangan JAR v1 (SHA256withRSA).
 */
object AabPackager {

    fun packageAab(ws: Workspace, dexFiles: List<File>, key: SigningKey, outputFile: File, log: BuildLogger): File {
        val base = File(ws.bundleStaging, "base")
        base.deleteRecursively()
        base.mkdirs()

        // 1. Ekstrak proto_resources.ap_ ke struktur modul base
        var resCount = 0
        ZipFile(ws.protoResourcesAp).use { ap ->
            for (e in ap.entries().toList()) {
                if (e.isDirectory) continue
                val target = when {
                    e.name == "AndroidManifest.xml" -> File(base, "manifest/AndroidManifest.xml")
                    e.name == "resources.pb" -> File(base, "resources.pb")
                    e.name.startsWith("res/") -> File(base, e.name)
                    else -> File(base, "root/" + e.name)
                }
                safeTarget(base, target)
                target.parentFile?.mkdirs()
                ap.getInputStream(e).use { ins -> target.outputStream().use { ins.copyTo(it) } }
                if (e.name.startsWith("res/")) resCount++
            }
        }
        if (!File(base, "manifest/AndroidManifest.xml").exists() || !File(base, "resources.pb").exists())
            throw BuildStageException(BuildStage.PACKAGE, "proto_resources.ap_ tidak valid (manifest/resources.pb hilang). Pastikan aapt2 link memakai --proto-format")
        log.info("bundle_staging/base: manifest proto, resources.pb, $resCount file res")

        // 2. DEX & assets
        val dexDir = File(base, "dex").apply { mkdirs() }
        dexFiles.forEach { it.copyTo(File(dexDir, it.name), overwrite = true) }
        if (ws.assets.exists() && (ws.assets.list()?.isNotEmpty() == true)) {
            ws.assets.copyRecursively(File(base, "assets"), overwrite = true)
        }

        // 3. base.zip — hanya entri file (tanpa entri direktori) agar lolos validasi bundletool
        val baseZip = File(ws.bundleStaging, "base.zip")
        if (baseZip.exists()) baseZip.delete()
        var n = 0
        ZipOutputStream(baseZip.outputStream().buffered()).use { zos ->
            base.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach { f ->
                val entry = ZipEntry(f.relativeTo(base).invariantSeparatorsPath).apply { time = AlignedZipWriter.FIXED_TIME }
                zos.putNextEntry(entry)
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
                n++
            }
        }
        log.info("base.zip dibuat ($n entri, ${baseZip.length() / 1024} KB)")

        // 4. bundletool build-bundle (API langsung, tanpa System.exit seperti BundleToolMain)
        val unsignedAab = File(ws.output, "app-release-unsigned.aab")
        if (unsignedAab.exists()) unsignedAab.delete()
        log.info("bundletool build-bundle --modules=base.zip --output=${unsignedAab.name}")
        try {
            BuildBundleCommand.builder()
                .setModulesPaths(ImmutableList.of(baseZip.toPath()))
                .setOutputPath(unsignedAab.toPath())
                .setOverwriteOutput(true)
                .build()
                .execute()
        } catch (e: Throwable) {
            throw BuildStageException(BuildStage.PACKAGE, "bundletool gagal: ${e.javaClass.simpleName}: ${e.message}", e)
        }
        if (!unsignedAab.exists()) throw BuildStageException(BuildStage.PACKAGE, "bundletool tidak menghasilkan .aab")
        log.info("bundletool OK → ${unsignedAab.length() / 1024} KB")

        // 5. Tanda tangan JAR v1
        outputFile.parentFile?.mkdirs()
        try {
            PackageSigner.signBundle(unsignedAab, outputFile, key) { log.info(it) }
        } catch (e: Exception) {
            throw BuildStageException(BuildStage.PACKAGE, "Penandatanganan AAB gagal: ${e.message}", e)
        }
        unsignedAab.delete()
        if (key.isDebug) log.warn("AAB ditandatangani dengan DEBUG key — Play Console akan menolaknya. Pilih keystore rilis.")
        log.success("AAB ditandatangani → ${outputFile.name}")
        return outputFile
    }

    private fun safeTarget(root: File, target: File) {
        if (!target.canonicalPath.startsWith(root.canonicalPath + File.separator))
            throw BuildStageException(BuildStage.PACKAGE, "Path entri tidak aman: ${target.path}")
    }
}
