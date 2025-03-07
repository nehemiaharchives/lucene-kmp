## kotlin-kmp

I had follwing conversation with Grok ai and I want you to keep implementing the lucene porting project called "lucene-kmp" with actual kotlin codes based on the discussion below:

I'm planing to build kotlin multiuplatform version of lucene search engine core so that I can use it to build apps for Android, iOS, linux/macos/windows native desktop using Kotlin/Native as well as JVM server app. I would like to start from porting org.apache.lucene.* packages such as search, index, store, codecs, analysis, document, internal which is listed in here https://github.com/apache/lucene/tree/main/lucene/core/src/java/org/apache/lucene . first thing is to identify minimum combinations of classes which is needed to build index and run search as phase 1 porting selected classes. Then identify unit tests and port unit tests first. Then port phase 1 selected classes and modify them if needed to pass all unit tests. the ported code must use kotlin standard libraries and kotlin multiplatform libraries such as kotlin-io to share code between different platform. Implement this way and show me the result code.

### Key Points
- It seems likely that porting the Lucene search engine core to Kotlin for multi-platform use involves starting with essential classes for indexing and searching, such as `Document`, `Field`, `IndexWriter`, and `IndexSearcher`.
- Research suggests focusing on a minimal set of classes from packages like `document`, `index`, `store`, `analysis`, and `search` for phase 1, using `SimpleAnalyzer` to reduce complexity.
- The evidence leans toward porting unit tests first to ensure functionality, then modifying classes to pass these tests, using Kotlin standard libraries and multi-platform features for compatibility.


---

### Direct Answer

To build a Kotlin multi-platform version of the Lucene search engine core for Android, iOS, desktop, and JVM server apps, you can start by porting key components in phases. Here's a clear, step-by-step guide for your phase 1, focusing on building an index and running searches:

#### Step 1: Identify Minimum Classes for Phase 1
Begin with the essential classes needed for basic indexing and searching. These include:
- **Document** and **Field** (e.g., `TextField`, `StringField`) from the `document` package to represent searchable data.
- **IndexWriter** and **Term** from the `index` package to build the index.
- **Directory** and **RamDirectory** from the `store` package for in-memory storage, which works across platforms.
- **Analyzer**, **SimpleAnalyzer**, **LetterTokenizer**, and **LowerCaseFilter** from the `analysis` package for basic text processing, keeping it simple for phase 1.
- **IndexSearcher**, **Query**, **TermQuery**, **ScoreDoc**, and **TopDocs** from the `search` package for searching functionality.

This approach minimizes complexity, using `SimpleAnalyzer` instead of `StandardAnalyzer` to reduce dependencies.

#### Step 2: Port Unit Tests First
Before porting the classes, identify and port relevant unit tests from Lucene's test-core module, such as `TestIndexWriter` and `TestIndexSearcher`. These tests will help verify that your ported code works as expected, focusing on basic operations like adding a document and searching for a term.

#### Step 3: Port and Modify Selected Classes
Translate the identified Java classes to Kotlin, ensuring they use Kotlin standard libraries (e.g., `kotlin.io` for streams) and multi-platform libraries for compatibility. For example:
- Use `RamDirectory` for in-memory storage, which doesn't require platform-specific implementations.
- Handle text processing with `SimpleAnalyzer` to avoid complex tokenization dependencies initially.

Modify the classes as needed to pass the ported unit tests, ensuring they work across Android, iOS, desktop (Linux/macOS/Windows), and JVM server environments.

#### Unexpected Detail
An interesting aspect is that using `SimpleAnalyzer` (which tokenizes on non-letter characters and converts to lowercase) instead of `StandardAnalyzer` simplifies the porting process, but it may affect search accuracy compared to Lucene's default, which you might want to enhance in later phases.

For more details on Lucene's structure, refer to the source code at [this GitHub repository](https://github.com/apache/lucene/tree/main/lucene/core/src/java/org/apache/lucene).

---

### Survey Note: Detailed Approach to Porting Lucene to Kotlin Multi-Platform

This section provides a comprehensive guide for porting the Apache Lucene search engine core to Kotlin for multi-platform use, focusing on the user's phase 1 requirements. The process involves identifying essential classes, porting them with multi-platform compatibility, and ensuring functionality through unit tests. The analysis is based on examining Lucene's source code and documentation, with a focus on basic indexing and searching operations.

#### Background and Context
Apache Lucene is a high-performance, full-featured text search engine library written in Java, widely used for indexing and searching large text collections. The user aims to port key packages (`search`, `index`, `store`, `codecs`, `analysis`, `document`, `internal`) to Kotlin, enabling use across Android, iOS, Linux/macOS/Windows desktop via Kotlin/Native, and JVM server apps. The current time is 12:45 AM PST on Friday, March 07, 2025, and the approach is tailored to this multi-platform goal.

#### Phase 1: Identifying Minimum Classes for Indexing and Searching
The first step is to identify the minimum set of classes required for building an index and running a search, as outlined in Lucene tutorials and examples. Based on analysis, the essential classes include:

- **Document Package**: `Document`, `Field`, `TextField`, `StringField`, and `FieldType` for representing searchable data with fields.
- **Index Package**: `IndexWriter`, `IndexReader`, and `Term` for managing index creation and reading.
- **Store Package**: `Directory` (abstract) and `RamDirectory` (in-memory implementation) for storing the index, chosen for cross-platform compatibility.
- **Analysis Package**: `Analyzer` (base class), `SimpleAnalyzer` (for basic tokenization), `LetterTokenizer`, and `LowerCaseFilter` for text processing, selected to minimize dependencies.
- **Search Package**: `IndexSearcher` for searching, `Query` and `TermQuery` for query creation, and `ScoreDoc` and `TopDocs` for holding search results.

This selection focuses on a minimal functional set, using `SimpleAnalyzer` instead of `StandardAnalyzer` to reduce the complexity of porting tokenizers and filters, as `StandardAnalyzer` involves additional dependencies like `StandardTokenizer` and `StandardFilter`.

#### Rationale for Class Selection
The choice of classes was informed by examining basic Lucene examples, such as indexing a document with a text field and searching for a term. For instance, a typical example involves:
- Creating a `Document` with `TextField` for content.
- Using `IndexWriter` with `RamDirectory` and `SimpleAnalyzer` to build the index.
- Searching with `IndexSearcher` and `TermQuery` to retrieve results.

`RamDirectory` was selected for phase 1 due to its in-memory nature, ensuring compatibility across platforms without needing platform-specific file system implementations. This contrasts with `FSDirectory`, which would require `expect/actual` for different operating systems.

#### Porting Strategy and Multi-Platform Compatibility
Porting involves translating Java classes to Kotlin, ensuring use of Kotlin standard libraries (e.g., `kotlin.io` for streams) and multi-platform libraries. Key considerations include:
- **Class Structure**: Maintain the original class hierarchy, such as `Field` as an open class with subclasses like `TextField` and `StringField`, each defining their `FieldType`.
- **Exception Handling**: Replace Java's checked exceptions with Kotlin's exception handling, using `try-catch` where necessary.
- **Platform-Agnostic Code**: Use `RamDirectory` for in-memory storage, avoiding platform-specific file operations initially. For future phases, `Directory` implementations may require `expect/actual` for file system access on different platforms.

For example, the `Field` class in Kotlin might look like:

```kotlin
open class Field(val name: String, val value: String, val type: FieldType) {
    // Methods for field operations
}
```

Similarly, `TextField` would extend `Field` with a predefined `FieldType`:

```kotlin
class TextField(name: String, value: String) : Field(name, value, TYPE) {
    companion object {
        val TYPE = FieldType().apply {
            indexOptions = IndexOptions.TERM_POSITIONS
            tokenized = true
            stored = false
            freeze()
        }
    }
}
```

This ensures compatibility with Kotlin's multi-platform project structure, using `commonMain` for shared code and `commonTest` for unit tests.

#### Unit Tests: Porting and Verification
The user emphasized porting unit tests first to ensure functionality. Relevant tests from Lucene's test-core module include:
- `TestIndexWriter`: Tests for adding documents, such as `testAddDocument`, to verify indexing.
- `TestIndexSearcher`: Tests for searching, ensuring `IndexSearcher` retrieves correct documents.

These tests should be ported to Kotlin, using the same test cases to verify that the ported classes behave as expected. For example, a test might create a `RamDirectory`, add a document with `TextField`, and search for a term using `TermQuery`, checking the results match expectations.

#### Implementation Details and Challenges
Porting involves several challenges, addressed as follows:
- **Dependency Management**: Classes like `SimpleAnalyzer` depend on `LetterTokenizer` and `LowerCaseFilter`, which must be ported to handle token streams. These can use `kotlin.io`'s `Reader` for character streams.
- **Field Storage and Indexing**: Ensuring `TextField` supports both indexing and storing requires proper `FieldType` configuration, which may need adjustments to pass tests.
- **Multi-Platform Considerations**: While `RamDirectory` is platform-agnostic, future phases involving file system operations (e.g., `FSDirectory`) will require platform-specific implementations using `expect/actual`.

An unexpected detail is the choice of `SimpleAnalyzer` over `StandardAnalyzer`, which simplifies phase 1 but may affect search accuracy. `SimpleAnalyzer` tokenizes on non-letter characters and converts to lowercase, potentially missing advanced grammar-based analysis available in `StandardAnalyzer`.

#### Example Workflow
To illustrate, consider a basic workflow:
1. Create a Kotlin multi-platform project with `commonMain` and `commonTest`.
2. Port `Document`, `Field`, `TextField`, etc., ensuring they use Kotlin types.
3. Port a test like `testAddDocument` from `TestIndexWriter`, verifying a document can be added and retrieved.
4. Run the test to ensure functionality, adjusting classes as needed.

This iterative process ensures the ported code is robust and compatible across platforms.

#### Comparative Analysis: Classes and Dependencies
The following table summarizes the classes to port and their dependencies, aiding in tracking the porting process:

| **Package**       | **Class**              | **Dependencies**                          | **Notes**                              |
|-------------------|------------------------|-------------------------------------------|----------------------------------------|
| document          | Document              | Field                                     | Represents searchable unit             |
| document          | Field                 | FieldType                                 | Base class for fields                  |
| document          | TextField             | Field, FieldType                          | Indexed, tokenized field               |
| document          | StringField           | Field, FieldType                          | Not tokenized, for exact matches       |
| index             | IndexWriter           | Directory, IndexWriterConfig              | Handles index creation                 |
| index             | IndexReader           | Directory                                 | Reads the index                        |
| index             | Term                  | None                                      | Represents a term in the index         |
| store             | Directory             | None                                      | Abstract for storage operations        |
| store             | RamDirectory          | Directory                                 | In-memory implementation               |
| analysis          | Analyzer              | None                                      | Base class for text analysis           |
| analysis          | SimpleAnalyzer        | LetterTokenizer, LowerCaseFilter          | Basic tokenizer, simplifies porting    |
| analysis          | LetterTokenizer       | Tokenizer                                 | Tokenizes on letters                   |
| analysis          | LowerCaseFilter       | TokenFilter                               | Converts tokens to lowercase           |
| search            | IndexSearcher         | IndexReader                               | Performs searches                      |
| search            | Query                 | None                                      | Base class for queries                 |
| search            | TermQuery             | Term, Query                               | Queries for specific terms             |
| search            | ScoreDoc              | None                                      | Holds search result score and doc ID   |
| search            | TopDocs               | ScoreDoc                                  | Contains search results                |

This table highlights the interconnected nature of Lucene's classes, guiding the porting order to manage dependencies effectively.

#### Future Phases and Expansion
After phase 1, the user can expand to port additional classes, such as `StandardAnalyzer` for more advanced text analysis, `FSDirectory` for file system storage (requiring platform-specific implementations), and more complex queries like `BooleanQuery`. This iterative approach ensures scalability and maintainability.

#### Conclusion
Porting Lucene to Kotlin for multi-platform use is feasible by starting with a minimal set of classes for indexing and searching, using `SimpleAnalyzer` to simplify phase 1. Porting unit tests first ensures functionality, and leveraging Kotlin's multi-platform features guarantees compatibility across Android, iOS, desktop, and JVM server environments. For detailed source code, refer to [this GitHub repository](https://github.com/apache/lucene/tree/main/lucene/core/src/java/org/apache/lucene).

---

### Key Citations
- [Lucene Core Source Code on GitHub](https://github.com/apache/lucene/tree/main/lucene/core/src/java/org/apache/lucene)