package org.gnit.lucenekmp.tests.util

/*import com.carrotsearch.randomizedtesting.RandomizedContext
import com.carrotsearch.randomizedtesting.generators.RandomPicks*/
import kotlinx.datetime.TimeZone

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.DocValuesFormat
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.codecs.lucene101.Lucene101Codec
//import org.gnit.lucenekmp.codecs.simpletext.SimpleTextCodec
import org.gnit.lucenekmp.jdkport.Locale
import org.gnit.lucenekmp.jdkport.PrintStream
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.similarities.Similarity
/*import org.gnit.lucenekmp.tests.codecs.asserting.AssertingCodec
import org.gnit.lucenekmp.tests.codecs.asserting.AssertingDocValuesFormat
import org.gnit.lucenekmp.tests.codecs.asserting.AssertingPostingsFormat
import org.gnit.lucenekmp.tests.codecs.cheapbastard.CheapBastardCodec
import org.gnit.lucenekmp.tests.codecs.compressing.CompressingCodec
import org.gnit.lucenekmp.tests.codecs.mockrandom.MockRandomPostingsFormat
import org.gnit.lucenekmp.tests.index.RandomCodec*/
import org.gnit.lucenekmp.tests.search.similarities.AssertingSimilarity
import org.gnit.lucenekmp.tests.search.similarities.RandomSimilarity
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.INFOSTREAM
/*import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.TEST_CODEC
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.TEST_DOCVALUESFORMAT
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.TEST_POSTINGSFORMAT
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.VERBOSE
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.assumeFalse
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.localeForLanguageTag
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.randomLocale
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.randomTimeZone
import org.gnit.lucenekmp.tests.util.LuceneTestCase.LiveIWCFlushMode
import org.gnit.lucenekmp.tests.util.LuceneTestCase.SuppressCodecs*/
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.PrintStreamInfoStream
import kotlin.random.Random

/** Setup and restore suite-level environment (fine grained junk that doesn't fit anywhere else).  */
class TestRuleSetupAndRestoreClassEnv : AbstractBeforeAfterRule() {
    private var savedCodec: Codec? = null
    private var savedLocale: Locale? = null
    private var savedTimeZone: TimeZone? = null
    private var savedInfoStream: InfoStream? = null

    var locale: Locale? = null
    var timeZone: TimeZone? = null
    var similarity: Similarity? = null
    var codec: Codec? = null

    /** Indicates whether the rule has executed its [.before] method fully.  */
    var isInitialized: Boolean = false
        private set

    /**
     * @see SuppressCodecs
     */
    var avoidCodecs: HashSet<String>? = null

    /*internal class ThreadNameFixingPrintStreamInfoStream(out: PrintStream) :
        PrintStreamInfoStream(out) {
        override fun message(component: String, message: String) {
            if ("TP" == component) {
                return  // ignore test points!
            }
            val name: String
            if (java.lang.Thread.currentThread().name.startsWith("TEST-")) {
                // The name of the main thread is way too
                // long when looking at IW verbose output...
                name = "main"
            } else {
                name = java.lang.Thread.currentThread().name
            }
            stream.println(
                component + " " + messageID + " [" + getTimestamp() + "; " + name + "]: " + message
            )
        }
    }*/

    @Throws(Exception::class)
    override fun before() {
        // if verbose: print some debugging stuff about which codecs are loaded.
        if (LuceneTestCase.VERBOSE) {
            println("Loaded codecs: " + Codec.availableCodecs())
            println("Loaded postingsFormats: " + PostingsFormat.availablePostingsFormats())
        }

        /*savedInfoStream = InfoStream.getDefault()
        val random: Random = RandomizedContext.current().getRandom()
        val v: Boolean = random.nextBoolean()
        if (LuceneTestCase.INFOSTREAM) {
            InfoStream.setDefault(ThreadNameFixingPrintStreamInfoStream(System.out))
        } else if (v) {
            InfoStream.setDefault(NullInfoStream())
        }

        val targetClass: java.lang.Class<*> = RandomizedContext.current().getTargetClass()
        avoidCodecs = java.util.HashSet<String>()
        if (targetClass.isAnnotationPresent(SuppressCodecs::class)) {
            val a: SuppressCodecs =
                targetClass.getAnnotation<SuppressCodecs>(SuppressCodecs::class)
            avoidCodecs.addAll(java.util.Arrays.asList<String>(*a.value))
        }

        savedCodec = Codec.getDefault()
        val randomVal: Int = random.nextInt(11)
        if ("default" == LuceneTestCase.TEST_CODEC) {
            codec = savedCodec // just use the default, don't randomize
        } else if (("random" == LuceneTestCase.TEST_POSTINGSFORMAT == false)
            || ("random" == LuceneTestCase.TEST_DOCVALUESFORMAT == false)
        ) {
            // the user wired postings or DV: this is messy
            // refactor into RandomCodec....

            val format: PostingsFormat
            if ("random" == LuceneTestCase.TEST_POSTINGSFORMAT) {
                format = AssertingPostingsFormat()
            } else if ("MockRandom" == LuceneTestCase.TEST_POSTINGSFORMAT) {
                format = MockRandomPostingsFormat(Random(random.nextLong()))
            } else {
                format =
                    PostingsFormat.forName(LuceneTestCase.TEST_POSTINGSFORMAT)
            }

            val dvFormat: DocValuesFormat
            if ("random" == LuceneTestCase.TEST_DOCVALUESFORMAT) {
                dvFormat = AssertingDocValuesFormat()
            } else {
                dvFormat =
                    DocValuesFormat.forName(LuceneTestCase.TEST_DOCVALUESFORMAT)
            }

            codec =
                object : AssertingCodec() {
                    override fun getPostingsFormatForField(field: String): PostingsFormat {
                        return format
                    }

                    override fun getDocValuesFormatForField(field: String): DocValuesFormat {
                        return dvFormat
                    }

                    override fun toString(): String {
                        return super.toString() + ": " + format.toString() + ", " + dvFormat.toString()
                    }
                }
        } else if ("SimpleText" == LuceneTestCase.TEST_CODEC
            || ("random" == LuceneTestCase.TEST_CODEC
                    && randomVal == 9 && LuceneTestCase.rarely(random)
                    && !shouldAvoidCodec("SimpleText"))
        ) {
            codec = SimpleTextCodec()
        } else if ("CheapBastard" == LuceneTestCase.TEST_CODEC
            || ("random" == LuceneTestCase.TEST_CODEC
                    && randomVal == 8 && !shouldAvoidCodec("CheapBastard") && !shouldAvoidCodec("Lucene41"))
        ) {
            // we also avoid this codec if Lucene41 is avoided, since thats the postings format it uses.
            codec = CheapBastardCodec()
        } else if ("Asserting" == LuceneTestCase.TEST_CODEC
            || ("random" == LuceneTestCase.TEST_CODEC && randomVal == 7 && !shouldAvoidCodec(
                "Asserting"
            ))
        ) {
            codec = AssertingCodec()
        } else if ("Compressing" == LuceneTestCase.TEST_CODEC
            || ("random" == LuceneTestCase.TEST_CODEC && randomVal == 6 && !shouldAvoidCodec(
                "Compressing"
            ))
        ) {
            codec = CompressingCodec.randomInstance(random)
        } else if ("Lucene100" == LuceneTestCase.TEST_CODEC
            || ("random" == LuceneTestCase.TEST_CODEC && randomVal == 5 && !shouldAvoidCodec(
                "Lucene100"
            ))
        ) {
            codec = Lucene101Codec(
                com.carrotsearch.randomizedtesting.generators.RandomPicks.randomFrom<Lucene101Codec.Mode>(
                    random,
                    Lucene101Codec.Mode.Lucene101Codec.Mode.entries.toTypedArray()
                )
            )
        } else if ("random" != LuceneTestCase.TEST_CODEC) {
            codec =
                Codec.forName(LuceneTestCase.TEST_CODEC)
        } else if ("random" == LuceneTestCase.TEST_POSTINGSFORMAT) {
            codec = RandomCodec(random, avoidCodecs)
        } else {
            assert(false)
        }
        Codec.setDefault(codec)

        // Initialize locale/ timezone.
        val testLocale: String = System.getProperty("tests.locale", "random")!!
        val testTimeZone: String = System.getProperty("tests.timezone", "random")!!

        // Always pick a random one for consistency (whether tests.locale was specified or not).
        savedLocale = Locale.getDefault()
        val randomLocale: Locale =
            LuceneTestCase.randomLocale(random)
        locale =
            if (testLocale == "random") randomLocale else LuceneTestCase.localeForLanguageTag(
                testLocale
            )
        Locale.setDefault(locale)

        savedTimeZone = TimeZone.getDefault()
        val randomTimeZone: TimeZone =
            LuceneTestCase.randomTimeZone(LuceneTestCase.random())
        timeZone = if (testTimeZone == "random") randomTimeZone else TimeZone.getTimeZone(
            testTimeZone
        )
        TimeZone.setDefault(timeZone)*/
        similarity =
            AssertingSimilarity(RandomSimilarity(LuceneTestCase.random()))

        // Check codec restrictions once at class level.
        try {
            checkCodecRestrictions(codec!!)
        } catch (e: /*org.junit.internal.AssumptionViolated*/Exception) {
            /*System.err.*/println(
                ("NOTE: "
                        + e.message
                        + " Suppressed codecs: "
                        + avoidCodecs!!.toTypedArray().contentToString())
            )
            throw e
        }

        // We have "stickiness" so that sometimes all we do is vary the RAM buffer size, other times
        // just the doc count to flush by, else both.
        // This way the assertMemory in DocumentsWriterFlushControl sometimes runs (when we always flush
        // by RAM).
        /*val flushMode: LiveIWCFlushMode
        when (LuceneTestCase.random().nextInt(3)) {
            0 -> flushMode = LiveIWCFlushMode.BY_RAM
            1 -> flushMode = LiveIWCFlushMode.BY_DOCS
            2 -> flushMode = LiveIWCFlushMode.EITHER
            else -> throw AssertionError()
        }

        LuceneTestCase.setLiveIWCFlushMode(flushMode)*/

        this.isInitialized = true
    }

    /**
     * Check codec restrictions.
     *
     * @throws AssumptionViolatedException if the class does not work with a given codec.
     */
    private fun checkCodecRestrictions(codec: Codec) {
        /*LuceneTestCase.assumeFalse(
            "Class not allowed to use codec: " + codec.name + ".",
            shouldAvoidCodec(codec.name)
        )

        if (codec is RandomCodec && !avoidCodecs.isEmpty()) {
            for (name in (codec as RandomCodec).formatNames) {
                LuceneTestCase.assumeFalse(
                    "Class not allowed to use postings format: $name.",
                    shouldAvoidCodec(name)
                )
            }
        }

        val pf: PostingsFormat = codec.postingsFormat()
        LuceneTestCase.assumeFalse(
            "Class not allowed to use postings format: " + pf.name + ".",
            shouldAvoidCodec(pf.name)
        )

        LuceneTestCase.assumeFalse(
            "Class not allowed to use postings format: " + LuceneTestCase.TEST_POSTINGSFORMAT + ".",
            shouldAvoidCodec(LuceneTestCase.TEST_POSTINGSFORMAT)
        )*/
    }

    //TODO AbstractBeforeAfterRule extends org.junit.rules.TestRule but not wired with kotlin.test
    // so below code exists but will not be executed
    /** After suite cleanup (always invoked).  */
    @Throws(Exception::class)
    override fun after() {
        Codec.default = savedCodec!!
        /*InfoStream.setDefault(savedInfoStream)
        if (savedLocale != null) Locale.setDefault(savedLocale)
        if (savedTimeZone != null) TimeZone.setDefault(savedTimeZone)*/
    }

    /** Should a given codec be avoided for the currently executing suite  */
    private fun shouldAvoidCodec(codec: String): Boolean {
        return !avoidCodecs!!.isEmpty() && avoidCodecs!!.contains(codec)
    }
}
