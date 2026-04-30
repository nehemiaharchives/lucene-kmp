# Release notes — 10.2.0-alpha12

## Overview

This release contains the porting progress and improvements in the kotlin-multiplatform `lucene-kmp`
module since `10.2.0-alpha11`.

## Notable Milestones

- mingwX64 target of Kotlin/Native supported!
- Core module components are now ported, and almost all unit tests ported and passing.
- Priority-1 API progress reached 100%: 181 dependency classes ported among 181 tracked priority-1 dependencies.
- Production class porting reached 1586 classes ported among 2399 tracked classes (66%).
- Unit test class porting reached 843 classes ported among 1186 tracked unit test classes (71%).
- Semantic progress from `PROGRESS2.md` reached 82% for production classes and 81% for unit test classes.
- Core `index` and `search` test coverage advanced substantially, including NRT readers, merges, doc values updates, sorting, collectors, query scoring, and concurrency tests.
- `test-framework` gained additional helper classes needed by the newly ported core tests.

## Bug Fixes

- Fixed synchronization and race bugs around `IndexWriter`, pending merges, commit state, rollback, and segment version updates.
- Fixed native and Windows filesystem support needed for `mingwX64`, including file write and lock behavior.
- Fixed `DenseConjunctionBulkScorer` behavior around `NO_MORE_DOCS` overflow.
- Fixed `JavaLangAccess` concurrent job registry corruption.
- Fixed Urdu normalization buffer growth for ligatures.
- Fixed nullable reset value handling in `IndexWriter.buildDocValuesUpdate`.
- Fixed `OneMerge.hasCompletedSuccessfully()` behavior before merge completion.
- Reduced native runtime of NRT/search-after style tests by lowering excessive iteration counts where appropriate for development speed.

## List of newly ported classes:

* /codecs/src/commonMain/kotlin/org/gnit/lucenekmp/codecs/memory/DirectPostingsFormat.kt
* /core/src/commonMain/kotlin/org/gnit/lucenekmp/index/NoMergeScheduler.kt
* /core/src/commonMain/kotlin/org/gnit/lucenekmp/index/PersistentSnapshotDeletionPolicy.kt
* /core/src/commonMain/kotlin/org/gnit/lucenekmp/index/TwoPhaseCommitTool.kt
* /core/src/commonMain/kotlin/org/gnit/lucenekmp/search/SearcherLifetimeManager.kt
* /core/src/commonMain/kotlin/org/gnit/lucenekmp/search/SeededKnnVectorQuery.kt
* /core/src/commonMain/kotlin/org/gnit/lucenekmp/search/SortRescorer.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/Test4GBStoredFields.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestLongPostings.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestManyFields.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestMaxPosition.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestMaxTermFrequency.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestMergePolicy.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestMergeRateLimiter.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestMixedCodecs.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestMixedDocValuesUpdates.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestMultiDocValues.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestMultiFields.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestMultiLevelSkipList.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestMultiTermsEnum.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestNRTReaderCleanup.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestNRTReaderWithThreads.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestNeverDelete.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestNewestSegment.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestNoDeletionPolicy.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestNoMergeScheduler.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestNumericDocValuesUpdates.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestOmitNorms.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestOmitPositions.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestOmitTf.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestOrdinalMap.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestParallelCompositeReader.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestParallelLeafReader.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestParallelReaderEmptyIndex.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestParallelTermEnum.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestPayloads.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestPayloadsOnVectors.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestPendingSoftDeletes.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestPerSegmentDeletes.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestPersistentSnapshotDeletionPolicy.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestPointValues.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestPostingsOffsets.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestPrefixCodedTerms.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestReadOnlyIndex.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestReaderClosed.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestReaderPool.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestReaderWrapperDVTypeCheck.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestRollback.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestRollingUpdates.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestSameTokenSamePosition.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestSegmentInfos.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestSegmentMerger.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestSegmentReader.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestSegmentTermDocs.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestSegmentTermEnum.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestSegmentToThreadMapping.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestSizeBoundedForceMerge.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestSnapshotDeletionPolicy.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestSoftDeletesDirectoryReaderWrapper.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestSoftDeletesRetentionMergePolicy.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestSortingCodecReader.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestStoredFieldsConsumer.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestStressAdvance.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestStressDeletes.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestStressIndexing.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestStressIndexing2.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestStressNRT.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestSumDocFreq.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestSwappedIndexFiles.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestTermStates.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestTermVectors.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestTermVectorsWriter.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestTermdocPerf.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestTerms.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestTermsEnum.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestTermsEnum2.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestTermsEnumIndex.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestTermsHashPerField.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestThreadedForceMerge.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestTragicIndexWriterDeadlock.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestTransactionRollback.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestTransactions.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestTryDelete.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestTwoPhaseCommitTool.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestUniqueTermCount.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/ReadAheadMatchAllDocsQuery.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestRegexpRandom.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestReqOptSumScorer.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestSameScoresWithThreads.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestScoreCachingWrappingScorer.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestScorerPerf.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestScorerUtil.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestSearchAfter.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestSearchWithThreads.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestSearcherManager.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestSeededKnnByteVectorQuery.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestSeededKnnFloatVectorQuery.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestSegmentCacheables.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestShardSearching.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestSimilarity.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestSimilarityProvider.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestSimpleExplanationsWithFillerDocs.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestSloppyPhraseQuery.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestSloppyPhraseQuery2.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestSortOptimization.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestSortRandom.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestSortRescorer.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestSortedNumericSortField.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestSortedSetSelector.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestSortedSetSortField.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestSynonymQuery.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestTermInSetQuery.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestTermQuery.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestTermRangeQuery.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestTermScorer.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestTopDocsCollector.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestTopDocsMerge.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestTopDocsRRF.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestTopFieldCollector.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestTopFieldCollectorEarlyTermination.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestTopKnnResults.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestTotalHitCountCollector.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestTotalHits.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestUsageTrackingFilterCachingPolicy.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestVectorSimilarityCollector.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestVectorSimilarityValuesSource.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestWANDScorer.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestWildcardQuery.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestWildcardRandom.kt
* /core/src/commonTest/kotlin/org/gnit/lucenekmp/search/TestXYPointDistanceSort.kt
* /core/src/jvmAndroidMain/kotlin/org/gnit/lucenekmp/jdkport/FilesPlatform.jvm.kt
* /core/src/mingwX64Main/kotlin/org/gnit/lucenekmp/jdkport/ArraysPlatform.native.kt
* /core/src/mingwX64Main/kotlin/org/gnit/lucenekmp/jdkport/FilesPlatform.windows.kt
* /core/src/mingwX64Main/kotlin/org/gnit/lucenekmp/jdkport/FilesWrite.native.kt
* /core/src/mingwX64Main/kotlin/org/gnit/lucenekmp/jdkport/ReentrantLockNativeWindows.kt
* /core/src/mingwX64Main/kotlin/org/gnit/lucenekmp/jdkport/ThreadId.kt
* /core/src/mingwX64Main/kotlin/org/gnit/lucenekmp/store/NrtOpenInputLock.native.kt
* /core/src/posixNativeMain/kotlin/org/gnit/lucenekmp/jdkport/FilesPlatform.native.kt
* /test-framework/src/commonMain/kotlin/org/gnit/lucenekmp/tests/analysis/CannedBinaryTokenStream.kt
* /test-framework/src/commonMain/kotlin/org/gnit/lucenekmp/tests/analysis/MockPayloadAnalyzer.kt
* /test-framework/src/commonMain/kotlin/org/gnit/lucenekmp/tests/index/OwnCacheKeyMultiReader.kt
* /test-framework/src/commonMain/kotlin/org/gnit/lucenekmp/tests/index/PerThreadPKLookup.kt
* /test-framework/src/commonMain/kotlin/org/gnit/lucenekmp/tests/mockfile/SharedReadOnlyFileHandle.kt
* /test-framework/src/commonMain/kotlin/org/gnit/lucenekmp/tests/search/ShardSearchingTestBase.kt
* /test-framework/src/iosArm64Main/kotlin/org/gnit/lucenekmp/tests/mockfile/SharedReadOnlyFileHandle.iosArm64.kt
* /test-framework/src/iosSimulatorArm64Main/kotlin/org/gnit/lucenekmp/tests/mockfile/SharedReadOnlyFileHandle.iosSimulatorArm64.kt
* /test-framework/src/iosX64Main/kotlin/org/gnit/lucenekmp/tests/mockfile/SharedReadOnlyFileHandle.iosX64.kt
* /test-framework/src/jvmAndroidMain/kotlin/org/gnit/lucenekmp/tests/mockfile/SharedReadOnlyFileHandle.jvmAndroid.kt
* /test-framework/src/linuxX64Main/kotlin/org/gnit/lucenekmp/tests/mockfile/SharedReadOnlyFileHandle.linuxX64.kt
* /test-framework/src/macosArm64Main/kotlin/org/gnit/lucenekmp/tests/mockfile/SharedReadOnlyFileHandle.macosArm64.kt
* /test-framework/src/macosX64Main/kotlin/org/gnit/lucenekmp/tests/mockfile/SharedReadOnlyFileHandle.macosX64.kt
* /test-framework/src/mingwX64Main/kotlin/org/gnit/lucenekmp/tests/mockfile/SharedReadOnlyFileHandle.windows.kt
