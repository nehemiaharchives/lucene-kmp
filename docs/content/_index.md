---
title: "lucene-kmp"
description: "Kotlin Multiplatform port of Apache Lucene APIs."
lead: "A Kotlin Multiplatform port of Apache Lucene focused on preserving Lucene behavior in common Kotlin code."
date: 2026-06-23T00:00:00+09:00
lastmod: 2026-06-23T00:00:00+09:00
draft: false
params:
  seo:
    title: "lucene-kmp"
    description: "Kotlin Multiplatform port of Apache Lucene APIs."
    canonical: ""
    robots: ""
---

lucene-kmp exists to make Lucene-style indexing, analysis, and search APIs available from Kotlin Multiplatform projects.

The port targets JVM, Android, iOS through Kotlin/Native, and native-friendly shared code where platform behavior can stay compatible with upstream Lucene.

This is active porting work. API compatibility with Apache Lucene is a goal, but the project is not an official Apache Lucene distribution and compatibility is not guaranteed yet.

```bash
./gradlew build
```

```bash
./gradlew :dokkaGenerate
```
