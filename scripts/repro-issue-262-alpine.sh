#!/usr/bin/env bash
set -euo pipefail

# Build the Kotlin/Native Linux x64 test executable on the host/glibc environment.
./gradlew :core:linkDebugTestLinuxX64

TEST_EXE="core/build/bin/linuxX64/debugTest/test.kexe"

if [ ! -x "$TEST_EXE" ]; then
  echo "Missing test executable: $TEST_EXE" >&2
  exit 1
fi

# Run only the vectorization/posting decoding test inside Alpine gcompat.
# Expected current behavior for issue #262: UnsupportedOperationException from VectorizationProvider.
docker run --rm \
  -v "$PWD:/repo:ro" \
  -w /repo \
  alpine:latest \
  sh -euxc '
    apk add --no-cache gcompat libgcc libstdc++ libunwind tzdata
    ldd '"$TEST_EXE"' || true
    '"$TEST_EXE"' \
      --ktest_filter=org.gnit.lucenekmp.internal.vectorization.TestPostingDecodingUtil.testDuelSplitInts
  '
