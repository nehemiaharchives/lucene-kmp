## Project Overview
I'm porting apache lucene from java to platform agnostic kotlin common code.
This project is a sub directory under the the root directory of the porting project and the directory name is lp.
Under this directory you find two sub directories:

1. lucene sub directory
   This is the source code of java lucene. the commit id is fixed to ec75fcad5a4208c7b9e35e870229d9b703cda8f3 until all java classes/unit tests ported. After all ported, the project will proceed to the next phase to port commit by commit from this commit id to catch up with the latest lucene code. The code will be ported from this read-only repository.

2. lucene-kmp (kmp stands for kotlin multiplatform) sub directory, which is THIS project

## Porting Guideline

- The GitHub copilot never make any change to lucene java source code but only read, copy from, analyze and answer question based on its content. If it's in agent mode, it can use git commands which does not change any code but only read the code and history.

- When porting, class name, interface name, method name, variable name should be the same as much as possible especially for APIs which is used by other classes.

- root package name of java lucene is org.apache.lucene. The root package name of the kotlin common code is org.gnit.lucene-kmp. The sub package structure under the root package should be the same as much as possible.

- When porting, when the certain java class included in JDK (e.g. java.util.List, java.util.Map, java.lang.String, etc) is used in lucene java code, you should use kotlin common code equivalent class (e.g. kotlin.collections.List, kotlin.collections.Map, kotlin.String, etc) instead of the JDK class. If those equivalent class is not found in kotlin common code of kotlin standard library, this project will copy the source code of the JDK class which is missing in kotlin std lib and port it into kotlin common code in a package called org.gnit.lucene-kmp.jdkport. These jdk ported classes/interface need to have annotation called @Ported with argument called from like this: @Ported(from="java.util.List") So when porting, if you encounter compilation error saying unresolved reference to certain JDK class/interface, you should first look into the package org.gnit.lucene-kmp.jdkport to see if the ported class/interface is already there. Only when if not found, it should be ported form JDK source code. Most of the missing JDK classes are already in the package.

- The ported project is targeting following platforms:
    1. jvm (jvm server, jvm desktop, Android)
    2. native (iOS, linux)

- The linux native target is not for projection but for development in linux desktop environment. The kotlin native uses LLVM as its compiler and gradle kotlin compile job emits almost exact same compilation error for native/linux and native/iOS. So even in the linux box which does not have build toolchain for iOS, you can quickly check if the ported code compiles for iOS or not by compiling for native/linux target.

- The porting project prioritizes kotlin common code over expect/actual mechanism to reduce the amount of platform specific code. So when porting, first try to use kotlin common code only. Only when if not possible, you should use expect/actual mechanism. When using expect/actual mechanism, the platform specific code should be created in following 2 source sets:
    1. jvmAndroidMain, jvmAndroidTest for jvm/android platform
    2. nativeMain, nativeTest for native platform (iOS, linux)

## Specific Instruction
* do not use String.toByteArray() but use String.encodeToByteArray() instead.
* because this project is kotlin common project, in common code, do not use String.format function but use kotlin string interpolation.

## Logging
* when logging, use the following code:
* import io.github.oshai.kotlinlogging.KotlinLogging
* private val logger = KotlinLogging.logger {}
* logger.debug { "message" }

## Running Unit Tests

### Priority 1, Intelij IDEA MCP Server
When you have access to Intelij IDEA MCP server, you should use the IDEA's internal test runner.

### Priority 2, Gradle command line
When you don't have access to Intelij IDEA MCP server, you should use the command line Gradle test runner.

## Additional Unit Test Porting Rules

- **Parity with Java tests**
    - All Lucene unit tests should be *ported*, not newly created. Keep method names, signatures, and local variable names **exactly** the same as the original Java tests.
    - Replace Java assertions (`assertTrue`, `assertEquals`, etc.) with the equivalent Kotlin test assertions.
    - Do not invent new tests or expectations beyond what exists in the Java source.

- **Package mapping**
    - For Lucene packages (`analysis, codecs, document, index, internal, search, store, util`), map:
        - `org.apache.lucene.*` → `org.gnit.lucenekmp.*` (for both code and tests).
    - For JDK classes missing from Kotlin stdlib, always check `org.gnit.lucenekmp.jdkport.*` first. If absent, port the class/method and add unit tests there.

- **Superclass methods**
    - If the class under test is a subclass of an abstract or base class and the subclass does not override some methods, create tests for those inherited methods as well.

- **Naming convention for jdkport tests**
    - For each ported JDK class, create a test named `ClassNameTest.kt`. Example:
        - `org.gnit.lucenekmp.jdkport.CharacterTest`
        - `org.gnit.lucenekmp.jdkport.Inet4AddressTest`

- **Test ordering / coverage**
    - JDK ported classes have tests created in alphabetical order. Interfaces and abstract classes can be skipped for direct tests.

- **Examples**
    - Java: `org.apache.lucene.util.TestUnicodeUtil`  
      Kotlin: `org.gnit.lucenekmp.util.TestUnicodeUtil`
