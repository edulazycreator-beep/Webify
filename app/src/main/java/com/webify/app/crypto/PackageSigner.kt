package com.webify.app.crypto

import com.android.apksig.ApkSigner
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Penandatanganan paket:
 *  - APK  → apksig (skema v1 + v2 + v3).
 *  - AAB  → tanda tangan JAR v1 (MANIFEST.MF / .SF / .RSA, SHA-256withRSA) setara `jarsigner`,
 *           format yang diterima Google Play Console untuk upload key.
 */
object PackageSigner {

    private val bc = BouncyCastleProvider()

    fun signApk(input: File, output: File, key: SigningKey, minSdk: Int, log: (String) -> Unit = {}) {
        if (output.exists()) output.delete()
        val signerConfig = ApkSigner.SignerConfig.Builder("CERT", key.privateKey, key.certificates).build()
        log("apksig: v1=true v2=true v3=true minSdk=$minSdk signer=${key.keystoreName}")
        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(input)
            .setOutputApk(output)
            .setMinSdkVersion(minSdk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setOtherSignersSignaturesPreserved(false)
            .setCreatedBy("Webify")
            .build()
            .sign()
    }

    /** Tanda tangan JAR v1 untuk Android App Bundle (.aab). */
    fun signBundle(input: File, output: File, key: SigningKey, log: (String) -> Unit = {}) {
        val signerName = "WEBIFY"
        val digestAlg = "SHA-256"
        val b64 = Base64.getEncoder()

        // 1. Hitung digest setiap entri non-META-INF signature.
        data class Item(val name: String, val digest: String)
        val items = ArrayList<Item>()
        ZipFile(input).use { zip ->
            val entries = zip.entries().toList().filter { !it.isDirectory && !isSignatureFile(it.name) }
            for (e in entries) {
                val md = MessageDigest.getInstance(digestAlg)
                zip.getInputStream(e).use { ins ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val r = ins.read(buf)
                        if (r < 0) break
                        md.update(buf, 0, r)
                    }
                }
                items += Item(e.name, b64.encodeToString(md.digest()))
            }
        }
        log("JarSigner: ${items.size} entri di-digest (SHA-256)")

        // 2. MANIFEST.MF
        val manifestMain = "Manifest-Version: 1.0\r\nCreated-By: Webify (on-device)\r\n\r\n".toByteArray()
        val sections = items.map { it.name to section(listOf("Name: ${it.name}", "SHA-256-Digest: ${it.digest}")) }
        val manifest = ByteArrayOutputStream().apply {
            write(manifestMain)
            sections.forEach { write(it.second) }
        }.toByteArray()

        // 3. Signature file (.SF)
        val sf = ByteArrayOutputStream().apply {
            write(
                section(
                    listOf(
                        "Signature-Version: 1.0",
                        "Created-By: Webify (on-device)",
                        "SHA-256-Digest-Manifest: " + b64.encodeToString(sha256(manifest)),
                        "SHA-256-Digest-Manifest-Main-Attributes: " + b64.encodeToString(sha256(manifestMain))
                    )
                )
            )
            sections.forEach { (name, bytes) ->
                write(section(listOf("Name: $name", "SHA-256-Digest: " + b64.encodeToString(sha256(bytes)))))
            }
        }.toByteArray()

        // 4. Blok tanda tangan PKCS#7 detached (.RSA / .EC)
        val sigAlg = if (key.privateKey.algorithm == "EC") "SHA256withECDSA" else "SHA256withRSA"
        val ext = if (key.privateKey.algorithm == "EC") "EC" else "RSA"
        val contentSigner = JcaContentSignerBuilder(sigAlg).setProvider(bc).build(key.privateKey)
        val gen = CMSSignedDataGenerator()
        gen.addSignerInfoGenerator(
            JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().setProvider(bc).build())
                .setDirectSignature(true)
                .build(contentSigner, key.certificate)
        )
        gen.addCertificates(JcaCertStore(key.certificates))
        val pkcs7 = gen.generate(CMSProcessableByteArray(sf), false).toASN1Structure().getEncoded("DER")

        // 5. Tulis arsip: META-INF dulu, kemudian entri asli (menjaga metode kompresi asli).
        if (output.exists()) output.delete()
        ZipFile(input).use { zip ->
            ZipOutputStream(output.outputStream().buffered()).use { zos ->
                putDeflated(zos, "META-INF/MANIFEST.MF", manifest)
                putDeflated(zos, "META-INF/$signerName.SF", sf)
                putDeflated(zos, "META-INF/$signerName.$ext", pkcs7)
                for (e in zip.entries().toList()) {
                    if (isSignatureFile(e.name) || e.name == "META-INF/MANIFEST.MF") continue
                    copyEntry(zip, e, zos)
                }
            }
        }
        log("JarSigner: tanda tangan $sigAlg ditambahkan → ${output.name}")
    }

    private fun isSignatureFile(name: String): Boolean {
        val upper = name.uppercase()
        if (!upper.startsWith("META-INF/")) return false
        if (upper == "META-INF/MANIFEST.MF") return true
        val leaf = upper.substringAfterLast('/')
        return upper.count { it == '/' } == 1 &&
            (leaf.endsWith(".SF") || leaf.endsWith(".RSA") || leaf.endsWith(".DSA") || leaf.endsWith(".EC") || leaf.startsWith("SIG-"))
    }

    /** Membuat section manifest dengan line-wrapping 72 byte sesuai spesifikasi JAR. */
    private fun section(lines: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        for (line in lines) {
            val bytes = line.toByteArray(Charsets.UTF_8)
            var offset = 0
            var first = true
            while (offset < bytes.size) {
                val max = if (first) 70 else 69 // 72 termasuk CRLF; baris lanjutan diawali spasi
                var end = minOf(bytes.size, offset + max)
                // jangan memotong di tengah karakter UTF-8 multibyte
                while (end < bytes.size && end > offset && (bytes[end].toInt() and 0xC0) == 0x80) end--
                if (!first) out.write(' '.code)
                out.write(bytes, offset, end - offset)
                out.write("\r\n".toByteArray())
                offset = end
                first = false
            }
            if (bytes.isEmpty()) out.write("\r\n".toByteArray())
        }
        out.write("\r\n".toByteArray())
        return out.toByteArray()
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

    private fun putDeflated(zos: ZipOutputStream, name: String, data: ByteArray) {
        val e = ZipEntry(name).apply { method = ZipEntry.DEFLATED; time = FIXED_TIME }
        zos.putNextEntry(e)
        zos.write(data)
        zos.closeEntry()
    }

    private fun copyEntry(zip: ZipFile, e: ZipEntry, zos: ZipOutputStream) {
        val data = zip.getInputStream(e).use { it.readBytes() }
        val ne = ZipEntry(e.name).apply { time = FIXED_TIME }
        if (e.isDirectory) {
            ne.method = ZipEntry.STORED; ne.size = 0; ne.compressedSize = 0; ne.crc = 0
        } else if (e.method == ZipEntry.STORED) {
            val crc = CRC32().apply { update(data) }
            ne.method = ZipEntry.STORED; ne.size = data.size.toLong(); ne.compressedSize = data.size.toLong(); ne.crc = crc.value
        } else {
            ne.method = ZipEntry.DEFLATED
        }
        zos.putNextEntry(ne)
        if (!e.isDirectory) zos.write(data)
        zos.closeEntry()
    }

    private const val FIXED_TIME = 347155200000L // 1981-01-01 (aman lintas zona waktu DOS), build reproducible
}
