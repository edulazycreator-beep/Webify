package com.webify.app.build

import com.webify.app.data.db.WebProjectEntity
import com.webify.app.build.ResourceGenerator.Companion.xmlAttr
import com.webify.app.domain.NativeConfigCodec

/** Tahap 2: Menghasilkan AndroidManifest.xml aplikasi output secara dinamis. */
object ManifestGenerator {

    fun generate(p: WebProjectEntity, ws: Workspace, log: BuildLogger) {
        val perms = linkedSetOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE")
        val extraPermLines = mutableListOf<String>()
        if (p.permCamera) perms += "android.permission.CAMERA"
        if (p.permMicrophone) {
            perms += "android.permission.RECORD_AUDIO"
            perms += "android.permission.MODIFY_AUDIO_SETTINGS"
        }
        if (p.permGeolocation) {
            perms += "android.permission.ACCESS_FINE_LOCATION"
            perms += "android.permission.ACCESS_COARSE_LOCATION"
        }
        if (p.permNotifications || p.fcmEnabled) perms += "android.permission.POST_NOTIFICATIONS"
        if (p.permCamera || p.qrScannerEnabled) perms += "android.permission.CAMERA"
        if (p.enableBiometricLock) {
            perms += "android.permission.USE_BIOMETRIC"
            perms += "android.permission.USE_FINGERPRINT"
        }
        perms += "android.permission.VIBRATE"
        if (p.permStorageDownloads || p.enableDownloadManager) {
            extraPermLines += """<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28"/>"""
        }

        val features = buildList {
            if (p.permCamera) add("""<uses-feature android:name="android.hardware.camera" android:required="false"/>""")
            if (p.permGeolocation) add("""<uses-feature android:name="android.hardware.location.gps" android:required="false"/>""")
            if (p.permMicrophone) add("""<uses-feature android:name="android.hardware.microphone" android:required="false"/>""")
        }

        val deepLink = p.deepLinkHost.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        val deepLinkFilter = if (deepLink.isNotEmpty()) """
            <intent-filter android:autoVerify="true">
                <action android:name="android.intent.action.VIEW"/>
                <category android:name="android.intent.category.DEFAULT"/>
                <category android:name="android.intent.category.BROWSABLE"/>
                <data android:scheme="https"/>
                <data android:scheme="http"/>
                <data android:host="${xmlAttr(deepLink)}"/>
            </intent-filter>""" else ""

        val orientation = p.screenOrientation.manifestValue
        val hasShortcuts = NativeConfigCodec.shortcuts(p.shortcutsJson).any { it.shortLabel.isNotBlank() && it.targetUrl.isNotBlank() }
        val pkg = p.packageName

        val manifest = buildString {
            appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            appendLine("""<manifest xmlns:android="http://schemas.android.com/apk/res/android"""")
            appendLine("""    package="$pkg"""")
            appendLine("""    android:versionCode="${p.versionCode}"""")
            appendLine("""    android:versionName="${xmlAttr(p.versionName)}">""")
            appendLine()
            appendLine("""    <uses-sdk android:minSdkVersion="${p.minSdk}" android:targetSdkVersion="${p.targetSdk}"/>""")
            perms.forEach { appendLine("""    <uses-permission android:name="$it"/>""") }
            extraPermLines.forEach { appendLine("    $it") }
            features.forEach { appendLine("    $it") }
            appendLine()
            appendLine("""    <application""")
            appendLine("""        android:label="@string/app_name"""")
            appendLine("""        android:icon="@mipmap/ic_launcher"""")
            appendLine("""        android:roundIcon="@mipmap/ic_launcher_round"""")
            appendLine("""        android:theme="@style/WebifyTheme"""")
            appendLine("""        android:allowBackup="true"""")
            appendLine("""        android:supportsRtl="true"""")
            appendLine("""        android:hardwareAccelerated="true"""")
            appendLine("""        android:largeHeap="true"""")
            appendLine("""        android:usesCleartextTraffic="${p.allowMixedContent}"""")
            appendLine("""        android:networkSecurityConfig="@xml/network_security_config">""")
            appendLine()
            appendLine("""        <activity""")
            appendLine("""            android:name=".MainActivity"""")
            appendLine("""            android:exported="true"""")
            appendLine("""            android:launchMode="singleTask"""")
            appendLine("""            android:screenOrientation="$orientation"""")
            appendLine("""            android:windowSoftInputMode="adjustResize"""")
            appendLine("""            android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboard|keyboardHidden|navigation|uiMode|density">""")
            appendLine("""            <intent-filter>""")
            appendLine("""                <action android:name="android.intent.action.MAIN"/>""")
            appendLine("""                <category android:name="android.intent.category.LAUNCHER"/>""")
            appendLine("""            </intent-filter>""")
            if (deepLinkFilter.isNotEmpty()) appendLine(deepLinkFilter.trimEnd())
            if (hasShortcuts) appendLine("""            <meta-data android:name="android.app.shortcuts" android:resource="@xml/shortcuts"/>""")
            appendLine("""        </activity>""")
            appendLine()
            appendLine("""        <receiver android:name=".WebifyNotificationReceiver" android:exported="false"/>""")
            if (p.qrScannerEnabled) {
                appendLine("""        <activity android:name=".WebifyQrScannerActivity" android:exported="false"""")
                appendLine("""            android:screenOrientation="portrait" android:theme="@android:style/Theme.Material.NoActionBar.Fullscreen"/>""")
            }
            if (p.fcmEnabled) {
                appendLine("""        <service android:name=".WebifyFirebaseMessagingService" android:exported="false">""")
                appendLine("""            <intent-filter>""")
                appendLine("""                <action android:name="com.google.firebase.MESSAGING_EVENT"/>""")
                appendLine("""            </intent-filter>""")
                appendLine("""        </service>""")
                appendLine("""        <meta-data android:name="com.google.firebase.messaging.default_notification_channel_id" android:value="webify_push"/>""")
                appendLine("""        <meta-data android:name="com.google.firebase.messaging.default_notification_icon" android:resource="@drawable/wf_ic_notifications"/>""")
            }
            if (p.admobEnabled) {
                appendLine("""        <meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="${xmlAttr(p.admobAppId.trim())}"/>""")
            }
            appendLine()
            appendLine("""        <provider""")
            appendLine("""            android:name=".WebifyFileProvider"""")
            appendLine("""            android:authorities="$pkg.fileprovider"""")
            appendLine("""            android:exported="false"""")
            appendLine("""            android:grantUriPermissions="true">""")
            appendLine("""            <meta-data android:name="webify.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths"/>""")
            appendLine("""        </provider>""")
            appendLine("""    </application>""")
            appendLine("""</manifest>""")
        }
        ws.manifest.writeText(manifest)
        log.info("AndroidManifest.xml: package=$pkg versionCode=${p.versionCode} versionName=${p.versionName}")
        log.info("Permissions: " + (perms.map { it.substringAfterLast('.') } +
            if (extraPermLines.isNotEmpty()) listOf("WRITE_EXTERNAL_STORAGE(≤28)") else emptyList()).joinToString(", "))
        if (deepLink.isNotEmpty()) log.info("Deep link / App Links aktif untuk host: $deepLink")
    }
}
