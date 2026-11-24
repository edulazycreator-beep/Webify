#!/usr/bin/env bash
# =============================================================================
# Webify — Provisioning aset toolchain on-device
#
#  1. libaapt2.so (arm64-v8a & armeabi-v7a) → app/src/main/jniLibs/<abi>/libaapt2.so
#  2. android.jar (platform android-35)     → app/src/main/assets/android.jar
#  3. Font OFL (opsional, sudah dibundel)   → app/src/main/assets/fonts/
#
# CATATAN PENTING tentang aapt2:
#   Artefak Maven `com.android.tools.build:aapt2` di Google Maven hanya berisi
#   binary HOST (linux x86_64 / macOS / Windows) — tidak bisa dieksekusi di HP
#   Android (ARM, Bionic). Karena itu skrip ini mengambil aapt2 yang dikompilasi
#   STATIS untuk Android dari rilis lzhiyong/android-sdk-tools (dibangun dari
#   source AOSP via NDK). Sumber bisa diganti lewat variabel lingkungan:
#     AAPT2_ARM64_URL / AAPT2_ARMV7_URL  → URL zip/binary alternatif
#     AAPT2_ARM64_PATH / AAPT2_ARMV7_PATH → binary lokal (lewati unduhan)
#
# Pemakaian:  ./scripts/prepare_toolchain.sh
# Prasyarat:  bash, curl, unzip, file (opsional), ANDROID_HOME/ANDROID_SDK_ROOT
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/app/src/main"
JNI="$APP/jniLibs"
ASSETS="$APP/assets"
WORK="$(mktemp -d -t webify-toolchain-XXXXXX)"
trap 'rm -rf "$WORK"' EXIT

PLATFORM_API="${WEBIFY_PLATFORM_API:-35}"
RELEASE_BASE="${AAPT2_RELEASE_BASE:-https://github.com/lzhiyong/android-sdk-tools/releases/latest/download}"
AAPT2_ARM64_URL="${AAPT2_ARM64_URL:-$RELEASE_BASE/android-sdk-tools-static-aarch64.zip}"
AAPT2_ARMV7_URL="${AAPT2_ARMV7_URL:-$RELEASE_BASE/android-sdk-tools-static-arm.zip}"

log()  { printf '\033[1;34m[webify]\033[0m %s\n' "$*"; }
ok()   { printf '\033[1;32m[  ok  ]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[ warn ]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[error ]\033[0m %s\n' "$*" >&2; exit 1; }

need() { command -v "$1" >/dev/null 2>&1 || die "Perintah '$1' dibutuhkan tetapi tidak ditemukan"; }
need curl
need unzip

download() { # url dest
  local url="$1" dest="$2" attempt
  for attempt in 1 2 3; do
    if curl -fL --retry 3 --retry-delay 2 --connect-timeout 20 -o "$dest" "$url"; then
      [ -s "$dest" ] && return 0
    fi
    warn "Unduhan gagal (percobaan $attempt): $url"
    sleep $((attempt * 2))
  done
  return 1
}

# -----------------------------------------------------------------------------
# 1. aapt2
# -----------------------------------------------------------------------------
install_aapt2() { # abi url localPath expectArch
  local abi="$1" url="$2" local_path="$3" expect="$4"
  local dest_dir="$JNI/$abi" dest="$JNI/$abi/libaapt2.so" bin=""
  mkdir -p "$dest_dir"

  if [ -n "$local_path" ]; then
    [ -f "$local_path" ] || die "AAPT2 lokal untuk $abi tidak ditemukan: $local_path"
    bin="$local_path"
    log "aapt2 [$abi] dari file lokal: $bin"
  else
    log "Mengunduh aapt2 [$abi]: $url"
    local pkg="$WORK/aapt2-$abi.pkg"
    download "$url" "$pkg" || die "Tidak dapat mengunduh aapt2 untuk $abi"
    if unzip -tq "$pkg" >/dev/null 2>&1; then
      local x="$WORK/x-$abi"
      mkdir -p "$x"
      unzip -q -o "$pkg" -d "$x"
      bin="$(find "$x" -type f -name aapt2 | head -n 1)"
      [ -n "$bin" ] || die "Binary aapt2 tidak ditemukan di arsip $url"
    else
      bin="$pkg" # URL menunjuk langsung ke binary
    fi
  fi

  if command -v file >/dev/null 2>&1; then
    local info; info="$(file -b "$bin")"
    echo "        $info"
    echo "$info" | grep -q "ELF" || die "aapt2 [$abi] bukan binary ELF"
    echo "$info" | grep -qi "$expect" || die "Arsitektur aapt2 [$abi] tidak cocok (harap $expect)"
    if echo "$info" | grep -qi "dynamically linked"; then
      warn "aapt2 [$abi] dinamis — pastikan semua library tersedia di perangkat (disarankan build statis)"
    fi
  fi

  install -m 0755 "$bin" "$dest"
  ok "jniLibs/$abi/libaapt2.so ($(du -h "$dest" | cut -f1))"
}

install_aapt2 "arm64-v8a"   "$AAPT2_ARM64_URL" "${AAPT2_ARM64_PATH:-}" "aarch64"
install_aapt2 "armeabi-v7a" "$AAPT2_ARMV7_URL" "${AAPT2_ARMV7_PATH:-}" "ARM"

# -----------------------------------------------------------------------------
# 2. android.jar
# -----------------------------------------------------------------------------
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$SDK" ] || die "ANDROID_HOME / ANDROID_SDK_ROOT belum di-set"
JAR="$SDK/platforms/android-$PLATFORM_API/android.jar"

if [ ! -f "$JAR" ]; then
  log "Platform android-$PLATFORM_API belum terpasang, menjalankan sdkmanager…"
  SDKMANAGER="$(command -v sdkmanager || true)"
  if [ -z "$SDKMANAGER" ]; then
    for c in "$SDK/cmdline-tools/latest/bin/sdkmanager" "$SDK"/cmdline-tools/*/bin/sdkmanager "$SDK/tools/bin/sdkmanager"; do
      [ -x "$c" ] && SDKMANAGER="$c" && break
    done
  fi
  [ -n "$SDKMANAGER" ] || die "sdkmanager tidak ditemukan; pasang platforms;android-$PLATFORM_API secara manual"
  yes | "$SDKMANAGER" --sdk_root="$SDK" "platforms;android-$PLATFORM_API" >/dev/null
fi
[ -f "$JAR" ] || die "android.jar tetap tidak ditemukan: $JAR"
mkdir -p "$ASSETS"
cp -f "$JAR" "$ASSETS/android.jar"
ok "assets/android.jar ← platforms/android-$PLATFORM_API ($(du -h "$ASSETS/android.jar" | cut -f1))"

# -----------------------------------------------------------------------------
# 3. Font (sudah ada di repo; unduh ulang hanya jika hilang)
# -----------------------------------------------------------------------------
FONTS="$ASSETS/fonts"
mkdir -p "$FONTS"
fetch_font() { # file url
  if [ -s "$FONTS/$1" ]; then ok "fonts/$1 sudah ada"; return; fi
  if download "$2" "$FONTS/$1"; then ok "fonts/$1 diunduh"; else warn "fonts/$1 gagal diunduh — aplikasi memakai font sistem"; rm -f "$FONTS/$1"; fi
}
GF="https://github.com/google/fonts/raw/main/ofl"
fetch_font "PlusJakartaSans.ttf" "$GF/plusjakartasans/PlusJakartaSans%5Bwght%5D.ttf"
fetch_font "Inter.ttf"           "$GF/inter/Inter%5Bopsz,wght%5D.ttf"
fetch_font "JetBrainsMono.ttf"   "$GF/jetbrainsmono/JetBrainsMono%5Bwght%5D.ttf"

log "Toolchain Webify siap. Lanjutkan dengan: ./gradlew assembleDebug"
