package com.webify.app.build

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.webify.app.MainActivity
import com.webify.app.R
import com.webify.app.domain.BuildType
import com.webify.app.util.FileActions

/** Notifikasi sistem saat build selesai (sukses → Install/Bagikan, gagal → ringkasan error). */
class BuildNotificationHelper(private val context: Context) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Hasil build", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Notifikasi saat kompilasi APK/AAB selesai"
                    }
                )
            }
        }
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openConsoleIntent(): PendingIntent {
        val i = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_CONSOLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun notifySuccess(appName: String, result: BuildResult.Success) {
        if (!canNotify()) return
        ensureChannel()
        val file = result.outputFile
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val size = FileActions.humanSize(result.sizeBytes)
        val isApk = result.buildType != BuildType.AAB_PLAYSTORE

        val install = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val canInstall = isApk && (Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls())
        val installPi = PendingIntent.getActivity(context, 2, install, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val share = Intent(Intent.ACTION_SEND).apply {
            type = if (isApk) "application/vnd.android.package-archive" else "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val sharePi = PendingIntent.getActivity(
            context, 3, Intent.createChooser(share, "Bagikan ${file.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val b = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Build Berhasil: $appName")
            .setContentText(if (isApk) "Ketuk untuk menginstal atau membagikan ${file.name} ($size)" else "Ketuk untuk membagikan ${file.name} ($size)")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                (if (isApk) "Ketuk untuk menginstal atau membagikan " else "Siap diunggah ke Play Console: ") + "${file.name} ($size)"
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(if (canInstall) installPi else openConsoleIntent())
            .addAction(0, "Bagikan", sharePi)
        if (canInstall) b.addAction(0, "Install", installPi)
        post(b)
    }

    fun notifyFailure(appName: String, result: BuildResult.Failure) {
        if (!canNotify()) return
        ensureChannel()
        val stage = result.stage?.label ?: "Build"
        val summary = result.summary.lineSequence().firstOrNull()?.take(160) ?: "Terjadi kesalahan"
        val b = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Build Gagal: $appName")
            .setContentText("[$stage] $summary")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Tahap $stage gagal.\n${result.summary.take(600)}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(openConsoleIntent())
        post(b)
    }

    @Suppress("MissingPermission")
    private fun post(b: NotificationCompat.Builder) {
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, b.build())
        } catch (_: SecurityException) {
            // izin dicabut di tengah jalan
        }
    }

    companion object {
        const val CHANNEL_ID = "webify_build_channel"
        private const val NOTIFICATION_ID = 7001
    }
}
