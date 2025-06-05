#!/usr/bin/env bash
# Codex environment bootstrap for lucene-kmp (API 34 online fetch)
# ---------------------------------------------------------------
# * Installs Temurin JDK 24.
# * Adds ISRG Root X1 to the JDK trust store.
# * Installs Android cmdline‑tools, then downloads **API 34 platform** and **Build‑Tools 34.0.0** directly from Google's CDN (dl.google.com).
# * Pulls the pre‑seeded Gradle cache (Git LFS) into ~/.gradle.
# * Verifies android‑34 platform & Build‑Tools 34.0.0 are present.
#
# Result: `./gradlew build --offline` works; only Google downloads are two ZIPs
# (~60 MB each) rather than hosting them in your repo.

set -euxo pipefail

# -----------------------------------------------------------------------------
# 1  Install JDK 24 (Temurin)
# -----------------------------------------------------------------------------
JDK_URL="https://github.com/adoptium/temurin24-binaries/releases/download/jdk-24.0.1%2B9/OpenJDK24U-jdk_x64_linux_hotspot_24.0.1_9.tar.gz"

curl -fL "$JDK_URL" -o /tmp/jdk24.tar.gz
mkdir -p /opt/jdk
# --strip-components=1 removes the top-level directory inside the tarball
tar -xzf /tmp/jdk24.tar.gz --strip-components=1 -C /opt/jdk
rm /tmp/jdk24.tar.gz

export JAVA_HOME=/opt/jdk
export PATH="$JAVA_HOME/bin:$PATH"

# -----------------------------------------------------------------------------
# 2  Add Let’s Encrypt ISRG Root X1 to this JDK
# -----------------------------------------------------------------------------
keytool -importcert -noprompt -trustcacerts \
        -alias isrgrootx1 \
        -file /usr/share/ca-certificates/mozilla/ISRG_Root_X1.crt \
        -keystore "$JAVA_HOME/lib/security/cacerts" \
        -storepass changeit

# -----------------------------------------------------------------------------
# 3  Install Android cmdline‑tools (manager only)
# -----------------------------------------------------------------------------
SDK_ROOT=/opt/android-sdk
SDK_ZIP="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"  # May 2025 revision

mkdir -p "$SDK_ROOT/cmdline-tools"
curl -fL "$SDK_ZIP" -o /tmp/cmdline-tools.zip
unzip -q /tmp/cmdline-tools.zip -d "$SDK_ROOT/cmdline-tools"
# Rename inner folder to "latest" per Google guidance
mv "$SDK_ROOT/cmdline-tools/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
rm /tmp/cmdline-tools.zip

# -----------------------------------------------------------------------------
# 4  Download API 34 platform + Build‑Tools 34.0.0 directly from Google CDN
# -----------------------------------------------------------------------------
#  • platform-34-ext7_r03.zip contains the android‑34 platform directory.
#  • build-tools_r34-linux.zip contains Build‑Tools 34.0.0.
PLATFORM_ZIP="https://dl.google.com/android/repository/platform-34-ext7_r03.zip"
BUILD_ZIP="https://dl.google.com/android/repository/build-tools_r34-linux.zip"

mkdir -p "$SDK_ROOT/platforms" "$SDK_ROOT/build-tools"

curl -fL "$PLATFORM_ZIP" -o /tmp/platform-34.zip
unzip -q /tmp/platform-34.zip -d "$SDK_ROOT/platforms"
rm /tmp/platform-34.zip

# Download Build-Tools r34 ZIP, which unfortunately extracts into a nested
# directory called "android-14". We relocate it to <sdk>/build-tools/34.0.0 so
# that AGP can discover it.
curl -fL "$BUILD_ZIP" -o /tmp/build-tools.zip
mkdir -p /tmp/build-tmp
unzip -q /tmp/build-tools.zip -d /tmp/build-tmp
mv /tmp/build-tmp/android-14 "$SDK_ROOT/build-tools/34.0.0"
rm -rf /tmp/build-tools.zip /tmp/build-tmp

# -----------------------------------------------------------------------------
# 5  Fetch Gradle offline cache (Git LFS)
# -----------------------------------------------------------------------------
CACHE_REPO="https://github.com/nehemiaharchives/lucene-kmp-gc.git"
TMP_CLONE="/tmp/lucene-kmp-gc"

git clone --depth 1 "$CACHE_REPO" "$TMP_CLONE"
rsync -a "$TMP_CLONE/" ~/.gradle/
rm -rf "$TMP_CLONE"

# -----------------------------------------------------------------------------
# 6  Sanity checks & Android verification
# -----------------------------------------------------------------------------
java  -version
javac -version

if [[ ! -f "$SDK_ROOT/platforms/android-34/android.jar" ]]; then
  echo "❌  android‑34 platform missing" >&2; exit 1
fi
if [[ ! -x "$SDK_ROOT/build-tools/34.0.0/aapt2" ]]; then
  echo "❌  Build‑Tools 34.0.0 missing" >&2; exit 1
fi

echo "✅  android‑34 platform and Build‑Tools 34.0.0 present"
ls -d "$SDK_ROOT"/platforms/android-34 "$SDK_ROOT"/build-tools/34.0.0 || true

echo "✅  Environment ready – run ./gradlew build --offline"
