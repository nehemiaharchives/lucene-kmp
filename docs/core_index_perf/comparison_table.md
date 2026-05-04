# JVM vs Native Performance Comparison

Performance comparison of test suites between JVM, Linux (native), and iOS (native) platforms.
**Only tests taking ≥1 second on at least one platform are included.**

## Summary Statistics

- **Total Unique Tests Across All Platforms**: 196
- **Tests Analyzed (≥1s on at least one platform)**: 84
- **JVM Tests in Log**: 196
- **Linux Tests in Log**: 112 (incomplete - job timeout)
- **iOS Tests in Log**: 171 (incomplete - job timeout)

### Total Execution Time (Filtered Tests Only)

- **JVM**: 3m 8s
- **Linux**: 12m 14s (3.89x slower)
- **iOS**: 41m 21s (13.16x slower)

### Performance Degradation Analysis

**Linux Platform Slowdowns:**
- Tests ≥2x slower: 43 (51%)
- Tests ≥5x slower: 24 (28%)
- Tests ≥10x slower: 9 (10%)

**iOS Platform Slowdowns:**
- Tests ≥2x slower: 76 (90%)
- Tests ≥5x slower: 56 (66%)
- Tests ≥10x slower: 47 (55%)

### Top 15 Slowest Tests on iOS

- TestSoftDeletesRetentionMergePolicy: 957.26x slower
- TestPrefixCodedTerms: 740.00x slower
- TestSizeBoundedForceMerge: 469.44x slower
- TestStressAdvance: 440.00x slower
- TestOmitNorms: 427.17x slower
- TestParallelCompositeReader: 347.33x slower
- TestMultiFields: 281.25x slower
- TestParallelLeafReader: 248.61x slower
- TestReaderClosed: 225.00x slower
- TestSegmentTermDocs: 215.92x slower
- TestIndexFileDeleter: 200.69x slower
- TestSegmentReader: 149.66x slower
- TestKnnGraph: 142.01x slower
- TestMergePolicy: 123.21x slower
- TestOmitPositions: 116.13x slower

### Top 15 Slowest Tests on Linux

- TestIndexFileDeleter: 232.64x slower
- TestLongPostings: 177.14x slower
- TestLockableConcurrentApproximatePriorityQueue: 23.94x slower
- TestIndexWriterOnDiskFull: 23.29x slower
- TestIndexingSequenceNumbers: 12.25x slower
- TestIndexWriterUnicode: 11.80x slower
- TestDefaultCodecParallelizesIO: 11.69x slower
- TestIndexWriterThreadsToSegments: 10.72x slower
- TestKnnGraph: 10.41x slower
- TestFieldsReader: 8.92x slower
- TestIndexWriterDelete: 8.24x slower
- TestFilterIndexInput: 8.00x slower
- TestIndexWriterForceMerge: 7.08x slower
- TestIndexWriterReader: 6.63x slower
- TestCustomNorms: 6.60x slower

---

## Detailed Comparison Table

Tests are sorted by performance impact. Column **Bigger X** shows the maximum slowdown across platforms.

| Test Name | Platform | JVM Time | Linux Time | iOS Time | Linux X | iOS X | Bigger X |
|-----------|----------|----------|-----------|---------|---------|-------|----------|
| TestIndexFileDeleter | all | 144ms | 33.5s | 28.9s | 232.64x | 200.69x | 232.64x |
| TestLongPostings | all | 280ms | 49.6s | 3.7s | 177.14x | 13.21x | 177.14x |
| TestKnnGraph | all | 269ms | 2.8s | 38.2s | 10.41x | 142.01x | 142.01x |
| TestLockableConcurrentApproximatePriorityQueue | all | 213ms | 5.1s | 18.6s | 23.94x | 87.32x | 87.32x |
| TestIndexingSequenceNumbers | all | 10.2s | 2m 5s | 8m 7s | 12.25x | 47.75x | 47.75x |
| TestIndexWriterWithThreads | all | 3.0s | 16.0s | 2m 19s | 5.33x | 46.33x | 46.33x |
| TestIndexWriterUnicode | all | 966ms | 11.4s | 43.8s | 11.80x | 45.34x | 45.34x |
| TestIndexWriterThreadsToSegments | all | 3.2s | 34.3s | 1m 48s | 10.72x | 33.75x | 33.75x |
| TestIndexWriterOnError | all | 1.4s | 7.3s | 38.5s | 5.21x | 27.50x | 27.50x |
| TestIndexWriterOnDiskFull | all | 146ms | 3.4s | 2.0s | 23.29x | 13.70x | 23.29x |
| TestIndexWriterExceptions2 | all | 233ms | 1.1s | 5.2s | 4.72x | 22.32x | 22.32x |
| TestIndexTooManyDocs | all | 169ms | 708ms | 2.7s | 4.19x | 15.98x | 15.98x |
| TestIndexableField | all | 141ms | 756ms | 2.2s | 5.36x | 15.60x | 15.60x |
| TestIndexWriterMerging | all | 1.6s | 9.8s | 19.2s | 6.12x | 12.00x | 12.00x |
| TestIndexWriterMergePolicy | all | 2.9s | 15.1s | 34.4s | 5.21x | 11.86x | 11.86x |
| TestCustomNorms | all | 119ms | 785ms | 1.4s | 6.60x | 11.76x | 11.76x |
| TestDefaultCodecParallelizesIO | all | 8.9s | 1m 44s | 1m 1s | 11.69x | 6.85x | 11.69x |
| TestIndexWriterForceMerge | all | 1.2s | 8.5s | 13.8s | 7.08x | 11.50x | 11.50x |
| TestIndexWriterReader | all | 3.8s | 25.2s | 43.6s | 6.63x | 11.47x | 11.47x |
| TestIndexWriterOutOfFileDescriptors | all | 372ms | 2.2s | 3.4s | 5.91x | 9.14x | 9.14x |
| TestFieldsReader | all | 437ms | 3.9s | 2.8s | 8.92x | 6.41x | 8.92x |
| TestIndexWriterDelete | all | 4.9s | 40.4s | 39.7s | 8.24x | 8.10x | 8.24x |
| TestFilterIndexInput | all | 1.3s | 10.4s | 7.8s | 8.00x | 6.00x | 8.00x |
| TestDirectoryReaderReopen | all | 279ms | 1.2s | 2.1s | 4.30x | 7.53x | 7.53x |
| TestIndexSorting | all | 2.2s | 10.9s | 14.3s | 4.95x | 6.50x | 6.50x |
| TestIndexWriterCommit | all | 3.0s | 11.8s | 17.2s | 3.93x | 5.73x | 5.73x |
| Test2BPostingsBytes | all | 339ms | 1.9s | 1.3s | 5.60x | 3.83x | 5.60x |
| TestDocsAndPositions | all | 182ms | 1.0s | 842ms | 5.49x | 4.63x | 5.49x |
| TestDirectoryReader | all | 547ms | 2.9s | 2.1s | 5.30x | 3.84x | 5.30x |
| TestBinaryDocValuesUpdates | all | 17.1s | 1m 22s | 38.3s | 4.80x | 2.24x | 4.80x |
| TestAllFilesHaveCodecHeader | all | 314ms | 1.5s | 907ms | 4.78x | 2.89x | 4.78x |
| TestDemoParallelLeafReader | all | 588ms | 2.8s | 2.4s | 4.76x | 4.08x | 4.76x |
| TestCrash | all | 294ms | 1.4s | 1.1s | 4.76x | 3.74x | 4.76x |
| TestFlushByRamOrCountsPolicy | all | 804ms | 3.7s | 3.5s | 4.60x | 4.35x | 4.60x |
| TestIndexOptions | all | 399ms | 1.8s | 1.4s | 4.51x | 3.51x | 4.51x |
| TestDocumentWriter | all | 267ms | 1.2s | 1.0s | 4.49x | 3.75x | 4.49x |
| Test2BPoints | all | 357ms | 1.6s | 1.4s | 4.48x | 3.92x | 4.48x |
| TestIndexManyDocuments | all | 256ms | 1.0s | 1.1s | 3.91x | 4.30x | 4.30x |
| TestCheckIndex | all | 633ms | 2.3s | 1.6s | 3.63x | 2.53x | 3.63x |
| TestAtomicUpdate | all | 546ms | 1.5s | 1.7s | 2.75x | 3.11x | 3.11x |
| TestIndexWriterExceptions | all | 12.4s | 33.2s | 35.8s | 2.68x | 2.89x | 2.89x |
| Test2BTerms | all | 2.6s | 7.1s | 6.0s | 2.73x | 2.31x | 2.73x |
| Test2BDocs | all | 659ms | 1.0s | 1.6s | 1.52x | 2.43x | 2.43x |
| Test2BBinaryDocValues | all | 2.4s | 1.0s | 5.5s | 0.42x | 2.29x | 2.29x |
| TestDeletionPolicy | all | 1.7s | 3.6s | 2.6s | 2.12x | 1.53x | 2.12x |
| TestIndexWriter | all | 11.9s | 18.6s | 24.3s | 1.56x | 2.04x | 2.04x |
| TestDuelingCodecs | all | 911ms | 1.7s | 1.7s | 1.87x | 1.87x | 1.87x |
| TestConcurrentMergeScheduler | all | 11.9s | 14.9s | 12.3s | 1.25x | 1.03x | 1.25x |
| TestMixedDocValuesUpdates | jvm+ios | 11.9s | — | 6m 13s | — | 31.34x | 31.34x |
| TestSoftDeletesRetentionMergePolicy | jvm+ios | 117ms | — | 1m 52s | — | 957.26x | 957.26x |
| TestManyFields | jvm+ios | 1.0s | — | 1m 41s | — | 101.00x | 101.00x |
| TestSegmentReader | jvm+ios | 441ms | — | 1m 6s | — | 149.66x | 149.66x |
| TestStressAdvance | jvm+ios | 150ms | — | 1m 6s | — | 440.00x | 440.00x |
| TestNumericDocValuesUpdates | jvm+ios | 1.6s | — | 1m 2s | — | 38.75x | 38.75x |
| TestSegmentTermDocs | jvm+ios | 245ms | — | 52.9s | — | 215.92x | 215.92x |
| TestPrefixCodedTerms | jvm+ios | 65ms | — | 48.1s | — | 740.00x | 740.00x |
| TestParallelCompositeReader | jvm+ios | 131ms | — | 45.5s | — | 347.33x | 347.33x |
| TestMultiFields | jvm+ios | 144ms | — | 40.5s | — | 281.25x | 281.25x |
| TestOmitNorms | jvm+ios | 92ms | — | 39.3s | — | 427.17x | 427.17x |
| TestTransactions | jvm+linux | 33.3s | — | — | — | — | — |
| TestParallelLeafReader | jvm+ios | 72ms | — | 17.9s | — | 248.61x | 248.61x |
| TestSizeBoundedForceMerge | jvm+ios | 36ms | — | 16.9s | — | 469.44x | 469.44x |
| TestRollingUpdates | jvm+ios | 395ms | — | 16.5s | — | 41.77x | 41.77x |
| TestSortingCodecReader | jvm+ios | 257ms | — | 15.1s | — | 58.75x | 58.75x |
| TestNRTThreads | jvm+ios | 723ms | — | 7.4s | — | 10.24x | 10.24x |
| TestMixedCodecs | jvm+ios | 459ms | — | 7.0s | — | 15.25x | 15.25x |
| TestMergePolicy | jvm+ios | 56ms | — | 6.9s | — | 123.21x | 123.21x |
| TestOmitTf | jvm+ios | 327ms | — | 6.0s | — | 18.35x | 18.35x |
| TestSegmentMerger | jvm+ios | 397ms | — | 6.0s | — | 15.11x | 15.11x |
| TestExitableDirectoryReader | all | 5.2s | 5.1s | 5.2s | 0.98x | 1.00x | 1.00x |
| TestNeverDelete | jvm+ios | 560ms | — | 4.9s | — | 8.75x | 8.75x |
| TestReaderPool | jvm+ios | 83ms | — | 4.7s | — | 56.63x | 56.63x |
| TestSnapshotDeletionPolicy | jvm+ios | 300ms | — | 4.6s | — | 15.33x | 15.33x |
| TestMaxTermFrequency | jvm+ios | 282ms | — | 4.4s | — | 15.60x | 15.60x |
| TestPersistentSnapshotDeletionPolicy | jvm+ios | 323ms | — | 3.9s | — | 12.07x | 12.07x |
| TestOmitPositions | jvm+ios | 31ms | — | 3.6s | — | 116.13x | 116.13x |
| TestMultiLevelSkipList | jvm+ios | 1.3s | — | 2.9s | — | 2.23x | 2.23x |
| TestReaderClosed | jvm+ios | 12ms | — | 2.7s | — | 225.00x | 225.00x |
| TestPostingsOffsets | jvm+ios | 133ms | — | 1.7s | — | 12.78x | 12.78x |
| TestPointValues | jvm+ios | 154ms | — | 1.7s | — | 11.04x | 11.04x |
| TestNRTReaderWithThreads | jvm+ios | 48ms | — | 1.6s | — | 33.33x | 33.33x |
| TestTragicIndexWriterDeadlock | jvm+linux | 1.5s | — | — | — | — | — |
| TestTieredMergePolicy | jvm+linux | 1.0s | — | — | — | — | — |
| TestAddIndexes | all | 7.2s | 6.1s | 6.2s | 0.85x | 0.86x | 0.86x |
