package com.webify.app.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.DecimalFormat

object FileActions {

    /** Info aplikasi terpasang (null bila belum terpasang). Webify mendeklarasikan <queries> MAIN/LAUNCHER. */
    data class InstalledInfo(val versionName: String?, val versionCode: Long, val lastUpdate: Long)

    fun installedInfo(context: Context, packageName: String): InstalledInfo? = try {
        val pi = context.packageManager.getPackageInfo(packageName, 0)
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        InstalledInfo(pi.versionName, code, pi.lastUpdateTime)
    } catch (_: Exception) { null }

    /** Package name & versionCode dari file APK. */
    fun apkInfo(context: Context, apk: File): Pair<String, Long>? = try {
        context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.let { pi ->
            @Suppress("DEPRECATION")
            val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
            pi.packageName to code
        }
    } catch (_: Exception) { null }

    fun launchApp(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent == null) { toast(context, "Aplikasi belum terpasang"); return false }
        return try { context.startActivity(intent); true } catch (e: Exception) { toast(context, "Gagal membuka: ${e.message}"); false }
    }

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Memasang APK hasil build via package installer sistem. */
    fun installApk(context: Context, file: File) {
        if (!file.exists()) {
            toast(context, "File APK tidak ditemukan")
            return
        }
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            toast(context, "Izinkan Webify memasang aplikasi, lalu ketuk Install lagi")
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) { }
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(context, file), "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(context, "Installer paket tidak tersedia")
        }
    }

    fun share(context: Context, file: File) {
        val mime = when (file.extension) {
            "apk" -> "application/vnd.android.package-archive"
            "aab" -> "application/octet-stream"
            else -> "*/*"
        }
        val uri = uriFor(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Bagikan ${file.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Menyalin file ke URI tujuan dari SAF (ACTION_CREATE_DOCUMENT). */
    fun exportTo(context: Context, file: File, target: Uri): Boolean = try {
        context.contentResolver.openOutputStream(target, "w")?.use { out -> file.inputStream().use { it.copyTo(out) } } != null
    } catch (e: Exception) {
        false
    }

    fun copy(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        if (Build.VERSION.SDK_INT < 33) toast(context, "$label disalin")
    }

    fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB")
        var v = bytes / 1024.0
        var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return DecimalFormat("#,##0.#").format(v) + " " + units[i]
    }
}
