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

## Code Style of Port
Style 1. Ease of side by side comparison: The property names, var/val names, method/function names, inner class names, all the names should be exactly same and appear in exact order as java lucene counter part so that those who read the class can easily compare the logic and behavior of the code when porting and debugging.  
Style 2. Do not add function/property/value/class/interface which is not in java lucene. This will make it difficult to compare the logic and behavior.
Style 3. getters and setters: java's ordinal getters and setters should be replaced with get() and set() of kotlin val/var to avoid compilation error. 
Style 4. Comment: port also the comments. Javadoc need to be translated to Kdoc. Block comments and slash comments need to be ported exactly same order as it appear in java lucene code. 
Style 5. Companion object: java's static methods or static classes needs to be ported into kotlin's `companion object {}` and breaks the side by side style rule 1 but allowed.
Style 6. Nullability: Get advantage of the kotlin language of null handling as much as possible by trying to use none-nullable var, val as much as possible. if a java lucene specifically inserts `null` to the val/var, then it can be nullable to mimic the behavior. 
Style 7. Use idiomatic kotlin: Get advantage of the kotlin language to write simple, concise, smart, easy to understand code while following the exact same logic and behavior while porting.
