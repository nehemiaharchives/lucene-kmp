---
applyTo:
  - "lucene/**"
  - "lucene-kmp/**"
description: "Source/test layout for Lucene → KMP"
---

# current project location

we are in lucene-kmp/ which is a sibling of lucene/ 

# Source/Test Layout

- Java Lucene (source): `lucene/lucene/core/src/java/org/apache/lucene/`
- Java Lucene (tests):  `lucene/lucene/core/src/test/org/apache/lucene/`
- KMP target (source):  `lucene-kmp/core/src/commonMain/kotlin/org/gnit/lucenekmp/`
- KMP target (tests):   `lucene-kmp/core/src/commonTest/kotlin/org/gnit/lucenekmp/`
- KMP subpackages include `jdkport` for JDK replacements.

# Naming
- Example (code):  `org.apache.lucene.document.BinaryDocValuesField`
  → `org.gnit.lucenekmp.document.BinaryDocValuesField`
- Example (test):  `org.apache.lucene.util.TestUnicodeUtil`
  → `org.gnit.lucenekmp.util.TestUnicodeUtil`
