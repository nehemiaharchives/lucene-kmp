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

- If the target java class to port into kotlin common is too large for your context size, eg. more than 500 LOC, or more than 50 unit test functions, try porting again baby step strategy. For example, create a class with empty functions with // TODO comments, or when you port unit test class, first create empty test class with no-op tests with //TODO comments as skeleton. Then one by one implement the //TODO of each functions.

- What should be ported next can be detemined by the output file of `progress.main.kts` script named `PROGERSS.md`. The md file is automatically geneted by the script and should be only be updated by the script. The script shows the Unit test classes which are not yet ported yet. Also, in the same way, `progressv2.main.kts` generates `PROGRESS2.md` which comes with more detailed porting progress info which includes number of methods and dependency depth. The higher the depth the sooner the porting must be done. For example, depth 7 dependency class should be ported first because those are not using other class, but other classes are using that class. After porting of depth 7 dependency classes finished, porting of depth 6 classes can start, then depth 5, 4, 3, 2, and 1. However, if I ask you to port specific class, port that class and its dependency classes.

- I asked AI agent to create `TODO.md` and `TODO_TEST.md` which is simple TODO lists of what to port next. You follow this TODO list from top to bottom. First, we will port from the `TODO_TEST.md` Test* classes with its couneter part marked as (Ported) from top to bottom. Because classes of priority 1 API and its dependenceis are already ported, and tests which verify them is needed. When you finished porting a Test class, delete the entry in `TODO_TEST.md`.

## Specific Instruction
* do not use String.toByteArray() but use String.encodeToByteArray() instead.
* because this project is kotlin common project, in common code, do not use String.format function but use kotlin string interpolation.
* @Ignore annotation in the test will not take any arguments in kmp.

## Logging
* when logging, use the following code:
* import io.github.oshai.kotlinlogging.KotlinLogging
* private val logger = KotlinLogging.logger {}
* logger.debug { "message" }
* 
## Agent‑Human Coworking Flow

### Step 1: Suggest & Discuss

* Propose multiple solutions using your built‑in knowledge.
* Only perform external research if:
    * You are uncertain about an API or behavior, **or**
    * I explicitly request official documentation or references.
* Otherwise, skip research and move straight to proposing fixes.

### Step 2: Code, Run, Debug
1. Apply the chosen code changes. when you code be careful of writing code in platform agnostic kotlin common code. avoid expect/actual pattern as much as possible. do not mix platform specific code such as jvm code in commonMain/commonTest.
2. Use `get_file_problems` tool of `jetbrains` MCP server to check if there are any compilation errors for each files you changed. iterate over until you solve all errors for Kotiln/JVM.
3. Use `get_run_configuration` tool `jetbrains` MCP server to find proper run configuration. to run either [`compileKotlinLinuxX64` and `compileTestKotlinLiuxX64` on linux] or [`compileKotlinMacosX64` and `compileTestKotlinMacosX64` on macOS], to check if there are any compilation errors for each files you changed especially for Kotlin/Native. iterate over until you solve all errors.
4. Use `get_run_configuration` tool `jetbrains` MCP server to find proper run configuration. to run specific unit test and use execute_run_configuration to run tests. if any test fail, find out root cause, iterate over until you fix all of them. tests should pass both in `jvmTest` and tests for native env which is either `linuxX64Test` or `macosX64Test` depending on your work env.
5. Perform internet search **only** if an error is unclear and you need confirmation of a fix. If you are confident in the solution, skip research and proceed.
6. lucene is very complex software. if you are not sure where is the bug, use `val logger = KotlinLogging.logger{}` if not found, add it, and `logger.debug { "things you want to see: $xxxx" }` to output debug log in the
   suspicious lines of code and make sure. then run the tests to see the debug log. then rethink the next suspicious code to track down the root cause of the bug.

## Tool Use Priority

### Priority 1, jetbrains MCP Server (always)
When you have access to jetbrains MCP server, you should use the IDEA's internal test runner. `.run` dir contains.
Example agent runtime environment: locally running ai coding agent in desktop/laptop of a developer such as codex cli, GitHub Copilot Agent.

### Priority 2, Gradle command line (avoid as much as possible)
When you don't have access to jetbrains MCP server, first ask Human developer to enable it and wait until it is enabled! Never use Gradle wrapper (./gradlew).
If you are in cloud environment where you have NO access to jetbrains MCP server, you are allowed to use the command line Gradle wrapper (./gradlew) to compile and run tests.
Example agent runtime environment: desktop/laptop but human developer forgot to launch JetBrains IDEs, or cloud coding agent such as codex web, Google Jules.
When you use ./gradlew command line tool, use like following (e.g. when you develop core module):
1. First write the code, then run `./gradlew core:compileKotlinJvm` and `./gradlew core:compileTestKotlinJvm` to check if there are compilation errors on Kotlin/JVM.
2. If you encounter compilation errors, find out the cause of the error, edit the code to fix the error, then repeat step 1.
3. Then, run if you are on linux run `./gradlew core:compileKotlinLinuxX64` and `./gradlew core:compileTestKotlinLiuxX64` or if you are on macOS run `./gradlew core:compileKotlinMacosX64` and `./gradlew core:compileTestKotlinMacosX64` to check if there are compilation errors on Kotlin/Native. 
4. If you encounter compilation errors, find out the cause of the error, edit the code to fix the error, then repeat step 3.
5. If there is no compilation error with jvm compile, then run `./gradlew core:compileKotlinLinuxX64` and `./gradlew core:compileTestKotlinLinuxX64` which is kotlin/native that covers both linux and ios
6. If there is no compilation error with both kotlni/jvm and kotlin/native, run unit tests, but run specific test class which you just ported or modified, not all tests.
7. If the specific test fails, find out the cause of the failure, edit the code to fix the error, then repeat step 3.
8. If the specific test passes, run `./gradlew jvmTest` to run all jvm tests.
9. If all jvm tests pass, run `./gradlew allTests` to run all tests for all platforms.


### troubleshooting java/cacerts problem
When you try to run `./gradldew` and get `Trust store file /etc/ssl/certs/java/cacerts does not exist or is not readable. This may lead to SSL connection failures.`, try to run following command to solve it:

```
keytool -importcert -noprompt -trustcacerts \
        -alias isrgrootx1 \
        -file /usr/share/ca-certificates/mozilla/ISRG_Root_X1.crt \
        -keystore "$JAVA_HOME/lib/security/cacerts" \
        -storepass changeit
```

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
