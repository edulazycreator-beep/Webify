# Webify — Mobile IDE & Website-to-App Builder

**Webify** (`com.webify.app`) mengubah website (URL) atau proyek web lokal (ZIP HTML/CSS/JS) menjadi aplikasi Android mandiri — **`.apk`** untuk uji coba dan **`.aab`** siap unggah ke Google Play Console — **100% di perangkat**, tanpa server cloud.

```
Resources → Manifest → AAPT2 → Java Gen → ECJ → D8 DEX → Package & Sign
```

## Struktur proyek

```
Webify/
├─ .github/workflows/build-webify.yml   CI: build APK debug+release, artifact 30 hari, GitHub Release untuk tag v*
├─ scripts/prepare_toolchain.sh         Unduh libaapt2.so (arm64/armv7) + salin android.jar ke assets
├─ app/
│  ├─ build.gradle.kts                  Compose BOM, Room/KSP, ecj, r8, bundletool, apksig, BouncyCastle, OkHttp
│  ├─ proguard-rules.pro                Melindungi toolchain dari R8
│  └─ src/main/
│     ├─ assets/java_templates/*.java   Template aplikasi output (MainActivity, WebViewClient, ChromeClient, …)
│     ├─ assets/fonts/                  Plus Jakarta Sans, Inter, JetBrains Mono (OFL, offline)
│     ├─ jniLibs/<abi>/libaapt2.so      ← dibuat oleh prepare_toolchain.sh (tidak di-commit)
│     └─ java/com/webify/app/
│        ├─ data/      Room (entities, DAO, DB), EncryptedSharedPreferences, repository
│        ├─ crypto/    KeystoreManager (BouncyCastle), PackageSigner (apksig v1+v2+v3, JAR signer AAB)
│        ├─ build/     WebifyBuildPipeline, ResourceGenerator, ManifestGenerator, Aapt2Wrapper,
│        │             EcjCompilerWrapper, D8CompilerWrapper, ZipAligner, ApkPackager, AabPackager, BuildManager
│        ├─ template/  JavaTemplateGenerator (BuildConfig.java dinamis + template)
│        ├─ ai/        AiService (Gemini / OpenAI / custom, streaming SSE), AiAssistantViewModel, PromptBuilder
│        └─ ui/        Theme "Obsidian & Cobalt IDE", navigasi, Dashboard, Builder Studio 5 tab,
│                      Live Build Console, Keystore Vault, AI Co-Pilot
```

## Push ke GitHub & unduh APK Webify

1. Buat repositori kosong di GitHub (mis. `webify`).
2. Di folder proyek:
   ```bash
   git init
   git add .
   git commit -m "Webify: initial commit"
   git branch -M main
   git remote add origin https://github.com/<username>/webify.git
   git push -u origin main
   ```
3. Buka tab **Actions** → workflow **Build Webify APK** berjalan otomatis (atau klik **Run workflow**).
4. Setelah selesai, buka run tersebut → bagian **Artifacts** → unduh **Webify-debug** / **Webify-release** (ZIP berisi APK).
5. Rilis bertanda versi: `git tag v1.0.0 && git push origin v1.0.0` → APK otomatis dilampirkan di **Releases**.

### (Opsional) Tanda tangan rilis Webify di CI

Tambahkan di *Settings → Secrets and variables → Actions*:

| Secret | Isi |
|---|---|
| `WEBIFY_KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `WEBIFY_KEYSTORE_PASSWORD` | password keystore |
| `WEBIFY_KEY_ALIAS` | alias kunci |
| `WEBIFY_KEY_PASSWORD` | password kunci |

Tanpa secrets, `Webify-release.apk` ditandatangani debug key agar build tidak pernah gagal.

### Build lokal

```bash
export ANDROID_HOME=$HOME/Android/Sdk
./scripts/prepare_toolchain.sh
./gradlew assembleDebug
```

## Catatan teknis penting

- **Sumber aapt2.** Artefak Maven `com.android.tools.build:aapt2` hanya berisi binary *host* (Linux x86-64/macOS/Windows), sehingga tidak bisa dijalankan di HP. `prepare_toolchain.sh` memakai aapt2 **statis untuk Android** dari rilis [`lzhiyong/android-sdk-tools`](https://github.com/lzhiyong/android-sdk-tools) (`android-sdk-tools-static-aarch64.zip` / `-arm.zip`). Sumber lain bisa dipakai via `AAPT2_ARM64_URL`, `AAPT2_ARMV7_URL`, atau path lokal `AAPT2_ARM64_PATH`, `AAPT2_ARMV7_PATH`.
- **W^X Android 10+.** Binary dikemas sebagai `jniLibs/<abi>/libaapt2.so` dengan `useLegacyPackaging = true` + `extractNativeLibs="true"`, lalu dieksekusi dari `nativeLibraryDir`. File ini juga dikecualikan dari proses strip.
- **bundletool.** Dipanggil melalui `BuildBundleCommand` (API yang sama dengan `build-bundle`), bukan `BundleToolMain.main()`, karena `main()` dapat memanggil `System.exit()` dan menutup aplikasi saat error.
- **Format keystore.** Android tidak memiliki provider JKS, jadi Webify membuat keystore **PKCS#12** (`.p12`, format default `keytool` sejak JDK 9 dan diterima Play App Signing). JKS lama perlu dikonversi dulu: `keytool -importkeystore -srckeystore lama.jks -destkeystore baru.p12 -deststoretype PKCS12`.
- **Signing AAB.** Signing AAB memakai JAR v1 (MANIFEST.MF/.SF/.RSA, SHA256withRSA). Play Console **menolak** AAB yang ditandatangani debug key, jadi pilih keystore rilis.
- **ecj di ART.** ecj 3.37 dijalankan in-process dengan `-proc:none` dan *core library desugaring*. Jika perangkat lama bermasalah, turunkan versi ecj di `app/build.gradle.kts`.
- **Build berjalan di scope aplikasi.** Build tetap berjalan saat Anda berpindah layar, tetapi biarkan Webify tetap terbuka sampai build selesai (belum memakai foreground service).
- **JS Bridge.** `window.WebifyBridge` tersedia untuk semua halaman pada domain internal. Batasi `allowedDomains` hanya ke domain yang Anda percayai.

## API `window.WebifyBridge` (aplikasi hasil generate)

```js
if (window.WebifyBridge) {
  WebifyBridge.showToast('Halo dari web!');
  WebifyBridge.vibrate(80);
  WebifyBridge.shareText('Cek aplikasi ini', 'Bagikan');
  const v = WebifyBridge.getAppVersion();          // "1.0.0"
  const info = JSON.parse(WebifyBridge.getDeviceInfo());
  WebifyBridge.copyToClipboard('teks');
  WebifyBridge.openExternal('https://contoh.com');
  WebifyBridge.closeApp();
}
```

Lisensi font: SIL Open Font License 1.1 (lihat `app/src/main/assets/fonts/licenses/`).
