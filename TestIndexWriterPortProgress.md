# TestIndexWriter Port Progress

Source of truth:
- Java source: `lucene/lucene/core/src/test/org/apache/lucene/index/TestIndexWriter.java`
- Kotlin target: `lucene-kmp/core/src/commonTest/kotlin/org/gnit/lucenekmp/index/TestIndexWriter.kt`

Notes:
- Status values:
  - `PORTED`: present in Kotlin in upstream order
  - `TODO`: not ported yet
  - `BLOCKED`: upstream dependency or platform simulation is missing
- Kotlin-specific class shape decision:
  - `TestIndexWriter.kt` is now `class TestIndexWriter : LuceneTestCase()`
  - static Java helpers are preserved inside `companion object`

## Ordered Progress

| Java line | Member | Status | Notes |
|---|---|---|---|
| 129 | `testDocCount` | PORTED | |
| 240 | `testCreateWithReader` | PORTED | |
| 269 | `testChangesAfterClose` | PORTED | |
| 282 | `testIndexNoDocuments` | PORTED | |
| 306 | `testSmallRAMBuffer` | PORTED | |
| 339 | `testChangingRAMBuffer` | PORTED | |
| 392 | `testEnablingNorms` | PORTED | |
| 452 | `testHighFreqTerm` | PORTED | |
| 487 | `testFlushWithNoMerging` | PORTED | |
| 510 | `testEmptyDocAfterFlushingRealDoc` | PORTED | |
| 536 | `testBadSegment` | PORTED | |
| 550 | `testMaxThreadPriority` | PORTED | |
| 573 | `testVariableSchema` | PORTED | |
| 624 | `testUnlimitedMaxFieldLength` | PORTED | |
| 642 | `testEmptyFieldName` | PORTED | |
| 652 | `testEmptyFieldNameTerms` | PORTED | |
| 670 | `testEmptyFieldNameWithEmptyTerm` | PORTED | |
| 713 | `testDoBeforeAfterFlush` | PORTED | |
| 739 | `testNegativePositions` | PORTED | |
| 771 | `testPositionIncrementGapEmptyField` | PORTED | |
| 809 | `testDeadlock` | PORTED | |
| 847 | `IndexerThreadInterrupt` | PORTED | |
| 1093 | `testThreadInterruptDeadlock` | PORTED | |
| 1126 | `testIndexStoreCombos` | PORTED | |
| 1228 | `testNoDocsIndex` | PORTED | |
| 1237 | `testDeleteUnusedFiles` | PORTED | Uses KMP `WindowsFS` over wrapped `okio.FileSystem`; runtime parity still needs validation |
| 1329 | `testDeleteUnusedFiles2` | PORTED | |
| 1374 | `testEmptyFSDirWithNoLock` | PORTED | |
| 1383 | `testEmptyDirRollback` | PORTED | |
| 1466 | `testNoUnwantedTVFiles` | PORTED | |
| 1513 | `StringSplitAnalyzer` | EXISTING | Already present in companion object |
| 1520 | `StringSplitTokenizer` | EXISTING | Already present in companion object |
| 1557 | `testWickedLongTerm` | PORTED | |
| 1627 | `testDeleteAllNRTLeftoverFiles` | PORTED | |
| 1649 | `testNRTReaderVersion` | PORTED | |
| 1674 | `testWhetherDeleteAllDeletesWriteLock` | PORTED | KMP uses `IndexWriterConfig()` instead of Java `newIndexWriterConfig(null)` because analyzer is non-null in KMP |
| 1692 | `testHasBlocksMergeFullyDelSegments` | PORTED | |
| 1728 | `testSingleDocsDoNotTriggerHasBlocks` | PORTED | |
| 1766 | `testCarryOverHasBlocks` | PORTED | |
| 1809 | `testPrepareCommitThenClose` | PORTED | |
| 1824 | `testPrepareCommitThenRollback` | PORTED | |
| 1835 | `testPrepareCommitThenRollback2` | PORTED | |
| 1850 | `testDontInvokeAnalyzerForUnAnalyzedFields` | PORTED | |
| 1889 | `testOtherFiles` | PORTED | |
| 1909 | `testStopwordsPosIncHole` | PORTED | |
| 1939 | `testStopwordsPosIncHole2` | PORTED | |
| 1972 | `testCommitWithUserDataOnly` | PORTED | |
| 2035 | `testGetCommitData` | PORTED | |
| 2055 | `testGetCommitDataFromOldSnapshot` | PORTED | |
| 2096 | `testNullAnalyzer` | PORTED | KMP uses an analyzer whose `createComponents` throws `NullPointerException` to model Java's `newIndexWriterConfig(null)` behavior |
| 2125 | `testNullDocument` | PORTED | |
| 2148 | `testNullDocuments` | PORTED | |
| 2171 | `testIterableFieldThrowsException` | PORTED | |
| 2220 | `testIterableThrowsException` | PORTED | |
| 2269 | `testIterableThrowsException2` | PORTED | |
| 2344 | `testCorruptFirstCommit` | PORTED | |
| 2394 | `testHasUncommittedChanges` | PORTED | |
| 2443 | `testMergeAllDeleted` | PORTED | |
| 2481 | `testDeleteSameTermAcrossFields` | PORTED | |
| 2502 | `testHasUncommittedChangesAfterException` | PORTED | |
| 2521 | `testDoubleClose` | PORTED | |
| 2533 | `testRollbackThenClose` | PORTED | |
| 2545 | `testCloseThenRollback` | PORTED | |
| 2557 | `testCloseWhileMergeIsRunning` | TODO | |
| 2557 | `testCloseWhileMergeIsRunning` | PORTED | |
| 2617 | `testCloseDuringCommit` | PORTED | KMP uses `IndexWriterConfig()` instead of Java `IndexWriterConfig(null)` since analyzer is unused in this commit-only path |
| 2667 | `testIds` | PORTED | |
| 2714 | `testEmptyNorm` | PORTED | |
| 2730 | `testManySeparateThreads` | PORTED | |
| 2762 | `testNRTSegmentsFile` | PORTED | |
| 2803 | `testNRTAfterCommit` | PORTED | |
| 2823 | `testNRTAfterSetUserDataWithoutCommit` | PORTED | |
| 2842 | `testNRTAfterSetUserDataWithCommit` | PORTED | |
| 2860 | `testCommitImmediatelyAfterNRTReopen` | PORTED | |
| 2881 | `testPendingDeleteDVGeneration` | PORTED | |
| 2949 | `testPendingDeletionsRollbackWithReader` | PORTED | |
| 2988 | `testWithPendingDeletions` | PORTED | |
| 3039 | `testPendingDeletesAlreadyWrittenFiles` | PORTED | |
| 3065 | `testLeftoverTempFiles` | PORTED | |
| 3089 | `testMassiveField` | PORTED | Upstream is `@Ignore`; Kotlin keeps the same ignored slot and full body |
| 3119 | `testRecordsIndexCreatedVersion` | PORTED | |
| 3129 | `testFlushLargestWriter` | PORTED | |
| 3185 | `testNeverCheckOutOnFullFlush` | PORTED | |
| 3206 | `testApplyDeletesWithoutFlushes` | PORTED | |
| 3238 | `testDeletesAppliedOnFlush` | PORTED | |
| 3292 | `testHoldLockOnLargestWriter` | PORTED | |
| 3350 | `testCheckPendingFlushPostUpdate` | PORTED | Uses newly ported `Collections.synchronizedSet` and `Thread.yield` helpers |
| 3446 | `testSoftUpdateDocuments` | PORTED | |
| 3532 | `testSoftUpdatesConcurrently` | PORTED | |
| 3536 | `testSoftUpdatesConcurrentlyMixedDeletes` | PORTED | |
| 3702 | `testDeleteHappensBeforeWhileFlush` | PORTED | |
| 3765 | `testFullyDeletedSegmentsReleaseFiles` | PORTED | Uses KMP `ExtrasFS.isExtra(...)` port in helper `assertFiles` |
| 3788 | `testSegmentInfoIsSnapshot` | PORTED | |
| 3814 | `testPreventChangingSoftDeletesField` | PORTED | |
| 3872 | `testPreventAddingIndexesWithDifferentSoftDeletesField` | PORTED | |
| 3907 | `testNotAllowUsingExistingFieldAsSoftDeletes` | PORTED | |
| 3945 | `testBrokenPayload` | PORTED | |
| 3959 | `testSoftAndHardLiveDocs` | PORTED | |
| 3992 | `testAbortFullyDeletedSegment` | PORTED | Kotlin uses `runBlocking { setAborted() }` inside `onMergeComplete()` because the KMP merge abort API is suspend |
| 4060 | `testSetIndexCreatedVersion` | PORTED | |
| 4116 | `testFlushWhileStartingNewThreads` | PORTED | KMP wraps `markForFullFlush()` in `runBlocking` because the common-code API is suspend |
| 4146 | `testRefreshAndRollbackConcurrently` | PORTED | |
| 4217 | `testCloseableQueue` | PORTED | |
| 4271 | `testRandomOperations` | PORTED | |
| 4329 | `testRandomOperationsWithSoftDeletes` | PORTED | |
| 4398 | `testMaxCompletedSequenceNumber` | PORTED | |
| 4466 | `testEnsureMaxSeqNoIsAccurateDuringFlush` | PORTED | |
| 4547 | `testSegmentCommitInfoId` | PORTED | |
| 4644 | `testMergeZeroDocsMergeIsClosedOnce` | PORTED | |
| 4687 | `testMergeOnCommitKeepFullyDeletedSegments` | PORTED | |
| 4726 | `testPendingNumDocs` | PORTED | |
| 4746 | `testIndexWriterBlocksOnStall` | PORTED | |
| 4791 | `testGetFieldNames` | PORTED | Required parity fix in `IndexWriter.getFieldNames()` to return an immutable snapshot |
| 4839 | `testParentAndSoftDeletesAreTheSame` | PORTED | |
| 4853 | `testParentFieldExistingIndex` | PORTED | |
| 4897 | `testIndexWithParentFieldIsCongruent` | PORTED | |
| 4943 | `testParentFieldIsAlreadyUsed` | PORTED | |
| 4968 | `testParentFieldEmptyIndex` | PORTED | |
| 4983 | `testDocValuesMixedSkippingIndex` | PORTED | |
| 5019 | `testDocValuesSkippingIndexWithoutDocValues` | PORTED | |
