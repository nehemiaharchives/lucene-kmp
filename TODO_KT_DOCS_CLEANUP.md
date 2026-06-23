# KDoc Cleanup: Replace `org.apache.lucene` → `org.gnit.lucenekmp`

> Only replaces occurrences in KDoc (`/** */`) and documentation-style comments (`/* */`, `//`).
> Does NOT touch: string literals (system props, usage examples), `@Ported` annotations, commented-out code/annotations, actual code references.

## Module: core

- [x] core/.../analysis/Analyzer.kt
- [x] core/.../analysis/CharArrayMap.kt
- [x] core/.../analysis/CharFilter.kt
- [x] core/.../analysis/standard/StandardTokenizer.kt
- [x] core/.../analysis/tokenattributes/PayloadAttribute.kt
- [x] core/.../analysis/tokenattributes/PositionIncrementAttribute.kt
- [x] core/.../codecs/PostingsReaderBase.kt
- [x] core/.../codecs/bloom/BloomFilteringPostingsFormat.kt
- [x] core/.../codecs/lucene101/Lucene101Codec.kt
- [x] core/.../codecs/lucene101/Lucene101PostingsFormat.kt
- [x] core/.../codecs/lucene90/blocktree/Lucene90BlockTreeTermsReader.kt
- [x] core/.../codecs/lucene94/Lucene94FieldInfosFormat.kt
- [x] core/.../codecs/lucene95/OrdToDocDISIReaderConfiguration.kt
- [x] core/.../codecs/lucene99/Lucene99FlatVectorsFormat.kt
- [x] core/.../document/DateTools.kt
- [x] core/.../document/Document.kt
- [x] core/.../document/DoubleDocValuesField.kt
- [x] core/.../document/FeatureField.kt
- [x] core/.../document/FloatDocValuesField.kt
- [x] core/.../document/SortedNumericDocValuesField.kt
- [x] core/.../index/QueryTimeout.kt
- [x] core/.../internal/tests/ConcurrentMergeSchedulerAccess.kt
- [x] core/.../internal/tests/FilterIndexInputAccess.kt
- [x] core/.../internal/tests/IndexPackageAccess.kt
- [x] core/.../internal/tests/SegmentReaderAccess.kt
- [x] core/.../internal/vectorization/VectorUtilSupport.kt
- [x] core/.../jdkport/DecimalFormat.kt
- [x] core/.../search/AutomatonQuery.kt
- [x] core/.../search/ByteVectorSimilarityValuesSource.kt
- [x] core/.../search/FloatVectorSimilarityValuesSource.kt
- [x] core/.../search/IndexOrDocValuesQuery.kt
- [x] core/.../search/MultiTermQuery.kt
- [x] core/.../search/RegexpQuery.kt
- [x] core/.../search/TermInSetQuery.kt
- [x] core/.../search/TimeLimitingBulkScorer.kt
- [x] core/.../search/VectorSimilarityValuesSource.kt
- [x] core/.../search/Weight.kt
- [x] core/.../search/comparators/TermOrdValComparator.kt
- [x] core/.../search/similarities/BM25Similarity.kt
- [x] core/.../search/similarities/Similarity.kt
- [x] core/.../search/similarities/TFIDFSimilarity.kt
- [x] core/.../util/AttributeImpl.kt
- [x] core/.../util/AttributeReflector.kt
- [x] core/.../util/fst/FST.kt
- [x] core/.../util/fst/FSTCompiler.kt
- [x] core/.../util/fst/Util.kt

### core tests

- [x] core/.../search/similarities/TestSimilarityBase.kt

## Module: analysis/common

- [x] analysis/common/.../classic/ClassicTokenizer.kt
- [x] analysis/common/.../core/LowerCaseFilter.kt
- [x] analysis/common/.../core/StopFilter.kt
- [x] analysis/common/.../miscellaneous/ConcatenateGraphFilter.kt

### analysis/common tests

- [x] analysis/common/.../email/TestUAX29URLEmailTokenizerFactory.kt

## Module: analysis/kuromoji

- [x] analysis/kuromoji/.../JapaneseBaseFormFilterFactory.kt
- [x] analysis/kuromoji/.../JapanesePartOfSpeechStopFilterFactory.kt
- [x] analysis/kuromoji/.../JapaneseReadingFormFilterFactory.kt
- [x] analysis/kuromoji/.../JapaneseTokenizerFactory.kt

## Module: codecs

- [x] codecs/.../blocktreeords/OrdsFieldReader.kt
- [x] codecs/.../blocktreeords/OrdsIntersectTermsEnum.kt
- [x] codecs/.../blocktreeords/OrdsSegmentTermsEnum.kt
- [x] codecs/.../blocktreeords/OrdsSegmentTermsEnumFrame.kt

## Module: queryparser

- [x] queryparser/.../classic/QueryParser.kt
- [x] queryparser/.../flexible/standard/CommonQueryParserConfiguration.kt
