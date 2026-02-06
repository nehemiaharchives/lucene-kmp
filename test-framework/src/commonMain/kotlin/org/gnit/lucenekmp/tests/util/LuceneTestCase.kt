package org.gnit.lucenekmp.tests.util

//import org.gnit.lucenekmp.store.FileSwitchDirectory
//import org.gnit.lucenekmp.store.NRTCachingDirectory
//import org.gnit.lucenekmp.util.configureTestLogging
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.TimeZone
import okio.IOException
import okio.Path
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.CodecReader
import org.gnit.lucenekmp.index.CompositeReader
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.LiveIndexWriterConfig
import org.gnit.lucenekmp.index.LogByteSizeMergePolicy
import org.gnit.lucenekmp.index.LogDocMergePolicy
import org.gnit.lucenekmp.index.LogMergePolicy
import org.gnit.lucenekmp.index.MergePolicy
import org.gnit.lucenekmp.index.ParallelCompositeReader
import org.gnit.lucenekmp.index.ParallelLeafReader
import org.gnit.lucenekmp.index.TieredMergePolicy
import org.gnit.lucenekmp.jdkport.ExecutorService
import org.gnit.lucenekmp.jdkport.LinkedBlockingQueue
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.ThreadPoolExecutor
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryCachingPolicy
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FSDirectory
import org.gnit.lucenekmp.store.FSLockFactory
import org.gnit.lucenekmp.store.FlushInfo
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.LockFactory
import org.gnit.lucenekmp.store.MMapDirectory
import org.gnit.lucenekmp.store.MergeInfo
import org.gnit.lucenekmp.store.NIOFSDirectory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.AlcoholicMergePolicy
import org.gnit.lucenekmp.tests.index.AssertingDirectoryReader
import org.gnit.lucenekmp.tests.index.AssertingLeafReader
import org.gnit.lucenekmp.tests.index.FieldFilterLeafReader
import org.gnit.lucenekmp.tests.index.MergingCodecReader
import org.gnit.lucenekmp.tests.index.MergingDirectoryReaderWrapper
import org.gnit.lucenekmp.tests.index.MismatchedCodecReader
import org.gnit.lucenekmp.tests.index.MismatchedDirectoryReader
import org.gnit.lucenekmp.tests.index.MismatchedLeafReader
import org.gnit.lucenekmp.tests.index.MockRandomMergePolicy
import org.gnit.lucenekmp.tests.search.AssertingIndexSearcher
import org.gnit.lucenekmp.tests.search.similarities.AssertingSimilarity
import org.gnit.lucenekmp.tests.search.similarities.RandomSimilarity
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.store.RawDirectoryWrapper
import org.gnit.lucenekmp.tests.util.RandomizedTest.Companion.systemPropertyAsBoolean
import org.gnit.lucenekmp.tests.util.RandomizedTest.Companion.systemPropertyAsInt
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.CommandLineUtil
import org.gnit.lucenekmp.util.NamedThreadFactory
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.test.*

open class LuceneTestCase/*: org.junit.Assert*/ { // Java lucene version inherits from junit Assert but in kmp it is not pssible.

    // uncomment only when debugging kotlin/native linuxX64 using KotlinLogging
    /*init {
        configureTestLogging()
    }*/

    @BeforeTest
    fun resetPerTestFieldTypes() {
        // Keep per-field type randomization scoped to a single test method, like Lucene's test runner.
        fieldToType.clear()
    }

    //↓ line 2860 of LuceneTestCase.java
    /** A runnable that can throw any checked exception.  */
    fun interface ThrowingRunnable {
        @Throws(Throwable::class)
        fun run()
    }

    /** A function Consumer that can throw any checked exception.  */
    fun interface ThrowingConsumer<T> {
        @Throws(Exception::class)
        fun accept(t: T)
    }
    //↑ line 2870 of LuceneTestCase.java

    companion object {
        val logger = KotlinLogging.logger {}


        // ↓ line 277 of LuceneTestCase.java
        // --------------------------------------------------------------------
        // Test groups, system properties and other annotations modifying tests
        // --------------------------------------------------------------------
        const val SYSPROP_NIGHTLY: String = "tests.nightly"
        const val SYSPROP_WEEKLY: String = "tests.weekly"
        const val SYSPROP_MONSTER: String = "tests.monster"
        const val SYSPROP_AWAITSFIX: String = "tests.awaitsfix"

        /**
         * @see .ignoreAfterMaxFailures
         */
        const val SYSPROP_MAXFAILURES: String = "tests.maxfailures"

        /**
         * @see .ignoreAfterMaxFailures
         */
        const val SYSPROP_FAILFAST: String = "tests.failfast"

        /** Annotation for tests that should only be run during nightly builds.  */
        @MustBeDocumented
        // @java.lang.annotation.Inherited // TODO not possible with kotlin common code, need to walk around
        @Retention(AnnotationRetention.RUNTIME)
        @TestGroup(enabled = false, sysProperty = SYSPROP_NIGHTLY)
        annotation class Nightly
        // ↑ line 294 of LuceneTestCase.java



        // ↓ line 408
        // -----------------------------------------------------------------
        // Truly immutable fields and constants, initialized once and valid
        // for all suites ever since.
        // -----------------------------------------------------------------
        /**
         * True if and only if tests are run in verbose mode. If this flag is false tests are not expected
         * to print any messages. Enforced with [TestRuleLimitSysouts].
         */
        val VERBOSE: Boolean = systemPropertyAsBoolean("tests.verbose", false)

        // line 421
        /** Enables or disables dumping of [InfoStream] messages.  */
        val INFOSTREAM: Boolean = systemPropertyAsBoolean("tests.infostream", VERBOSE)

        /** Leave temporary files on disk, even on successful runs. */
        val LEAVE_TEMPORARY: Boolean = run {
            var defaultValue = false
            val props = listOf("tests.leaveTemporary", "tests.leavetemporary", "tests.leavetmpdir")
            for (property in props) {
                defaultValue = defaultValue || systemPropertyAsBoolean(property, false)
            }
            defaultValue
        }

        val TEST_ASSERTS_ENABLED: Boolean =
            systemPropertyAsBoolean(
                "tests.asserts",
                true
            )

        /**
         * The default (embedded resource) lines file.
         *
         * @see .TEST_LINE_DOCS_FILE
         */
        const val DEFAULT_LINE_DOCS_FILE: String = "europarl.lines.txt.gz"

        /**
         * Random sample from enwiki used in tests. See `help/tests.txt`. gradle task downloading
         * this data set: `gradlew getEnWikiRandomLines`.
         */
        const val JENKINS_LARGE_LINE_DOCS_FILE: String = "enwiki.random.lines.txt"

        /** Gets the codec to run tests with.  */
        val TEST_CODEC: String = System.getProperty("tests.codec", "random")!!

        /** Gets the postingsFormat to run tests with.  */
        val TEST_POSTINGSFORMAT: String =
            System.getProperty("tests.postingsformat", "random")!!

        /** Gets the docValuesFormat to run tests with  */
        val TEST_DOCVALUESFORMAT: String =
            System.getProperty("tests.docvaluesformat", "random")!!

        /** Gets the directory to run tests with  */
        val TEST_DIRECTORY: String = System.getProperty("tests.directory", "random")!!

        /** The line file used in tests (by [LineFileDocs]).  */
        val TEST_LINE_DOCS_FILE: String = System.getProperty(
            "tests.linedocsfile",
            DEFAULT_LINE_DOCS_FILE
        )!!

        /** Whether or not [Nightly] tests should run.  */
        val TEST_NIGHTLY: Boolean = systemPropertyAsBoolean(
            SYSPROP_NIGHTLY,
            /*Nightly::class.getAnnotation<A>(TestGroup::class)
                .enabled()*/ false
        )

        /** Whether or not [Weekly] tests should run.  */
        val TEST_WEEKLY: Boolean =
            systemPropertyAsBoolean(
                SYSPROP_WEEKLY,
                /*Weekly::class.java.getAnnotation<com.carrotsearch.randomizedtesting.annotations.TestGroup?>(
                    com.carrotsearch.randomizedtesting.annotations.TestGroup::class.java,
                ).enabled*/ false
            )

        /** Whether or not monster tests should run. */
        val TEST_MONSTER: Boolean = systemPropertyAsBoolean(SYSPROP_MONSTER, false)
        // ↑ line 469



        // line 484
        /**
         * A random multiplier which you should use when writing random tests: multiply it by the number
         * of iterations to scale your tests (for nightly builds).
         */
        val RANDOM_MULTIPLIER: Int = systemPropertyAsInt("tests.multiplier", defaultRandomMultiplier())

        /** Compute the default value of the random multiplier (based on [.TEST_NIGHTLY]).  */
        fun defaultRandomMultiplier(): Int {
            return if (TEST_NIGHTLY) 2 else 1
        }
        // line 490


        // line 507
        /** Filesystem-based [Directory] implementations.  */
        private val FS_DIRECTORIES: MutableList<String> = mutableListOf<String>("NIOFSDirectory")

        /** All [Directory] implementations.  */
        private val CORE_DIRECTORIES: MutableList<String> = FS_DIRECTORIES.plus(ByteBuffersDirectory::class.simpleName!!).toMutableList()

        // line 519
        /** Class environment setup rule.  */
        private var classEnvRule: TestRuleSetupAndRestoreClassEnv? = null

        private fun getClassEnvRule(): TestRuleSetupAndRestoreClassEnv {
            var rule = classEnvRule
            if (rule == null) {
                rule = TestRuleSetupAndRestoreClassEnv()
                classEnvRule = rule
            }
            if (rule.similarity == null) {
                rule.similarity = AssertingSimilarity(RandomSimilarity(random()))
            }
            return rule
        }


        /** A [QueryCachingPolicy] that randomly caches.  */
        val MAYBE_CACHE_POLICY: QueryCachingPolicy =
            object : QueryCachingPolicy {
                override fun onUse(query: Query) {}

                @Throws(IOException::class)
                override fun shouldCache(query: Query): Boolean {
                    return random().nextBoolean()
                }
            }
        // line 530

        internal const val TEMP_NAME_RETRY_THRESHOLD: Int = 9999

        /**
         * This is lucene-kmp original to walkaround the use of tempFilesCleanupRule
         */
        private val tempFilesCleanup = TempFilesCleanup()

        // line 618 of LuceneTestCase.java
        /** Returns random string, including full unicode range.  */
        fun randomUnicodeString(r: Random): String {
            return randomUnicodeString(r, 20)
        }

        /** Returns a random string up to a certain length.  */
        fun randomUnicodeString(r: Random, maxLength: Int): String {
            val end = TestUtil.nextInt(r, 0, maxLength)
            if (end == 0) {
                // allow 0 length
                return ""
            }
            val buffer = CharArray(end)
            randomFixedLengthUnicodeString(r, buffer, 0, buffer.size)
            return buffer.concatToString(0, 0 + end)
        }

        /** Fills provided char[] with valid random unicode code unit sequence.  */
        fun randomFixedLengthUnicodeString(
            random: Random, chars: CharArray, offset: Int, length: Int
        ) {
            var i = offset
            val end = offset + length
            while (i < end) {
                val t: Int = random.nextInt(5)
                if (0 == t && i < length - 1) {
                    // Make a surrogate pair
                    // High surrogate
                    chars[i++] = TestUtil.nextInt(random, 0xd800, 0xdbff).toChar()
                    // Low surrogate
                    chars[i++] = TestUtil.nextInt(random, 0xdc00, 0xdfff).toChar()
                } else if (t <= 1) {
                    chars[i++] = random.nextInt(0x80).toChar()
                } else if (2 == t) {
                    chars[i++] = TestUtil.nextInt(random, 0x80, 0x7ff).toChar()
                } else if (3 == t) {
                    chars[i++] = TestUtil.nextInt(random, 0x800, 0xd7ff).toChar()
                } else if (4 == t) {
                    chars[i++] = TestUtil.nextInt(random, 0xe000, 0xffff).toChar()
                }
            }
        }
        // line 657


        // line 674
        private val fieldToType: MutableMap<String, FieldType> = HashMap<String, FieldType>()


        // line 725

        // -----------------------------------------------------------------
        // Test facilities and facades for subclasses.
        // -----------------------------------------------------------------

        /**
         * Access to the current {@link RandomizedContext}'s Random instance. It is safe to use this
         * method from multiple threads, etc., but it should be called while within a runner's scope (so
         * no static initializers). The returned {@link Random} instance will be <b>different</b> when
         * this method is called inside a {@link BeforeClass} hook (static suite scope) and within {@link
         * Before}/ {@link After} hooks or test methods.
         *
         * <p>The returned instance must not be shared with other threads or cross a single scope's
         * boundary. For example, a {@link Random} acquired within a test method shouldn't be reused for
         * another test case.
         *
         * <p>There is an overhead connected with getting the {@link Random} for a particular context and
         * thread. It is better to cache the {@link Random} locally if tight loops with multiple
         * invocations are present or create a derivative local {@link Random} for millions of calls like
         * this:
         *
         * <pre>
         * Random random = new Random(random().nextLong());
         * // tight loop with many invocations.
         * </pre>
         */
        fun random(): Random {
            return Random.Default
        }



        // line 799 of LuceneTestCase.java
        /**
         * Some tests expect the directory to contain a single segment, and want to do tests on that
         * segment's reader. This is an utility method to help them.
         */
        /*
          public static SegmentReader getOnlySegmentReader(DirectoryReader reader) {
            List<LeafReaderContext> subReaders = reader.leaves();
            if (subReaders.size() != 1) {
              throw new IllegalArgumentException(reader + " has " + subReaders.size() + " segments instead of exactly one");
            }
            final LeafReader r = subReaders.get(0).reader();
            assertTrue("expected a SegmentReader but got " + r, r instanceof SegmentReader);
            return (SegmentReader) r;
          }
            */
        /**
         * Some tests expect the directory to contain a single segment, and want to do tests on that
         * segment's reader. This is an utility method to help them.
         */
        fun getOnlyLeafReader(reader: IndexReader): LeafReader {
            val subReaders: MutableList<LeafReaderContext> = reader.leaves()
            require(subReaders.size == 1) { reader.toString() + " has " + subReaders.size + " segments instead of exactly one" }
            return subReaders[0].reader()
        }

        /**
         * Returns true if and only if the calling thread is the primary thread executing the test case.
         */
        /*protected fun isTestThread(): Boolean {
            org.junit.Assert.assertNotNull(
                "Test case thread not set?",
                threadAndTestNameRule.testCaseThread
            )
            return java.lang.Thread.currentThread() === threadAndTestNameRule.testCaseThread
        }*/

        /**
         * Returns a number of at least `i`
         *
         *
         * The actual number returned will be influenced by whether [.TEST_NIGHTLY] is active and
         * [.RANDOM_MULTIPLIER], but also with some random fudge.
         */
        fun atLeast(random: Random, i: Int): Int {
            val min: Int = i * RANDOM_MULTIPLIER
            val max = min + (min / 2)
            return TestUtil.nextInt(random, min, max)
        }

        fun atLeast(i: Int): Int {
            return atLeast(Random, i)
        }

        /**
         * Returns true if something should happen rarely,
         *
         *
         * The actual number returned will be influenced by whether [.TEST_NIGHTLY] is active and
         * [.RANDOM_MULTIPLIER].
         */
        fun rarely(random: Random): Boolean {
            var p = if (TEST_NIGHTLY) 5 else 1
            p =
                (p + (p * ln(RANDOM_MULTIPLIER.toDouble()))).toInt()
            val min = 100 - min(p, 20) // never more than 20
            return random.nextInt(100) >= min
        }

        fun rarely(): Boolean {
            return rarely(random())
        }

        fun usually(random: Random): Boolean {
            return !rarely(random)
        }

        fun usually(): Boolean {
            return usually(random())
        }

        fun assumeTrue(msg: String, condition: Boolean) {
            RandomizedTest.assumeTrue(msg, condition)
        }

        fun assumeFalse(msg: String, condition: Boolean) {
            RandomizedTest.assumeFalse(msg, condition)
        }

        fun assumeNoException(msg: String, e: Exception) {
            RandomizedTest.assumeNoException(msg, e)
        }
        //↑ line 871 of LuceneTestCase.java


        // line 932 of LuceneTestCase.java
        /** create a new index writer config with random defaults  */
        fun newIndexWriterConfig(): IndexWriterConfig {
            return newIndexWriterConfig(MockAnalyzer(random()))
        }

        /** create a new index writer config with random defaults  */
        fun newIndexWriterConfig(a: Analyzer): IndexWriterConfig {
            return newIndexWriterConfig(random(), a)
        }

        /** create a new index writer config with random defaults using the specified random  */
        fun newIndexWriterConfig(
            r: Random,
            a: Analyzer
        ): IndexWriterConfig {
            val c = IndexWriterConfig(a)

            // TODO implement following later to improve tests
            /*c.setSimilarity(LuceneTestCase.classEnvRule.similarity)
            if (LuceneTestCase.VERBOSE) {
                // Even though TestRuleSetupAndRestoreClassEnv calls
                // InfoStream.setDefault, we do it again here so that
                // the PrintStreamInfoStream.messageID increments so
                // that when there are separate instances of
                // IndexWriter created we see "IW 0", "IW 1", "IW 2",
                // ... instead of just always "IW 0":
                c.setInfoStream(
                    ThreadNameFixingPrintStreamInfoStream(java.lang.System.out)
                )
            }

            if (LuceneTestCase.rarely(r)) {
                c.setMergeScheduler(SerialMergeScheduler())
            } else if (LuceneTestCase.rarely(r)) {
                val cms: ConcurrentMergeScheduler?
                if (r.nextBoolean()) {
                    cms = LuceneTestCase.TestConcurrentMergeScheduler()
                } else {
                    cms =
                        object : LuceneTestCase.TestConcurrentMergeScheduler() {

                            override fun maybeStall(mergeSource: MergeScheduler.MergeSource?): Boolean {
                                return true
                            }
                        }
                }
                val maxThreadCount: Int = TestUtil.nextInt(r, 1, 4)
                val maxMergeCount: Int =
                    TestUtil.nextInt(r, maxThreadCount, maxThreadCount + 4)
                cms.setMaxMergesAndThreads(maxMergeCount, maxThreadCount)
                if (random().nextBoolean()) {
                    cms.disableAutoIOThrottle()
                    assertFalse(cms.getAutoIOThrottle())
                }
                cms.setForceMergeMBPerSec(10 + 10 * random().nextDouble())
                c.setMergeScheduler(cms)
            } else {
                // Always use consistent settings, else CMS's dynamic (SSD or not)
                // defaults can change, hurting reproducibility:
                val cms: ConcurrentMergeScheduler =
                    if (randomBoolean()) LuceneTestCase.TestConcurrentMergeScheduler() else ConcurrentMergeScheduler()

                // Only 1 thread can run at once (should maybe help reproducibility),
                // with up to 3 pending merges before segment-producing threads are
                // stalled:
                cms.setMaxMergesAndThreads(3, 1)
                c.setMergeScheduler(cms)
            }

            if (r.nextBoolean()) {
                if (LuceneTestCase.rarely(r)) {
                    // crazy value
                    c.setMaxBufferedDocs(TestUtil.nextInt(r, 2, 15))
                } else {
                    // reasonable value
                    c.setMaxBufferedDocs(TestUtil.nextInt(r, 16, 1000))
                }
            }

            c.setMergePolicy(LuceneTestCase.newMergePolicy(r))

            if (LuceneTestCase.rarely(r)) {
                c.setMergedSegmentWarmer(SimpleMergedSegmentWarmer(c.getInfoStream()))
            }
            c.setUseCompoundFile(r.nextBoolean())
            c.setReaderPooling(r.nextBoolean())
            if (LuceneTestCase.rarely(r)) {
                c.setCheckPendingFlushUpdate(false)
            }

            if (LuceneTestCase.rarely(r)) {
                c.setIndexWriterEventListener(MockIndexWriterEventListener())
            }
            when (r.nextInt(3)) {
                0 ->         // Disable merge on refresh
                    c.setMaxFullFlushMergeWaitMillis(0L)

                1 ->         // Very low timeout, merges will likely not be able to run in time
                    c.setMaxFullFlushMergeWaitMillis(1L)

                else ->         // Very long timeout, merges will almost always be able to run in time
                    c.setMaxFullFlushMergeWaitMillis(1000L)
            }

            c.setMaxFullFlushMergeWaitMillis(
                (if (LuceneTestCase.rarely()) atLeast(
                    r,
                    1000
                ) else atLeast(r, 200)).toLong()
            )*/
            return c
        }

        fun newMergePolicy(r: Random): MergePolicy {
            return newMergePolicy(r, true)
        }

        fun newMergePolicy(
            r: Random,
            includeMockMP: Boolean
        ): MergePolicy {
            if (includeMockMP && rarely(r)) {
                return MockRandomMergePolicy(r)
            } else if (r.nextBoolean()) {
                return newTieredMergePolicy(r)
            } else if (rarely(r)) {
                return newAlcoholicMergePolicy(
                    r,
                    classEnvRule!!.timeZone!!
                )
            }
            return newLogMergePolicy(r)
        }

        fun newMergePolicy(): MergePolicy {
            return newMergePolicy(random())
        }

        fun newLogMergePolicy(): LogMergePolicy {
            return newLogMergePolicy(random())
        }

        fun newTieredMergePolicy(): TieredMergePolicy {
            return newTieredMergePolicy(random())
        }

        fun newAlcoholicMergePolicy(): AlcoholicMergePolicy {
            return newAlcoholicMergePolicy(
                random(),
                classEnvRule!!.timeZone!!
            )
        }

        fun newAlcoholicMergePolicy(
            r: Random,
            tz: TimeZone
        ): AlcoholicMergePolicy {
            return AlcoholicMergePolicy(tz, Random(r.nextLong()))
        }

        fun newLogMergePolicy(r: Random): LogMergePolicy {
            val logmp: LogMergePolicy =
                if (r.nextBoolean()) LogDocMergePolicy() else LogByteSizeMergePolicy()
            logmp.calibrateSizeByDeletes = r.nextBoolean()
            logmp.targetSearchConcurrency = TestUtil.nextInt(random(), 1, 16)
            if (rarely(r)) {
                logmp.mergeFactor = TestUtil.nextInt(r, 2, 9)
            } else {
                logmp.mergeFactor = TestUtil.nextInt(r, 10, 50)
            }
            configureRandom(r, logmp)
            return logmp
        }

        private fun configureRandom(
            r: Random,
            mergePolicy: MergePolicy
        ) {
            if (r.nextBoolean()) {
                mergePolicy.noCFSRatio = 0.1 + r.nextDouble() * 0.8
            } else {
                mergePolicy.noCFSRatio = if (r.nextBoolean()) 1.0 else 0.0
            }

            if (rarely(r)) {
                mergePolicy.maxCFSSegmentSizeMB = 0.2 + r.nextDouble() * 2.0
            } else {
                mergePolicy.maxCFSSegmentSizeMB = Double.POSITIVE_INFINITY
            }
        }

        fun newTieredMergePolicy(r: Random): TieredMergePolicy {
            val tmp = TieredMergePolicy()
            if (rarely(r)) {
                tmp.setMaxMergedSegmentMB(0.2 + r.nextDouble() * 2.0)
            } else {
                tmp.setMaxMergedSegmentMB(10 + r.nextDouble() * 100)
            }
            tmp.setFloorSegmentMB(0.2 + r.nextDouble() * 2.0)
            tmp.setForceMergeDeletesPctAllowed(0.0 + r.nextDouble() * 30.0)
            if (rarely(r)) {
                tmp.setSegmentsPerTier(
                    TestUtil.nextInt(r, 2, 20).toDouble()
                )
            } else {
                tmp.setSegmentsPerTier(
                    TestUtil.nextInt(r, 10, 50).toDouble()
                )
            }
            if (rarely(r)) {
                tmp.setTargetSearchConcurrency(
                    TestUtil.nextInt(
                        r,
                        10,
                        50
                    )
                )
            } else {
                tmp.setTargetSearchConcurrency(
                    TestUtil.nextInt(
                        r,
                        2,
                        20
                    )
                )
            }

            configureRandom(r, tmp)
            tmp.setDeletesPctAllowed(
                20 + random().nextDouble() * 30
            )
            return tmp
        }

        fun newLogMergePolicy(useCFS: Boolean): MergePolicy {
            val logmp: MergePolicy = newLogMergePolicy()
            logmp.noCFSRatio = if (useCFS) 1.0 else 0.0
            return logmp
        }

        fun newLogMergePolicy(useCFS: Boolean, mergeFactor: Int): LogMergePolicy {
            val logmp: LogMergePolicy = newLogMergePolicy()
            logmp.noCFSRatio = if (useCFS) 1.0 else 0.0
            logmp.mergeFactor = mergeFactor
            return logmp
        }

        fun newLogMergePolicy(mergeFactor: Int): LogMergePolicy {
            val logmp: LogMergePolicy = newLogMergePolicy()
            logmp.mergeFactor = mergeFactor
            return logmp
        }


        // if you want it in LiveIndexWriterConfig: it must and will be tested here.
        fun maybeChangeLiveIndexWriterConfig(r: Random, c: LiveIndexWriterConfig) {
            var didChange = false

            val previous = c.toString()

            // TODO implement following later to improve tests
            /*if (LuceneTestCase.rarely(r)) {
                // change flush parameters:
                // this is complicated because the api requires you "invoke setters in a magical order!"
                // LUCENE-5661: workaround for race conditions in the API

                // TODO synchronized is not supported in kotlin multiplatform common code, need walk around
                //synchronized(c) {
                    val flushByRAM: Boolean
                    when (LuceneTestCase.liveIWCFlushMode) {
                        LiveIWCFlushMode.BY_RAM -> flushByRAM = true
                        LiveIWCFlushMode.BY_DOCS -> flushByRAM = false
                        LiveIWCFlushMode.EITHER -> flushByRAM = r.nextBoolean()
                        else -> throw AssertionError()
                    }
                    if (flushByRAM) {
                        c.setRAMBufferSizeMB(TestUtil.nextInt(r, 1, 10).toDouble())
                        c.setMaxBufferedDocs(IndexWriterConfig.DISABLE_AUTO_FLUSH)
                    } else {
                        if (LuceneTestCase.rarely(r)) {
                            // crazy value
                            c.setMaxBufferedDocs(TestUtil.nextInt(r, 2, 15))
                        } else {
                            // reasonable value
                            c.setMaxBufferedDocs(TestUtil.nextInt(r, 16, 1000))
                        }
                        c.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH.toDouble())
                    }
                //}
                didChange = true
            }

            if (LuceneTestCase.rarely(r)) {
                val curWarmer: org.apache.lucene.index.IndexWriter.IndexReaderWarmer? = c.getMergedSegmentWarmer()
                if (curWarmer == null || curWarmer is SimpleMergedSegmentWarmer) {
                    // change warmer parameters
                    if (r.nextBoolean()) {
                        c.setMergedSegmentWarmer(SimpleMergedSegmentWarmer(c.getInfoStream()))
                    } else {
                        c.setMergedSegmentWarmer(null)
                    }
                }
                didChange = true
            }

            if (LuceneTestCase.rarely(r)) {
                // change CFS flush parameters
                c.setUseCompoundFile(r.nextBoolean())
                didChange = true
            }

            if (LuceneTestCase.rarely(r)) {
                // change CMS merge parameters
                val ms: MergeScheduler? = c.getMergeScheduler()
                if (ms is ConcurrentMergeScheduler) {
                    val maxThreadCount: Int = TestUtil.nextInt(r, 1, 4)
                    val maxMergeCount: Int =
                        TestUtil.nextInt(r, maxThreadCount, maxThreadCount + 4)
                    val enableAutoIOThrottle: Boolean =
                        random().nextBoolean()
                    if (enableAutoIOThrottle) {
                        ms.enableAutoIOThrottle()
                    } else {
                        ms.disableAutoIOThrottle()
                    }
                    ms.setMaxMergesAndThreads(maxMergeCount, maxThreadCount)
                    didChange = true
                }
            }

            if (LuceneTestCase.rarely(r)) {
                val mp: MergePolicy = c.getMergePolicy()
                LuceneTestCase.configureRandom(r, mp)
                if (mp is LogMergePolicy) {
                    mp.setCalibrateSizeByDeletes(r.nextBoolean())
                    if (LuceneTestCase.rarely(r)) {
                        mp.mergeFactor = TestUtil.nextInt(r, 2, 9)
                    } else {
                        mp.mergeFactor = TestUtil.nextInt(r, 10, 50)
                    }
                } else if (mp is TieredMergePolicy) {
                    if (LuceneTestCase.rarely(r)) {
                        mp.setMaxMergedSegmentMB(0.2 + r.nextDouble() * 2.0)
                    } else {
                        mp.setMaxMergedSegmentMB(r.nextDouble() * 100)
                    }
                    mp.setFloorSegmentMB(0.2 + r.nextDouble() * 2.0)
                    mp.setForceMergeDeletesPctAllowed(0.0 + r.nextDouble() * 30.0)
                    if (LuceneTestCase.rarely(r)) {
                        mp.setSegmentsPerTier(TestUtil.nextInt(r, 2, 20).toDouble())
                    } else {
                        mp.setSegmentsPerTier(TestUtil.nextInt(r, 10, 50).toDouble())
                    }
                    LuceneTestCase.configureRandom(r, mp)
                    mp.setDeletesPctAllowed(20 + LuceneTestCase.random().nextDouble() * 30)
                }
                didChange = true
            }*/
            if (VERBOSE && didChange) {
                val current = c.toString()
                val previousLines: Array<String?> =
                    previous.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val currentLines: Array<String?> =
                    current.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val diff = StringBuilder()

                // this should always be the case, diff each line
                if (previousLines.size == currentLines.size) {
                    for (i in previousLines.indices) {
                        if (previousLines[i] != currentLines[i]) {
                            diff.append("- ").append(previousLines[i]).append("\n")
                            diff.append("+ ").append(currentLines[i]).append("\n")
                        }
                    }
                } else {
                    // but just in case of something ridiculous...
                    diff.append(current)
                }

                // its possible to be empty, if we "change" a value to what it had before.
                if (diff.isNotEmpty()) {
                    println("NOTE: LuceneTestCase: randomly changed IWC's live settings:")
                    println(diff)
                }
            }
        }

        // line 1276
        /**
         * Returns a new Directory instance. Use this when the test does not care about the specific
         * Directory implementation (most tests).
         *
         *
         * The Directory is wrapped with [BaseDirectoryWrapper]. this means usually it will be
         * picky, such as ensuring that you properly close it and all open files in your test. It will
         * emulate some features of Windows, such as not allowing open files to be overwritten.
         */
        fun newDirectory(): BaseDirectoryWrapper {
            return newDirectory(random())
        }

        /** Like [.newDirectory] except randomly the [VirusCheckingFS] may be installed  */
        /*fun newMaybeVirusCheckingDirectory(): BaseDirectoryWrapper {
            if (random().nextInt(5) == 4) {
                val path: Path =
                    LuceneTestCase.addVirusChecker(LuceneTestCase.createTempDir())
                return newFSDirectory(path)
            } else {
                return newDirectory(random())
            }
        }*/

        /**
         * Returns a new Directory instance, using the specified random. See [.newDirectory] for
         * more information.
         */
        fun newDirectory(r: Random): BaseDirectoryWrapper {
            return wrapDirectory(
                r,
                newDirectoryImpl(
                    r,
                    TEST_DIRECTORY
                ),
                rarely(r),
                false
            )
        }

        fun newMockDirectory(): MockDirectoryWrapper {
            return newMockDirectory(random())
        }

        fun newMockDirectory(r: Random): MockDirectoryWrapper {
            return wrapDirectory(
                r,
                newDirectoryImpl(
                    r,
                    TEST_DIRECTORY
                ),
                false,
                false
            ) as MockDirectoryWrapper
        }

        fun newMockDirectory(
            r: Random,
            lf: LockFactory
        ): MockDirectoryWrapper {
            return wrapDirectory(
                r,
                newDirectoryImpl(
                    r,
                    TEST_DIRECTORY,
                    lf
                ),
                false,
                false
            ) as MockDirectoryWrapper
        }

        fun newMockFSDirectory(f: Path): MockDirectoryWrapper {
            return newFSDirectory(
                f,
                FSLockFactory.default,
                false
            ) as MockDirectoryWrapper
        }

        fun newMockFSDirectory(
            f: Path,
            lf: LockFactory
        ): MockDirectoryWrapper {
            return newFSDirectory(
                f,
                lf,
                false
            ) as MockDirectoryWrapper
        }

        /*fun addVirusChecker(path: Path): Path {
            var path: Path = path
            if (TestUtil.hasVirusChecker(path) == false) {
                val fs: VirusCheckingFS = VirusCheckingFS(
                    path.getFileSystem(),
                    .random().nextLong()
                )
                path = fs.wrapPath(path)
            }
            return path
        }*/

        /**
         * Returns a new Directory instance, with contents copied from the provided directory. See [ ][.newDirectory] for more information.
         */
        @Throws(IOException::class)
        fun newDirectory(d: Directory): BaseDirectoryWrapper {
            return newDirectory(
                random(),
                d
            )
        }

        /** Returns a new FSDirectory instance over the given file, which must be a folder.  */
        fun newFSDirectory(f: Path): BaseDirectoryWrapper {
            return newFSDirectory(f, FSLockFactory.default)
        }

        /** Like [.newFSDirectory], but randomly insert [VirusCheckingFS]  */
        /*fun newMaybeVirusCheckingFSDirectory(f: Path): BaseDirectoryWrapper {
            var f: Path = f
            if (.random().nextInt(5) == 4) {
                f = addVirusChecker(f)
            }
            return newFSDirectory(f, FSLockFactory.default)
        }*/

        /** Returns a new FSDirectory instance over the given file, which must be a folder.  */
        fun newFSDirectory(
            f: Path,
            lf: LockFactory
        ): BaseDirectoryWrapper {
            return newFSDirectory(f, lf, rarely())
        }

        private fun newFSDirectory(
            f: Path,
            lf: LockFactory,
            bare: Boolean
        ): BaseDirectoryWrapper {
            var fsdirClass: String = TEST_DIRECTORY
            if (fsdirClass == "random") {
                fsdirClass =
                    RandomPicks.randomFrom(
                        random(),
                        FS_DIRECTORIES
                    )
            }

            var clazz: KClass<out FSDirectory>
            try {
                try {
                    clazz = CommandLineUtil.loadFSDirectoryClass(fsdirClass)
                } catch (e: ClassCastException) {
                    // TEST_DIRECTORY is not a sub-class of FSDirectory, so draw one at random
                    fsdirClass =
                        RandomPicks.randomFrom(
                            random(),
                            FS_DIRECTORIES
                        )
                    clazz = CommandLineUtil.loadFSDirectoryClass(fsdirClass)
                }

                val fsdir: Directory =
                    newFSDirectoryImpl(clazz, f, lf)
                return wrapDirectory(
                    random(),
                    fsdir,
                    bare,
                    true
                )
            } catch (e: Exception) {
                Rethrow.rethrow(e)
                throw e // dummy to prevent compiler failure
            }
        }

        /*private fun newFileSwitchDirectory(
            random: Random,
            dir1: Directory?,
            dir2: Directory?
        ): Directory {
            var fileExtensions = mutableListOf<String?>(
                "fdt", "fdx", "tim", "tip", "si", "fnm", "pos", "dii", "dim", "nvm", "nvd", "dvm",
                "dvd"
            )
            fileExtensions.shuffle(random)
            fileExtensions = fileExtensions.subList(0, 1 + random.nextInt(fileExtensions.size))
            return FileSwitchDirectory(HashSet<String?>(fileExtensions), dir1, dir2, true)
        }*/

        /**
         * Returns a new Directory instance, using the specified random with contents copied from the
         * provided directory. See [.newDirectory] for more information.
         */
        @Throws(IOException::class)
        fun newDirectory(
            r: Random,
            d: Directory
        ): BaseDirectoryWrapper {
            val impl: Directory =
                newDirectoryImpl(
                    r,
                    TEST_DIRECTORY
                )
            for (file in d.listAll()) {
                if (file.startsWith(IndexFileNames.SEGMENTS)
                    || IndexFileNames.CODEC_FILE_PATTERN.matches(file)
                ) {
                    impl.copyFrom(
                        d,
                        file,
                        file,
                        newIOContext(r)
                    )
                }
            }
            return wrapDirectory(
                r,
                impl,
                rarely(r),
                false
            )
        }

        private fun wrapDirectory(
            random: Random,
            directory: Directory,
            bare: Boolean,
            filesystem: Boolean
        ): BaseDirectoryWrapper {
            // IOContext randomization might make NRTCachingDirectory make bad decisions, so avoid
            // using it if the user requested a filesystem directory.
            var directory: Directory = directory
            /*if (rarely(random) && !bare && filesystem == false) {
                directory = NRTCachingDirectory(directory, random.nextDouble(), random.nextDouble())
            }*/

            if (bare) {
                val base: BaseDirectoryWrapper = RawDirectoryWrapper(directory)

                // TODO do something if it breaks without following
                /*LuceneTestCase.closeAfterSuite<CloseableDirectory>(
                    CloseableDirectory(
                        base,
                        LuceneTestCase.suiteFailureMarker
                    )
                )*/
                return base
            } else {
                val mock = MockDirectoryWrapper(random, directory)

                // TODO do something if it breaks without following
                /*mock.setThrottling(LuceneTestCase.TEST_THROTTLING)
                LuceneTestCase.closeAfterSuite<CloseableDirectory>(
                    CloseableDirectory(
                        mock,
                        LuceneTestCase.suiteFailureMarker
                    )
                )*/
                return mock
            }
        }

        fun newStringField(
            name: String,
            value: String?,
            stored: Field.Store
        ): Field {
            return newField(
                random(),
                name,
                value,
                if (stored == Field.Store.YES) StringField.TYPE_STORED else StringField.TYPE_NOT_STORED
            )
        }

        fun newStringField(
            name: String,
            value: BytesRef?,
            stored: Field.Store
        ): Field {
            return newField(
                random(),
                name,
                value,
                if (stored == Field.Store.YES) StringField.TYPE_STORED else StringField.TYPE_NOT_STORED
            )
        }

        fun newTextField(
            name: String,
            value: String?,
            stored: Field.Store
        ): Field {
            return newField(
                random(),
                name,
                value,
                if (stored == Field.Store.YES) TextField.TYPE_STORED else TextField.TYPE_NOT_STORED
            )
        }

        fun newStringField(
            random: Random,
            name: String,
            value: String?,
            stored: Field.Store
        ): Field {
            return newField(
                random,
                name,
                value,
                if (stored == Field.Store.YES) StringField.TYPE_STORED else StringField.TYPE_NOT_STORED
            )
        }

        fun newStringField(
            random: Random,
            name: String,
            value: BytesRef?,
            stored: Field.Store
        ): Field {
            return newField(
                random,
                name,
                value,
                if (stored == Field.Store.YES) StringField.TYPE_STORED else StringField.TYPE_NOT_STORED
            )
        }

        fun newTextField(
            random: Random,
            name: String,
            value: String?,
            stored: Field.Store
        ): Field {
            return newField(
                random,
                name,
                value,
                if (stored == Field.Store.YES) TextField.TYPE_STORED else TextField.TYPE_NOT_STORED
            )
        }

        fun newField(
            name: String,
            value: String?,
            type: FieldType
        ): Field {
            return newField(random(), name, value, type)
        }

        // TODO: if we can pull out the "make term vector options
        // consistent across all instances of the same field name"
        // write-once schema sort of helper class then we can
        // remove the sync here.  We can also fold the random
        // "enable norms" (now commented out, below) into that:
        /*@Synchronized*/
        fun newField(
            random: Random,
            name: String,
            value: Any?,
            type: FieldType
        ): Field {
            // Defeat any consumers that illegally rely on intern'd
            // strings (we removed this from Lucene a while back):

            val name = name
            /*name = java.lang.String(name) as String*/

            val prevType: FieldType? = fieldToType[name]

            if (prevType != null) {
                // always use the same fieldType for the same field name
                return createField(name, value, prevType)
            }

            // TODO: once all core & test codecs can index
            // offsets, sometimes randomly turn on offsets if we are
            // already indexing positions...
            val newType = FieldType(type)
            if (!newType.stored() && random.nextBoolean()) {
                newType.setStored(true) // randomly store it
            }
            if (newType.indexOptions() != IndexOptions.NONE) {
                if (!newType.storeTermVectors() && random.nextBoolean()) {
                    newType.setStoreTermVectors(true)
                    if (!newType.storeTermVectorPositions()) {
                        newType.setStoreTermVectorPositions(random.nextBoolean())
                        if (newType.storeTermVectorPositions()) {
                            if (!newType.storeTermVectorPayloads()) {
                                newType.setStoreTermVectorPayloads(random.nextBoolean())
                            }
                        }
                    }
                    // Check for strings as offsets are disallowed on binary fields
                    if (value is String && !newType.storeTermVectorOffsets()) {
                        newType.setStoreTermVectorOffsets(random.nextBoolean())
                    }

                    if (VERBOSE) {
                        println("NOTE: LuceneTestCase: upgrade name=$name type=$newType")
                    }
                }
            }
            newType.freeze()
            fieldToType[name] = newType

            // TODO: we need to do this, but smarter, ie, most of
            // the time we set the same value for a given field but
            // sometimes (rarely) we change it up:
            /*
            if (newType.omitNorms()) {
              newType.setOmitNorms(random.nextBoolean());
            }
            */
            return createField(name, value, newType)
        }

        private fun createField(
            name: String,
            value: Any?,
            fieldType: FieldType
        ): Field {
            if (value is String) {
                return Field(name, value, fieldType)
            } else if (value is BytesRef) {
                return Field(
                    name,
                    value,
                    fieldType
                )
            } else {
                throw IllegalArgumentException("value must be String or BytesRef")
            }
        }
        // line 1565


        // line 1610
        @Throws(IOException::class)
        private fun newFSDirectoryImpl(
            clazz: KClass<out FSDirectory>,
            path: Path,
            lf: LockFactory
        ): Directory {
            var d: FSDirectory? = null
            try {
                d = CommandLineUtil.newFSDirectory(clazz, path, lf)
            } catch (e: /*java.lang.ReflectiveOperation*/Exception) {
                Rethrow.rethrow(e)
            }
            return d!!
        }

        fun newDirectoryImpl(
            random: Random,
            clazzName: String
        ): Directory {
            return newDirectoryImpl(
                random,
                clazzName,
                FSLockFactory.default
            )
        }

        fun newDirectoryImpl(
            random: Random,
            clazzName: String,
            lf: LockFactory
        ): Directory {
            var clazzName = clazzName
            if (clazzName == "random") {
                if (rarely(random)) {
                    clazzName =
                        RandomPicks.randomFrom(
                            random,
                            CORE_DIRECTORIES
                        )
                } /*else if (rarely(random)) {
                    val clazzName1: String =
                        if (rarely(random))
                            RandomPicks.randomFrom(
                                random,
                                CORE_DIRECTORIES
                            )
                        else
                            ByteBuffersDirectory::class.simpleName!!
                    val clazzName2: String =
                        if (rarely(random))
                            RandomPicks.randomFrom<String>(
                                random,
                                CORE_DIRECTORIES
                            )
                        else
                            ByteBuffersDirectory::class.simpleName!!
                    return LuceneTestCase.newFileSwitchDirectory(
                        random,
                        newDirectoryImpl(random, clazzName1, lf),
                        newDirectoryImpl(random, clazzName2, lf)
                    )
                } */else {
                    clazzName = ByteBuffersDirectory::class.simpleName!!
                }
            }

            try {
                /*val clazz: KClass<out Directory> =
                    CommandLineUtil.loadDirectoryClass(clazzName)*/
                // If it is a FSDirectory type, try its ctor(Path)
                /*if (FSDirectory::class.isAssignableFrom(clazz)) {
                    val dir: Path =
                        LuceneTestCase.createTempDir("index-$clazzName")
                    return newFSDirectoryImpl(
                        clazz.asSubclass<FSDirectory>(
                            FSDirectory::class
                        ), dir, lf
                    )
                }*/

                // See if it has a Path/LockFactory ctor even though it's not an
                // FSDir subclass:
                /*try {
                    val pathCtor: java.lang.reflect.Constructor<out Directory> =
                        clazz.getConstructor(
                            Path::class,
                            LockFactory::class
                        )
                    val dir: Path? =
                        LuceneTestCase.createTempDir("index")
                    return pathCtor.newInstance(dir, lf)
                } catch (nsme: NoSuchMethodException) {
                    // Ignore
                }*/

                // the remaining dirs are no longer filesystem based, so we must check that the
                // passedLockFactory is not file based:
                /*if (lf !is FSLockFactory) {
                    // try ctor with only LockFactory
                    try {
                        //"NIOFSDirectory", "MMapDirectory"
                        return clazz.getConstructor(LockFactory::class).newInstance(lf)
                    } catch (nsme: *//*NoSuchMethod*//*Exception) {
                        // Ignore
                    }
                }*/

                // try empty ctor
                //return clazz.getConstructor().newInstance()


            } catch (e: Exception) {
                Rethrow.rethrow(e)
                /*throw null*/ // dummy to prevent compiler failure
            }

            // TODO following is walkaround to bypass above
            return when (clazzName) {
                "ByteBuffersDirectory" -> {
                    if (lf is FSLockFactory) {
                        ByteBuffersDirectory()
                    } else {
                        ByteBuffersDirectory(lockFactory = lf)
                    }
                }
                "NIOFSDirectory" -> {
                    val dir: Path = createTempDir("index-$clazzName")
                    NIOFSDirectory(path = dir, lockFactory = lf)
                }
                "MMapDirectory" -> {
                    val dir: Path = createTempDir("index-$clazzName")
                    MMapDirectory(path = dir)
                }
                else -> throw UnsupportedOperationException("Unsupported directory: $clazzName")
            }
        }

        @Throws(IOException::class)
        fun wrapReader(r: IndexReader): IndexReader {
            var r: IndexReader = r
            val random: Random = random()

            var i = 0
            val c: Int = random.nextInt(6) + 1
            while (i < c) {
                when (random.nextInt(5)) {
                    0 -> {
                        // will create no FC insanity in atomic case, as ParallelLeafReader has own cache key:
                        if (VERBOSE) {
                            println("NOTE: LuceneTestCase.wrapReader: wrapping previous reader=$r with ParallelLeaf/CompositeReader")
                        }
                        r = if (r is LeafReader)
                                ParallelLeafReader(r)
                            else
                                ParallelCompositeReader(r as CompositeReader)
                    }

                    1 -> if (r is LeafReader) {
                        val allFields: MutableList<String> = mutableListOf()
                        for (fi in r.fieldInfos) {
                            allFields.add(fi.name)
                        }
                        allFields.shuffle(random)
                        val end = if (allFields.isEmpty()) 0 else random.nextInt(allFields.size)
                        val fields: MutableSet<String> = HashSet(allFields.subList(0, end))
                        // will create no FC insanity as ParallelLeafReader has own cache key:
                        if (VERBOSE) {
                            println("NOTE: LuceneTestCase.wrapReader: wrapping previous reader=$r with ParallelLeafReader")
                        }
                        r = ParallelLeafReader(
                                FieldFilterLeafReader(r, fields, false),
                                FieldFilterLeafReader(r, fields, true)
                            )
                    }

                    2 -> {
                        // Häckidy-Hick-Hack: a standard Reader will cause FC insanity, so we use
                        // QueryUtils' reader with a fake cache key, so insanity checker cannot walk
                        // along our reader:
                        if (VERBOSE) {
                            println(
                                ("NOTE: LuceneTestCase.wrapReader: wrapping previous reader="
                                        + r
                                        + " with AssertingLeaf/DirectoryReader")
                            )
                        }
                        if (r is LeafReader) {
                            r = AssertingLeafReader(r)
                        } else if (r is DirectoryReader) {
                            r = AssertingDirectoryReader(r)
                        }
                    }

                    3 -> {
                        if (VERBOSE) {
                            println(
                                ("NOTE: LuceneTestCase.wrapReader: wrapping previous reader="
                                        + r
                                        + " with MismatchedLeaf/Directory/CodecReader")
                            )
                        }
                        if (r is LeafReader) {
                            r = MismatchedLeafReader(r, random)
                        } else if (r is DirectoryReader) {
                            r = MismatchedDirectoryReader(r, random)
                        } else if (r is CodecReader) {
                            r = MismatchedCodecReader(r, random)
                        }
                    }

                    4 -> {
                        if (VERBOSE) {
                            println(
                                ("NOTE: LuceneTestCase.wrapReader: wrapping previous reader="
                                        + r
                                        + " with MergingCodecReader")
                            )
                        }
                        if (r is CodecReader) {
                            r = MergingCodecReader(r)
                        } else if (r is DirectoryReader) {
                            var allLeavesAreCodecReaders = true
                            for (ctx in r.leaves()) {
                                if (ctx.reader() is CodecReader == false) {
                                    allLeavesAreCodecReaders = false
                                    break
                                }
                            }
                            if (allLeavesAreCodecReaders) {
                                r = MergingDirectoryReaderWrapper(r)
                            }
                        }
                    }

                    else -> fail("should not get here")
                }
                i++
            }

            if (VERBOSE) {
                println("wrapReader wrapped: $r")
            }

            return r
        }

        /** Sometimes wrap the IndexReader as slow, parallel or filter reader (or combinations of that)  */
        @Throws(IOException::class)
        fun maybeWrapReader(r: IndexReader): IndexReader {
            var r: IndexReader = r
            if (rarely()) {
                r = wrapReader(r)
            }
            return r
        }

        /** TODO: javadoc  */
        fun newIOContext(random: Random): IOContext {
            return newIOContext(random, IOContext.DEFAULT)
        }

        /** TODO: javadoc  */
        fun newIOContext(
            random: Random,
            oldContext: IOContext
        ): IOContext {
            if (oldContext === IOContext.READONCE) {
                return oldContext // don't mess with the READONCE singleton
            }
            val randomNumDocs: Int = random.nextInt(4192)
            val size: Int = random.nextInt(512) * randomNumDocs
            if (oldContext.flushInfo != null) {
                // Always return at least the estimatedSegmentSize of
                // the incoming IOContext:
                return IOContext(
                    FlushInfo(
                        randomNumDocs, max(oldContext.flushInfo!!.estimatedSegmentSize, size.toLong())
                    )
                )
            } else if (oldContext.mergeInfo != null) {
                // Always return at least the estimatedMergeBytes of
                // the incoming IOContext:
                return IOContext(
                    MergeInfo(
                        randomNumDocs,
                        max(oldContext.mergeInfo!!.estimatedMergeBytes, size.toLong()),
                        random.nextBoolean(),
                        TestUtil.nextInt(random, 1, 100)
                    )
                )
            } else {
                // Make a totally random IOContext, except READONCE which has semantic implications
                val context: IOContext
                when (random.nextInt(3)) {
                    0 -> context = IOContext.DEFAULT
                    1 -> context = IOContext(
                        MergeInfo(
                            randomNumDocs,
                            size.toLong(),
                            true,
                            -1
                        )
                    )

                    2 -> context = IOContext(
                        FlushInfo(
                            randomNumDocs,
                            size.toLong()
                        )
                    )

                    else -> context = IOContext.DEFAULT
                }
                return context
            }
        }
        // line 1847


        //↓ line 1888 of LuceneTestCase.java
        private var executor: ExecutorService? = null

        /*@org.junit.BeforeClass*/
        fun setUpExecutorService() {
            val threads: Int = TestUtil.nextInt(
                random(),
                1,
                2
            )
            executor =
                ThreadPoolExecutor(
                    threads,
                    threads,
                    0L,
                    TimeUnit.MILLISECONDS,
                    LinkedBlockingQueue(),
                    NamedThreadFactory("LuceneTestCase")
                )
            // uncomment to intensify LUCENE-3840
            // executor.prestartAllCoreThreads();
            if (VERBOSE) {
                println("NOTE: Created shared ExecutorService with $threads threads")
            }
        }

        /*@org.junit.AfterClass*/
        fun shutdownExecutorService() {
            TestUtil.shutdownExecutorService(executor!!)
            executor = null
        }

        /** Create a new searcher over the reader. This searcher might randomly use threads.  */
        fun newSearcher(r: IndexReader): IndexSearcher {
            return newSearcher(r, true)
        }

        /** Create a new searcher over the reader. This searcher might randomly use threads.  */
        fun newSearcher(
            r: IndexReader,
            maybeWrap: Boolean
        ): IndexSearcher {
            return newSearcher(r, maybeWrap, true)
        }

        /**
         * Create a new searcher over the reader. This searcher might randomly use threads. if `
         * maybeWrap` is true, this searcher might wrap the reader with one that returns null for
         * getSequentialSubReaders. If `wrapWithAssertions` is true, this searcher might be an
         * [AssertingIndexSearcher] instance.
         */
        fun newSearcher(
            r: IndexReader, maybeWrap: Boolean, wrapWithAssertions: Boolean
        ): IndexSearcher {
            return newSearcher(
                r,
                maybeWrap,
                wrapWithAssertions,
                RandomizedTest.randomBoolean()
            )
        }

        /**
         * Create a new searcher over the reader. If `
         * maybeWrap` is true, this searcher might wrap the reader with one that returns null for
         * getSequentialSubReaders. If `wrapWithAssertions` is true, this searcher might be an
         * [AssertingIndexSearcher] instance. The searcher will use threads if `useThreads
        ` *  is set to true.
         */
        fun newSearcher(
            r: IndexReader,
            maybeWrap: Boolean,
            wrapWithAssertions: Boolean,
            useThreads: Boolean
        ): IndexSearcher {
            if (useThreads) {
                return newSearcher(
                    r,
                    maybeWrap,
                    wrapWithAssertions,
                    Concurrency.INTRA_SEGMENT
                )
            }
            return newSearcher(
                r,
                maybeWrap,
                wrapWithAssertions,
                Concurrency.NONE
            )
        }

        /** What level of concurrency is supported by the searcher being created  */
        enum class Concurrency {
            /** No concurrency, meaning an executor won't be provided to the searcher  */
            NONE,

            /**
             * Inter-segment concurrency, meaning an executor will be provided to the searcher and slices
             * will be randomly created to concurrently search entire segments
             */
            INTER_SEGMENT,

            /**
             * Intra-segment concurrency, meaning an executor will be provided to the searcher and slices
             * will be randomly created to concurrently search segment partitions
             */
            INTRA_SEGMENT
        }

        fun newSearcher(
            r: IndexReader,
            maybeWrap: Boolean,
            wrapWithAssertions: Boolean,
            concurrency: Concurrency?
        ): IndexSearcher {
            var r: IndexReader = r
            val random: Random = random()
            if (concurrency == Concurrency.NONE) {
                if (maybeWrap) {
                    try {
                        r = maybeWrapReader(r)
                    } catch (e: IOException) {
                        Rethrow.rethrow(e)
                    }
                }
                // TODO: this whole check is a coverage hack, we should move it to tests for various
                // filterreaders.
                // ultimately whatever you do will be checkIndex'd at the end anyway.
                if (random.nextInt(500) == 0 && r is LeafReader) {
                    // TODO: not useful to check DirectoryReader (redundant with checkindex)
                    // but maybe sometimes run this on the other crazy readers maybeWrapReader creates?
                    try {
                        TestUtil.checkReader(r)
                    } catch (e: IOException) {
                        Rethrow.rethrow(e)
                    }
                }
                val ret: IndexSearcher
                if (wrapWithAssertions) {
                    ret =
                        if (random.nextBoolean())
                            AssertingIndexSearcher(random, r)
                        else
                            AssertingIndexSearcher(random, r.context)
                } else {
                    ret =
                        if (random.nextBoolean()) IndexSearcher(r) else IndexSearcher(
                            r.context
                        )
                }
                ret.similarity = getClassEnvRule().similarity!!
                return ret
            } else {
                val ex: ExecutorService?
                if (random.nextBoolean()) {
                    ex = null
                } else {
                    ex = executor
                    if (VERBOSE) {
                        println("NOTE: newSearcher using shared ExecutorService")
                    }
                }
                val ret: IndexSearcher
                val maxDocPerSlice = if (random.nextBoolean()) 1 else 1 + random.nextInt(1000)
                val maxSegmentsPerSlice = if (random.nextBoolean()) 1 else 1 + random.nextInt(10)
                if (wrapWithAssertions) {
                    if (random.nextBoolean()) {
                        ret =
                            object : AssertingIndexSearcher(random, r, ex) {
                                override fun slices(leaves: MutableList<LeafReaderContext>): Array<LeafSlice> {
                                    return slices(
                                        leaves, maxDocPerSlice, maxSegmentsPerSlice, concurrency!!
                                    )
                                }
                            }
                    } else {
                        ret =
                            object : AssertingIndexSearcher(random, r.context, ex) {
                                override fun slices(leaves: MutableList<LeafReaderContext>): Array<LeafSlice> {
                                    return slices(
                                        leaves, maxDocPerSlice, maxSegmentsPerSlice, concurrency!!
                                    )
                                }
                            }
                    }
                } else {
                    ret =
                        object : IndexSearcher(r, ex) {
                            override fun slices(leaves: MutableList<LeafReaderContext>): Array<LeafSlice> {
                                return slices(
                                    leaves, maxDocPerSlice, maxSegmentsPerSlice, concurrency!!
                                )
                            }
                        }
                }
                ret.similarity = getClassEnvRule().similarity!!
                ret.queryCachingPolicy = MAYBE_CACHE_POLICY
                if (random().nextBoolean()) {
                    ret.timeout = org.gnit.lucenekmp.index.QueryTimeout { false }
                }
                return ret
            }
        }

        /**
         * Creates leaf slices according to the concurrency argument, that optionally leverage
         * intra-segment concurrency by splitting segments into multiple partitions according to the
         * maxDocsPerSlice argument.
         */
        private fun slices(
            leaves: MutableList<LeafReaderContext>,
            maxDocsPerSlice: Int,
            maxSegmentsPerSlice: Int,
            concurrency: Concurrency
        ): Array<IndexSearcher.LeafSlice> {
            assert(concurrency != Concurrency.NONE)
            // Rarely test slices without partitions even though intra-segment concurrency is supported
            return IndexSearcher.slices(
                leaves,
                maxDocsPerSlice,
                maxSegmentsPerSlice,
                concurrency == Concurrency.INTRA_SEGMENT && RandomizedTest.frequently()
            )
        }
        // line 2069


        //↓ line 2824 of LuceneTestCase.java
        private data class StackFrame(val className: String, val methodName: String)

        private fun parseStackFrames(trace: String): List<StackFrame> {
            val frames = mutableListOf<StackFrame>()
            val pattern = Regex("""\s*at\s+([^\s(]+)""")
            for (line in trace.lineSequence().drop(1)) { // skip header line
                val match = pattern.find(line) ?: continue
                val fqMethod = match.groupValues[1]
                val lastDot = fqMethod.lastIndexOf('.')
                if (lastDot <= 0 || lastDot == fqMethod.length - 1) {
                    continue
                }
                val className = fqMethod.substring(0, lastDot)
                val methodName = fqMethod.substring(lastDot + 1)
                frames.add(StackFrame(className, methodName))
            }
            return if (frames.size > 1) frames.drop(1) else emptyList() // drop this helper frame
        }

        /** Inspects stack trace to figure out if a method of a specific class called us.  */
        fun callStackContains(clazz: KClass<*>, methodName: String): Boolean {
            val className = clazz.qualifiedName ?: clazz.toString()
            val trace = Throwable().stackTraceToString()
            val frames = parseStackFrames(trace)
            if (frames.isNotEmpty()) {
                return frames.any { it.className == className && it.methodName == methodName }
            }
            return trace.contains(className) && trace.contains(methodName)
        }

        /**
         * Inspects stack trace to figure out if one of the given method names (no class restriction)
         * called us.
         */
        fun callStackContainsAnyOf(vararg methodNames: String): Boolean {
            val methods = methodNames.toSet()
            val trace = Throwable().stackTraceToString()
            val frames = parseStackFrames(trace)
            if (frames.isNotEmpty()) {
                return frames.any { it.methodName in methods }
            }
            return methods.any { trace.contains(it) }
        }

        /** Inspects stack trace if the given class called us.  */
        fun callStackContains(clazz: KClass<*>): Boolean {
            val className = clazz.qualifiedName ?: clazz.toString()
            val trace = Throwable().stackTraceToString()
            val frames = parseStackFrames(trace)
            if (frames.isNotEmpty()) {
                return frames.any { it.className == className }
            }
            return trace.contains(className)
        }

        /** Checks a specific exception class is thrown by the given runnable, and returns it.  */
        fun <T : Throwable> expectThrows(
            expectedType: KClass<T>, runnable: LuceneTestCase.ThrowingRunnable
        ): T {
            return expectThrows(
                expectedType,
                "Expected exception " + expectedType.simpleName + " but no exception was thrown",
                runnable
            )
        }

        /** Checks a specific exception class is thrown by the given runnable, and returns it.  */
        fun <T : Throwable> expectThrows(
            expectedType: KClass<T>,
            noExceptionMessage: String?,
            runnable: LuceneTestCase.ThrowingRunnable
        ): T {
            val thrown: Throwable? =
                _expectThrows(mutableListOf(expectedType), runnable)
            if (expectedType.isInstance(thrown)) {
                return expectedType.cast(thrown)
            }
            if (null == thrown) {
                throw AssertionError(noExceptionMessage)
            }

            fail(
                message = "Unexpected exception type, expected "
                        + expectedType.simpleName
                        + " but got: "
                        + thrown,
                cause = thrown
            )
        }

        /** Checks a specific exception class is thrown by the given runnable, and returns it.  */
        fun <T : Throwable> expectThrowsAnyOf(
            expectedTypes: MutableList<KClass<out T>>,
            runnable: LuceneTestCase.ThrowingRunnable
        ): T {
            if (expectedTypes.isEmpty()) {
                throw AssertionError("At least one expected exception type is required?")
            }

            val thrown: Throwable? = _expectThrows(expectedTypes, runnable)
            if (null != thrown) {
                for (expectedType in expectedTypes) {
                    if (expectedType.isInstance(thrown)) {
                        return expectedType.cast(thrown)
                    }
                }
            }

            val exceptionTypes =
                expectedTypes.map { obj: KClass<out T> -> obj.simpleName }

            if (thrown != null) {
                fail(
                    message = "Unexpected exception type, expected any of "
                            + exceptionTypes
                            + " but got: "
                            + thrown,
                    cause = thrown
                )
            } else {
                throw AssertionError(
                    ("Expected any of the following exception types: "
                            + exceptionTypes
                            + " but no exception was thrown.")
                )
            }
        }

        /**
         * Checks that specific wrapped and outer exception classes are thrown by the given runnable, and
         * returns the wrapped exception.
         */
        fun <TO : Throwable, TW : Throwable> expectThrows(
            expectedOuterType: KClass<TO>,
            expectedWrappedType: KClass<TW>,
            runnable: LuceneTestCase.ThrowingRunnable
        ): TW {
            val thrown: Throwable? =
                _expectThrows(mutableListOf(expectedOuterType), runnable)
            if (null == thrown) {
                fail(
                    message = "Expected outer exception "
                            + expectedOuterType.simpleName
                            + " but no exception was thrown."
                )
            }
            if (expectedOuterType.isInstance(thrown)) {
                val cause = thrown.cause
                if (expectedWrappedType.isInstance(cause)) {
                    return expectedWrappedType.cast(cause)
                } else {
                    fail(
                        message = "Unexpected wrapped exception type, expected "
                                + expectedWrappedType.simpleName
                                + " but got: "
                                + cause,
                        cause = thrown
                    )
                }
            }

            fail(
                message = "Unexpected outer exception type, expected "
                        + expectedOuterType.simpleName
                        + " but got: "
                        + thrown,
                cause = thrown
            )
        }


        /**
         * Checks that one of the specified wrapped and outer exception classes are thrown by the given
         * runnable, and returns the outer exception.
         *
         *
         * This method accepts outer exceptions with no wrapped exception; an empty list of expected
         * wrapped exception types indicates no wrapped exception.
         */
        fun <TO : Throwable, TW : Throwable> expectThrowsAnyOf(
            expectedOuterToWrappedTypes: Map<KClass<out TO>, MutableList<KClass<out TW>>>,
            runnable: LuceneTestCase.ThrowingRunnable
        ): TO? {
            val outerClasses: MutableList<KClass<out TO>> = expectedOuterToWrappedTypes.keys.toMutableList()
            val thrown: Throwable? = _expectThrows(outerClasses, runnable)

            if (null == thrown) {
                val outerTypes =
                    outerClasses.map { obj: KClass<out TO> -> obj.simpleName }
                throw AssertionError(
                    ("Expected any of the following outer exception types: "
                            + outerTypes
                            + " but no exception was thrown.")
                )
            }
            for (entry in expectedOuterToWrappedTypes.entries) {
                val expectedOuterType: KClass<out TO> = entry.key
                val expectedWrappedTypes: MutableList<KClass<out TW>> = entry.value
                val cause = thrown.cause
                if (expectedOuterType.isInstance(thrown)) {
                    if (expectedWrappedTypes.isEmpty()) {
                        return null // no wrapped exception
                    } else {
                        for (expectedWrappedType in expectedWrappedTypes) {
                            if (expectedWrappedType.isInstance(cause)) {
                                return expectedOuterType.cast(thrown)
                            }
                        }
                        val wrappedTypes =
                            expectedWrappedTypes.map { obj: KClass<out TW> -> obj.simpleName }

                        fail(
                            message = "Unexpected wrapped exception type, expected one of $wrappedTypes but got: $cause",
                            cause = thrown
                        )
                    }
                }
            }
            val outerTypes =
                outerClasses.map { obj: KClass<out TO> -> obj.simpleName }

            fail(
                message = "Unexpected outer exception type, expected one of $outerTypes but got: $thrown",
                cause = thrown
            )
        }

        /**
         * Helper method for [.expectThrows] and [.expectThrowsAnyOf] that takes care of
         * propagating any [AssertionError] or [AssumptionViolatedException] instances thrown
         * if and only if they are super classes of the `expectedTypes`. Otherwise simply
         * returns any [Throwable] thrown, regardless of type, or null if the `runnable`
         * completed w/o error.
         */
        private fun _expectThrows(
            expectedTypes: MutableList<out KClass<*>>,
            runnable: LuceneTestCase.ThrowingRunnable
        ): Throwable? {
            try {
                runnable.run()
            } catch (ae: AssertionError) {
                for (expectedType in expectedTypes) {
                    if (expectedType.isInstance(ae)) { // user is expecting this type explicitly
                        return ae
                    }
                }
                throw ae
                /*} catch (ae: AssumptionViolatedException) { // TODO this exception is junit specific and incompatible with kotlin multiplatform common code. need walk around.
                    for (expectedType in expectedTypes) {
                        if (expectedType.isInstance(ae)) {
                            return ae
                        }
                    }
                    throw ae*/
            } catch (e: Throwable) {
                return e
            }
            return null
        }
        //↑ line 3056 of LuceneTestCase.java


        //↓ line 3071
        /**
         * Creates an empty, temporary folder (when the name of the folder is of no importance).
         *
         * @see .createTempDir
         */
        fun createTempDir(): Path {
            return createTempDir("tempDir")
        }

        /**
         * Creates an empty, temporary folder with the given name prefix.
         *
         *
         * The folder will be automatically removed after the test class completes successfully. The
         * test should close any file handles that would prevent the folder from being removed.
         */
        fun createTempDir(prefix: String): Path {
            return tempFilesCleanup.createTempDir(prefix)
        }

        /**
         * Returns true if the file exists in the directory (slow for some Directory impls).
         */
        @Throws(IOException::class)
        fun slowFileExists(dir: Directory, fileName: String): Boolean {
            return dir.listAll().contains(fileName)
        }

        /**
         * Creates an empty file with the given prefix and suffix.
         *
         *
         * The file will be automatically removed after the test class completes successfully. The test
         * should close any file handles that would prevent the folder from being removed.
         */
        @Throws(IOException::class)
        fun createTempFile(prefix: String, suffix: String): Path {
            return tempFilesCleanup.createTempFile(prefix, suffix)
        }

        /**
         * Creates an empty temporary file.
         *
         * @see .createTempFile
         */
        @Throws(IOException::class)
        fun createTempFile(): Path {
            return createTempFile("tempFile", ".tmp")
        }
        //↑ line 30107

        //TODO: implement the collate() and others of LuceneTestCase.java

        //↓ line 3202 of LuceneTestCase.java
        /**
         * Creates a [BytesRef] holding UTF-8 bytes for the incoming String, that sometimes uses a
         * non-zero `offset`, and non-zero end-padding, to tickle latent bugs that fail to look at
         * `BytesRef.offset`.
         */
        fun newBytesRef(s: String): BytesRef {
            return newBytesRef(s.encodeToByteArray())
        }

        /**
         * Creates a copy of the incoming [BytesRef] that sometimes uses a non-zero `offset`,
         * and non-zero end-padding, to tickle latent bugs that fail to look at `BytesRef.offset`.
         */
        fun newBytesRef(b: BytesRef): BytesRef {
            assertTrue(b.isValid())
            return newBytesRef(b.bytes, b.offset, b.length)
        }

        /**
         * Creates a random BytesRef from the incoming bytes that sometimes uses a non-zero `offset`, and non-zero end-padding, to tickle latent bugs that fail to look at `BytesRef.offset`.
         */
        fun newBytesRef(b: ByteArray): BytesRef {
            return newBytesRef(b, 0, b.size)
        }

        /**
         * Creates a random empty BytesRef that sometimes uses a non-zero `offset`, and non-zero
         * end-padding, to tickle latent bugs that fail to look at `BytesRef.offset`.
         */
        fun newBytesRef(): BytesRef {
            return newBytesRef(ByteArray(0), 0, 0)
        }

        /**
         * Creates a random empty BytesRef, with at least the requested length of bytes free, that
         * sometimes uses a non-zero `offset`, and non-zero end-padding, to tickle latent bugs that
         * fail to look at `BytesRef.offset`.
         */
        fun newBytesRef(byteLength: Int): BytesRef {
            return newBytesRef(ByteArray(byteLength), 0, byteLength)
        }

        /**
         * Creates a copy of the incoming bytes slice that sometimes uses a non-zero `offset`, and
         * non-zero end-padding, to tickle latent bugs that fail to look at `BytesRef.offset`.
         */
        fun newBytesRef(bytesIn: ByteArray, offset: Int, length: Int): BytesRef {
            //logger.debug { "LTC.newBytesRef!  bytesIn.length=" + bytesIn.size + " offset=" + offset + " length=" + length }

            assertTrue(
                "got offset=" + offset + " length=" + length + " bytesIn.length=" + bytesIn.size
            ) { bytesIn.size >= offset + length }

            // randomly set a non-zero offset
            val startOffset = /*if (Random.nextBoolean()) {
                Random.nextInt(1, 20)
            } else {
                0
            }*/
                Random.nextInt(1, 20)

            // also randomly set an end padding:
            val endPadding = /*if (Random.nextBoolean()) {
                Random.nextInt(1, 20)
            } else {
                0
            }*/
                Random.nextInt(1, 20)

            val bytes = ByteArray(startOffset + length + endPadding)

            bytesIn.copyInto(bytes, startOffset, offset, offset + length)

            //logger.debug { "LTC:  return bytes.length=" + bytes.size + " startOffset=" + startOffset + " length=" + length }
            val it = BytesRef(bytes, startOffset, length)
            assertTrue(it.isValid())

            if (Random.nextInt(1, 17) == 7) {
                // try to ferret out bugs in this method too!
                return newBytesRef(it.bytes, it.offset, it.length)
            }

            return it
        }
        //↑ line 3288 of LuceneTestCase.java

    }
}
