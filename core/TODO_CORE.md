Fail 1, Status (Fixed)
ArraysTest failed with 2 tests:

1: testEqualsFloatArrayRangesNaNHandling failed with

kotlin.AssertionError: Expected value to be true.
kotlin.AssertionError: Expected value to be true.
at kotlin.Error#<init>(Unknown Source)
at kotlin.AssertionError#<init>(Unknown Source)
at kotlin.test.DefaultAsserter#fail(Unknown Source)
at kotlin.test.Asserter#fail(Unknown Source)
at kotlin.test.Asserter#assertTrue(Unknown Source)
at kotlin.test.Asserter#assertTrue(Unknown Source)
at kotlin.test.Asserter#assertTrue(Unknown Source)
at kotlin.test.Asserter#assertTrue(Unknown Source)

2: testEqualsFloatArrayRangesZeroHandling failed with

kotlin.AssertionError: Expected value to be false.
kotlin.AssertionError: Expected value to be false.
at kotlin.Error#<init>(Unknown Source)
at kotlin.AssertionError#<init>(Unknown Source)
at kotlin.test.DefaultAsserter#fail(Unknown Source)
at kotlin.test.Asserter#fail(Unknown Source)
at kotlin.test.Asserter#assertTrue(Unknown Source)
at kotlin.test.Asserter#assertTrue(Unknown Source)
at kotlin.test.Asserter#assertTrue(Unknown Source)
at kotlin.test.Asserter#assertTrue(Unknown Source)


Fail 2, Status (Fixed)
BreakIteratorTest runs more than 51 min and when I saw the output, it hanged at here:
comparing expected and actual...
>\u0915\u094d\u094d\u0924<
testFollowing():
bi.following(0) -> 4
bi.following(1) -> 4
bi.following(2) -> 4
bi.following(3) -> 4
bi.following(4) -> -1
testPreceding():
bi.preceding(0) -> -1
bi.preceding(1) -> 0
bi.preceding(2) -> 0
bi.preceding(3) -> 0
bi.preceding(4) -> 0
testIsBoundary():
bi.isBoundary(0) -> true
bi.isBoundary(1) -> false
bi.isBoundary(2) -> false
bi.isBoundary(3) -> false
bi.isBoundary(4) -> true
Multiple selection test...
next(0) -> 0
next(1) -> 4
next(0) -> 4
next(-1) -> 0


Fail 3, Status (Not Fixed Yet)
TestFileSwitchDirectory, TestMMapDirectory, TestMultiMMap, TestNIOFSDirectory  failed at testCreateOutputForExistingFile like this:


kotlin.AssertionError: Unexpected exception type, expected FileAlreadyExistsException but got: okio.IOException: The file exists.
kotlin.AssertionError: Unexpected exception type, expected FileAlreadyExistsException but got: okio.IOException: The file exists.
at kotlin.Error#<init>(Unknown Source)
at kotlin.AssertionError#<init>(Unknown Source)
at kotlin.test.DefaultAsserter#fail(Unknown Source)
at kotlin.test.Asserter#fail(Unknown Source)
at kotlin.test#fail(Unknown Source)
at org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion#expectThrows(Unknown Source)
at org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion#expectThrows(Unknown Source)
at org.gnit.lucenekmp.tests.store.BaseDirectoryTestCase#testCreateOutputForExistingFile(Unknown Source)
at org.gnit.lucenekmp.store.TestNIOFSDirectory#testCreateOutputForExistingFile(Unknown Source)
at org.gnit.lucenekmp.store.$TestNIOFSDirectory$test$539.$TestNIOFSDirectory$test$539$$FUNCTION_REFERENCE_FOR$testCreateOutputForExistingFile$45.invoke#internal(Unknown Source)
at org.gnit.lucenekmp.store.$TestNIOFSDirectory$test$539.$TestNIOFSDirectory$test$539$$FUNCTION_REFERENCE_FOR$testCreateOutputForExistingFile$45.$<bridge-DNC>invoke(Unknown Source)
at kotlin.Function1#invoke(Unknown Source)
at kotlin.native.internal.test.BaseClassSuite.TestCase#doRun(Unknown Source)
at kotlin.native.internal.test.TestCase#doRun(Unknown Source)
at kotlin.native.internal.test.TestCase#run(Unknown Source)
at kotlin.native.internal.test.TestCase#run(Unknown Source)
at kotlin.native.internal.test.TestRunner.run#internal(Unknown Source)
at kotlin.native.internal.test.TestRunner.runIteration#internal(Unknown Source)
at kotlin.native.internal.test.TestRunner#run(Unknown Source)
at kotlin.native.internal.test#testLauncherEntryPoint(Unknown Source)
at kotlin.native.internal.test#main(Unknown Source)
at <global>.Konan_start(Unknown Source)
at <global>.Init_and_run_start(Unknown Source)
at <global>.__tmainCRTStartup(Unknown Source)
at <global>.mainCRTStartup(Unknown Source)
at <global>._ZSt25__throw_bad_function_callv(Unknown Source)
at <global>._ZSt25__throw_bad_function_callv(Unknown Source)
at kotlin.Exception#<init>(Unknown Source)
at okio.IOException#<init>(Unknown Source)
at okio.IOException#<init>(Unknown Source)
at org.gnit.lucenekmp.jdkport.lastErrorToIOException#internal(Unknown Source)
at org.gnit.lucenekmp.jdkport#newOutputStreamPlatform(Unknown Source)
at org.gnit.lucenekmp.jdkport.Files#newOutputStream(Unknown Source)
at org.gnit.lucenekmp.store.FSDirectory.FSIndexOutput.FSDirectory$FSIndexOutput$1.<init>#internal(Unknown Source)
at org.gnit.lucenekmp.store.FSDirectory.FSIndexOutput#<init>__at__org.gnit.lucenekmp.store.FSDirectory(Unknown Source)
at org.gnit.lucenekmp.store.FSDirectory.FSIndexOutput#<init>__at__org.gnit.lucenekmp.store.FSDirectory(Unknown Source)
at org.gnit.lucenekmp.store.FSDirectory#createOutput(Unknown Source)
at org.gnit.lucenekmp.store.Directory#createOutput(Unknown Source)
at org.gnit.lucenekmp.tests.store.BaseDirectoryTestCase.BaseDirectoryTestCase$testCreateOutputForExistingFile$$inlined$use$1.run#internal(Unknown Source)
at org.gnit.lucenekmp.tests.util.LuceneTestCase.ThrowingRunnable#run(Unknown Source)
at org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion._expectThrows#internal(Unknown Source)
at org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion#expectThrows(Unknown Source)



Fail 4, Status (Not Fixed Yet)
TestNRTCachingDirectory failed testCreateTempOutputSameName at

okio.IOException: The file exists.
okio.IOException: The file exists.
at kotlin.Exception#<init>(Unknown Source)
at okio.IOException#<init>(Unknown Source)
at okio.IOException#<init>(Unknown Source)
at org.gnit.lucenekmp.jdkport.lastErrorToIOException#internal(Unknown Source)
at org.gnit.lucenekmp.jdkport#newOutputStreamPlatform(Unknown Source)
at org.gnit.lucenekmp.jdkport.Files#newOutputStream(Unknown Source)
at org.gnit.lucenekmp.store.FSDirectory.FSIndexOutput.FSDirectory$FSIndexOutput$1.<init>#internal(Unknown Source)
at org.gnit.lucenekmp.store.FSDirectory.FSIndexOutput#<init>__at__org.gnit.lucenekmp.store.FSDirectory(Unknown Source)
at org.gnit.lucenekmp.store.FSDirectory#createTempOutput(Unknown Source)
at org.gnit.lucenekmp.store.Directory#createTempOutput(Unknown Source)
at org.gnit.lucenekmp.store.NRTCachingDirectory#createTempOutput(Unknown Source)
at org.gnit.lucenekmp.store.NRTCachingDirectory#createTempOutput(Unknown Source)
at org.gnit.lucenekmp.store.TestNRTCachingDirectory#testCreateTempOutputSameName(Unknown Source)
at org.gnit.lucenekmp.store.$TestNRTCachingDirectory$test$540.$TestNRTCachingDirectory$test$540$$FUNCTION_REFERENCE_FOR$testCreateTempOutputSameName$1.invoke#internal(Unknown Source)
at org.gnit.lucenekmp.store.$TestNRTCachingDirectory$test$540.$TestNRTCachingDirectory$test$540$$FUNCTION_REFERENCE_FOR$testCreateTempOutputSameName$1.$<bridge-DNC>invoke(Unknown Source)
at kotlin.Function1#invoke(Unknown Source)
at kotlin.native.internal.test.BaseClassSuite.TestCase#doRun(Unknown Source)
at kotlin.native.internal.test.TestCase#doRun(Unknown Source)
at kotlin.native.internal.test.TestCase#run(Unknown Source)
at kotlin.native.internal.test.TestCase#run(Unknown Source)
at kotlin.native.internal.test.TestRunner.run#internal(Unknown Source)
at kotlin.native.internal.test.TestRunner.runIteration#internal(Unknown Source)
at kotlin.native.internal.test.TestRunner#run(Unknown Source)
at kotlin.native.internal.test#testLauncherEntryPoint(Unknown Source)
at kotlin.native.internal.test#main(Unknown Source)
at <global>.Konan_start(Unknown Source)
at <global>.Init_and_run_start(Unknown Source)
at <global>.__tmainCRTStartup(Unknown Source)
at <global>.mainCRTStartup(Unknown Source)
at <global>._ZSt25__throw_bad_function_callv(Unknown Source)
at <global>._ZSt25__throw_bad_function_callv(Unknown Source)


Fail 5, Status (Not Fixed Yet)
In TestMultiMMap, testSeekingExceptions hangs and no logs are ouput. need to add debug logs for each steps to figure out which steps causes hang.


