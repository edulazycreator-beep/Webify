package com.webify.app.build

import com.webify.app.crypto.PackageSigner
import com.webify.app.crypto.SigningKey
import java.io.File
import java.util.zip.ZipFile

/**
 * Tahap 7 (APK): resources.ap_ + classes*.dex + assets/ → zipalign → apksig (v1+v2+v3).
 */
object ApkPackager {

    fun packageApk(
        ws: Workspace,
        dexFiles: List<File>,
        key: SigningKey,
        minSdk: Int,
        outputFile: File,
        log: BuildLogger
    ): File {
        val unsigned = File(ws.output, "app-unsigned-aligned.apk")
        if (unsigned.exists()) unsigned.delete()

        var entryCount = 0
        AlignedZipWriter(unsigned).use { zw ->
            // 1. Salin isi resources.ap_ (AndroidManifest.xml biner, resources.arsc, res/**)
            ZipFile(ws.resourcesAp).use { ap ->
                val entries = ap.entries().toList().filter { !it.isDirectory }
                    .sortedBy { order(it.name) }
                for (e in entries) {
                    val data = ap.getInputStream(e).use { it.readBytes() }
                    zw.put(e.name, data, compress = !AlignedZipWriter.shouldStore(e.name))
                    entryCount++
                }
            }
            // 2. classes.dex di root arsip
            for (dex in dexFiles) {
                zw.put(dex.name, dex.readBytes(), compress = true)
                entryCount++
            }
            // 3. assets/**
            if (ws.assets.exists()) {
                ws.assets.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach { f ->
                    val name = "assets/" + f.relativeTo(ws.assets).invariantSeparatorsPath
                    if (!zw.has(name)) {
                        zw.put(name, f.readBytes(), compress = !AlignedZipWriter.shouldStore(name))
                        entryCount++
                    }
                }
            }
        }
        log.info("Paket APK: $entryCount entri, zipalign 4-byte diterapkan")

        val misaligned = ZipAlignVerifier.misaligned(unsigned)
        if (misaligned.isNotEmpty()) {
            log.warn("Entri tidak sejajar: ${misaligned.take(5)}")
            throw BuildStageException(BuildStage.PACKAGE, "Verifikasi zipalign gagal (${misaligned.size} entri)")
        }
        log.info("Verifikasi zipalign -c 4: OK")

        outputFile.parentFile?.mkdirs()
        try {
            PackageSigner.signApk(unsigned, outputFile, key, minSdk) { log.info(it) }
        } catch (e: Exception) {
            throw BuildStageException(BuildStage.PACKAGE, "Penandatanganan APK gagal: ${e.message}", e)
        }
        unsigned.delete()
        log.success("APK ditandatangani (${if (key.isDebug) "debug key" else key.keystoreName}) → ${outputFile.name}")
        return outputFile
    }

    /** Urutan entri: manifest, resources.arsc, lalu sisanya. */
    private fun order(name: String) = when (name) {
        "AndroidManifest.xml" -> "0"
        "resources.arsc" -> "1"
        else -> "2$name"
    }
}
