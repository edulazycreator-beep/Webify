package com.webify.app.crypto

import android.content.Context
import android.net.Uri
import com.webify.app.data.db.KeystoreDao
import com.webify.app.data.db.KeystoreEntity
import com.webify.app.data.db.WebProjectDao
import com.webify.app.data.prefs.SecurePrefs
import com.webify.app.domain.KeyAlgorithm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500NameBuilder
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Kunci penandatanganan siap pakai (private key + rantai sertifikat). */
data class SigningKey(
    val privateKey: PrivateKey,
    val certificates: List<X509Certificate>,
    val keystoreName: String,
    val isDebug: Boolean
) {
    val certificate: X509Certificate get() = certificates.first()
}

data class DistinguishedName(
    val commonName: String,
    val organizationalUnit: String = "",
    val organization: String = "",
    val locality: String = "",
    val state: String = "",
    val countryCode: String = ""
)

data class ReleaseKeystoreRequest(
    val name: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
    val algorithm: KeyAlgorithm,
    val validityYears: Int,
    val dn: DistinguishedName
)

class KeystoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Modul kriptografi Webify: membuat keystore debug/rilis (PKCS#12) memakai BouncyCastle,
 * impor/ekspor, perhitungan fingerprint, dan memuat SigningKey untuk pipeline build.
 *
 * Catatan format: Android tidak menyediakan provider JKS. Webify menulis keystore PKCS#12
 * (format default keytool sejak JDK 9, diterima Google Play / Play App Signing).
 * Impor mendukung PKCS#12 (.p12/.pfx/.jks modern) dan BKS.
 */
class KeystoreManager(
    private val context: Context,
    private val keystoreDao: KeystoreDao,
    private val projectDao: WebProjectDao,
    private val securePrefs: SecurePrefs
) {
    private val bc = BouncyCastleProvider()
    private val mutex = Mutex()
    private val keystoreDir: File get() = File(context.filesDir, "keystores").apply { mkdirs() }

    // ------------------------------------------------------------------ Debug keystore
    suspend fun ensureDebugKeystore(): KeystoreEntity = withContext(Dispatchers.IO) {
        mutex.withLock {
            val existing = keystoreDao.getDebugDefault()
            if (existing != null && File(existing.filePath).exists() &&
                securePrefs.storePassword(existing.id) != null
            ) return@withLock existing

            existing?.let { keystoreDao.delete(it) }
            val file = File(keystoreDir, DEBUG_FILE)
            val entity = createKeystoreFile(
                file = file,
                storePassword = DEBUG_PASSWORD,
                keyAlias = DEBUG_ALIAS,
                keyPassword = DEBUG_PASSWORD,
                algorithm = KeyAlgorithm.RSA_2048,
                validityYears = 30,
                dn = DistinguishedName(commonName = "Webify Debug", organization = "Webify", countryCode = "ID"),
                name = "Webify Debug Key",
                isDebug = true
            )
            keystoreDao.insert(entity)
            securePrefs.saveKeystorePasswords(entity.id, DEBUG_PASSWORD, DEBUG_PASSWORD)
            entity
        }
    }

    // ------------------------------------------------------------------ Release keystore
    suspend fun generateReleaseKeystore(req: ReleaseKeystoreRequest): KeystoreEntity = withContext(Dispatchers.Default) {
        validateRequest(req)
        val safeName = req.name.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
        val file = File(keystoreDir, "${safeName}_${System.currentTimeMillis()}.p12")
        val entity = createKeystoreFile(
            file = file,
            storePassword = req.storePassword,
            keyAlias = req.keyAlias.trim(),
            keyPassword = req.keyPassword,
            algorithm = req.algorithm,
            validityYears = req.validityYears,
            dn = req.dn,
            name = req.name.trim(),
            isDebug = false
        )
        withContext(Dispatchers.IO) {
            keystoreDao.insert(entity)
            securePrefs.saveKeystorePasswords(entity.id, req.storePassword, req.keyPassword)
        }
        entity
    }

    private fun validateRequest(req: ReleaseKeystoreRequest) {
        if (req.name.isBlank()) throw KeystoreException("Nama keystore wajib diisi")
        if (req.storePassword.length < 6) throw KeystoreException("Password keystore minimal 6 karakter")
        if (req.keyPassword.length < 6) throw KeystoreException("Password key minimal 6 karakter")
        if (!req.keyAlias.matches(Regex("^[A-Za-z0-9._-]{1,64}$")))
            throw KeystoreException("Alias hanya boleh huruf, angka, titik, garis bawah, strip")
        if (req.validityYears !in 25..50) throw KeystoreException("Masa berlaku harus 25–50 tahun")
        if (req.dn.commonName.isBlank()) throw KeystoreException("Common Name (CN) wajib diisi")
        if (req.dn.countryCode.isNotBlank() && !req.dn.countryCode.matches(Regex("^[A-Za-z]{2}$")))
            throw KeystoreException("Kode negara harus 2 huruf (mis. ID)")
    }

    private fun createKeystoreFile(
        file: File,
        storePassword: String,
        keyAlias: String,
        keyPassword: String,
        algorithm: KeyAlgorithm,
        validityYears: Int,
        dn: DistinguishedName,
        name: String,
        isDebug: Boolean
    ): KeystoreEntity {
        try {
            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(algorithm.bits, SecureRandom())
            val keyPair = kpg.generateKeyPair()

            val nb = X500NameBuilder(BCStyle.INSTANCE).addRDN(BCStyle.CN, dn.commonName.trim())
            if (dn.organizationalUnit.isNotBlank()) nb.addRDN(BCStyle.OU, dn.organizationalUnit.trim())
            if (dn.organization.isNotBlank()) nb.addRDN(BCStyle.O, dn.organization.trim())
            if (dn.locality.isNotBlank()) nb.addRDN(BCStyle.L, dn.locality.trim())
            if (dn.state.isNotBlank()) nb.addRDN(BCStyle.ST, dn.state.trim())
            if (dn.countryCode.isNotBlank()) nb.addRDN(BCStyle.C, dn.countryCode.trim().uppercase(Locale.ROOT))
            val subject = nb.build()

            val notBefore = Date(System.currentTimeMillis() - 24L * 3600 * 1000)
            val notAfter = Calendar.getInstance().apply { add(Calendar.YEAR, validityYears) }.time
            val serial = BigInteger(64, SecureRandom()).abs().add(BigInteger.ONE)

            val builder = JcaX509v3CertificateBuilder(subject, serial, notBefore, notAfter, subject, keyPair.public)
                .addExtension(Extension.basicConstraints, false, BasicConstraints(false))
            val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider(bc).build(keyPair.private)
            val cert = JcaX509CertificateConverter().setProvider(bc).getCertificate(builder.build(signer))

            val ks = KeyStore.getInstance("PKCS12", bc)
            ks.load(null, null)
            ks.setKeyEntry(keyAlias, keyPair.private, keyPassword.toCharArray(), arrayOf(cert))
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.outputStream().use { ks.store(it, storePassword.toCharArray()) }
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) throw KeystoreException("Gagal menyimpan file keystore")

            return KeystoreEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                filePath = file.absolutePath,
                isDebugDefault = isDebug,
                keyAlias = keyAlias,
                keyAlgorithm = algorithm,
                commonName = dn.commonName,
                organizationalUnit = dn.organizationalUnit,
                organization = dn.organization,
                locality = dn.locality,
                state = dn.state,
                countryCode = dn.countryCode.uppercase(Locale.ROOT),
                validityYears = validityYears,
                sha1Fingerprint = fingerprint(cert, "SHA-1"),
                sha256Fingerprint = fingerprint(cert, "SHA-256")
            )
        } catch (e: KeystoreException) {
            throw e
        } catch (e: Exception) {
            throw KeystoreException("Gagal membuat keystore: ${e.message}", e)
        }
    }

    // ------------------------------------------------------------------ Import / export
    /** Membaca alias yang ada di keystore (untuk form impor). */
    suspend fun listAliases(uri: Uri, storePassword: String): List<String> = withContext(Dispatchers.IO) {
        val bytes = readUri(uri)
        val ks = loadAnyKeystore(bytes, storePassword.toCharArray())
        ks.aliases().toList().filter { ks.isKeyEntry(it) }
    }

    suspend fun importKeystore(
        uri: Uri,
        name: String,
        storePassword: String,
        keyAlias: String,
        keyPassword: String
    ): KeystoreEntity = withContext(Dispatchers.IO) {
        if (name.isBlank()) throw KeystoreException("Nama keystore wajib diisi")
        val bytes = readUri(uri)
        val ks = loadAnyKeystore(bytes, storePassword.toCharArray())
        val alias = keyAlias.ifBlank { ks.aliases().toList().firstOrNull { ks.isKeyEntry(it) } }
            ?: throw KeystoreException("Tidak ditemukan private key di keystore")
        if (!ks.isKeyEntry(alias)) throw KeystoreException("Alias '$alias' tidak memiliki private key")
        val effectiveKeyPw = keyPassword.ifEmpty { storePassword }
        val key = recoverKey(ks, alias, effectiveKeyPw, storePassword)
            ?: throw KeystoreException("Password key salah untuk alias '$alias'")
        val cert = ks.getCertificate(alias) as? X509Certificate
            ?: throw KeystoreException("Sertifikat X.509 tidak ditemukan untuk alias '$alias'")
        if (key.algorithm != "RSA" && key.algorithm != "EC")
            throw KeystoreException("Algoritma kunci ${key.algorithm} tidak didukung")

        // Normalisasi: simpan ulang sebagai PKCS#12 internal agar selalu dapat dimuat di Android.
        val chain = ks.getCertificateChain(alias)?.map { it as X509Certificate } ?: listOf(cert)
        val out = KeyStore.getInstance("PKCS12", bc).apply { load(null, null) }
        out.setKeyEntry(alias, key, effectiveKeyPw.toCharArray(), chain.toTypedArray())
        val file = File(keystoreDir, "imported_${System.currentTimeMillis()}.p12")
        file.outputStream().use { out.store(it, storePassword.toCharArray()) }

        val principal = cert.subjectX500Principal.name
        fun rdn(tag: String) = Regex("(?:^|,)\\s*$tag=([^,]*)").find(principal)?.groupValues?.get(1)?.trim() ?: ""
        val years = ((cert.notAfter.time - cert.notBefore.time) / (365.25 * 24 * 3600 * 1000)).toInt()
        val bits = (cert.publicKey as? java.security.interfaces.RSAPublicKey)?.modulus?.bitLength() ?: 2048
        val entity = KeystoreEntity(
            name = name.trim(),
            filePath = file.absolutePath,
            keyAlias = alias,
            keyAlgorithm = if (bits >= 4096) KeyAlgorithm.RSA_4096 else KeyAlgorithm.RSA_2048,
            commonName = rdn("CN"),
            organizationalUnit = rdn("OU"),
            organization = rdn("O"),
            locality = rdn("L"),
            state = rdn("ST"),
            countryCode = rdn("C"),
            validityYears = years,
            sha1Fingerprint = fingerprint(cert, "SHA-1"),
            sha256Fingerprint = fingerprint(cert, "SHA-256")
        )
        keystoreDao.insert(entity)
        securePrefs.saveKeystorePasswords(entity.id, storePassword, effectiveKeyPw)
        entity
    }

    suspend fun exportKeystore(entity: KeystoreEntity, target: Uri) = withContext(Dispatchers.IO) {
        val src = File(entity.filePath)
        if (!src.exists()) throw KeystoreException("File keystore tidak ditemukan")
        context.contentResolver.openOutputStream(target, "w")?.use { out ->
            src.inputStream().use { it.copyTo(out) }
        } ?: throw KeystoreException("Tidak dapat menulis ke lokasi tujuan")
    }

    suspend fun delete(entity: KeystoreEntity) = withContext(Dispatchers.IO) {
        if (entity.isDebugDefault) throw KeystoreException("Debug keystore bawaan tidak dapat dihapus")
        projectDao.clearKeystore(entity.id)
        keystoreDao.delete(entity)
        securePrefs.removeKeystore(entity.id)
        runCatching { File(entity.filePath).delete() }
    }

    // ------------------------------------------------------------------ Loading
    /** Memuat SigningKey. keystoreId null → debug key bawaan. */
    suspend fun loadSigningKey(keystoreId: String?): SigningKey = withContext(Dispatchers.IO) {
        val entity = if (keystoreId == null) ensureDebugKeystore()
        else keystoreDao.get(keystoreId) ?: throw KeystoreException("Keystore tidak ditemukan (id=$keystoreId)")
        val storePw = securePrefs.storePassword(entity.id)
            ?: throw KeystoreException("Password keystore '${entity.name}' tidak tersimpan. Impor ulang keystore.")
        val keyPw = securePrefs.keyPassword(entity.id) ?: storePw
        val file = File(entity.filePath)
        if (!file.exists()) throw KeystoreException("File keystore '${entity.name}' hilang dari penyimpanan")
        val ks = loadAnyKeystore(file.readBytes(), storePw.toCharArray())
        val key = recoverKey(ks, entity.keyAlias, keyPw, storePw)
            ?: throw KeystoreException("Gagal membuka private key '${entity.keyAlias}'")
        val chain = ks.getCertificateChain(entity.keyAlias)?.map { it as X509Certificate }
            ?: listOfNotNull(ks.getCertificate(entity.keyAlias) as? X509Certificate)
        if (chain.isEmpty()) throw KeystoreException("Sertifikat tidak ditemukan")
        chain.first().checkValidity()
        SigningKey(normalizeKey(key), chain, entity.name, entity.isDebugDefault)
    }

    private fun recoverKey(ks: KeyStore, alias: String, keyPw: String, storePw: String): PrivateKey? {
        for (pw in listOf(keyPw, storePw).distinct()) {
            try {
                (ks.getKey(alias, pw.toCharArray()) as? PrivateKey)?.let { return it }
            } catch (_: Exception) { /* coba password berikutnya */ }
        }
        return null
    }

    /** Konversi key BC → key standar provider sistem agar kompatibel dengan apksig/Conscrypt. */
    private fun normalizeKey(key: PrivateKey): PrivateKey = try {
        KeyFactory.getInstance(key.algorithm).generatePrivate(PKCS8EncodedKeySpec(key.encoded))
    } catch (_: Exception) {
        key
    }

    private fun loadAnyKeystore(bytes: ByteArray, password: CharArray): KeyStore {
        val attempts = listOf<() -> KeyStore>(
            { KeyStore.getInstance("PKCS12", bc) },
            { KeyStore.getInstance("BKS", bc) },
            { KeyStore.getInstance("PKCS12") },
            { KeyStore.getInstance("JKS") }
        )
        var last: Exception? = null
        var wrongPassword = false
        for (factory in attempts) {
            try {
                val ks = factory()
                ks.load(ByteArrayInputStream(bytes), password)
                return ks
            } catch (e: Exception) {
                val msg = (e.message ?: "").lowercase(Locale.ROOT)
                if ("password" in msg || "mac" in msg || e is java.security.UnrecoverableKeyException) wrongPassword = true
                last = e
            }
        }
        throw if (wrongPassword) KeystoreException("Password keystore salah", last)
        else KeystoreException(
            "Format keystore tidak didukung. Untuk JKS lama, konversi dulu: " +
                "keytool -importkeystore -srckeystore lama.jks -destkeystore baru.p12 -deststoretype PKCS12",
            last
        )
    }

    private fun readUri(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw KeystoreException("Tidak dapat membaca file yang dipilih")

    companion object {
        const val DEBUG_FILE = "webify_debug.keystore"
        const val DEBUG_ALIAS = "webifydebugkey"
        const val DEBUG_PASSWORD = "webify123"

        fun fingerprint(cert: X509Certificate, algorithm: String): String =
            MessageDigest.getInstance(algorithm).digest(cert.encoded)
                .joinToString(":") { "%02X".format(it) }
    }
}
