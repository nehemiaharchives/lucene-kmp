---
applyTo:
  - "lucene-kmp/**"
  - "lucene-kmp/core/src/commonTest/kotlin/**"
description: "KMP + Lucene porting rules for code and tests"
---

# Kotlin Multiplatform & Lucene Porting Rules

- **Target**: Kotlin *common* only (Android/iOS/JVM). **Do not** use JDK-only APIs. Avoid `expect/actual`.
- **Packages**
    - Ported Lucene classes: keep subpackages `analysis, codecs, document, index, internal, search, store, util`.
    - Map `org.apache.lucene.*` → `org.gnit.lucenekmp.*` one-to-one for both code and tests.
- **JDK gaps**
    - If jdk specific class is used in java lucene and not found in kotlin std lib, try to find `org.gnit.lucenekmp.jdkport.*` for JDK replacements (e.g., `BitSet`, `Character`, `Inet4Address`) and use it.
    - If a needed JDK API or method is missing, add it under `jdkport` (same type name); add unit tests.
    - For extension shims, follow `*Ext.kt` naming (e.g., `DoubleExt.kt`).
- **Unit test parity**
    - Tests are **ports**, not new tests. Keep method names, signatures, and variable names **exactly** as Java tests.
    - Replace Java assertions with Kotlin test assertions. Do not invent new expectations.
    - If testing a subclass whose superclass provides behavior, add tests for any non-overridden superclass methods.
- **Logging**
    - Use KotlinLogging:  
      `import io.github.oshai.kotlinlogging.KotlinLogging`  
      `private val logger = KotlinLogging.logger {}`  
      `logger.debug { "message" }`
