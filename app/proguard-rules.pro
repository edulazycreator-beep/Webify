# =============================================================================
# Webify — aturan R8/ProGuard
# Toolchain on-device (ecj, D8/R8, bundletool, apksig, BouncyCastle) memakai
# refleksi, ServiceLoader, dan resource internal; jangan di-obfuscate / dibuang.
# =============================================================================

-ignorewarnings
-dontobfuscate
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable,Exceptions

# ---- Eclipse Compiler for Java (ecj) ---------------------------------------
-keep class org.eclipse.jdt.** { *; }
-keep class org.eclipse.jdt.internal.compiler.batch.** { *; }
-dontwarn org.eclipse.jdt.**
-dontwarn javax.lang.model.**
-dontwarn javax.annotation.processing.**
-dontwarn javax.tools.**

# ---- D8 / R8 ----------------------------------------------------------------
-keep class com.android.tools.r8.** { *; }
-dontwarn com.android.tools.r8.**

# ---- bundletool (+ dependensi: guava, protobuf, dagger, jose4j, aapt2-proto) --
-keep class com.android.tools.build.bundletool.** { *; }
-keep class com.android.bundle.** { *; }
-keep class com.android.aapt.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class com.google.common.** { *; }
-keep class dagger.** { *; }
-keep class org.jose4j.** { *; }
-dontwarn com.android.tools.build.bundletool.**
-dontwarn com.google.protobuf.**
-dontwarn com.google.common.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**
-dontwarn dagger.**
-dontwarn org.jose4j.**
-dontwarn org.slf4j.**
-dontwarn com.google.auto.value.**
-dontwarn org.bitbucket.**

# ---- apksig -----------------------------------------------------------------
-keep class com.android.apksig.** { *; }
-dontwarn com.android.apksig.**

# ---- BouncyCastle (JCA provider memakai refleksi berbasis nama kelas) --------
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ---- OkHttp / Okio ----------------------------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# ---- kotlinx.serialization ----------------------------------------------------
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-dontwarn kotlinx.serialization.**

# ---- Room -------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class com.webify.app.data.db.** { *; }

# ---- Security Crypto / Tink ---------------------------------------------------
-dontwarn com.google.api.client.**
-dontwarn com.google.crypto.tink.**
-dontwarn org.joda.time.**

# ---- Webify ------------------------------------------------------------------
-keep class com.webify.app.build.** { *; }
-keep class com.webify.app.crypto.** { *; }
