# Lucene KMP Port Progress

## Priority 1 Dependencies (Java)
* Dependencies: 289 at Depth 1
* Dependencies: 398 at Depth 2
* Dependencies: 380 at Depth 3
* Dependencies: 285 at Depth 4
* Dependencies: 82 at Depth 5
* Dependencies: 80 at Depth 6
* Dependencies: 34 at Depth 7
* Dependencies: 3 at Depth 8
### Total priority-1 classes and their dependencies: 707

## Unit Test Dependencies (Java)
### total unit test classes: 6077
* Unit Test Dependencies: 1964 at Depth 1
* Unit Test Dependencies: 1602 at Depth 2
* Unit Test Dependencies: 1675 at Depth 3
* Unit Test Dependencies: 594 at Depth 4
* Unit Test Dependencies: 139 at Depth 5
* Unit Test Dependencies: 23 at Depth 6
* Unit Test Dependencies: 2 at Depth 7
### Total Unit Test and their Dependencies: 2622

## Priority 1 Dependencies (KMP)
### Total KMP classes: 1411

## Unit Test Dependencies (KMP)
### Total KMP Unit Test classes: 2110

## Progress Table for Lucene Classes
| Java Class | KMP Class | Depth | Class Ported | Java Core Methods | KMP Core Methods | Semantic Progress | Missing Core Methods |
| --- | --- | --- | --- | --- | --- | --- | --- |
| [org.apache.lucene.analysis.CharArrayMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/CharArrayMap.java) | org.gnit.lucenekmp.analysis.CharArrayMap | Depth 4 | [x] | 23 | 21 | 87% | 3 |
| [org.apache.lucene.analysis.StopwordAnalyzerBase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/StopwordAnalyzerBase.java) | org.gnit.lucenekmp.analysis.StopwordAnalyzerBase | Depth 2 | [x] | 2 | 2 | 76% | 0 |
| [org.apache.lucene.analysis.tokenattributes.CharTermAttributeImpl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/CharTermAttributeImpl.java) | org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttributeImpl | Depth 3 | [x] | 17 | 18 | 94% | 1 |
| [org.apache.lucene.codecs.Codec](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/Codec.java) | org.gnit.lucenekmp.codecs.Codec | Depth 2 | [x] | 14 | 15 | 93% | 1 |
| [org.apache.lucene.codecs.DocValuesFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/DocValuesFormat.java) | org.gnit.lucenekmp.codecs.DocValuesFormat | Depth 3 | [x] | 5 | 7 | 82% | 1 |
| [org.apache.lucene.codecs.KnnVectorsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/KnnVectorsFormat.java) | org.gnit.lucenekmp.codecs.KnnVectorsFormat | Depth 3 | [x] | 6 | 13 | 84% | 1 |
| [org.apache.lucene.codecs.PostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/PostingsFormat.java) | org.gnit.lucenekmp.codecs.PostingsFormat | Depth 3 | [x] | 5 | 8 | 82% | 1 |
| [org.apache.lucene.codecs.hnsw.FlatVectorsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/hnsw/FlatVectorsReader.java) | org.gnit.lucenekmp.codecs.hnsw.FlatVectorsReader | Depth 3 | [x] | 4 | 4 | 88% | 0 |
| [org.apache.lucene.document.DateTools](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/DateTools.java) | org.gnit.lucenekmp.document.DateTools | Depth 3 | [x] | 6 | 12 | 50% | 3 |
| [org.apache.lucene.document.DocValuesLongHashSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/DocValuesLongHashSet.java) | org.gnit.lucenekmp.document.DocValuesLongHashSet | Depth 4 | [x] | 3 | 5 | 87% | 0 |
| [org.apache.lucene.index.ApproximatePriorityQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ApproximatePriorityQueue.java) | org.gnit.lucenekmp.index.ApproximatePriorityQueue | Depth 5 | [x] | 4 | 4 | 56% | 2 |
| [org.apache.lucene.index.CheckIndex](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/CheckIndex.java) | org.gnit.lucenekmp.index.CheckIndex | Depth 3 | [x] | 53 | 65 | 94% | 3 |
| [org.apache.lucene.index.ConcurrentApproximatePriorityQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ConcurrentApproximatePriorityQueue.java) | org.gnit.lucenekmp.index.ConcurrentApproximatePriorityQueue | Depth 4 | [x] | 4 | 4 | 34% | 3 |
| [org.apache.lucene.index.ConcurrentMergeScheduler](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ConcurrentMergeScheduler.java) | org.gnit.lucenekmp.index.ConcurrentMergeScheduler | Depth 3 | [x] | 28 | 35 | 84% | 5 |
| [org.apache.lucene.index.DocumentsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriter.java) | org.gnit.lucenekmp.index.DocumentsWriter | Depth 1 | [x] | 27 | 40 | 90% | 3 |
| [org.apache.lucene.index.DocumentsWriterFlushControl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriterFlushControl.java) | org.gnit.lucenekmp.index.DocumentsWriterFlushControl | Depth 1 | [x] | 37 | 39 | 99% | 0 |
| [org.apache.lucene.index.DocumentsWriterPerThreadPool](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriterPerThreadPool.java) | org.gnit.lucenekmp.index.DocumentsWriterPerThreadPool | Depth 2 | [x] | 10 | 10 | 91% | 1 |
| [org.apache.lucene.index.DocumentsWriterStallControl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriterStallControl.java) | org.gnit.lucenekmp.index.DocumentsWriterStallControl | Depth 2 | [x] | 8 | 8 | 67% | 3 |
| [org.apache.lucene.index.FieldInfo](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FieldInfo.java) | org.gnit.lucenekmp.index.FieldInfo | Depth 1 | [x] | 21 | 21 | 98% | 0 |
| [org.apache.lucene.index.IndexWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexWriter.java) | org.gnit.lucenekmp.index.IndexWriter | Depth 1 | [x] | 141 | 202 | 98% | 2 |
| [org.apache.lucene.index.IndexWriterConfig](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexWriterConfig.java) | org.gnit.lucenekmp.index.IndexWriterConfig | Depth 1 | [x] | 26 | 26 | 77% | 0 |
| [org.apache.lucene.index.LeafMetaData](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/LeafMetaData.java) | org.gnit.lucenekmp.index.LeafMetaData | Depth 1 | [x] | 3 | 0 | 14% | 3 |
| [org.apache.lucene.index.LiveIndexWriterConfig](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/LiveIndexWriterConfig.java) | org.gnit.lucenekmp.index.LiveIndexWriterConfig | Depth 1 | [x] | 6 | 6 | 94% | 0 |
| [org.apache.lucene.index.LockableConcurrentApproximatePriorityQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/LockableConcurrentApproximatePriorityQueue.java) | org.gnit.lucenekmp.index.LockableConcurrentApproximatePriorityQueue | Depth 3 | [x] | 4 | 5 | 25% | 3 |
| [org.apache.lucene.index.MergeScheduler](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MergeScheduler.java) | org.gnit.lucenekmp.index.MergeScheduler | Depth 1 | [x] | 6 | 6 | 84% | 1 |
| [org.apache.lucene.index.ReaderPool](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ReaderPool.java) | org.gnit.lucenekmp.index.ReaderPool | Depth 2 | [x] | 16 | 30 | 94% | 1 |
| [org.apache.lucene.index.ReaderSlice](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ReaderSlice.java) | org.gnit.lucenekmp.index.ReaderSlice | Depth 3 | [x] | 1 | 0 | 64% | 1 |
| [org.apache.lucene.index.SortFieldProvider](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortFieldProvider.java) | org.gnit.lucenekmp.index.SortFieldProvider | Depth 3 | [x] | 6 | 6 | 84% | 1 |
| [org.apache.lucene.index.StandardDirectoryReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/StandardDirectoryReader.java) | org.gnit.lucenekmp.index.StandardDirectoryReader | Depth 1 | [x] | 14 | 19 | 94% | 1 |
| [org.apache.lucene.index.StoredFieldDataInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/StoredFieldDataInput.java) | org.gnit.lucenekmp.index.StoredFieldDataInput | Depth 1 | [x] | 0 | 0 | 80% | 0 |
| [org.apache.lucene.internal.hppc.IntArrayList](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/IntArrayList.java) | org.gnit.lucenekmp.internal.hppc.IntArrayList | Depth 3 | [x] | 28 | 28 | 98% | 0 |
| [org.apache.lucene.internal.hppc.LongArrayList](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/LongArrayList.java) | org.gnit.lucenekmp.internal.hppc.LongArrayList | Depth 3 | [x] | 28 | 29 | 98% | 0 |
| [org.apache.lucene.internal.tests.TestSecrets](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/tests/TestSecrets.java) | org.gnit.lucenekmp.internal.tests.TestSecrets | Depth 1 | [x] | 2 | 8 | 92% | 0 |
| [org.apache.lucene.internal.vectorization.DefaultVectorUtilSupport](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/vectorization/DefaultVectorUtilSupport.java) | org.gnit.lucenekmp.internal.vectorization.DefaultVectorUtilSupport | Depth 6 | [x] | 11 | 11 | 81% | 2 |
| [org.apache.lucene.internal.vectorization.VectorizationProvider](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/vectorization/VectorizationProvider.java) | org.gnit.lucenekmp.internal.vectorization.VectorizationProvider | Depth 5 | [x] | 4 | 2 | 59% | 2 |
| [org.apache.lucene.search.AbstractKnnVectorQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/AbstractKnnVectorQuery.java) | org.gnit.lucenekmp.search.AbstractKnnVectorQuery | Depth 6 | [x] | 12 | 12 | 64% | 5 |
| [org.apache.lucene.search.CollectionStatistics](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/CollectionStatistics.java) | org.gnit.lucenekmp.search.CollectionStatistics | Depth 1 | [x] | 4 | 0 | 23% | 4 |
| [org.apache.lucene.search.DisjunctionMaxScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisjunctionMaxScorer.java) | org.gnit.lucenekmp.search.DisjunctionMaxScorer | Depth 4 | [x] | 3 | 3 | 83% | 0 |
| [org.apache.lucene.search.FieldValueHitQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FieldValueHitQueue.java) | org.gnit.lucenekmp.search.FieldValueHitQueue | Depth 3 | [x] | 4 | 4 | 82% | 1 |
| [org.apache.lucene.search.KnnByteVectorQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/KnnByteVectorQuery.java) | org.gnit.lucenekmp.search.KnnByteVectorQuery | Depth 4 | [x] | 2 | 2 | 37% | 2 |
| [org.apache.lucene.search.KnnFloatVectorQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/KnnFloatVectorQuery.java) | org.gnit.lucenekmp.search.KnnFloatVectorQuery | Depth 4 | [x] | 2 | 2 | 37% | 2 |
| [org.apache.lucene.search.LRUQueryCache](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/LRUQueryCache.java) | org.gnit.lucenekmp.search.LRUQueryCache | Depth 2 | [x] | 23 | 25 | 81% | 5 |
| [org.apache.lucene.search.Multiset](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/Multiset.java) | org.gnit.lucenekmp.search.Multiset | Depth 2 | [x] | 4 | 5 | 82% | 1 |
| [org.apache.lucene.search.ScorerUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ScorerUtil.java) | org.gnit.lucenekmp.search.ScorerUtil | Depth 2 | [x] | 4 | 4 | 75% | 1 |
| [org.apache.lucene.search.SloppyPhraseMatcher](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/SloppyPhraseMatcher.java) | org.gnit.lucenekmp.search.SloppyPhraseMatcher | Depth 4 | [x] | 29 | 29 | 93% | 2 |
| [org.apache.lucene.search.TaskExecutor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TaskExecutor.java) | org.gnit.lucenekmp.search.TaskExecutor | Depth 2 | [x] | 4 | 6 | 76% | 1 |
| [org.apache.lucene.search.TermRangeQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TermRangeQuery.java) | org.gnit.lucenekmp.search.TermRangeQuery | Depth 2 | [x] | 5 | 4 | 85% | 1 |
| [org.apache.lucene.search.TermScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TermScorer.java) | org.gnit.lucenekmp.search.TermScorer | Depth 2 | [x] | 4 | 4 | 90% | 0 |
| [org.apache.lucene.search.TermStatistics](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TermStatistics.java) | org.gnit.lucenekmp.search.TermStatistics | Depth 1 | [x] | 2 | 0 | 18% | 2 |
| [org.apache.lucene.search.WildcardQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/WildcardQuery.java) | org.gnit.lucenekmp.search.WildcardQuery | Depth 2 | [x] | 1 | 1 | 66% | 0 |
| [org.apache.lucene.store.ByteBuffersDataOutput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/ByteBuffersDataOutput.java) | org.gnit.lucenekmp.store.ByteBuffersDataOutput | Depth 4 | [x] | 25 | 29 | 92% | 2 |
| [org.apache.lucene.store.FlushInfo](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/FlushInfo.java) | org.gnit.lucenekmp.store.FlushInfo | Depth 1 | [x] | 2 | 0 | 23% | 2 |
| [org.apache.lucene.store.IOContext](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/IOContext.java) | org.gnit.lucenekmp.store.IOContext | Depth 2 | [x] | 4 | 1 | 30% | 3 |
| [org.apache.lucene.store.MergeInfo](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/MergeInfo.java) | org.gnit.lucenekmp.store.MergeInfo | Depth 1 | [x] | 3 | 0 | 28% | 3 |
| [org.apache.lucene.store.RandomAccessInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/RandomAccessInput.java) | org.gnit.lucenekmp.store.RandomAccessInput | Depth 2 | [x] | 6 | 6 | 91% | 0 |
| [org.apache.lucene.util.ArrayUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/ArrayUtil.java) | org.gnit.lucenekmp.util.ArrayUtil | Depth 2 | [x] | 61 | 60 | 90% | 6 |
| [org.apache.lucene.util.AttributeFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/AttributeFactory.java) | org.gnit.lucenekmp.util.AttributeFactory | Depth 2 | [x] | 3 | 15 | 66% | 1 |
| [org.apache.lucene.util.AttributeSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/AttributeSource.java) | org.gnit.lucenekmp.util.AttributeSource | Depth 2 | [x] | 15 | 18 | 94% | 1 |
| [org.apache.lucene.util.CharsRef](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/CharsRef.java) | org.gnit.lucenekmp.util.CharsRef | Depth 5 | [x] | 6 | 6 | 67% | 2 |
| [org.apache.lucene.util.ClassLoaderUtils](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/ClassLoaderUtils.java) | org.gnit.lucenekmp.util.ClassLoaderUtils | Depth 3 | [x] | 1 | 1 | 0% | 1 |
| [org.apache.lucene.util.CloseableThreadLocal](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/CloseableThreadLocal.java) | org.gnit.lucenekmp.util.CloseableThreadLocal | Depth 1 | [x] | 5 | 3 | 64% | 2 |
| [org.apache.lucene.util.CollectionUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/CollectionUtil.java) | org.gnit.lucenekmp.util.CollectionUtil | Depth 2 | [x] | 6 | 6 | 66% | 2 |
| [org.apache.lucene.util.FileDeleter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/FileDeleter.java) | org.gnit.lucenekmp.util.FileDeleter | Depth 3 | [x] | 13 | 31 | 92% | 1 |
| [org.apache.lucene.util.HotspotVMOptions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/HotspotVMOptions.java) | org.gnit.lucenekmp.util.HotspotVMOptions | Depth 2 | [] | 1 | 0 | 0% | 1 |
| [org.apache.lucene.util.IOUtils](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/IOUtils.java) | org.gnit.lucenekmp.util.IOUtils | Depth 2 | [x] | 19 | 25 | 63% | 7 |
| [org.apache.lucene.util.IgnoreRandomChains](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/IgnoreRandomChains.java) | org.gnit.lucenekmp.util.IgnoreRandomChains | Depth 4 | [] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.util.NamedSPILoader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/NamedSPILoader.java) | org.gnit.lucenekmp.util.NamedSPILoader | Depth 2 | [x] | 5 | 5 | 82% | 1 |
| [org.apache.lucene.util.NamedThreadFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/NamedThreadFactory.java) | org.gnit.lucenekmp.util.NamedThreadFactory | Depth 3 | [x] | 2 | 2 | 38% | 1 |
| [org.apache.lucene.util.RamUsageEstimator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/RamUsageEstimator.java) | org.gnit.lucenekmp.util.RamUsageEstimator | Depth 3 | [x] | 42 | 45 | 97% | 1 |
| [org.apache.lucene.util.SuppressForbidden](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/SuppressForbidden.java) | org.gnit.lucenekmp.util.SuppressForbidden | Depth 2 | [] | 0 | 0 | 0% | 0 |
| [org.apache.lucene.util.automaton.RegExp](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/RegExp.java) | org.gnit.lucenekmp.util.automaton.RegExp | Depth 3 | [x] | 49 | 52 | 98% | 1 |
| [org.apache.lucene.util.automaton.StringsToAutomaton](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/StringsToAutomaton.java) | org.gnit.lucenekmp.util.automaton.StringsToAutomaton | Depth 5 | [x] | 7 | 7 | 85% | 1 |
| [org.apache.lucene.util.bkd.BKDConfig](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/bkd/BKDConfig.java) | org.gnit.lucenekmp.util.bkd.BKDConfig | Depth 3 | [x] | 7 | 3 | 47% | 4 |
| [org.apache.lucene.util.hnsw.BlockingFloatHeap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/BlockingFloatHeap.java) | org.gnit.lucenekmp.util.hnsw.BlockingFloatHeap | Depth 7 | [x] | 6 | 9 | 76% | 0 |


## Detailed Method Analysis Reports

## Detailed Analysis: org.apache.lucene.analysis.CharArrayMap -> org.gnit.lucenekmp.analysis.CharArrayMap

### Method Categories:
- **Java Core Business Logic**: 23
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 4
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 21
- **KMP Property Accessors**: 7
- **KMP Auto-Generated**: 4
- **KMP Synthetic**: 2
- **Semantic Completion**: 87%

### Missing Core Methods:
- `public V put(java.lang.CharSequence, V)`
- `public V put(char[], V)`
- `public org.apache.lucene.analysis.CharArraySet keySet()`


## Detailed Analysis: org.apache.lucene.analysis.StopwordAnalyzerBase -> org.gnit.lucenekmp.analysis.StopwordAnalyzerBase

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 76%


## Detailed Analysis: org.apache.lucene.analysis.tokenattributes.CharTermAttributeImpl -> org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttributeImpl

### Method Categories:
- **Java Core Business Logic**: 17
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 4
- **Java Synthetic**: 5
- **KMP Core Business Logic**: 18
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 4
- **KMP Synthetic**: 7
- **Semantic Completion**: 94%

### Missing Core Methods:
- `public char charAt(int)`


## Detailed Analysis: org.apache.lucene.codecs.Codec -> org.gnit.lucenekmp.codecs.Codec

### Method Categories:
- **Java Core Business Logic**: 14
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 15
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 2
- **Semantic Completion**: 93%

### Missing Core Methods:
- `public static void reloadCodecs(java.lang.ClassLoader)`


## Detailed Analysis: org.apache.lucene.codecs.DocValuesFormat -> org.gnit.lucenekmp.codecs.DocValuesFormat

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 7
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 1
- **Semantic Completion**: 82%

### Missing Core Methods:
- `public static void reloadDocValuesFormats(java.lang.ClassLoader)`


## Detailed Analysis: org.apache.lucene.codecs.KnnVectorsFormat -> org.gnit.lucenekmp.codecs.KnnVectorsFormat

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 13
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 84%

### Missing Core Methods:
- `public static void reloadKnnVectorsFormat(java.lang.ClassLoader)`


## Detailed Analysis: org.apache.lucene.codecs.PostingsFormat -> org.gnit.lucenekmp.codecs.PostingsFormat

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 8
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 2
- **Semantic Completion**: 82%

### Missing Core Methods:
- `public static void reloadPostingsFormats(java.lang.ClassLoader)`


## Detailed Analysis: org.apache.lucene.codecs.hnsw.FlatVectorsReader -> org.gnit.lucenekmp.codecs.hnsw.FlatVectorsReader

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 88%


## Detailed Analysis: org.apache.lucene.document.DateTools -> org.gnit.lucenekmp.document.DateTools

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 12
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 50%

### Missing Core Methods:
- `public static java.lang.String dateToString(java.util.Date, org.apache.lucene.document.DateTools$Resolution)`
- `public static java.util.Date stringToDate(java.lang.String)`
- `public static java.util.Date round(java.util.Date, org.apache.lucene.document.DateTools$Resolution)`


## Detailed Analysis: org.apache.lucene.document.DocValuesLongHashSet -> org.gnit.lucenekmp.document.DocValuesLongHashSet

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 3
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 8
- **KMP Auto-Generated**: 3
- **KMP Synthetic**: 1
- **Semantic Completion**: 87%


## Detailed Analysis: org.apache.lucene.index.ApproximatePriorityQueue -> org.gnit.lucenekmp.index.ApproximatePriorityQueue

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 56%

### Missing Core Methods:
- `package-private boolean contains(java.lang.Object)`
- `package-private boolean remove(java.lang.Object)`


## Detailed Analysis: org.apache.lucene.index.CheckIndex -> org.gnit.lucenekmp.index.CheckIndex

### Method Categories:
- **Java Core Business Logic**: 53
- **Java Property Accessors**: 6
- **Java Auto-Generated**: 0
- **Java Synthetic**: 4
- **KMP Core Business Logic**: 65
- **KMP Property Accessors**: 6
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 15
- **Semantic Completion**: 94%

### Missing Core Methods:
- `public org.apache.lucene.index.CheckIndex$Status checkIndex()`
- `public static org.apache.lucene.index.CheckIndex$Status$TermIndexStatus testPostings(org.apache.lucene.index.CodecReader, java.io.PrintStream)`
- `public static org.apache.lucene.index.CheckIndex$Status$TermVectorStatus testTermVectors(org.apache.lucene.index.CodecReader, java.io.PrintStream)`


## Detailed Analysis: org.apache.lucene.index.ConcurrentApproximatePriorityQueue -> org.gnit.lucenekmp.index.ConcurrentApproximatePriorityQueue

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 34%

### Missing Core Methods:
- `package-private void add(T, long)`
- `package-private boolean contains(java.lang.Object)`
- `package-private boolean remove(java.lang.Object)`


## Detailed Analysis: org.apache.lucene.index.ConcurrentMergeScheduler -> org.gnit.lucenekmp.index.ConcurrentMergeScheduler

### Method Categories:
- **Java Core Business Logic**: 28
- **Java Property Accessors**: 7
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 35
- **KMP Property Accessors**: 15
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 17
- **Semantic Completion**: 84%

### Missing Core Methods:
- `package-private void removeMergeThread()`
- `public void sync()`
- `public void merge(org.apache.lucene.index.MergeScheduler$MergeSource, org.apache.lucene.index.MergeTrigger)`
- `protected void doStall()`
- `package-private void runOnMergeFinished(org.apache.lucene.index.MergeScheduler$MergeSource)`


## Detailed Analysis: org.apache.lucene.index.DocumentsWriter -> org.gnit.lucenekmp.index.DocumentsWriter

### Method Categories:
- **Java Core Business Logic**: 27
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 0
- **Java Synthetic**: 11
- **KMP Core Business Logic**: 40
- **KMP Property Accessors**: 9
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 7
- **Semantic Completion**: 90%

### Missing Core Methods:
- `package-private void abort()`
- `package-private java.io.Closeable lockAndAbortAll()`
- `private void doFlush(org.apache.lucene.index.DocumentsWriterPerThread)`


## Detailed Analysis: org.apache.lucene.index.DocumentsWriterFlushControl -> org.gnit.lucenekmp.index.DocumentsWriterFlushControl

### Method Categories:
- **Java Core Business Logic**: 37
- **Java Property Accessors**: 9
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 39
- **KMP Property Accessors**: 10
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 3
- **Semantic Completion**: 99%


## Detailed Analysis: org.apache.lucene.index.DocumentsWriterPerThreadPool -> org.gnit.lucenekmp.index.DocumentsWriterPerThreadPool

### Method Categories:
- **Java Core Business Logic**: 10
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 10
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 4
- **Semantic Completion**: 91%

### Missing Core Methods:
- `package-private void marksAsFreeAndUnlock(org.apache.lucene.index.DocumentsWriterPerThread)`


## Detailed Analysis: org.apache.lucene.index.DocumentsWriterStallControl -> org.gnit.lucenekmp.index.DocumentsWriterStallControl

### Method Categories:
- **Java Core Business Logic**: 8
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 8
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 67%

### Missing Core Methods:
- `private void incWaiters()`
- `private void decrWaiters()`
- `package-private boolean isThreadQueued(java.lang.Thread)`


## Detailed Analysis: org.apache.lucene.index.FieldInfo -> org.gnit.lucenekmp.index.FieldInfo

### Method Categories:
- **Java Core Business Logic**: 21
- **Java Property Accessors**: 16
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 21
- **KMP Property Accessors**: 16
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 98%


## Detailed Analysis: org.apache.lucene.index.IndexWriter -> org.gnit.lucenekmp.index.IndexWriter

### Method Categories:
- **Java Core Business Logic**: 141
- **Java Property Accessors**: 28
- **Java Auto-Generated**: 0
- **Java Synthetic**: 31
- **KMP Core Business Logic**: 202
- **KMP Property Accessors**: 33
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 36
- **Semantic Completion**: 98%

### Missing Core Methods:
- `protected void ensureOpen()`
- `private long commitInternal(org.apache.lucene.index.MergePolicy)`


## Detailed Analysis: org.apache.lucene.index.IndexWriterConfig -> org.gnit.lucenekmp.index.IndexWriterConfig

### Method Categories:
- **Java Core Business Logic**: 26
- **Java Property Accessors**: 15
- **Java Auto-Generated**: 1
- **Java Synthetic**: 6
- **KMP Core Business Logic**: 26
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 6
- **Semantic Completion**: 77%


## Detailed Analysis: org.apache.lucene.index.LeafMetaData -> org.gnit.lucenekmp.index.LeafMetaData

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 14%

### Missing Core Methods:
- `public int createdVersionMajor()`
- `public org.apache.lucene.util.Version minVersion()`
- `public boolean hasBlocks()`


## Detailed Analysis: org.apache.lucene.index.LiveIndexWriterConfig -> org.gnit.lucenekmp.index.LiveIndexWriterConfig

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 26
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 55
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 94%


## Detailed Analysis: org.apache.lucene.index.LockableConcurrentApproximatePriorityQueue -> org.gnit.lucenekmp.index.LockableConcurrentApproximatePriorityQueue

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 25%

### Missing Core Methods:
- `package-private boolean remove(java.lang.Object)`
- `package-private boolean contains(java.lang.Object)`
- `package-private void addAndUnlock(T, long)`


## Detailed Analysis: org.apache.lucene.index.MergeScheduler -> org.gnit.lucenekmp.index.MergeScheduler

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 84%

### Missing Core Methods:
- `public void merge(org.apache.lucene.index.MergeScheduler$MergeSource, org.apache.lucene.index.MergeTrigger)`


## Detailed Analysis: org.apache.lucene.index.ReaderPool -> org.gnit.lucenekmp.index.ReaderPool

### Method Categories:
- **Java Core Business Logic**: 16
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 30
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 94%

### Missing Core Methods:
- `private boolean noDups()`


## Detailed Analysis: org.apache.lucene.index.ReaderSlice -> org.gnit.lucenekmp.index.ReaderSlice

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 7
- **KMP Synthetic**: 2
- **Semantic Completion**: 64%

### Missing Core Methods:
- `public int readerIndex()`


## Detailed Analysis: org.apache.lucene.index.SortFieldProvider -> org.gnit.lucenekmp.index.SortFieldProvider

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 84%

### Missing Core Methods:
- `public static void reloadSortFieldProviders(java.lang.ClassLoader)`


## Detailed Analysis: org.apache.lucene.index.StandardDirectoryReader -> org.gnit.lucenekmp.index.StandardDirectoryReader

### Method Categories:
- **Java Core Business Logic**: 14
- **Java Property Accessors**: 5
- **Java Auto-Generated**: 1
- **Java Synthetic**: 3
- **KMP Core Business Logic**: 19
- **KMP Property Accessors**: 6
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 2
- **Semantic Completion**: 94%

### Missing Core Methods:
- `private static void decRefWhileHandlingException(org.apache.lucene.index.SegmentReader[])`


## Detailed Analysis: org.apache.lucene.index.StoredFieldDataInput -> org.gnit.lucenekmp.index.StoredFieldDataInput

### Method Categories:
- **Java Core Business Logic**: 0
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 80%


## Detailed Analysis: org.apache.lucene.internal.hppc.IntArrayList -> org.gnit.lucenekmp.internal.hppc.IntArrayList

### Method Categories:
- **Java Core Business Logic**: 28
- **Java Property Accessors**: 6
- **Java Auto-Generated**: 4
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 28
- **KMP Property Accessors**: 11
- **KMP Auto-Generated**: 4
- **KMP Synthetic**: 3
- **Semantic Completion**: 98%


## Detailed Analysis: org.apache.lucene.internal.hppc.LongArrayList -> org.gnit.lucenekmp.internal.hppc.LongArrayList

### Method Categories:
- **Java Core Business Logic**: 28
- **Java Property Accessors**: 6
- **Java Auto-Generated**: 4
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 29
- **KMP Property Accessors**: 10
- **KMP Auto-Generated**: 4
- **KMP Synthetic**: 3
- **Semantic Completion**: 98%


## Detailed Analysis: org.apache.lucene.internal.tests.TestSecrets -> org.gnit.lucenekmp.internal.tests.TestSecrets

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 10
- **Java Auto-Generated**: 0
- **Java Synthetic**: 3
- **KMP Core Business Logic**: 8
- **KMP Property Accessors**: 9
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 92%


## Detailed Analysis: org.apache.lucene.internal.vectorization.DefaultVectorUtilSupport -> org.gnit.lucenekmp.internal.vectorization.DefaultVectorUtilSupport

### Method Categories:
- **Java Core Business Logic**: 11
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 11
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 81%

### Missing Core Methods:
- `private static float fma(float, float, float)`
- `public static long int4BitDotProductImpl(byte[], byte[])`


## Detailed Analysis: org.apache.lucene.internal.vectorization.VectorizationProvider -> org.gnit.lucenekmp.internal.vectorization.VectorizationProvider

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 1
- **Semantic Completion**: 59%

### Missing Core Methods:
- `package-private static org.apache.lucene.internal.vectorization.VectorizationProvider lookup(boolean)`
- `private static java.util.Optional<java.lang.Module> lookupVectorModule()`


## Detailed Analysis: org.apache.lucene.search.AbstractKnnVectorQuery -> org.gnit.lucenekmp.search.AbstractKnnVectorQuery

### Method Categories:
- **Java Core Business Logic**: 12
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 2
- **Java Synthetic**: 3
- **KMP Core Business Logic**: 12
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 1
- **Semantic Completion**: 64%

### Missing Core Methods:
- `protected org.apache.lucene.search.knn.KnnCollectorManager getKnnCollectorManager(int, org.apache.lucene.search.IndexSearcher)`
- `protected org.apache.lucene.search.TopDocs approximateSearch(org.apache.lucene.index.LeafReaderContext, org.apache.lucene.util.Bits, int, org.apache.lucene.search.knn.KnnCollectorManager)`
- `package-private org.apache.lucene.search.VectorScorer createVectorScorer(org.apache.lucene.index.LeafReaderContext, org.apache.lucene.index.FieldInfo)`
- `protected org.apache.lucene.search.TopDocs exactSearch(org.apache.lucene.index.LeafReaderContext, org.apache.lucene.search.DocIdSetIterator, org.apache.lucene.index.QueryTimeout)`
- `protected org.apache.lucene.search.TopDocs mergeLeafResults(org.apache.lucene.search.TopDocs[])`


## Detailed Analysis: org.apache.lucene.search.CollectionStatistics -> org.gnit.lucenekmp.search.CollectionStatistics

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 9
- **KMP Synthetic**: 1
- **Semantic Completion**: 23%

### Missing Core Methods:
- `public long maxDoc()`
- `public long docCount()`
- `public long sumTotalTermFreq()`
- `public long sumDocFreq()`


## Detailed Analysis: org.apache.lucene.search.DisjunctionMaxScorer -> org.gnit.lucenekmp.search.DisjunctionMaxScorer

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 83%


## Detailed Analysis: org.apache.lucene.search.FieldValueHitQueue -> org.gnit.lucenekmp.search.FieldValueHitQueue

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 82%

### Missing Core Methods:
- `protected boolean lessThan(org.apache.lucene.search.FieldValueHitQueue$Entry, org.apache.lucene.search.FieldValueHitQueue$Entry)`


## Detailed Analysis: org.apache.lucene.search.KnnByteVectorQuery -> org.gnit.lucenekmp.search.KnnByteVectorQuery

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 3
- **Java Synthetic**: 5
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 3
- **KMP Synthetic**: 0
- **Semantic Completion**: 37%

### Missing Core Methods:
- `protected org.apache.lucene.search.TopDocs approximateSearch(org.apache.lucene.index.LeafReaderContext, org.apache.lucene.util.Bits, int, org.apache.lucene.search.knn.KnnCollectorManager)`
- `package-private org.apache.lucene.search.VectorScorer createVectorScorer(org.apache.lucene.index.LeafReaderContext, org.apache.lucene.index.FieldInfo)`


## Detailed Analysis: org.apache.lucene.search.KnnFloatVectorQuery -> org.gnit.lucenekmp.search.KnnFloatVectorQuery

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 3
- **Java Synthetic**: 5
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 3
- **KMP Synthetic**: 0
- **Semantic Completion**: 37%

### Missing Core Methods:
- `protected org.apache.lucene.search.TopDocs approximateSearch(org.apache.lucene.index.LeafReaderContext, org.apache.lucene.util.Bits, int, org.apache.lucene.search.knn.KnnCollectorManager)`
- `package-private org.apache.lucene.search.VectorScorer createVectorScorer(org.apache.lucene.index.LeafReaderContext, org.apache.lucene.index.FieldInfo)`


## Detailed Analysis: org.apache.lucene.search.LRUQueryCache -> org.gnit.lucenekmp.search.LRUQueryCache

### Method Categories:
- **Java Core Business Logic**: 23
- **Java Property Accessors**: 7
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 25
- **KMP Property Accessors**: 7
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 11
- **Semantic Completion**: 81%

### Missing Core Methods:
- `private void putIfAbsent(org.apache.lucene.search.Query, org.apache.lucene.search.LRUQueryCache$CacheAndCount, org.apache.lucene.index.IndexReader$CacheHelper)`
- `public void clearCoreCacheKey(java.lang.Object)`
- `public void clearQuery(org.apache.lucene.search.Query)`
- `public void clear()`
- `package-private void assertConsistent()`


## Detailed Analysis: org.apache.lucene.search.Multiset -> org.gnit.lucenekmp.search.Multiset

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 2
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 2
- **KMP Synthetic**: 1
- **Semantic Completion**: 82%

### Missing Core Methods:
- `public boolean remove(java.lang.Object)`


## Detailed Analysis: org.apache.lucene.search.ScorerUtil -> org.gnit.lucenekmp.search.ScorerUtil

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 75%

### Missing Core Methods:
- `package-private static long costWithMinShouldMatch(java.util.stream.LongStream, int, int)`


## Detailed Analysis: org.apache.lucene.search.SloppyPhraseMatcher -> org.gnit.lucenekmp.search.SloppyPhraseMatcher

### Method Categories:
- **Java Core Business Logic**: 29
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 3
- **KMP Core Business Logic**: 29
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 93%

### Missing Core Methods:
- `private org.apache.lucene.search.PhrasePositions[] repeatingPPs(java.util.HashMap<org.apache.lucene.index.Term, java.lang.Integer>)`
- `private java.util.ArrayList<org.apache.lucene.util.FixedBitSet> ppTermsBitSets(org.apache.lucene.search.PhrasePositions[], java.util.HashMap<org.apache.lucene.index.Term, java.lang.Integer>)`


## Detailed Analysis: org.apache.lucene.search.TaskExecutor -> org.gnit.lucenekmp.search.TaskExecutor

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 1
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 2
- **Semantic Completion**: 76%

### Missing Core Methods:
- `private static boolean assertAllFuturesCompleted(java.util.Collection<? extends java.util.concurrent.Future<?>>)`


## Detailed Analysis: org.apache.lucene.search.TermRangeQuery -> org.gnit.lucenekmp.search.TermRangeQuery

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 3
- **KMP Synthetic**: 1
- **Semantic Completion**: 85%

### Missing Core Methods:
- `public static org.apache.lucene.search.TermRangeQuery newStringRange(java.lang.String, java.lang.String, java.lang.String, boolean, boolean)`


## Detailed Analysis: org.apache.lucene.search.TermScorer -> org.gnit.lucenekmp.search.TermScorer

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 4
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 90%


## Detailed Analysis: org.apache.lucene.search.TermStatistics -> org.gnit.lucenekmp.search.TermStatistics

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 18%

### Missing Core Methods:
- `public long docFreq()`
- `public long totalTermFreq()`


## Detailed Analysis: org.apache.lucene.search.WildcardQuery -> org.gnit.lucenekmp.search.WildcardQuery

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 0
- **Semantic Completion**: 66%


## Detailed Analysis: org.apache.lucene.store.ByteBuffersDataOutput -> org.gnit.lucenekmp.store.ByteBuffersDataOutput

### Method Categories:
- **Java Core Business Logic**: 25
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 29
- **KMP Property Accessors**: 3
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 4
- **Semantic Completion**: 92%

### Missing Core Methods:
- `public java.util.ArrayList<java.nio.ByteBuffer> toBufferList()`
- `public java.util.ArrayList<java.nio.ByteBuffer> toWriteableBufferList()`


## Detailed Analysis: org.apache.lucene.store.FlushInfo -> org.gnit.lucenekmp.store.FlushInfo

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 6
- **KMP Synthetic**: 1
- **Semantic Completion**: 23%

### Missing Core Methods:
- `public int numDocs()`
- `public long estimatedSegmentSize()`


## Detailed Analysis: org.apache.lucene.store.IOContext -> org.gnit.lucenekmp.store.IOContext

### Method Categories:
- **Java Core Business Logic**: 4
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 3
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 6
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 30%

### Missing Core Methods:
- `public org.apache.lucene.store.MergeInfo mergeInfo()`
- `public org.apache.lucene.store.FlushInfo flushInfo()`
- `public org.apache.lucene.store.ReadAdvice readAdvice()`


## Detailed Analysis: org.apache.lucene.store.MergeInfo -> org.gnit.lucenekmp.store.MergeInfo

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 0
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 8
- **KMP Synthetic**: 1
- **Semantic Completion**: 28%

### Missing Core Methods:
- `public int totalMaxDoc()`
- `public long estimatedMergeBytes()`
- `public int mergeMaxNumSegments()`


## Detailed Analysis: org.apache.lucene.store.RandomAccessInput -> org.gnit.lucenekmp.store.RandomAccessInput

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 91%


## Detailed Analysis: org.apache.lucene.util.ArrayUtil -> org.gnit.lucenekmp.util.ArrayUtil

### Method Categories:
- **Java Core Business Logic**: 61
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 1
- **Java Synthetic**: 1
- **KMP Core Business Logic**: 60
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 8
- **Semantic Completion**: 90%

### Missing Core Methods:
- `public static T[] growExact(T[], int)`
- `public static T[] grow(T[])`
- `public static T[] grow(T[], int)`
- `public static T[] copyArray(T[])`
- `public static T[] copyOfSubArray(T[], int, int)`
- `public static org.apache.lucene.util.ArrayUtil$ByteArrayComparator getUnsignedComparator(int)`


## Detailed Analysis: org.apache.lucene.util.AttributeFactory -> org.gnit.lucenekmp.util.AttributeFactory

### Method Categories:
- **Java Core Business Logic**: 3
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 15
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 66%

### Missing Core Methods:
- `package-private static java.lang.invoke.MethodHandle findAttributeImplCtor(java.lang.Class<? extends org.apache.lucene.util.AttributeImpl>)`


## Detailed Analysis: org.apache.lucene.util.AttributeSource -> org.gnit.lucenekmp.util.AttributeSource

### Method Categories:
- **Java Core Business Logic**: 15
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 18
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 3
- **KMP Synthetic**: 2
- **Semantic Completion**: 94%

### Missing Core Methods:
- `package-private static java.lang.Class<? extends org.apache.lucene.util.Attribute>[] getAttributeInterfaces(java.lang.Class<? extends org.apache.lucene.util.AttributeImpl>)`


## Detailed Analysis: org.apache.lucene.util.CharsRef -> org.gnit.lucenekmp.util.CharsRef

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 4
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 9
- **KMP Auto-Generated**: 3
- **KMP Synthetic**: 4
- **Semantic Completion**: 67%

### Missing Core Methods:
- `public boolean charsEquals(org.apache.lucene.util.CharsRef)`
- `public char charAt(int)`


## Detailed Analysis: org.apache.lucene.util.ClassLoaderUtils -> org.gnit.lucenekmp.util.ClassLoaderUtils

### Method Categories:
- **Java Core Business Logic**: 1
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 1
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 0%

### Missing Core Methods:
- `public static boolean isParentClassLoader(java.lang.ClassLoader, java.lang.ClassLoader)`


## Detailed Analysis: org.apache.lucene.util.CloseableThreadLocal -> org.gnit.lucenekmp.util.CloseableThreadLocal

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 64%

### Missing Core Methods:
- `private void maybePurge()`
- `private void purge()`


## Detailed Analysis: org.apache.lucene.util.CollectionUtil -> org.gnit.lucenekmp.util.CollectionUtil

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 6
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 66%

### Missing Core Methods:
- `public static java.util.HashMap<K, V> newHashMap(int)`
- `public static java.util.HashSet<E> newHashSet(int)`


## Detailed Analysis: org.apache.lucene.util.FileDeleter -> org.gnit.lucenekmp.util.FileDeleter

### Method Categories:
- **Java Core Business Logic**: 13
- **Java Property Accessors**: 2
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 31
- **KMP Property Accessors**: 2
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 92%

### Missing Core Methods:
- `private boolean decRef(java.lang.String)`


## Detailed Analysis: org.apache.lucene.util.IOUtils -> org.gnit.lucenekmp.util.IOUtils

### Method Categories:
- **Java Core Business Logic**: 19
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 2
- **KMP Core Business Logic**: 25
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 63%

### Missing Core Methods:
- `public static void close(java.io.Closeable[])`
- `public static void closeWhileHandlingException(java.io.Closeable[])`
- `public static void deleteFilesIgnoringExceptions(java.nio.file.Path[])`
- `public static void deleteFilesIfExist(java.nio.file.Path[])`
- `public static void rm(java.nio.file.Path[])`
- `private static java.util.LinkedHashMap<java.nio.file.Path, java.lang.Throwable> rm(java.util.LinkedHashMap<java.nio.file.Path, java.lang.Throwable>, java.nio.file.Path[])`
- `public static java.lang.Error rethrowAlways(java.lang.Throwable)`


## Detailed Analysis: org.apache.lucene.util.NamedSPILoader -> org.gnit.lucenekmp.util.NamedSPILoader

### Method Categories:
- **Java Core Business Logic**: 5
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 5
- **KMP Property Accessors**: 1
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 82%

### Missing Core Methods:
- `public void reload(java.lang.ClassLoader)`


## Detailed Analysis: org.apache.lucene.util.NamedThreadFactory -> org.gnit.lucenekmp.util.NamedThreadFactory

### Method Categories:
- **Java Core Business Logic**: 2
- **Java Property Accessors**: 1
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 2
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 2
- **Semantic Completion**: 38%

### Missing Core Methods:
- `public java.lang.Thread newThread(java.lang.Runnable)`


## Detailed Analysis: org.apache.lucene.util.RamUsageEstimator -> org.gnit.lucenekmp.util.RamUsageEstimator

### Method Categories:
- **Java Core Business Logic**: 42
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 45
- **KMP Property Accessors**: 5
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 5
- **Semantic Completion**: 97%

### Missing Core Methods:
- `private static long shallowSizeOfArray(java.lang.Object)`


## Detailed Analysis: org.apache.lucene.util.automaton.RegExp -> org.gnit.lucenekmp.util.automaton.RegExp

### Method Categories:
- **Java Core Business Logic**: 49
- **Java Property Accessors**: 4
- **Java Auto-Generated**: 1
- **Java Synthetic**: 3
- **KMP Core Business Logic**: 52
- **KMP Property Accessors**: 17
- **KMP Auto-Generated**: 1
- **KMP Synthetic**: 2
- **Semantic Completion**: 98%

### Missing Core Methods:
- `package-private org.apache.lucene.util.automaton.RegExp iterativeParseExp(java.util.function.Supplier<org.apache.lucene.util.automaton.RegExp>, java.util.function.BooleanSupplier, org.apache.lucene.util.automaton.RegExp$MakeRegexGroup)`


## Detailed Analysis: org.apache.lucene.util.automaton.StringsToAutomaton -> org.gnit.lucenekmp.util.automaton.StringsToAutomaton

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 7
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 3
- **Semantic Completion**: 85%

### Missing Core Methods:
- `private static int convert(org.apache.lucene.util.automaton.Automaton$Builder, org.apache.lucene.util.automaton.StringsToAutomaton$State, java.util.IdentityHashMap<org.apache.lucene.util.automaton.StringsToAutomaton$State, java.lang.Integer>)`


## Detailed Analysis: org.apache.lucene.util.bkd.BKDConfig -> org.gnit.lucenekmp.util.bkd.BKDConfig

### Method Categories:
- **Java Core Business Logic**: 7
- **Java Property Accessors**: 0
- **Java Auto-Generated**: 3
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 3
- **KMP Property Accessors**: 4
- **KMP Auto-Generated**: 8
- **KMP Synthetic**: 1
- **Semantic Completion**: 47%

### Missing Core Methods:
- `public int numDims()`
- `public int numIndexDims()`
- `public int bytesPerDim()`
- `public int maxPointsInLeafNode()`


## Detailed Analysis: org.apache.lucene.util.hnsw.BlockingFloatHeap -> org.gnit.lucenekmp.util.hnsw.BlockingFloatHeap

### Method Categories:
- **Java Core Business Logic**: 6
- **Java Property Accessors**: 3
- **Java Auto-Generated**: 0
- **Java Synthetic**: 0
- **KMP Core Business Logic**: 9
- **KMP Property Accessors**: 0
- **KMP Auto-Generated**: 0
- **KMP Synthetic**: 0
- **Semantic Completion**: 76%



## Progress Table for Unit Test Classes
| Java Unit Test Class | KMP Unit Test Class | Depth | Class Ported | Java Core Methods | KMP Core Methods | Semantic Progress |
| --- | --- | --- | --- | --- | --- | --- |
| [org.apache.lucene.TestAssertions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/TestAssertions.java) | org.gnit.lucenekmp.TestAssertions | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.TestDemo](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/TestDemo.java) | org.gnit.lucenekmp.TestDemo | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.TestExternalCodecs](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/TestExternalCodecs.java) | org.gnit.lucenekmp.TestExternalCodecs | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.TestMergeSchedulerExternal](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/TestMergeSchedulerExternal.java) | org.gnit.lucenekmp.TestMergeSchedulerExternal | Depth 1 | [] | 3 | 0 | 0% |
| [org.apache.lucene.TestSearch](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/TestSearch.java) | org.gnit.lucenekmp.TestSearch | Depth 1 | [x] | 3 | 3 | 66% |
| [org.apache.lucene.TestSearchForDuplicates](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/TestSearchForDuplicates.java) | org.gnit.lucenekmp.TestSearchForDuplicates | Depth 1 | [] | 4 | 0 | 0% |
| [org.apache.lucene.analysis.AbstractAnalysisFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/AbstractAnalysisFactory.java) | org.gnit.lucenekmp.analysis.AbstractAnalysisFactory | Depth 2 | [x] | 24 | 21 | 85% |
| [org.apache.lucene.analysis.AnalysisSPILoader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/AnalysisSPILoader.java) | org.gnit.lucenekmp.analysis.AnalysisSPILoader | Depth 2 | [x] | 7 | 7 | 85% |
| [org.apache.lucene.analysis.CharArrayMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/CharArrayMap.java) | org.gnit.lucenekmp.analysis.CharArrayMap | Depth 3 | [x] | 23 | 21 | 87% |
| [org.apache.lucene.analysis.CharFilterFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/CharFilterFactory.java) | org.gnit.lucenekmp.analysis.CharFilterFactory | Depth 3 | [x] | 7 | 7 | 85% |
| [org.apache.lucene.analysis.StopwordAnalyzerBase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/StopwordAnalyzerBase.java) | org.gnit.lucenekmp.analysis.StopwordAnalyzerBase | Depth 3 | [x] | 2 | 2 | 76% |
| [org.apache.lucene.analysis.TestReusableStringReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/analysis/TestReusableStringReader.java) | org.gnit.lucenekmp.analysis.TestReusableStringReader | Depth 1 | [x] | 1 | 1 | 0% |
| [org.apache.lucene.analysis.TestStopFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/analysis/TestStopFilter.java) | org.gnit.lucenekmp.analysis.TestStopFilter | Depth 1 | [x] | 9 | 5 | 55% |
| [org.apache.lucene.analysis.TokenFilterFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/TokenFilterFactory.java) | org.gnit.lucenekmp.analysis.TokenFilterFactory | Depth 3 | [x] | 7 | 7 | 85% |
| [org.apache.lucene.analysis.TokenizerFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/TokenizerFactory.java) | org.gnit.lucenekmp.analysis.TokenizerFactory | Depth 3 | [x] | 6 | 6 | 84% |
| [org.apache.lucene.analysis.tokenattributes.CharTermAttributeImpl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/tokenattributes/CharTermAttributeImpl.java) | org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttributeImpl | Depth 2 | [x] | 17 | 18 | 94% |
| [org.apache.lucene.codecs.Codec](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/Codec.java) | org.gnit.lucenekmp.codecs.Codec | Depth 3 | [x] | 14 | 15 | 93% |
| [org.apache.lucene.codecs.DocValuesFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/DocValuesFormat.java) | org.gnit.lucenekmp.codecs.DocValuesFormat | Depth 3 | [x] | 5 | 7 | 82% |
| [org.apache.lucene.codecs.KnnVectorsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/KnnVectorsFormat.java) | org.gnit.lucenekmp.codecs.KnnVectorsFormat | Depth 3 | [x] | 6 | 13 | 84% |
| [org.apache.lucene.codecs.PostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/PostingsFormat.java) | org.gnit.lucenekmp.codecs.PostingsFormat | Depth 3 | [x] | 5 | 8 | 82% |
| [org.apache.lucene.codecs.TermStats](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/TermStats.java) | org.gnit.lucenekmp.codecs.TermStats | Depth 4 | [x] | 2 | 0 | 23% |
| [org.apache.lucene.codecs.TestCodecLoadingDeadlock](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/codecs/TestCodecLoadingDeadlock.java) | org.gnit.lucenekmp.codecs.TestCodecLoadingDeadlock | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.codecs.bitvectors.FlatBitVectorsScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/bitvectors/FlatBitVectorsScorer.java) | org.gnit.lucenekmp.codecs.bitvectors.FlatBitVectorsScorer | Depth 4 | [] | 3 | 0 | 0% |
| [org.apache.lucene.codecs.bitvectors.HnswBitVectorsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/bitvectors/HnswBitVectorsFormat.java) | org.gnit.lucenekmp.codecs.bitvectors.HnswBitVectorsFormat | Depth 3 | [] | 3 | 0 | 0% |
| [org.apache.lucene.codecs.bitvectors.TestHnswBitVectorsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/bitvectors/TestHnswBitVectorsFormat.java) | org.gnit.lucenekmp.codecs.bitvectors.TestHnswBitVectorsFormat | Depth 1 | [] | 5 | 0 | 0% |
| [org.apache.lucene.codecs.blockterms.TestFixedGapPostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/blockterms/TestFixedGapPostingsFormat.java) | org.gnit.lucenekmp.codecs.blockterms.TestFixedGapPostingsFormat | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.blockterms.TestVarGapDocFreqIntervalPostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/blockterms/TestVarGapDocFreqIntervalPostingsFormat.java) | org.gnit.lucenekmp.codecs.blockterms.TestVarGapDocFreqIntervalPostingsFormat | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.blockterms.TestVarGapFixedIntervalPostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/blockterms/TestVarGapFixedIntervalPostingsFormat.java) | org.gnit.lucenekmp.codecs.blockterms.TestVarGapFixedIntervalPostingsFormat | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.blocktreeords.OrdsSegmentTermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/blocktreeords/OrdsSegmentTermsEnum.java) | org.gnit.lucenekmp.codecs.blocktreeords.OrdsSegmentTermsEnum | Depth 6 | [x] | 18 | 18 | 80% |
| [org.apache.lucene.codecs.blocktreeords.TestOrdsBlockTree](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/blocktreeords/TestOrdsBlockTree.java) | org.gnit.lucenekmp.codecs.blocktreeords.TestOrdsBlockTree | Depth 1 | [] | 8 | 0 | 0% |
| [org.apache.lucene.codecs.bloom.TestBloomPostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/bloom/TestBloomPostingsFormat.java) | org.gnit.lucenekmp.codecs.bloom.TestBloomPostingsFormat | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.compressing.TestDeflateWithPresetDictCompressionMode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/codecs/compressing/TestDeflateWithPresetDictCompressionMode.java) | org.gnit.lucenekmp.codecs.compressing.TestDeflateWithPresetDictCompressionMode | Depth 1 | [x] | 1 | 5 | 0% |
| [org.apache.lucene.codecs.hnsw.FlatVectorsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/hnsw/FlatVectorsReader.java) | org.gnit.lucenekmp.codecs.hnsw.FlatVectorsReader | Depth 3 | [x] | 4 | 4 | 88% |
| [org.apache.lucene.codecs.hnsw.TestFlatVectorScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/codecs/hnsw/TestFlatVectorScorer.java) | org.gnit.lucenekmp.codecs.hnsw.TestFlatVectorScorer | Depth 2 | [x] | 11 | 13 | 63% |
| [org.apache.lucene.codecs.lucene102.OffHeapBinarizedVectorValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene102/OffHeapBinarizedVectorValues.java) | org.gnit.lucenekmp.codecs.lucene102.OffHeapBinarizedVectorValues | Depth 4 | [x] | 4 | 3 | 73% |
| [org.apache.lucene.codecs.lucene90.tests.MockTermStateFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/lucene90/tests/MockTermStateFactory.java) | org.gnit.lucenekmp.codecs.lucene90.tests.MockTermStateFactory | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.lucene99.Lucene99ScalarQuantizedVectorScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/codecs/lucene99/Lucene99ScalarQuantizedVectorScorer.java) | org.gnit.lucenekmp.codecs.lucene99.Lucene99ScalarQuantizedVectorScorer | Depth 3 | [x] | 6 | 7 | 83% |
| [org.apache.lucene.codecs.lucene99.TestLucene99HnswQuantizedVectorsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/codecs/lucene99/TestLucene99HnswQuantizedVectorsFormat.java) | org.gnit.lucenekmp.codecs.lucene99.TestLucene99HnswQuantizedVectorsFormat | Depth 1 | [x] | 9 | 57 | 89% |
| [org.apache.lucene.codecs.lucene99.TestLucene99ScalarQuantizedVectorScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/codecs/lucene99/TestLucene99ScalarQuantizedVectorScorer.java) | org.gnit.lucenekmp.codecs.lucene99.TestLucene99ScalarQuantizedVectorScorer | Depth 1 | [x] | 16 | 18 | 93% |
| [org.apache.lucene.codecs.lucene99.TestLucene99ScalarQuantizedVectorsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/codecs/lucene99/TestLucene99ScalarQuantizedVectorsFormat.java) | org.gnit.lucenekmp.codecs.lucene99.TestLucene99ScalarQuantizedVectorsFormat | Depth 1 | [x] | 8 | 52 | 88% |
| [org.apache.lucene.codecs.lucene99.TestLucene99ScalarQuantizedVectorsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/codecs/lucene99/TestLucene99ScalarQuantizedVectorsWriter.java) | org.gnit.lucenekmp.codecs.lucene99.TestLucene99ScalarQuantizedVectorsWriter | Depth 1 | [x] | 5 | 5 | 80% |
| [org.apache.lucene.codecs.memory.FSTPostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/memory/FSTPostingsFormat.java) | org.gnit.lucenekmp.codecs.memory.FSTPostingsFormat | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.codecs.memory.FSTTermOutputs](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/memory/FSTTermOutputs.java) | org.gnit.lucenekmp.codecs.memory.FSTTermOutputs | Depth 5 | [] | 10 | 0 | 0% |
| [org.apache.lucene.codecs.memory.FSTTermsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/memory/FSTTermsReader.java) | org.gnit.lucenekmp.codecs.memory.FSTTermsReader | Depth 4 | [] | 6 | 0 | 0% |
| [org.apache.lucene.codecs.memory.FSTTermsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/memory/FSTTermsWriter.java) | org.gnit.lucenekmp.codecs.memory.FSTTermsWriter | Depth 4 | [] | 3 | 0 | 0% |
| [org.apache.lucene.codecs.memory.TestDirectPostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/memory/TestDirectPostingsFormat.java) | org.gnit.lucenekmp.codecs.memory.TestDirectPostingsFormat | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.memory.TestFSTPostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/memory/TestFSTPostingsFormat.java) | org.gnit.lucenekmp.codecs.memory.TestFSTPostingsFormat | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.perfield.TestPerFieldDocValuesFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/codecs/perfield/TestPerFieldDocValuesFormat.java) | org.gnit.lucenekmp.codecs.perfield.TestPerFieldDocValuesFormat | Depth 1 | [x] | 5 | 4 | 82% |
| [org.apache.lucene.codecs.perfield.TestPerFieldKnnVectorsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/codecs/perfield/TestPerFieldKnnVectorsFormat.java) | org.gnit.lucenekmp.codecs.perfield.TestPerFieldKnnVectorsFormat | Depth 1 | [x] | 5 | 52 | 82% |
| [org.apache.lucene.codecs.simpletext.SimpleTextDocValuesReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/simpletext/SimpleTextDocValuesReader.java) | org.gnit.lucenekmp.codecs.simpletext.SimpleTextDocValuesReader | Depth 5 | [x] | 14 | 19 | 92% |
| [org.apache.lucene.codecs.simpletext.SimpleTextFieldsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/simpletext/SimpleTextFieldsReader.java) | org.gnit.lucenekmp.codecs.simpletext.SimpleTextFieldsReader | Depth 5 | [x] | 4 | 4 | 81% |
| [org.apache.lucene.codecs.simpletext.TestSimpleTextCompoundFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/simpletext/TestSimpleTextCompoundFormat.java) | org.gnit.lucenekmp.codecs.simpletext.TestSimpleTextCompoundFormat | Depth 1 | [] | 3 | 0 | 0% |
| [org.apache.lucene.codecs.simpletext.TestSimpleTextDocValuesFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/simpletext/TestSimpleTextDocValuesFormat.java) | org.gnit.lucenekmp.codecs.simpletext.TestSimpleTextDocValuesFormat | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.codecs.simpletext.TestSimpleTextFieldInfoFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/simpletext/TestSimpleTextFieldInfoFormat.java) | org.gnit.lucenekmp.codecs.simpletext.TestSimpleTextFieldInfoFormat | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.simpletext.TestSimpleTextKnnVectorsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/simpletext/TestSimpleTextKnnVectorsFormat.java) | org.gnit.lucenekmp.codecs.simpletext.TestSimpleTextKnnVectorsFormat | Depth 1 | [] | 3 | 0 | 0% |
| [org.apache.lucene.codecs.simpletext.TestSimpleTextLiveDocsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/simpletext/TestSimpleTextLiveDocsFormat.java) | org.gnit.lucenekmp.codecs.simpletext.TestSimpleTextLiveDocsFormat | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.simpletext.TestSimpleTextNormsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/simpletext/TestSimpleTextNormsFormat.java) | org.gnit.lucenekmp.codecs.simpletext.TestSimpleTextNormsFormat | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.simpletext.TestSimpleTextPointsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/simpletext/TestSimpleTextPointsFormat.java) | org.gnit.lucenekmp.codecs.simpletext.TestSimpleTextPointsFormat | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.simpletext.TestSimpleTextPostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/simpletext/TestSimpleTextPostingsFormat.java) | org.gnit.lucenekmp.codecs.simpletext.TestSimpleTextPostingsFormat | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.simpletext.TestSimpleTextSegmentInfoFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/simpletext/TestSimpleTextSegmentInfoFormat.java) | org.gnit.lucenekmp.codecs.simpletext.TestSimpleTextSegmentInfoFormat | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.codecs.simpletext.TestSimpleTextStoredFieldsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/simpletext/TestSimpleTextStoredFieldsFormat.java) | org.gnit.lucenekmp.codecs.simpletext.TestSimpleTextStoredFieldsFormat | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.simpletext.TestSimpleTextTermVectorsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/simpletext/TestSimpleTextTermVectorsFormat.java) | org.gnit.lucenekmp.codecs.simpletext.TestSimpleTextTermVectorsFormat | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.BlockDecoder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/BlockDecoder.java) | org.gnit.lucenekmp.codecs.uniformsplit.BlockDecoder | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.BlockEncoder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/BlockEncoder.java) | org.gnit.lucenekmp.codecs.uniformsplit.BlockEncoder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.BlockHeader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/BlockHeader.java) | org.gnit.lucenekmp.codecs.uniformsplit.BlockHeader | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.BlockLine](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/BlockLine.java) | org.gnit.lucenekmp.codecs.uniformsplit.BlockLine | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.BlockReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/BlockReader.java) | org.gnit.lucenekmp.codecs.uniformsplit.BlockReader | Depth 2 | [] | 28 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.BlockWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/BlockWriter.java) | org.gnit.lucenekmp.codecs.uniformsplit.BlockWriter | Depth 2 | [] | 10 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.DeltaBaseTermStateSerializer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/DeltaBaseTermStateSerializer.java) | org.gnit.lucenekmp.codecs.uniformsplit.DeltaBaseTermStateSerializer | Depth 2 | [] | 6 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.FSTDictionary](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/FSTDictionary.java) | org.gnit.lucenekmp.codecs.uniformsplit.FSTDictionary | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.FieldMetadata](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/FieldMetadata.java) | org.gnit.lucenekmp.codecs.uniformsplit.FieldMetadata | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.IndexDictionary](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/IndexDictionary.java) | org.gnit.lucenekmp.codecs.uniformsplit.IndexDictionary | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.IntersectBlockReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/IntersectBlockReader.java) | org.gnit.lucenekmp.codecs.uniformsplit.IntersectBlockReader | Depth 6 | [] | 8 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.RamUsageUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/RamUsageUtil.java) | org.gnit.lucenekmp.codecs.uniformsplit.RamUsageUtil | Depth 3 | [] | 7 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.TermBytes](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/TermBytes.java) | org.gnit.lucenekmp.codecs.uniformsplit.TermBytes | Depth 2 | [] | 3 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.TestBlockWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/uniformsplit/TestBlockWriter.java) | org.gnit.lucenekmp.codecs.uniformsplit.TestBlockWriter | Depth 1 | [] | 5 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.TestFSTDictionary](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/uniformsplit/TestFSTDictionary.java) | org.gnit.lucenekmp.codecs.uniformsplit.TestFSTDictionary | Depth 1 | [] | 9 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.TestTermBytes](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/uniformsplit/TestTermBytes.java) | org.gnit.lucenekmp.codecs.uniformsplit.TestTermBytes | Depth 1 | [] | 18 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.TestTermBytesComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/uniformsplit/TestTermBytesComparator.java) | org.gnit.lucenekmp.codecs.uniformsplit.TestTermBytesComparator | Depth 1 | [] | 6 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.TestUniformSplitPostingFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/uniformsplit/TestUniformSplitPostingFormat.java) | org.gnit.lucenekmp.codecs.uniformsplit.TestUniformSplitPostingFormat | Depth 1 | [] | 5 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.UniformSplitPostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/UniformSplitPostingsFormat.java) | org.gnit.lucenekmp.codecs.uniformsplit.UniformSplitPostingsFormat | Depth 2 | [] | 5 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.UniformSplitTerms](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/UniformSplitTerms.java) | org.gnit.lucenekmp.codecs.uniformsplit.UniformSplitTerms | Depth 4 | [] | 6 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.UniformSplitTermsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/UniformSplitTermsReader.java) | org.gnit.lucenekmp.codecs.uniformsplit.UniformSplitTermsReader | Depth 3 | [] | 9 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.UniformSplitTermsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/UniformSplitTermsWriter.java) | org.gnit.lucenekmp.codecs.uniformsplit.UniformSplitTermsWriter | Depth 2 | [] | 9 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.sharedterms.FieldMetadataTermState](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/sharedterms/FieldMetadataTermState.java) | org.gnit.lucenekmp.codecs.uniformsplit.sharedterms.FieldMetadataTermState | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.sharedterms.STBlockLine](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/sharedterms/STBlockLine.java) | org.gnit.lucenekmp.codecs.uniformsplit.sharedterms.STBlockLine | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.sharedterms.STBlockReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/sharedterms/STBlockReader.java) | org.gnit.lucenekmp.codecs.uniformsplit.sharedterms.STBlockReader | Depth 2 | [] | 8 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.sharedterms.STBlockWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/sharedterms/STBlockWriter.java) | org.gnit.lucenekmp.codecs.uniformsplit.sharedterms.STBlockWriter | Depth 4 | [] | 5 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.sharedterms.STIntersectBlockReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/sharedterms/STIntersectBlockReader.java) | org.gnit.lucenekmp.codecs.uniformsplit.sharedterms.STIntersectBlockReader | Depth 5 | [] | 3 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.sharedterms.STMergingBlockReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/sharedterms/STMergingBlockReader.java) | org.gnit.lucenekmp.codecs.uniformsplit.sharedterms.STMergingBlockReader | Depth 4 | [] | 7 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.sharedterms.STMergingTermsEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/sharedterms/STMergingTermsEnum.java) | org.gnit.lucenekmp.codecs.uniformsplit.sharedterms.STMergingTermsEnum | Depth 5 | [] | 9 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.sharedterms.STUniformSplitPostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/sharedterms/STUniformSplitPostingsFormat.java) | org.gnit.lucenekmp.codecs.uniformsplit.sharedterms.STUniformSplitPostingsFormat | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.sharedterms.STUniformSplitTerms](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/sharedterms/STUniformSplitTerms.java) | org.gnit.lucenekmp.codecs.uniformsplit.sharedterms.STUniformSplitTerms | Depth 4 | [] | 2 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.sharedterms.STUniformSplitTermsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/sharedterms/STUniformSplitTermsReader.java) | org.gnit.lucenekmp.codecs.uniformsplit.sharedterms.STUniformSplitTermsReader | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.sharedterms.STUniformSplitTermsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/sharedterms/STUniformSplitTermsWriter.java) | org.gnit.lucenekmp.codecs.uniformsplit.sharedterms.STUniformSplitTermsWriter | Depth 4 | [] | 16 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.sharedterms.TestSTBlockReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/uniformsplit/sharedterms/TestSTBlockReader.java) | org.gnit.lucenekmp.codecs.uniformsplit.sharedterms.TestSTBlockReader | Depth 2 | [] | 10 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.sharedterms.TestSTUniformSplitPostingFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/test/org/apache/lucene/codecs/uniformsplit/sharedterms/TestSTUniformSplitPostingFormat.java) | org.gnit.lucenekmp.codecs.uniformsplit.sharedterms.TestSTUniformSplitPostingFormat | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.codecs.uniformsplit.sharedterms.UnionFieldMetadataBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/codecs/src/java/org/apache/lucene/codecs/uniformsplit/sharedterms/UnionFieldMetadataBuilder.java) | org.gnit.lucenekmp.codecs.uniformsplit.sharedterms.UnionFieldMetadataBuilder | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.document.BaseLatLonSpatialTestCase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/BaseLatLonSpatialTestCase.java) | org.gnit.lucenekmp.document.BaseLatLonSpatialTestCase | Depth 2 | [x] | 17 | 17 | 91% |
| [org.apache.lucene.document.BaseXYShapeTestCase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/BaseXYShapeTestCase.java) | org.gnit.lucenekmp.document.BaseXYShapeTestCase | Depth 2 | [x] | 21 | 21 | 97% |
| [org.apache.lucene.document.DateTools](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/DateTools.java) | org.gnit.lucenekmp.document.DateTools | Depth 3 | [x] | 6 | 12 | 50% |
| [org.apache.lucene.document.DocValuesLongHashSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/DocValuesLongHashSet.java) | org.gnit.lucenekmp.document.DocValuesLongHashSet | Depth 2 | [x] | 3 | 5 | 87% |
| [org.apache.lucene.document.InetAddressPoint](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/InetAddressPoint.java) | org.gnit.lucenekmp.document.InetAddressPoint | Depth 3 | [x] | 8 | 9 | 89% |
| [org.apache.lucene.document.LatLonPointSortField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/LatLonPointSortField.java) | org.gnit.lucenekmp.document.LatLonPointSortField | Depth 3 | [x] | 1 | 1 | 78% |
| [org.apache.lucene.document.LatLonShapeDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/LatLonShapeDocValues.java) | org.gnit.lucenekmp.document.LatLonShapeDocValues | Depth 3 | [x] | 2 | 2 | 68% |
| [org.apache.lucene.document.LatLonShapeDocValuesQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/LatLonShapeDocValuesQuery.java) | org.gnit.lucenekmp.document.LatLonShapeDocValuesQuery | Depth 3 | [] | 3 | 0 | 0% |
| [org.apache.lucene.document.ShapeDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/ShapeDocValues.java) | org.gnit.lucenekmp.document.ShapeDocValues | Depth 3 | [x] | 12 | 15 | 94% |
| [org.apache.lucene.document.TestDateTools](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestDateTools.java) | org.gnit.lucenekmp.document.TestDateTools | Depth 1 | [x] | 6 | 11 | 83% |
| [org.apache.lucene.document.TestDocument](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestDocument.java) | org.gnit.lucenekmp.document.TestDocument | Depth 1 | [] | 14 | 0 | 0% |
| [org.apache.lucene.document.TestFieldType](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestFieldType.java) | org.gnit.lucenekmp.document.TestFieldType | Depth 1 | [x] | 7 | 7 | 85% |
| [org.apache.lucene.document.TestLatLonDocValuesMultiPointPointQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestLatLonDocValuesMultiPointPointQueries.java) | org.gnit.lucenekmp.document.TestLatLonDocValuesMultiPointPointQueries | Depth 1 | [x] | 3 | 7 | 85% |
| [org.apache.lucene.document.TestLatLonDocValuesPointPointQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestLatLonDocValuesPointPointQueries.java) | org.gnit.lucenekmp.document.TestLatLonDocValuesPointPointQueries | Depth 1 | [x] | 2 | 6 | 81% |
| [org.apache.lucene.document.TestLatLonLineShapeDVQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestLatLonLineShapeDVQueries.java) | org.gnit.lucenekmp.document.TestLatLonLineShapeDVQueries | Depth 1 | [x] | 5 | 10 | 90% |
| [org.apache.lucene.document.TestLatLonLineShapeQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestLatLonLineShapeQueries.java) | org.gnit.lucenekmp.document.TestLatLonLineShapeQueries | Depth 1 | [x] | 2 | 11 | 81% |
| [org.apache.lucene.document.TestLatLonMultiLineShapeQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestLatLonMultiLineShapeQueries.java) | org.gnit.lucenekmp.document.TestLatLonMultiLineShapeQueries | Depth 1 | [x] | 3 | 11 | 61% |
| [org.apache.lucene.document.TestLatLonMultiPointPointQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestLatLonMultiPointPointQueries.java) | org.gnit.lucenekmp.document.TestLatLonMultiPointPointQueries | Depth 1 | [x] | 3 | 9 | 85% |
| [org.apache.lucene.document.TestLatLonMultiPointShapeQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestLatLonMultiPointShapeQueries.java) | org.gnit.lucenekmp.document.TestLatLonMultiPointShapeQueries | Depth 1 | [x] | 3 | 11 | 61% |
| [org.apache.lucene.document.TestLatLonMultiPolygonShapeQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestLatLonMultiPolygonShapeQueries.java) | org.gnit.lucenekmp.document.TestLatLonMultiPolygonShapeQueries | Depth 1 | [x] | 4 | 12 | 69% |
| [org.apache.lucene.document.TestLatLonPointPointQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestLatLonPointPointQueries.java) | org.gnit.lucenekmp.document.TestLatLonPointPointQueries | Depth 1 | [x] | 2 | 8 | 81% |
| [org.apache.lucene.document.TestLatLonPointShapeDVQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestLatLonPointShapeDVQueries.java) | org.gnit.lucenekmp.document.TestLatLonPointShapeDVQueries | Depth 1 | [x] | 5 | 10 | 90% |
| [org.apache.lucene.document.TestLatLonPointShapeQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestLatLonPointShapeQueries.java) | org.gnit.lucenekmp.document.TestLatLonPointShapeQueries | Depth 1 | [x] | 2 | 11 | 81% |
| [org.apache.lucene.document.TestLatLonPolygonShapeDVQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestLatLonPolygonShapeDVQueries.java) | org.gnit.lucenekmp.document.TestLatLonPolygonShapeDVQueries | Depth 1 | [x] | 5 | 10 | 90% |
| [org.apache.lucene.document.TestLatLonPolygonShapeQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestLatLonPolygonShapeQueries.java) | org.gnit.lucenekmp.document.TestLatLonPolygonShapeQueries | Depth 1 | [x] | 2 | 10 | 81% |
| [org.apache.lucene.document.TestXYLineShapeDVQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestXYLineShapeDVQueries.java) | org.gnit.lucenekmp.document.TestXYLineShapeDVQueries | Depth 1 | [x] | 5 | 10 | 90% |
| [org.apache.lucene.document.TestXYLineShapeQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestXYLineShapeQueries.java) | org.gnit.lucenekmp.document.TestXYLineShapeQueries | Depth 1 | [x] | 2 | 7 | 81% |
| [org.apache.lucene.document.TestXYMultiLineShapeQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestXYMultiLineShapeQueries.java) | org.gnit.lucenekmp.document.TestXYMultiLineShapeQueries | Depth 1 | [x] | 3 | 7 | 61% |
| [org.apache.lucene.document.TestXYMultiPointShapeQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestXYMultiPointShapeQueries.java) | org.gnit.lucenekmp.document.TestXYMultiPointShapeQueries | Depth 1 | [x] | 3 | 7 | 61% |
| [org.apache.lucene.document.TestXYMultiPolygonShapeQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestXYMultiPolygonShapeQueries.java) | org.gnit.lucenekmp.document.TestXYMultiPolygonShapeQueries | Depth 1 | [x] | 3 | 7 | 61% |
| [org.apache.lucene.document.TestXYPointShapeDVQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestXYPointShapeDVQueries.java) | org.gnit.lucenekmp.document.TestXYPointShapeDVQueries | Depth 1 | [x] | 5 | 10 | 90% |
| [org.apache.lucene.document.TestXYPointShapeQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestXYPointShapeQueries.java) | org.gnit.lucenekmp.document.TestXYPointShapeQueries | Depth 1 | [x] | 2 | 7 | 81% |
| [org.apache.lucene.document.TestXYPolygonShapeDVQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestXYPolygonShapeDVQueries.java) | org.gnit.lucenekmp.document.TestXYPolygonShapeDVQueries | Depth 1 | [x] | 5 | 10 | 90% |
| [org.apache.lucene.document.TestXYPolygonShapeQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/document/TestXYPolygonShapeQueries.java) | org.gnit.lucenekmp.document.TestXYPolygonShapeQueries | Depth 1 | [x] | 2 | 6 | 81% |
| [org.apache.lucene.document.XYPointSortField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/XYPointSortField.java) | org.gnit.lucenekmp.document.XYPointSortField | Depth 3 | [x] | 1 | 1 | 78% |
| [org.apache.lucene.document.XYShapeDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/XYShapeDocValues.java) | org.gnit.lucenekmp.document.XYShapeDocValues | Depth 4 | [x] | 2 | 2 | 68% |
| [org.apache.lucene.document.XYShapeQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/document/XYShapeQuery.java) | org.gnit.lucenekmp.document.XYShapeQuery | Depth 4 | [x] | 2 | 2 | 61% |
| [org.apache.lucene.geo.TestTessellator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/geo/TestTessellator.java) | org.gnit.lucenekmp.geo.TestTessellator | Depth 2 | [x] | 82 | 90 | 98% |
| [org.apache.lucene.index.ApproximatePriorityQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ApproximatePriorityQueue.java) | org.gnit.lucenekmp.index.ApproximatePriorityQueue | Depth 2 | [x] | 4 | 4 | 56% |
| [org.apache.lucene.index.CheckIndex](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/CheckIndex.java) | org.gnit.lucenekmp.index.CheckIndex | Depth 3 | [x] | 53 | 65 | 94% |
| [org.apache.lucene.index.ConcurrentApproximatePriorityQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ConcurrentApproximatePriorityQueue.java) | org.gnit.lucenekmp.index.ConcurrentApproximatePriorityQueue | Depth 2 | [x] | 4 | 4 | 34% |
| [org.apache.lucene.index.ConcurrentMergeScheduler](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ConcurrentMergeScheduler.java) | org.gnit.lucenekmp.index.ConcurrentMergeScheduler | Depth 3 | [x] | 28 | 35 | 84% |
| [org.apache.lucene.index.DocumentsWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriter.java) | org.gnit.lucenekmp.index.DocumentsWriter | Depth 2 | [x] | 27 | 40 | 90% |
| [org.apache.lucene.index.DocumentsWriterFlushControl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriterFlushControl.java) | org.gnit.lucenekmp.index.DocumentsWriterFlushControl | Depth 2 | [x] | 37 | 39 | 99% |
| [org.apache.lucene.index.DocumentsWriterPerThreadPool](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriterPerThreadPool.java) | org.gnit.lucenekmp.index.DocumentsWriterPerThreadPool | Depth 2 | [x] | 10 | 10 | 91% |
| [org.apache.lucene.index.DocumentsWriterStallControl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/DocumentsWriterStallControl.java) | org.gnit.lucenekmp.index.DocumentsWriterStallControl | Depth 2 | [x] | 8 | 8 | 67% |
| [org.apache.lucene.index.FieldInfo](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/FieldInfo.java) | org.gnit.lucenekmp.index.FieldInfo | Depth 2 | [x] | 21 | 21 | 98% |
| [org.apache.lucene.index.IndexWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexWriter.java) | org.gnit.lucenekmp.index.IndexWriter | Depth 3 | [x] | 141 | 202 | 98% |
| [org.apache.lucene.index.IndexWriterConfig](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/IndexWriterConfig.java) | org.gnit.lucenekmp.index.IndexWriterConfig | Depth 2 | [x] | 26 | 26 | 77% |
| [org.apache.lucene.index.LeafMetaData](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/LeafMetaData.java) | org.gnit.lucenekmp.index.LeafMetaData | Depth 2 | [x] | 3 | 0 | 14% |
| [org.apache.lucene.index.LiveIndexWriterConfig](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/LiveIndexWriterConfig.java) | org.gnit.lucenekmp.index.LiveIndexWriterConfig | Depth 2 | [x] | 6 | 6 | 94% |
| [org.apache.lucene.index.LockableConcurrentApproximatePriorityQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/LockableConcurrentApproximatePriorityQueue.java) | org.gnit.lucenekmp.index.LockableConcurrentApproximatePriorityQueue | Depth 2 | [x] | 4 | 5 | 25% |
| [org.apache.lucene.index.MergeScheduler](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/MergeScheduler.java) | org.gnit.lucenekmp.index.MergeScheduler | Depth 2 | [x] | 6 | 6 | 84% |
| [org.apache.lucene.index.NoMergeScheduler](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/NoMergeScheduler.java) | org.gnit.lucenekmp.index.NoMergeScheduler | Depth 2 | [x] | 4 | 4 | 76% |
| [org.apache.lucene.index.ReaderPool](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ReaderPool.java) | org.gnit.lucenekmp.index.ReaderPool | Depth 3 | [x] | 16 | 30 | 94% |
| [org.apache.lucene.index.ReaderSlice](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/ReaderSlice.java) | org.gnit.lucenekmp.index.ReaderSlice | Depth 3 | [x] | 1 | 0 | 64% |
| [org.apache.lucene.index.SerialMergeScheduler](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SerialMergeScheduler.java) | org.gnit.lucenekmp.index.SerialMergeScheduler | Depth 2 | [x] | 2 | 3 | 50% |
| [org.apache.lucene.index.SortFieldProvider](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/SortFieldProvider.java) | org.gnit.lucenekmp.index.SortFieldProvider | Depth 4 | [x] | 6 | 6 | 84% |
| [org.apache.lucene.index.StandardDirectoryReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/StandardDirectoryReader.java) | org.gnit.lucenekmp.index.StandardDirectoryReader | Depth 3 | [x] | 14 | 19 | 94% |
| [org.apache.lucene.index.StoredFieldDataInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/index/StoredFieldDataInput.java) | org.gnit.lucenekmp.index.StoredFieldDataInput | Depth 2 | [x] | 0 | 0 | 80% |
| [org.apache.lucene.index.TestByteSliceReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestByteSliceReader.java) | org.gnit.lucenekmp.index.TestByteSliceReader | Depth 1 | [x] | 4 | 3 | 50% |
| [org.apache.lucene.index.TestCodecs](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestCodecs.java) | org.gnit.lucenekmp.index.TestCodecs | Depth 1 | [x] | 6 | 5 | 83% |
| [org.apache.lucene.index.TestDocumentsWriterStallControl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestDocumentsWriterStallControl.java) | org.gnit.lucenekmp.index.TestDocumentsWriterStallControl | Depth 2 | [x] | 9 | 9 | 33% |
| [org.apache.lucene.index.TestFieldsReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestFieldsReader.java) | org.gnit.lucenekmp.index.TestFieldsReader | Depth 1 | [x] | 4 | 4 | 50% |
| [org.apache.lucene.index.TestFilterCodecReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestFilterCodecReader.java) | org.gnit.lucenekmp.index.TestFilterCodecReader | Depth 1 | [x] | 3 | 2 | 66% |
| [org.apache.lucene.index.TestFilterLeafReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestFilterLeafReader.java) | org.gnit.lucenekmp.index.TestFilterLeafReader | Depth 1 | [x] | 4 | 3 | 75% |
| [org.apache.lucene.index.TestIndexInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexInput.java) | org.gnit.lucenekmp.index.TestIndexInput | Depth 1 | [x] | 9 | 9 | 77% |
| [org.apache.lucene.index.TestIndexWriterOnJRECrash](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexWriterOnJRECrash.java) | org.gnit.lucenekmp.index.TestIndexWriterOnJRECrash | Depth 2 | [x] | 4 | 1 | 25% |
| [org.apache.lucene.index.TestIndexingSequenceNumbers](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestIndexingSequenceNumbers.java) | org.gnit.lucenekmp.index.TestIndexingSequenceNumbers | Depth 2 | [x] | 8 | 10 | 87% |
| [org.apache.lucene.index.TestNoDeletionPolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestNoDeletionPolicy.java) | org.gnit.lucenekmp.index.TestNoDeletionPolicy | Depth 1 | [x] | 4 | 2 | 50% |
| [org.apache.lucene.index.TestNoMergePolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestNoMergePolicy.java) | org.gnit.lucenekmp.index.TestNoMergePolicy | Depth 1 | [x] | 8 | 9 | 75% |
| [org.apache.lucene.index.TestNoMergeScheduler](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestNoMergeScheduler.java) | org.gnit.lucenekmp.index.TestNoMergeScheduler | Depth 1 | [x] | 3 | 1 | 33% |
| [org.apache.lucene.index.TestPendingSoftDeletes](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestPendingSoftDeletes.java) | org.gnit.lucenekmp.index.TestPendingSoftDeletes | Depth 1 | [x] | 7 | 10 | 85% |
| [org.apache.lucene.index.TestPersistentSnapshotDeletionPolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestPersistentSnapshotDeletionPolicy.java) | org.gnit.lucenekmp.index.TestPersistentSnapshotDeletionPolicy | Depth 2 | [x] | 9 | 15 | 77% |
| [org.apache.lucene.index.TestReadOnlyIndex](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestReadOnlyIndex.java) | org.gnit.lucenekmp.index.TestReadOnlyIndex | Depth 1 | [x] | 4 | 4 | 75% |
| [org.apache.lucene.index.TestTerm](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/index/TestTerm.java) | org.gnit.lucenekmp.index.TestTerm | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.internal.hppc.IntArrayList](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/IntArrayList.java) | org.gnit.lucenekmp.internal.hppc.IntArrayList | Depth 3 | [x] | 28 | 28 | 98% |
| [org.apache.lucene.internal.hppc.IntDoubleHashMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/IntDoubleHashMap.java) | org.gnit.lucenekmp.internal.hppc.IntDoubleHashMap | Depth 3 | [x] | 28 | 28 | 98% |
| [org.apache.lucene.internal.hppc.IntFloatHashMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/IntFloatHashMap.java) | org.gnit.lucenekmp.internal.hppc.IntFloatHashMap | Depth 3 | [x] | 28 | 28 | 94% |
| [org.apache.lucene.internal.hppc.IntLongHashMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/IntLongHashMap.java) | org.gnit.lucenekmp.internal.hppc.IntLongHashMap | Depth 3 | [x] | 28 | 28 | 98% |
| [org.apache.lucene.internal.hppc.LongArrayList](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/LongArrayList.java) | org.gnit.lucenekmp.internal.hppc.LongArrayList | Depth 3 | [x] | 28 | 29 | 98% |
| [org.apache.lucene.internal.hppc.LongFloatHashMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/hppc/LongFloatHashMap.java) | org.gnit.lucenekmp.internal.hppc.LongFloatHashMap | Depth 3 | [x] | 28 | 28 | 94% |
| [org.apache.lucene.internal.hppc.TestCharHashSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/internal/hppc/TestCharHashSet.java) | org.gnit.lucenekmp.internal.hppc.TestCharHashSet | Depth 1 | [x] | 28 | 29 | 96% |
| [org.apache.lucene.internal.hppc.TestFloatArrayList](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/internal/hppc/TestFloatArrayList.java) | org.gnit.lucenekmp.internal.hppc.TestFloatArrayList | Depth 1 | [x] | 41 | 41 | 97% |
| [org.apache.lucene.internal.hppc.TestIntDoubleHashMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/internal/hppc/TestIntDoubleHashMap.java) | org.gnit.lucenekmp.internal.hppc.TestIntDoubleHashMap | Depth 1 | [x] | 40 | 42 | 95% |
| [org.apache.lucene.internal.hppc.TestIntFloatHashMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/internal/hppc/TestIntFloatHashMap.java) | org.gnit.lucenekmp.internal.hppc.TestIntFloatHashMap | Depth 1 | [x] | 40 | 42 | 95% |
| [org.apache.lucene.internal.hppc.TestIntHashSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/internal/hppc/TestIntHashSet.java) | org.gnit.lucenekmp.internal.hppc.TestIntHashSet | Depth 1 | [x] | 28 | 27 | 92% |
| [org.apache.lucene.internal.hppc.TestIntLongHashMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/internal/hppc/TestIntLongHashMap.java) | org.gnit.lucenekmp.internal.hppc.TestIntLongHashMap | Depth 1 | [x] | 40 | 42 | 95% |
| [org.apache.lucene.internal.hppc.TestIntObjectHashMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/internal/hppc/TestIntObjectHashMap.java) | org.gnit.lucenekmp.internal.hppc.TestIntObjectHashMap | Depth 1 | [x] | 38 | 32 | 81% |
| [org.apache.lucene.internal.hppc.TestLongFloatHashMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/internal/hppc/TestLongFloatHashMap.java) | org.gnit.lucenekmp.internal.hppc.TestLongFloatHashMap | Depth 1 | [x] | 40 | 42 | 95% |
| [org.apache.lucene.internal.hppc.TestLongHashSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/internal/hppc/TestLongHashSet.java) | org.gnit.lucenekmp.internal.hppc.TestLongHashSet | Depth 1 | [x] | 28 | 30 | 96% |
| [org.apache.lucene.internal.hppc.TestLongObjectHashMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/internal/hppc/TestLongObjectHashMap.java) | org.gnit.lucenekmp.internal.hppc.TestLongObjectHashMap | Depth 1 | [x] | 36 | 36 | 91% |
| [org.apache.lucene.internal.tests.TestSecrets](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/tests/TestSecrets.java) | org.gnit.lucenekmp.internal.tests.TestSecrets | Depth 2 | [x] | 2 | 8 | 92% |
| [org.apache.lucene.internal.vectorization.DefaultVectorUtilSupport](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/vectorization/DefaultVectorUtilSupport.java) | org.gnit.lucenekmp.internal.vectorization.DefaultVectorUtilSupport | Depth 3 | [x] | 11 | 11 | 81% |
| [org.apache.lucene.internal.vectorization.TestVectorScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/internal/vectorization/TestVectorScorer.java) | org.gnit.lucenekmp.internal.vectorization.TestVectorScorer | Depth 1 | [x] | 23 | 28 | 95% |
| [org.apache.lucene.internal.vectorization.TestVectorUtilSupport](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/internal/vectorization/TestVectorUtilSupport.java) | org.gnit.lucenekmp.internal.vectorization.TestVectorUtilSupport | Depth 1 | [x] | 12 | 38 | 91% |
| [org.apache.lucene.internal.vectorization.VectorizationProvider](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/internal/vectorization/VectorizationProvider.java) | org.gnit.lucenekmp.internal.vectorization.VectorizationProvider | Depth 3 | [x] | 4 | 2 | 59% |
| [org.apache.lucene.queries.CommonTermsQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/CommonTermsQuery.java) | org.gnit.lucenekmp.queries.CommonTermsQuery | Depth 2 | [] | 10 | 0 | 0% |
| [org.apache.lucene.queries.TestCommonTermsQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/TestCommonTermsQuery.java) | org.gnit.lucenekmp.queries.TestCommonTermsQuery | Depth 1 | [] | 10 | 0 | 0% |
| [org.apache.lucene.queries.function.FunctionMatchQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/FunctionMatchQuery.java) | org.gnit.lucenekmp.queries.function.FunctionMatchQuery | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.FunctionQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/FunctionQuery.java) | org.gnit.lucenekmp.queries.function.FunctionQuery | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.FunctionRangeQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/FunctionRangeQuery.java) | org.gnit.lucenekmp.queries.function.FunctionRangeQuery | Depth 3 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.function.FunctionScoreQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/FunctionScoreQuery.java) | org.gnit.lucenekmp.queries.function.FunctionScoreQuery | Depth 3 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.function.FunctionTestSetup](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/function/FunctionTestSetup.java) | org.gnit.lucenekmp.queries.function.FunctionTestSetup | Depth 1 | [] | 7 | 0 | 0% |
| [org.apache.lucene.queries.function.FunctionValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/FunctionValues.java) | org.gnit.lucenekmp.queries.function.FunctionValues | Depth 3 | [] | 25 | 0 | 0% |
| [org.apache.lucene.queries.function.IndexReaderFunctions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/IndexReaderFunctions.java) | org.gnit.lucenekmp.queries.function.IndexReaderFunctions | Depth 3 | [] | 9 | 0 | 0% |
| [org.apache.lucene.queries.function.TestDocValuesFieldSources](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/function/TestDocValuesFieldSources.java) | org.gnit.lucenekmp.queries.function.TestDocValuesFieldSources | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.TestFieldScoreQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/function/TestFieldScoreQuery.java) | org.gnit.lucenekmp.queries.function.TestFieldScoreQuery | Depth 1 | [] | 12 | 0 | 0% |
| [org.apache.lucene.queries.function.TestFunctionMatchQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/function/TestFunctionMatchQuery.java) | org.gnit.lucenekmp.queries.function.TestFunctionMatchQuery | Depth 1 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.function.TestFunctionQueryExplanations](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/function/TestFunctionQueryExplanations.java) | org.gnit.lucenekmp.queries.function.TestFunctionQueryExplanations | Depth 1 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.function.TestFunctionQuerySort](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/function/TestFunctionQuerySort.java) | org.gnit.lucenekmp.queries.function.TestFunctionQuerySort | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.TestFunctionRangeQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/function/TestFunctionRangeQuery.java) | org.gnit.lucenekmp.queries.function.TestFunctionRangeQuery | Depth 1 | [] | 15 | 0 | 0% |
| [org.apache.lucene.queries.function.TestFunctionScoreExplanations](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/function/TestFunctionScoreExplanations.java) | org.gnit.lucenekmp.queries.function.TestFunctionScoreExplanations | Depth 1 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.function.TestFunctionScoreQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/function/TestFunctionScoreQuery.java) | org.gnit.lucenekmp.queries.function.TestFunctionScoreQuery | Depth 1 | [] | 14 | 0 | 0% |
| [org.apache.lucene.queries.function.TestIndexReaderFunctions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/function/TestIndexReaderFunctions.java) | org.gnit.lucenekmp.queries.function.TestIndexReaderFunctions | Depth 1 | [] | 15 | 0 | 0% |
| [org.apache.lucene.queries.function.TestKnnVectorSimilarityFunctions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/function/TestKnnVectorSimilarityFunctions.java) | org.gnit.lucenekmp.queries.function.TestKnnVectorSimilarityFunctions | Depth 1 | [] | 14 | 0 | 0% |
| [org.apache.lucene.queries.function.TestLongNormValueSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/function/TestLongNormValueSource.java) | org.gnit.lucenekmp.queries.function.TestLongNormValueSource | Depth 1 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queries.function.TestSortedSetFieldSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/function/TestSortedSetFieldSource.java) | org.gnit.lucenekmp.queries.function.TestSortedSetFieldSource | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.function.TestValueSources](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/function/TestValueSources.java) | org.gnit.lucenekmp.queries.function.TestValueSources | Depth 1 | [] | 44 | 0 | 0% |
| [org.apache.lucene.queries.function.ValueSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/ValueSource.java) | org.gnit.lucenekmp.queries.function.ValueSource | Depth 3 | [] | 7 | 0 | 0% |
| [org.apache.lucene.queries.function.ValueSourceScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/ValueSourceScorer.java) | org.gnit.lucenekmp.queries.function.ValueSourceScorer | Depth 3 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.function.docvalues.BoolDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/docvalues/BoolDocValues.java) | org.gnit.lucenekmp.queries.function.docvalues.BoolDocValues | Depth 3 | [] | 9 | 0 | 0% |
| [org.apache.lucene.queries.function.docvalues.DocTermsIndexDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/docvalues/DocTermsIndexDocValues.java) | org.gnit.lucenekmp.queries.function.docvalues.DocTermsIndexDocValues | Depth 5 | [] | 11 | 0 | 0% |
| [org.apache.lucene.queries.function.docvalues.DoubleDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/docvalues/DoubleDocValues.java) | org.gnit.lucenekmp.queries.function.docvalues.DoubleDocValues | Depth 3 | [] | 10 | 0 | 0% |
| [org.apache.lucene.queries.function.docvalues.FloatDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/docvalues/FloatDocValues.java) | org.gnit.lucenekmp.queries.function.docvalues.FloatDocValues | Depth 3 | [] | 9 | 0 | 0% |
| [org.apache.lucene.queries.function.docvalues.IntDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/docvalues/IntDocValues.java) | org.gnit.lucenekmp.queries.function.docvalues.IntDocValues | Depth 3 | [] | 9 | 0 | 0% |
| [org.apache.lucene.queries.function.docvalues.LongDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/docvalues/LongDocValues.java) | org.gnit.lucenekmp.queries.function.docvalues.LongDocValues | Depth 3 | [] | 11 | 0 | 0% |
| [org.apache.lucene.queries.function.docvalues.StrDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/docvalues/StrDocValues.java) | org.gnit.lucenekmp.queries.function.docvalues.StrDocValues | Depth 5 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.function.docvalues.TestBoolValOfNumericDVs](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/function/docvalues/TestBoolValOfNumericDVs.java) | org.gnit.lucenekmp.queries.function.docvalues.TestBoolValOfNumericDVs | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.BoolFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/BoolFunction.java) | org.gnit.lucenekmp.queries.function.valuesource.BoolFunction | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.ByteKnnVectorFieldSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/ByteKnnVectorFieldSource.java) | org.gnit.lucenekmp.queries.function.valuesource.ByteKnnVectorFieldSource | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.ByteVectorSimilarityFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/ByteVectorSimilarityFunction.java) | org.gnit.lucenekmp.queries.function.valuesource.ByteVectorSimilarityFunction | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.BytesRefFieldSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/BytesRefFieldSource.java) | org.gnit.lucenekmp.queries.function.valuesource.BytesRefFieldSource | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.ConstKnnByteVectorValueSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/ConstKnnByteVectorValueSource.java) | org.gnit.lucenekmp.queries.function.valuesource.ConstKnnByteVectorValueSource | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.ConstKnnFloatValueSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/ConstKnnFloatValueSource.java) | org.gnit.lucenekmp.queries.function.valuesource.ConstKnnFloatValueSource | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.ConstNumberSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/ConstNumberSource.java) | org.gnit.lucenekmp.queries.function.valuesource.ConstNumberSource | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.ConstValueSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/ConstValueSource.java) | org.gnit.lucenekmp.queries.function.valuesource.ConstValueSource | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.DivFloatFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/DivFloatFunction.java) | org.gnit.lucenekmp.queries.function.valuesource.DivFloatFunction | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.DocFreqValueSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/DocFreqValueSource.java) | org.gnit.lucenekmp.queries.function.valuesource.DocFreqValueSource | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.DoubleConstValueSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/DoubleConstValueSource.java) | org.gnit.lucenekmp.queries.function.valuesource.DoubleConstValueSource | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.DoubleFieldSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/DoubleFieldSource.java) | org.gnit.lucenekmp.queries.function.valuesource.DoubleFieldSource | Depth 3 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.DualFloatFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/DualFloatFunction.java) | org.gnit.lucenekmp.queries.function.valuesource.DualFloatFunction | Depth 4 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.FieldCacheSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/FieldCacheSource.java) | org.gnit.lucenekmp.queries.function.valuesource.FieldCacheSource | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.FloatFieldSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/FloatFieldSource.java) | org.gnit.lucenekmp.queries.function.valuesource.FloatFieldSource | Depth 3 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.FloatKnnVectorFieldSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/FloatKnnVectorFieldSource.java) | org.gnit.lucenekmp.queries.function.valuesource.FloatKnnVectorFieldSource | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.FloatVectorSimilarityFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/FloatVectorSimilarityFunction.java) | org.gnit.lucenekmp.queries.function.valuesource.FloatVectorSimilarityFunction | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.IDFValueSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/IDFValueSource.java) | org.gnit.lucenekmp.queries.function.valuesource.IDFValueSource | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.IfFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/IfFunction.java) | org.gnit.lucenekmp.queries.function.valuesource.IfFunction | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.IntFieldSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/IntFieldSource.java) | org.gnit.lucenekmp.queries.function.valuesource.IntFieldSource | Depth 3 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.JoinDocFreqValueSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/JoinDocFreqValueSource.java) | org.gnit.lucenekmp.queries.function.valuesource.JoinDocFreqValueSource | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.LinearFloatFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/LinearFloatFunction.java) | org.gnit.lucenekmp.queries.function.valuesource.LinearFloatFunction | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.LiteralValueSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/LiteralValueSource.java) | org.gnit.lucenekmp.queries.function.valuesource.LiteralValueSource | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.LongFieldSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/LongFieldSource.java) | org.gnit.lucenekmp.queries.function.valuesource.LongFieldSource | Depth 3 | [] | 6 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.MaxDocValueSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/MaxDocValueSource.java) | org.gnit.lucenekmp.queries.function.valuesource.MaxDocValueSource | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.MaxFloatFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/MaxFloatFunction.java) | org.gnit.lucenekmp.queries.function.valuesource.MaxFloatFunction | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.MinFloatFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/MinFloatFunction.java) | org.gnit.lucenekmp.queries.function.valuesource.MinFloatFunction | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.MultiBoolFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/MultiBoolFunction.java) | org.gnit.lucenekmp.queries.function.valuesource.MultiBoolFunction | Depth 3 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.MultiFloatFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/MultiFloatFunction.java) | org.gnit.lucenekmp.queries.function.valuesource.MultiFloatFunction | Depth 3 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.MultiFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/MultiFunction.java) | org.gnit.lucenekmp.queries.function.valuesource.MultiFunction | Depth 3 | [] | 7 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.MultiValuedDoubleFieldSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/MultiValuedDoubleFieldSource.java) | org.gnit.lucenekmp.queries.function.valuesource.MultiValuedDoubleFieldSource | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.MultiValuedFloatFieldSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/MultiValuedFloatFieldSource.java) | org.gnit.lucenekmp.queries.function.valuesource.MultiValuedFloatFieldSource | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.MultiValuedIntFieldSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/MultiValuedIntFieldSource.java) | org.gnit.lucenekmp.queries.function.valuesource.MultiValuedIntFieldSource | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.MultiValuedLongFieldSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/MultiValuedLongFieldSource.java) | org.gnit.lucenekmp.queries.function.valuesource.MultiValuedLongFieldSource | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.NormValueSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/NormValueSource.java) | org.gnit.lucenekmp.queries.function.valuesource.NormValueSource | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.NumDocsValueSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/NumDocsValueSource.java) | org.gnit.lucenekmp.queries.function.valuesource.NumDocsValueSource | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.PowFloatFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/PowFloatFunction.java) | org.gnit.lucenekmp.queries.function.valuesource.PowFloatFunction | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.ProductFloatFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/ProductFloatFunction.java) | org.gnit.lucenekmp.queries.function.valuesource.ProductFloatFunction | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.QueryDocValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/QueryDocValues.java) | org.gnit.lucenekmp.queries.function.valuesource.QueryDocValues | Depth 4 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.QueryValueSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/QueryValueSource.java) | org.gnit.lucenekmp.queries.function.valuesource.QueryValueSource | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.RangeMapFloatFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/RangeMapFloatFunction.java) | org.gnit.lucenekmp.queries.function.valuesource.RangeMapFloatFunction | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.ReciprocalFloatFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/ReciprocalFloatFunction.java) | org.gnit.lucenekmp.queries.function.valuesource.ReciprocalFloatFunction | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.ScaleFloatFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/ScaleFloatFunction.java) | org.gnit.lucenekmp.queries.function.valuesource.ScaleFloatFunction | Depth 3 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.SortedSetFieldSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/SortedSetFieldSource.java) | org.gnit.lucenekmp.queries.function.valuesource.SortedSetFieldSource | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.SumFloatFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/SumFloatFunction.java) | org.gnit.lucenekmp.queries.function.valuesource.SumFloatFunction | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.SumTotalTermFreqValueSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/SumTotalTermFreqValueSource.java) | org.gnit.lucenekmp.queries.function.valuesource.SumTotalTermFreqValueSource | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.TFValueSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/TFValueSource.java) | org.gnit.lucenekmp.queries.function.valuesource.TFValueSource | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.TermFreqValueSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/TermFreqValueSource.java) | org.gnit.lucenekmp.queries.function.valuesource.TermFreqValueSource | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.TotalTermFreqValueSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/TotalTermFreqValueSource.java) | org.gnit.lucenekmp.queries.function.valuesource.TotalTermFreqValueSource | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.VectorFieldFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/VectorFieldFunction.java) | org.gnit.lucenekmp.queries.function.valuesource.VectorFieldFunction | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.function.valuesource.VectorSimilarityFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/function/valuesource/VectorSimilarityFunction.java) | org.gnit.lucenekmp.queries.function.valuesource.VectorSimilarityFunction | Depth 4 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.intervals.BlockIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/BlockIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.BlockIntervalsSource | Depth 4 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.intervals.CachingMatchesIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/CachingMatchesIterator.java) | org.gnit.lucenekmp.queries.intervals.CachingMatchesIterator | Depth 5 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.intervals.ConjunctionIntervalIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/ConjunctionIntervalIterator.java) | org.gnit.lucenekmp.queries.intervals.ConjunctionIntervalIterator | Depth 5 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.intervals.ConjunctionIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/ConjunctionIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.ConjunctionIntervalsSource | Depth 4 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.intervals.ConjunctionMatchesIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/ConjunctionMatchesIterator.java) | org.gnit.lucenekmp.queries.intervals.ConjunctionMatchesIterator | Depth 5 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queries.intervals.ContainedByIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/ContainedByIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.ContainedByIntervalsSource | Depth 4 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.intervals.ContainingIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/ContainingIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.ContainingIntervalsSource | Depth 4 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queries.intervals.DifferenceIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/DifferenceIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.DifferenceIntervalsSource | Depth 4 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.intervals.DisiPriorityQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/DisiPriorityQueue.java) | org.gnit.lucenekmp.queries.intervals.DisiPriorityQueue | Depth 5 | [] | 11 | 0 | 0% |
| [org.apache.lucene.queries.intervals.DisiWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/DisiWrapper.java) | org.gnit.lucenekmp.queries.intervals.DisiWrapper | Depth 5 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queries.intervals.DisjunctionDISIApproximation](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/DisjunctionDISIApproximation.java) | org.gnit.lucenekmp.queries.intervals.DisjunctionDISIApproximation | Depth 5 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.intervals.DisjunctionIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/DisjunctionIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.DisjunctionIntervalsSource | Depth 4 | [] | 7 | 0 | 0% |
| [org.apache.lucene.queries.intervals.Disjunctions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/Disjunctions.java) | org.gnit.lucenekmp.queries.intervals.Disjunctions | Depth 4 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.intervals.ExtendedIntervalIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/ExtendedIntervalIterator.java) | org.gnit.lucenekmp.queries.intervals.ExtendedIntervalIterator | Depth 4 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.intervals.ExtendedIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/ExtendedIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.ExtendedIntervalsSource | Depth 4 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.intervals.FilteredIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/FilteredIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.FilteredIntervalsSource | Depth 4 | [] | 8 | 0 | 0% |
| [org.apache.lucene.queries.intervals.FilteringIntervalIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/FilteringIntervalIterator.java) | org.gnit.lucenekmp.queries.intervals.FilteringIntervalIterator | Depth 5 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.intervals.FixedFieldIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/FixedFieldIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.FixedFieldIntervalsSource | Depth 3 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.intervals.IntervalBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/IntervalBuilder.java) | org.gnit.lucenekmp.queries.intervals.IntervalBuilder | Depth 2 | [] | 7 | 0 | 0% |
| [org.apache.lucene.queries.intervals.IntervalFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/IntervalFilter.java) | org.gnit.lucenekmp.queries.intervals.IntervalFilter | Depth 5 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.intervals.IntervalIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/IntervalIterator.java) | org.gnit.lucenekmp.queries.intervals.IntervalIterator | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.intervals.IntervalMatches](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/IntervalMatches.java) | org.gnit.lucenekmp.queries.intervals.IntervalMatches | Depth 4 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.intervals.IntervalMatchesIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/IntervalMatchesIterator.java) | org.gnit.lucenekmp.queries.intervals.IntervalMatchesIterator | Depth 2 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queries.intervals.IntervalQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/IntervalQuery.java) | org.gnit.lucenekmp.queries.intervals.IntervalQuery | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.intervals.IntervalScoreFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/IntervalScoreFunction.java) | org.gnit.lucenekmp.queries.intervals.IntervalScoreFunction | Depth 4 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queries.intervals.IntervalScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/IntervalScorer.java) | org.gnit.lucenekmp.queries.intervals.IntervalScorer | Depth 5 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queries.intervals.Intervals](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/Intervals.java) | org.gnit.lucenekmp.queries.intervals.Intervals | Depth 2 | [] | 43 | 0 | 0% |
| [org.apache.lucene.queries.intervals.IntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/IntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.IntervalsSource | Depth 2 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.intervals.MinimizingConjunctionIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/MinimizingConjunctionIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.MinimizingConjunctionIntervalsSource | Depth 4 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.intervals.MinimumShouldMatchIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/MinimumShouldMatchIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.MinimumShouldMatchIntervalsSource | Depth 4 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.intervals.MultiTermIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/MultiTermIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.MultiTermIntervalsSource | Depth 4 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.intervals.NoMatchIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/NoMatchIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.NoMatchIntervalsSource | Depth 3 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.intervals.NonOverlappingIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/NonOverlappingIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.NonOverlappingIntervalsSource | Depth 4 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.intervals.NotContainedByIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/NotContainedByIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.NotContainedByIntervalsSource | Depth 4 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.intervals.NotContainingIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/NotContainingIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.NotContainingIntervalsSource | Depth 4 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.intervals.OffsetIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/OffsetIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.OffsetIntervalsSource | Depth 3 | [] | 6 | 0 | 0% |
| [org.apache.lucene.queries.intervals.OneTimeIntervalSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/intervals/OneTimeIntervalSource.java) | org.gnit.lucenekmp.queries.intervals.OneTimeIntervalSource | Depth 1 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.intervals.OrderedIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/OrderedIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.OrderedIntervalsSource | Depth 4 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.intervals.OverlappingIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/OverlappingIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.OverlappingIntervalsSource | Depth 4 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queries.intervals.PayloadFilteredTermIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/PayloadFilteredTermIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.PayloadFilteredTermIntervalsSource | Depth 4 | [] | 7 | 0 | 0% |
| [org.apache.lucene.queries.intervals.RelativeIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/RelativeIterator.java) | org.gnit.lucenekmp.queries.intervals.RelativeIterator | Depth 5 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.intervals.RepeatingIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/RepeatingIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.RepeatingIntervalsSource | Depth 5 | [] | 6 | 0 | 0% |
| [org.apache.lucene.queries.intervals.TermIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/TermIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.TermIntervalsSource | Depth 4 | [] | 8 | 0 | 0% |
| [org.apache.lucene.queries.intervals.TestComplexMatches](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/intervals/TestComplexMatches.java) | org.gnit.lucenekmp.queries.intervals.TestComplexMatches | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.intervals.TestDisjunctionRewrites](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/intervals/TestDisjunctionRewrites.java) | org.gnit.lucenekmp.queries.intervals.TestDisjunctionRewrites | Depth 1 | [] | 12 | 0 | 0% |
| [org.apache.lucene.queries.intervals.TestIntervalBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/intervals/TestIntervalBuilder.java) | org.gnit.lucenekmp.queries.intervals.TestIntervalBuilder | Depth 1 | [] | 12 | 0 | 0% |
| [org.apache.lucene.queries.intervals.TestIntervalQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/intervals/TestIntervalQuery.java) | org.gnit.lucenekmp.queries.intervals.TestIntervalQuery | Depth 1 | [] | 37 | 0 | 0% |
| [org.apache.lucene.queries.intervals.TestIntervals](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/intervals/TestIntervals.java) | org.gnit.lucenekmp.queries.intervals.TestIntervals | Depth 1 | [] | 50 | 0 | 0% |
| [org.apache.lucene.queries.intervals.TestPayloadFilteredInterval](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/intervals/TestPayloadFilteredInterval.java) | org.gnit.lucenekmp.queries.intervals.TestPayloadFilteredInterval | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.intervals.TestSimplifications](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/intervals/TestSimplifications.java) | org.gnit.lucenekmp.queries.intervals.TestSimplifications | Depth 1 | [] | 12 | 0 | 0% |
| [org.apache.lucene.queries.intervals.UnorderedIntervalsSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/intervals/UnorderedIntervalsSource.java) | org.gnit.lucenekmp.queries.intervals.UnorderedIntervalsSource | Depth 4 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.mlt.MoreLikeThis](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/mlt/MoreLikeThis.java) | org.gnit.lucenekmp.queries.mlt.MoreLikeThis | Depth 3 | [] | 15 | 0 | 0% |
| [org.apache.lucene.queries.mlt.MoreLikeThisQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/mlt/MoreLikeThisQuery.java) | org.gnit.lucenekmp.queries.mlt.MoreLikeThisQuery | Depth 2 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.mlt.TestMoreLikeThis](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/mlt/TestMoreLikeThis.java) | org.gnit.lucenekmp.queries.mlt.TestMoreLikeThis | Depth 1 | [] | 18 | 0 | 0% |
| [org.apache.lucene.queries.payloads.AveragePayloadFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/payloads/AveragePayloadFunction.java) | org.gnit.lucenekmp.queries.payloads.AveragePayloadFunction | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.payloads.MaxPayloadFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/payloads/MaxPayloadFunction.java) | org.gnit.lucenekmp.queries.payloads.MaxPayloadFunction | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.payloads.MinPayloadFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/payloads/MinPayloadFunction.java) | org.gnit.lucenekmp.queries.payloads.MinPayloadFunction | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.payloads.PayloadDecoder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/payloads/PayloadDecoder.java) | org.gnit.lucenekmp.queries.payloads.PayloadDecoder | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.payloads.PayloadFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/payloads/PayloadFunction.java) | org.gnit.lucenekmp.queries.payloads.PayloadFunction | Depth 2 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.payloads.PayloadHelper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/payloads/PayloadHelper.java) | org.gnit.lucenekmp.queries.payloads.PayloadHelper | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.payloads.PayloadMatcher](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/payloads/PayloadMatcher.java) | org.gnit.lucenekmp.queries.payloads.PayloadMatcher | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.payloads.PayloadMatcherFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/payloads/PayloadMatcherFactory.java) | org.gnit.lucenekmp.queries.payloads.PayloadMatcherFactory | Depth 5 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.payloads.PayloadScoreQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/payloads/PayloadScoreQuery.java) | org.gnit.lucenekmp.queries.payloads.PayloadScoreQuery | Depth 3 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queries.payloads.SpanPayloadCheckQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/payloads/SpanPayloadCheckQuery.java) | org.gnit.lucenekmp.queries.payloads.SpanPayloadCheckQuery | Depth 3 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.payloads.TestPayloadCheckQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/payloads/TestPayloadCheckQuery.java) | org.gnit.lucenekmp.queries.payloads.TestPayloadCheckQuery | Depth 1 | [] | 10 | 0 | 0% |
| [org.apache.lucene.queries.payloads.TestPayloadExplanations](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/payloads/TestPayloadExplanations.java) | org.gnit.lucenekmp.queries.payloads.TestPayloadExplanations | Depth 1 | [] | 8 | 0 | 0% |
| [org.apache.lucene.queries.payloads.TestPayloadScoreQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/payloads/TestPayloadScoreQuery.java) | org.gnit.lucenekmp.queries.payloads.TestPayloadScoreQuery | Depth 1 | [] | 11 | 0 | 0% |
| [org.apache.lucene.queries.payloads.TestPayloadSpanPositions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/payloads/TestPayloadSpanPositions.java) | org.gnit.lucenekmp.queries.payloads.TestPayloadSpanPositions | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.payloads.TestPayloadSpans](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/payloads/TestPayloadSpans.java) | org.gnit.lucenekmp.queries.payloads.TestPayloadSpans | Depth 1 | [] | 13 | 0 | 0% |
| [org.apache.lucene.queries.payloads.TestPayloadTermQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/payloads/TestPayloadTermQuery.java) | org.gnit.lucenekmp.queries.payloads.TestPayloadTermQuery | Depth 1 | [] | 7 | 0 | 0% |
| [org.apache.lucene.queries.spans.AssertingSpanQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/AssertingSpanQuery.java) | org.gnit.lucenekmp.queries.spans.AssertingSpanQuery | Depth 1 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queries.spans.AssertingSpanWeight](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/AssertingSpanWeight.java) | org.gnit.lucenekmp.queries.spans.AssertingSpanWeight | Depth 1 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queries.spans.AssertingSpans](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/AssertingSpans.java) | org.gnit.lucenekmp.queries.spans.AssertingSpans | Depth 2 | [] | 10 | 0 | 0% |
| [org.apache.lucene.queries.spans.BaseSpanExplanationTestCase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/BaseSpanExplanationTestCase.java) | org.gnit.lucenekmp.queries.spans.BaseSpanExplanationTestCase | Depth 1 | [] | 11 | 0 | 0% |
| [org.apache.lucene.queries.spans.JustCompileSearchSpans](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/JustCompileSearchSpans.java) | org.gnit.lucenekmp.queries.spans.JustCompileSearchSpans | Depth 2 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queries.spans.SpanNearQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/spans/SpanNearQuery.java) | org.gnit.lucenekmp.queries.spans.SpanNearQuery | Depth 3 | [x] | 6 | 5 | 88% |
| [org.apache.lucene.queries.spans.SpanTermQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/spans/SpanTermQuery.java) | org.gnit.lucenekmp.queries.spans.SpanTermQuery | Depth 3 | [x] | 3 | 3 | 81% |
| [org.apache.lucene.queries.spans.SpanTestUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/SpanTestUtil.java) | org.gnit.lucenekmp.queries.spans.SpanTestUtil | Depth 1 | [] | 16 | 0 | 0% |
| [org.apache.lucene.queries.spans.Spans](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/java/org/apache/lucene/queries/spans/Spans.java) | org.gnit.lucenekmp.queries.spans.Spans | Depth 2 | [x] | 8 | 8 | 77% |
| [org.apache.lucene.queries.spans.TestBasics](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestBasics.java) | org.gnit.lucenekmp.queries.spans.TestBasics | Depth 1 | [] | 32 | 0 | 0% |
| [org.apache.lucene.queries.spans.TestFieldMaskingSpanQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestFieldMaskingSpanQuery.java) | org.gnit.lucenekmp.queries.spans.TestFieldMaskingSpanQuery | Depth 1 | [] | 17 | 0 | 0% |
| [org.apache.lucene.queries.spans.TestFilterSpans](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestFilterSpans.java) | org.gnit.lucenekmp.queries.spans.TestFilterSpans | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.spans.TestNearSpansOrdered](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestNearSpansOrdered.java) | org.gnit.lucenekmp.queries.spans.TestNearSpansOrdered | Depth 1 | [] | 26 | 0 | 0% |
| [org.apache.lucene.queries.spans.TestQueryRescorerWithSpans](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestQueryRescorerWithSpans.java) | org.gnit.lucenekmp.queries.spans.TestQueryRescorerWithSpans | Depth 1 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.spans.TestSpanCollection](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestSpanCollection.java) | org.gnit.lucenekmp.queries.spans.TestSpanCollection | Depth 1 | [] | 6 | 0 | 0% |
| [org.apache.lucene.queries.spans.TestSpanContainQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestSpanContainQuery.java) | org.gnit.lucenekmp.queries.spans.TestSpanContainQuery | Depth 1 | [] | 10 | 0 | 0% |
| [org.apache.lucene.queries.spans.TestSpanExplanations](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestSpanExplanations.java) | org.gnit.lucenekmp.queries.spans.TestSpanExplanations | Depth 1 | [] | 33 | 0 | 0% |
| [org.apache.lucene.queries.spans.TestSpanExplanationsOfNonMatches](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestSpanExplanationsOfNonMatches.java) | org.gnit.lucenekmp.queries.spans.TestSpanExplanationsOfNonMatches | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.spans.TestSpanFirstQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestSpanFirstQuery.java) | org.gnit.lucenekmp.queries.spans.TestSpanFirstQuery | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.spans.TestSpanMatches](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestSpanMatches.java) | org.gnit.lucenekmp.queries.spans.TestSpanMatches | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.spans.TestSpanMultiTermQueryWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestSpanMultiTermQueryWrapper.java) | org.gnit.lucenekmp.queries.spans.TestSpanMultiTermQueryWrapper | Depth 1 | [] | 11 | 0 | 0% |
| [org.apache.lucene.queries.spans.TestSpanNearQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestSpanNearQuery.java) | org.gnit.lucenekmp.queries.spans.TestSpanNearQuery | Depth 1 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queries.spans.TestSpanNotQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestSpanNotQuery.java) | org.gnit.lucenekmp.queries.spans.TestSpanNotQuery | Depth 1 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.spans.TestSpanOrQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestSpanOrQuery.java) | org.gnit.lucenekmp.queries.spans.TestSpanOrQuery | Depth 1 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queries.spans.TestSpanQueryVisitor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestSpanQueryVisitor.java) | org.gnit.lucenekmp.queries.spans.TestSpanQueryVisitor | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queries.spans.TestSpanSearchEquivalence](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestSpanSearchEquivalence.java) | org.gnit.lucenekmp.queries.spans.TestSpanSearchEquivalence | Depth 1 | [] | 32 | 0 | 0% |
| [org.apache.lucene.queries.spans.TestSpanSimilarity](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestSpanSimilarity.java) | org.gnit.lucenekmp.queries.spans.TestSpanSimilarity | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.spans.TestSpanTermQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestSpanTermQuery.java) | org.gnit.lucenekmp.queries.spans.TestSpanTermQuery | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queries.spans.TestSpans](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestSpans.java) | org.gnit.lucenekmp.queries.spans.TestSpans | Depth 1 | [] | 39 | 0 | 0% |
| [org.apache.lucene.queries.spans.TestSpansEnum](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queries/src/test/org/apache/lucene/queries/spans/TestSpansEnum.java) | org.gnit.lucenekmp.queries.spans.TestSpansEnum | Depth 1 | [] | 11 | 0 | 0% |
| [org.apache.lucene.queryparser.classic.TestMultiAnalyzer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/classic/TestMultiAnalyzer.java) | org.gnit.lucenekmp.queryparser.classic.TestMultiAnalyzer | Depth 1 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.classic.TestMultiFieldQueryParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/classic/TestMultiFieldQueryParser.java) | org.gnit.lucenekmp.queryparser.classic.TestMultiFieldQueryParser | Depth 1 | [] | 13 | 0 | 0% |
| [org.apache.lucene.queryparser.classic.TestMultiPhraseQueryParsing](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/classic/TestMultiPhraseQueryParsing.java) | org.gnit.lucenekmp.queryparser.classic.TestMultiPhraseQueryParsing | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.classic.TestQueryParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/classic/TestQueryParser.java) | org.gnit.lucenekmp.queryparser.classic.TestQueryParser | Depth 1 | [] | 40 | 0 | 0% |
| [org.apache.lucene.queryparser.complexPhrase.ComplexPhraseQueryParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/complexPhrase/ComplexPhraseQueryParser.java) | org.gnit.lucenekmp.queryparser.complexPhrase.ComplexPhraseQueryParser | Depth 3 | [x] | 8 | 7 | 88% |
| [org.apache.lucene.queryparser.complexPhrase.TestComplexPhraseQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/complexPhrase/TestComplexPhraseQuery.java) | org.gnit.lucenekmp.queryparser.complexPhrase.TestComplexPhraseQuery | Depth 2 | [] | 13 | 0 | 0% |
| [org.apache.lucene.queryparser.ext.ExtendableQueryParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/ext/ExtendableQueryParser.java) | org.gnit.lucenekmp.queryparser.ext.ExtendableQueryParser | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.ext.ExtensionQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/ext/ExtensionQuery.java) | org.gnit.lucenekmp.queryparser.ext.ExtensionQuery | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.ext.ExtensionStub](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/ext/ExtensionStub.java) | org.gnit.lucenekmp.queryparser.ext.ExtensionStub | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.ext.Extensions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/ext/Extensions.java) | org.gnit.lucenekmp.queryparser.ext.Extensions | Depth 3 | [] | 6 | 0 | 0% |
| [org.apache.lucene.queryparser.ext.ParserExtension](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/ext/ParserExtension.java) | org.gnit.lucenekmp.queryparser.ext.ParserExtension | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.ext.TestExtendableQueryParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/ext/TestExtendableQueryParser.java) | org.gnit.lucenekmp.queryparser.ext.TestExtendableQueryParser | Depth 1 | [] | 7 | 0 | 0% |
| [org.apache.lucene.queryparser.ext.TestExtensions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/ext/TestExtensions.java) | org.gnit.lucenekmp.queryparser.ext.TestExtensions | Depth 1 | [] | 6 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.QueryNodeError](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/QueryNodeError.java) | org.gnit.lucenekmp.queryparser.flexible.core.QueryNodeError | Depth 4 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.QueryNodeException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/QueryNodeException.java) | org.gnit.lucenekmp.queryparser.flexible.core.QueryNodeException | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.QueryNodeParseException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/QueryNodeParseException.java) | org.gnit.lucenekmp.queryparser.flexible.core.QueryNodeParseException | Depth 2 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.QueryParserHelper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/QueryParserHelper.java) | org.gnit.lucenekmp.queryparser.flexible.core.QueryParserHelper | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.builders.QueryBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/builders/QueryBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.core.builders.QueryBuilder | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.builders.QueryTreeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/builders/QueryTreeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.core.builders.QueryTreeBuilder | Depth 2 | [] | 7 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.builders.TestQueryTreeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/core/builders/TestQueryTreeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.core.builders.TestQueryTreeBuilder | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.config.AbstractQueryConfig](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/config/AbstractQueryConfig.java) | org.gnit.lucenekmp.queryparser.flexible.core.config.AbstractQueryConfig | Depth 3 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.config.ConfigurationKey](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/config/ConfigurationKey.java) | org.gnit.lucenekmp.queryparser.flexible.core.config.ConfigurationKey | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.config.FieldConfig](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/config/FieldConfig.java) | org.gnit.lucenekmp.queryparser.flexible.core.config.FieldConfig | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.config.FieldConfigListener](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/config/FieldConfigListener.java) | org.gnit.lucenekmp.queryparser.flexible.core.config.FieldConfigListener | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.config.QueryConfigHandler](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/config/QueryConfigHandler.java) | org.gnit.lucenekmp.queryparser.flexible.core.config.QueryConfigHandler | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.messages.QueryParserMessages](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/messages/QueryParserMessages.java) | org.gnit.lucenekmp.queryparser.flexible.core.messages.QueryParserMessages | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.AndQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/AndQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.AndQueryNode | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.BooleanQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/BooleanQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.BooleanQueryNode | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.BoostQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/BoostQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.BoostQueryNode | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.DeletedQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/DeletedQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.DeletedQueryNode | Depth 5 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.FieldQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/FieldQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldQueryNode | Depth 2 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.FieldValuePairQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/FieldValuePairQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldValuePairQueryNode | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.FieldableNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/FieldableNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.FieldableNode | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.FuzzyQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/FuzzyQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.FuzzyQueryNode | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.GroupQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/GroupQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.GroupQueryNode | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.MatchAllDocsQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/MatchAllDocsQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.MatchAllDocsQueryNode | Depth 4 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.MatchNoDocsQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/MatchNoDocsQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.MatchNoDocsQueryNode | Depth 4 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.ModifierQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/ModifierQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.ModifierQueryNode | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.NoTokenFoundQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/NoTokenFoundQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.NoTokenFoundQueryNode | Depth 5 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.OrQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/OrQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.OrQueryNode | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.QueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/QueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNode | Depth 2 | [] | 11 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.QueryNodeImpl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/QueryNodeImpl.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.QueryNodeImpl | Depth 3 | [] | 12 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.QuotedFieldQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/QuotedFieldQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.QuotedFieldQueryNode | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.RangeQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/RangeQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.RangeQueryNode | Depth 5 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.SlopQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/SlopQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.SlopQueryNode | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.TestQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/core/nodes/TestQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.TestQueryNode | Depth 1 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.TextableQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/TextableQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.TextableQueryNode | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.TokenizedPhraseQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/TokenizedPhraseQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.TokenizedPhraseQueryNode | Depth 4 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.nodes.ValueQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/nodes/ValueQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.core.nodes.ValueQueryNode | Depth 4 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.parser.EscapeQuerySyntax](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/parser/EscapeQuerySyntax.java) | org.gnit.lucenekmp.queryparser.flexible.core.parser.EscapeQuerySyntax | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.parser.SyntaxParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/parser/SyntaxParser.java) | org.gnit.lucenekmp.queryparser.flexible.core.parser.SyntaxParser | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.processors.NoChildOptimizationQueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/processors/NoChildOptimizationQueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.core.processors.NoChildOptimizationQueryNodeProcessor | Depth 4 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.processors.QueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/processors/QueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessor | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.processors.QueryNodeProcessorImpl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/processors/QueryNodeProcessorImpl.java) | org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorImpl | Depth 3 | [] | 7 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.processors.QueryNodeProcessorPipeline](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/processors/QueryNodeProcessorPipeline.java) | org.gnit.lucenekmp.queryparser.flexible.core.processors.QueryNodeProcessorPipeline | Depth 2 | [] | 21 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.processors.RemoveDeletedQueryNodesProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/processors/RemoveDeletedQueryNodesProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.core.processors.RemoveDeletedQueryNodesProcessor | Depth 4 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.util.StringUtils](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/util/StringUtils.java) | org.gnit.lucenekmp.queryparser.flexible.core.util.StringUtils | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.util.TestUnescapedCharSequence](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/core/util/TestUnescapedCharSequence.java) | org.gnit.lucenekmp.queryparser.flexible.core.util.TestUnescapedCharSequence | Depth 1 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.core.util.UnescapedCharSequence](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/core/util/UnescapedCharSequence.java) | org.gnit.lucenekmp.queryparser.flexible.core.util.UnescapedCharSequence | Depth 2 | [] | 7 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.messages.Message](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/messages/Message.java) | org.gnit.lucenekmp.queryparser.flexible.messages.Message | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.messages.MessageImpl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/messages/MessageImpl.java) | org.gnit.lucenekmp.queryparser.flexible.messages.MessageImpl | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.messages.MessagesTestBundle](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/messages/MessagesTestBundle.java) | org.gnit.lucenekmp.queryparser.flexible.messages.MessagesTestBundle | Depth 2 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.messages.NLS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/messages/NLS.java) | org.gnit.lucenekmp.queryparser.flexible.messages.NLS | Depth 2 | [] | 9 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.messages.NLSException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/messages/NLSException.java) | org.gnit.lucenekmp.queryparser.flexible.messages.NLSException | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.messages.TestNLS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/messages/TestNLS.java) | org.gnit.lucenekmp.queryparser.flexible.messages.TestNLS | Depth 1 | [] | 6 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.precedence.PrecedenceQueryParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/precedence/PrecedenceQueryParser.java) | org.gnit.lucenekmp.queryparser.flexible.precedence.PrecedenceQueryParser | Depth 2 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.precedence.TestPrecedenceQueryParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/precedence/TestPrecedenceQueryParser.java) | org.gnit.lucenekmp.queryparser.flexible.precedence.TestPrecedenceQueryParser | Depth 1 | [] | 35 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.precedence.processors.BooleanModifiersQueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/precedence/processors/BooleanModifiersQueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.precedence.processors.BooleanModifiersQueryNodeProcessor | Depth 4 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.precedence.processors.PrecedenceQueryNodeProcessorPipeline](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/precedence/processors/PrecedenceQueryNodeProcessorPipeline.java) | org.gnit.lucenekmp.queryparser.flexible.precedence.processors.PrecedenceQueryNodeProcessorPipeline | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.spans.SpanOrQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/spans/SpanOrQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.spans.SpanOrQueryNodeBuilder | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.spans.SpanTermQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/spans/SpanTermQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.spans.SpanTermQueryNodeBuilder | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.spans.SpansQueryConfigHandler](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/spans/SpansQueryConfigHandler.java) | org.gnit.lucenekmp.queryparser.flexible.spans.SpansQueryConfigHandler | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.spans.SpansQueryTreeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/spans/SpansQueryTreeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.spans.SpansQueryTreeBuilder | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.spans.SpansValidatorQueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/spans/SpansValidatorQueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.spans.SpansValidatorQueryNodeProcessor | Depth 2 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.spans.TestSpanQueryParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/spans/TestSpanQueryParser.java) | org.gnit.lucenekmp.queryparser.flexible.spans.TestSpanQueryParser | Depth 1 | [] | 7 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.spans.TestSpanQueryParserSimpleSample](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/spans/TestSpanQueryParserSimpleSample.java) | org.gnit.lucenekmp.queryparser.flexible.spans.TestSpanQueryParserSimpleSample | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.spans.UniqueFieldAttribute](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/spans/UniqueFieldAttribute.java) | org.gnit.lucenekmp.queryparser.flexible.spans.UniqueFieldAttribute | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.spans.UniqueFieldAttributeImpl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/spans/UniqueFieldAttributeImpl.java) | org.gnit.lucenekmp.queryparser.flexible.spans.UniqueFieldAttributeImpl | Depth 1 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.spans.UniqueFieldQueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/spans/UniqueFieldQueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.spans.UniqueFieldQueryNodeProcessor | Depth 2 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.QueryParserUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/QueryParserUtil.java) | org.gnit.lucenekmp.queryparser.flexible.standard.QueryParserUtil | Depth 2 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.StandardQueryParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/StandardQueryParser.java) | org.gnit.lucenekmp.queryparser.flexible.standard.StandardQueryParser | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.TestMultiAnalyzerQPHelper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/standard/TestMultiAnalyzerQPHelper.java) | org.gnit.lucenekmp.queryparser.flexible.standard.TestMultiAnalyzerQPHelper | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.TestMultiFieldQPHelper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/standard/TestMultiFieldQPHelper.java) | org.gnit.lucenekmp.queryparser.flexible.standard.TestMultiFieldQPHelper | Depth 1 | [] | 12 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.TestPointQueryParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/standard/TestPointQueryParser.java) | org.gnit.lucenekmp.queryparser.flexible.standard.TestPointQueryParser | Depth 1 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.TestQPHelper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/standard/TestQPHelper.java) | org.gnit.lucenekmp.queryparser.flexible.standard.TestQPHelper | Depth 2 | [] | 61 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.TestStandardQP](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/standard/TestStandardQP.java) | org.gnit.lucenekmp.queryparser.flexible.standard.TestStandardQP | Depth 1 | [] | 17 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.TestStandardQPEnhancements](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/flexible/standard/TestStandardQPEnhancements.java) | org.gnit.lucenekmp.queryparser.flexible.standard.TestStandardQPEnhancements | Depth 1 | [] | 26 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.BooleanQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/BooleanQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.BooleanQueryNodeBuilder | Depth 5 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.BoostQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/BoostQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.BoostQueryNodeBuilder | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.DummyQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/DummyQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.DummyQueryNodeBuilder | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.FieldQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/FieldQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.FieldQueryNodeBuilder | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.FuzzyQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/FuzzyQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.FuzzyQueryNodeBuilder | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.GroupQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/GroupQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.GroupQueryNodeBuilder | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.IntervalQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/IntervalQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.IntervalQueryNodeBuilder | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.MatchAllDocsQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/MatchAllDocsQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.MatchAllDocsQueryNodeBuilder | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.MatchNoDocsQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/MatchNoDocsQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.MatchNoDocsQueryNodeBuilder | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.MinShouldMatchNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/MinShouldMatchNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.MinShouldMatchNodeBuilder | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.ModifierQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/ModifierQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.ModifierQueryNodeBuilder | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.MultiPhraseQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/MultiPhraseQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.MultiPhraseQueryNodeBuilder | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.PhraseQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/PhraseQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.PhraseQueryNodeBuilder | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.PointRangeQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/PointRangeQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.PointRangeQueryNodeBuilder | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.PrefixWildcardQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/PrefixWildcardQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.PrefixWildcardQueryNodeBuilder | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.RegexpQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/RegexpQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.RegexpQueryNodeBuilder | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.SlopQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/SlopQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.SlopQueryNodeBuilder | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.StandardQueryBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/StandardQueryBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.StandardQueryBuilder | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.StandardQueryTreeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/StandardQueryTreeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.StandardQueryTreeBuilder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.SynonymQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/SynonymQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.SynonymQueryNodeBuilder | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.TermRangeQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/TermRangeQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.TermRangeQueryNodeBuilder | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.builders.WildcardQueryNodeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/builders/WildcardQueryNodeBuilder.java) | org.gnit.lucenekmp.queryparser.flexible.standard.builders.WildcardQueryNodeBuilder | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.config.FieldBoostMapFCListener](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/config/FieldBoostMapFCListener.java) | org.gnit.lucenekmp.queryparser.flexible.standard.config.FieldBoostMapFCListener | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.config.FieldDateResolutionFCListener](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/config/FieldDateResolutionFCListener.java) | org.gnit.lucenekmp.queryparser.flexible.standard.config.FieldDateResolutionFCListener | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.config.FuzzyConfig](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/config/FuzzyConfig.java) | org.gnit.lucenekmp.queryparser.flexible.standard.config.FuzzyConfig | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.config.PointsConfig](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/config/PointsConfig.java) | org.gnit.lucenekmp.queryparser.flexible.standard.config.PointsConfig | Depth 2 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.config.PointsConfigListener](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/config/PointsConfigListener.java) | org.gnit.lucenekmp.queryparser.flexible.standard.config.PointsConfigListener | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.config.StandardQueryConfigHandler](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/config/StandardQueryConfigHandler.java) | org.gnit.lucenekmp.queryparser.flexible.standard.config.StandardQueryConfigHandler | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.AbstractRangeQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/AbstractRangeQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.AbstractRangeQueryNode | Depth 4 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.BooleanModifierNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/BooleanModifierNode.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.BooleanModifierNode | Depth 5 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.IntervalQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/IntervalQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.IntervalQueryNode | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.MinShouldMatchNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/MinShouldMatchNode.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.MinShouldMatchNode | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.MultiPhraseQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/MultiPhraseQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.MultiPhraseQueryNode | Depth 4 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.PointQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/PointQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.PointQueryNode | Depth 4 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.PointRangeQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/PointRangeQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.PointRangeQueryNode | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.PrefixWildcardQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/PrefixWildcardQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.PrefixWildcardQueryNode | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.RegexpQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/RegexpQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.RegexpQueryNode | Depth 3 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.SynonymQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/SynonymQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.SynonymQueryNode | Depth 4 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.TermRangeQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/TermRangeQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.TermRangeQueryNode | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.WildcardQueryNode](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/WildcardQueryNode.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.WildcardQueryNode | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.After](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/After.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.After | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.AnalyzedText](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/AnalyzedText.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.AnalyzedText | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.AtLeast](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/AtLeast.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.AtLeast | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.Before](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/Before.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.Before | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.ContainedBy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/ContainedBy.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.ContainedBy | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.Containing](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/Containing.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.Containing | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.Extend](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/Extend.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.Extend | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.FuzzyTerm](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/FuzzyTerm.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.FuzzyTerm | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.IntervalFunction](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/IntervalFunction.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.IntervalFunction | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.MaxGaps](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/MaxGaps.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.MaxGaps | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.MaxWidth](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/MaxWidth.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.MaxWidth | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.NonOverlapping](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/NonOverlapping.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.NonOverlapping | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.NotContainedBy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/NotContainedBy.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.NotContainedBy | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.NotContaining](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/NotContaining.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.NotContaining | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.NotWithin](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/NotWithin.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.NotWithin | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.Or](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/Or.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.Or | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.Ordered](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/Ordered.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.Ordered | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.Overlapping](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/Overlapping.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.Overlapping | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.Phrase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/Phrase.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.Phrase | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.Unordered](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/Unordered.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.Unordered | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.UnorderedNoOverlaps](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/UnorderedNoOverlaps.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.UnorderedNoOverlaps | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.Wildcard](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/Wildcard.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.Wildcard | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.nodes.intervalfn.Within](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/nodes/intervalfn/Within.java) | org.gnit.lucenekmp.queryparser.flexible.standard.nodes.intervalfn.Within | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.parser.EscapeQuerySyntaxImpl](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/parser/EscapeQuerySyntaxImpl.java) | org.gnit.lucenekmp.queryparser.flexible.standard.parser.EscapeQuerySyntaxImpl | Depth 3 | [] | 8 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.parser.ParseException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/parser/ParseException.java) | org.gnit.lucenekmp.queryparser.flexible.standard.parser.ParseException | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.parser.StandardSyntaxParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/parser/StandardSyntaxParser.java) | org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParser | Depth 3 | [] | 151 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.parser.StandardSyntaxParserConstants](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/parser/StandardSyntaxParserConstants.java) | org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserConstants | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.parser.StandardSyntaxParserTokenManager](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/parser/StandardSyntaxParserTokenManager.java) | org.gnit.lucenekmp.queryparser.flexible.standard.parser.StandardSyntaxParserTokenManager | Depth 3 | [] | 43 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.parser.Token](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/parser/Token.java) | org.gnit.lucenekmp.queryparser.flexible.standard.parser.Token | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.parser.TokenMgrError](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/parser/TokenMgrError.java) | org.gnit.lucenekmp.queryparser.flexible.standard.parser.TokenMgrError | Depth 4 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.processors.AllowLeadingWildcardProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/processors/AllowLeadingWildcardProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.standard.processors.AllowLeadingWildcardProcessor | Depth 4 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.processors.AnalyzerQueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/processors/AnalyzerQueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.standard.processors.AnalyzerQueryNodeProcessor | Depth 4 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.processors.BooleanQuery2ModifierNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/processors/BooleanQuery2ModifierNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.standard.processors.BooleanQuery2ModifierNodeProcessor | Depth 4 | [] | 9 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.processors.BooleanSingleChildOptimizationQueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/processors/BooleanSingleChildOptimizationQueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.standard.processors.BooleanSingleChildOptimizationQueryNodeProcessor | Depth 4 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.processors.BoostQueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/processors/BoostQueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.standard.processors.BoostQueryNodeProcessor | Depth 4 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.processors.DefaultPhraseSlopQueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/processors/DefaultPhraseSlopQueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.standard.processors.DefaultPhraseSlopQueryNodeProcessor | Depth 4 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.processors.FuzzyQueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/processors/FuzzyQueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.standard.processors.FuzzyQueryNodeProcessor | Depth 4 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.processors.IntervalQueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/processors/IntervalQueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.standard.processors.IntervalQueryNodeProcessor | Depth 4 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.processors.MatchAllDocsQueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/processors/MatchAllDocsQueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.standard.processors.MatchAllDocsQueryNodeProcessor | Depth 4 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.processors.MultiFieldQueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/processors/MultiFieldQueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.standard.processors.MultiFieldQueryNodeProcessor | Depth 4 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.processors.MultiTermRewriteMethodProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/processors/MultiTermRewriteMethodProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.standard.processors.MultiTermRewriteMethodProcessor | Depth 4 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.processors.OpenRangeQueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/processors/OpenRangeQueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.standard.processors.OpenRangeQueryNodeProcessor | Depth 4 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.processors.PhraseSlopQueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/processors/PhraseSlopQueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.standard.processors.PhraseSlopQueryNodeProcessor | Depth 4 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.processors.PointQueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/processors/PointQueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.standard.processors.PointQueryNodeProcessor | Depth 4 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.processors.PointRangeQueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/processors/PointRangeQueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.standard.processors.PointRangeQueryNodeProcessor | Depth 4 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.processors.RegexpQueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/processors/RegexpQueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.standard.processors.RegexpQueryNodeProcessor | Depth 4 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.processors.RemoveEmptyNonLeafQueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/processors/RemoveEmptyNonLeafQueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.standard.processors.RemoveEmptyNonLeafQueryNodeProcessor | Depth 4 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.processors.StandardQueryNodeProcessorPipeline](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/processors/StandardQueryNodeProcessorPipeline.java) | org.gnit.lucenekmp.queryparser.flexible.standard.processors.StandardQueryNodeProcessorPipeline | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.processors.TermRangeQueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/processors/TermRangeQueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.standard.processors.TermRangeQueryNodeProcessor | Depth 4 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.flexible.standard.processors.WildcardQueryNodeProcessor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/flexible/standard/processors/WildcardQueryNodeProcessor.java) | org.gnit.lucenekmp.queryparser.flexible.standard.processors.WildcardQueryNodeProcessor | Depth 2 | [] | 6 | 0 | 0% |
| [org.apache.lucene.queryparser.simple.SimpleQueryParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/simple/SimpleQueryParser.java) | org.gnit.lucenekmp.queryparser.simple.SimpleQueryParser | Depth 3 | [] | 14 | 0 | 0% |
| [org.apache.lucene.queryparser.simple.TestSimpleQueryParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/simple/TestSimpleQueryParser.java) | org.gnit.lucenekmp.queryparser.simple.TestSimpleQueryParser | Depth 1 | [] | 49 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.parser.ParseException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/parser/ParseException.java) | org.gnit.lucenekmp.queryparser.surround.parser.ParseException | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.parser.QueryParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/parser/QueryParser.java) | org.gnit.lucenekmp.queryparser.surround.parser.QueryParser | Depth 3 | [] | 43 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.parser.QueryParserConstants](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/parser/QueryParserConstants.java) | org.gnit.lucenekmp.queryparser.surround.parser.QueryParserConstants | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.parser.QueryParserTokenManager](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/parser/QueryParserTokenManager.java) | org.gnit.lucenekmp.queryparser.surround.parser.QueryParserTokenManager | Depth 3 | [] | 20 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.parser.Token](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/parser/Token.java) | org.gnit.lucenekmp.queryparser.surround.parser.Token | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.parser.TokenMgrError](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/parser/TokenMgrError.java) | org.gnit.lucenekmp.queryparser.surround.parser.TokenMgrError | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.AndQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/query/AndQuery.java) | org.gnit.lucenekmp.queryparser.surround.query.AndQuery | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.BasicQueryFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/query/BasicQueryFactory.java) | org.gnit.lucenekmp.queryparser.surround.query.BasicQueryFactory | Depth 2 | [] | 4 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.BooleanQueryTestFacade](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/surround/query/BooleanQueryTestFacade.java) | org.gnit.lucenekmp.queryparser.surround.query.BooleanQueryTestFacade | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.ComposedQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/query/ComposedQuery.java) | org.gnit.lucenekmp.queryparser.surround.query.ComposedQuery | Depth 4 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.DistanceQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/query/DistanceQuery.java) | org.gnit.lucenekmp.queryparser.surround.query.DistanceQuery | Depth 3 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.DistanceRewriteQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/query/DistanceRewriteQuery.java) | org.gnit.lucenekmp.queryparser.surround.query.DistanceRewriteQuery | Depth 4 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.DistanceSubQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/query/DistanceSubQuery.java) | org.gnit.lucenekmp.queryparser.surround.query.DistanceSubQuery | Depth 4 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.ExceptionQueryTestFacade](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/surround/query/ExceptionQueryTestFacade.java) | org.gnit.lucenekmp.queryparser.surround.query.ExceptionQueryTestFacade | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.FieldsQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/query/FieldsQuery.java) | org.gnit.lucenekmp.queryparser.surround.query.FieldsQuery | Depth 3 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.NotQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/query/NotQuery.java) | org.gnit.lucenekmp.queryparser.surround.query.NotQuery | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.OrQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/query/OrQuery.java) | org.gnit.lucenekmp.queryparser.surround.query.OrQuery | Depth 3 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.RewriteQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/query/RewriteQuery.java) | org.gnit.lucenekmp.queryparser.surround.query.RewriteQuery | Depth 5 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.SimpleTerm](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/query/SimpleTerm.java) | org.gnit.lucenekmp.queryparser.surround.query.SimpleTerm | Depth 5 | [] | 6 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.SimpleTermRewriteQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/query/SimpleTermRewriteQuery.java) | org.gnit.lucenekmp.queryparser.surround.query.SimpleTermRewriteQuery | Depth 6 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.SingleFieldTestDb](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/surround/query/SingleFieldTestDb.java) | org.gnit.lucenekmp.queryparser.surround.query.SingleFieldTestDb | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.SpanNearClauseFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/query/SpanNearClauseFactory.java) | org.gnit.lucenekmp.queryparser.surround.query.SpanNearClauseFactory | Depth 4 | [] | 5 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.SrndBooleanQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/query/SrndBooleanQuery.java) | org.gnit.lucenekmp.queryparser.surround.query.SrndBooleanQuery | Depth 4 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.SrndPrefixQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/query/SrndPrefixQuery.java) | org.gnit.lucenekmp.queryparser.surround.query.SrndPrefixQuery | Depth 3 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.SrndQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/query/SrndQuery.java) | org.gnit.lucenekmp.queryparser.surround.query.SrndQuery | Depth 2 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.SrndTermQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/query/SrndTermQuery.java) | org.gnit.lucenekmp.queryparser.surround.query.SrndTermQuery | Depth 3 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.SrndTruncQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/query/SrndTruncQuery.java) | org.gnit.lucenekmp.queryparser.surround.query.SrndTruncQuery | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.Test01Exceptions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/surround/query/Test01Exceptions.java) | org.gnit.lucenekmp.queryparser.surround.query.Test01Exceptions | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.Test02Boolean](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/surround/query/Test02Boolean.java) | org.gnit.lucenekmp.queryparser.surround.query.Test02Boolean | Depth 1 | [] | 26 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.Test03Distance](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/surround/query/Test03Distance.java) | org.gnit.lucenekmp.queryparser.surround.query.Test03Distance | Depth 1 | [] | 48 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.TestSrndQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/surround/query/TestSrndQuery.java) | org.gnit.lucenekmp.queryparser.surround.query.TestSrndQuery | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.surround.query.TooManyBasicQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/surround/query/TooManyBasicQueries.java) | org.gnit.lucenekmp.queryparser.surround.query.TooManyBasicQueries | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.util.QueryParserTestBase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/util/QueryParserTestBase.java) | org.gnit.lucenekmp.queryparser.util.QueryParserTestBase | Depth 1 | [] | 74 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.CoreParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/CoreParser.java) | org.gnit.lucenekmp.queryparser.xml.CoreParser | Depth 2 | [] | 8 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.CoreParserTestIndexData](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/xml/CoreParserTestIndexData.java) | org.gnit.lucenekmp.queryparser.xml.CoreParserTestIndexData | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.CorePlusExtensionsParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/CorePlusExtensionsParser.java) | org.gnit.lucenekmp.queryparser.xml.CorePlusExtensionsParser | Depth 2 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.CorePlusQueriesParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/CorePlusQueriesParser.java) | org.gnit.lucenekmp.queryparser.xml.CorePlusQueriesParser | Depth 2 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.DOMUtils](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/DOMUtils.java) | org.gnit.lucenekmp.queryparser.xml.DOMUtils | Depth 4 | [] | 17 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.ParserException](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/ParserException.java) | org.gnit.lucenekmp.queryparser.xml.ParserException | Depth 2 | [] | 0 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.QueryBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/QueryBuilder.java) | org.gnit.lucenekmp.queryparser.xml.QueryBuilder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.QueryBuilderFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/QueryBuilderFactory.java) | org.gnit.lucenekmp.queryparser.xml.QueryBuilderFactory | Depth 3 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.TestCoreParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/xml/TestCoreParser.java) | org.gnit.lucenekmp.queryparser.xml.TestCoreParser | Depth 1 | [] | 34 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.TestCorePlusExtensionsParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/xml/TestCorePlusExtensionsParser.java) | org.gnit.lucenekmp.queryparser.xml.TestCorePlusExtensionsParser | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.TestCorePlusQueriesParser](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/test/org/apache/lucene/queryparser/xml/TestCorePlusQueriesParser.java) | org.gnit.lucenekmp.queryparser.xml.TestCorePlusQueriesParser | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.BooleanQueryBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/BooleanQueryBuilder.java) | org.gnit.lucenekmp.queryparser.xml.builders.BooleanQueryBuilder | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.BoostingTermBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/BoostingTermBuilder.java) | org.gnit.lucenekmp.queryparser.xml.builders.BoostingTermBuilder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.ConstantScoreQueryBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/ConstantScoreQueryBuilder.java) | org.gnit.lucenekmp.queryparser.xml.builders.ConstantScoreQueryBuilder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.DisjunctionMaxQueryBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/DisjunctionMaxQueryBuilder.java) | org.gnit.lucenekmp.queryparser.xml.builders.DisjunctionMaxQueryBuilder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.FuzzyLikeThisQueryBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/FuzzyLikeThisQueryBuilder.java) | org.gnit.lucenekmp.queryparser.xml.builders.FuzzyLikeThisQueryBuilder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.LikeThisQueryBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/LikeThisQueryBuilder.java) | org.gnit.lucenekmp.queryparser.xml.builders.LikeThisQueryBuilder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.MatchAllDocsQueryBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/MatchAllDocsQueryBuilder.java) | org.gnit.lucenekmp.queryparser.xml.builders.MatchAllDocsQueryBuilder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.PointRangeQueryBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/PointRangeQueryBuilder.java) | org.gnit.lucenekmp.queryparser.xml.builders.PointRangeQueryBuilder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.RangeQueryBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/RangeQueryBuilder.java) | org.gnit.lucenekmp.queryparser.xml.builders.RangeQueryBuilder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.SpanBuilderBase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/SpanBuilderBase.java) | org.gnit.lucenekmp.queryparser.xml.builders.SpanBuilderBase | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.SpanFirstBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/SpanFirstBuilder.java) | org.gnit.lucenekmp.queryparser.xml.builders.SpanFirstBuilder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.SpanNearBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/SpanNearBuilder.java) | org.gnit.lucenekmp.queryparser.xml.builders.SpanNearBuilder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.SpanNotBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/SpanNotBuilder.java) | org.gnit.lucenekmp.queryparser.xml.builders.SpanNotBuilder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.SpanOrBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/SpanOrBuilder.java) | org.gnit.lucenekmp.queryparser.xml.builders.SpanOrBuilder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.SpanOrTermsBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/SpanOrTermsBuilder.java) | org.gnit.lucenekmp.queryparser.xml.builders.SpanOrTermsBuilder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.SpanPositionRangeBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/SpanPositionRangeBuilder.java) | org.gnit.lucenekmp.queryparser.xml.builders.SpanPositionRangeBuilder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.SpanQueryBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/SpanQueryBuilder.java) | org.gnit.lucenekmp.queryparser.xml.builders.SpanQueryBuilder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.SpanQueryBuilderFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/SpanQueryBuilderFactory.java) | org.gnit.lucenekmp.queryparser.xml.builders.SpanQueryBuilderFactory | Depth 3 | [] | 3 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.SpanTermBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/SpanTermBuilder.java) | org.gnit.lucenekmp.queryparser.xml.builders.SpanTermBuilder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.TermQueryBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/TermQueryBuilder.java) | org.gnit.lucenekmp.queryparser.xml.builders.TermQueryBuilder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.TermsQueryBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/TermsQueryBuilder.java) | org.gnit.lucenekmp.queryparser.xml.builders.TermsQueryBuilder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.queryparser.xml.builders.UserInputQueryBuilder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/queryparser/src/java/org/apache/lucene/queryparser/xml/builders/UserInputQueryBuilder.java) | org.gnit.lucenekmp.queryparser.xml.builders.UserInputQueryBuilder | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.AbstractKnnVectorQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/AbstractKnnVectorQuery.java) | org.gnit.lucenekmp.search.AbstractKnnVectorQuery | Depth 3 | [x] | 12 | 12 | 64% |
| [org.apache.lucene.search.CollectionStatistics](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/CollectionStatistics.java) | org.gnit.lucenekmp.search.CollectionStatistics | Depth 2 | [x] | 4 | 0 | 23% |
| [org.apache.lucene.search.DisjunctionMaxScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/DisjunctionMaxScorer.java) | org.gnit.lucenekmp.search.DisjunctionMaxScorer | Depth 4 | [x] | 3 | 3 | 83% |
| [org.apache.lucene.search.FieldValueHitQueue](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FieldValueHitQueue.java) | org.gnit.lucenekmp.search.FieldValueHitQueue | Depth 3 | [x] | 4 | 4 | 82% |
| [org.apache.lucene.search.FilterMatchesIterator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/FilterMatchesIterator.java) | org.gnit.lucenekmp.search.FilterMatchesIterator | Depth 4 | [] | 4 | 0 | 0% |
| [org.apache.lucene.search.IndexSortSortedNumericDocValuesRangeQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/IndexSortSortedNumericDocValuesRangeQuery.java) | org.gnit.lucenekmp.search.IndexSortSortedNumericDocValuesRangeQuery | Depth 3 | [x] | 13 | 14 | 78% |
| [org.apache.lucene.search.IntArrayDocIdSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/IntArrayDocIdSet.java) | org.gnit.lucenekmp.search.IntArrayDocIdSet | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.search.JustCompileSearch](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/JustCompileSearch.java) | org.gnit.lucenekmp.search.JustCompileSearch | Depth 2 | [] | 0 | 0 | 0% |
| [org.apache.lucene.search.KnnByteVectorQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/KnnByteVectorQuery.java) | org.gnit.lucenekmp.search.KnnByteVectorQuery | Depth 2 | [x] | 2 | 2 | 37% |
| [org.apache.lucene.search.KnnFloatVectorQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/KnnFloatVectorQuery.java) | org.gnit.lucenekmp.search.KnnFloatVectorQuery | Depth 2 | [x] | 2 | 2 | 37% |
| [org.apache.lucene.search.LRUQueryCache](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/LRUQueryCache.java) | org.gnit.lucenekmp.search.LRUQueryCache | Depth 3 | [x] | 23 | 25 | 81% |
| [org.apache.lucene.search.Multiset](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/Multiset.java) | org.gnit.lucenekmp.search.Multiset | Depth 3 | [x] | 4 | 5 | 82% |
| [org.apache.lucene.search.ScorerUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/ScorerUtil.java) | org.gnit.lucenekmp.search.ScorerUtil | Depth 3 | [x] | 4 | 4 | 75% |
| [org.apache.lucene.search.SeededKnnVectorQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/SeededKnnVectorQuery.java) | org.gnit.lucenekmp.search.SeededKnnVectorQuery | Depth 3 | [x] | 10 | 10 | 37% |
| [org.apache.lucene.search.SloppyPhraseMatcher](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/SloppyPhraseMatcher.java) | org.gnit.lucenekmp.search.SloppyPhraseMatcher | Depth 5 | [x] | 29 | 29 | 93% |
| [org.apache.lucene.search.SortedNumericSortField](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/SortedNumericSortField.java) | org.gnit.lucenekmp.search.SortedNumericSortField | Depth 3 | [x] | 4 | 8 | 91% |
| [org.apache.lucene.search.TaskExecutor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TaskExecutor.java) | org.gnit.lucenekmp.search.TaskExecutor | Depth 3 | [x] | 4 | 6 | 76% |
| [org.apache.lucene.search.TermRangeQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TermRangeQuery.java) | org.gnit.lucenekmp.search.TermRangeQuery | Depth 2 | [x] | 5 | 4 | 85% |
| [org.apache.lucene.search.TermScorer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TermScorer.java) | org.gnit.lucenekmp.search.TermScorer | Depth 2 | [x] | 4 | 4 | 90% |
| [org.apache.lucene.search.TermStatistics](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/TermStatistics.java) | org.gnit.lucenekmp.search.TermStatistics | Depth 2 | [x] | 2 | 0 | 18% |
| [org.apache.lucene.search.TestCollectorManager](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestCollectorManager.java) | org.gnit.lucenekmp.search.TestCollectorManager | Depth 1 | [x] | 5 | 8 | 80% |
| [org.apache.lucene.search.TestCustomSearcherSort](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestCustomSearcherSort.java) | org.gnit.lucenekmp.search.TestCustomSearcherSort | Depth 1 | [x] | 7 | 7 | 71% |
| [org.apache.lucene.search.TestDateSort](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestDateSort.java) | org.gnit.lucenekmp.search.TestDateSort | Depth 1 | [x] | 4 | 4 | 50% |
| [org.apache.lucene.search.TestDisjunctionMaxQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestDisjunctionMaxQuery.java) | org.gnit.lucenekmp.search.TestDisjunctionMaxQuery | Depth 1 | [x] | 25 | 26 | 88% |
| [org.apache.lucene.search.TestDoubleRangeFieldQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestDoubleRangeFieldQueries.java) | org.gnit.lucenekmp.search.TestDoubleRangeFieldQueries | Depth 2 | [x] | 8 | 14 | 87% |
| [org.apache.lucene.search.TestEarlyTermination](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestEarlyTermination.java) | org.gnit.lucenekmp.search.TestEarlyTermination | Depth 1 | [x] | 3 | 3 | 33% |
| [org.apache.lucene.search.TestElevationComparator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestElevationComparator.java) | org.gnit.lucenekmp.search.TestElevationComparator | Depth 1 | [x] | 7 | 7 | 71% |
| [org.apache.lucene.search.TestFilterWeight](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestFilterWeight.java) | org.gnit.lucenekmp.search.TestFilterWeight | Depth 1 | [x] | 2 | 2 | 0% |
| [org.apache.lucene.search.TestFloatRangeFieldQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestFloatRangeFieldQueries.java) | org.gnit.lucenekmp.search.TestFloatRangeFieldQueries | Depth 2 | [x] | 8 | 14 | 87% |
| [org.apache.lucene.search.TestIndriAndQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestIndriAndQuery.java) | org.gnit.lucenekmp.search.TestIndriAndQuery | Depth 1 | [x] | 7 | 7 | 71% |
| [org.apache.lucene.search.TestMultiCollectorManager](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestMultiCollectorManager.java) | org.gnit.lucenekmp.search.TestMultiCollectorManager | Depth 1 | [x] | 9 | 12 | 88% |
| [org.apache.lucene.search.TestMultiPhraseQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestMultiPhraseQuery.java) | org.gnit.lucenekmp.search.TestMultiPhraseQuery | Depth 1 | [x] | 21 | 22 | 95% |
| [org.apache.lucene.search.TestPhraseQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestPhraseQuery.java) | org.gnit.lucenekmp.search.TestPhraseQuery | Depth 1 | [x] | 29 | 35 | 93% |
| [org.apache.lucene.search.TestPointQueries](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestPointQueries.java) | org.gnit.lucenekmp.search.TestPointQueries | Depth 1 | [x] | 64 | 71 | 98% |
| [org.apache.lucene.search.TestRegexpRandom2](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestRegexpRandom2.java) | org.gnit.lucenekmp.search.TestRegexpRandom2 | Depth 1 | [x] | 4 | 4 | 50% |
| [org.apache.lucene.search.TestSimpleExplanationsWithFillerDocs](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSimpleExplanationsWithFillerDocs.java) | org.gnit.lucenekmp.search.TestSimpleExplanationsWithFillerDocs | Depth 1 | [x] | 5 | 73 | 80% |
| [org.apache.lucene.search.TestSort](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSort.java) | org.gnit.lucenekmp.search.TestSort | Depth 1 | [x] | 36 | 46 | 97% |
| [org.apache.lucene.search.TestSortOptimization](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestSortOptimization.java) | org.gnit.lucenekmp.search.TestSortOptimization | Depth 1 | [x] | 23 | 26 | 95% |
| [org.apache.lucene.search.TestTaskExecutor](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/search/TestTaskExecutor.java) | org.gnit.lucenekmp.search.TestTaskExecutor | Depth 1 | [x] | 14 | 33 | 85% |
| [org.apache.lucene.search.WildcardQuery](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/search/WildcardQuery.java) | org.gnit.lucenekmp.search.WildcardQuery | Depth 2 | [x] | 1 | 1 | 66% |
| [org.apache.lucene.store.ByteBuffersDataOutput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/ByteBuffersDataOutput.java) | org.gnit.lucenekmp.store.ByteBuffersDataOutput | Depth 2 | [x] | 25 | 29 | 92% |
| [org.apache.lucene.store.ByteBuffersIndexInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/ByteBuffersIndexInput.java) | org.gnit.lucenekmp.store.ByteBuffersIndexInput | Depth 2 | [x] | 27 | 22 | 78% |
| [org.apache.lucene.store.FlushInfo](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/FlushInfo.java) | org.gnit.lucenekmp.store.FlushInfo | Depth 2 | [x] | 2 | 0 | 23% |
| [org.apache.lucene.store.IOContext](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/IOContext.java) | org.gnit.lucenekmp.store.IOContext | Depth 3 | [x] | 4 | 1 | 30% |
| [org.apache.lucene.store.LockStressTest](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/LockStressTest.java) | org.gnit.lucenekmp.store.LockStressTest | Depth 2 | [] | 3 | 0 | 0% |
| [org.apache.lucene.store.MergeInfo](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/MergeInfo.java) | org.gnit.lucenekmp.store.MergeInfo | Depth 2 | [x] | 3 | 0 | 28% |
| [org.apache.lucene.store.NRTCachingDirectory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/NRTCachingDirectory.java) | org.gnit.lucenekmp.store.NRTCachingDirectory | Depth 2 | [x] | 15 | 18 | 93% |
| [org.apache.lucene.store.RandomAccessInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/RandomAccessInput.java) | org.gnit.lucenekmp.store.RandomAccessInput | Depth 2 | [x] | 6 | 6 | 91% |
| [org.apache.lucene.store.TestBufferedIndexInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/store/TestBufferedIndexInput.java) | org.gnit.lucenekmp.store.TestBufferedIndexInput | Depth 1 | [x] | 13 | 15 | 92% |
| [org.apache.lucene.store.TestByteBuffersDirectory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/store/TestByteBuffersDirectory.java) | org.gnit.lucenekmp.store.TestByteBuffersDirectory | Depth 1 | [x] | 3 | 69 | 66% |
| [org.apache.lucene.store.TestInputStreamDataInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/store/TestInputStreamDataInput.java) | org.gnit.lucenekmp.store.TestInputStreamDataInput | Depth 1 | [x] | 8 | 7 | 50% |
| [org.apache.lucene.store.TestMMapDirectory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/store/TestMMapDirectory.java) | org.gnit.lucenekmp.store.TestMMapDirectory | Depth 1 | [x] | 14 | 83 | 92% |
| [org.apache.lucene.store.TestStressLockFactories](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/store/TestStressLockFactories.java) | org.gnit.lucenekmp.store.TestStressLockFactories | Depth 1 | [x] | 7 | 2 | 28% |
| [org.apache.lucene.store.VerifyingLockFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/store/VerifyingLockFactory.java) | org.gnit.lucenekmp.store.VerifyingLockFactory | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.analysis.BaseTokenStreamTestCase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/analysis/BaseTokenStreamTestCase.java) | org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase | Depth 3 | [x] | 58 | 50 | 86% |
| [org.apache.lucene.tests.analysis.LookaheadTokenFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/analysis/LookaheadTokenFilter.java) | org.gnit.lucenekmp.tests.analysis.LookaheadTokenFilter | Depth 3 | [x] | 7 | 7 | 85% |
| [org.apache.lucene.tests.analysis.MockGraphTokenFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/analysis/MockGraphTokenFilter.java) | org.gnit.lucenekmp.tests.analysis.MockGraphTokenFilter | Depth 2 | [x] | 5 | 4 | 60% |
| [org.apache.lucene.tests.analysis.MockRandomLookaheadTokenFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/analysis/MockRandomLookaheadTokenFilter.java) | org.gnit.lucenekmp.tests.analysis.MockRandomLookaheadTokenFilter | Depth 2 | [] | 4 | 0 | 0% |
| [org.apache.lucene.tests.analysis.MockTokenizer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/analysis/MockTokenizer.java) | org.gnit.lucenekmp.tests.analysis.MockTokenizer | Depth 3 | [x] | 11 | 10 | 82% |
| [org.apache.lucene.tests.analysis.SimplePayloadFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/analysis/SimplePayloadFilter.java) | org.gnit.lucenekmp.tests.analysis.SimplePayloadFilter | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.analysis.TestLookaheadTokenFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/analysis/TestLookaheadTokenFilter.java) | org.gnit.lucenekmp.tests.analysis.TestLookaheadTokenFilter | Depth 1 | [] | 3 | 0 | 0% |
| [org.apache.lucene.tests.analysis.TestMockAnalyzer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/analysis/TestMockAnalyzer.java) | org.gnit.lucenekmp.tests.analysis.TestMockAnalyzer | Depth 1 | [] | 17 | 0 | 0% |
| [org.apache.lucene.tests.analysis.TestMockCharFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/analysis/TestMockCharFilter.java) | org.gnit.lucenekmp.tests.analysis.TestMockCharFilter | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.analysis.TestMockSynonymFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/analysis/TestMockSynonymFilter.java) | org.gnit.lucenekmp.tests.analysis.TestMockSynonymFilter | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.analysis.TestToken](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/analysis/TestToken.java) | org.gnit.lucenekmp.tests.analysis.TestToken | Depth 1 | [] | 7 | 0 | 0% |
| [org.apache.lucene.tests.analysis.Token](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/analysis/Token.java) | org.gnit.lucenekmp.tests.analysis.Token | Depth 2 | [x] | 5 | 6 | 95% |
| [org.apache.lucene.tests.codecs.asserting.AssertingCodec](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/asserting/AssertingCodec.java) | org.gnit.lucenekmp.tests.codecs.asserting.AssertingCodec | Depth 3 | [x] | 12 | 17 | 91% |
| [org.apache.lucene.tests.codecs.asserting.TestAssertingDocValuesFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/codecs/asserting/TestAssertingDocValuesFormat.java) | org.gnit.lucenekmp.tests.codecs.asserting.TestAssertingDocValuesFormat | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.tests.codecs.asserting.TestAssertingNormsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/codecs/asserting/TestAssertingNormsFormat.java) | org.gnit.lucenekmp.tests.codecs.asserting.TestAssertingNormsFormat | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.tests.codecs.asserting.TestAssertingPointsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/codecs/asserting/TestAssertingPointsFormat.java) | org.gnit.lucenekmp.tests.codecs.asserting.TestAssertingPointsFormat | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.tests.codecs.asserting.TestAssertingPostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/codecs/asserting/TestAssertingPostingsFormat.java) | org.gnit.lucenekmp.tests.codecs.asserting.TestAssertingPostingsFormat | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.tests.codecs.asserting.TestAssertingStoredFieldsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/codecs/asserting/TestAssertingStoredFieldsFormat.java) | org.gnit.lucenekmp.tests.codecs.asserting.TestAssertingStoredFieldsFormat | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.tests.codecs.asserting.TestAssertingTermVectorsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/codecs/asserting/TestAssertingTermVectorsFormat.java) | org.gnit.lucenekmp.tests.codecs.asserting.TestAssertingTermVectorsFormat | Depth 1 | [] | 0 | 0 | 0% |
| [org.apache.lucene.tests.codecs.blockterms.LuceneVarGapDocFreqInterval](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/blockterms/LuceneVarGapDocFreqInterval.java) | org.gnit.lucenekmp.tests.codecs.blockterms.LuceneVarGapDocFreqInterval | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.codecs.blockterms.LuceneVarGapFixedInterval](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/blockterms/LuceneVarGapFixedInterval.java) | org.gnit.lucenekmp.tests.codecs.blockterms.LuceneVarGapFixedInterval | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.codecs.cheapbastard.CheapBastardCodec](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/cheapbastard/CheapBastardCodec.java) | org.gnit.lucenekmp.tests.codecs.cheapbastard.CheapBastardCodec | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.codecs.mockrandom.MockRandomPostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/mockrandom/MockRandomPostingsFormat.java) | org.gnit.lucenekmp.tests.codecs.mockrandom.MockRandomPostingsFormat | Depth 4 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.codecs.uniformsplit.Rot13CypherTestUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/uniformsplit/Rot13CypherTestUtil.java) | org.gnit.lucenekmp.tests.codecs.uniformsplit.Rot13CypherTestUtil | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.codecs.uniformsplit.UniformSplitRot13PostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/uniformsplit/UniformSplitRot13PostingsFormat.java) | org.gnit.lucenekmp.tests.codecs.uniformsplit.UniformSplitRot13PostingsFormat | Depth 3 | [] | 8 | 0 | 0% |
| [org.apache.lucene.tests.codecs.uniformsplit.sharedterms.STUniformSplitRot13PostingsFormat](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/uniformsplit/sharedterms/STUniformSplitRot13PostingsFormat.java) | org.gnit.lucenekmp.tests.codecs.uniformsplit.sharedterms.STUniformSplitRot13PostingsFormat | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.codecs.vector.ConfigurableMCodec](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/codecs/vector/ConfigurableMCodec.java) | org.gnit.lucenekmp.tests.codecs.vector.ConfigurableMCodec | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.geo.EarthDebugger](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/geo/EarthDebugger.java) | org.gnit.lucenekmp.tests.geo.EarthDebugger | Depth 2 | [] | 13 | 0 | 0% |
| [org.apache.lucene.tests.index.AssertingLeafReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/AssertingLeafReader.java) | org.gnit.lucenekmp.tests.index.AssertingLeafReader | Depth 3 | [x] | 12 | 14 | 92% |
| [org.apache.lucene.tests.index.LegacyBaseDocValuesFormatTestCase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/LegacyBaseDocValuesFormatTestCase.java) | org.gnit.lucenekmp.tests.index.LegacyBaseDocValuesFormatTestCase | Depth 4 | [x] | 123 | 175 | 99% |
| [org.apache.lucene.tests.index.RandomCodec](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/RandomCodec.java) | org.gnit.lucenekmp.tests.index.RandomCodec | Depth 3 | [] | 8 | 0 | 0% |
| [org.apache.lucene.tests.index.RandomIndexWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/RandomIndexWriter.java) | org.gnit.lucenekmp.tests.index.RandomIndexWriter | Depth 3 | [x] | 25 | 26 | 97% |
| [org.apache.lucene.tests.index.RandomPostingsTester](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/RandomPostingsTester.java) | org.gnit.lucenekmp.tests.index.RandomPostingsTester | Depth 3 | [x] | 7 | 12 | 85% |
| [org.apache.lucene.tests.index.TestAssertingLeafReader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/index/TestAssertingLeafReader.java) | org.gnit.lucenekmp.tests.index.TestAssertingLeafReader | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.index.TestForceMergePolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/index/TestForceMergePolicy.java) | org.gnit.lucenekmp.tests.index.TestForceMergePolicy | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.index.TestMockRandomMergePolicy](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/index/TestMockRandomMergePolicy.java) | org.gnit.lucenekmp.tests.index.TestMockRandomMergePolicy | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.index.ThreadedIndexingAndSearchingTestCase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/index/ThreadedIndexingAndSearchingTestCase.java) | org.gnit.lucenekmp.tests.index.ThreadedIndexingAndSearchingTestCase | Depth 3 | [x] | 16 | 18 | 94% |
| [org.apache.lucene.tests.mockfile.DisableFsyncFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/DisableFsyncFS.java) | org.gnit.lucenekmp.tests.mockfile.DisableFsyncFS | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.ExtrasFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/ExtrasFS.java) | org.gnit.lucenekmp.tests.mockfile.ExtrasFS | Depth 2 | [x] | 2 | 2 | 50% |
| [org.apache.lucene.tests.mockfile.FilterAsynchronousFileChannel](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/FilterAsynchronousFileChannel.java) | org.gnit.lucenekmp.tests.mockfile.FilterAsynchronousFileChannel | Depth 4 | [] | 10 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.FilterFileChannel](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/FilterFileChannel.java) | org.gnit.lucenekmp.tests.mockfile.FilterFileChannel | Depth 3 | [] | 15 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.FilterFileStore](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/FilterFileStore.java) | org.gnit.lucenekmp.tests.mockfile.FilterFileStore | Depth 5 | [] | 4 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.FilterFileSystem](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/FilterFileSystem.java) | org.gnit.lucenekmp.tests.mockfile.FilterFileSystem | Depth 4 | [x] | 5 | 15 | 22% |
| [org.apache.lucene.tests.mockfile.FilterFileSystemProvider](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/FilterFileSystemProvider.java) | org.gnit.lucenekmp.tests.mockfile.FilterFileSystemProvider | Depth 3 | [x] | 28 | 17 | 5% |
| [org.apache.lucene.tests.mockfile.FilterPath](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/FilterPath.java) | org.gnit.lucenekmp.tests.mockfile.FilterPath | Depth 3 | [x] | 21 | 21 | 79% |
| [org.apache.lucene.tests.mockfile.HandleLimitFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/HandleLimitFS.java) | org.gnit.lucenekmp.tests.mockfile.HandleLimitFS | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.HandleTrackingFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/HandleTrackingFS.java) | org.gnit.lucenekmp.tests.mockfile.HandleTrackingFS | Depth 3 | [x] | 9 | 8 | 33% |
| [org.apache.lucene.tests.mockfile.LeakFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/LeakFS.java) | org.gnit.lucenekmp.tests.mockfile.LeakFS | Depth 2 | [] | 3 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.MockFileSystemTestCase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/MockFileSystemTestCase.java) | org.gnit.lucenekmp.tests.mockfile.MockFileSystemTestCase | Depth 2 | [] | 8 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.ShuffleFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/ShuffleFS.java) | org.gnit.lucenekmp.tests.mockfile.ShuffleFS | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.TestDisableFsyncFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/mockfile/TestDisableFsyncFS.java) | org.gnit.lucenekmp.tests.mockfile.TestDisableFsyncFS | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.TestExtrasFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/mockfile/TestExtrasFS.java) | org.gnit.lucenekmp.tests.mockfile.TestExtrasFS | Depth 1 | [] | 5 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.TestHandleLimitFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/mockfile/TestHandleLimitFS.java) | org.gnit.lucenekmp.tests.mockfile.TestHandleLimitFS | Depth 1 | [] | 3 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.TestHandleTrackingFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/mockfile/TestHandleTrackingFS.java) | org.gnit.lucenekmp.tests.mockfile.TestHandleTrackingFS | Depth 2 | [] | 3 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.TestLeakFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/mockfile/TestLeakFS.java) | org.gnit.lucenekmp.tests.mockfile.TestLeakFS | Depth 1 | [] | 6 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.TestShuffleFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/mockfile/TestShuffleFS.java) | org.gnit.lucenekmp.tests.mockfile.TestShuffleFS | Depth 1 | [] | 6 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.TestVerboseFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/mockfile/TestVerboseFS.java) | org.gnit.lucenekmp.tests.mockfile.TestVerboseFS | Depth 1 | [] | 12 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.TestVirusCheckingFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/mockfile/TestVirusCheckingFS.java) | org.gnit.lucenekmp.tests.mockfile.TestVirusCheckingFS | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.TestWindowsFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/mockfile/TestWindowsFS.java) | org.gnit.lucenekmp.tests.mockfile.TestWindowsFS | Depth 2 | [] | 8 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.VerboseFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/VerboseFS.java) | org.gnit.lucenekmp.tests.mockfile.VerboseFS | Depth 2 | [] | 14 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.VirusCheckingFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/VirusCheckingFS.java) | org.gnit.lucenekmp.tests.mockfile.VirusCheckingFS | Depth 2 | [] | 3 | 0 | 0% |
| [org.apache.lucene.tests.mockfile.WindowsFS](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/WindowsFS.java) | org.gnit.lucenekmp.tests.mockfile.WindowsFS | Depth 2 | [x] | 9 | 7 | 33% |
| [org.apache.lucene.tests.mockfile.WindowsPath](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/mockfile/WindowsPath.java) | org.gnit.lucenekmp.tests.mockfile.WindowsPath | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.search.CheckHits](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/search/CheckHits.java) | org.gnit.lucenekmp.tests.search.CheckHits | Depth 3 | [x] | 15 | 14 | 93% |
| [org.apache.lucene.tests.search.MatchesTestBase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/search/MatchesTestBase.java) | org.gnit.lucenekmp.tests.search.MatchesTestBase | Depth 2 | [x] | 10 | 10 | 81% |
| [org.apache.lucene.tests.search.QueryUtils](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/search/QueryUtils.java) | org.gnit.lucenekmp.tests.search.QueryUtils | Depth 3 | [x] | 13 | 12 | 92% |
| [org.apache.lucene.tests.search.TestBaseExplanationTestCase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/search/TestBaseExplanationTestCase.java) | org.gnit.lucenekmp.tests.search.TestBaseExplanationTestCase | Depth 1 | [] | 4 | 0 | 0% |
| [org.apache.lucene.tests.search.TestPerThreadPKLookup](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/search/TestPerThreadPKLookup.java) | org.gnit.lucenekmp.tests.search.TestPerThreadPKLookup | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.search.similarities.BaseSimilarityTestCase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/search/similarities/BaseSimilarityTestCase.java) | org.gnit.lucenekmp.tests.search.similarities.BaseSimilarityTestCase | Depth 2 | [x] | 7 | 6 | 71% |
| [org.apache.lucene.tests.store.BaseDirectoryWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/store/BaseDirectoryWrapper.java) | org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper | Depth 2 | [x] | 1 | 1 | 85% |
| [org.apache.lucene.tests.store.CorruptingIndexOutput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/store/CorruptingIndexOutput.java) | org.gnit.lucenekmp.tests.store.CorruptingIndexOutput | Depth 2 | [x] | 3 | 3 | 85% |
| [org.apache.lucene.tests.store.MockDirectoryWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/store/MockDirectoryWrapper.java) | org.gnit.lucenekmp.tests.store.MockDirectoryWrapper | Depth 2 | [x] | 33 | 51 | 95% |
| [org.apache.lucene.tests.store.TestMockDirectoryWrapper](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/store/TestMockDirectoryWrapper.java) | org.gnit.lucenekmp.tests.store.TestMockDirectoryWrapper | Depth 1 | [] | 10 | 0 | 0% |
| [org.apache.lucene.tests.store.TestSerializedIOCountingDirectory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/store/TestSerializedIOCountingDirectory.java) | org.gnit.lucenekmp.tests.store.TestSerializedIOCountingDirectory | Depth 1 | [] | 3 | 0 | 0% |
| [org.apache.lucene.tests.util.AbstractBeforeAfterRule](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/AbstractBeforeAfterRule.java) | org.gnit.lucenekmp.tests.util.AbstractBeforeAfterRule | Depth 4 | [x] | 3 | 2 | 66% |
| [org.apache.lucene.tests.util.FailureMarker](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/FailureMarker.java) | org.gnit.lucenekmp.tests.util.FailureMarker | Depth 3 | [] | 3 | 0 | 0% |
| [org.apache.lucene.tests.util.LuceneJUnit3MethodProvider](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/LuceneJUnit3MethodProvider.java) | org.gnit.lucenekmp.tests.util.LuceneJUnit3MethodProvider | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.LuceneTestCase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/LuceneTestCase.java) | org.gnit.lucenekmp.tests.util.LuceneTestCase | Depth 3 | [x] | 139 | 107 | 69% |
| [org.apache.lucene.tests.util.QuickPatchThreadsFilter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/QuickPatchThreadsFilter.java) | org.gnit.lucenekmp.tests.util.QuickPatchThreadsFilter | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.RamUsageTester](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/RamUsageTester.java) | org.gnit.lucenekmp.tests.util.RamUsageTester | Depth 3 | [x] | 8 | 16 | 62% |
| [org.apache.lucene.tests.util.RunListenerPrintReproduceInfo](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/RunListenerPrintReproduceInfo.java) | org.gnit.lucenekmp.tests.util.RunListenerPrintReproduceInfo | Depth 3 | [] | 9 | 0 | 0% |
| [org.apache.lucene.tests.util.SorePoint](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/SorePoint.java) | org.gnit.lucenekmp.tests.util.SorePoint | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.SoreType](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/SoreType.java) | org.gnit.lucenekmp.tests.util.SoreType | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.TestBeforeAfterOverrides](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/TestBeforeAfterOverrides.java) | org.gnit.lucenekmp.tests.util.TestBeforeAfterOverrides | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.util.TestCodecReported](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/TestCodecReported.java) | org.gnit.lucenekmp.tests.util.TestCodecReported | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.TestExceptionInBeforeClassHooks](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/TestExceptionInBeforeClassHooks.java) | org.gnit.lucenekmp.tests.util.TestExceptionInBeforeClassHooks | Depth 2 | [] | 3 | 0 | 0% |
| [org.apache.lucene.tests.util.TestExpectThrows](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/TestExpectThrows.java) | org.gnit.lucenekmp.tests.util.TestExpectThrows | Depth 2 | [] | 6 | 0 | 0% |
| [org.apache.lucene.tests.util.TestFailIfDirectoryNotClosed](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/TestFailIfDirectoryNotClosed.java) | org.gnit.lucenekmp.tests.util.TestFailIfDirectoryNotClosed | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.TestFailIfUnreferencedFiles](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/TestFailIfUnreferencedFiles.java) | org.gnit.lucenekmp.tests.util.TestFailIfUnreferencedFiles | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.TestFloatingPointUlpEquality](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/TestFloatingPointUlpEquality.java) | org.gnit.lucenekmp.tests.util.TestFloatingPointUlpEquality | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.util.TestGroupFiltering](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/TestGroupFiltering.java) | org.gnit.lucenekmp.tests.util.TestGroupFiltering | Depth 2 | [] | 4 | 0 | 0% |
| [org.apache.lucene.tests.util.TestJUnitRuleOrder](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/TestJUnitRuleOrder.java) | org.gnit.lucenekmp.tests.util.TestJUnitRuleOrder | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.TestLineFileDocs](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/TestLineFileDocs.java) | org.gnit.lucenekmp.tests.util.TestLineFileDocs | Depth 1 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.TestMaxFailuresRule](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/TestMaxFailuresRule.java) | org.gnit.lucenekmp.tests.util.TestMaxFailuresRule | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.util.TestPleaseFail](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/TestPleaseFail.java) | org.gnit.lucenekmp.tests.util.TestPleaseFail | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRamUsageTesterOnWildAnimals](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/TestRamUsageTesterOnWildAnimals.java) | org.gnit.lucenekmp.tests.util.TestRamUsageTesterOnWildAnimals | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.TestReproduceMessage](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/TestReproduceMessage.java) | org.gnit.lucenekmp.tests.util.TestReproduceMessage | Depth 2 | [] | 23 | 0 | 0% |
| [org.apache.lucene.tests.util.TestReproduceMessageWithRepeated](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/TestReproduceMessageWithRepeated.java) | org.gnit.lucenekmp.tests.util.TestReproduceMessageWithRepeated | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleAssertionsRequired](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleAssertionsRequired.java) | org.gnit.lucenekmp.tests.util.TestRuleAssertionsRequired | Depth 4 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleDelegate](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleDelegate.java) | org.gnit.lucenekmp.tests.util.TestRuleDelegate | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleIgnoreAfterMaxFailures](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleIgnoreAfterMaxFailures.java) | org.gnit.lucenekmp.tests.util.TestRuleIgnoreAfterMaxFailures | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleIgnoreTestSuites](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleIgnoreTestSuites.java) | org.gnit.lucenekmp.tests.util.TestRuleIgnoreTestSuites | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleLimitSysouts](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleLimitSysouts.java) | org.gnit.lucenekmp.tests.util.TestRuleLimitSysouts | Depth 4 | [] | 6 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleMarkFailure](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleMarkFailure.java) | org.gnit.lucenekmp.tests.util.TestRuleMarkFailure | Depth 3 | [x] | 7 | 3 | 42% |
| [org.apache.lucene.tests.util.TestRuleRestoreSystemProperties](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleRestoreSystemProperties.java) | org.gnit.lucenekmp.tests.util.TestRuleRestoreSystemProperties | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleSetupAndRestoreInstanceEnv](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleSetupAndRestoreInstanceEnv.java) | org.gnit.lucenekmp.tests.util.TestRuleSetupAndRestoreInstanceEnv | Depth 3 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleSetupTeardownChained](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleSetupTeardownChained.java) | org.gnit.lucenekmp.tests.util.TestRuleSetupTeardownChained | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleStoreClassName](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleStoreClassName.java) | org.gnit.lucenekmp.tests.util.TestRuleStoreClassName | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleTemporaryFilesCleanup](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleTemporaryFilesCleanup.java) | org.gnit.lucenekmp.tests.util.TestRuleTemporaryFilesCleanup | Depth 2 | [] | 8 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRuleThreadAndTestName](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestRuleThreadAndTestName.java) | org.gnit.lucenekmp.tests.util.TestRuleThreadAndTestName | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.TestRunWithRestrictedPermissions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/TestRunWithRestrictedPermissions.java) | org.gnit.lucenekmp.tests.util.TestRunWithRestrictedPermissions | Depth 1 | [] | 6 | 0 | 0% |
| [org.apache.lucene.tests.util.TestSeedFromUncaught](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/TestSeedFromUncaught.java) | org.gnit.lucenekmp.tests.util.TestSeedFromUncaught | Depth 2 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.TestSetupTeardownChaining](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/TestSetupTeardownChaining.java) | org.gnit.lucenekmp.tests.util.TestSetupTeardownChaining | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.tests.util.TestUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/TestUtil.java) | org.gnit.lucenekmp.tests.util.TestUtil | Depth 3 | [x] | 66 | 60 | 83% |
| [org.apache.lucene.tests.util.TestWorstCaseTestBehavior](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/TestWorstCaseTestBehavior.java) | org.gnit.lucenekmp.tests.util.TestWorstCaseTestBehavior | Depth 2 | [] | 6 | 0 | 0% |
| [org.apache.lucene.tests.util.VerifyTestClassNamingConvention](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/VerifyTestClassNamingConvention.java) | org.gnit.lucenekmp.tests.util.VerifyTestClassNamingConvention | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.tests.util.WithNestedTests](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/test/org/apache/lucene/tests/util/WithNestedTests.java) | org.gnit.lucenekmp.tests.util.WithNestedTests | Depth 3 | [] | 3 | 0 | 0% |
| [org.apache.lucene.tests.util.automaton.AutomatonTestUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/automaton/AutomatonTestUtil.java) | org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil | Depth 3 | [x] | 20 | 21 | 95% |
| [org.apache.lucene.tests.util.fst.FSTTester](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/test-framework/src/java/org/apache/lucene/tests/util/fst/FSTTester.java) | org.gnit.lucenekmp.tests.util.fst.FSTTester | Depth 2 | [x] | 14 | 1 | 7% |
| [org.apache.lucene.util.ArrayUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/ArrayUtil.java) | org.gnit.lucenekmp.util.ArrayUtil | Depth 3 | [x] | 61 | 60 | 90% |
| [org.apache.lucene.util.AttributeFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/AttributeFactory.java) | org.gnit.lucenekmp.util.AttributeFactory | Depth 3 | [x] | 3 | 15 | 66% |
| [org.apache.lucene.util.AttributeSource](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/AttributeSource.java) | org.gnit.lucenekmp.util.AttributeSource | Depth 3 | [x] | 15 | 18 | 94% |
| [org.apache.lucene.util.CharsRef](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/CharsRef.java) | org.gnit.lucenekmp.util.CharsRef | Depth 3 | [x] | 6 | 6 | 67% |
| [org.apache.lucene.util.ClassLoaderUtils](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/ClassLoaderUtils.java) | org.gnit.lucenekmp.util.ClassLoaderUtils | Depth 2 | [x] | 1 | 1 | 0% |
| [org.apache.lucene.util.CloseableThreadLocal](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/CloseableThreadLocal.java) | org.gnit.lucenekmp.util.CloseableThreadLocal | Depth 2 | [x] | 5 | 3 | 64% |
| [org.apache.lucene.util.CollectionUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/CollectionUtil.java) | org.gnit.lucenekmp.util.CollectionUtil | Depth 3 | [x] | 6 | 6 | 66% |
| [org.apache.lucene.util.FileDeleter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/FileDeleter.java) | org.gnit.lucenekmp.util.FileDeleter | Depth 4 | [x] | 13 | 31 | 92% |
| [org.apache.lucene.util.HotspotVMOptions](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/HotspotVMOptions.java) | org.gnit.lucenekmp.util.HotspotVMOptions | Depth 3 | [] | 1 | 0 | 0% |
| [org.apache.lucene.util.IOUtils](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/IOUtils.java) | org.gnit.lucenekmp.util.IOUtils | Depth 3 | [x] | 19 | 25 | 63% |
| [org.apache.lucene.util.IgnoreRandomChains](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/IgnoreRandomChains.java) | org.gnit.lucenekmp.util.IgnoreRandomChains | Depth 3 | [] | 0 | 0 | 0% |
| [org.apache.lucene.util.JavaLoggingInfoStream](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/JavaLoggingInfoStream.java) | org.gnit.lucenekmp.util.JavaLoggingInfoStream | Depth 2 | [] | 4 | 0 | 0% |
| [org.apache.lucene.util.NamedSPILoader](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/NamedSPILoader.java) | org.gnit.lucenekmp.util.NamedSPILoader | Depth 3 | [x] | 5 | 5 | 82% |
| [org.apache.lucene.util.NamedThreadFactory](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/NamedThreadFactory.java) | org.gnit.lucenekmp.util.NamedThreadFactory | Depth 2 | [x] | 2 | 2 | 38% |
| [org.apache.lucene.util.OfflineSorter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/OfflineSorter.java) | org.gnit.lucenekmp.util.OfflineSorter | Depth 3 | [x] | 7 | 12 | 93% |
| [org.apache.lucene.util.RamUsageEstimator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/RamUsageEstimator.java) | org.gnit.lucenekmp.util.RamUsageEstimator | Depth 3 | [x] | 42 | 45 | 97% |
| [org.apache.lucene.util.SelectorBenchmark](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/SelectorBenchmark.java) | org.gnit.lucenekmp.util.SelectorBenchmark | Depth 1 | [] | 4 | 0 | 0% |
| [org.apache.lucene.util.SorterBenchmark](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/SorterBenchmark.java) | org.gnit.lucenekmp.util.SorterBenchmark | Depth 1 | [] | 4 | 0 | 0% |
| [org.apache.lucene.util.SuppressForbidden](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/SuppressForbidden.java) | org.gnit.lucenekmp.util.SuppressForbidden | Depth 2 | [] | 0 | 0 | 0% |
| [org.apache.lucene.util.TestBytesRef](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/TestBytesRef.java) | org.gnit.lucenekmp.util.TestBytesRef | Depth 1 | [x] | 4 | 0 | 0% |
| [org.apache.lucene.util.TestBytesRefHash](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/TestBytesRefHash.java) | org.gnit.lucenekmp.util.TestBytesRefHash | Depth 1 | [x] | 13 | 12 | 92% |
| [org.apache.lucene.util.TestClassLoaderUtils](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/TestClassLoaderUtils.java) | org.gnit.lucenekmp.util.TestClassLoaderUtils | Depth 1 | [x] | 2 | 1 | 50% |
| [org.apache.lucene.util.TestFixedBitSet](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/TestFixedBitSet.java) | org.gnit.lucenekmp.util.TestFixedBitSet | Depth 1 | [x] | 29 | 29 | 96% |
| [org.apache.lucene.util.TestFrequencyTrackingRingBuffer](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/TestFrequencyTrackingRingBuffer.java) | org.gnit.lucenekmp.util.TestFrequencyTrackingRingBuffer | Depth 1 | [x] | 3 | 3 | 66% |
| [org.apache.lucene.util.TestJavaLoggingInfoStream](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/TestJavaLoggingInfoStream.java) | org.gnit.lucenekmp.util.TestJavaLoggingInfoStream | Depth 1 | [x] | 2 | 1 | 50% |
| [org.apache.lucene.util.TestMathUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/TestMathUtil.java) | org.gnit.lucenekmp.util.TestMathUtil | Depth 1 | [x] | 7 | 7 | 85% |
| [org.apache.lucene.util.TestRamUsageEstimator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/TestRamUsageEstimator.java) | org.gnit.lucenekmp.util.TestRamUsageEstimator | Depth 2 | [x] | 10 | 4 | 40% |
| [org.apache.lucene.util.TestRecyclingByteBlockAllocator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/TestRecyclingByteBlockAllocator.java) | org.gnit.lucenekmp.util.TestRecyclingByteBlockAllocator | Depth 1 | [x] | 5 | 4 | 80% |
| [org.apache.lucene.util.TestRecyclingIntBlockAllocator](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/TestRecyclingIntBlockAllocator.java) | org.gnit.lucenekmp.util.TestRecyclingIntBlockAllocator | Depth 1 | [x] | 5 | 4 | 80% |
| [org.apache.lucene.util.TestVectorUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/TestVectorUtil.java) | org.gnit.lucenekmp.util.TestVectorUtil | Depth 2 | [x] | 45 | 56 | 91% |
| [org.apache.lucene.util.TestWeakIdentityMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/TestWeakIdentityMap.java) | org.gnit.lucenekmp.util.TestWeakIdentityMap | Depth 2 | [x] | 3 | 2 | 66% |
| [org.apache.lucene.util.VirtualMethod](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/VirtualMethod.java) | org.gnit.lucenekmp.util.VirtualMethod | Depth 3 | [x] | 4 | 3 | 75% |
| [org.apache.lucene.util.WeakIdentityMap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/WeakIdentityMap.java) | org.gnit.lucenekmp.util.WeakIdentityMap | Depth 3 | [] | 12 | 0 | 0% |
| [org.apache.lucene.util.automaton.RegExp](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/RegExp.java) | org.gnit.lucenekmp.util.automaton.RegExp | Depth 3 | [x] | 49 | 52 | 98% |
| [org.apache.lucene.util.automaton.StringsToAutomaton](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/automaton/StringsToAutomaton.java) | org.gnit.lucenekmp.util.automaton.StringsToAutomaton | Depth 3 | [x] | 7 | 7 | 85% |
| [org.apache.lucene.util.automaton.TestOperations](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/automaton/TestOperations.java) | org.gnit.lucenekmp.util.automaton.TestOperations | Depth 1 | [x] | 31 | 33 | 96% |
| [org.apache.lucene.util.automaton.TestRegExp](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/automaton/TestRegExp.java) | org.gnit.lucenekmp.util.automaton.TestRegExp | Depth 1 | [x] | 15 | 12 | 80% |
| [org.apache.lucene.util.automaton.TestUTF32ToUTF8](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/automaton/TestUTF32ToUTF8.java) | org.gnit.lucenekmp.util.automaton.TestUTF32ToUTF8 | Depth 1 | [x] | 12 | 10 | 83% |
| [org.apache.lucene.util.bkd.BKDConfig](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/bkd/BKDConfig.java) | org.gnit.lucenekmp.util.bkd.BKDConfig | Depth 2 | [x] | 7 | 3 | 47% |
| [org.apache.lucene.util.bkd.BKDUtil](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/bkd/BKDUtil.java) | org.gnit.lucenekmp.util.bkd.BKDUtil | Depth 3 | [x] | 7 | 13 | 85% |
| [org.apache.lucene.util.bkd.HeapPointWriter](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/bkd/HeapPointWriter.java) | org.gnit.lucenekmp.util.bkd.HeapPointWriter | Depth 3 | [x] | 17 | 18 | 94% |
| [org.apache.lucene.util.bkd.TestBKD](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/bkd/TestBKD.java) | org.gnit.lucenekmp.util.bkd.TestBKD | Depth 1 | [x] | 37 | 33 | 81% |
| [org.apache.lucene.util.bkd.TestBKDRadixSelector](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/bkd/TestBKDRadixSelector.java) | org.gnit.lucenekmp.util.bkd.TestBKDRadixSelector | Depth 1 | [x] | 21 | 22 | 95% |
| [org.apache.lucene.util.fst.NoOutputs](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/fst/NoOutputs.java) | org.gnit.lucenekmp.util.fst.NoOutputs | Depth 3 | [x] | 8 | 7 | 89% |
| [org.apache.lucene.util.fst.Test2BFST](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/fst/Test2BFST.java) | org.gnit.lucenekmp.util.fst.Test2BFST | Depth 1 | [x] | 2 | 1 | 50% |
| [org.apache.lucene.util.fst.Test2BFSTOffHeap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/fst/Test2BFSTOffHeap.java) | org.gnit.lucenekmp.util.fst.Test2BFSTOffHeap | Depth 1 | [x] | 2 | 1 | 50% |
| [org.apache.lucene.util.fst.TestFSTs](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/fst/TestFSTs.java) | org.gnit.lucenekmp.util.fst.TestFSTs | Depth 2 | [x] | 30 | 9 | 30% |
| [org.apache.lucene.util.hnsw.BlockingFloatHeap](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/BlockingFloatHeap.java) | org.gnit.lucenekmp.util.hnsw.BlockingFloatHeap | Depth 2 | [x] | 6 | 9 | 76% |
| [org.apache.lucene.util.hnsw.HnswGraphTestCase](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/hnsw/HnswGraphTestCase.java) | org.gnit.lucenekmp.util.hnsw.HnswGraphTestCase | Depth 1 | [x] | 49 | 56 | 96% |
| [org.apache.lucene.util.hnsw.HnswLock](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/hnsw/HnswLock.java) | org.gnit.lucenekmp.util.hnsw.HnswLock | Depth 3 | [x] | 3 | 3 | 33% |
| [org.apache.lucene.util.hnsw.MockByteVectorValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/hnsw/MockByteVectorValues.java) | org.gnit.lucenekmp.util.hnsw.MockByteVectorValues | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.util.hnsw.MockVectorValues](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/hnsw/MockVectorValues.java) | org.gnit.lucenekmp.util.hnsw.MockVectorValues | Depth 1 | [] | 2 | 0 | 0% |
| [org.apache.lucene.util.hnsw.TestHnswByteVectorGraph](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/hnsw/TestHnswByteVectorGraph.java) | org.gnit.lucenekmp.util.hnsw.TestHnswByteVectorGraph | Depth 1 | [x] | 10 | 29 | 55% |
| [org.apache.lucene.util.hnsw.TestHnswFloatVectorGraph](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/test/org/apache/lucene/util/hnsw/TestHnswFloatVectorGraph.java) | org.gnit.lucenekmp.util.hnsw.TestHnswFloatVectorGraph | Depth 1 | [x] | 10 | 29 | 55% |
| [org.apache.lucene.util.packed.PackedDataInput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/PackedDataInput.java) | org.gnit.lucenekmp.util.packed.PackedDataInput | Depth 2 | [] | 2 | 0 | 0% |
| [org.apache.lucene.util.packed.PackedDataOutput](https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/util/packed/PackedDataOutput.java) | org.gnit.lucenekmp.util.packed.PackedDataOutput | Depth 2 | [] | 2 | 0 | 0% |


TODO.md written to: /home/joel/code/bbl-lucene/lucene-kmp/TODO.md

## Summary

### Lucene Priority-1 Classes (Semantic Analysis)
- Total Priority-1 Classes: 707
- Ported Priority-1 Classes: 704
- Priority-1 Class Porting Progress: 99%
- **Semantic Completion Progress: 97%**
- Total Core Methods Needed: 4794
- Core Methods Implemented: 4676

### Lucene Classes (Semantic Analysis)
- Total Classes: 1780
- Ported Classes: 1333
- Class Porting Progress: 74%
- **Semantic Completion Progress: 82%**
- Total Core Methods Needed: 10907
- Core Methods Implemented: 8954

### Unit Test Classes (Semantic Analysis)
- Total Unit Test Classes: 2622
- Ported Unit Test Classes: 2023
- Unit Test Porting Progress: 77%
- **Unit Test Semantic Completion: 80%**
- Total Test Core Methods Needed: 18425
- Test Core Methods Implemented: 14915
