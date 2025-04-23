I'm porting apache lucene from java to platform agnostic kotlin common code.
The code needs to be generated strictly avoiding any java specific code.
Use only kotlin common code and libraries for multiplatform development.

In case lucene depends on any class or interface which is specific to jdk and there are no equivalent ones found in kotlin standard library or 3rd party libraries, I ported them from jdk to kotlin common code in the package  org.gnit.lucenekmp.jdkport.* so You needs to use them if you find any such class or interface.

In case a equivalent class or interface exists but equivalent function was not found, I created kotlin extension function for the missing function in the package org.gnit.lucenekmp.jdkport.* so you need to use them if you find any such function. 
