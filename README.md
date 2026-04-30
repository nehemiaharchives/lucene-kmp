# Lucene Kotlin Multiplatform

[![CI](https://github.com/nehemiaharchives/lucene-kmp/actions/workflows/ci.yml/badge.svg)](https://github.com/nehemiaharchives/lucene-kmp/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/nehemiaharchives/lucene-kmp/graph/badge.svg?token=YRN8URPQA4)](https://codecov.io/gh/nehemiaharchives/lucene-kmp)

## Overview

lucene-kmp is a [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) port of [Apache Lucene](https://github.com/apache/lucene/) for projects that need Lucene-style indexing and full text search outside the JVM. Its main advantage is that it brings local full-text search to mobile and tablet targets where Java Lucene does not run, including iOS through Kotlin/Native.

The goal is to let Kotlin Multiplatform apps share one search implementation across Android, iOS, desktop, and native targets. The core indexing, search, and store paths are the most tested parts of the port, while some non-core Lucene APIs and modules are still incomplete. Current releases are published to [Maven Central](https://central.sonatype.com/) as alpha builds while the port catches up from the Lucene source baseline toward the 10.2.0 release.

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

## Lucene or SQL

In KMP development, SQL libraries such as SQLDelight and Room KMP are the right choice for structured app data: tables, rows, primary keys, relations, transactions, migrations, and exact queries like "all notes updated after this date" or "all messages in this conversation." Use lucene-kmp when the user experience is a search box over text: ranked results, tokenization, stemming, phrase queries, typo-tolerant search, and fast lookup across document fields. Many apps should use both: keep SQLDelight or Room KMP as the source of truth, then build a lucene-kmp index from the searchable fields for high-quality local search.

| Capability / difference | Lucene | SQL |
| --- | --- | --- |
| Full-text relevance scoring | ✓ Built in | ✗ Not part of standard SQL |
| Tokenization and text analysis | ✓ Analyzer pipeline | ✗ Usually external or database-specific |
| Stemming, stop words, and language-aware search | ✓ Common Lucene use case | ✗ Database-specific, if available |
| Phrase queries | ✓ Built in | ✗ Not generally available with normal indexes |
| Fuzzy search and typo tolerance | ✓ Supported by query types | ✗ Not generally available with normal indexes |
| Boolean text queries | ✓ Designed for search queries | △ Possible, but not optimized for full-text relevance |
| Wildcard and prefix text queries | ✓ Supported by query types | △ Possible with `LIKE`, often slow or limited |
| Range queries over indexed fields | ✓ Supported | ✓ Supported |
| Fielded document search | ✓ Natural model | ✓ Natural model with columns |
| Faceting / drill-down search | ✓ Search-oriented pattern | △ Possible with aggregation queries |
| Sorting and filtering search results | ✓ Supported with search results | ✓ Supported |
| Incremental updates | ✓ Add, update, delete documents | ✓ Insert, update, delete rows |
| Transactions and relational integrity | ✗ Not the primary model | ✓ Core strength |
| Joins across normalized data | ✗ Denormalize into documents | ✓ Core strength |
| Index type | Inverted index optimized for term lookup and scoring | B-tree and related indexes optimized for exact lookup, ranges, joins, and ordering |
| Data model | Documents with fields | Tables with rows, columns, constraints, and relations |
| Best fit | Search boxes, ranked results, local document/content search | Source-of-truth data storage, relational queries, transactions |

## Gradle Dependencies

in your `build.gradle.kts` add things you need to use among following:

```kotlin
// Core Lucene APIs for indexing, searching, documents, fields, stores, analyzers, geo, and IP address queries.
// Most apps should start with this module.
implementation("org.gnit.lucene-kmp:lucene-kmp-core:10.2.0-alpha12")

// Classic query parsers for user-entered search strings, fielded queries, phrases, wildcards, and boolean syntax.
implementation("org.gnit.lucene-kmp:lucene-kmp-queryparser:10.2.0-alpha12")

// Span and positional query APIs for precise token-order, proximity, containment, and advanced text matching.
implementation("org.gnit.lucene-kmp:lucene-kmp-queries:10.2.0-alpha12")

// Additional codec implementations such as SimpleText, memory postings, and block-tree ords formats.
// Add this when you need explicit codec support beyond the core default path.
implementation("org.gnit.lucene-kmp:lucene-kmp-codecs:10.2.0-alpha12")

// Common analyzers, tokenizers, stemmers, and filters for broad language support.
// Includes English, French, German, Spanish, Portuguese, Italian, Dutch, Russian, Swedish, 
// Hindi, Bengali, Thai, Indonesian, Nepali, Tamil, Telugu.
implementation("org.gnit.lucene-kmp:lucene-kmp-analysis-common:10.2.0-alpha12")

// Extra analyzers for languages and integrations that are useful but less central than analysis-common.
// Includes Vietnamese, Tagalog, Marathi, Gujarati, Urdu.
implementation("org.gnit.lucene-kmp:lucene-kmp-analysis-extra:10.2.0-alpha12")

// Morfologik dictionary-backed morphological analysis, currently including Polish and Ukrainian analyzers.
implementation("org.gnit.lucene-kmp:lucene-kmp-analysis-morfologik:10.2.0-alpha12")

// Chinese analyzer powered by Smart Chinese analysis.
implementation("org.gnit.lucene-kmp:lucene-kmp-analysis-smartcn:10.2.0-alpha12")

// Korean analyzer powered by Nori morphological analysis.
implementation("org.gnit.lucene-kmp:lucene-kmp-analysis-nori:10.2.0-alpha12")

// Japanese analyzer powered by Kuromoji morphological analysis.
implementation("org.gnit.lucene-kmp:lucene-kmp-analysis-kuromoji:10.2.0-alpha12")
```

## Usage
### Building Index

Create a `Directory`, configure an `IndexWriter`, then add one Lucene `Document` per record in your
app. Text fields are analyzed for full-text search, point fields are indexed for fast numeric
filters, stored fields are returned in search results, and doc-values fields are used for sorting.

```text
import okio.Path
import org.gnit.lucenekmp.analysis.standard.StandardAnalyzer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.store.FSDirectory
```

```kotlin
fun buildIndex(indexPath: Path) {
    Files.createDirectories(indexPath)

    val directory = FSDirectory.open(indexPath)
    val analyzer = StandardAnalyzer()
    val config = IndexWriterConfig(analyzer)
        .setOpenMode(IndexWriterConfig.OpenMode.CREATE)

    IndexWriter(directory, config).use { writer ->
        val doc = Document()

        doc.add(StringField(
            "id",
            "note-1",
            Field.Store.YES
        ))
        doc.add(TextField(
            "title",
            "Kotlin Multiplatform search",
            Field.Store.YES
        ))
        doc.add(TextField(
            "body",
            "lucene-kmp brings Lucene-style full-text search to Android and iOS.",
            Field.Store.YES
        ))

        doc.add(IntPoint("year", 2026))
        doc.add(StoredField("year", 2026))
        doc.add(NumericDocValuesField("yearSort", 2026L))

        writer.addDocument(doc)
    }

    directory.close()
}
```

### Search

Open the same index with a `DirectoryReader`, create an `IndexSearcher`, parse user text with
`QueryParser`, then combine full-text matching with structured filters and sorting.

```text
import okio.Path
import org.gnit.lucenekmp.analysis.standard.StandardAnalyzer
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.index.StandardDirectoryReader
import org.gnit.lucenekmp.queryparser.classic.QueryParser
import org.gnit.lucenekmp.search.BooleanClause
import org.gnit.lucenekmp.search.BooleanQuery
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.store.FSLockFactory
import org.gnit.lucenekmp.store.NIOFSDirectory
```

```kotlin
fun searchIndex(indexPath: Path, userText: String = "full-text search") {
    val directory = NIOFSDirectory(indexPath, FSLockFactory.default)
    val reader = StandardDirectoryReader.open(directory, null, null)
    val searcher = IndexSearcher(reader)

    val analyzer = StandardAnalyzer()
    val textQuery = QueryParser("body", analyzer)
        .parse(userText)
    val yearFilter = IntPoint.newRangeQuery("year", 2020, 2026)

    val query = BooleanQuery.Builder()
        .add(textQuery, BooleanClause.Occur.MUST)
        .add(yearFilter, BooleanClause.Occur.FILTER)
        .build()

    val sort = Sort(
        SortField.FIELD_SCORE,
        SortField("yearSort", SortField.Type.LONG, true)
    )

    val hits = searcher.search(query, 10, sort)
    val storedFields = searcher.storedFields()

    for (scoreDoc in hits.scoreDocs) {
        val doc = storedFields.document(scoreDoc.doc)
        println("${doc.get("id")} score=${scoreDoc.score} title=${doc.get("title")} year=${doc.get("year")}")
    }

    reader.close()
    directory.close()
}
```

## Query

The examples below use a simple mobile app data model:

```kotlin
// Example document fields:
// id: stable app/database id
// title: short searchable text
// body: long searchable text
// category: exact structured value
// updatedAt: timestamp for filtering or sorting
// location: latitude/longitude for geo queries
// ip: IPv4 or IPv6 address for network queries
```

Imports are omitted from the snippets to keep the examples focused. The class names shown are from
`org.gnit.lucenekmp.*` packages.

### Basic standard queries

Start with the same exact-match, range, boolean, and sort patterns that mobile developers already
know from SQL. SQL is usually the better source of truth for this kind of structured data, while
Lucene is useful when these filters need to be combined with full-text relevance.

#### Exact lookup by id

SQL:

```sql
SELECT * FROM notes WHERE id = 'note-123';
```

lucene-kmp:

```kotlin
val query = TermQuery(Term("id", "note-123"))
val hits = searcher.search(query, 10)
```

#### Filter by category

SQL:

```sql
SELECT * FROM notes WHERE category = 'bible-study';
```

lucene-kmp:

```kotlin
val query = TermQuery(Term("category", "bible-study"))
val hits = searcher.search(query, 10)
```

#### Numeric range filter

SQL:

```sql
SELECT * FROM notes
WHERE updated_at BETWEEN 1704067200 AND 1735689600;
```

lucene-kmp:

```kotlin
val query = LongPoint.newRangeQuery(
    "updatedAt",
    1_704_067_200L,
    1_735_689_600L
)
val hits = searcher.search(query, 10)
```

#### Combine exact filters

SQL:

```sql
SELECT * FROM notes
WHERE category = 'journal'
  AND updated_at >= 1704067200
  AND deleted = 0;
```

lucene-kmp:

```kotlin
val query = BooleanQuery.Builder()
    .add(TermQuery(Term("category", "journal")), Occur.FILTER)
    .add(LongPoint.newRangeQuery("updatedAt", 1_704_067_200L, Long.MAX_VALUE), Occur.FILTER)
    .add(TermQuery(Term("deleted", "false")), Occur.FILTER)
    .build()

val hits = searcher.search(query, 10)
```

#### Sort structured results

SQL:

```sql
SELECT * FROM notes
WHERE category = 'journal'
ORDER BY updated_at DESC
LIMIT 20;
```

lucene-kmp:

```kotlin
val query = TermQuery(Term("category", "journal"))
val sort = Sort(SortField("updatedAt", SortField.Type.LONG, true))
val hits = searcher.search(query, 20, sort)
```

### Advanced search queries

These examples are the reason to add a Lucene index next to SQLDelight or Room KMP. SQL can store
the original records, while lucene-kmp can serve the user-facing search experience.

#### Relevance-ranked full-text search

SQL can do substring matching, but it does not normally rank results by term statistics and field
matches. Lucene returns the best matches first.

```kotlin
val analyzer = StandardAnalyzer()
val parser = QueryParser("body", analyzer)

val query = parser.parse("resurrection hope")
val hits = searcher.search(query, 20)
```

#### Search multiple fields with different boosts

This is useful for mobile search screens where a title match should rank higher than a body match.

```kotlin
val analyzer = StandardAnalyzer()
val parser = MultiFieldQueryParser(
    arrayOf("title", "body", "tags"),
    analyzer,
    mapOf(
        "title" to 4.0f,
        "tags" to 2.0f,
        "body" to 1.0f
    )
)

val query = parser.parse("faith works")
val hits = searcher.search(query, 20)
```

#### Phrase query

Phrase queries match words in sequence, not just anywhere in the same document.

```kotlin
val query = PhraseQuery.Builder()
    .add(Term("body", "kingdom"))
    .add(Term("body", "god"))
    .build()

val hits = searcher.search(query, 20)
```

The query parser can also build phrase queries from user text:

```kotlin
val query = QueryParser("body", StandardAnalyzer())
    .parse("\"kingdom of god\"")
```

#### Near phrase query

Slop allows words to appear near each other, which is useful when users remember a phrase
approximately.

```kotlin
val query = PhraseQuery.Builder()
    .setSlop(3)
    .add(Term("body", "love"))
    .add(Term("body", "neighbor"))
    .build()

val hits = searcher.search(query, 20)
```

#### Fuzzy query for typos

Fuzzy queries can match terms that are close to the user's input.

```kotlin
val query = FuzzyQuery(Term("body", "resurection"), 2)
val hits = searcher.search(query, 20)
```

The query parser syntax also supports fuzzy terms:

```kotlin
val query = QueryParser("body", StandardAnalyzer())
    .parse("resurection~2")
```

#### Prefix and wildcard queries

Prefix and wildcard queries are useful for search-as-you-type and flexible term matching.

```kotlin
val prefixQuery = PrefixQuery(Term("title", "gen"))
val wildcardQuery = WildcardQuery(Term("title", "psal?"))

val prefixHits = searcher.search(prefixQuery, 10)
val wildcardHits = searcher.search(wildcardQuery, 10)
```

#### Mix full-text relevance with structured filters

This is a common pattern for real apps: search text, but only inside the current notebook, category,
tenant, user, or date range.

```kotlin
val textQuery = QueryParser("body", StandardAnalyzer()).parse("mercy peace")

val query = BooleanQuery.Builder()
    .add(textQuery, Occur.MUST)
    .add(TermQuery(Term("notebookId", "personal")), Occur.FILTER)
    .add(LongPoint.newRangeQuery("updatedAt", 1_704_067_200L, Long.MAX_VALUE), Occur.FILTER)
    .build()

val hits = searcher.search(query, 20)
```

### Lucene-only features

These examples are difficult or impractical with ordinary SQL indexes because they depend on Lucene's
inverted index, analyzers, scoring model, point indexes, or specialized query implementations.

#### Why a result matched

Lucene can explain scoring, which helps debug search ranking and tune analyzers or boosts.

```kotlin
val query = QueryParser("body", StandardAnalyzer()).parse("grace faith")
val hits = searcher.search(query, 10)

if (hits.scoreDocs.isNotEmpty()) {
    val explanation = searcher.explain(query, hits.scoreDocs[0].doc)
    println(explanation)
}
```

#### Geospatial queries

lucene-kmp also includes Lucene's geo-style APIs. These are useful for local search features such as
"near me", map bounding boxes, delivery regions, geofenced content, and distance sorting.

##### Index latitude/longitude fields

Use `LatLonPoint` for efficient geo filtering and `LatLonDocValuesField` when you also need distance
sorting.

```kotlin
val doc = Document()
doc.add(TextField("title", "Coffee shop near Shibuya Station", Field.Store.YES))
doc.add(LatLonPoint("location", 35.6580, 139.7016))
doc.add(LatLonDocValuesField("location", 35.6580, 139.7016))
doc.add(StoredField("placeId", "shibuya-coffee-1"))

writer.addDocument(doc)
```

##### Bounding box query

This is similar to a map viewport query.

```kotlin
val query = LatLonPoint.newBoxQuery(
    "location",
    35.60, 35.75,
    139.60, 139.85
)

val hits = searcher.search(query, 50)
```

##### Distance query

Find documents within a radius from a point.

```kotlin
val query = LatLonPoint.newDistanceQuery(
    "location",
    35.6580,
    139.7016,
    1_000.0
)

val hits = searcher.search(query, 20)
```

##### Polygon query

Polygon queries are useful for custom regions that are not simple rectangles.

```kotlin
val polygon = Polygon(
    doubleArrayOf(35.65, 35.65, 35.70, 35.70, 35.65),
    doubleArrayOf(139.68, 139.74, 139.74, 139.68, 139.68)
)

val query = LatLonPoint.newPolygonQuery("location", polygon)
val hits = searcher.search(query, 50)
```

##### Sort by distance

Use this for "nearest first" results.

```kotlin
val sort = Sort(LatLonDocValuesField.newDistanceSort("location", 35.6580, 139.7016))
val hits = searcher.search(MatchAllDocsQuery(), 20, sort)
```

##### Combine text search with geo search

This is hard to express well with plain SQL because results need both text relevance and spatial
filtering.

```kotlin
val textQuery = QueryParser("body", StandardAnalyzer()).parse("quiet workspace wifi")
val nearbyQuery = LatLonPoint.newDistanceQuery("location", 35.6580, 139.7016, 2_000.0)

val query = BooleanQuery.Builder()
    .add(textQuery, Occur.MUST)
    .add(nearbyQuery, Occur.FILTER)
    .build()

val hits = searcher.search(query, 20)
```

#### IP address and network queries

`InetAddressPoint` indexes IPv4 and IPv6 addresses as 128-bit point values. This is useful for local
security logs, sync logs, device inventories, server lists, and network tools that need exact IP,
CIDR, or address-range lookup.

##### Index an IP address

```kotlin
val address = InetAddress.getByName("192.168.1.42")

val doc = Document()
doc.add(InetAddressPoint("ip", address))
doc.add(StoredField("host", "tablet-42"))

writer.addDocument(doc)
```

##### Exact IP query

```kotlin
val query = InetAddressPoint.newExactQuery(
    "ip",
    InetAddress.getByName("192.168.1.42")
)

val hits = searcher.search(query, 10)
```

##### CIDR prefix query

Find every address inside a network such as `192.168.1.0/24`.

```kotlin
val query = InetAddressPoint.newPrefixQuery(
    "ip",
    InetAddress.getByName("192.168.1.0"),
    24
)

val hits = searcher.search(query, 100)
```

##### IP range query

```kotlin
val query = InetAddressPoint.newRangeQuery(
    "ip",
    InetAddress.getByName("192.168.1.10"),
    InetAddress.getByName("192.168.1.99")
)

val hits = searcher.search(query, 100)
```

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

## Lucene Java Source Project
currently the port is based on following commit of lucene main branch which is some commits before lucene 10.2.0 release
```
commit ec75fcad5a4208c7b9e35e870229d9b703cda8f3 (HEAD -> main, origin/main, origin/HEAD)
Author: Robert Muir <rmuir@apache.org>
Date:   Sun Mar 2 14:11:10 2025 -0500

    reformat the python code with 'make reformat' and enable format in CI check

    currently the python code has a mix of indentation, styles, imports
    ordering, etc. for example, it is very difficult to work with mixed
    indentation levels: the language is sensitive to indentation.

    reformat all the code with 'make reformat' and enable format checks when
    linting. It works like spotless, just don't think about it.
```

## Porting Progress
Package-level completion for Lucene modules, split by production code and tests.

### core module
The `core` module is the foundation of lucene-kmp. It contains the core indexing, searching,
documents, geo, store, and utility APIs that most applications use directly, and it is the base
that the other Lucene modules build on.

| Package | Code | Tests |
| --- | ---: | ---: |
| `org.apache.lucene.analysis` | 94% | 100% |
| `org.apache.lucene.codecs` | 99% | 97% |
| `org.apache.lucene.document` | 98% | 100% |
| `org.apache.lucene.geo` | 100% | 100% |
| `org.apache.lucene.index` | 98% | 100% |
| `org.apache.lucene.internal` | 100% | 100% |
| `org.apache.lucene.search` | 99% | 98% |
| `org.apache.lucene.store` | 96% | 100% |
| `org.apache.lucene.util` | 95% | 95% |
| **Total** | **97%** | **98%** |

### queryparser module
The `queryparser` module parses user-entered search strings into Lucene `Query` objects. This is
the module to add when you want classic query syntax such as fielded search, phrases, boolean
operators, wildcards, and multi-field parsing.

| Package | Code | Tests |
| --- | ---: | ---: |
| `org.apache.lucene.queryparser.charstream` | 100% | - |
| `org.apache.lucene.queryparser.classic` | 100% | 100% |
| `org.apache.lucene.queryparser.complexPhrase` | 100% | 100% |
| `org.apache.lucene.queryparser.ext` | 0% | 0% |
| `org.apache.lucene.queryparser.flexible.core` | 0% | 0% |
| `org.apache.lucene.queryparser.flexible.messages` | 0% | 0% |
| `org.apache.lucene.queryparser.flexible.precedence` | 0% | 0% |
| `org.apache.lucene.queryparser.flexible.standard` | 33% | 0% |
| `org.apache.lucene.queryparser.simple` | 0% | 0% |
| `org.apache.lucene.queryparser.surround.query` | 0% | 0% |
| `org.apache.lucene.queryparser.xml` | 0% | 0% |

### queries module
The `queries` module contains additional query families beyond the core search API. In the current
port it is mainly valuable for advanced positional and span-based querying.

| Package | Code | Tests |
| --- | ---: | ---: |
| `org.apache.lucene.queries` | 0% | 0% |
| `org.apache.lucene.queries.function` | 0% | 0% |
| `org.apache.lucene.queries.intervals` | 0% | 0% |
| `org.apache.lucene.queries.mlt` | 0% | 0% |
| `org.apache.lucene.queries.payloads` | 0% | 0% |
| `org.apache.lucene.queries.spans` | 100% | 100% |

### codecs module
The `codecs` module contains Lucene index format implementations and related low-level read/write
components. Add it when you need concrete codec support beyond the default path exposed by `core`.

| Package | Code | Tests |
| --- | ---: | ---: |
| `org.apache.lucene.codecs` | 100% | 75% |
| `org.apache.lucene.codecs.bitvectors` | 0% | 0% |
| `org.apache.lucene.codecs.blockterms` | 100% | 0% |
| `org.apache.lucene.codecs.blocktreeords` | 100% | 100% |
| `org.apache.lucene.codecs.bloom` | 100% | 0% |
| `org.apache.lucene.codecs.compressing` | 100% | 100% |
| `org.apache.lucene.codecs.hnsw` | 100% | 100% |
| `org.apache.lucene.codecs.lucene101` | 88% | 100% |
| `org.apache.lucene.codecs.lucene102` | 100% | 100% |
| `org.apache.lucene.codecs.lucene90` | 100% | 100% |
| `org.apache.lucene.codecs.lucene94` | 100% | 100% |
| `org.apache.lucene.codecs.lucene95` | 100% | - |
| `org.apache.lucene.codecs.lucene99` | 100% | 100% |
| `org.apache.lucene.codecs.memory` | 20% | 0% |
| `org.apache.lucene.codecs.perfield` | 100% | 100% |
| `org.apache.lucene.codecs.simpletext` | 100% | 90% |
| `org.apache.lucene.codecs.uniformsplit` | 0% | 0% |

### analysis-common module
The `analysis-common` module contains the standard shared analyzers, tokenizers, stemmers, and token
filters that cover a broad set of languages and general text processing tasks.

| Package | Code | Tests |
| --- | ---: | ---: |
| `org.apache.lucene.analysis` | 100% | 100% |
| `org.apache.lucene.analysis.bn` | 100% | 100% |
| `org.apache.lucene.analysis.charfilter` | 50% | 25% |
| `org.apache.lucene.analysis.core` | 25% | 0% |
| `org.apache.lucene.analysis.de` | 100% | 100% |
| `org.apache.lucene.analysis.en` | 100% | 100% |
| `org.apache.lucene.analysis.es` | 70% | 71% |
| `org.apache.lucene.analysis.fr` | 71% | 60% |
| `org.apache.lucene.analysis.hi` | 100% | 100% |
| `org.apache.lucene.analysis.id` | 100% | 100% |
| `org.apache.lucene.analysis.it` | 100% | 100% |
| `org.apache.lucene.analysis.ne` | 100% | 100% |
| `org.apache.lucene.analysis.nl` | 100% | 100% |
| `org.apache.lucene.analysis.pt` | 100% | 100% |
| `org.apache.lucene.analysis.ru` | 100% | 100% |
| `org.apache.lucene.analysis.standard` | 100% | 100% |
| `org.apache.lucene.analysis.sv` | 100% | 100% |
| `org.apache.lucene.analysis.ta` | 100% | 100% |
| `org.apache.lucene.analysis.te` | 100% | 100% |
| `org.apache.lucene.analysis.th` | 100% | 100% |
| `org.apache.lucene.analysis.tokenattributes` | 87% | 100% |
| `org.apache.lucene.analysis.uk` | 100% | 100% |
| `org.apache.lucene.analysis.util` | 81% | 87% |

### analysis-extra module
The `analysis-extra` module currently groups KMP-specific and custom analyzer work that does not map
cleanly to one upstream Lucene Java module. It currently includes extra language analyzers such as
Vietnamese, Gujarati, Marathi, Tagalog, and Urdu.

| Package | Code | Tests |
| --- | ---: | ---: |
| Custom KMP analyzers, not tracked as upstream Lucene package rows in `PROGRESS.md` | - | - |

### analysis-morfologik module
The `analysis-morfologik` module provides dictionary-backed morphological analysis based on
Morfologik, currently covering analyzers such as Polish and Ukrainian.

| Package | Code | Tests |
| --- | ---: | ---: |
| `org.apache.lucene.analysis.morfologik` | 100% | 100% |

### analysis-smartcn module
The `analysis-smartcn` module provides Chinese text analysis based on Lucene's Smart Chinese
analyzer.

| Package | Code | Tests |
| --- | ---: | ---: |
| `org.apache.lucene.analysis.cn.smart` | 100% | 100% |
| `org.apache.lucene.analysis.cn.smart.hhmm` | 100% | - |

### analysis-nori module
The `analysis-nori` module provides Korean text analysis based on Nori morphological analysis and
its supporting dictionaries.

| Package | Code | Tests |
| --- | ---: | ---: |
| `org.apache.lucene.analysis.ko` | 100% | 100% |
| `org.apache.lucene.analysis.ko.dict` | 100% | 100% |
| `org.apache.lucene.analysis.ko.tokenattributes` | 100% | - |

### analysis-kuromoji module
The `analysis-kuromoji` module provides Japanese text analysis based on Kuromoji morphological
analysis and its supporting dictionaries.

| Package | Code | Tests |
| --- | ---: | ---: |
| `org.apache.lucene.analysis.ja` | 100% | 100% |
| `org.apache.lucene.analysis.ja.completion` | 100% | 100% |
| `org.apache.lucene.analysis.ja.dict` | 100% | 100% |
| `org.apache.lucene.analysis.ja.tokenattributes` | 100% | - |


[PROGRESS.md](PROGRESS.md) is the high-level porting dashboard. It summarizes which Lucene classes
and unit test classes have been ported, so it is useful for quickly checking broad module coverage.
The file is generated by [progress.main.kts](progress.main.kts), which also prints the report to the
console.

[PROGRESS2.md](PROGRESS2.md) tracks semantic porting progress. It compares Java Lucene source and
lucene-kmp source at a finer level, including method-level parity and missing core business logic,
so it is the better report when deciding whether a class is functionally complete. The file is
generated by [progressv2.main.kts](progressv2.main.kts), which also prints the semantic report to the
console.

## TODO
[TODO.md](TODO.md) is the working queue for what to port next. Start there when looking for the next
class, test, module, or compatibility gap to work on.

After the current TODO queue is complete, the next milestone is catching up from the initial port
commit to Lucene 10.2.0 and later releases. The intended workflow is commit-by-commit catch-up
against the Lucene main branch, assisted by AI coding agents such as GitHub Copilot,
[OpenAI Codex](https://chatgpt.com/codex), [Google Jules](https://jules.google.com), and
[JetBrains Junie](https://www.jetbrains.com/junie/).

## Motivation
I built and was using [bbl](https://github.com/nehemiaharchives/bbl) command line bible to read and search the bible on my machine. 
I used Apache Lucene and Kotlin/JVM for the full text search capability.
Then I also built [bbl-android](https://github.com/nehemiaharchives/bbl-android) without the search feature, because Java Lucene does not run on Android.
I knoew Lucene was already ported to 13 languages/platforms but it was too large codebase for me alone to port.
Then AI coding assistants/agents start to emerge and I started [bbl-kmp](https://github.com/nehemiaharchives/bbl-kmp) to read and search bible in cli and mobile porting and dogfooding lucene-kmp!

## License
Apache License 2.0, see [LICENSE](LICENSE) file.
