import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Signing rilis opsional: dari env (GitHub Secrets) atau keystore.properties lokal.
val releaseSigning: Map<String, String>? = run {
    val env = System.getenv()
    val fromEnv = env["WEBIFY_KEYSTORE_FILE"]?.takeIf { it.isNotBlank() && file(it).exists() }?.let {
        mapOf(
            "storeFile" to it,
            "storePassword" to (env["WEBIFY_KEYSTORE_PASSWORD"] ?: ""),
            "keyAlias" to (env["WEBIFY_KEY_ALIAS"] ?: ""),
            "keyPassword" to (env["WEBIFY_KEY_PASSWORD"] ?: env["WEBIFY_KEYSTORE_PASSWORD"] ?: "")
        )
    }
    fromEnv ?: rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
        val p = Properties().apply { f.inputStream().use { load(it) } }
        mapOf(
            "storeFile" to rootProject.file(p.getProperty("storeFile")).absolutePath,
            "storePassword" to p.getProperty("storePassword", ""),
            "keyAlias" to p.getProperty("keyAlias", ""),
            "keyPassword" to p.getProperty("keyPassword", p.getProperty("storePassword", ""))
        )
    }
}

android {
    namespace = "com.webify.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.webify.app"
        // API 26+: bundletool & ecj membutuhkan java.nio.file secara penuh.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables { useSupportLibrary = true }
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning.getValue("storeFile"))
                storePassword = releaseSigning.getValue("storePassword")
                keyAlias = releaseSigning.getValue("keyAlias")
                keyPassword = releaseSigning.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ""
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Fallback ke debug signing agar build CI tidak pernah gagal karena secret kosong.
            signingConfig = if (releaseSigning != null) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // android.jar bundel tidak boleh dikompresi ulang agar bisa disalin cepat.
    androidResources { noCompress += listOf("jar") }

    packaging {
        jniLibs {
            // Wajib agar libaapt2.so diekstrak ke nativeLibraryDir (W^X compliant).
            useLegacyPackaging = true
            // libaapt2.so adalah executable statis, bukan shared library: jangan di-strip.
            keepDebugSymbols += listOf("**/libaapt2.so")
        }
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/LICENSE.md",
                "META-INF/NOTICE", "META-INF/NOTICE.txt", "META-INF/NOTICE.md", "META-INF/ASL2.0",
                "META-INF/*.kotlin_module", "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/BCKEY.DSA", "META-INF/BCKEY.SF", "META-INF/BC1024KE.DSA", "META-INF/BC1024KE.SF",
                "META-INF/BC2048KE.DSA", "META-INF/BC2048KE.SF", "META-INF/*.RSA", "META-INF/*.SF",
                "META-INF/eclipse.inf", "META-INF/ecj.1", "META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties",
                "META-INF/services/javax.annotation.processing.Processor",
                "META-INF/services/javax.tools.JavaCompiler",
                "about.html", "plugin.properties", "plugin.xml", "about_files/**",
                "**/*.proto", "google/protobuf/**", "LICENSE", "NOTICE",
                "/META-INF/{AL2.0,LGPL2.1}"
            )
            pickFirsts += listOf("META-INF/versions/9/module-info.class", "module-info.class")
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // Compose & UI
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Persistensi & keamanan
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Coroutines, jaringan & serialisasi
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Toolchain kompilasi on-device
    implementation("org.eclipse.jdt:ecj:3.37.0")
    implementation("com.android.tools:r8:8.3.37")
    // bundletool membawa aapt2-proto, protobuf & guava secara transitif.
    implementation("com.android.tools.build:bundletool:1.16.0")
    // Guava dipakai langsung (ImmutableList untuk BuildBundleCommand); bundletool hanya membawanya di runtime.
    implementation("com.google.guava:guava:32.1.3-jre")
    implementation("com.android.tools.build:apksig:8.3.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
}
