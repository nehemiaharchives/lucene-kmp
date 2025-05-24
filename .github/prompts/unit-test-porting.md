# util package tests

## overall project description
I'm porting apache lucene from java to platform agnostic kotlin common to make multiplatform library which suppports Android, iOS, and JVM(server).

## java package structure
The root package for port source of lucene core is this: lucene/lucene/core/src/java/org/apache/lucene/
And it contains sub packages such as analysis, codecs, document, index, internal, search, store, util
The root package for port soruce of lucene core unit test is this: lucene/lucene/core/src/test/org/apache/lucene/
And it contains sub packages for unit test such as analysis, codecs, document, index, internal, search, store, util

## kmp package structure
The root package for port destination of lucene core is this: lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/
And it contains sub packages such as analysis, codecs, document, index, internal, search, store, util, jdkport
The root package for port destination of lucene core unit test is this: lucene-kmp/core/src/commonTest/kotlin/org/gnit/lucenekmp/
And it contains sub packages for unit test such as analysis, codecs, document, index, internal, search, store, util, jdkport

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

## how far this project has done so far
So far, all the JDK classes are ported and all of them has unit tests. Unit tests for classes in jdkport package are made alphabetical order starting form ArrayDequeExt.kt, then ArraySupport.kt ... etc. I skip creating tests for interfaces and abstract classes.

I started to create unit tests for jdkport classes because they are more fundamental building block to run lucene. For the lucene ported classes I would like to start port unit tests from java and test exact same things in kotlin common. I will start porting form unit tests in "util" sub package because they are secondaly fundamental building block to run lucene.

This is the background of the project and current progress which is related to my following request.


## the task
This time, my request is this: generate platform agnostic kotlin common port of following Java Unit Test class:

lucene/lucene/core/src/test/org/apache/lucene/util/[the class name to be tested].java

in following dir:

lucene-kmp/core/src/commonTest/kotlin/org/gnit/lucenekmp/util/

When you create tests for functions of a class, if the class is subclass of abstract class and if you find any functions are not overidden by the sub class, create test for the function of the super class.

Unit tests should be ported, not created newly. Keep the test function/method name as it is. Keep the method signature, valuable names used in the unit test as it is. Just replace java assertions to kotlin test assertions. Do not create test which is not in the java lucene unit tests.

Create unit tests following above requirement, run the test using shell script named gradle_output.sh. Use it like gradle_output.sh allTests. wait for the test execution, then read the build_output.txt to check if it pass or fail, or has compilation error. test command if test fails, review both implementation of the tested function and the test code. Verify tested function is correctly implemented. Verify if the test assertion is correctly expecting the outcome of the unit test.

## never forget this is kotlin common
Always keep in mind that this is kotlin common project and never use jdk specific feature in the both tested and test code when you make change. The code also needs to work in iOS. Avoid expect/actual pattern to implement things but implement all of them by using just kotlin standard library for common target.

## usage of jdkport classes while porting java unit test to kotlin common unit test
While you port unit tests, you might encounter a case that the lucene unit test calls some jdk class which does not have equivalent in kotlin std lib. For example, when java.lang.System.arraycopy() is used in java lucene's unit test, try to find if there is already org.gnit.lucenekmp.System.arraycopy() with same method signature. If you find it use it. If you do not find the class in jdkport sub package, port it from jdk to kotlin common. If you find the class in jdkport but did not find exact same function/method, port the function/method and add it in the found class. When you port missing jdk class or function create also unit tests.

## logging
when logging, use the following code:
* import io.github.oshai.kotlinlogging.KotlinLogging
* private val logger = KotlinLogging.logger {}
* logger.debug { "message" }

ok, then start implementing unit test and iterate over adjusting codes until all the tests pass.
