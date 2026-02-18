---
name: port
description: Port a class/interface from java lucene into platform agnostic kotlin common lucene-kmp codebase
---

## overall project description
I'm porting Apache Lucene from Java to platform agnostic kotlin common to make multiplatform library `lucene-kmp` which supports Kotlin/Android, Kotlin/Native(iOS, macOS, Linux), and Kotlin/JVM(desktop and server).

## java package structure
For example, in `core` module, the root package is: `lucene/lucene/core/src/java/org/apache/lucene/`
`core` module's unit tests are in root package: `lucene/lucene/core/src/test/org/apache/lucene/`
And those root contains sub packages such as analysis, codecs, document, index, internal, search, store, util and their sub packages.

## kmp package structure
For example, in `core` module, the root package for port destination is: `lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/`
lucene-kmp `core` module for unit tests are in the root package: `lucene-kmp/core/src/commonTest/kotlin/org/gnit/lucenekmp/`
And it contains sub packages such as analysis, codecs, document, index, internal, search, store, util, and especially the one which is not found in java lucene: jdkport

## package name convention on porting process
For classes/interfaces in sub packages which is made by lucene, which is "analysis, codecs, document, index, internal, search, store, util", we will respect exact same java lucene's sub packagename for both "code" and "unit test".

For example,
in case of code, org.apache.lucene.document.BinaryDocValuesField has been ported to org.gnit.lucenekmp.document.BinaryDocValuesField
in case of unit test, org.apache.lucenep.util.TestUnicodeUtil has been ported to org.gnit.lucenekmp.util.TestUnicodeUtil

## about jdkport package
During the porting process there was JDK classes which is used in Java lucene but have no equivalent pair in kotlin standard library (standard library of kotlin common). To smooth the porting process I decided to port also those JDK classes into "package org.gnit.lucenekmp.jdkport" which is located in lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/jdkport

There are some edge case such as Double. jdk and kotlin standard lib both has Double class but the some jdk Double functions are not found in kotlin ones. In such case, I created a kotlin file contains extension functions to fill the functionality pair gap. The file name convention for extension function is for example "for Double class, kt file is named DoubleExt.kt"

So for classes/intefaces in jdkport sub packages, we will NOT respect jdk package names but all JDK classes/interfaces will be ported to jdkport sub package. The name of the classes/interfaces will be kept exact same.

For example,
java.util.BitSet which is used in lucene java but no equivalent exsits in kotlin std lib will be ported to org.gnit.lucenekmp.jdkport.BitSet
java.lang.Character has been ported to org.gnit.lucenekmp.jdkport.Character
java.net.Inet4Address has been ported to org.gnit.lucenekmp.jdkport.Inet4Address

naming convention for unit tests for those classes will be ClassNameTest, which is for example:
org.gnit.lucenekmp.jdkport.CharacterTest and org.gnit.lucenekmp.jdkport.Inet4AddressTest

Port of Classloader.java and ServiceLoader.java is does as skeleton with no operation functions because I still did not decide how to walk around this JVM specific feature in kotlin common. So creating unit tests for Classloader and ServiceLoader is also skipped for now.

And because jdk package structure will be lost, for only jdk port class/interface, we add `@Ported` annotation to the class like `@Ported(from = "java.lang.Character")` 

## Steps to Port

Step 1. Create the exact same of the class/interface by replacing .java to .kt in the target package. e.g. `ClazzToPort.java` to `ClazzToPort.kt`
Step 2. First copy import statements which starts with `org.apache.lucene` by replacing it with `org.gnit.lucenekmp` do not port jdk import statements at this time.
    Step 2.1: create empty class with LuceneTestCase inheritance and import statements with correct org.gnit.lucenekmp packagess
    Step 2.2: Create inner classes with proper inheritance and property members
    Step 2.3: Add property members and no-op functions in inner class and main class
    Step 2.4: Port and fill implementations of each no-op functions and try to follow as much java lucene behavior possible, run `get_file_problems` tool of jetbrains mcp server for each time after porting a function and fix error
Step 3. run `get_file_problems` tool of jetbrains mcp server to the file. unresolved reference compilation error means un-ported-dependency class/interface, leave current class as it is and start over from Step 1 for that class.
Step 4. After import-statements-only class/interface file `ClazzToPort.kt` get no compilation error with `get_file_problems`, start porting the body of the code.
Step 5. During porting body of the class, if you find any jdk specific class used, try to find it in `jdkport` package and if you find import it and use it.
Step 6. If you did not find `jdkport` class/interface, do one of the following:
    Case 1 -> Functional interface: interfaces in `java.util.function` should be replaced with kotlin function representation, e.g. `Predicate<T>` to be `(T) -> Boolean`, `IntFunction<R>` to be `(Int) -> R` 
    Case 2 -> Collection operation such as `.stream()` or `map` should be replaced with idiomatic kotlin equivalents. array creation should be done with `Array(size){ i-> entry }` style.
    Case 3 -> `java.lang.Thread` related classes are mostly ported to `jdkport` so use it but if not found port it using kotlin coroutine `Job` and related coroutine building block mimicking the behavior as much as possible. 
    Case 4 -> Large but mostly static method utility jdk classes such as `java.lang.Math` are ported partially. port only specific missing static method by adding that specific kotlin function to existing `jdkport` class.
    Case 5 -> Complex reflections or java.security or other jdk specific things which can not be translated into KMP common code should be bypassed by creating empty no-op class and functions throwing UnsupportedOperationException()
    Case 6 -> ClassLoader related logic, should be replaced with hard coded class names and constructors. see examples of existing already ported `NamedSPILoader.java` and `NamedSPILoader.kt`
    Case else -> go to Step 7 which is porting new jdk port
Step 7. Create a empty kotlin class/interface `jdkport` and try to find if all the dependencies or super class, super interface is already in `jdkport` package, if not found, port super class/interface recursively.  
Step 8. If all the super class/interface of the jdk class ported into `jdkport` package, start porting the jdkport class.
Step 9. If there are no missing `jdkport` class, and if there are no un-ported-dependency class/interface from Java Lucene, port the code of the `ClazzToPort.kt`
Step 10. After porting logic and behaviors code, run `get_file_problems` tool of jetbrains mcp server and edit and iterate over until all errors resolves.
Step 11. Final parity pass: compare Java and Kotlin files side-by-side. Check for missing or misordered member properties, functions, and logic blocks. Add missing pieces and reorder Kotlin members/functions/logic to match Java ordering so both files stay side-by-side with full behavior and implementation parity.

## Non-negotiable defaults (do this without being asked)

1. **Side-by-side parity is default**
   - Keep Java control flow shape, local variable order, comments, and key blocks in the same order as upstream Java.
   - For tests, keep parity for important inline blocks (e.g. custom scheduler object blocks, custom `InfoStream` blocks, latch/barrier choreography, and merge/thread coordination).
   - If a Java block cannot be copied 1:1 due to KMP constraints, keep the structure and intent, then add the smallest possible KMP-safe replacement.

2. **KMP common safety is default**
   - In `commonMain/commonTest`, do **not** use JVM-only threading APIs or `kotlin.concurrent.thread`.
   - Replace Java/JVM thread usage with Kotlin coroutines (`Job`, `CoroutineScope`, `launch`, `join`) or `jdkport` concurrency primitives when they already exist.
   - For lock/coordination behavior, prefer existing `jdkport` constructs (e.g. `ReentrantLock`, `CountDownLatch`) where parity benefits from them.

3. **Compile-fix loop is mandatory**
   - After each substantial edit chunk (or after each ported function for long files), run JetBrains `get_file_problems`.
   - Fix all **errors** immediately before continuing.
   - Keep iterating until there are zero errors in edited files.
   - Warnings can remain unless they indicate a parity/correctness issue.

4. **Kotlin API correctness while preserving parity**
   - Keep Java intent but use Kotlin-correct API shapes:
     - anonymous subclass creation uses `object : Type(...) { ... }`
     - `assertEquals(expected, actual, message)` ordering for Kotlin test APIs
   - Do not keep Java call signatures when they are invalid in Kotlin.

5. **No prompt repetition policy**
   - Assume the user always wants the above parity + KMP-safe behavior unless explicitly overridden.
   - Do not wait for reminders like “keep side by side”, “run get_file_problems”, or “replace thread usage in common”.

## Code Style of Port
Style 1. Ease of side by side comparison: The property names, var/val names, method/function names, inner class names, all the names should be exactly same and appear in exact order as java lucene counter part so that those who read the class can easily compare the logic and behavior of the code when porting and debugging. However, there is exceptions on getters and setters: java's ordinal getters and setters should be replaced with get() and set() of kotlin public val/var to avoid compilation error e.g. ```Platform declaration clash: The following declarations have the same JVM signature (getBasicModel()Lorg/gnit/lucenekmp/search/similarities/BasicModel;):
    fun `<get-basicModel>`(): BasicModel defined in org.gnit.lucenekmp.search.similarities.DFRSimilarity
    fun getBasicModel(): BasicModel defined in org.gnit.lucenekmp.search.similarities.DFRSimilarity``` in such case, do not port `getBasicModel()` but make `public val basicModel` publicly accessible. 
Style 2. Do not add function/property/value/class/interface which is not in java lucene. This will make it difficult to compare the logic and behavior.
Style 3. Comment: port also the comments. Javadoc need to be translated to Kdoc. Block comments and slash comments need to be ported exactly same order as it appear in java lucene code. 
Style 4. Companion object: java's static methods or static classes needs to be ported into kotlin's `companion object {}` and breaks the side by side style rule 1 but allowed.
Style 5. Nullability: Get advantage of the kotlin language of null handling as much as possible by trying to use none-nullable var, val as much as possible. if a java lucene specifically inserts `null` to the val/var, then it can be nullable to mimic the behavior. If the value is kotlin-none-nullable, `Objects.requireNonNull()` or `assert(someVariable != null)` can be omitted but if it is nullable in kotlin, use top level function `requireNotNull()`.
Style 6. Use idiomatic kotlin: Get advantage of the kotlin language to write simple, concise, smart, easy to understand code while following the exact same logic and behavior while porting.
    Style 6.1. String concatenation using + operator in java need to be replaced with kotlin string interpolation. e.g. `"DFR " + basicModel.toString() + afterEffect.toString() + normalization.toString();` to be ported into `"DFR $basicModel$afterEffect$normalization"`
    Style 6.2. Two comparisons should be converted to a range check: e.g. `assert docID >= 0 && docID < maxDoc;` to be `assert(docID in 0..<maxDoc)` 
Style 7. Avoid numeric value mismatch: in Java Byte and Int or Long and Double is implicitly widened, but in Kotlin they are not; use explicit toX conversions or parentheses to preserve numeric type and operator behavior.
Style 8. Junit Test method/function inheritance: in unit tests defined in Java Junit super classes are executed in sub test classes, but in Kotlin unit testing using kotlin.test, super class unit test functions needs to be explicitly inherited in sub classes to be executed. So find all `fun testXXX()` methods in super test class and add `@Test` annotation and define inherited class `override fun testXXX() = super.testXXX()` where XXX is the name of the test function.
Style 8.1. For abstract/base test classes, do **not** put `@Test` on superclass test methods. Keep superclass methods as `open`, and in each concrete subclass add `@Test override fun testXXX() = super.testXXX()` for every inherited test that must run.
Style 8.2. Place inherited test overrides at the **bottom** of the concrete test class, grouped by superclass with comments. Example:
`// tests inherited from BaseSuperClassX`
then contiguous `@Test override fun test...() = super.test...()` entries for that superclass.
Style 9. Atomics: Add `@OptIn(ExperimentalAtomicApi::class)` to var/val/function when you use classes in `kotlin.concurrent.atomics`
    Style 9.1 `fun incrementAndGet()`: Int is deprecated. Use `incrementAndFetch()` instead.
    Style 9.2 do not use `fun get()`, use `fun load()` instead
    Style 9.3 do not use `fun addAndGet()`, use `fun addAndFetch()` instead
Style 10. assert keyword in java need to be replaced with `assert()` function with `import org.gnit.lucenekmp.jdkport.assert`
Style 11. In `commonMain/commonTest`, any Java `Thread`-style test or production logic must be ported with coroutine/job semantics or existing `jdkport` synchronization tools; never introduce JVM-only thread helpers.
