# Lucene KMP Port Progress
## Package statistics (priority‑1 deps)
| Java package | KMP mapped | Classes | Ported | % | Done |
| --- | --- | --- | --- | --- | --- |
| org.apache.lucene |     org.gnit.lucenekmp | 711 | 608 | 85% | [ ] |
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
|   org.apache.lucene.index |       org.gnit.lucenekmp.index | 169 | 93 | 55% | [ ] |
|     org.apache.lucene.internal.hppc |         org.gnit.lucenekmp.internal.hppc | 14 | 14 | 100% | [x] |
|     org.apache.lucene.internal.vectorization |         org.gnit.lucenekmp.internal.vectorization | 5 | 5 | 100% | [x] |
|   org.apache.lucene.search |       org.gnit.lucenekmp.search | 138 | 115 | 83% | [ ] |
|     org.apache.lucene.search.comparators |         org.gnit.lucenekmp.search.comparators | 8 | 8 | 100% | [x] |
|     org.apache.lucene.search.knn |         org.gnit.lucenekmp.search.knn | 4 | 4 | 100% | [x] |
|     org.apache.lucene.search.similarities |         org.gnit.lucenekmp.search.similarities | 2 | 1 | 50% | [ ] |
|   org.apache.lucene.store |       org.gnit.lucenekmp.store | 36 | 33 | 91% | [ ] |
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
| org.apache.lucene.index.IndexWriter | org.gnit.lucenekmp.index.IndexWriter | 77 | 50 | 27 | 64% | [ ] |
| org.apache.lucene.index.IndexWriterConfig | org.gnit.lucenekmp.index.IndexWriterConfig | 15 | 10 | 5 | 66% | [ ] |
| org.apache.lucene.store.FSDirectory | org.gnit.lucenekmp.store.FSDirectory | 8 | 8 | 0 | 100% | [x] |
| org.apache.lucene.analysis.Analyzer | org.gnit.lucenekmp.analysis.Analyzer | 10 | 10 | 0 | 100% | [x] |
| org.apache.lucene.document.Document | org.gnit.lucenekmp.document.Document | 2 | 2 | 0 | 100% | [x] |
| org.apache.lucene.document.Field | org.gnit.lucenekmp.document.Field | 12 | 12 | 0 | 100% | [x] |
| org.apache.lucene.document.IntPoint | org.gnit.lucenekmp.document.IntPoint | 6 | 6 | 0 | 100% | [x] |
| org.apache.lucene.document.StoredField | org.gnit.lucenekmp.document.StoredField | 3 | 3 | 0 | 100% | [x] |
| org.apache.lucene.document.TextField | org.gnit.lucenekmp.document.TextField | 4 | 4 | 0 | 100% | [x] |
| org.apache.lucene.index.DirectoryReader | org.gnit.lucenekmp.index.DirectoryReader | 6 | 4 | 2 | 66% | [ ] |
| org.apache.lucene.index.StandardDirectoryReader | org.gnit.lucenekmp.index.StandardDirectoryReader | 13 | 11 | 2 | 84% | [ ] |
| org.apache.lucene.queryparser.classic.QueryParser | org.gnit.lucenekmp.queryparser.classic.QueryParser | 0 | 0 | 0 | 100% | [ ] |
| org.apache.lucene.search.IndexSearcher | org.gnit.lucenekmp.search.IndexSearcher | 42 | 32 | 10 | 76% | [ ] |
| org.apache.lucene.store.FSLockFactory | org.gnit.lucenekmp.store.FSLockFactory | 4 | 4 | 0 | 100% | [ ] |
| org.apache.lucene.store.NIOFSDirectory | org.gnit.lucenekmp.store.NIOFSDirectory | 5 | 5 | 0 | 100% | [x] |
| org.apache.lucene.document.IntPoint | org.gnit.lucenekmp.document.IntPoint | 6 | 6 | 0 | 100% | [x] |
| org.apache.lucene.search.Query | org.gnit.lucenekmp.search.Query | 3 | 3 | 0 | 100% | [ ] |
| org.apache.lucene.search.BooleanQuery | org.gnit.lucenekmp.search.BooleanQuery | 11 | 11 | 0 | 100% | [x] |
| org.apache.lucene.search.BooleanClause | org.gnit.lucenekmp.search.BooleanClause | 0 | 0 | 0 | 100% | [ ] |
| org.apache.lucene.search.Sort | org.gnit.lucenekmp.search.Sort | 0 | 0 | 0 | 100% | [x] |
| org.apache.lucene.search.SortField | org.gnit.lucenekmp.search.SortField | 19 | 19 | 0 | 100% | [x] |
| TOTAL |  | 170 | 130 | 40 | 76% | [ ] |

## KMP Deps To Port
| Java FQN | Expected KMP FQN |
| --- | --- |
| org.apache.lucene.index.ApproximatePriorityQueue | org.gnit.lucenekmp.index.ApproximatePriorityQueue |
| org.apache.lucene.index.BaseCompositeReader | org.gnit.lucenekmp.index.BaseCompositeReader |
| org.apache.lucene.index.BinaryDocValuesFieldUpdates | org.gnit.lucenekmp.index.BinaryDocValuesFieldUpdates |
| org.apache.lucene.index.BinaryDocValuesWriter | org.gnit.lucenekmp.index.BinaryDocValuesWriter |
| org.apache.lucene.index.BufferedUpdatesStream | org.gnit.lucenekmp.index.BufferedUpdatesStream |
| org.apache.lucene.index.ByteSlicePool | org.gnit.lucenekmp.index.ByteSlicePool |
| org.apache.lucene.index.ByteSliceReader | org.gnit.lucenekmp.index.ByteSliceReader |
| org.apache.lucene.index.CachingMergeContext | org.gnit.lucenekmp.index.CachingMergeContext |
| org.apache.lucene.index.CheckIndex | org.gnit.lucenekmp.index.CheckIndex |
| org.apache.lucene.index.ConcurrentApproximatePriorityQueue | org.gnit.lucenekmp.index.ConcurrentApproximatePriorityQueue |
| org.apache.lucene.index.ConcurrentMergeScheduler | org.gnit.lucenekmp.index.ConcurrentMergeScheduler |
| org.apache.lucene.index.DirectoryReader | org.gnit.lucenekmp.index.DirectoryReader |
| org.apache.lucene.index.DocValuesFieldUpdates | org.gnit.lucenekmp.index.DocValuesFieldUpdates |
| org.apache.lucene.index.DocValuesLeafReader | org.gnit.lucenekmp.index.DocValuesLeafReader |
| org.apache.lucene.index.DocValuesWriter | org.gnit.lucenekmp.index.DocValuesWriter |
| org.apache.lucene.index.DocumentsWriter | org.gnit.lucenekmp.index.DocumentsWriter |
| org.apache.lucene.index.DocumentsWriterDeleteQueue | org.gnit.lucenekmp.index.DocumentsWriterDeleteQueue |
| org.apache.lucene.index.DocumentsWriterFlushControl | org.gnit.lucenekmp.index.DocumentsWriterFlushControl |
| org.apache.lucene.index.DocumentsWriterFlushQueue | org.gnit.lucenekmp.index.DocumentsWriterFlushQueue |
| org.apache.lucene.index.DocumentsWriterPerThread | org.gnit.lucenekmp.index.DocumentsWriterPerThread |
| org.apache.lucene.index.DocumentsWriterPerThreadPool | org.gnit.lucenekmp.index.DocumentsWriterPerThreadPool |
| org.apache.lucene.index.DocumentsWriterStallControl | org.gnit.lucenekmp.index.DocumentsWriterStallControl |
| org.apache.lucene.index.FilterCodecReader | org.gnit.lucenekmp.index.FilterCodecReader |
| org.apache.lucene.index.FilterMergePolicy | org.gnit.lucenekmp.index.FilterMergePolicy |
| org.apache.lucene.index.FlushByRamOrCountsPolicy | org.gnit.lucenekmp.index.FlushByRamOrCountsPolicy |
| org.apache.lucene.index.FlushPolicy | org.gnit.lucenekmp.index.FlushPolicy |
| org.apache.lucene.index.FreqProxFields | org.gnit.lucenekmp.index.FreqProxFields |
| org.apache.lucene.index.FreqProxTermsWriter | org.gnit.lucenekmp.index.FreqProxTermsWriter |
| org.apache.lucene.index.FreqProxTermsWriterPerField | org.gnit.lucenekmp.index.FreqProxTermsWriterPerField |
| org.apache.lucene.index.FrozenBufferedUpdates | org.gnit.lucenekmp.index.FrozenBufferedUpdates |
| org.apache.lucene.index.IndexDeletionPolicy | org.gnit.lucenekmp.index.IndexDeletionPolicy |
| org.apache.lucene.index.IndexFileDeleter | org.gnit.lucenekmp.index.IndexFileDeleter |
| org.apache.lucene.index.IndexNotFoundException | org.gnit.lucenekmp.index.IndexNotFoundException |
| org.apache.lucene.index.IndexWriterConfig | org.gnit.lucenekmp.index.IndexWriterConfig |
| org.apache.lucene.index.IndexWriterEventListener | org.gnit.lucenekmp.index.IndexWriterEventListener |
| org.apache.lucene.index.IndexingChain | org.gnit.lucenekmp.index.IndexingChain |
| org.apache.lucene.index.KeepOnlyLastCommitDeletionPolicy | org.gnit.lucenekmp.index.KeepOnlyLastCommitDeletionPolicy |
| org.apache.lucene.index.LiveIndexWriterConfig | org.gnit.lucenekmp.index.LiveIndexWriterConfig |
| org.apache.lucene.index.LockableConcurrentApproximatePriorityQueue | org.gnit.lucenekmp.index.LockableConcurrentApproximatePriorityQueue |
| org.apache.lucene.index.MergeRateLimiter | org.gnit.lucenekmp.index.MergeRateLimiter |
| org.apache.lucene.index.MergeScheduler | org.gnit.lucenekmp.index.MergeScheduler |
| org.apache.lucene.index.MergeTrigger | org.gnit.lucenekmp.index.MergeTrigger |
| org.apache.lucene.index.MultiBits | org.gnit.lucenekmp.index.MultiBits |
| org.apache.lucene.index.MultiDocValues | org.gnit.lucenekmp.index.MultiDocValues |
| org.apache.lucene.index.MultiReader | org.gnit.lucenekmp.index.MultiReader |
| org.apache.lucene.index.NormValuesWriter | org.gnit.lucenekmp.index.NormValuesWriter |
| org.apache.lucene.index.NumericDocValuesFieldUpdates | org.gnit.lucenekmp.index.NumericDocValuesFieldUpdates |
| org.apache.lucene.index.NumericDocValuesWriter | org.gnit.lucenekmp.index.NumericDocValuesWriter |
| org.apache.lucene.index.OneMergeWrappingMergePolicy | org.gnit.lucenekmp.index.OneMergeWrappingMergePolicy |
| org.apache.lucene.index.ParallelPostingsArray | org.gnit.lucenekmp.index.ParallelPostingsArray |
| org.apache.lucene.index.PendingDeletes | org.gnit.lucenekmp.index.PendingDeletes |
| org.apache.lucene.index.PendingSoftDeletes | org.gnit.lucenekmp.index.PendingSoftDeletes |
| org.apache.lucene.index.PointValuesWriter | org.gnit.lucenekmp.index.PointValuesWriter |
| org.apache.lucene.index.ReaderPool | org.gnit.lucenekmp.index.ReaderPool |
| org.apache.lucene.index.ReadersAndUpdates | org.gnit.lucenekmp.index.ReadersAndUpdates |
| org.apache.lucene.index.SegmentCoreReaders | org.gnit.lucenekmp.index.SegmentCoreReaders |
| org.apache.lucene.index.SegmentDocValues | org.gnit.lucenekmp.index.SegmentDocValues |
| org.apache.lucene.index.SegmentDocValuesProducer | org.gnit.lucenekmp.index.SegmentDocValuesProducer |
| org.apache.lucene.index.SegmentInfos | org.gnit.lucenekmp.index.SegmentInfos |
| org.apache.lucene.index.SegmentMerger | org.gnit.lucenekmp.index.SegmentMerger |
| org.apache.lucene.index.SegmentReader | org.gnit.lucenekmp.index.SegmentReader |
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
| org.apache.lucene.index.TermsHash | org.gnit.lucenekmp.index.TermsHash |
| org.apache.lucene.index.TermsHashPerField | org.gnit.lucenekmp.index.TermsHashPerField |
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
| org.apache.lucene.search.similarities.BM25Similarity | org.gnit.lucenekmp.search.similarities.BM25Similarity |
| org.apache.lucene.store.LockValidatingDirectoryWrapper | org.gnit.lucenekmp.store.LockValidatingDirectoryWrapper |
| org.apache.lucene.store.RateLimitedIndexOutput | org.gnit.lucenekmp.store.RateLimitedIndexOutput |
| org.apache.lucene.store.RateLimiter | org.gnit.lucenekmp.store.RateLimiter |

