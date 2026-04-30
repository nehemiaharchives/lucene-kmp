# 10.2.0-alpha07

Date: 2026-01-16

## Highlights
- Improved CI reliability and Android build wiring, including task dependency fixes for generated dictionaries and test task naming.
- Upgraded Android Gradle Plugin and refreshed build configuration/dependencies.
- Continued core/search porting with new reader/assertion utilities and similarity test coverage.

## Added
- BibleKoreanAnalyzer integration in `analysis:nori`.
- Bloom filter functionality and related classes/methods.
- Merging and mismatched reader utilities (`MergingCodecReader`, `MergingDirectoryReaderWrapper`, `Mismatched*Reader`, `FieldFilterLeafReader`, `Asserting*Reader/Collector`).
- Similarity tests and infrastructure progress (`TestBM25Similarity`, `TestIndriDirichletSimilarity`, `TestLMDirichletSimilarity`, `BaseSimilarityTestCase` scaffolding).

## Changed
- Android build configuration updates, including AGP upgrade and Gradle task naming adjustments for Android test tasks.
- CI coverage and Android task exclusion lists updated to match renamed tasks.
- Kotlin/Native Linux compilation/test coverage progressed.

## Fixed
- Deterministic stabilization for `ThreadPoolExecutorTest` to reduce CI flakiness.
- Hindi search regression.
- `QueryParser`/`QueryParserBase` handling of nullable `fuzzySlop`.
- Gradle task dependency wiring for generated dictionary data (morfologik, smartcn, nori, kuromoji) and GB2312 mapping generation.
- Missing Android test source configuration in Gradle.

## Notes
- `LuceneTestCase.createTempDir` remains a TODO and should be implemented before relying on temp-dir-heavy tests.
