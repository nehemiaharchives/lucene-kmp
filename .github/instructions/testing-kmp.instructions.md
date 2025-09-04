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

# writing code, then compile then run tests
1. First write the code, then run `./gradlew compileKotlinJvm` and `./gradlew compileTestKotlinJvm` to check if there are compilation errors.
2. If you encounter compilation errors, find out the cause of the error, edit the code to fix the error, then repeat step 1.
3. If there is no compilation error, run unit tests, but run specific test class which you just ported or modified, not all tests.
4. If the specific test fails, find out the cause of the failure, edit the code to fix the error, then repeat step 3.
5. If the specific test passes, run `./gradlew jvmTest` to run all jvm tests.
6. If all jvm tests pass, run `./gradlew allTests` to run all tests for all platforms.
