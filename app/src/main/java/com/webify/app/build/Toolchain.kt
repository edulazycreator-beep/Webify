package com.webify.app.build

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/**
 * Lokasi aset toolchain on-device.
 *  - aapt2 : jniLibs/<abi>/libaapt2.so → dieksekusi dari nativeLibraryDir (patuh W^X Android 10+).
 *  - android.jar : dibundel di assets/, disalin sekali ke filesDir/toolchain.
 */
class Toolchain(private val context: Context) {

    private val mutex = Mutex()
    private val toolchainDir = File(context.filesDir, "toolchain")

    val androidJar: File get() = File(toolchainDir, "android.jar")
    val aapt2: File get() = File(context.applicationInfo.nativeLibraryDir, "libaapt2.so")

    data class Status(val aapt2Ready: Boolean, val androidJarReady: Boolean, val abi: String, val message: String) {
        val ready get() = aapt2Ready && androidJarReady
    }

    suspend fun prepare(): Status = withContext(Dispatchers.IO) {
        mutex.withLock {
            toolchainDir.mkdirs()
            val expectedSize = try {
                context.assets.openFd(ASSET_ANDROID_JAR).use { it.length }
            } catch (_: Exception) {
                -1L // aset terkompresi atau tidak ada → bandingkan via stamp versi
            }
            val stamp = File(toolchainDir, "android.jar.stamp")
            val versionStamp = "${context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime}"
            val needCopy = !androidJar.exists() ||
                (expectedSize > 0 && androidJar.length() != expectedSize) ||
                (!stamp.exists() || stamp.readText() != versionStamp)
            if (needCopy) {
                try {
                    val tmp = File(toolchainDir, "android.jar.tmp")
                    context.assets.open(ASSET_ANDROID_JAR).use { ins -> tmp.outputStream().use { ins.copyTo(it, 256 * 1024) } }
                    if (androidJar.exists()) androidJar.delete()
                    tmp.renameTo(androidJar)
                    stamp.writeText(versionStamp)
                } catch (e: FileNotFoundException) {
                    // aset belum diprovisioning (jalankan scripts/prepare_toolchain.sh)
                }
            }
            status()
        }
    }

    fun status(): Status {
        val aapt2Ok = aapt2.exists() && aapt2.length() > 0
        val jarOk = androidJar.exists() && androidJar.length() > 1_000_000
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val msg = buildList {
            if (!aapt2Ok) add("libaapt2.so tidak ditemukan untuk ABI $abi (jalankan scripts/prepare_toolchain.sh sebelum build Webify)")
            if (!jarOk) add("android.jar belum tersedia di assets")
        }.joinToString("; ").ifEmpty { "Toolchain siap" }
        return Status(aapt2Ok, jarOk, abi, msg)
    }

    companion object {
        const val ASSET_ANDROID_JAR = "android.jar"
    }
}
