# lucene-kmp 10.2.0-alpha14 Release Notes

This is a release prepared mainly for the upcoming `bbl-kmp` release. It focuses on platform coverage, build/tooling updates, and making the project documentation easier to use.

## Highlights

- Added Linux ARM64 support.
- Added Alpine Linux support.
- Upgraded the build to Kotlin `2.4.0` with workaround and bug fixes.
- Upgraded the Gradle wrapper to Gradle `9.5.1`.
- Added the lucene-kmp documentation website.
- Added generated API documentation through Dokka.

## Platform support

`10.2.0-alpha14` expands the supported Linux target matrix.

Supported targets now include:

- JVM
- Android
- iOS: `iosArm64`, `iosSimulatorArm64`, `iosX64`
- macOS: `macosArm64`, `macosX64`
- Linux: `linuxX64`, `linuxArm64`
- Windows: `mingwX64`
- Alpine Linux

The Linux ARM64 and Alpine Linux work is especially important for `bbl-kmp`, because it improves the path toward broader native CLI distribution.

## Build and dependency updates

- Kotlin was upgraded to `2.4.0`.
- Gradle was upgraded to `9.5.1`.
- The build configuration was adjusted for the expanded Kotlin Multiplatform target matrix.
- Documentation generation is now part of the project build workflow through Dokka.

## Documentation

This release adds the first lucene-kmp website under `docs/`.

The documentation work includes:

- A Hugo/Doks-based documentation site.
- Usage documentation moved into the website structure.
- Generated Dokka API documentation copied into `docs/api`.
- GitHub Pages workflow support for building and publishing the website and API docs.

## Notes for bbl-kmp

This release is mainly a support release for `bbl-kmp`.

For apps using lucene-kmp through Maven Central, update dependencies from:

```kotlin
implementation("org.gnit.lucene-kmp:lucene-kmp-core:10.2.0-alpha13")
implementation("org.gnit.lucene-kmp:lucene-kmp-queryparser:10.2.0-alpha13")
```

to:

```kotlin
implementation("org.gnit.lucene-kmp:lucene-kmp-core:10.2.0-alpha14")
implementation("org.gnit.lucene-kmp:lucene-kmp-queryparser:10.2.0-alpha14")
```

Use the same version bump for other lucene-kmp modules such as `analysis-common`, `analysis-extra`, `analysis-kuromoji`, `analysis-nori`, `analysis-smartcn`, `analysis-morfologik`, `codecs`, and `queries`.

## Compatibility

This is still an alpha release. APIs and supported target behavior may continue to change before a stable lucene-kmp release.