# Lucene Kotlin Multiplatform

[![CI](https://github.com/nehemiaharchives/lucene-kmp/actions/workflows/ci.yml/badge.svg)](https://github.com/nehemiaharchives/lucene-kmp/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/nehemiaharchives/lucene-kmp/graph/badge.svg?token=YRN8URPQA4)](https://codecov.io/gh/nehemiaharchives/lucene-kmp)

## Overview

lucene-kmp is a [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) port of [Apache Lucene](https://github.com/apache/lucene/) enabling ondevice indexing and full text search capabilities in KMP/CMP mobile and desktop apps.

## Supported platforms
* Kotlin/Jvm
* Kotlin/Android
* Kotlin/Native iOS (iosArm64, iosSimulatorArm64, iosX64)
* Kotlin/Native macOS (macosArm64, macosX64)
* Kotlin/Native Linux (linuxX64)
* Kotlin/Native Windows (mingwX64)

## Features
* **On-device full-text search**: build and query local indexes without a backend service, useful for offline-first mobile apps and private user data.
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
implementation("org.gnit.lucene-kmp:lucene-kmp-core:10.2.0-alpha12")
implementation("org.gnit.lucene-kmp:lucene-kmp-queryparser:10.2.0-alpha12")
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
lucene-kmp prioritizes the core indexing and search APIs, then adds common analyzers and codecs.
However extra analyzers where Java Lucene does not have are added.
Those analyzers support languages with a lot of speaking population as in [LANGUAGE_COVERAGE.md](LANGUAGE_COVERAGE.md).

| Java Lucene                 | lucene-kmp            | Note                          |
|-----------------------------|-----------------------|-------------------------------|
| `analysis/common`           | `analysis/common`     | Language Analyzers            |
| `analysis/icu`              |                       |                               |
| `analysis/kuromoji`         | `analysis/kuromoji`   | JapaneseAnalyzer              |
| `analysis/morfologik`       | `analysis/morfologik` | Polish and Ukranian Analyzers |
| `analysis/morfologik.tests` |                       |                               |
| `analysis/nori`             | `analysis/nori`       | KoreanAnalyzer                |
| `analysis/opennlp`          |                       |                               |
| `analysis/phonetic`         |                       |                               |
| `analysis/smartcn`          | `analysis/smartcn`    | SmartChineseAnalyzer          |
| `analysis/stempel`          |                       |                               |
|                             | `analysis/extra`      | Additional Language Analyzers |
|                             | `analysis/hebmorph`   | HebrewAnalzyer                |
|                             | `analysis/horn`       | AmharicAnalyzer               |
| `analysis.tests`            |                       |                               |
| `backward-codecs`           |                       | Will not be supported in kmp  |
| `benchmark`                 |                       |                               |
| `benchmark-jmh`             |                       |                               |
| `classification`            |                       |                               |
| `codecs`                    | `codecs`              | Index format codecs           |
| `core`                      | `core`                | Indexing and Searching        |
| `core.tests`                |                       |                               |
| `demo`                      |                       |                               |
| `distribution`              |                       |                               |
| `distribution.tests`        |                       |                               |
| `documentation`             |                       |                               |
| `expressions`               |                       |                               |
| `facet`                     |                       |                               |
| `grouping`                  |                       |                               |
| `highlighter`               |                       |                               |
| `join`                      |                       |                               |
| `luke`                      |                       |                               |
| `memory`                    |                       |                               |
| `misc`                      |                       |                               |
| `monitor`                   |                       |                               |
| `queries`                   | `queries`             | Span and positional queries   |
| `queryparser`               | `queryparser`         | Parses query strings          |
| `replicator`                |                       |                               |
| `sandbox`                   |                       |                               |
| `spatial-extras`            |                       |                               |
| `spatial-test-fixtures`     |                       |                               |
| `spatial3d`                 |                       |                               |
| `suggest`                   |                       |                               |
| `test-framework`            | `test-framework`      | Shared test classes           |

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
[PROGRESS.md](PROGRESS.md) tracks the mechanical porting progress.

currently the port is based on commit `ec75fcad5a4208c7b9e35e870229d9b703cda8f3` of lucene main branch which is some commits before lucene 10.2.0 release

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

Lucene started as an open source Java project created by Doug Cutting and later became an [Apache
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

## License
Apache License 2.0, see [LICENSE](LICENSE) file.
