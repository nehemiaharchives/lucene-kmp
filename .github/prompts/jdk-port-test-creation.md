---
mode: 'agent'
tools: ['changes', 'codebase', 'vscodeAPI', 'usages', 'terminalLastCommand', 'terminalSelection', 'searchResults', 'fetch', 'findTestFiles', 'problems', 'extensions'] 
---

# jdk tests

I'm porting apache lucene from java to platform agnostic kotlin common to make multiplatform library which suppports Android, iOS, and JVM(server).

During the porting process there was JDK classes which is used in Java lucene but have no equivalent pair in kotlin standard library (standard library of kotlin common). To smooth the porting process I decided to port also those JDK classes into package org.gnit.lucenekmp.jdkport.

There are some edge case such as Double. jdk and kotlin standard lib both has Double class but the some jdk Double functions are not found in kotlin ones. In such case, I created a kotlin file contains extension functions to fill the functionality pair gap. The file name convention for extension function is for example "for Double class, kt file is named DoubleExt.kt"

So far, some JDK classes are ported and some of them has unit tests. Unit tests for classes in jdkport package are made alphabetical order starting form ArrayDequeExt.kt, then ArraySupport.kt ... etc. I skip creating tests for interfaces and abstract classes.

Port of Classloader.java and ServiceLoader.java is does as skeleton with no operation functions because I still did not decide how to walk around this JVM specific feature in kotlin common. So creating unit tests for Classloader and ServiceLoader is also skipped for now.

This is the background of the project and current progress which is related to my following request.

This time, my request is this: create Unit tests for following classes:

core/src/commonMain/kotlin/org/gnit/lucenekmp/jdkport/[the class name].kt

When you create tests for functions of a class, if the class is subclass of abstract class and if you find any functions are not overidden by the sub class, create test for the function of the super class.

create test files in core/src/commonTest/kotlin/org/gnit/lucenekmp/jdkport

Test class/file name convention is for example, for System.kt, SystemTest.kt. And for IntExt.kt file, IntExtTest.kt.

Create unit tests following above requirement, run the test by ./gradlew allTest command if test fails, review both implementation of the tested function and the test code. Verify tested function is correctly implemented. Verify if the test assertion is correctly expecting the outcome of the unit test. 

JDK ported classes are ported by copy and pasting in intelij idea with automatic java to kotlin conversion feature. So in most cases, logic is directly mapped from jdk and does not need to be changed. So when test fails, do not change the fundamental implementation logic but try to find mistakes caused by java to kotlin conversion. Always keep in mind that this is kotlin common project and never use jdk specific feature in the both tested and test code when you make change. The code also needs to work in iOS. Avoid expect/actual pattern to implement things but implement all of them by using just kotlin standard library for common target.

ok, then start implementing unit test and iterate over adjusting codes until all the tests pass.
