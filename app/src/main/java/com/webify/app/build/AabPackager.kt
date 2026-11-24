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
        if (ws.nativeLibs.exists() && (ws.nativeLibs.list()?.isNotEmpty() == true)) {
            ws.nativeLibs.copyRecursively(File(base, "lib"), overwrite = true)
        }
        if (ws.javaResources.exists() && (ws.javaResources.list()?.isNotEmpty() == true)) {
            ws.javaResources.copyRecursively(File(base, "root"), overwrite = false) { _, _ -> OnErrorAction.SKIP }
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

        // 4. bundletool build-bundle (API langsung). Bila kelas bundletool gagal di-link di ART,
        //    fallback ke perakit AAB bawaan Webify yang menghasilkan struktur identik.
        val unsignedAab = File(ws.output, "app-release-unsigned.aab")
        if (unsignedAab.exists()) unsignedAab.delete()
        log.info("bundletool build-bundle --modules=base.zip --output=${unsignedAab.name}")
        var usedFallback = false
        try {
            BuildBundleCommand.builder()
                .setModulesPaths(ImmutableList.of(baseZip.toPath()))
                .setOutputPath(unsignedAab.toPath())
                .setOverwriteOutput(true)
                .build()
                .execute()
        } catch (e: LinkageError) {
            // NoClassDefFoundError / ExceptionInInitializerError / VerifyError dari runtime Android
            log.warn("bundletool tidak dapat dijalankan di runtime ini: ${e.javaClass.simpleName}: ${e.message}")
            causeChain(e).drop(1).forEach { log.warn("  ↳ disebabkan oleh: $it") }
            log.info("Beralih ke perakit AAB bawaan Webify (struktur identik dengan bundletool build-bundle)")
            usedFallback = true
        } catch (e: Throwable) {
            val msg = causeChain(e).joinToString(" ← ")
            throw BuildStageException(BuildStage.PACKAGE, "bundletool gagal: $msg", e)
        }
        if (usedFallback) {
            if (unsignedAab.exists()) unsignedAab.delete()
            assembleBundle(base, unsignedAab, log)
        }
        if (!unsignedAab.exists()) throw BuildStageException(BuildStage.PACKAGE, "AAB tidak dihasilkan")
        verifyBundle(unsignedAab, log)
        log.info("AAB tersusun → ${unsignedAab.length() / 1024} KB${if (usedFallback) " (perakit bawaan)" else " (bundletool)"}")

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

    /**
     * Perakit AAB bawaan: base/(semua file) + BundleConfig.pb di root, tanpa entri direktori.
     * Format identik dengan keluaran `bundletool build-bundle` untuk modul base tunggal.
     */
    private fun assembleBundle(base: File, out: File, log: BuildLogger) {
        var count = 0
        ZipOutputStream(out.outputStream().buffered()).use { zos ->
            // BundleConfig.pb: BundleConfig { bundletool { version: "<versi>" } } — protobuf di-encode manual
            putEntry(zos, "BundleConfig.pb", bundleConfigBytes(BUNDLETOOL_VERSION))
            base.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(base).invariantSeparatorsPath }.forEach { f ->
                putEntry(zos, "base/" + f.relativeTo(base).invariantSeparatorsPath, f.readBytes())
                count++
            }
        }
        log.info("Perakit AAB: BundleConfig.pb + $count entri modul base")
    }

    /** Validasi struktur minimum yang diperiksa Play Console & bundletool. */
    private fun verifyBundle(aab: File, log: BuildLogger) {
        ZipFile(aab).use { zip ->
            val names = zip.entries().toList().filter { !it.isDirectory }.map { it.name }.toSet()
            val missing = listOf("BundleConfig.pb", "base/manifest/AndroidManifest.xml", "base/resources.pb", "base/dex/classes.dex")
                .filter { it !in names }
            if (missing.isNotEmpty()) throw BuildStageException(BuildStage.PACKAGE, "Struktur AAB tidak lengkap, hilang: ${missing.joinToString()}")
            val bad = names.filter { n ->
                !(n == "BundleConfig.pb" || n.startsWith("base/") || n.startsWith("BUNDLE-METADATA/") || n.startsWith("META-INF/"))
            }
            if (bad.isNotEmpty()) throw BuildStageException(BuildStage.PACKAGE, "Entri tidak dikenal di root AAB: ${bad.take(5)}")
            val unknownDirs = names.filter { it.startsWith("base/") }
                .map { it.removePrefix("base/").substringBefore('/') }
                .filter { it !in setOf("manifest", "dex", "res", "assets", "lib", "root", "resources.pb", "native.pb", "assets.pb") }
                .distinct()
            if (unknownDirs.isNotEmpty()) throw BuildStageException(BuildStage.PACKAGE, "Folder modul tidak valid: $unknownDirs")
            log.info("Validasi struktur AAB: OK (${names.size} entri)")
        }
    }

    private fun putEntry(zos: ZipOutputStream, name: String, data: ByteArray) {
        val e = ZipEntry(name).apply { time = AlignedZipWriter.FIXED_TIME }
        zos.putNextEntry(e)
        zos.write(data)
        zos.closeEntry()
    }

    /** Encode protobuf: field 1 (Bundletool, LEN) → field 2 (version, string). */
    internal fun bundleConfigBytes(version: String): ByteArray {
        val v = version.toByteArray(Charsets.UTF_8)
        val inner = byteArrayOf(0x12) + varint(v.size) + v
        return byteArrayOf(0x0A) + varint(inner.size) + inner
    }

    private fun varint(value: Int): ByteArray {
        val out = ArrayList<Byte>()
        var v = value
        while (v and 0x7F.inv() != 0) {
            out += ((v and 0x7F) or 0x80).toByte()
            v = v ushr 7
        }
        out += v.toByte()
        return out.toByteArray()
    }

    private fun causeChain(t: Throwable): List<String> {
        val out = mutableListOf<String>()
        var c: Throwable? = t
        val seen = HashSet<Throwable>()
        while (c != null && seen.add(c) && out.size < 8) {
            out += "${c.javaClass.simpleName}: ${c.message}"
            c = c.cause
        }
        return out
    }

    private const val BUNDLETOOL_VERSION = "1.16.0"

    private fun safeTarget(root: File, target: File) {
        if (!target.canonicalPath.startsWith(root.canonicalPath + File.separator))
            throw BuildStageException(BuildStage.PACKAGE, "Path entri tidak aman: ${target.path}")
    }
}
