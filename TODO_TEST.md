# TODO: Unported Unit Test Classes

From PROGRESS2.md → Progress Table for Unit Test Classes, ordered by dependency depth (higher first).

## Depth 6
- org.apache.lucene.tests.mockfile.FilterInputStream2 → org.gnit.lucenekmp.tests.mockfile.FilterInputStream2
- org.apache.lucene.tests.mockfile.FilterOutputStream2 → org.gnit.lucenekmp.tests.mockfile.FilterOutputStream2
- org.apache.lucene.tests.mockfile.FilterSeekableByteChannel → org.gnit.lucenekmp.tests.mockfile.FilterSeekableByteChannel

## Depth 5
- org.apache.lucene.search.similarities.BooleanSimilarity → org.gnit.lucenekmp.search.similarities.BooleanSimilarity
- org.apache.lucene.search.similarities.Normalization → org.gnit.lucenekmp.search.similarities.Normalization
- org.apache.lucene.tests.codecs.asserting.AssertingKnnVectorsFormat → org.gnit.lucenekmp.tests.codecs.asserting.AssertingKnnVectorsFormat
- org.apache.lucene.tests.codecs.asserting.AssertingLiveDocsFormat → org.gnit.lucenekmp.tests.codecs.asserting.AssertingLiveDocsFormat
- org.apache.lucene.tests.codecs.asserting.AssertingNormsFormat → org.gnit.lucenekmp.tests.codecs.asserting.AssertingNormsFormat
- org.apache.lucene.tests.codecs.asserting.AssertingPointsFormat → org.gnit.lucenekmp.tests.codecs.asserting.AssertingPointsFormat
- org.apache.lucene.tests.codecs.asserting.AssertingStoredFieldsFormat → org.gnit.lucenekmp.tests.codecs.asserting.AssertingStoredFieldsFormat
- org.apache.lucene.tests.codecs.asserting.AssertingTermVectorsFormat → org.gnit.lucenekmp.tests.codecs.asserting.AssertingTermVectorsFormat
- org.apache.lucene.tests.codecs.bloom.TestBloomFilteredLucenePostings → org.gnit.lucenekmp.tests.codecs.bloom.TestBloomFilteredLucenePostings
- org.apache.lucene.tests.codecs.compressing.dummy.DummyCompressingCodec → org.gnit.lucenekmp.tests.codecs.compressing.dummy.DummyCompressingCodec
- org.apache.lucene.tests.index.MergeReaderWrapper → org.gnit.lucenekmp.tests.index.MergeReaderWrapper
- org.apache.lucene.tests.mockfile.FilterAsynchronousFileChannel → org.gnit.lucenekmp.tests.mockfile.FilterAsynchronousFileChannel
- org.apache.lucene.tests.mockfile.FilterDirectoryStream → org.gnit.lucenekmp.tests.mockfile.FilterDirectoryStream
- org.apache.lucene.tests.mockfile.FilterFileChannel → org.gnit.lucenekmp.tests.mockfile.FilterFileChannel
- org.apache.lucene.tests.mockfile.FilterFileStore → org.gnit.lucenekmp.tests.mockfile.FilterFileStore
- org.apache.lucene.tests.mockfile.HandleTrackingFS → org.gnit.lucenekmp.tests.mockfile.HandleTrackingFS

## Depth 4
- org.apache.lucene.index.SimpleMergedSegmentWarmer → org.gnit.lucenekmp.index.SimpleMergedSegmentWarmer
- org.apache.lucene.index.SnapshotDeletionPolicy → org.gnit.lucenekmp.index.SnapshotDeletionPolicy
- org.apache.lucene.search.similarities.AfterEffect → org.gnit.lucenekmp.search.similarities.AfterEffect
- org.apache.lucene.search.similarities.AfterEffectB → org.gnit.lucenekmp.search.similarities.AfterEffectB
- org.apache.lucene.search.similarities.AfterEffectL → org.gnit.lucenekmp.search.similarities.AfterEffectL
- org.apache.lucene.search.similarities.BasicModel → org.gnit.lucenekmp.search.similarities.BasicModel
- org.apache.lucene.search.similarities.BasicModelG → org.gnit.lucenekmp.search.similarities.BasicModelG
- org.apache.lucene.search.similarities.BasicModelIF → org.gnit.lucenekmp.search.similarities.BasicModelIF
- org.apache.lucene.search.similarities.BasicModelIn → org.gnit.lucenekmp.search.similarities.BasicModelIn
- org.apache.lucene.search.similarities.BasicModelIne → org.gnit.lucenekmp.search.similarities.BasicModelIne
- org.apache.lucene.search.similarities.DFISimilarity → org.gnit.lucenekmp.search.similarities.DFISimilarity
- org.apache.lucene.search.similarities.DFRSimilarity → org.gnit.lucenekmp.search.similarities.DFRSimilarity
- org.apache.lucene.search.similarities.Distribution → org.gnit.lucenekmp.search.similarities.Distribution
- org.apache.lucene.search.similarities.DistributionLL → org.gnit.lucenekmp.search.similarities.DistributionLL
- org.apache.lucene.search.similarities.DistributionSPL → org.gnit.lucenekmp.search.similarities.DistributionSPL
- org.apache.lucene.search.similarities.IBSimilarity → org.gnit.lucenekmp.search.similarities.IBSimilarity
- org.apache.lucene.search.similarities.Independence → org.gnit.lucenekmp.search.similarities.Independence
- org.apache.lucene.search.similarities.IndependenceChiSquared → org.gnit.lucenekmp.search.similarities.IndependenceChiSquared
- org.apache.lucene.search.similarities.IndependenceSaturated → org.gnit.lucenekmp.search.similarities.IndependenceSaturated
- org.apache.lucene.search.similarities.IndependenceStandardized → org.gnit.lucenekmp.search.similarities.IndependenceStandardized
- org.apache.lucene.search.similarities.LMJelinekMercerSimilarity → org.gnit.lucenekmp.search.similarities.LMJelinekMercerSimilarity
- org.apache.lucene.search.similarities.Lambda → org.gnit.lucenekmp.search.similarities.Lambda
- org.apache.lucene.search.similarities.LambdaDF → org.gnit.lucenekmp.search.similarities.LambdaDF
- org.apache.lucene.search.similarities.LambdaTTF → org.gnit.lucenekmp.search.similarities.LambdaTTF
- org.apache.lucene.search.similarities.NormalizationH1 → org.gnit.lucenekmp.search.similarities.NormalizationH1
- org.apache.lucene.search.similarities.NormalizationH2 → org.gnit.lucenekmp.search.similarities.NormalizationH2
- org.apache.lucene.search.similarities.NormalizationH3 → org.gnit.lucenekmp.search.similarities.NormalizationH3
- org.apache.lucene.search.similarities.NormalizationZ → org.gnit.lucenekmp.search.similarities.NormalizationZ
- org.apache.lucene.tests.codecs.asserting.AssertingCodec → org.gnit.lucenekmp.tests.codecs.asserting.AssertingCodec
- org.apache.lucene.tests.codecs.asserting.AssertingDocValuesFormat → org.gnit.lucenekmp.tests.codecs.asserting.AssertingDocValuesFormat
- org.apache.lucene.tests.codecs.asserting.AssertingPostingsFormat → org.gnit.lucenekmp.tests.codecs.asserting.AssertingPostingsFormat
- org.apache.lucene.tests.codecs.blockterms.LuceneVarGapDocFreqInterval → org.gnit.lucenekmp.tests.codecs.blockterms.LuceneVarGapDocFreqInterval
- org.apache.lucene.tests.codecs.blockterms.LuceneVarGapFixedInterval → org.gnit.lucenekmp.tests.codecs.blockterms.LuceneVarGapFixedInterval
- org.apache.lucene.tests.codecs.compressing.DeflateWithPresetCompressingCodec → org.gnit.lucenekmp.tests.codecs.compressing.DeflateWithPresetCompressingCodec
- org.apache.lucene.tests.codecs.compressing.FastCompressingCodec → org.gnit.lucenekmp.tests.codecs.compressing.FastCompressingCodec
- org.apache.lucene.tests.codecs.compressing.FastDecompressionCompressingCodec → org.gnit.lucenekmp.tests.codecs.compressing.FastDecompressionCompressingCodec
- org.apache.lucene.tests.codecs.compressing.HighCompressionCompressingCodec → org.gnit.lucenekmp.tests.codecs.compressing.HighCompressionCompressingCodec
- org.apache.lucene.tests.codecs.compressing.LZ4WithPresetCompressingCodec → org.gnit.lucenekmp.tests.codecs.compressing.LZ4WithPresetCompressingCodec
- org.apache.lucene.tests.codecs.mockrandom.MockRandomPostingsFormat → org.gnit.lucenekmp.tests.codecs.mockrandom.MockRandomPostingsFormat
- org.apache.lucene.tests.index.MockRandomMergePolicy → org.gnit.lucenekmp.tests.index.MockRandomMergePolicy
- org.apache.lucene.tests.index.RandomCodec → org.gnit.lucenekmp.tests.index.RandomCodec
- org.apache.lucene.tests.mockfile.DisableFsyncFS → org.gnit.lucenekmp.tests.mockfile.DisableFsyncFS
- org.apache.lucene.tests.mockfile.FilterFileSystem → org.gnit.lucenekmp.tests.mockfile.FilterFileSystem
- org.apache.lucene.tests.mockfile.FilterFileSystemProvider → org.gnit.lucenekmp.tests.mockfile.FilterFileSystemProvider
- org.apache.lucene.tests.mockfile.FilterPath → org.gnit.lucenekmp.tests.mockfile.FilterPath
- org.apache.lucene.tests.mockfile.ShuffleFS → org.gnit.lucenekmp.tests.mockfile.ShuffleFS
- org.apache.lucene.tests.mockfile.WindowsPath → org.gnit.lucenekmp.tests.mockfile.WindowsPath
- org.apache.lucene.tests.util.TestRuleAssertionsRequired → org.gnit.lucenekmp.tests.util.TestRuleAssertionsRequired
- org.apache.lucene.tests.util.TestRuleIgnoreTestSuites → org.gnit.lucenekmp.tests.util.TestRuleIgnoreTestSuites
- org.apache.lucene.tests.util.TestRuleLimitSysouts → org.gnit.lucenekmp.tests.util.TestRuleLimitSysouts
- org.apache.lucene.util.HotspotVMOptions → org.gnit.lucenekmp.util.HotspotVMOptions

## Depth 3
- org.apache.lucene.codecs.FilterCodec → org.gnit.lucenekmp.codecs.FilterCodec
- org.apache.lucene.document.KeywordField → org.gnit.lucenekmp.document.KeywordField
- org.apache.lucene.index.LogByteSizeMergePolicy → org.gnit.lucenekmp.index.LogByteSizeMergePolicy
- org.apache.lucene.index.LogDocMergePolicy → org.gnit.lucenekmp.index.LogDocMergePolicy
- org.apache.lucene.index.LogMergePolicy → org.gnit.lucenekmp.index.LogMergePolicy
- org.apache.lucene.index.NoDeletionPolicy → org.gnit.lucenekmp.index.NoDeletionPolicy
- org.apache.lucene.store.FileSwitchDirectory → org.gnit.lucenekmp.store.FileSwitchDirectory
- org.apache.lucene.store.NRTCachingDirectory → org.gnit.lucenekmp.store.NRTCachingDirectory
- org.apache.lucene.tests.codecs.blockterms.LuceneFixedGap → org.gnit.lucenekmp.tests.codecs.blockterms.LuceneFixedGap
- org.apache.lucene.tests.codecs.cheapbastard.CheapBastardCodec → org.gnit.lucenekmp.tests.codecs.cheapbastard.CheapBastardCodec
- org.apache.lucene.tests.codecs.compressing.CompressingCodec → org.gnit.lucenekmp.tests.codecs.compressing.CompressingCodec
- org.apache.lucene.tests.index.AlcoholicMergePolicy → org.gnit.lucenekmp.tests.index.AlcoholicMergePolicy
- org.apache.lucene.tests.index.BaseIndexFileFormatTestCase → org.gnit.lucenekmp.tests.index.BaseIndexFileFormatTestCase
- org.apache.lucene.tests.index.MockIndexWriterEventListener → org.gnit.lucenekmp.tests.index.MockIndexWriterEventListener
- org.apache.lucene.tests.mockfile.ExtrasFS → org.gnit.lucenekmp.tests.mockfile.ExtrasFS
- org.apache.lucene.tests.mockfile.HandleLimitFS → org.gnit.lucenekmp.tests.mockfile.HandleLimitFS
- org.apache.lucene.tests.mockfile.LeakFS → org.gnit.lucenekmp.tests.mockfile.LeakFS
- org.apache.lucene.tests.mockfile.VerboseFS → org.gnit.lucenekmp.tests.mockfile.VerboseFS
- org.apache.lucene.tests.mockfile.VirusCheckingFS → org.gnit.lucenekmp.tests.mockfile.VirusCheckingFS
- org.apache.lucene.tests.mockfile.WindowsFS → org.gnit.lucenekmp.tests.mockfile.WindowsFS
- org.apache.lucene.tests.store.MockIndexInputWrapper → org.gnit.lucenekmp.tests.store.MockIndexInputWrapper
- org.apache.lucene.tests.store.MockIndexOutputWrapper → org.gnit.lucenekmp.tests.store.MockIndexOutputWrapper
- org.apache.lucene.tests.store.SlowClosingMockIndexInputWrapper → org.gnit.lucenekmp.tests.store.SlowClosingMockIndexInputWrapper
- org.apache.lucene.tests.store.SlowOpeningMockIndexInputWrapper → org.gnit.lucenekmp.tests.store.SlowOpeningMockIndexInputWrapper
- org.apache.lucene.tests.util.FailureMarker → org.gnit.lucenekmp.tests.util.FailureMarker
- org.apache.lucene.tests.util.LuceneJUnit3MethodProvider → org.gnit.lucenekmp.tests.util.LuceneJUnit3MethodProvider
- org.apache.lucene.tests.util.QuickPatchThreadsFilter → org.gnit.lucenekmp.tests.util.QuickPatchThreadsFilter
- org.apache.lucene.tests.util.RunListenerPrintReproduceInfo → org.gnit.lucenekmp.tests.util.RunListenerPrintReproduceInfo
- org.apache.lucene.tests.util.TestRuleDelegate → org.gnit.lucenekmp.tests.util.TestRuleDelegate
- org.apache.lucene.tests.util.TestRuleIgnoreAfterMaxFailures → org.gnit.lucenekmp.tests.util.TestRuleIgnoreAfterMaxFailures
- org.apache.lucene.tests.util.TestRuleRestoreSystemProperties → org.gnit.lucenekmp.tests.util.TestRuleRestoreSystemProperties
- org.apache.lucene.tests.util.TestRuleSetupAndRestoreInstanceEnv → org.gnit.lucenekmp.tests.util.TestRuleSetupAndRestoreInstanceEnv
- org.apache.lucene.tests.util.TestRuleSetupTeardownChained → org.gnit.lucenekmp.tests.util.TestRuleSetupTeardownChained
- org.apache.lucene.tests.util.TestRuleStoreClassName → org.gnit.lucenekmp.tests.util.TestRuleStoreClassName
- org.apache.lucene.tests.util.TestRuleThreadAndTestName → org.gnit.lucenekmp.tests.util.TestRuleThreadAndTestName
- org.apache.lucene.tests.util.ThrottledIndexOutput → org.gnit.lucenekmp.tests.util.ThrottledIndexOutput
- org.apache.lucene.tests.util.VerifyTestClassNamingConvention → org.gnit.lucenekmp.tests.util.VerifyTestClassNamingConvention

## Depth 2
- org.apache.lucene.tests.index.BaseKnnVectorsFormatTestCase → org.gnit.lucenekmp.tests.index.BaseKnnVectorsFormatTestCase
- org.apache.lucene.tests.index.ForceMergePolicy → org.gnit.lucenekmp.tests.index.ForceMergePolicy
- org.apache.lucene.tests.util.TestRuleTemporaryFilesCleanup → org.gnit.lucenekmp.tests.util.TestRuleTemporaryFilesCleanup
- org.apache.lucene.util.SuppressForbidden → org.gnit.lucenekmp.util.SuppressForbidden

