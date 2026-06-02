# Usage

Lucene expects to have a inverted index on disk or in memory built prior to searching.
The examples below show how to build an index and run queries against it.

## Gradle Dependencies

in your `build.gradle.kts` add things you need to use among following:

```kotlin
// Core Lucene APIs for indexing, searching, documents, fields, stores, analyzers, geo, and IP address queries.
// Most apps should start with this module.
implementation("org.gnit.lucene-kmp:lucene-kmp-core:10.2.0-alpha13")

// Classic query parsers for user-entered search strings, fielded queries, phrases, wildcards, and boolean syntax.
implementation("org.gnit.lucene-kmp:lucene-kmp-queryparser:10.2.0-alpha13")

// Span and positional query APIs for precise token-order, proximity, containment, and advanced text matching.
implementation("org.gnit.lucene-kmp:lucene-kmp-queries:10.2.0-alpha13")

// Additional codec implementations such as SimpleText, memory postings, and block-tree ords formats.
// Add this when you need explicit codec support beyond the core default path.
implementation("org.gnit.lucene-kmp:lucene-kmp-codecs:10.2.0-alpha13")

// Common analyzers, tokenizers, stemmers, and filters for broad language support.
// Includes English, French, German, Spanish, Portuguese, Italian, Dutch, Russian, Swedish, 
// Hindi, Bengali, Thai, Indonesian, Nepali, Tamil, Telugu.
implementation("org.gnit.lucene-kmp:lucene-kmp-analysis-common:10.2.0-alpha13")

// Extra analyzers for languages and integrations that are useful but less central than analysis-common.
// Includes Vietnamese, Tagalog, Marathi, Gujarati, Urdu.
implementation("org.gnit.lucene-kmp:lucene-kmp-analysis-extra:10.2.0-alpha13")

// Morfologik dictionary-backed morphological analysis, currently including Polish and Ukrainian analyzers.
implementation("org.gnit.lucene-kmp:lucene-kmp-analysis-morfologik:10.2.0-alpha13")

// Chinese analyzer powered by Smart Chinese analysis.
implementation("org.gnit.lucene-kmp:lucene-kmp-analysis-smartcn:10.2.0-alpha13")

// Korean analyzer powered by Nori morphological analysis.
implementation("org.gnit.lucene-kmp:lucene-kmp-analysis-nori:10.2.0-alpha13")

// Japanese analyzer powered by Kuromoji morphological analysis.
implementation("org.gnit.lucene-kmp:lucene-kmp-analysis-kuromoji:10.2.0-alpha13")
```

## Building Index

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

## Search

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
