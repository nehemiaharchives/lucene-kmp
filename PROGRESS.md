# Lucene KMP Port Progress
## Package statistics (priority‑1 deps)
| Java package | KMP mapped | Classes | Ported | % | Done |
| --- | --- | --- | --- | --- | --- |
| org.apache.lucene |     org.gnit.lucenekmp | 714 | 645 | 90% | [ ] |
|   org.apache.lucene.analysis |       org.gnit.lucenekmp.analysis | 14 | 14 | 100% | [x] |
|     org.apache.lucene.analysis.standard |         org.gnit.lucenekmp.analysis.standard | 3 | 3 | 100% | [x] |
|     org.apache.lucene.analysis.tokenattributes |         org.gnit.lucenekmp.analysis.tokenattributes | 11 | 11 | 100% | [x] |
|   org.apache.lucene.codecs |       org.gnit.lucenekmp.codecs | 35 | 35 | 100% | [x] |
|     org.apache.lucene.codecs.compressing |         org.gnit.lucenekmp.codecs.compressing | 4 | 4 | 100% | [x] |
|     org.apache.lucene.codecs.hnsw |         org.gnit.lucenekmp.codecs.hnsw | 4 | 4 | 100% | [x] |
|     org.apache.lucene.codecs.lucene101 |         org.gnit.lucenekmp.codecs.lucene101 | 7 | 7 | 100% | [x] |
|       org.apache.lucene.codecs.lucene90.blocktree |           org.gnit.lucenekmp.codecs.lucene90.blocktree | 9 | 9 | 100% | [x] |
|       org.apache.lucene.codecs.lucene90.compressing |           org.gnit.lucenekmp.codecs.lucene90.compressing | 10 | 10 | 100% | [x] |
|     org.apache.lucene.codecs.perfield |         org.gnit.lucenekmp.codecs.perfield | 3 | 3 | 100% | [x] |
|   org.apache.lucene.document |       org.gnit.lucenekmp.document | 29 | 29 | 100% | [x] |
|   org.apache.lucene.geo |       org.gnit.lucenekmp.geo | 26 | 26 | 100% | [x] |
|   org.apache.lucene.index |       org.gnit.lucenekmp.index | 169 | 123 | 72% | [ ] |
|     org.apache.lucene.internal.hppc |         org.gnit.lucenekmp.internal.hppc | 14 | 14 | 100% | [x] |
|     org.apache.lucene.internal.tests |         org.gnit.lucenekmp.internal.tests | 2 | 2 | 100% | [x] |
|     org.apache.lucene.internal.vectorization |         org.gnit.lucenekmp.internal.vectorization | 5 | 5 | 100% | [x] |
|   org.apache.lucene.search |       org.gnit.lucenekmp.search | 138 | 115 | 83% | [ ] |
|     org.apache.lucene.search.comparators |         org.gnit.lucenekmp.search.comparators | 8 | 8 | 100% | [x] |
|     org.apache.lucene.search.knn |         org.gnit.lucenekmp.search.knn | 4 | 4 | 100% | [x] |
|     org.apache.lucene.search.similarities |         org.gnit.lucenekmp.search.similarities | 2 | 2 | 100% | [x] |
|   org.apache.lucene.store |       org.gnit.lucenekmp.store | 37 | 37 | 100% | [x] |
|   org.apache.lucene.util |       org.gnit.lucenekmp.util | 86 | 86 | 100% | [x] |
|     org.apache.lucene.util.automaton |         org.gnit.lucenekmp.util.automaton | 20 | 20 | 100% | [x] |
|     org.apache.lucene.util.bkd |         org.gnit.lucenekmp.util.bkd | 1 | 1 | 100% | [x] |
|     org.apache.lucene.util.compress |         org.gnit.lucenekmp.util.compress | 2 | 2 | 100% | [x] |
|     org.apache.lucene.util.fst |         org.gnit.lucenekmp.util.fst | 17 | 17 | 100% | [x] |
|     org.apache.lucene.util.hnsw |         org.gnit.lucenekmp.util.hnsw | 7 | 7 | 100% | [x] |
|     org.apache.lucene.util.packed |         org.gnit.lucenekmp.util.packed | 47 | 47 | 100% | [x] |


## Priority-1 API progress
| Java class | Mapped class | Java Deps | KMP Deps Ported | KMP Deps To Port | % | Done |
| --- | --- | --- | --- | --- | --- | --- |
| org.apache.lucene.index.IndexWriter | org.gnit.lucenekmp.index.IndexWriter | 78 | 65 | 13 | 83% | [ ] |
| org.apache.lucene.index.IndexWriterConfig | org.gnit.lucenekmp.index.IndexWriterConfig | 15 | 11 | 4 | 73% | [ ] |
| org.apache.lucene.store.FSDirectory | org.gnit.lucenekmp.store.FSDirectory | 8 | 8 | 0 | 100% | [x] |
| org.apache.lucene.analysis.Analyzer | org.gnit.lucenekmp.analysis.Analyzer | 10 | 10 | 0 | 100% | [x] |
| org.apache.lucene.document.Document | org.gnit.lucenekmp.document.Document | 2 | 2 | 0 | 100% | [x] |
| org.apache.lucene.document.Field | org.gnit.lucenekmp.document.Field | 12 | 12 | 0 | 100% | [x] |
| org.apache.lucene.document.IntPoint | org.gnit.lucenekmp.document.IntPoint | 6 | 6 | 0 | 100% | [x] |
| org.apache.lucene.document.StoredField | org.gnit.lucenekmp.document.StoredField | 3 | 3 | 0 | 100% | [x] |
| org.apache.lucene.document.TextField | org.gnit.lucenekmp.document.TextField | 4 | 4 | 0 | 100% | [x] |
| org.apache.lucene.index.DirectoryReader | org.gnit.lucenekmp.index.DirectoryReader | 6 | 6 | 0 | 100% | [x] |
| org.apache.lucene.index.StandardDirectoryReader | org.gnit.lucenekmp.index.StandardDirectoryReader | 13 | 13 | 0 | 100% | [x] |
| org.apache.lucene.queryparser.classic.QueryParser | org.gnit.lucenekmp.queryparser.classic.QueryParser | 0 | 0 | 0 | 100% | [ ] |
| org.apache.lucene.search.IndexSearcher | org.gnit.lucenekmp.search.IndexSearcher | 42 | 33 | 9 | 78% | [ ] |
| org.apache.lucene.store.FSLockFactory | org.gnit.lucenekmp.store.FSLockFactory | 4 | 4 | 0 | 100% | [ ] |
| org.apache.lucene.store.NIOFSDirectory | org.gnit.lucenekmp.store.NIOFSDirectory | 5 | 5 | 0 | 100% | [x] |
| org.apache.lucene.document.IntPoint | org.gnit.lucenekmp.document.IntPoint | 6 | 6 | 0 | 100% | [x] |
| org.apache.lucene.search.Query | org.gnit.lucenekmp.search.Query | 3 | 3 | 0 | 100% | [ ] |
| org.apache.lucene.search.BooleanQuery | org.gnit.lucenekmp.search.BooleanQuery | 11 | 11 | 0 | 100% | [x] |
| org.apache.lucene.search.BooleanClause | org.gnit.lucenekmp.search.BooleanClause | 0 | 0 | 0 | 100% | [ ] |
| org.apache.lucene.search.Sort | org.gnit.lucenekmp.search.Sort | 0 | 0 | 0 | 100% | [x] |
| org.apache.lucene.search.SortField | org.gnit.lucenekmp.search.SortField | 19 | 19 | 0 | 100% | [x] |
| TOTAL |  | 171 | 147 | 24 | 85% | [ ] |

## KMP Deps To Port
| Java FQN | Expected KMP FQN |
| --- | --- |
| org.apache.lucene.index.CheckIndex | org.gnit.lucenekmp.index.CheckIndex |
| org.apache.lucene.index.ConcurrentApproximatePriorityQueue | org.gnit.lucenekmp.index.ConcurrentApproximatePriorityQueue |
| org.apache.lucene.index.ConcurrentMergeScheduler | org.gnit.lucenekmp.index.ConcurrentMergeScheduler |
| org.apache.lucene.index.DocumentsWriter | org.gnit.lucenekmp.index.DocumentsWriter |
| org.apache.lucene.index.DocumentsWriterDeleteQueue | org.gnit.lucenekmp.index.DocumentsWriterDeleteQueue |
| org.apache.lucene.index.DocumentsWriterFlushControl | org.gnit.lucenekmp.index.DocumentsWriterFlushControl |
| org.apache.lucene.index.DocumentsWriterFlushQueue | org.gnit.lucenekmp.index.DocumentsWriterFlushQueue |
| org.apache.lucene.index.DocumentsWriterPerThread | org.gnit.lucenekmp.index.DocumentsWriterPerThread |
| org.apache.lucene.index.DocumentsWriterPerThreadPool | org.gnit.lucenekmp.index.DocumentsWriterPerThreadPool |
| org.apache.lucene.index.DocumentsWriterStallControl | org.gnit.lucenekmp.index.DocumentsWriterStallControl |
| org.apache.lucene.index.FilterMergePolicy | org.gnit.lucenekmp.index.FilterMergePolicy |
| org.apache.lucene.index.FlushByRamOrCountsPolicy | org.gnit.lucenekmp.index.FlushByRamOrCountsPolicy |
| org.apache.lucene.index.FlushPolicy | org.gnit.lucenekmp.index.FlushPolicy |
| org.apache.lucene.index.FreqProxTermsWriter | org.gnit.lucenekmp.index.FreqProxTermsWriter |
| org.apache.lucene.index.IndexDeletionPolicy | org.gnit.lucenekmp.index.IndexDeletionPolicy |
| org.apache.lucene.index.IndexFileDeleter | org.gnit.lucenekmp.index.IndexFileDeleter |
| org.apache.lucene.index.IndexWriterConfig | org.gnit.lucenekmp.index.IndexWriterConfig |
| org.apache.lucene.index.IndexWriterEventListener | org.gnit.lucenekmp.index.IndexWriterEventListener |
| org.apache.lucene.index.IndexingChain | org.gnit.lucenekmp.index.IndexingChain |
| org.apache.lucene.index.KeepOnlyLastCommitDeletionPolicy | org.gnit.lucenekmp.index.KeepOnlyLastCommitDeletionPolicy |
| org.apache.lucene.index.LockableConcurrentApproximatePriorityQueue | org.gnit.lucenekmp.index.LockableConcurrentApproximatePriorityQueue |
| org.apache.lucene.index.MergeRateLimiter | org.gnit.lucenekmp.index.MergeRateLimiter |
| org.apache.lucene.index.MergeScheduler | org.gnit.lucenekmp.index.MergeScheduler |
| org.apache.lucene.index.MultiBits | org.gnit.lucenekmp.index.MultiBits |
| org.apache.lucene.index.MultiDocValues | org.gnit.lucenekmp.index.MultiDocValues |
| org.apache.lucene.index.MultiReader | org.gnit.lucenekmp.index.MultiReader |
| org.apache.lucene.index.NormValuesWriter | org.gnit.lucenekmp.index.NormValuesWriter |
| org.apache.lucene.index.NumericDocValuesWriter | org.gnit.lucenekmp.index.NumericDocValuesWriter |
| org.apache.lucene.index.OneMergeWrappingMergePolicy | org.gnit.lucenekmp.index.OneMergeWrappingMergePolicy |
| org.apache.lucene.index.PendingSoftDeletes | org.gnit.lucenekmp.index.PendingSoftDeletes |
| org.apache.lucene.index.PointValuesWriter | org.gnit.lucenekmp.index.PointValuesWriter |
| org.apache.lucene.index.ReaderPool | org.gnit.lucenekmp.index.ReaderPool |
| org.apache.lucene.index.SegmentMerger | org.gnit.lucenekmp.index.SegmentMerger |
| org.apache.lucene.index.SlowCompositeCodecReaderWrapper | org.gnit.lucenekmp.index.SlowCompositeCodecReaderWrapper |
| org.apache.lucene.index.SortedDocValuesWriter | org.gnit.lucenekmp.index.SortedDocValuesWriter |
| org.apache.lucene.index.SortedNumericDocValuesWriter | org.gnit.lucenekmp.index.SortedNumericDocValuesWriter |
| org.apache.lucene.index.SortedSetDocValuesWriter | org.gnit.lucenekmp.index.SortedSetDocValuesWriter |
| org.apache.lucene.index.SortingCodecReader | org.gnit.lucenekmp.index.SortingCodecReader |
| org.apache.lucene.index.SortingStoredFieldsConsumer | org.gnit.lucenekmp.index.SortingStoredFieldsConsumer |
| org.apache.lucene.index.SortingTermVectorsConsumer | org.gnit.lucenekmp.index.SortingTermVectorsConsumer |
| org.apache.lucene.index.StoredFieldsConsumer | org.gnit.lucenekmp.index.StoredFieldsConsumer |
| org.apache.lucene.index.TermVectorsConsumer | org.gnit.lucenekmp.index.TermVectorsConsumer |
| org.apache.lucene.index.TermVectorsConsumerPerField | org.gnit.lucenekmp.index.TermVectorsConsumerPerField |
| org.apache.lucene.index.TieredMergePolicy | org.gnit.lucenekmp.index.TieredMergePolicy |
| org.apache.lucene.index.TrackingTmpOutputDirectoryWrapper | org.gnit.lucenekmp.index.TrackingTmpOutputDirectoryWrapper |
| org.apache.lucene.index.VectorValuesConsumer | org.gnit.lucenekmp.index.VectorValuesConsumer |
| org.apache.lucene.search.CollectorManager | org.gnit.lucenekmp.search.CollectorManager |
| org.apache.lucene.search.ExactPhraseMatcher | org.gnit.lucenekmp.search.ExactPhraseMatcher |
| org.apache.lucene.search.FieldValueHitQueue | org.gnit.lucenekmp.search.FieldValueHitQueue |
| org.apache.lucene.search.LRUQueryCache | org.gnit.lucenekmp.search.LRUQueryCache |
| org.apache.lucene.search.MaxScoreAccumulator | org.gnit.lucenekmp.search.MaxScoreAccumulator |
| org.apache.lucene.search.MultiLeafFieldComparator | org.gnit.lucenekmp.search.MultiLeafFieldComparator |
| org.apache.lucene.search.PhraseMatcher | org.gnit.lucenekmp.search.PhraseMatcher |
| org.apache.lucene.search.PhrasePositions | org.gnit.lucenekmp.search.PhrasePositions |
| org.apache.lucene.search.PhraseQuery | org.gnit.lucenekmp.search.PhraseQuery |
| org.apache.lucene.search.PhraseQueue | org.gnit.lucenekmp.search.PhraseQueue |
| org.apache.lucene.search.PhraseScorer | org.gnit.lucenekmp.search.PhraseScorer |
| org.apache.lucene.search.PhraseWeight | org.gnit.lucenekmp.search.PhraseWeight |
| org.apache.lucene.search.ScoreCachingWrappingScorer | org.gnit.lucenekmp.search.ScoreCachingWrappingScorer |
| org.apache.lucene.search.SloppyPhraseMatcher | org.gnit.lucenekmp.search.SloppyPhraseMatcher |
| org.apache.lucene.search.TermInSetQuery | org.gnit.lucenekmp.search.TermInSetQuery |
| org.apache.lucene.search.TimeLimitingBulkScorer | org.gnit.lucenekmp.search.TimeLimitingBulkScorer |
| org.apache.lucene.search.TopFieldCollector | org.gnit.lucenekmp.search.TopFieldCollector |
| org.apache.lucene.search.TopFieldCollectorManager | org.gnit.lucenekmp.search.TopFieldCollectorManager |
| org.apache.lucene.search.TopScoreDocCollector | org.gnit.lucenekmp.search.TopScoreDocCollector |
| org.apache.lucene.search.TopScoreDocCollectorManager | org.gnit.lucenekmp.search.TopScoreDocCollectorManager |
| org.apache.lucene.search.TotalHitCountCollector | org.gnit.lucenekmp.search.TotalHitCountCollector |
| org.apache.lucene.search.TotalHitCountCollectorManager | org.gnit.lucenekmp.search.TotalHitCountCollectorManager |
| org.apache.lucene.search.UsageTrackingQueryCachingPolicy | org.gnit.lucenekmp.search.UsageTrackingQueryCachingPolicy |


## Test Classes Port Progress
| Subpackage | Count | Ported | % |
| --- | --- | --- | --- |
| org.apache.lucene | 6 | 0 | 0% |
|   org.apache.lucene.analysis | 15 | 0 | 0% |
|     org.apache.lucene.analysis.standard | 2 | 0 | 0% |
|     org.apache.lucene.analysis.tokenattributes | 4 | 0 | 0% |
|   org.apache.lucene.codecs | 4 | 0 | 0% |
|     org.apache.lucene.codecs.compressing | 5 | 0 | 0% |
|     org.apache.lucene.codecs.hnsw | 1 | 0 | 0% |
|     org.apache.lucene.codecs.lucene101 | 6 | 0 | 0% |
|     org.apache.lucene.codecs.lucene102 | 2 | 0 | 0% |
|     org.apache.lucene.codecs.lucene90 | 14 | 0 | 0% |
|       org.apache.lucene.codecs.lucene90.blocktree | 1 | 0 | 0% |
|       org.apache.lucene.codecs.lucene90.compressing | 3 | 0 | 0% |
|     org.apache.lucene.codecs.lucene94 | 1 | 0 | 0% |
|     org.apache.lucene.codecs.lucene99 | 6 | 0 | 0% |
|     org.apache.lucene.codecs.perfield | 4 | 0 | 0% |
|   org.apache.lucene.document | 50 | 0 | 0% |
|   org.apache.lucene.geo | 17 | 0 | 0% |
|   org.apache.lucene.index | 197 | 0 | 0% |
|     org.apache.lucene.internal.hppc | 15 | 0 | 0% |
|     org.apache.lucene.internal.tests | 1 | 0 | 0% |
|     org.apache.lucene.internal.vectorization | 4 | 0 | 0% |
|   org.apache.lucene.search | 149 | 0 | 0% |
|     org.apache.lucene.search.knn | 1 | 0 | 0% |
|     org.apache.lucene.search.similarities | 25 | 0 | 0% |
|   org.apache.lucene.store | 27 | 0 | 0% |
|   org.apache.lucene.util | 63 | 4 | 6% |
|     org.apache.lucene.util.automaton | 15 | 0 | 0% |
|     org.apache.lucene.util.bkd | 8 | 0 | 0% |
|     org.apache.lucene.util.compress | 3 | 0 | 0% |
|     org.apache.lucene.util.fst | 8 | 0 | 0% |
|     org.apache.lucene.util.graph | 1 | 0 | 0% |
|     org.apache.lucene.util.hnsw | 8 | 0 | 0% |
|     org.apache.lucene.util.mutable | 1 | 0 | 0% |
|     org.apache.lucene.util.packed | 3 | 0 | 0% |
|     org.apache.lucene.util.quantization | 3 | 0 | 0% |
| Total | 673 | 4 | 0% |


## Tests To Port
| Java Test FQN | Kotlin Test FQN |
| --- | --- |
| org.apache.lucene.TestAssertions | org.gnit.lucenekmp.TestAssertions |
| org.apache.lucene.TestDemo | org.gnit.lucenekmp.TestDemo |
| org.apache.lucene.TestExternalCodecs | org.gnit.lucenekmp.TestExternalCodecs |
| org.apache.lucene.TestMergeSchedulerExternal | org.gnit.lucenekmp.TestMergeSchedulerExternal |
| org.apache.lucene.TestSearch | org.gnit.lucenekmp.TestSearch |
| org.apache.lucene.TestSearchForDuplicates | org.gnit.lucenekmp.TestSearchForDuplicates |
| org.apache.lucene.analysis.TestAbstractAnalysisFactory | org.gnit.lucenekmp.analysis.TestAbstractAnalysisFactory |
| org.apache.lucene.analysis.TestAnalysisSPILoader | org.gnit.lucenekmp.analysis.TestAnalysisSPILoader |
| org.apache.lucene.analysis.TestAnalyzerWrapper | org.gnit.lucenekmp.analysis.TestAnalyzerWrapper |
| org.apache.lucene.analysis.TestAutomatonToTokenStream | org.gnit.lucenekmp.analysis.TestAutomatonToTokenStream |
| org.apache.lucene.analysis.TestCachingTokenFilter | org.gnit.lucenekmp.analysis.TestCachingTokenFilter |
| org.apache.lucene.analysis.TestCharArrayMap | org.gnit.lucenekmp.analysis.TestCharArrayMap |
| org.apache.lucene.analysis.TestCharArraySet | org.gnit.lucenekmp.analysis.TestCharArraySet |
| org.apache.lucene.analysis.TestCharFilter | org.gnit.lucenekmp.analysis.TestCharFilter |
| org.apache.lucene.analysis.TestCharacterUtils | org.gnit.lucenekmp.analysis.TestCharacterUtils |
| org.apache.lucene.analysis.TestDelegatingAnalyzerWrapper | org.gnit.lucenekmp.analysis.TestDelegatingAnalyzerWrapper |
| org.apache.lucene.analysis.TestGraphTokenFilter | org.gnit.lucenekmp.analysis.TestGraphTokenFilter |
| org.apache.lucene.analysis.TestGraphTokenizers | org.gnit.lucenekmp.analysis.TestGraphTokenizers |
| org.apache.lucene.analysis.TestReusableStringReader | org.gnit.lucenekmp.analysis.TestReusableStringReader |
| org.apache.lucene.analysis.TestStopFilter | org.gnit.lucenekmp.analysis.TestStopFilter |
| org.apache.lucene.analysis.TestWordlistLoader | org.gnit.lucenekmp.analysis.TestWordlistLoader |
| org.apache.lucene.analysis.standard.TestStandardAnalyzer | org.gnit.lucenekmp.analysis.standard.TestStandardAnalyzer |
| org.apache.lucene.analysis.standard.TestStandardFactories | org.gnit.lucenekmp.analysis.standard.TestStandardFactories |
| org.apache.lucene.analysis.tokenattributes.TestBytesRefAttImpl | org.gnit.lucenekmp.analysis.tokenattributes.TestBytesRefAttImpl |
| org.apache.lucene.analysis.tokenattributes.TestCharTermAttributeImpl | org.gnit.lucenekmp.analysis.tokenattributes.TestCharTermAttributeImpl |
| org.apache.lucene.analysis.tokenattributes.TestPackedTokenAttributeImpl | org.gnit.lucenekmp.analysis.tokenattributes.TestPackedTokenAttributeImpl |
| org.apache.lucene.analysis.tokenattributes.TestSimpleAttributeImpl | org.gnit.lucenekmp.analysis.tokenattributes.TestSimpleAttributeImpl |
| org.apache.lucene.codecs.TestCodecLoadingDeadlock | org.gnit.lucenekmp.codecs.TestCodecLoadingDeadlock |
| org.apache.lucene.codecs.TestCodecUtil | org.gnit.lucenekmp.codecs.TestCodecUtil |
| org.apache.lucene.codecs.TestCompetitiveFreqNormAccumulator | org.gnit.lucenekmp.codecs.TestCompetitiveFreqNormAccumulator |
| org.apache.lucene.codecs.TestMinimalCodec | org.gnit.lucenekmp.codecs.TestMinimalCodec |
| org.apache.lucene.codecs.compressing.TestDeflateWithPresetDictCompressionMode | org.gnit.lucenekmp.codecs.compressing.TestDeflateWithPresetDictCompressionMode |
| org.apache.lucene.codecs.compressing.TestFastCompressionMode | org.gnit.lucenekmp.codecs.compressing.TestFastCompressionMode |
| org.apache.lucene.codecs.compressing.TestFastDecompressionMode | org.gnit.lucenekmp.codecs.compressing.TestFastDecompressionMode |
| org.apache.lucene.codecs.compressing.TestHighCompressionMode | org.gnit.lucenekmp.codecs.compressing.TestHighCompressionMode |
| org.apache.lucene.codecs.compressing.TestLZ4WithPresetDictCompressionMode | org.gnit.lucenekmp.codecs.compressing.TestLZ4WithPresetDictCompressionMode |
| org.apache.lucene.codecs.hnsw.TestFlatVectorScorer | org.gnit.lucenekmp.codecs.hnsw.TestFlatVectorScorer |
| org.apache.lucene.codecs.lucene101.TestForDeltaUtil | org.gnit.lucenekmp.codecs.lucene101.TestForDeltaUtil |
| org.apache.lucene.codecs.lucene101.TestForUtil | org.gnit.lucenekmp.codecs.lucene101.TestForUtil |
| org.apache.lucene.codecs.lucene101.TestLucene101PostingsFormat | org.gnit.lucenekmp.codecs.lucene101.TestLucene101PostingsFormat |
| org.apache.lucene.codecs.lucene101.TestLucene101PostingsFormatV0 | org.gnit.lucenekmp.codecs.lucene101.TestLucene101PostingsFormatV0 |
| org.apache.lucene.codecs.lucene101.TestPForUtil | org.gnit.lucenekmp.codecs.lucene101.TestPForUtil |
| org.apache.lucene.codecs.lucene101.TestPostingsUtil | org.gnit.lucenekmp.codecs.lucene101.TestPostingsUtil |
| org.apache.lucene.codecs.lucene102.TestLucene102BinaryQuantizedVectorsFormat | org.gnit.lucenekmp.codecs.lucene102.TestLucene102BinaryQuantizedVectorsFormat |
| org.apache.lucene.codecs.lucene102.TestLucene102HnswBinaryQuantizedVectorsFormat | org.gnit.lucenekmp.codecs.lucene102.TestLucene102HnswBinaryQuantizedVectorsFormat |
| org.apache.lucene.codecs.lucene90.TestIndexedDISI | org.gnit.lucenekmp.codecs.lucene90.TestIndexedDISI |
| org.apache.lucene.codecs.lucene90.TestLucene90CompoundFormat | org.gnit.lucenekmp.codecs.lucene90.TestLucene90CompoundFormat |
| org.apache.lucene.codecs.lucene90.TestLucene90DocValuesFormat | org.gnit.lucenekmp.codecs.lucene90.TestLucene90DocValuesFormat |
| org.apache.lucene.codecs.lucene90.TestLucene90DocValuesFormatMergeInstance | org.gnit.lucenekmp.codecs.lucene90.TestLucene90DocValuesFormatMergeInstance |
| org.apache.lucene.codecs.lucene90.TestLucene90DocValuesFormatVariableSkipInterval | org.gnit.lucenekmp.codecs.lucene90.TestLucene90DocValuesFormatVariableSkipInterval |
| org.apache.lucene.codecs.lucene90.TestLucene90FieldInfosFormat | org.gnit.lucenekmp.codecs.lucene90.TestLucene90FieldInfosFormat |
| org.apache.lucene.codecs.lucene90.TestLucene90LiveDocsFormat | org.gnit.lucenekmp.codecs.lucene90.TestLucene90LiveDocsFormat |
| org.apache.lucene.codecs.lucene90.TestLucene90NormsFormat | org.gnit.lucenekmp.codecs.lucene90.TestLucene90NormsFormat |
| org.apache.lucene.codecs.lucene90.TestLucene90NormsFormatMergeInstance | org.gnit.lucenekmp.codecs.lucene90.TestLucene90NormsFormatMergeInstance |
| org.apache.lucene.codecs.lucene90.TestLucene90PointsFormat | org.gnit.lucenekmp.codecs.lucene90.TestLucene90PointsFormat |
| org.apache.lucene.codecs.lucene90.TestLucene90StoredFieldsFormat | org.gnit.lucenekmp.codecs.lucene90.TestLucene90StoredFieldsFormat |
| org.apache.lucene.codecs.lucene90.TestLucene90StoredFieldsFormatHighCompression | org.gnit.lucenekmp.codecs.lucene90.TestLucene90StoredFieldsFormatHighCompression |
| org.apache.lucene.codecs.lucene90.TestLucene90StoredFieldsFormatMergeInstance | org.gnit.lucenekmp.codecs.lucene90.TestLucene90StoredFieldsFormatMergeInstance |
| org.apache.lucene.codecs.lucene90.TestLucene90TermVectorsFormat | org.gnit.lucenekmp.codecs.lucene90.TestLucene90TermVectorsFormat |
| org.apache.lucene.codecs.lucene90.blocktree.TestMSBVLong | org.gnit.lucenekmp.codecs.lucene90.blocktree.TestMSBVLong |
| org.apache.lucene.codecs.lucene90.compressing.TestCompressingStoredFieldsFormat | org.gnit.lucenekmp.codecs.lucene90.compressing.TestCompressingStoredFieldsFormat |
| org.apache.lucene.codecs.lucene90.compressing.TestCompressingTermVectorsFormat | org.gnit.lucenekmp.codecs.lucene90.compressing.TestCompressingTermVectorsFormat |
| org.apache.lucene.codecs.lucene90.compressing.TestStoredFieldsInt | org.gnit.lucenekmp.codecs.lucene90.compressing.TestStoredFieldsInt |
| org.apache.lucene.codecs.lucene94.TestLucene94FieldInfosFormat | org.gnit.lucenekmp.codecs.lucene94.TestLucene94FieldInfosFormat |
| org.apache.lucene.codecs.lucene99.TestLucene99HnswQuantizedVectorsFormat | org.gnit.lucenekmp.codecs.lucene99.TestLucene99HnswQuantizedVectorsFormat |
| org.apache.lucene.codecs.lucene99.TestLucene99HnswVectorsFormat | org.gnit.lucenekmp.codecs.lucene99.TestLucene99HnswVectorsFormat |
| org.apache.lucene.codecs.lucene99.TestLucene99ScalarQuantizedVectorScorer | org.gnit.lucenekmp.codecs.lucene99.TestLucene99ScalarQuantizedVectorScorer |
| org.apache.lucene.codecs.lucene99.TestLucene99ScalarQuantizedVectorsFormat | org.gnit.lucenekmp.codecs.lucene99.TestLucene99ScalarQuantizedVectorsFormat |
| org.apache.lucene.codecs.lucene99.TestLucene99ScalarQuantizedVectorsWriter | org.gnit.lucenekmp.codecs.lucene99.TestLucene99ScalarQuantizedVectorsWriter |
| org.apache.lucene.codecs.lucene99.TestLucene99SegmentInfoFormat | org.gnit.lucenekmp.codecs.lucene99.TestLucene99SegmentInfoFormat |
| org.apache.lucene.codecs.perfield.TestPerFieldDocValuesFormat | org.gnit.lucenekmp.codecs.perfield.TestPerFieldDocValuesFormat |
| org.apache.lucene.codecs.perfield.TestPerFieldKnnVectorsFormat | org.gnit.lucenekmp.codecs.perfield.TestPerFieldKnnVectorsFormat |
| org.apache.lucene.codecs.perfield.TestPerFieldPostingsFormat | org.gnit.lucenekmp.codecs.perfield.TestPerFieldPostingsFormat |
| org.apache.lucene.codecs.perfield.TestPerFieldPostingsFormat2 | org.gnit.lucenekmp.codecs.perfield.TestPerFieldPostingsFormat2 |
| org.apache.lucene.document.TestBinaryDocument | org.gnit.lucenekmp.document.TestBinaryDocument |
| org.apache.lucene.document.TestDateTools | org.gnit.lucenekmp.document.TestDateTools |
| org.apache.lucene.document.TestDocValuesLongHashSet | org.gnit.lucenekmp.document.TestDocValuesLongHashSet |
| org.apache.lucene.document.TestDoubleRange | org.gnit.lucenekmp.document.TestDoubleRange |
| org.apache.lucene.document.TestFeatureDoubleValues | org.gnit.lucenekmp.document.TestFeatureDoubleValues |
| org.apache.lucene.document.TestFeatureField | org.gnit.lucenekmp.document.TestFeatureField |
| org.apache.lucene.document.TestFeatureSort | org.gnit.lucenekmp.document.TestFeatureSort |
| org.apache.lucene.document.TestField | org.gnit.lucenekmp.document.TestField |
| org.apache.lucene.document.TestFieldType | org.gnit.lucenekmp.document.TestFieldType |
| org.apache.lucene.document.TestFloatRange | org.gnit.lucenekmp.document.TestFloatRange |
| org.apache.lucene.document.TestInetAddressPoint | org.gnit.lucenekmp.document.TestInetAddressPoint |
| org.apache.lucene.document.TestIntRange | org.gnit.lucenekmp.document.TestIntRange |
| org.apache.lucene.document.TestKeywordField | org.gnit.lucenekmp.document.TestKeywordField |
| org.apache.lucene.document.TestLatLonDocValuesField | org.gnit.lucenekmp.document.TestLatLonDocValuesField |
| org.apache.lucene.document.TestLatLonDocValuesMultiPointPointQueries | org.gnit.lucenekmp.document.TestLatLonDocValuesMultiPointPointQueries |
| org.apache.lucene.document.TestLatLonDocValuesPointPointQueries | org.gnit.lucenekmp.document.TestLatLonDocValuesPointPointQueries |
| org.apache.lucene.document.TestLatLonLineShapeDVQueries | org.gnit.lucenekmp.document.TestLatLonLineShapeDVQueries |
| org.apache.lucene.document.TestLatLonLineShapeQueries | org.gnit.lucenekmp.document.TestLatLonLineShapeQueries |
| org.apache.lucene.document.TestLatLonMultiLineShapeQueries | org.gnit.lucenekmp.document.TestLatLonMultiLineShapeQueries |
| org.apache.lucene.document.TestLatLonMultiPointPointQueries | org.gnit.lucenekmp.document.TestLatLonMultiPointPointQueries |
| org.apache.lucene.document.TestLatLonMultiPointShapeQueries | org.gnit.lucenekmp.document.TestLatLonMultiPointShapeQueries |
| org.apache.lucene.document.TestLatLonMultiPolygonShapeQueries | org.gnit.lucenekmp.document.TestLatLonMultiPolygonShapeQueries |
| org.apache.lucene.document.TestLatLonPoint | org.gnit.lucenekmp.document.TestLatLonPoint |
| org.apache.lucene.document.TestLatLonPointDistanceFeatureQuery | org.gnit.lucenekmp.document.TestLatLonPointDistanceFeatureQuery |
| org.apache.lucene.document.TestLatLonPointDistanceSort | org.gnit.lucenekmp.document.TestLatLonPointDistanceSort |
| org.apache.lucene.document.TestLatLonPointPointQueries | org.gnit.lucenekmp.document.TestLatLonPointPointQueries |
| org.apache.lucene.document.TestLatLonPointShapeDVQueries | org.gnit.lucenekmp.document.TestLatLonPointShapeDVQueries |
| org.apache.lucene.document.TestLatLonPointShapeQueries | org.gnit.lucenekmp.document.TestLatLonPointShapeQueries |
| org.apache.lucene.document.TestLatLonPolygonShapeDVQueries | org.gnit.lucenekmp.document.TestLatLonPolygonShapeDVQueries |
| org.apache.lucene.document.TestLatLonPolygonShapeQueries | org.gnit.lucenekmp.document.TestLatLonPolygonShapeQueries |
| org.apache.lucene.document.TestLatLonShape | org.gnit.lucenekmp.document.TestLatLonShape |
| org.apache.lucene.document.TestLatLonShapeEncoding | org.gnit.lucenekmp.document.TestLatLonShapeEncoding |
| org.apache.lucene.document.TestLongDistanceFeatureQuery | org.gnit.lucenekmp.document.TestLongDistanceFeatureQuery |
| org.apache.lucene.document.TestLongRange | org.gnit.lucenekmp.document.TestLongRange |
| org.apache.lucene.document.TestManyKnnDocs | org.gnit.lucenekmp.document.TestManyKnnDocs |
| org.apache.lucene.document.TestPerFieldConsistency | org.gnit.lucenekmp.document.TestPerFieldConsistency |
| org.apache.lucene.document.TestShapeDocValues | org.gnit.lucenekmp.document.TestShapeDocValues |
| org.apache.lucene.document.TestSortedSetDocValuesSetQuery | org.gnit.lucenekmp.document.TestSortedSetDocValuesSetQuery |
| org.apache.lucene.document.TestXYLineShapeDVQueries | org.gnit.lucenekmp.document.TestXYLineShapeDVQueries |
| org.apache.lucene.document.TestXYLineShapeQueries | org.gnit.lucenekmp.document.TestXYLineShapeQueries |
| org.apache.lucene.document.TestXYMultiLineShapeQueries | org.gnit.lucenekmp.document.TestXYMultiLineShapeQueries |
| org.apache.lucene.document.TestXYMultiPointShapeQueries | org.gnit.lucenekmp.document.TestXYMultiPointShapeQueries |
| org.apache.lucene.document.TestXYMultiPolygonShapeQueries | org.gnit.lucenekmp.document.TestXYMultiPolygonShapeQueries |
| org.apache.lucene.document.TestXYPointShapeDVQueries | org.gnit.lucenekmp.document.TestXYPointShapeDVQueries |
| org.apache.lucene.document.TestXYPointShapeQueries | org.gnit.lucenekmp.document.TestXYPointShapeQueries |
| org.apache.lucene.document.TestXYPolygonShapeDVQueries | org.gnit.lucenekmp.document.TestXYPolygonShapeDVQueries |
| org.apache.lucene.document.TestXYPolygonShapeQueries | org.gnit.lucenekmp.document.TestXYPolygonShapeQueries |
| org.apache.lucene.document.TestXYShape | org.gnit.lucenekmp.document.TestXYShape |
| org.apache.lucene.document.TestXYShapeEncoding | org.gnit.lucenekmp.document.TestXYShapeEncoding |
| org.apache.lucene.geo.TestCircle | org.gnit.lucenekmp.geo.TestCircle |
| org.apache.lucene.geo.TestCircle2D | org.gnit.lucenekmp.geo.TestCircle2D |
| org.apache.lucene.geo.TestGeoEncodingUtils | org.gnit.lucenekmp.geo.TestGeoEncodingUtils |
| org.apache.lucene.geo.TestGeoUtils | org.gnit.lucenekmp.geo.TestGeoUtils |
| org.apache.lucene.geo.TestLine2D | org.gnit.lucenekmp.geo.TestLine2D |
| org.apache.lucene.geo.TestPoint | org.gnit.lucenekmp.geo.TestPoint |
| org.apache.lucene.geo.TestPoint2D | org.gnit.lucenekmp.geo.TestPoint2D |
| org.apache.lucene.geo.TestPolygon | org.gnit.lucenekmp.geo.TestPolygon |
| org.apache.lucene.geo.TestPolygon2D | org.gnit.lucenekmp.geo.TestPolygon2D |
| org.apache.lucene.geo.TestRectangle2D | org.gnit.lucenekmp.geo.TestRectangle2D |
| org.apache.lucene.geo.TestSimpleWKTShapeParsing | org.gnit.lucenekmp.geo.TestSimpleWKTShapeParsing |
| org.apache.lucene.geo.TestTessellator | org.gnit.lucenekmp.geo.TestTessellator |
| org.apache.lucene.geo.TestXYCircle | org.gnit.lucenekmp.geo.TestXYCircle |
| org.apache.lucene.geo.TestXYLine | org.gnit.lucenekmp.geo.TestXYLine |
| org.apache.lucene.geo.TestXYPoint | org.gnit.lucenekmp.geo.TestXYPoint |
| org.apache.lucene.geo.TestXYPolygon | org.gnit.lucenekmp.geo.TestXYPolygon |
| org.apache.lucene.geo.TestXYRectangle | org.gnit.lucenekmp.geo.TestXYRectangle |
| org.apache.lucene.index.Test2BBinaryDocValues | org.gnit.lucenekmp.index.Test2BBinaryDocValues |
| org.apache.lucene.index.Test2BDocs | org.gnit.lucenekmp.index.Test2BDocs |
| org.apache.lucene.index.Test2BNumericDocValues | org.gnit.lucenekmp.index.Test2BNumericDocValues |
| org.apache.lucene.index.Test2BPoints | org.gnit.lucenekmp.index.Test2BPoints |
| org.apache.lucene.index.Test2BPositions | org.gnit.lucenekmp.index.Test2BPositions |
| org.apache.lucene.index.Test2BPostings | org.gnit.lucenekmp.index.Test2BPostings |
| org.apache.lucene.index.Test2BPostingsBytes | org.gnit.lucenekmp.index.Test2BPostingsBytes |
| org.apache.lucene.index.Test2BSortedDocValuesFixedSorted | org.gnit.lucenekmp.index.Test2BSortedDocValuesFixedSorted |
| org.apache.lucene.index.Test2BSortedDocValuesOrds | org.gnit.lucenekmp.index.Test2BSortedDocValuesOrds |
| org.apache.lucene.index.Test2BTerms | org.gnit.lucenekmp.index.Test2BTerms |
| org.apache.lucene.index.Test4GBStoredFields | org.gnit.lucenekmp.index.Test4GBStoredFields |
| org.apache.lucene.index.TestAddIndexes | org.gnit.lucenekmp.index.TestAddIndexes |
| org.apache.lucene.index.TestAllFilesCheckIndexHeader | org.gnit.lucenekmp.index.TestAllFilesCheckIndexHeader |
| org.apache.lucene.index.TestAllFilesDetectMismatchedChecksum | org.gnit.lucenekmp.index.TestAllFilesDetectMismatchedChecksum |
| org.apache.lucene.index.TestAllFilesDetectTruncation | org.gnit.lucenekmp.index.TestAllFilesDetectTruncation |
| org.apache.lucene.index.TestAllFilesHaveChecksumFooter | org.gnit.lucenekmp.index.TestAllFilesHaveChecksumFooter |
| org.apache.lucene.index.TestAllFilesHaveCodecHeader | org.gnit.lucenekmp.index.TestAllFilesHaveCodecHeader |
| org.apache.lucene.index.TestApproximatePriorityQueue | org.gnit.lucenekmp.index.TestApproximatePriorityQueue |
| org.apache.lucene.index.TestAtomicUpdate | org.gnit.lucenekmp.index.TestAtomicUpdate |
| org.apache.lucene.index.TestBagOfPositions | org.gnit.lucenekmp.index.TestBagOfPositions |
| org.apache.lucene.index.TestBagOfPostings | org.gnit.lucenekmp.index.TestBagOfPostings |
| org.apache.lucene.index.TestBinaryDocValuesUpdates | org.gnit.lucenekmp.index.TestBinaryDocValuesUpdates |
| org.apache.lucene.index.TestBinaryTerms | org.gnit.lucenekmp.index.TestBinaryTerms |
| org.apache.lucene.index.TestBufferedUpdates | org.gnit.lucenekmp.index.TestBufferedUpdates |
| org.apache.lucene.index.TestByteSlicePool | org.gnit.lucenekmp.index.TestByteSlicePool |
| org.apache.lucene.index.TestByteSliceReader | org.gnit.lucenekmp.index.TestByteSliceReader |
| org.apache.lucene.index.TestCachingMergeContext | org.gnit.lucenekmp.index.TestCachingMergeContext |
| org.apache.lucene.index.TestCheckIndex | org.gnit.lucenekmp.index.TestCheckIndex |
| org.apache.lucene.index.TestCodecHoldsOpenFiles | org.gnit.lucenekmp.index.TestCodecHoldsOpenFiles |
| org.apache.lucene.index.TestCodecs | org.gnit.lucenekmp.index.TestCodecs |
| org.apache.lucene.index.TestConcurrentApproximatePriorityQueue | org.gnit.lucenekmp.index.TestConcurrentApproximatePriorityQueue |
| org.apache.lucene.index.TestConcurrentMergeScheduler | org.gnit.lucenekmp.index.TestConcurrentMergeScheduler |
| org.apache.lucene.index.TestConsistentFieldNumbers | org.gnit.lucenekmp.index.TestConsistentFieldNumbers |
| org.apache.lucene.index.TestCrash | org.gnit.lucenekmp.index.TestCrash |
| org.apache.lucene.index.TestCrashCausesCorruptIndex | org.gnit.lucenekmp.index.TestCrashCausesCorruptIndex |
| org.apache.lucene.index.TestCustomNorms | org.gnit.lucenekmp.index.TestCustomNorms |
| org.apache.lucene.index.TestCustomTermFreq | org.gnit.lucenekmp.index.TestCustomTermFreq |
| org.apache.lucene.index.TestDefaultCodecParallelizesIO | org.gnit.lucenekmp.index.TestDefaultCodecParallelizesIO |
| org.apache.lucene.index.TestDeletionPolicy | org.gnit.lucenekmp.index.TestDeletionPolicy |
| org.apache.lucene.index.TestDemoParallelLeafReader | org.gnit.lucenekmp.index.TestDemoParallelLeafReader |
| org.apache.lucene.index.TestDirectoryReader | org.gnit.lucenekmp.index.TestDirectoryReader |
| org.apache.lucene.index.TestDirectoryReaderReopen | org.gnit.lucenekmp.index.TestDirectoryReaderReopen |
| org.apache.lucene.index.TestDoc | org.gnit.lucenekmp.index.TestDoc |
| org.apache.lucene.index.TestDocCount | org.gnit.lucenekmp.index.TestDocCount |
| org.apache.lucene.index.TestDocIDMerger | org.gnit.lucenekmp.index.TestDocIDMerger |
| org.apache.lucene.index.TestDocInverterPerFieldErrorInfo | org.gnit.lucenekmp.index.TestDocInverterPerFieldErrorInfo |
| org.apache.lucene.index.TestDocValues | org.gnit.lucenekmp.index.TestDocValues |
| org.apache.lucene.index.TestDocValuesFieldUpdates | org.gnit.lucenekmp.index.TestDocValuesFieldUpdates |
| org.apache.lucene.index.TestDocValuesIndexing | org.gnit.lucenekmp.index.TestDocValuesIndexing |
| org.apache.lucene.index.TestDocsAndPositions | org.gnit.lucenekmp.index.TestDocsAndPositions |
| org.apache.lucene.index.TestDocsWithFieldSet | org.gnit.lucenekmp.index.TestDocsWithFieldSet |
| org.apache.lucene.index.TestDocumentWriter | org.gnit.lucenekmp.index.TestDocumentWriter |
| org.apache.lucene.index.TestDocumentsWriterDeleteQueue | org.gnit.lucenekmp.index.TestDocumentsWriterDeleteQueue |
| org.apache.lucene.index.TestDocumentsWriterPerThreadPool | org.gnit.lucenekmp.index.TestDocumentsWriterPerThreadPool |
| org.apache.lucene.index.TestDocumentsWriterStallControl | org.gnit.lucenekmp.index.TestDocumentsWriterStallControl |
| org.apache.lucene.index.TestDuelingCodecs | org.gnit.lucenekmp.index.TestDuelingCodecs |
| org.apache.lucene.index.TestDuelingCodecsAtNight | org.gnit.lucenekmp.index.TestDuelingCodecsAtNight |
| org.apache.lucene.index.TestExceedMaxTermLength | org.gnit.lucenekmp.index.TestExceedMaxTermLength |
| org.apache.lucene.index.TestExitableDirectoryReader | org.gnit.lucenekmp.index.TestExitableDirectoryReader |
| org.apache.lucene.index.TestFieldInfos | org.gnit.lucenekmp.index.TestFieldInfos |
| org.apache.lucene.index.TestFieldInvertState | org.gnit.lucenekmp.index.TestFieldInvertState |
| org.apache.lucene.index.TestFieldReuse | org.gnit.lucenekmp.index.TestFieldReuse |
| org.apache.lucene.index.TestFieldUpdatesBuffer | org.gnit.lucenekmp.index.TestFieldUpdatesBuffer |
| org.apache.lucene.index.TestFieldsReader | org.gnit.lucenekmp.index.TestFieldsReader |
| org.apache.lucene.index.TestFilterCodecReader | org.gnit.lucenekmp.index.TestFilterCodecReader |
| org.apache.lucene.index.TestFilterDirectoryReader | org.gnit.lucenekmp.index.TestFilterDirectoryReader |
| org.apache.lucene.index.TestFilterIndexInput | org.gnit.lucenekmp.index.TestFilterIndexInput |
| org.apache.lucene.index.TestFilterLeafReader | org.gnit.lucenekmp.index.TestFilterLeafReader |
| org.apache.lucene.index.TestFilterMergePolicy | org.gnit.lucenekmp.index.TestFilterMergePolicy |
| org.apache.lucene.index.TestFlex | org.gnit.lucenekmp.index.TestFlex |
| org.apache.lucene.index.TestFlushByRamOrCountsPolicy | org.gnit.lucenekmp.index.TestFlushByRamOrCountsPolicy |
| org.apache.lucene.index.TestForTooMuchCloning | org.gnit.lucenekmp.index.TestForTooMuchCloning |
| org.apache.lucene.index.TestForceMergeForever | org.gnit.lucenekmp.index.TestForceMergeForever |
| org.apache.lucene.index.TestFrozenBufferedUpdates | org.gnit.lucenekmp.index.TestFrozenBufferedUpdates |
| org.apache.lucene.index.TestIndexCommit | org.gnit.lucenekmp.index.TestIndexCommit |
| org.apache.lucene.index.TestIndexFileDeleter | org.gnit.lucenekmp.index.TestIndexFileDeleter |
| org.apache.lucene.index.TestIndexInput | org.gnit.lucenekmp.index.TestIndexInput |
| org.apache.lucene.index.TestIndexManyDocuments | org.gnit.lucenekmp.index.TestIndexManyDocuments |
| org.apache.lucene.index.TestIndexOptions | org.gnit.lucenekmp.index.TestIndexOptions |
| org.apache.lucene.index.TestIndexReaderClose | org.gnit.lucenekmp.index.TestIndexReaderClose |
| org.apache.lucene.index.TestIndexSorting | org.gnit.lucenekmp.index.TestIndexSorting |
| org.apache.lucene.index.TestIndexTooManyDocs | org.gnit.lucenekmp.index.TestIndexTooManyDocs |
| org.apache.lucene.index.TestIndexWriterCommit | org.gnit.lucenekmp.index.TestIndexWriterCommit |
| org.apache.lucene.index.TestIndexWriterConfig | org.gnit.lucenekmp.index.TestIndexWriterConfig |
| org.apache.lucene.index.TestIndexWriterDelete | org.gnit.lucenekmp.index.TestIndexWriterDelete |
| org.apache.lucene.index.TestIndexWriterExceptions | org.gnit.lucenekmp.index.TestIndexWriterExceptions |
| org.apache.lucene.index.TestIndexWriterExceptions2 | org.gnit.lucenekmp.index.TestIndexWriterExceptions2 |
| org.apache.lucene.index.TestIndexWriterForceMerge | org.gnit.lucenekmp.index.TestIndexWriterForceMerge |
| org.apache.lucene.index.TestIndexWriterFromReader | org.gnit.lucenekmp.index.TestIndexWriterFromReader |
| org.apache.lucene.index.TestIndexWriterLockRelease | org.gnit.lucenekmp.index.TestIndexWriterLockRelease |
| org.apache.lucene.index.TestIndexWriterMaxDocs | org.gnit.lucenekmp.index.TestIndexWriterMaxDocs |
| org.apache.lucene.index.TestIndexWriterMergePolicy | org.gnit.lucenekmp.index.TestIndexWriterMergePolicy |
| org.apache.lucene.index.TestIndexWriterMerging | org.gnit.lucenekmp.index.TestIndexWriterMerging |
| org.apache.lucene.index.TestIndexWriterNRTIsCurrent | org.gnit.lucenekmp.index.TestIndexWriterNRTIsCurrent |
| org.apache.lucene.index.TestIndexWriterOnDiskFull | org.gnit.lucenekmp.index.TestIndexWriterOnDiskFull |
| org.apache.lucene.index.TestIndexWriterOnError | org.gnit.lucenekmp.index.TestIndexWriterOnError |
| org.apache.lucene.index.TestIndexWriterOnJRECrash | org.gnit.lucenekmp.index.TestIndexWriterOnJRECrash |
| org.apache.lucene.index.TestIndexWriterOutOfFileDescriptors | org.gnit.lucenekmp.index.TestIndexWriterOutOfFileDescriptors |
| org.apache.lucene.index.TestIndexWriterReader | org.gnit.lucenekmp.index.TestIndexWriterReader |
| org.apache.lucene.index.TestIndexWriterThreadsToSegments | org.gnit.lucenekmp.index.TestIndexWriterThreadsToSegments |
| org.apache.lucene.index.TestIndexWriterUnicode | org.gnit.lucenekmp.index.TestIndexWriterUnicode |
| org.apache.lucene.index.TestIndexWriterWithThreads | org.gnit.lucenekmp.index.TestIndexWriterWithThreads |
| org.apache.lucene.index.TestIndexableField | org.gnit.lucenekmp.index.TestIndexableField |
| org.apache.lucene.index.TestIndexingSequenceNumbers | org.gnit.lucenekmp.index.TestIndexingSequenceNumbers |
| org.apache.lucene.index.TestInfoStream | org.gnit.lucenekmp.index.TestInfoStream |
| org.apache.lucene.index.TestIntBlockPool | org.gnit.lucenekmp.index.TestIntBlockPool |
| org.apache.lucene.index.TestIsCurrent | org.gnit.lucenekmp.index.TestIsCurrent |
| org.apache.lucene.index.TestKnnGraph | org.gnit.lucenekmp.index.TestKnnGraph |
| org.apache.lucene.index.TestLockableConcurrentApproximatePriorityQueue | org.gnit.lucenekmp.index.TestLockableConcurrentApproximatePriorityQueue |
| org.apache.lucene.index.TestLogMergePolicy | org.gnit.lucenekmp.index.TestLogMergePolicy |
| org.apache.lucene.index.TestLongPostings | org.gnit.lucenekmp.index.TestLongPostings |
| org.apache.lucene.index.TestManyFields | org.gnit.lucenekmp.index.TestManyFields |
| org.apache.lucene.index.TestMaxPosition | org.gnit.lucenekmp.index.TestMaxPosition |
| org.apache.lucene.index.TestMaxTermFrequency | org.gnit.lucenekmp.index.TestMaxTermFrequency |
| org.apache.lucene.index.TestMergePolicy | org.gnit.lucenekmp.index.TestMergePolicy |
| org.apache.lucene.index.TestMergeRateLimiter | org.gnit.lucenekmp.index.TestMergeRateLimiter |
| org.apache.lucene.index.TestMixedCodecs | org.gnit.lucenekmp.index.TestMixedCodecs |
| org.apache.lucene.index.TestMixedDocValuesUpdates | org.gnit.lucenekmp.index.TestMixedDocValuesUpdates |
| org.apache.lucene.index.TestMultiDocValues | org.gnit.lucenekmp.index.TestMultiDocValues |
| org.apache.lucene.index.TestMultiFields | org.gnit.lucenekmp.index.TestMultiFields |
| org.apache.lucene.index.TestMultiLevelSkipList | org.gnit.lucenekmp.index.TestMultiLevelSkipList |
| org.apache.lucene.index.TestMultiTermsEnum | org.gnit.lucenekmp.index.TestMultiTermsEnum |
| org.apache.lucene.index.TestNRTReaderCleanup | org.gnit.lucenekmp.index.TestNRTReaderCleanup |
| org.apache.lucene.index.TestNRTReaderWithThreads | org.gnit.lucenekmp.index.TestNRTReaderWithThreads |
| org.apache.lucene.index.TestNRTThreads | org.gnit.lucenekmp.index.TestNRTThreads |
| org.apache.lucene.index.TestNeverDelete | org.gnit.lucenekmp.index.TestNeverDelete |
| org.apache.lucene.index.TestNewestSegment | org.gnit.lucenekmp.index.TestNewestSegment |
| org.apache.lucene.index.TestNoDeletionPolicy | org.gnit.lucenekmp.index.TestNoDeletionPolicy |
| org.apache.lucene.index.TestNoMergePolicy | org.gnit.lucenekmp.index.TestNoMergePolicy |
| org.apache.lucene.index.TestNoMergeScheduler | org.gnit.lucenekmp.index.TestNoMergeScheduler |
| org.apache.lucene.index.TestNorms | org.gnit.lucenekmp.index.TestNorms |
| org.apache.lucene.index.TestNumericDocValuesUpdates | org.gnit.lucenekmp.index.TestNumericDocValuesUpdates |
| org.apache.lucene.index.TestOmitNorms | org.gnit.lucenekmp.index.TestOmitNorms |
| org.apache.lucene.index.TestOmitPositions | org.gnit.lucenekmp.index.TestOmitPositions |
| org.apache.lucene.index.TestOmitTf | org.gnit.lucenekmp.index.TestOmitTf |
| org.apache.lucene.index.TestOneMergeWrappingMergePolicy | org.gnit.lucenekmp.index.TestOneMergeWrappingMergePolicy |
| org.apache.lucene.index.TestOrdinalMap | org.gnit.lucenekmp.index.TestOrdinalMap |
| org.apache.lucene.index.TestParallelCompositeReader | org.gnit.lucenekmp.index.TestParallelCompositeReader |
| org.apache.lucene.index.TestParallelLeafReader | org.gnit.lucenekmp.index.TestParallelLeafReader |
| org.apache.lucene.index.TestParallelReaderEmptyIndex | org.gnit.lucenekmp.index.TestParallelReaderEmptyIndex |
| org.apache.lucene.index.TestParallelTermEnum | org.gnit.lucenekmp.index.TestParallelTermEnum |
| org.apache.lucene.index.TestPayloads | org.gnit.lucenekmp.index.TestPayloads |
| org.apache.lucene.index.TestPayloadsOnVectors | org.gnit.lucenekmp.index.TestPayloadsOnVectors |
| org.apache.lucene.index.TestPendingDeletes | org.gnit.lucenekmp.index.TestPendingDeletes |
| org.apache.lucene.index.TestPendingSoftDeletes | org.gnit.lucenekmp.index.TestPendingSoftDeletes |
| org.apache.lucene.index.TestPerSegmentDeletes | org.gnit.lucenekmp.index.TestPerSegmentDeletes |
| org.apache.lucene.index.TestPersistentSnapshotDeletionPolicy | org.gnit.lucenekmp.index.TestPersistentSnapshotDeletionPolicy |
| org.apache.lucene.index.TestPointValues | org.gnit.lucenekmp.index.TestPointValues |
| org.apache.lucene.index.TestPostingsOffsets | org.gnit.lucenekmp.index.TestPostingsOffsets |
| org.apache.lucene.index.TestPrefixCodedTerms | org.gnit.lucenekmp.index.TestPrefixCodedTerms |
| org.apache.lucene.index.TestReadOnlyIndex | org.gnit.lucenekmp.index.TestReadOnlyIndex |
| org.apache.lucene.index.TestReaderClosed | org.gnit.lucenekmp.index.TestReaderClosed |
| org.apache.lucene.index.TestReaderPool | org.gnit.lucenekmp.index.TestReaderPool |
| org.apache.lucene.index.TestReaderWrapperDVTypeCheck | org.gnit.lucenekmp.index.TestReaderWrapperDVTypeCheck |
| org.apache.lucene.index.TestRollback | org.gnit.lucenekmp.index.TestRollback |
| org.apache.lucene.index.TestRollingUpdates | org.gnit.lucenekmp.index.TestRollingUpdates |
| org.apache.lucene.index.TestSameTokenSamePosition | org.gnit.lucenekmp.index.TestSameTokenSamePosition |
| org.apache.lucene.index.TestSegmentInfos | org.gnit.lucenekmp.index.TestSegmentInfos |
| org.apache.lucene.index.TestSegmentMerger | org.gnit.lucenekmp.index.TestSegmentMerger |
| org.apache.lucene.index.TestSegmentReader | org.gnit.lucenekmp.index.TestSegmentReader |
| org.apache.lucene.index.TestSegmentTermDocs | org.gnit.lucenekmp.index.TestSegmentTermDocs |
| org.apache.lucene.index.TestSegmentTermEnum | org.gnit.lucenekmp.index.TestSegmentTermEnum |
| org.apache.lucene.index.TestSegmentToThreadMapping | org.gnit.lucenekmp.index.TestSegmentToThreadMapping |
| org.apache.lucene.index.TestSizeBoundedForceMerge | org.gnit.lucenekmp.index.TestSizeBoundedForceMerge |
| org.apache.lucene.index.TestSnapshotDeletionPolicy | org.gnit.lucenekmp.index.TestSnapshotDeletionPolicy |
| org.apache.lucene.index.TestSoftDeletesDirectoryReaderWrapper | org.gnit.lucenekmp.index.TestSoftDeletesDirectoryReaderWrapper |
| org.apache.lucene.index.TestSoftDeletesRetentionMergePolicy | org.gnit.lucenekmp.index.TestSoftDeletesRetentionMergePolicy |
| org.apache.lucene.index.TestSortingCodecReader | org.gnit.lucenekmp.index.TestSortingCodecReader |
| org.apache.lucene.index.TestStoredFieldsConsumer | org.gnit.lucenekmp.index.TestStoredFieldsConsumer |
| org.apache.lucene.index.TestStressAdvance | org.gnit.lucenekmp.index.TestStressAdvance |
| org.apache.lucene.index.TestStressDeletes | org.gnit.lucenekmp.index.TestStressDeletes |
| org.apache.lucene.index.TestStressIndexing | org.gnit.lucenekmp.index.TestStressIndexing |
| org.apache.lucene.index.TestStressIndexing2 | org.gnit.lucenekmp.index.TestStressIndexing2 |
| org.apache.lucene.index.TestStressNRT | org.gnit.lucenekmp.index.TestStressNRT |
| org.apache.lucene.index.TestSumDocFreq | org.gnit.lucenekmp.index.TestSumDocFreq |
| org.apache.lucene.index.TestSwappedIndexFiles | org.gnit.lucenekmp.index.TestSwappedIndexFiles |
| org.apache.lucene.index.TestTermStates | org.gnit.lucenekmp.index.TestTermStates |
| org.apache.lucene.index.TestTermVectors | org.gnit.lucenekmp.index.TestTermVectors |
| org.apache.lucene.index.TestTermVectorsReader | org.gnit.lucenekmp.index.TestTermVectorsReader |
| org.apache.lucene.index.TestTermVectorsWriter | org.gnit.lucenekmp.index.TestTermVectorsWriter |
| org.apache.lucene.index.TestTermdocPerf | org.gnit.lucenekmp.index.TestTermdocPerf |
| org.apache.lucene.index.TestTerms | org.gnit.lucenekmp.index.TestTerms |
| org.apache.lucene.index.TestTermsEnum | org.gnit.lucenekmp.index.TestTermsEnum |
| org.apache.lucene.index.TestTermsEnum2 | org.gnit.lucenekmp.index.TestTermsEnum2 |
| org.apache.lucene.index.TestTermsEnumIndex | org.gnit.lucenekmp.index.TestTermsEnumIndex |
| org.apache.lucene.index.TestTermsHashPerField | org.gnit.lucenekmp.index.TestTermsHashPerField |
| org.apache.lucene.index.TestThreadedForceMerge | org.gnit.lucenekmp.index.TestThreadedForceMerge |
| org.apache.lucene.index.TestTieredMergePolicy | org.gnit.lucenekmp.index.TestTieredMergePolicy |
| org.apache.lucene.index.TestTragicIndexWriterDeadlock | org.gnit.lucenekmp.index.TestTragicIndexWriterDeadlock |
| org.apache.lucene.index.TestTransactionRollback | org.gnit.lucenekmp.index.TestTransactionRollback |
| org.apache.lucene.index.TestTransactions | org.gnit.lucenekmp.index.TestTransactions |
| org.apache.lucene.index.TestTryDelete | org.gnit.lucenekmp.index.TestTryDelete |
| org.apache.lucene.index.TestTwoPhaseCommitTool | org.gnit.lucenekmp.index.TestTwoPhaseCommitTool |
| org.apache.lucene.index.TestUniqueTermCount | org.gnit.lucenekmp.index.TestUniqueTermCount |
| org.apache.lucene.index.TestUpgradeIndexMergePolicy | org.gnit.lucenekmp.index.TestUpgradeIndexMergePolicy |
| org.apache.lucene.internal.hppc.TestCharHashSet | org.gnit.lucenekmp.internal.hppc.TestCharHashSet |
| org.apache.lucene.internal.hppc.TestCharObjectHashMap | org.gnit.lucenekmp.internal.hppc.TestCharObjectHashMap |
| org.apache.lucene.internal.hppc.TestFloatArrayList | org.gnit.lucenekmp.internal.hppc.TestFloatArrayList |
| org.apache.lucene.internal.hppc.TestIntArrayList | org.gnit.lucenekmp.internal.hppc.TestIntArrayList |
| org.apache.lucene.internal.hppc.TestIntDoubleHashMap | org.gnit.lucenekmp.internal.hppc.TestIntDoubleHashMap |
| org.apache.lucene.internal.hppc.TestIntFloatHashMap | org.gnit.lucenekmp.internal.hppc.TestIntFloatHashMap |
| org.apache.lucene.internal.hppc.TestIntHashSet | org.gnit.lucenekmp.internal.hppc.TestIntHashSet |
| org.apache.lucene.internal.hppc.TestIntIntHashMap | org.gnit.lucenekmp.internal.hppc.TestIntIntHashMap |
| org.apache.lucene.internal.hppc.TestIntLongHashMap | org.gnit.lucenekmp.internal.hppc.TestIntLongHashMap |
| org.apache.lucene.internal.hppc.TestIntObjectHashMap | org.gnit.lucenekmp.internal.hppc.TestIntObjectHashMap |
| org.apache.lucene.internal.hppc.TestLongArrayList | org.gnit.lucenekmp.internal.hppc.TestLongArrayList |
| org.apache.lucene.internal.hppc.TestLongFloatHashMap | org.gnit.lucenekmp.internal.hppc.TestLongFloatHashMap |
| org.apache.lucene.internal.hppc.TestLongHashSet | org.gnit.lucenekmp.internal.hppc.TestLongHashSet |
| org.apache.lucene.internal.hppc.TestLongIntHashMap | org.gnit.lucenekmp.internal.hppc.TestLongIntHashMap |
| org.apache.lucene.internal.hppc.TestLongObjectHashMap | org.gnit.lucenekmp.internal.hppc.TestLongObjectHashMap |
| org.apache.lucene.internal.tests.TestTestSecrets | org.gnit.lucenekmp.internal.tests.TestTestSecrets |
| org.apache.lucene.internal.vectorization.TestPostingDecodingUtil | org.gnit.lucenekmp.internal.vectorization.TestPostingDecodingUtil |
| org.apache.lucene.internal.vectorization.TestVectorScorer | org.gnit.lucenekmp.internal.vectorization.TestVectorScorer |
| org.apache.lucene.internal.vectorization.TestVectorUtilSupport | org.gnit.lucenekmp.internal.vectorization.TestVectorUtilSupport |
| org.apache.lucene.internal.vectorization.TestVectorizationProvider | org.gnit.lucenekmp.internal.vectorization.TestVectorizationProvider |
| org.apache.lucene.search.TestApproximationSearchEquivalence | org.gnit.lucenekmp.search.TestApproximationSearchEquivalence |
| org.apache.lucene.search.TestAutomatonQuery | org.gnit.lucenekmp.search.TestAutomatonQuery |
| org.apache.lucene.search.TestAutomatonQueryUnicode | org.gnit.lucenekmp.search.TestAutomatonQueryUnicode |
| org.apache.lucene.search.TestBaseRangeFilter | org.gnit.lucenekmp.search.TestBaseRangeFilter |
| org.apache.lucene.search.TestBlendedTermQuery | org.gnit.lucenekmp.search.TestBlendedTermQuery |
| org.apache.lucene.search.TestBlockMaxConjunction | org.gnit.lucenekmp.search.TestBlockMaxConjunction |
| org.apache.lucene.search.TestBoolean2 | org.gnit.lucenekmp.search.TestBoolean2 |
| org.apache.lucene.search.TestBoolean2ScorerSupplier | org.gnit.lucenekmp.search.TestBoolean2ScorerSupplier |
| org.apache.lucene.search.TestBooleanMinShouldMatch | org.gnit.lucenekmp.search.TestBooleanMinShouldMatch |
| org.apache.lucene.search.TestBooleanOr | org.gnit.lucenekmp.search.TestBooleanOr |
| org.apache.lucene.search.TestBooleanQuery | org.gnit.lucenekmp.search.TestBooleanQuery |
| org.apache.lucene.search.TestBooleanQueryVisitSubscorers | org.gnit.lucenekmp.search.TestBooleanQueryVisitSubscorers |
| org.apache.lucene.search.TestBooleanRewrites | org.gnit.lucenekmp.search.TestBooleanRewrites |
| org.apache.lucene.search.TestBooleanScorer | org.gnit.lucenekmp.search.TestBooleanScorer |
| org.apache.lucene.search.TestBoostQuery | org.gnit.lucenekmp.search.TestBoostQuery |
| org.apache.lucene.search.TestByteVectorSimilarityQuery | org.gnit.lucenekmp.search.TestByteVectorSimilarityQuery |
| org.apache.lucene.search.TestCachingCollector | org.gnit.lucenekmp.search.TestCachingCollector |
| org.apache.lucene.search.TestCollectorManager | org.gnit.lucenekmp.search.TestCollectorManager |
| org.apache.lucene.search.TestCombinedFieldQuery | org.gnit.lucenekmp.search.TestCombinedFieldQuery |
| org.apache.lucene.search.TestComplexExplanations | org.gnit.lucenekmp.search.TestComplexExplanations |
| org.apache.lucene.search.TestComplexExplanationsOfNonMatches | org.gnit.lucenekmp.search.TestComplexExplanationsOfNonMatches |
| org.apache.lucene.search.TestConjunctionDISI | org.gnit.lucenekmp.search.TestConjunctionDISI |
| org.apache.lucene.search.TestConjunctions | org.gnit.lucenekmp.search.TestConjunctions |
| org.apache.lucene.search.TestConstantScoreQuery | org.gnit.lucenekmp.search.TestConstantScoreQuery |
| org.apache.lucene.search.TestConstantScoreScorer | org.gnit.lucenekmp.search.TestConstantScoreScorer |
| org.apache.lucene.search.TestControlledRealTimeReopenThread | org.gnit.lucenekmp.search.TestControlledRealTimeReopenThread |
| org.apache.lucene.search.TestCustomSearcherSort | org.gnit.lucenekmp.search.TestCustomSearcherSort |
| org.apache.lucene.search.TestDateSort | org.gnit.lucenekmp.search.TestDateSort |
| org.apache.lucene.search.TestDenseConjunctionBulkScorer | org.gnit.lucenekmp.search.TestDenseConjunctionBulkScorer |
| org.apache.lucene.search.TestDisiPriorityQueue | org.gnit.lucenekmp.search.TestDisiPriorityQueue |
| org.apache.lucene.search.TestDisjunctionMaxQuery | org.gnit.lucenekmp.search.TestDisjunctionMaxQuery |
| org.apache.lucene.search.TestDisjunctionScoreBlockBoundaryPropagator | org.gnit.lucenekmp.search.TestDisjunctionScoreBlockBoundaryPropagator |
| org.apache.lucene.search.TestDocIdSetIterator | org.gnit.lucenekmp.search.TestDocIdSetIterator |
| org.apache.lucene.search.TestDocValuesQueries | org.gnit.lucenekmp.search.TestDocValuesQueries |
| org.apache.lucene.search.TestDocValuesRangeIterator | org.gnit.lucenekmp.search.TestDocValuesRangeIterator |
| org.apache.lucene.search.TestDocValuesRewriteMethod | org.gnit.lucenekmp.search.TestDocValuesRewriteMethod |
| org.apache.lucene.search.TestDoubleRangeFieldQueries | org.gnit.lucenekmp.search.TestDoubleRangeFieldQueries |
| org.apache.lucene.search.TestDoubleValuesSource | org.gnit.lucenekmp.search.TestDoubleValuesSource |
| org.apache.lucene.search.TestEarlyTermination | org.gnit.lucenekmp.search.TestEarlyTermination |
| org.apache.lucene.search.TestElevationComparator | org.gnit.lucenekmp.search.TestElevationComparator |
| org.apache.lucene.search.TestFieldCacheRewriteMethod | org.gnit.lucenekmp.search.TestFieldCacheRewriteMethod |
| org.apache.lucene.search.TestFieldExistsQuery | org.gnit.lucenekmp.search.TestFieldExistsQuery |
| org.apache.lucene.search.TestFilterWeight | org.gnit.lucenekmp.search.TestFilterWeight |
| org.apache.lucene.search.TestFloatRangeFieldQueries | org.gnit.lucenekmp.search.TestFloatRangeFieldQueries |
| org.apache.lucene.search.TestFloatVectorSimilarityQuery | org.gnit.lucenekmp.search.TestFloatVectorSimilarityQuery |
| org.apache.lucene.search.TestFuzzyQuery | org.gnit.lucenekmp.search.TestFuzzyQuery |
| org.apache.lucene.search.TestFuzzyTermOnShortTerms | org.gnit.lucenekmp.search.TestFuzzyTermOnShortTerms |
| org.apache.lucene.search.TestIndexOrDocValuesQuery | org.gnit.lucenekmp.search.TestIndexOrDocValuesQuery |
| org.apache.lucene.search.TestIndexSortSortedNumericDocValuesRangeQuery | org.gnit.lucenekmp.search.TestIndexSortSortedNumericDocValuesRangeQuery |
| org.apache.lucene.search.TestIndriAndQuery | org.gnit.lucenekmp.search.TestIndriAndQuery |
| org.apache.lucene.search.TestInetAddressRangeQueries | org.gnit.lucenekmp.search.TestInetAddressRangeQueries |
| org.apache.lucene.search.TestIntRangeFieldQueries | org.gnit.lucenekmp.search.TestIntRangeFieldQueries |
| org.apache.lucene.search.TestKnnByteVectorQuery | org.gnit.lucenekmp.search.TestKnnByteVectorQuery |
| org.apache.lucene.search.TestKnnByteVectorQueryMMap | org.gnit.lucenekmp.search.TestKnnByteVectorQueryMMap |
| org.apache.lucene.search.TestKnnFloatVectorQuery | org.gnit.lucenekmp.search.TestKnnFloatVectorQuery |
| org.apache.lucene.search.TestLRUQueryCache | org.gnit.lucenekmp.search.TestLRUQueryCache |
| org.apache.lucene.search.TestLatLonDocValuesQueries | org.gnit.lucenekmp.search.TestLatLonDocValuesQueries |
| org.apache.lucene.search.TestLatLonPointQueries | org.gnit.lucenekmp.search.TestLatLonPointQueries |
| org.apache.lucene.search.TestLiveFieldValues | org.gnit.lucenekmp.search.TestLiveFieldValues |
| org.apache.lucene.search.TestLongRangeFieldQueries | org.gnit.lucenekmp.search.TestLongRangeFieldQueries |
| org.apache.lucene.search.TestLongValuesSource | org.gnit.lucenekmp.search.TestLongValuesSource |
| org.apache.lucene.search.TestMatchAllDocsQuery | org.gnit.lucenekmp.search.TestMatchAllDocsQuery |
| org.apache.lucene.search.TestMatchNoDocsQuery | org.gnit.lucenekmp.search.TestMatchNoDocsQuery |
| org.apache.lucene.search.TestMatchesIterator | org.gnit.lucenekmp.search.TestMatchesIterator |
| org.apache.lucene.search.TestMaxClauseLimit | org.gnit.lucenekmp.search.TestMaxClauseLimit |
| org.apache.lucene.search.TestMaxScoreAccumulator | org.gnit.lucenekmp.search.TestMaxScoreAccumulator |
| org.apache.lucene.search.TestMaxScoreBulkScorer | org.gnit.lucenekmp.search.TestMaxScoreBulkScorer |
| org.apache.lucene.search.TestMinShouldMatch2 | org.gnit.lucenekmp.search.TestMinShouldMatch2 |
| org.apache.lucene.search.TestMultiCollector | org.gnit.lucenekmp.search.TestMultiCollector |
| org.apache.lucene.search.TestMultiCollectorManager | org.gnit.lucenekmp.search.TestMultiCollectorManager |
| org.apache.lucene.search.TestMultiPhraseEnum | org.gnit.lucenekmp.search.TestMultiPhraseEnum |
| org.apache.lucene.search.TestMultiPhraseQuery | org.gnit.lucenekmp.search.TestMultiPhraseQuery |
| org.apache.lucene.search.TestMultiSliceMerge | org.gnit.lucenekmp.search.TestMultiSliceMerge |
| org.apache.lucene.search.TestMultiTermConstantScore | org.gnit.lucenekmp.search.TestMultiTermConstantScore |
| org.apache.lucene.search.TestMultiTermQueryRewrites | org.gnit.lucenekmp.search.TestMultiTermQueryRewrites |
| org.apache.lucene.search.TestMultiThreadTermVectors | org.gnit.lucenekmp.search.TestMultiThreadTermVectors |
| org.apache.lucene.search.TestMultiset | org.gnit.lucenekmp.search.TestMultiset |
| org.apache.lucene.search.TestNGramPhraseQuery | org.gnit.lucenekmp.search.TestNGramPhraseQuery |
| org.apache.lucene.search.TestNearest | org.gnit.lucenekmp.search.TestNearest |
| org.apache.lucene.search.TestNeedsScores | org.gnit.lucenekmp.search.TestNeedsScores |
| org.apache.lucene.search.TestNot | org.gnit.lucenekmp.search.TestNot |
| org.apache.lucene.search.TestPhrasePrefixQuery | org.gnit.lucenekmp.search.TestPhrasePrefixQuery |
| org.apache.lucene.search.TestPhraseQuery | org.gnit.lucenekmp.search.TestPhraseQuery |
| org.apache.lucene.search.TestPointQueries | org.gnit.lucenekmp.search.TestPointQueries |
| org.apache.lucene.search.TestPositionIncrement | org.gnit.lucenekmp.search.TestPositionIncrement |
| org.apache.lucene.search.TestPositiveScoresOnlyCollector | org.gnit.lucenekmp.search.TestPositiveScoresOnlyCollector |
| org.apache.lucene.search.TestPrefixInBooleanQuery | org.gnit.lucenekmp.search.TestPrefixInBooleanQuery |
| org.apache.lucene.search.TestPrefixQuery | org.gnit.lucenekmp.search.TestPrefixQuery |
| org.apache.lucene.search.TestPrefixRandom | org.gnit.lucenekmp.search.TestPrefixRandom |
| org.apache.lucene.search.TestQueryRescorer | org.gnit.lucenekmp.search.TestQueryRescorer |
| org.apache.lucene.search.TestQueryVisitor | org.gnit.lucenekmp.search.TestQueryVisitor |
| org.apache.lucene.search.TestRangeFieldsDocValuesQuery | org.gnit.lucenekmp.search.TestRangeFieldsDocValuesQuery |
| org.apache.lucene.search.TestRegexpQuery | org.gnit.lucenekmp.search.TestRegexpQuery |
| org.apache.lucene.search.TestRegexpRandom | org.gnit.lucenekmp.search.TestRegexpRandom |
| org.apache.lucene.search.TestRegexpRandom2 | org.gnit.lucenekmp.search.TestRegexpRandom2 |
| org.apache.lucene.search.TestReqExclBulkScorer | org.gnit.lucenekmp.search.TestReqExclBulkScorer |
| org.apache.lucene.search.TestReqOptSumScorer | org.gnit.lucenekmp.search.TestReqOptSumScorer |
| org.apache.lucene.search.TestSameScoresWithThreads | org.gnit.lucenekmp.search.TestSameScoresWithThreads |
| org.apache.lucene.search.TestScoreCachingWrappingScorer | org.gnit.lucenekmp.search.TestScoreCachingWrappingScorer |
| org.apache.lucene.search.TestScorerPerf | org.gnit.lucenekmp.search.TestScorerPerf |
| org.apache.lucene.search.TestScorerUtil | org.gnit.lucenekmp.search.TestScorerUtil |
| org.apache.lucene.search.TestSearchAfter | org.gnit.lucenekmp.search.TestSearchAfter |
| org.apache.lucene.search.TestSearchWithThreads | org.gnit.lucenekmp.search.TestSearchWithThreads |
| org.apache.lucene.search.TestSearcherManager | org.gnit.lucenekmp.search.TestSearcherManager |
| org.apache.lucene.search.TestSeededKnnByteVectorQuery | org.gnit.lucenekmp.search.TestSeededKnnByteVectorQuery |
| org.apache.lucene.search.TestSeededKnnFloatVectorQuery | org.gnit.lucenekmp.search.TestSeededKnnFloatVectorQuery |
| org.apache.lucene.search.TestSegmentCacheables | org.gnit.lucenekmp.search.TestSegmentCacheables |
| org.apache.lucene.search.TestShardSearching | org.gnit.lucenekmp.search.TestShardSearching |
| org.apache.lucene.search.TestSimilarity | org.gnit.lucenekmp.search.TestSimilarity |
| org.apache.lucene.search.TestSimilarityProvider | org.gnit.lucenekmp.search.TestSimilarityProvider |
| org.apache.lucene.search.TestSimpleExplanations | org.gnit.lucenekmp.search.TestSimpleExplanations |
| org.apache.lucene.search.TestSimpleExplanationsOfNonMatches | org.gnit.lucenekmp.search.TestSimpleExplanationsOfNonMatches |
| org.apache.lucene.search.TestSimpleExplanationsWithFillerDocs | org.gnit.lucenekmp.search.TestSimpleExplanationsWithFillerDocs |
| org.apache.lucene.search.TestSimpleSearchEquivalence | org.gnit.lucenekmp.search.TestSimpleSearchEquivalence |
| org.apache.lucene.search.TestSloppyPhraseQuery | org.gnit.lucenekmp.search.TestSloppyPhraseQuery |
| org.apache.lucene.search.TestSloppyPhraseQuery2 | org.gnit.lucenekmp.search.TestSloppyPhraseQuery2 |
| org.apache.lucene.search.TestSort | org.gnit.lucenekmp.search.TestSort |
| org.apache.lucene.search.TestSortOptimization | org.gnit.lucenekmp.search.TestSortOptimization |
| org.apache.lucene.search.TestSortRandom | org.gnit.lucenekmp.search.TestSortRandom |
| org.apache.lucene.search.TestSortRescorer | org.gnit.lucenekmp.search.TestSortRescorer |
| org.apache.lucene.search.TestSortedNumericSortField | org.gnit.lucenekmp.search.TestSortedNumericSortField |
| org.apache.lucene.search.TestSortedSetSelector | org.gnit.lucenekmp.search.TestSortedSetSelector |
| org.apache.lucene.search.TestSortedSetSortField | org.gnit.lucenekmp.search.TestSortedSetSortField |
| org.apache.lucene.search.TestSynonymQuery | org.gnit.lucenekmp.search.TestSynonymQuery |
| org.apache.lucene.search.TestTaskExecutor | org.gnit.lucenekmp.search.TestTaskExecutor |
| org.apache.lucene.search.TestTermInSetQuery | org.gnit.lucenekmp.search.TestTermInSetQuery |
| org.apache.lucene.search.TestTermQuery | org.gnit.lucenekmp.search.TestTermQuery |
| org.apache.lucene.search.TestTermRangeQuery | org.gnit.lucenekmp.search.TestTermRangeQuery |
| org.apache.lucene.search.TestTermScorer | org.gnit.lucenekmp.search.TestTermScorer |
| org.apache.lucene.search.TestTimeLimitingBulkScorer | org.gnit.lucenekmp.search.TestTimeLimitingBulkScorer |
| org.apache.lucene.search.TestTopDocsCollector | org.gnit.lucenekmp.search.TestTopDocsCollector |
| org.apache.lucene.search.TestTopDocsMerge | org.gnit.lucenekmp.search.TestTopDocsMerge |
| org.apache.lucene.search.TestTopDocsRRF | org.gnit.lucenekmp.search.TestTopDocsRRF |
| org.apache.lucene.search.TestTopFieldCollector | org.gnit.lucenekmp.search.TestTopFieldCollector |
| org.apache.lucene.search.TestTopFieldCollectorEarlyTermination | org.gnit.lucenekmp.search.TestTopFieldCollectorEarlyTermination |
| org.apache.lucene.search.TestTopKnnResults | org.gnit.lucenekmp.search.TestTopKnnResults |
| org.apache.lucene.search.TestTotalHitCountCollector | org.gnit.lucenekmp.search.TestTotalHitCountCollector |
| org.apache.lucene.search.TestTotalHits | org.gnit.lucenekmp.search.TestTotalHits |
| org.apache.lucene.search.TestUsageTrackingFilterCachingPolicy | org.gnit.lucenekmp.search.TestUsageTrackingFilterCachingPolicy |
| org.apache.lucene.search.TestVectorScorer | org.gnit.lucenekmp.search.TestVectorScorer |
| org.apache.lucene.search.TestVectorSimilarityCollector | org.gnit.lucenekmp.search.TestVectorSimilarityCollector |
| org.apache.lucene.search.TestVectorSimilarityValuesSource | org.gnit.lucenekmp.search.TestVectorSimilarityValuesSource |
| org.apache.lucene.search.TestWANDScorer | org.gnit.lucenekmp.search.TestWANDScorer |
| org.apache.lucene.search.TestWildcardQuery | org.gnit.lucenekmp.search.TestWildcardQuery |
| org.apache.lucene.search.TestWildcardRandom | org.gnit.lucenekmp.search.TestWildcardRandom |
| org.apache.lucene.search.TestXYDocValuesQueries | org.gnit.lucenekmp.search.TestXYDocValuesQueries |
| org.apache.lucene.search.TestXYPointDistanceSort | org.gnit.lucenekmp.search.TestXYPointDistanceSort |
| org.apache.lucene.search.TestXYPointQueries | org.gnit.lucenekmp.search.TestXYPointQueries |
| org.apache.lucene.search.knn.TestMultiLeafKnnCollector | org.gnit.lucenekmp.search.knn.TestMultiLeafKnnCollector |
| org.apache.lucene.search.similarities.TestAxiomaticF1EXP | org.gnit.lucenekmp.search.similarities.TestAxiomaticF1EXP |
| org.apache.lucene.search.similarities.TestAxiomaticF1LOG | org.gnit.lucenekmp.search.similarities.TestAxiomaticF1LOG |
| org.apache.lucene.search.similarities.TestAxiomaticF2EXP | org.gnit.lucenekmp.search.similarities.TestAxiomaticF2EXP |
| org.apache.lucene.search.similarities.TestAxiomaticF2LOG | org.gnit.lucenekmp.search.similarities.TestAxiomaticF2LOG |
| org.apache.lucene.search.similarities.TestAxiomaticF3EXP | org.gnit.lucenekmp.search.similarities.TestAxiomaticF3EXP |
| org.apache.lucene.search.similarities.TestAxiomaticF3LOG | org.gnit.lucenekmp.search.similarities.TestAxiomaticF3LOG |
| org.apache.lucene.search.similarities.TestAxiomaticSimilarity | org.gnit.lucenekmp.search.similarities.TestAxiomaticSimilarity |
| org.apache.lucene.search.similarities.TestBM25Similarity | org.gnit.lucenekmp.search.similarities.TestBM25Similarity |
| org.apache.lucene.search.similarities.TestBasicModelG | org.gnit.lucenekmp.search.similarities.TestBasicModelG |
| org.apache.lucene.search.similarities.TestBasicModelIF | org.gnit.lucenekmp.search.similarities.TestBasicModelIF |
| org.apache.lucene.search.similarities.TestBasicModelIn | org.gnit.lucenekmp.search.similarities.TestBasicModelIn |
| org.apache.lucene.search.similarities.TestBasicModelIne | org.gnit.lucenekmp.search.similarities.TestBasicModelIne |
| org.apache.lucene.search.similarities.TestBooleanSimilarity | org.gnit.lucenekmp.search.similarities.TestBooleanSimilarity |
| org.apache.lucene.search.similarities.TestClassicSimilarity | org.gnit.lucenekmp.search.similarities.TestClassicSimilarity |
| org.apache.lucene.search.similarities.TestDistributionLL | org.gnit.lucenekmp.search.similarities.TestDistributionLL |
| org.apache.lucene.search.similarities.TestDistributionSPL | org.gnit.lucenekmp.search.similarities.TestDistributionSPL |
| org.apache.lucene.search.similarities.TestIndependenceChiSquared | org.gnit.lucenekmp.search.similarities.TestIndependenceChiSquared |
| org.apache.lucene.search.similarities.TestIndependenceSaturated | org.gnit.lucenekmp.search.similarities.TestIndependenceSaturated |
| org.apache.lucene.search.similarities.TestIndependenceStandardized | org.gnit.lucenekmp.search.similarities.TestIndependenceStandardized |
| org.apache.lucene.search.similarities.TestIndriDirichletSimilarity | org.gnit.lucenekmp.search.similarities.TestIndriDirichletSimilarity |
| org.apache.lucene.search.similarities.TestLMDirichletSimilarity | org.gnit.lucenekmp.search.similarities.TestLMDirichletSimilarity |
| org.apache.lucene.search.similarities.TestLMJelinekMercerSimilarity | org.gnit.lucenekmp.search.similarities.TestLMJelinekMercerSimilarity |
| org.apache.lucene.search.similarities.TestRawTFSimilarity | org.gnit.lucenekmp.search.similarities.TestRawTFSimilarity |
| org.apache.lucene.search.similarities.TestSimilarity2 | org.gnit.lucenekmp.search.similarities.TestSimilarity2 |
| org.apache.lucene.search.similarities.TestSimilarityBase | org.gnit.lucenekmp.search.similarities.TestSimilarityBase |
| org.apache.lucene.store.TestBufferedChecksum | org.gnit.lucenekmp.store.TestBufferedChecksum |
| org.apache.lucene.store.TestBufferedIndexInput | org.gnit.lucenekmp.store.TestBufferedIndexInput |
| org.apache.lucene.store.TestByteArrayDataInput | org.gnit.lucenekmp.store.TestByteArrayDataInput |
| org.apache.lucene.store.TestByteBuffersDataInput | org.gnit.lucenekmp.store.TestByteBuffersDataInput |
| org.apache.lucene.store.TestByteBuffersDataOutput | org.gnit.lucenekmp.store.TestByteBuffersDataOutput |
| org.apache.lucene.store.TestByteBuffersDirectory | org.gnit.lucenekmp.store.TestByteBuffersDirectory |
| org.apache.lucene.store.TestChecksumIndexInput | org.gnit.lucenekmp.store.TestChecksumIndexInput |
| org.apache.lucene.store.TestDirectory | org.gnit.lucenekmp.store.TestDirectory |
| org.apache.lucene.store.TestFileSwitchDirectory | org.gnit.lucenekmp.store.TestFileSwitchDirectory |
| org.apache.lucene.store.TestFilterDirectory | org.gnit.lucenekmp.store.TestFilterDirectory |
| org.apache.lucene.store.TestFilterIndexOutput | org.gnit.lucenekmp.store.TestFilterIndexOutput |
| org.apache.lucene.store.TestIndexOutputAlignment | org.gnit.lucenekmp.store.TestIndexOutputAlignment |
| org.apache.lucene.store.TestInputStreamDataInput | org.gnit.lucenekmp.store.TestInputStreamDataInput |
| org.apache.lucene.store.TestLockFactory | org.gnit.lucenekmp.store.TestLockFactory |
| org.apache.lucene.store.TestMMapDirectory | org.gnit.lucenekmp.store.TestMMapDirectory |
| org.apache.lucene.store.TestMultiByteBuffersDirectory | org.gnit.lucenekmp.store.TestMultiByteBuffersDirectory |
| org.apache.lucene.store.TestMultiMMap | org.gnit.lucenekmp.store.TestMultiMMap |
| org.apache.lucene.store.TestNIOFSDirectory | org.gnit.lucenekmp.store.TestNIOFSDirectory |
| org.apache.lucene.store.TestNRTCachingDirectory | org.gnit.lucenekmp.store.TestNRTCachingDirectory |
| org.apache.lucene.store.TestNativeFSLockFactory | org.gnit.lucenekmp.store.TestNativeFSLockFactory |
| org.apache.lucene.store.TestOutputStreamIndexOutput | org.gnit.lucenekmp.store.TestOutputStreamIndexOutput |
| org.apache.lucene.store.TestRateLimiter | org.gnit.lucenekmp.store.TestRateLimiter |
| org.apache.lucene.store.TestSimpleFSLockFactory | org.gnit.lucenekmp.store.TestSimpleFSLockFactory |
| org.apache.lucene.store.TestSingleInstanceLockFactory | org.gnit.lucenekmp.store.TestSingleInstanceLockFactory |
| org.apache.lucene.store.TestSleepingLockWrapper | org.gnit.lucenekmp.store.TestSleepingLockWrapper |
| org.apache.lucene.store.TestStressLockFactories | org.gnit.lucenekmp.store.TestStressLockFactories |
| org.apache.lucene.store.TestTrackingDirectoryWrapper | org.gnit.lucenekmp.store.TestTrackingDirectoryWrapper |
| org.apache.lucene.util.Test2BPagedBytes | org.gnit.lucenekmp.util.Test2BPagedBytes |
| org.apache.lucene.util.TestAttributeSource | org.gnit.lucenekmp.util.TestAttributeSource |
| org.apache.lucene.util.TestByteBlockPool | org.gnit.lucenekmp.util.TestByteBlockPool |
| org.apache.lucene.util.TestBytesRefArray | org.gnit.lucenekmp.util.TestBytesRefArray |
| org.apache.lucene.util.TestBytesRefHash | org.gnit.lucenekmp.util.TestBytesRefHash |
| org.apache.lucene.util.TestCharsRef | org.gnit.lucenekmp.util.TestCharsRef |
| org.apache.lucene.util.TestCharsRefBuilder | org.gnit.lucenekmp.util.TestCharsRefBuilder |
| org.apache.lucene.util.TestClassLoaderUtils | org.gnit.lucenekmp.util.TestClassLoaderUtils |
| org.apache.lucene.util.TestCloseableThreadLocal | org.gnit.lucenekmp.util.TestCloseableThreadLocal |
| org.apache.lucene.util.TestCollectionUtil | org.gnit.lucenekmp.util.TestCollectionUtil |
| org.apache.lucene.util.TestDocIdSetBuilder | org.gnit.lucenekmp.util.TestDocIdSetBuilder |
| org.apache.lucene.util.TestFilterIterator | org.gnit.lucenekmp.util.TestFilterIterator |
| org.apache.lucene.util.TestFixedBitDocIdSet | org.gnit.lucenekmp.util.TestFixedBitDocIdSet |
| org.apache.lucene.util.TestFixedBitSet | org.gnit.lucenekmp.util.TestFixedBitSet |
| org.apache.lucene.util.TestFixedLengthBytesRefArray | org.gnit.lucenekmp.util.TestFixedLengthBytesRefArray |
| org.apache.lucene.util.TestFrequencyTrackingRingBuffer | org.gnit.lucenekmp.util.TestFrequencyTrackingRingBuffer |
| org.apache.lucene.util.TestIOUtils | org.gnit.lucenekmp.util.TestIOUtils |
| org.apache.lucene.util.TestInPlaceMergeSorter | org.gnit.lucenekmp.util.TestInPlaceMergeSorter |
| org.apache.lucene.util.TestIntArrayDocIdSet | org.gnit.lucenekmp.util.TestIntArrayDocIdSet |
| org.apache.lucene.util.TestIntroSelector | org.gnit.lucenekmp.util.TestIntroSelector |
| org.apache.lucene.util.TestIntroSorter | org.gnit.lucenekmp.util.TestIntroSorter |
| org.apache.lucene.util.TestIntsRef | org.gnit.lucenekmp.util.TestIntsRef |
| org.apache.lucene.util.TestJavaLoggingInfoStream | org.gnit.lucenekmp.util.TestJavaLoggingInfoStream |
| org.apache.lucene.util.TestLSBRadixSorter | org.gnit.lucenekmp.util.TestLSBRadixSorter |
| org.apache.lucene.util.TestLongBitSet | org.gnit.lucenekmp.util.TestLongBitSet |
| org.apache.lucene.util.TestLongHeap | org.gnit.lucenekmp.util.TestLongHeap |
| org.apache.lucene.util.TestLongsRef | org.gnit.lucenekmp.util.TestLongsRef |
| org.apache.lucene.util.TestMSBRadixSorter | org.gnit.lucenekmp.util.TestMSBRadixSorter |
| org.apache.lucene.util.TestMathUtil | org.gnit.lucenekmp.util.TestMathUtil |
| org.apache.lucene.util.TestMergedIterator | org.gnit.lucenekmp.util.TestMergedIterator |
| org.apache.lucene.util.TestNamedSPILoader | org.gnit.lucenekmp.util.TestNamedSPILoader |
| org.apache.lucene.util.TestNotDocIdSet | org.gnit.lucenekmp.util.TestNotDocIdSet |
| org.apache.lucene.util.TestNumericUtils | org.gnit.lucenekmp.util.TestNumericUtils |
| org.apache.lucene.util.TestOfflineSorter | org.gnit.lucenekmp.util.TestOfflineSorter |
| org.apache.lucene.util.TestPagedBytes | org.gnit.lucenekmp.util.TestPagedBytes |
| org.apache.lucene.util.TestPriorityQueue | org.gnit.lucenekmp.util.TestPriorityQueue |
| org.apache.lucene.util.TestQueryBuilder | org.gnit.lucenekmp.util.TestQueryBuilder |
| org.apache.lucene.util.TestRadixSelector | org.gnit.lucenekmp.util.TestRadixSelector |
| org.apache.lucene.util.TestRamUsageEstimator | org.gnit.lucenekmp.util.TestRamUsageEstimator |
| org.apache.lucene.util.TestRecyclingByteBlockAllocator | org.gnit.lucenekmp.util.TestRecyclingByteBlockAllocator |
| org.apache.lucene.util.TestRecyclingIntBlockAllocator | org.gnit.lucenekmp.util.TestRecyclingIntBlockAllocator |
| org.apache.lucene.util.TestRoaringDocIdSet | org.gnit.lucenekmp.util.TestRoaringDocIdSet |
| org.apache.lucene.util.TestRollingBuffer | org.gnit.lucenekmp.util.TestRollingBuffer |
| org.apache.lucene.util.TestSentinelIntSet | org.gnit.lucenekmp.util.TestSentinelIntSet |
| org.apache.lucene.util.TestSetOnce | org.gnit.lucenekmp.util.TestSetOnce |
| org.apache.lucene.util.TestSloppyMath | org.gnit.lucenekmp.util.TestSloppyMath |
| org.apache.lucene.util.TestSmallFloat | org.gnit.lucenekmp.util.TestSmallFloat |
| org.apache.lucene.util.TestSparseFixedBitDocIdSet | org.gnit.lucenekmp.util.TestSparseFixedBitDocIdSet |
| org.apache.lucene.util.TestSparseFixedBitSet | org.gnit.lucenekmp.util.TestSparseFixedBitSet |
| org.apache.lucene.util.TestStableMSBRadixSorter | org.gnit.lucenekmp.util.TestStableMSBRadixSorter |
| org.apache.lucene.util.TestStressRamUsageEstimator | org.gnit.lucenekmp.util.TestStressRamUsageEstimator |
| org.apache.lucene.util.TestStringHelper | org.gnit.lucenekmp.util.TestStringHelper |
| org.apache.lucene.util.TestStringSorter | org.gnit.lucenekmp.util.TestStringSorter |
| org.apache.lucene.util.TestTimSorterWorstCase | org.gnit.lucenekmp.util.TestTimSorterWorstCase |
| org.apache.lucene.util.TestVectorUtil | org.gnit.lucenekmp.util.TestVectorUtil |
| org.apache.lucene.util.TestVersion | org.gnit.lucenekmp.util.TestVersion |
| org.apache.lucene.util.TestVirtualMethod | org.gnit.lucenekmp.util.TestVirtualMethod |
| org.apache.lucene.util.TestWeakIdentityMap | org.gnit.lucenekmp.util.TestWeakIdentityMap |
| org.apache.lucene.util.automaton.TestAutomaton | org.gnit.lucenekmp.util.automaton.TestAutomaton |
| org.apache.lucene.util.automaton.TestCompiledAutomaton | org.gnit.lucenekmp.util.automaton.TestCompiledAutomaton |
| org.apache.lucene.util.automaton.TestDeterminism | org.gnit.lucenekmp.util.automaton.TestDeterminism |
| org.apache.lucene.util.automaton.TestDeterminizeLexicon | org.gnit.lucenekmp.util.automaton.TestDeterminizeLexicon |
| org.apache.lucene.util.automaton.TestFiniteStringsIterator | org.gnit.lucenekmp.util.automaton.TestFiniteStringsIterator |
| org.apache.lucene.util.automaton.TestIntSet | org.gnit.lucenekmp.util.automaton.TestIntSet |
| org.apache.lucene.util.automaton.TestLevenshteinAutomata | org.gnit.lucenekmp.util.automaton.TestLevenshteinAutomata |
| org.apache.lucene.util.automaton.TestLimitedFiniteStringsIterator | org.gnit.lucenekmp.util.automaton.TestLimitedFiniteStringsIterator |
| org.apache.lucene.util.automaton.TestMinimize | org.gnit.lucenekmp.util.automaton.TestMinimize |
| org.apache.lucene.util.automaton.TestNFARunAutomaton | org.gnit.lucenekmp.util.automaton.TestNFARunAutomaton |
| org.apache.lucene.util.automaton.TestOperations | org.gnit.lucenekmp.util.automaton.TestOperations |
| org.apache.lucene.util.automaton.TestRegExp | org.gnit.lucenekmp.util.automaton.TestRegExp |
| org.apache.lucene.util.automaton.TestRegExpParsing | org.gnit.lucenekmp.util.automaton.TestRegExpParsing |
| org.apache.lucene.util.automaton.TestStringsToAutomaton | org.gnit.lucenekmp.util.automaton.TestStringsToAutomaton |
| org.apache.lucene.util.automaton.TestUTF32ToUTF8 | org.gnit.lucenekmp.util.automaton.TestUTF32ToUTF8 |
| org.apache.lucene.util.bkd.Test4BBKDPoints | org.gnit.lucenekmp.util.bkd.Test4BBKDPoints |
| org.apache.lucene.util.bkd.TestBKD | org.gnit.lucenekmp.util.bkd.TestBKD |
| org.apache.lucene.util.bkd.TestBKDConfig | org.gnit.lucenekmp.util.bkd.TestBKDConfig |
| org.apache.lucene.util.bkd.TestBKDRadixSelector | org.gnit.lucenekmp.util.bkd.TestBKDRadixSelector |
| org.apache.lucene.util.bkd.TestBKDRadixSort | org.gnit.lucenekmp.util.bkd.TestBKDRadixSort |
| org.apache.lucene.util.bkd.TestBKDUtil | org.gnit.lucenekmp.util.bkd.TestBKDUtil |
| org.apache.lucene.util.bkd.TestDocIdsWriter | org.gnit.lucenekmp.util.bkd.TestDocIdsWriter |
| org.apache.lucene.util.bkd.TestMutablePointTreeReaderUtils | org.gnit.lucenekmp.util.bkd.TestMutablePointTreeReaderUtils |
| org.apache.lucene.util.compress.TestFastLZ4 | org.gnit.lucenekmp.util.compress.TestFastLZ4 |
| org.apache.lucene.util.compress.TestHighLZ4 | org.gnit.lucenekmp.util.compress.TestHighLZ4 |
| org.apache.lucene.util.compress.TestLowercaseAsciiCompression | org.gnit.lucenekmp.util.compress.TestLowercaseAsciiCompression |
| org.apache.lucene.util.fst.Test2BFST | org.gnit.lucenekmp.util.fst.Test2BFST |
| org.apache.lucene.util.fst.Test2BFSTOffHeap | org.gnit.lucenekmp.util.fst.Test2BFSTOffHeap |
| org.apache.lucene.util.fst.TestBitTableUtil | org.gnit.lucenekmp.util.fst.TestBitTableUtil |
| org.apache.lucene.util.fst.TestFSTDirectAddressing | org.gnit.lucenekmp.util.fst.TestFSTDirectAddressing |
| org.apache.lucene.util.fst.TestFSTSuffixNodeCache | org.gnit.lucenekmp.util.fst.TestFSTSuffixNodeCache |
| org.apache.lucene.util.fst.TestFSTs | org.gnit.lucenekmp.util.fst.TestFSTs |
| org.apache.lucene.util.fst.TestGrowableByteArrayDataOutput | org.gnit.lucenekmp.util.fst.TestGrowableByteArrayDataOutput |
| org.apache.lucene.util.fst.TestUtil | org.gnit.lucenekmp.util.fst.TestUtil |
| org.apache.lucene.util.graph.TestGraphTokenStreamFiniteStrings | org.gnit.lucenekmp.util.graph.TestGraphTokenStreamFiniteStrings |
| org.apache.lucene.util.hnsw.TestBlockingFloatHeap | org.gnit.lucenekmp.util.hnsw.TestBlockingFloatHeap |
| org.apache.lucene.util.hnsw.TestFloatHeap | org.gnit.lucenekmp.util.hnsw.TestFloatHeap |
| org.apache.lucene.util.hnsw.TestHnswByteVectorGraph | org.gnit.lucenekmp.util.hnsw.TestHnswByteVectorGraph |
| org.apache.lucene.util.hnsw.TestHnswFloatVectorGraph | org.gnit.lucenekmp.util.hnsw.TestHnswFloatVectorGraph |
| org.apache.lucene.util.hnsw.TestHnswUtil | org.gnit.lucenekmp.util.hnsw.TestHnswUtil |
| org.apache.lucene.util.hnsw.TestNeighborArray | org.gnit.lucenekmp.util.hnsw.TestNeighborArray |
| org.apache.lucene.util.hnsw.TestNeighborQueue | org.gnit.lucenekmp.util.hnsw.TestNeighborQueue |
| org.apache.lucene.util.hnsw.TestOnHeapHnswGraph | org.gnit.lucenekmp.util.hnsw.TestOnHeapHnswGraph |
| org.apache.lucene.util.mutable.TestMutableValues | org.gnit.lucenekmp.util.mutable.TestMutableValues |
| org.apache.lucene.util.packed.TestDirectMonotonic | org.gnit.lucenekmp.util.packed.TestDirectMonotonic |
| org.apache.lucene.util.packed.TestDirectPacked | org.gnit.lucenekmp.util.packed.TestDirectPacked |
| org.apache.lucene.util.packed.TestPackedInts | org.gnit.lucenekmp.util.packed.TestPackedInts |
| org.apache.lucene.util.quantization.TestOptimizedScalarQuantizer | org.gnit.lucenekmp.util.quantization.TestOptimizedScalarQuantizer |
| org.apache.lucene.util.quantization.TestScalarQuantizedVectorSimilarity | org.gnit.lucenekmp.util.quantization.TestScalarQuantizedVectorSimilarity |
| org.apache.lucene.util.quantization.TestScalarQuantizer | org.gnit.lucenekmp.util.quantization.TestScalarQuantizer |

