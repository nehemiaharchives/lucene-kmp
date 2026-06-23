# Release notes - 10.2.0-alpha13

## Overview

This release contains the porting progress and improvements in the kotlin-multiplatform `lucene-kmp`
module since `10.2.0-alpha12`.

The main focus of this release is analyzer coverage: `analysis:common` gained many upstream Lucene
analyzer packages and tests, `analysis:extra` gained many additional language analyzers, and
Bible-specific analyzer behavior was improved through `bbl-kmp` dogfooding.

## Notable Milestones

- Production class porting reached 1825 classes ported among 2390 tracked non-deprecated classes
  (76%), up from 1586 classes (66%) in `10.2.0-alpha12` before the deprecated-class exclusion was
  applied.
- Priority-1 dependency coverage remained effectively complete, with 181/181 tracked priority-1
  dependencies ported and the priority-1 package total moving from 745/748 to 747/748.
- Semantic progress from `PROGRESS2.md` is now 79% for production classes after the expanded
  nested-module scan and deprecated-class exclusion.
- Unit test class porting reached 2790 classes ported among 3558 tracked non-deprecated unit test
  classes (78%), up from 2023 classes (77%) in the alpha12 report.
- Unit test semantic completion from `PROGRESS2.md` is now 80%, with implemented test core methods
  rising from 14947 in the alpha12 report to 18529 after nested analyzer modules were included.
- `analysis:common` now includes broad upstream analyzer support for language analyzers, char filters,
  token filters, factories, synonym handling, shingle filters, n-grams, payloads, compound-word
  handling, and UAX29 URL/email tokenization.
- New generated-data workflows were added for analyzer modules that need vendored lexical data while
  keeping the repository self-contained.
- Documentation was expanded with language coverage tracking, usage documentation, README updates,
  and reusable agent skills.

## Analyzer Coverage

### Upstream `analysis:common` ports

This release added or completed upstream Lucene analyzer families including:

- Arabic, Brazilian, Bulgarian, Catalan, CJK, Classic, Czech, Danish, Estonian, Basque, Finnish,
  Galician, Greek, Hungarian, Irish, Latvian, Lithuanian, Norwegian, Persian, Romanian, Serbian,
  Sorani, and Turkish analyzers.
- Core analyzer components such as `FlattenGraphFilter`, `KeywordAnalyzer`, copied common-module
  `LowerCaseFilter` and `StopFilter`, decimal digit handling, whitespace/token factories, and
  analysis factory registration.
- Char filter support including `HTMLStripCharFilter`, `MappingCharFilterFactory`, and generated
  scanner tables.
- Compound-word support including dictionary and hyphenation token filters plus the hyphenation
  support classes.
- Miscellaneous filters including ASCII folding, capitalization, concatenate graph, fingerprint,
  hyphenated words, keep/protected terms, keyword repeat/marker, length/limit filters, remove
  duplicates, Scandinavian normalization/folding, trim/truncate, type-as-synonym, and word delimiter
  filters.
- N-gram, pattern tokenizer, payload, reverse, shingle, synonym, custom analyzer, and UAX29 URL/email
  tokenizer support.

### Additional language analyzers

The release also added or expanded language analyzers beyond the upstream common analyzer set:

- Amharic and Oromo through the new `analysis:horn` module.
- Hebrew through the new `analysis:hebmorph` module.
- Assamese, Belarusian, Burmese, Cebuano, Haitian Creole, Hausa, Igbo, Ilocano, Javanese, Kannada,
  Khmer, Malayalam, Malay, Odia, Punjabi, Sinhala, Sundanese, Swahili, Tigrinya, Uzbek, and Yoruba.

### Bible-specific analyzers

Bible search dogfooding drove analyzer additions and fixes for:

- English, Spanish, Portuguese, Russian, Telugu, Tagalog, Vietnamese, Ukrainian, Korean, Swedish,
  Bengali, Hindi, Tamil, and Marathi.
- Jesus/Christ and Bible-name normalization across multiple languages, including context-sensitive
  handling for some languages and NT-scoped Ukrainian support.

## Bug Fixes

- Fixed `WhitespaceTokenizer.isTokenChar` behavior.
- Fixed WordDelimiter graph offset test parity and full-UTF8-range char-code handling.
- Restored Java synchronization around the `IndexWriter` try-update doc-values path.
- Fixed `TestStressNRT` flakiness caused by `SegmentDocValues` races and merge-thread lifecycle
  leaks.
- Confirmed and fixed the `TestOperations` hang tracked in issue 189.
- Confirmed that U+2019 RIGHT SINGLE QUOTE does not break `StandardAnalyzer`, closing issue 244.
- Strengthened `TestConcurrentMergeScheduler.testNoExtraFiles` by asserting
  `assertNoUnreferencedFiles`, closing issue 247.
- Fixed duplicate Kotlin/Native KLIB resolver errors in composite `bbl-kmp` builds.
- Fixed Bible analyzer behavior for Telugu, Bengali, Hindi, Tamil, Marathi, Korean, Ukrainian,
  Russian, Vietnamese, and Swedish search cases.

## Build, CI, and Documentation

- Added `analysis:hebmorph` and `analysis:horn` Gradle modules.
- Added generated-data Gradle scripts for Hebrew and Horn analyzer data.
- Vendored required analyzer lexical data under `gradle/` so generators do not depend on sibling
  repositories.
- Updated CI configuration and dependency versions.
- Added core-index iOS test execution scripting and refreshed core-index performance logs.
- Added `docs/USAGE.md`.
- Added `LANGUAGE_COVERAGE.md`.
- Updated `README.md` with Java Lucene, `lucene-kmp`, usage, and Maven Central guidance.
- Added reusable `.agents` skills for porting, progress updates, and native speedup work.

## Progress Summary

### Counting changes

The progress scripts were corrected before this release note was finalized:

- `progressv2.main.kts` now scans nested Gradle modules such as `analysis:common`, `analysis:icu`,
  `analysis:kuromoji`, `analysis:nori`, `analysis:smartcn`, and KMP-only analyzer modules such as
  `analysis:extra`, `analysis:hebmorph`, and `analysis:horn`. Earlier `PROGRESS2.md` runs scanned
  only a flat `analysis` path, so many analyzer classes and tests were missing from the semantic
  totals.
- `progress.main.kts` and `progressv2.main.kts` now exclude upstream Java Lucene classes and tests
  whose top-level class is annotated `@Deprecated`. These are no longer counted as classes that
  should be ported.

Because of those fixes, the alpha13 progress numbers are not a pure apples-to-apples denominator
comparison against alpha12. The class denominator is larger in `PROGRESS2.md` because nested analyzer
modules are now included, while some upstream deprecated classes were removed from the port-required
set.

From `PROGRESS.md`:

- Priority-1 package coverage: 747/748 classes (99%), up from 745/748.
- Priority-1 API dependencies: 181/181 complete (100%).
- All tracked non-deprecated class coverage: 1825/2390 classes (76%), up from 1586/2399 (66%) in
  the alpha12 report before deprecated-class filtering.
- `analysis:common` package families completed during this release include Arabic, Bulgarian,
  Brazilian, CJK, Classic, CommonGrams, Compound, Core, Custom, Czech, Danish, Greek, Email,
  Finnish, Galician, Hungarian, Latvian, Lithuanian, Miscellaneous, NGram, Norwegian, Payloads,
  Reverse, Romanian, Shingle, Serbian, Synonym, and Turkish.

From `PROGRESS2.md`:

- Total production class universe is now 2430 classes, with 1854 ported (76%).
- Production semantic completion is now 79% after including nested analyzer modules.
- Total unit test class universe is now 3558 classes, with 2790 ported (78%).
- Unit test semantic completion is now 80%.
- Implemented test core methods rose from 14947 in the alpha12 report to 18529.

## Representative Newly Ported Areas

This release added several hundred files. Representative new areas include:

- `analysis/common/src/commonMain/kotlin/org/gnit/lucenekmp/analysis/*`
- `analysis/common/src/commonTest/kotlin/org/gnit/lucenekmp/analysis/*`
- `analysis/extra/src/commonMain/kotlin/org/gnit/lucenekmp/analysis/*`
- `analysis/extra/src/commonTest/kotlin/org/gnit/lucenekmp/analysis/*`
- `analysis/hebmorph/`
- `analysis/horn/`
- `gradle/generateHebMorphData.gradle.kts`
- `gradle/generateHornData.gradle.kts`
- `LANGUAGE_COVERAGE.md`
- `docs/USAGE.md`
