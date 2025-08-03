#!/usr/bin/env bash
# Codex environment bootstrap for lucene-kmp (API 34 + Kotlin/Native prebuilt)
# ---------------------------------------------------------------------------
# * Installs Temurin JDK 24.
# * Adds ISRG Root X1 to the JDK trust store.
# * Installs Android cmdline-tools, then downloads **API 34 platform** and
#   **Build-Tools 34.0.0** directly from Google's CDN.
# * Downloads Kotlin/Native **2.1.21** prebuilt **and its shared LLVM/LLDB
#   dependencies** for Linux x86_64 into `$HOME/.konan`, so Gradle can resolve
#   native toolchains offline, including GCC and libffi.
# * Pulls the pre-seeded Gradle cache (Release assets) into `~/.gradle`.
# * Verifies android-34 platform, Build-Tools 34.0.0, Kotlin/Native bundle, and
#   LLVM/GCC/libffi tool-chain presence.
#
# After this script finishes you can run:   ./gradlew build --offline
# No further outbound HTTP is required during the build.

set -euxo pipefail

# -----------------------------------------------------------------------------
# Config block (versions and URLs)
# -----------------------------------------------------------------------------
#OPT_DIR="$HOME/.local/opt"   #in case mkdir fail in /opt like in jules.google.com
OPT_DIR="/opt"                #/opt is fine in most cases such as chatgpt.com/codex

JDK_VERSION="24.0.1_9"

# Base URL for Gradle cache tarballs (GitHub Release)
RELEASE_VERSION="1.3"
RELEASE_BASE_URL="https://github.com/nehemiaharchives/lucene-kmp-gc/releases/download/${RELEASE_VERSION}"

# -----------------------------------------------------------------------------
# 1  Install JDK 24 (Temurin)
# -----------------------------------------------------------------------------
JDK_URL="https://github.com/adoptium/temurin24-binaries/releases/download/jdk-${JDK_VERSION//_/%2B}/OpenJDK24U-jdk_x64_linux_hotspot_${JDK_VERSION}.tar.gz"

curl -fL "$JDK_URL" -o /tmp/jdk24.tar.gz
mkdir -p "$OPT_DIR/jdk"
# --strip-components=1 removes top-level directory
tar -xzf /tmp/jdk24.tar.gz --strip-components=1 -C "$OPT_DIR/jdk"
rm /tmp/jdk24.tar.gz

export JAVA_HOME="$OPT_DIR/jdk"
export PATH="$JAVA_HOME/bin:$PATH"

# -----------------------------------------------------------------------------
# 2  Add Let’s Encrypt ISRG Root X1 to this JDK
# -----------------------------------------------------------------------------
keytool -importcert -noprompt -trustcacerts \
        -alias isrgrootx1 \
        -file /usr/share/ca-certificates/mozilla/ISRG_Root_X1.crt \
        -keystore "$JAVA_HOME/lib/security/cacerts" \
        -storepass changeit

# -----------------------------------------------------------------------------
# 3  Install Android cmdline-tools
# -----------------------------------------------------------------------------
SDK_ROOT="$OPT_DIR/android-sdk"
SDK_ZIP="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"

export ANDROID_HOME="$SDK_ROOT"

mkdir -p "$SDK_ROOT/cmdline-tools"
curl -fL "$SDK_ZIP" -o /tmp/cmdline-tools.zip
unzip -q /tmp/cmdline-tools.zip -d "$SDK_ROOT/cmdline-tools"
# Rename inner folder to "latest"
mv "$SDK_ROOT/cmdline-tools/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
rm /tmp/cmdline-tools.zip

# Accept Android SDK licences non-interactively
( yes | "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --licenses --sdk_root="$SDK_ROOT" >/dev/null 2>&1 ) || true

# -----------------------------------------------------------------------------
# 4  Download Android API Platform + Build-Tools
# -----------------------------------------------------------------------------
PLATFORM_ZIP="https://dl.google.com/android/repository/platform-34-ext7_r03.zip"
BUILD_ZIP="https://dl.google.com/android/repository/build-tools_r34-linux.zip"

mkdir -p "$SDK_ROOT/platforms" "$SDK_ROOT/build-tools"

curl -fL "$PLATFORM_ZIP" -o /tmp/platform-34.zip
unzip -q /tmp/platform-34.zip -d "$SDK_ROOT/platforms"
rm /tmp/platform-34.zip

curl -fL "$BUILD_ZIP" -o /tmp/build-tools.zip
mkdir -p /tmp/build-tmp
unzip -q /tmp/build-tools.zip -d /tmp/build-tmp
mv /tmp/build-tmp/android-14 "$SDK_ROOT/build-tools/34.0.0"
rm -rf /tmp/build-tools.zip /tmp/build-tmp

# -----------------------------------------------------------------------------
# 5  Download Kotlin/Native prebuilt + dependencies into ~/.konan
# -----------------------------------------------------------------------------
KONAN_DIR="$HOME/.konan"
mkdir -p "$KONAN_DIR"

# 5a – compiler bundle
KONAN_VERSION="2.2.0"
KONAN_PLATFORM="linux-x86_64"   # this container is Linux/amd64
KONAN_PREBUILT_DIR="kotlin-native-prebuilt-${KONAN_PLATFORM}-${KONAN_VERSION}"
KONAN_ARCHIVE="$KONAN_PREBUILT_DIR.tar.gz"
KONAN_URL="https://github.com/JetBrains/kotlin/releases/download/v${KONAN_VERSION}/${KONAN_ARCHIVE}"

curl -fL "$KONAN_URL" -o /tmp/${KONAN_ARCHIVE}

tar -xzf /tmp/${KONAN_ARCHIVE} -C "$KONAN_DIR"
rm /tmp/${KONAN_ARCHIVE}

# 5b – LLVM, LLDB, GCC, and libffi shared dependencies
DEPS_DIR="$KONAN_DIR/dependencies"
mkdir -p "$DEPS_DIR"

LLVM_DIR="llvm-19-x86_64-linux-essentials-100"
LLDB_DIR="lldb-4-linux"
GCC_DIR="x86_64-unknown-linux-gnu-gcc-8.3.0-glibc-2.19-kernel-4.9-2"
LIBFFI_DIR="libffi-3.2.1-2-linux-x86-64"

LLVM_ARCHIVE="$LLVM_DIR.tar.gz"
LLDB_ARCHIVE="$LLDB_DIR.tar.gz"
GCC_ARCHIVE="$GCC_DIR.tar.gz"
LIBFFI_ARCHIVE="$LIBFFI_DIR.tar.gz"

LLVM_URL="https://download.jetbrains.com/kotlin/native/resources/llvm/19-x86_64-linux/$LLVM_ARCHIVE"
LLDB_URL="https://download.jetbrains.com/kotlin/native/$LLDB_ARCHIVE"
GCC_URL="https://download.jetbrains.com/kotlin/native/$GCC_ARCHIVE"
LIBFFI_URL="https://download.jetbrains.com/kotlin/native/$LIBFFI_ARCHIVE"

curl -fL "$LLVM_URL" -o /tmp/${LLVM_ARCHIVE}
 tar -xzf /tmp/${LLVM_ARCHIVE} -C "$DEPS_DIR"
rm /tmp/${LLVM_ARCHIVE}

curl -fL "$LLDB_URL" -o /tmp/${LLDB_ARCHIVE}
 tar -xzf /tmp/${LLDB_ARCHIVE} -C "$DEPS_DIR"
rm /tmp/${LLDB_ARCHIVE}

curl -fL "$GCC_URL" -o /tmp/${GCC_ARCHIVE}
 tar -xzf /tmp/${GCC_ARCHIVE} -C "$DEPS_DIR"
rm /tmp/${GCC_ARCHIVE}

curl -fL "$LIBFFI_URL" -o /tmp/${LIBFFI_ARCHIVE}
 tar -xzf /tmp/${LIBFFI_ARCHIVE} -C "$DEPS_DIR"
rm /tmp/${LIBFFI_ARCHIVE}

# 5c – Populate .extracted so DependencyProcessor skips network fetches
cat > "$DEPS_DIR/.extracted" <<EOF
$GCC_DIR
$LLDB_DIR
$LLVM_DIR
$LIBFFI_DIR
EOF

# -----------------------------------------------------------------------------
# 6  Fetch Gradle offline cache from Release assets
# -----------------------------------------------------------------------------
# Remove any previous Gradle cache to ensure a clean state
rm -rf "$HOME/.gradle"

# a) modules-2
echo "🔽  Downloading modules-2 cache…"
curl -L "$RELEASE_BASE_URL/caches_modules-2.tar.gz" -o /tmp/caches_modules-2.tar.gz
mkdir -p "$HOME/.gradle/caches"
tar -xzf /tmp/caches_modules-2.tar.gz -C "$HOME/.gradle/caches"
rm /tmp/caches_modules-2.tar.gz

# b) jars-* caches (if any)
echo "🔽  Downloading jars-* cache…"
curl -L "$RELEASE_BASE_URL/caches_jars.tar.gz" -o /tmp/caches_jars.tar.gz
if tar -tzf /tmp/caches_jars.tar.gz &>/dev/null; then
  tar -xzf /tmp/caches_jars.tar.gz -C "$HOME/.gradle/caches"
  rm /tmp/caches_jars.tar.gz
else
  echo "⚠️  No jars-* tarball found or empty"
  rm /tmp/caches_jars.tar.gz || true
fi

# c) wrapper
echo "🔽  Downloading Gradle wrapper caches…"
curl -L "$RELEASE_BASE_URL/wrapper.tar.gz" -o /tmp/wrapper.tar.gz
tar -xzf /tmp/wrapper.tar.gz -C "$HOME/.gradle"
rm /tmp/wrapper.tar.gz

# d) android
echo "🔽  Downloading Android cache…"
curl -L "$RELEASE_BASE_URL/android.tar.gz" -o /tmp/android.tar.gz
if tar -tzf /tmp/android.tar.gz &>/dev/null; then
  tar -xzf /tmp/android.tar.gz -C "$HOME/.gradle"
  rm /tmp/android.tar.gz
else
  echo "⚠️  No android tarball found or empty"
  rm /tmp/android.tar.gz || true
fi

# -----------------------------------------------------------------------------
# 7  Disable Kotlin/Native auto-download
# -----------------------------------------------------------------------------
mkdir -p "$HOME/.gradle"
cat >> "$HOME/.gradle/gradle.properties" <<EOF
kotlin.native.distribution.download=false
kotlin.native.home=$KONAN_DIR/$KONAN_PREBUILT_DIR
EOF

# -----------------------------------------------------------------------------
# 8  Sanity checks
# -----------------------------------------------------------------------------
java -version
javac -version

[[ -f "$SDK_ROOT/platforms/android-34/android.jar" ]]          || { echo "❌  android-34 platform missing" >&2; exit 1; }
[[ -x "$SDK_ROOT/build-tools/34.0.0/aapt2" ]]                  || { echo "❌  Build-Tools 34.0.0 missing" >&2; exit 1; }
[[ -d "$KONAN_DIR/$KONAN_PREBUILT_DIR" ]]                      || { echo "❌  Kotlin/Native bundle missing" >&2; exit 1; }
[[ -x "$DEPS_DIR/$LLVM_DIR/bin/clang-19" ]]                    || { echo "❌  LLVM dependencies missing" >&2; exit 1; }
[[ -x "$DEPS_DIR/$LLDB_DIR/bin/lldb" ]]                        || { echo "❌  LLDB dependencies missing" >&2; exit 1; }
[[ -x "$DEPS_DIR/$GCC_DIR/bin/x86_64-unknown-linux-gnu-gcc" ]] || { echo "❌  GCC dependencies missing" >&2; exit 1; }
[[ -d "$DEPS_DIR/$LIBFFI_DIR/lib" ]]                           || { echo "❌  libffi dependencies missing" >&2; exit 1; }

echo "✅  All Android and Kotlin/Native components present — you can now run ./gradlew build --offline"
./gradlew --offline :core:compileKotlinJvm :core:compileKotlinLinuxX64
