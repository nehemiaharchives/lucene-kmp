# instruction
* port TestXXX.java to TestXXX.kt as platform agnostic kotlin common code.
* do not use expect/actual pattern
* create test class under core/src/commonTest/kotlin/ dir. do not put under other targets such as core/src/jvmTest because this project is kotlin common.
* During porting progress when you realize some small (less than 1000 lines of code) dependency classes are used in Java lucene but not yet ported in kotlin common lucene-kmp, port them first.
* if the dependencies are larger than 1000 lines of code, mark the testFunction() @Ignore with empty test implementation contains //TODO implement after FooDependency class is ported

# dependencies which is not yet ported from java to kotlin common
* org.gnit.lucenekmp.package.name.Foo.kt
* org.gnit.lucenekmp.package.name.Bar.kt
* org.gnit.lucenekmp.package.name.Baz.kt

# java test methos porting progress to kotiln common test functions
[x] testFoo()
[] testBar()
[] testBaz()
