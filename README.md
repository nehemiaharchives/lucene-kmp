# Lucene Kotlin Multiplatform

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-blue.svg?style=flat&logo=kotlin)](https://kotlinlang.org)
[![Maven Central](https://img.shields.io/maven-central/v/org.gnit.lucene-kmp/lucene-kmp-core-jvm)](https://central.sonatype.com/search?namespace=org.gnit.lucene-kmp)
[![CI](https://github.com/nehemiaharchives/lucene-kmp/actions/workflows/ci.yml/badge.svg)](https://github.com/nehemiaharchives/lucene-kmp/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/nehemiaharchives/lucene-kmp/graph/badge.svg?token=YRN8URPQA4)](https://codecov.io/gh/nehemiaharchives/lucene-kmp)
[![Apache 2 License](https://img.shields.io/github/license/nehemiaharchives/lucene-kmp)](https://github.com/nehemiaharchives/lucene-kmp/blob/master/LICENSE)

## Overview

lucene-kmp is a [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) port of [Apache Lucene](https://github.com/apache/lucene/) enabling On-device indexing and full text search capabilities in KMP/CMP Android, iOS and desktop apps.

## Supported platforms
* Kotlin/Jvm
* Kotlin/Android
* Kotlin/Native iOS (iosArm64, iosSimulatorArm64, iosX64)
* Kotlin/Native macOS (macosArm64, macosX64)
* Kotlin/Native Linux (linuxX64)
* Kotlin/Native Windows (mingwX64)

## Features
* **On-device full-text search**: build and query local indexes without a backend service, useful for offline-first mobile/desktop apps and private user data.
* **Relevance-ranked results**: return the best matches first instead of only exact substring matches.
* **Analyzer-based tokenization**: normalize text with analyzers for case folding, tokenization, stop words, stemming, and language-specific search behavior.
* **Rich query support**: support common search features such as phrases, boolean queries, wildcard queries, range queries, and query parsing.
* **Fielded documents**: index structured records with separate searchable and stored fields, such as title, body, author, tags, date, or category.
* **Filtering, sorting, and pagination**: combine text relevance with structured filters and stable result ordering for app search screens.
* **Incremental indexing**: add, update, and delete documents as local app data changes instead of rebuilding the whole index.
* **Shared KMP search code**: keep indexing and search logic in common Kotlin code and use it from Android, iOS, desktop, and native targets.

## Getting started

lucene-kmp is published to Maven Central. Add the `core` and `queryparser` module to your `build.gradle.kts`:

```kotlin
implementation("org.gnit.lucene-kmp:lucene-kmp-core:10.2.0-alpha13")
implementation("org.gnit.lucene-kmp:lucene-kmp-queryparser:10.2.0-alpha13")
```

Build a small index and search it (imports are omitted; class names live under `org.gnit.lucenekmp.*`):

```kotlin
val directory = FSDirectory.open(indexPath)
val analyzer = StandardAnalyzer()

IndexWriter(directory, IndexWriterConfig(analyzer)).use { writer ->
    val doc = Document().apply {
        add(StringField("id", "note-1", Field.Store.YES))
        add(TextField("body", "lucene-kmp brings Lucene-style full-text search to Android and iOS.", Field.Store.YES))
    }
    writer.addDocument(doc)
}

val reader = StandardDirectoryReader.open(directory, null, null)
val searcher = IndexSearcher(reader)
val query = QueryParser("body", analyzer).parse("full-text search")
val hits = searcher.search(query, 10)
val storedFields = searcher.storedFields()
for (scoreDoc in hits.scoreDocs) {
    val doc = storedFields.document(scoreDoc.doc)
    println("id=${doc.get("id")} score=${scoreDoc.score}")
}
reader.close()
directory.close()
```

For indexed field types, filters, sort, phrase/fuzzy/wildcard queries, geo and IP queries, see
[docs/USAGE.md](docs/USAGE.md).

## Java Lucene and lucene-kmp
* lucene-kmp prioritizes the core indexing and search APIs, then adds common analyzers and codecs.
* `core` and `queryparser` classes ported so far get the index and search jobs done. 
* Some optionals classes left not ported yet.
* Languages with large speaker population not covered by Java Lucene got extra analyzers in lucene-kmp.
* See [LANGUAGE_COVERAGE.md](LANGUAGE_COVERAGE.md) for language coverage comparison table.

| Java Lucene                 | lucene-kmp            | Ported | Note                          |
|-----------------------------|-----------------------|--------|-------------------------------|
| `analysis/common`           | `analysis/common`     | `83%`  | Language Analyzers            |
| `analysis/icu`              |                       |        |                               |
| `analysis/kuromoji`         | `analysis/kuromoji`   | `98%`  | JapaneseAnalyzer              |
| `analysis/morfologik`       | `analysis/morfologik` | `100%` | Polish and Ukranian Analyzers |
| `analysis/morfologik.tests` |                       |        |                               |
| `analysis/nori`             | `analysis/nori`       | `100%` | KoreanAnalyzer                |
| `analysis/opennlp`          |                       |        |                               |
| `analysis/phonetic`         |                       |        |                               |
| `analysis/smartcn`          | `analysis/smartcn`    | `100%` | SmartChineseAnalyzer          |
| `analysis/stempel`          |                       |        |                               |
|                             | `analysis/extra`      | `new`  | Additional Language Analyzers |
|                             | `analysis/hebmorph`   | `new`  | HebrewAnalzyer                |
|                             | `analysis/horn`       | `new`  | AmharicAnalyzer               |
| `analysis.tests`            |                       |        |                               |
| `backward-codecs`           |                       |        | Will not be supported in kmp  |
| `benchmark`                 |                       |        |                               |
| `benchmark-jmh`             |                       |        |                               |
| `classification`            |                       |        |                               |
| `codecs`                    | `codecs`              | `59%`  | Index format codecs           |
| `core`                      | `core`                | `97%`  | Indexing and Searching        |
| `core.tests`                |                       |        |                               |
| `demo`                      |                       |        |                               |
| `distribution`              |                       |        |                               |
| `distribution.tests`        |                       |        |                               |
| `documentation`             |                       |        |                               |
| `expressions`               |                       |        |                               |
| `facet`                     |                       |        |                               |
| `grouping`                  |                       |        |                               |
| `highlighter`               |                       |        |                               |
| `join`                      |                       |        |                               | 
| `luke`                      |                       |        |                               |
| `memory`                    |                       |        |                               |
| `misc`                      |                       |        |                               |
| `monitor`                   |                       |        |                               |
| `queries`                   | `queries`             | `17%`  | Span and positional queries   |
| `queryparser`               | `queryparser`         | `5%`   | Parses query strings          |
| `replicator`                |                       |        |                               |
| `sandbox`                   |                       |        |                               |
| `spatial-extras`            |                       |        |                               |
| `spatial-test-fixtures`     |                       |        |                               |
| `spatial3d`                 |                       |        |                               |
| `suggest`                   |                       |        |                               |
| `test-framework`            | `test-framework`      | `77%`  | Shared test classes           |

## Lucene or SQL

If you wonder you need to use lucene-kmp, consider the difference between a search engine library and a database.
In KMP development, SQL libraries such as SQLDelight and Room KMP are the right choice for structured app data: tables, rows, primary keys, relations, transactions, migrations, and exact queries like "all notes updated after this date" or "all messages in this conversation." Use lucene-kmp when the user experience is a search box over text: ranked results, tokenization, stemming, phrase queries, typo-tolerant search, and fast lookup across document fields. Many apps should use both: keep SQLDelight or Room KMP as the source of truth, then build a lucene-kmp index from the searchable fields for high-quality local search.

| Capability / difference                         | Lucene                                                      | SQL                                                                                |
|-------------------------------------------------|-------------------------------------------------------------|------------------------------------------------------------------------------------|
| Full-text relevance scoring                     | ✓ Built in                                                  | ✗ Not part of standard SQL                                                         |
| Tokenization and text analysis                  | ✓ Analyzer pipeline                                         | ✗ Usually external or database-specific                                            |
| Stemming, stop words, and language-aware search | ✓ Common Lucene use case                                    | ✗ Database-specific, if available                                                  |
| Phrase queries                                  | ✓ Built in                                                  | ✗ Not generally available with normal indexes                                      |
| Fuzzy search and typo tolerance                 | ✓ Supported by query types                                  | ✗ Not generally available with normal indexes                                      |
| Boolean text queries                            | ✓ Designed for search queries                               | △ Possible, but not optimized for full-text relevance                              |
| Wildcard and prefix text queries                | ✓ Supported by query types                                  | △ Possible with `LIKE`, often slow or limited                                      |
| Range queries over indexed fields               | ✓ Supported                                                 | ✓ Supported                                                                        |
| Fielded document search                         | ✓ Natural model                                             | ✓ Natural model with columns                                                       |
| Faceting / drill-down search                    | ✓ Search-oriented pattern                                   | △ Possible with aggregation queries                                                |
| Sorting and filtering search results            | ✓ Supported with search results                             | ✓ Supported                                                                        |
| Incremental updates                             | ✓ Add, update, delete documents                             | ✓ Insert, update, delete rows                                                      |
| Transactions and relational integrity           | ✗ Not the primary model                                     | ✓ Core strength                                                                    |
| Joins across normalized data                    | ✗ Denormalize into documents                                | ✓ Core strength                                                                    |
| Index type                                      | Inverted index optimized for term lookup and scoring        | B-tree and related indexes optimized for exact lookup, ranges, joins, and ordering |
| Data model                                      | Documents with fields                                       | Tables with rows, columns, constraints, and relations                              |
| Best fit                                        | Search boxes, ranked results, local document/content search | Source-of-truth data storage, relational queries, transactions                     |

## Porting Progress
* [PROGRESS.md](PROGRESS.md) tracks the porting progress by class counts, generated by [progress.main.kts](progress.main.kts).
* [PROGRESS2.md](PROGRESS2.md) tracks the porting progress by semantic completion of core methods and classes, generated by [progress2.main.kts](progress2.main.kts).
* [TODO.md](TODO.md) tracks the dependency depth aware porting order suggestion, generated by [progress2.main.kts](progress2.main.kts).

Currently the port is based on Java Lucene commit [`ec75fcad5a4208c7b9e35e870229d9b703cda8f3`](https://github.com/apache/lucene/commit/ec75fcad5a4208c7b9e35e870229d9b703cda8f3) on Mar 7, 2025 which is some commits ahead of lucene 10.2.0 release.
No specific reason why I chose this. This is the day I decided to port Lucene to KMP and just ran git clone.

### `jdkport` package

Lucene is heavily dependent on JDK classes which drop in replacement is not available in Kotlin/Common standard library.

The `org.gnit.lucenekmp.jdkport` package lives inside the `core` module and provides
Kotlin Multiplatform replacements for JDK classes and interfaces that the Java Lucene code base
depends on but that are not part of the Kotlin standard library, such as `TreeMap`, `ByteBuffer`,
`BufferedReader`, `ReentrantLock`, `ThreadPoolExecutor`, `Charset`, `BreakIterator`, and `Math`.

Porting those jdk source doe into kmp consumes a lot of time, effort and tokens,
but I thought it worth doing it to behave as close as possible to the Java Lucene
and side by side easy review.

The jdkport classes are mostly ported from openjdk 24 fully.
In some case, partial methods/functions are ported.
And in case such as concurrency utilities, Kotlin Coroutines are as alternative to Java Threads and locks.
java.util.Date is replaced by kotlin.time.Instant

### 3rd-party dependencies
lucene-kmp try not to depend on other libraries as much as possible but exceptionally depends on following kmp libs:
* [`io.github.oshai:kotlin-logging`](https://github.com/oshai/kotlin-logging) for logging
* [`dev.scottpierce:kotlin-env-var`](https://github.com/ScottPierce/kotlin-env-var) for environment variable access in tests
* [`com.squareup.okio:okio`](https://github.com/square/okio) for file and path access replacing `java.nio.Path` and related operations
* [`com.ionspin.kotlin:bignum`](https://github.com/ionspin/kotlin-multiplatform-bignum) replaces `java.math.BigInteger`


## About Apache Lucene

[Apache Lucene](https://github.com/apache/lucene) is a high-performance search engine library. It
is not a search server or database by itself; it is the indexing and query engine that applications
embed when they need fast full-text search, relevance ranking, fielded search, filters, sorting,
faceting-style building blocks, and specialized query types over local or server-side data.

Lucene started as an open source Java project created by Doug Cutting [@cutting](https://github.com/cutting) and later became an [Apache
Software Foundation](https://www.apache.org/) project. Over time it became one of the foundational libraries for information
retrieval on the JVM, with a long history of production use, careful index-format evolution, and
deep engineering around analyzers, inverted indexes, scoring, segment merging, and low-level search
performance.

Many well-known search platforms are built on Apache Lucene. [Apache Solr](https://github.com/apache/solr)
is the long-running Lucene search server in the Apache ecosystem.
[Elasticsearch](https://github.com/elastic/elasticsearch) and
[OpenSearch](https://github.com/opensearch-project/OpenSearch) also use Lucene as their core search
engine underneath distributed indexing, clustering, REST APIs, replication, and operational tooling.
In that sense, Lucene is the lower-level engine that powers many higher-level search systems.

Disclaimer:
I am not affiliated with the Apache Software Foundation or the official Lucene project.
lucene-kmp is an independent port of the Java Lucene source code to Kotlin Multiplatform, and it is not an official Apache project.
I'm hoping in one day it will be listed as 14th entry of the list of [other language ports of Lucene](https://cwiki.apache.org/confluence/display/lucene/LuceneImplementations).


## Motivation
I built and was using [bbl](https://github.com/nehemiaharchives/bbl) command line bible to read and search the bible on my machine. 
I used Apache Lucene and Kotlin/JVM for the full text search capability.
Then I also built [bbl-android](https://github.com/nehemiaharchives/bbl-android) without the search feature, because Java Lucene does not run on Android.
I knoew Lucene was already ported to 13 languages/platforms but it was too large codebase for me alone to port.
Then AI coding assistants/agents start to emerge and I started [bbl-kmp](https://github.com/nehemiaharchives/bbl-kmp) to read and search bible in cli and mobile porting and dogfooding lucene-kmp!


## AI Usage
* I started to port Lucene using JetBrains' Covert [Java to Kotlin](https://www.jetbrains.com/help/idea/get-started-with-kotlin.html#convert-java-to-kotlin) feature.
At that time, one line suggestion of GitHub Copilot was only AI assistant I used.
* Then when it gets time and effort consuming to rewrite such as "Java threading to Kotlin coroutines", I used ChatGPT on the web to draft the ported code.
* Then GitHub Copilot plugin for IntelliJ IDEA launched and I used "Edit" feature to rewrite 10s of lines of block of code.
* Codex launched, I immediately started to use it but for partial edit, coworking with human review and modification.
* [ANGENTS.md](ANGENTS.md) and [SKILL.md](.agents/skills/port/SKILL.md) are created based on the know-hows and lessons learned from the semi-manual porting process.
* Codex with gpt-5.4 under the hood were the first model which is reliably able to port a whole class. gpt-5.5 managed to port a whole package.

## Documentation
The project website lives under [docs/](docs/) and is built with Doks / Thulite.

Build the combined website and generated Dokka API docs locally:

```bash
scripts/build-docs-site.sh
```

Generated outputs are intentionally not committed:

- `docs/public/` contains the Hugo site artifact.
- `docs/api/` contains generated Dokka API docs for local preview and deployment.

## License
Apache License 2.0, see [LICENSE](LICENSE) file.
