# TODO: Unported Unit Test Classes

From PROGRESS2.md → Progress Table for Unit Test Classes:

## Depth 4
- org.apache.lucene.tests.codecs.bloom.TestBloomFilteredLucenePostings -> org.apache.lucene.tests.codecs.bloom.TestBloomFilteredLucenePostings

## Depth 3
- org.apache.lucene.tests.util.TestRuleAssertionsRequired -> org.apache.lucene.tests.util.TestRuleAssertionsRequired
- org.apache.lucene.tests.util.TestRuleIgnoreTestSuites -> org.apache.lucene.tests.util.TestRuleIgnoreTestSuites
- org.apache.lucene.tests.util.TestRuleLimitSysouts -> org.apache.lucene.tests.util.TestRuleLimitSysouts

## Depth 2
- org.apache.lucene.index.TestExitableDirectoryReader -> org.apache.lucene.index.ExitableDirectoryReader
- org.apache.lucene.index.TestFilterLeafReader -> org.apache.lucene.index.FilterLeafReader (Ported)
- org.apache.lucene.search.TestConjunctions -> org.apache.lucene.search.BooleanQuery (Ported)
- org.apache.lucene.tests.util.TestRuleDelegate -> org.apache.lucene.tests.util.TestRuleDelegate
- org.apache.lucene.tests.util.TestRuleIgnoreAfterMaxFailures -> org.apache.lucene.tests.util.TestRuleIgnoreAfterMaxFailures
- org.apache.lucene.tests.util.TestRuleMarkFailure -> org.apache.lucene.tests.util.TestRuleMarkFailure
- org.apache.lucene.tests.util.TestRuleRestoreSystemProperties -> org.apache.lucene.tests.util.TestRuleRestoreSystemProperties
- org.apache.lucene.tests.util.TestRuleSetupAndRestoreClassEnv -> org.apache.lucene.tests.util.TestRuleSetupAndRestoreClassEnv
- org.apache.lucene.tests.util.TestRuleSetupAndRestoreInstanceEnv -> org.apache.lucene.tests.util.TestRuleSetupAndRestoreInstanceEnv
- org.apache.lucene.tests.util.TestRuleSetupTeardownChained -> org.apache.lucene.tests.util.TestRuleSetupTeardownChained
- org.apache.lucene.tests.util.TestRuleStoreClassName -> org.apache.lucene.tests.util.TestRuleStoreClassName
- org.apache.lucene.tests.util.TestRuleThreadAndTestName -> org.apache.lucene.tests.util.TestRuleThreadAndTestName

## Depth 1
- org.apache.lucene.analysis.tokenattributes.TestCharTermAttributeImpl -> org.apache.lucene.analysis.tokenattributes.CharTermAttributeImpl (Ported)
- org.apache.lucene.analysis.tokenattributes.TestPackedTokenAttributeImpl -> org.apache.lucene.analysis.tokenattributes.PackedTokenAttributeImpl (Ported)
- org.apache.lucene.index.TestByteSlicePool -> org.apache.lucene.index.ByteSlicePool (Ported)
- org.apache.lucene.index.TestCachingMergeContext -> org.apache.lucene.index.CachingMergeContext (Ported)
- org.apache.lucene.index.TestDirectoryReader -> org.apache.lucene.index.DirectoryReader (Ported)
- org.apache.lucene.index.TestDocumentsWriterStallControl -> org.apache.lucene.index.DocumentsWriterStallControl (Ported)
- org.apache.lucene.index.TestFieldInvertState -> org.apache.lucene.index.FieldInvertState (Ported)
- org.apache.lucene.index.TestFlushByRamOrCountsPolicy -> org.apache.lucene.index.FlushByRamOrCountsPolicy (Ported)
- org.apache.lucene.index.TestIndexCommit -> org.apache.lucene.index.IndexCommit (Ported)
- org.apache.lucene.index.TestIndexWriter -> org.apache.lucene.index.IndexWriter (Ported)
- org.apache.lucene.index.TestIndexWriterConfig -> org.apache.lucene.index.IndexWriterConfig (Ported)
- org.apache.lucene.index.TestLockableConcurrentApproximatePriorityQueue -> org.apache.lucene.index.LockableConcurrentApproximatePriorityQueue (Ported)
- org.apache.lucene.index.TestMultiFields -> org.apache.lucene.index.MultiFields (Ported)
- org.apache.lucene.index.TestOneMergeWrappingMergePolicy -> org.apache.lucene.index.OneMergeWrappingMergePolicy (Ported)
- org.apache.lucene.index.TestPendingSoftDeletes -> org.apache.lucene.index.PendingSoftDeletes (Ported)
- org.apache.lucene.index.TestTermVectorsReader -> org.apache.lucene.codecs.TermVectorsReader (Ported)
- org.apache.lucene.internal.vectorization.TestVectorScorer -> org.apache.lucene.search.VectorScorer (Ported)
- org.apache.lucene.search.TestBooleanQuery -> org.apache.lucene.search.BooleanQuery (Ported)
- org.apache.lucene.search.TestCollectorManager -> org.apache.lucene.search.CollectorManager (Ported)
- org.apache.lucene.search.TestDisjunctionScoreBlockBoundaryPropagator -> org.apache.lucene.search.DisjunctionScoreBlockBoundaryPropagator (Ported)
- org.apache.lucene.search.TestIndexSearcher -> org.apache.lucene.search.IndexSearcher (Ported)
- org.apache.lucene.search.TestKnnByteVectorQuery -> org.apache.lucene.search.KnnByteVectorQuery (Ported)
- org.apache.lucene.search.TestKnnFloatVectorQuery -> org.apache.lucene.search.KnnFloatVectorQuery (Ported)
- org.apache.lucene.search.TestMatchesIterator -> org.apache.lucene.search.MatchesIterator (Ported)
- org.apache.lucene.search.TestReqExclBulkScorer -> org.apache.lucene.search.ReqExclBulkScorer (Ported)
- org.apache.lucene.search.TestTermScorer -> org.apache.lucene.search.TermScorer (Ported)
- org.apache.lucene.search.TestTopDocsCollector -> org.apache.lucene.search.TopDocsCollector (Ported)
- org.apache.lucene.search.TestVectorScorer -> org.apache.lucene.search.VectorScorer (Ported)
- org.apache.lucene.store.TestNIOFSDirectory -> org.apache.lucene.store.NIOFSDirectory (Ported)
- org.apache.lucene.tests.util.TestRuleTemporaryFilesCleanup -> org.apache.lucene.tests.util.TestRuleTemporaryFilesCleanup
- org.apache.lucene.util.TestCloseableThreadLocal -> org.apache.lucene.util.CloseableThreadLocal (Ported)
