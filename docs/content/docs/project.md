---
title: "Project"
description: "What lucene-kmp is and how this site is organized."
summary: ""
date: 2026-06-23T00:00:00+09:00
lastmod: 2026-06-23T00:00:00+09:00
draft: false
weight: 30
toc: true
params:
  seo:
    title: "Project"
    description: "What lucene-kmp is and how this site is organized."
    canonical: ""
    robots: ""
---

lucene-kmp is a Kotlin Multiplatform port of Apache Lucene APIs.

The implementation favors common Kotlin code and close behavioral parity with upstream Lucene. Platform-specific code is kept narrow where Kotlin Multiplatform requires it.

Supported and intended targets include JVM, Android, iOS through Kotlin/Native, and other native-friendly shared code targets where the port can preserve Lucene behavior.

The project is experimental and under active porting. API compatibility with Apache Lucene is a goal, but compatibility is not guaranteed yet.

This website is built from `docs/`. Generated Dokka API documentation is copied into `docs/api/` locally and into `docs/public/api/` for GitHub Pages deployment.
