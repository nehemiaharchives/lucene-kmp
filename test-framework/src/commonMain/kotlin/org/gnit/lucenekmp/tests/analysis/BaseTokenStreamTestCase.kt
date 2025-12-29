package org.gnit.lucenekmp.tests.analysis

import okio.IOException
import okio.Path.Companion.toPath
import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.TokenStreamToAutomaton
import org.gnit.lucenekmp.analysis.tokenattributes.*
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexableField
import org.gnit.lucenekmp.index.IndexableFieldType
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.InterruptedException
import org.gnit.lucenekmp.jdkport.PrintWriter
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.StringReader
import org.gnit.lucenekmp.jdkport.StringWriter
import org.gnit.lucenekmp.jdkport.Writer
import org.gnit.lucenekmp.search.BoostAttribute
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.Rethrow
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.tests.util.automaton.AutomatonTestUtil
import org.gnit.lucenekmp.util.Attribute
import org.gnit.lucenekmp.util.AttributeFactory
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import org.gnit.lucenekmp.util.AttributeSource
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.fst.Util
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.jvm.JvmOverloads
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail


/**
 * Base class for all Lucene unit tests that use TokenStreams.
 *
 *
 * When writing unit tests for analysis components, it's highly recommended to use the helper
 * methods here (especially in conjunction with [MockAnalyzer] or [MockTokenizer]), as
 * they contain many assertions and checks to catch bugs.
 *
 * @see MockAnalyzer
 *
 * @see MockTokenizer
 */
abstract class BaseTokenStreamTestCase : LuceneTestCase() {
    // some helpers to test Analyzers and TokenStreams:
    /**
     * Attribute that records if it was cleared or not. This is used for testing that
     * clearAttributes() was called correctly.
     */
    interface CheckClearAttributesAttribute : Attribute {
        val andResetClearCalled: Boolean
    }

    /**
     * Attribute that records if it was cleared or not. This is used for testing that
     * clearAttributes() was called correctly.
     */
    class CheckClearAttributesAttributeImpl : AttributeImpl(),
        CheckClearAttributesAttribute {
        private var clearCalled = false

        override val andResetClearCalled: Boolean
            get() {
                try {
                    return clearCalled
                } finally {
                    clearCalled = false
                }
            }

        override fun clear() {
            clearCalled = true
        }

        override fun equals(other: Any?): Boolean {
            return (other is CheckClearAttributesAttributeImpl
                    && other.clearCalled == this.clearCalled)
        }

        override fun hashCode(): Int {
            return 76137213 xor clearCalled.hashCode()
        }

        override fun copyTo(target: AttributeImpl) {
            target.clear()
        }

        override fun reflectWith(reflector: AttributeReflector) {
            reflector.reflect(CheckClearAttributesAttribute::class, "clearCalled", clearCalled)
        }

        override fun newInstance(): AttributeImpl {
            return CheckClearAttributesAttributeImpl()
        }
    }

    internal class AnalysisThread(
        val seed: Long,
        latch: CountDownLatch,
        a: Analyzer,
        iterations: Int,
        maxWordLength: Int,
        useCharFilter: Boolean,
        simple: Boolean,
        graphOffsetsAreCorrect: Boolean,
        iw: RandomIndexWriter?
    ) /*: java.lang.Thread()*/ { // use kotlin.coroutine.Job instead of Thread
        val iterations: Int
        val maxWordLength: Int
        val a: Analyzer
        val useCharFilter: Boolean
        val simple: Boolean
        val graphOffsetsAreCorrect: Boolean
        val iw: RandomIndexWriter?
        val latch: CountDownLatch

        // NOTE: not volatile because we don't want the tests to
        // add memory barriers (ie alter how threads
        // interact)... so this is just "best effort":
        var failed: Boolean = false
        private var job: Job? = null

        init {
            this.a = a
            this.iterations = iterations
            this.maxWordLength = maxWordLength
            this.useCharFilter = useCharFilter
            this.simple = simple
            this.graphOffsetsAreCorrect = graphOffsetsAreCorrect
            this.iw = iw
            this.latch = latch
        }

        fun run() {
            var success = false
            try {
                latch.await()
                // see the part in checkRandomData where it replays the same text again
                // to verify reproducability/reuse: hopefully this would catch thread hazards.
                checkRandomData(
                    Random(seed),
                    a,
                    iterations,
                    maxWordLength,
                    useCharFilter,
                    simple,
                    graphOffsetsAreCorrect,
                    iw
                )
                success = true
            } catch (e: Exception) {
                Rethrow.rethrow(e)
            } finally {
                failed = !success
            }
        }

        fun start(){
            job = CoroutineScope(Dispatchers.Default).launch {
                run()
            }
        }

        fun join(){
            runBlocking {
                job?.join()
            }
        }
    }

    @Throws(IOException::class)
    protected fun toDot(a: Analyzer, inputText: String): String {
        val sw = StringWriter()
        val ts: TokenStream = a.tokenStream("field", inputText)
        ts.reset()
        TokenStreamToDot(inputText, ts, PrintWriter(sw)).toDot()
        return sw.toString()
    }

    @Throws(IOException::class)
    protected fun toDotFile(
        a: Analyzer,
        inputText: String,
        localFileName: String
    ) {
        val w: Writer = Files.newBufferedWriter(
            localFileName.toPath(),
            StandardCharsets.UTF_8
        )
        val ts: TokenStream = a.tokenStream("field", inputText)
        ts.reset()
        TokenStreamToDot(inputText, ts, PrintWriter(w)).toDot()
        w.close()
    }

    companion object {
        init {
            AttributeSource.registerAttributeInterfaces(
                CheckClearAttributesAttributeImpl::class,
                arrayOf(CheckClearAttributesAttribute::class)
            )
        }

        // graphOffsetsAreCorrect validates:
        //   - graph offsets are correct (all tokens leaving from
        //     pos X have the same startOffset; all tokens
        //     arriving to pos Y have the same endOffset)
        @Throws(IOException::class)
        fun assertTokenStreamContents(
            ts: TokenStream,
            output: Array<String>,
            startOffsets: IntArray?,
            endOffsets: IntArray?,
            types: Array<String>?,
            posIncrements: IntArray?,
            posLengths: IntArray?,
            finalOffset: Int?,
            finalPosInc: Int?,
            keywordAtts: BooleanArray?,
            graphOffsetsAreCorrect: Boolean,
            payloads: Array<ByteArray>?,
            flags: IntArray?,
            boost: FloatArray?
        ) {
            /*assertNotNull(output)*/
            val checkClearAtt: CheckClearAttributesAttribute =
                if (ts.hasAttribute(CheckClearAttributesAttribute::class)) {
                    ts.getAttribute(CheckClearAttributesAttribute::class)
                } else {
                    ts.addAttributeImpl(CheckClearAttributesAttributeImpl())
                    ts.getAttribute(CheckClearAttributesAttribute::class)
                }

            var termAtt: CharTermAttribute? = null
            if (output.isNotEmpty()) {
                assertTrue(
                    ts.hasAttribute(CharTermAttribute::class), "has no CharTermAttribute"
                )
                termAtt = ts.getAttribute(CharTermAttribute::class)

                // every UTF-16 character-based TokenStream MUST provide a TermToBytesRefAttribute,
                // implemented by same instance like the CharTermAttribute:
                assertTrue(
                    ts.hasAttribute(TermToBytesRefAttribute::class)
                    ,"has no TermToBytesRefAttribute"
                )
                val bytesAtt: TermToBytesRefAttribute =
                    ts.getAttribute(
                        TermToBytesRefAttribute::class
                    )

                // ConcatenateGraphFilter has some tricky logic violating this. We have an extra assert there:
                if (bytesAtt::class.simpleName != "BytesRefBuilderTermAttributeImpl") {
                    assertSame(
                        termAtt as TermToBytesRefAttribute,
                        bytesAtt,
                        "TermToBytesRefAttribute must be implemented by same instance"
                    )
                }
            }

            var offsetAtt: OffsetAttribute? = null
            if (startOffsets != null || endOffsets != null || finalOffset != null) {
                assertTrue(
                    ts.hasAttribute(OffsetAttribute::class),
                    "has no OffsetAttribute"
                )
                offsetAtt =
                    ts.getAttribute<OffsetAttribute>(OffsetAttribute::class)
            }

            var typeAtt: TypeAttribute? = null
            if (types != null) {
                assertTrue(
                    ts.hasAttribute(TypeAttribute::class),
                    "has no TypeAttribute"
                )
                typeAtt =
                    ts.getAttribute<TypeAttribute>(TypeAttribute::class)
            }

            var posIncrAtt: PositionIncrementAttribute? = null
            if (posIncrements != null || finalPosInc != null) {
                assertTrue(
                    ts.hasAttribute(PositionIncrementAttribute::class)
                        ,"has no PositionIncrementAttribute"
                )
                posIncrAtt =
                    ts.getAttribute<PositionIncrementAttribute>(
                        PositionIncrementAttribute::class
                    )
            }

            var posLengthAtt: PositionLengthAttribute? = null
            if (posLengths != null) {
                assertTrue(
                    ts.hasAttribute(PositionLengthAttribute::class)
                        ,"has no PositionLengthAttribute"
                )
                posLengthAtt =
                    ts.getAttribute<PositionLengthAttribute>(
                        PositionLengthAttribute::class
                    )
            }

            var keywordAtt: KeywordAttribute? = null
            if (keywordAtts != null) {
                assertTrue(
                    ts.hasAttribute(KeywordAttribute::class)
                    ,"has no KeywordAttribute"
                )
                keywordAtt = ts.getAttribute(KeywordAttribute::class)
            }

            var payloadAtt: PayloadAttribute? = null
            if (payloads != null) {
                assertTrue(
                    ts.hasAttribute(PayloadAttribute::class)
                    ,"has no PayloadAttribute"
                )
                payloadAtt =
                    ts.getAttribute(
                        PayloadAttribute::class
                    )
            }

            var flagsAtt: FlagsAttribute? = null
            if (flags != null) {
                assertTrue(
                    ts.hasAttribute(FlagsAttribute::class)
                    ,"has no FlagsAttribute"
                )
                flagsAtt =
                    ts.getAttribute(FlagsAttribute::class)
            }

            var boostAtt: BoostAttribute? = null
            if (boost != null) {
                assertTrue(
                    ts.hasAttribute(BoostAttribute::class)
                    ,"has no BoostAttribute"
                )
                boostAtt =
                    ts.getAttribute<BoostAttribute>(BoostAttribute::class)
            }

            // Maps position to the start/end offset:
            val posToStartOffset: MutableMap<Int, Int> = mutableMapOf()
            val posToEndOffset: MutableMap<Int, Int> = mutableMapOf()

            // TODO: would be nice to be able to assert silly duplicated tokens are not created, but a
            // number of cases do this "legitimately": LUCENE-7622
            ts.reset()
            var pos = -1
            var lastStartOffset = 0
            for (i in output.indices) {
                // extra safety to enforce, that the state is not preserved and also assign bogus values
                ts.clearAttributes()
                termAtt!!.setEmpty()!!.append("bogusTerm")
                if (offsetAtt != null) offsetAtt.setOffset(14584724, 24683243)
                if (typeAtt != null) typeAtt.setType("bogusType")
                if (posIncrAtt != null) posIncrAtt.setPositionIncrement(45987657)
                if (posLengthAtt != null) posLengthAtt.positionLength = 45987653
                if (keywordAtt != null) keywordAtt.isKeyword = (i and 1) == 0
                if (payloadAtt != null) payloadAtt.payload = BytesRef(
                        byteArrayOf(0x00, -0x21, 0x12, -0x43, 0x24)
                    )

                if (flagsAtt != null) flagsAtt.flags = 0.inv() // all 1's

                if (boostAtt != null) boostAtt.boost = -1f

                checkClearAtt.andResetClearCalled // reset it, because we called clearAttribute() before
                assertTrue( ts.incrementToken(), "token $i does not exist")
                assertTrue(
                    checkClearAtt.andResetClearCalled
                    , "clearAttributes() was not called correctly in TokenStream chain at token $i"
                )

                assertEquals( output[i], termAtt.toString(), "term $i")
                if (startOffsets != null) {
                    assertEquals(
                        startOffsets[i].toLong(),
                        offsetAtt!!.startOffset().toLong(),
                        "startOffset $i term=$termAtt"
                    )
                }
                if (endOffsets != null) {
                    assertEquals(
                        endOffsets[i].toLong(),
                        offsetAtt!!.endOffset().toLong(),
                        "endOffset $i term=$termAtt"
                    )
                }
                if (types != null) {
                    assertEquals(
                        types[i],
                        typeAtt!!.type(),
                        "type $i term=$termAtt"
                    )
                }
                if (posIncrements != null) {
                    assertEquals(
                        posIncrements[i].toLong(),
                        posIncrAtt!!.getPositionIncrement().toLong(),
                        "posIncrement $i term=$termAtt"
                    )
                }
                if (posLengths != null) {
                    assertEquals(
                        posLengths[i].toLong(),
                        posLengthAtt!!.positionLength.toLong(),
                        "posLength $i term=$termAtt"
                    )
                }
                if (keywordAtts != null) {
                    assertEquals(
                        keywordAtts[i],
                        keywordAtt!!.isKeyword,
                        "keywordAtt $i term=$termAtt"
                    )
                }
                if (flagsAtt != null) {
                    assertEquals(
                        flags!![i].toLong(),
                        flagsAtt.flags.toLong(),
                        "flagsAtt $i term=$termAtt"
                    )
                }
                if (boostAtt != null) {
                    assertEquals(
                        boost!![i].toDouble(),
                        boostAtt.boost.toDouble(),
                        0.001,
                        "boostAtt $i term=$termAtt"
                    )
                }
                if (payloads != null) {
                    if (payloads[i] != null) {
                        assertEquals(
                            BytesRef(payloads[i]),
                            payloadAtt!!.payload,
                            message = "payloads $i"
                        )
                    } else {
                        assertNull( payloads[i], "payloads $i")
                    }
                }
                if (posIncrAtt != null) {
                    if (i == 0) {
                        assertTrue(
                            posIncrAtt.getPositionIncrement() >= 1, "first posIncrement must be >= 1"
                        )
                    } else {
                        assertTrue(
                            posIncrAtt.getPositionIncrement() >= 0, "posIncrement must be >= 0"
                        )
                    }
                }
                if (posLengthAtt != null) {
                    assertTrue(
                        posLengthAtt.positionLength >= 1,
                        "posLength must be >= 1; got: " + posLengthAtt.positionLength
                    )
                }
                // we can enforce some basic things about a few attributes even if the caller doesn't check:
                if (offsetAtt != null) {
                    val startOffset: Int = offsetAtt.startOffset()
                    val endOffset: Int = offsetAtt.endOffset()
                    if (finalOffset != null) {
                        assertTrue(
                            startOffset <= finalOffset,
                            ("startOffset (= "
                                    + startOffset
                                    + ") must be <= finalOffset (= "
                                    + finalOffset
                                    + ") term="
                                    + termAtt)
                        )
                        assertTrue(
                            endOffset <= finalOffset,
                            ("endOffset must be <= finalOffset: got endOffset="
                                    + endOffset
                                    + " vs finalOffset="
                                    + finalOffset
                                    + " term="
                                    + termAtt)
                        )
                    }

                    assertTrue(
                        offsetAtt.startOffset() >= lastStartOffset,
                        ("offsets must not go backwards startOffset="
                                + startOffset
                                + " is < lastStartOffset="
                                + lastStartOffset
                                + " term="
                                + termAtt)
                    )
                    lastStartOffset = offsetAtt.startOffset()

                    if (graphOffsetsAreCorrect && posLengthAtt != null && posIncrAtt != null) {
                        // Validate offset consistency in the graph, ie
                        // all tokens leaving from a certain pos have the
                        // same startOffset, and all tokens arriving to a
                        // certain pos have the same endOffset:
                        val posInc: Int = posIncrAtt.getPositionIncrement()
                        pos += posInc

                        val posLength: Int = posLengthAtt.positionLength

                        if (!posToStartOffset.containsKey(pos)) {
                            // First time we've seen a token leaving from this position:
                            posToStartOffset[pos] = startOffset
                            // System.out.println("  + s " + pos + " -> " + startOffset);
                        } else {
                            // We've seen a token leaving from this position
                            // before; verify the startOffset is the same:
                            // System.out.println("  + vs " + pos + " -> " + startOffset);
                            assertEquals(
                                posToStartOffset[pos]!!.toLong(),
                                startOffset.toLong(),
                                (i.toString() + " inconsistent startOffset: pos="
                                        + pos
                                        + " posLen="
                                        + posLength
                                        + " token="
                                        + termAtt)
                            )
                        }

                        val endPos = pos + posLength

                        if (!posToEndOffset.containsKey(endPos)) {
                            // First time we've seen a token arriving to this position:
                            posToEndOffset[endPos] = endOffset
                            // System.out.println("  + e " + endPos + " -> " + endOffset);
                        } else {
                            // We've seen a token arriving to this position
                            // before; verify the endOffset is the same:
                            // System.out.println("  + ve " + endPos + " -> " + endOffset);
                            assertEquals(
                                posToEndOffset[endPos]!!.toLong(),
                                endOffset.toLong(),
                                ("inconsistent endOffset "
                                        + i
                                        + " pos="
                                        + pos
                                        + " posLen="
                                        + posLength
                                        + " token="
                                        + termAtt)
                            )
                        }
                    }
                }
            }

            if (ts.incrementToken()) {
                fail(
                    ("TokenStream has more tokens than expected (expected count="
                            + output.size
                            + "); extra token="
                            + ts.getAttribute<CharTermAttribute>(
                        CharTermAttribute::class
                    ))
                )
            }

            // repeat our extra safety checks for end()
            ts.clearAttributes()
            if (termAtt != null) termAtt.setEmpty()!!.append("bogusTerm")
            if (offsetAtt != null) offsetAtt.setOffset(14584724, 24683243)
            if (typeAtt != null) typeAtt.setType("bogusType")
            if (posIncrAtt != null) posIncrAtt.setPositionIncrement(45987657)
            if (posLengthAtt != null) posLengthAtt.positionLength = 45987653
            if (keywordAtt != null) keywordAtt.isKeyword = true
            if (payloadAtt != null) payloadAtt.payload = BytesRef(
                    byteArrayOf(0x00, -0x21, 0x12, -0x43, 0x24)
                )

            if (flagsAtt != null) flagsAtt.flags = 0.inv() // all 1's

            if (boostAtt != null) boostAtt.boost = -1f

            checkClearAtt.andResetClearCalled // reset it, because we called clearAttribute() before

            ts.end()
            assertTrue(
                checkClearAtt.andResetClearCalled,
                "super.end()/clearAttributes() was not called correctly in end()"
            )

            if (finalOffset != null) {
                assertEquals(
                    finalOffset.toLong(),
                    offsetAtt!!.endOffset().toLong(),
                    "finalOffset"
                )
            }
            if (offsetAtt != null) {
                assertTrue(offsetAtt.endOffset() >= 0, "finalOffset must be >= 0")
            }
            if (finalPosInc != null) {
                assertEquals(
                    finalPosInc.toLong(),
                    posIncrAtt!!.getPositionIncrement().toLong(),
                    "finalPosInc"
                )
            }

            ts.close()
        }

        @Throws(IOException::class)
        fun assertTokenStreamContents(
            ts: TokenStream,
            output: Array<String>,
            startOffsets: IntArray,
            endOffsets: IntArray,
            types: Array<String>,
            posIncrements: IntArray,
            posLengths: IntArray,
            finalOffset: Int,
            finalPosInc: Int,
            keywordAtts: BooleanArray,
            graphOffsetsAreCorrect: Boolean,
            payloads: Array<ByteArray>,
            flags: IntArray
        ) {
            assertTokenStreamContents(
                ts,
                output,
                startOffsets,
                endOffsets,
                types,
                posIncrements,
                posLengths,
                finalOffset,
                finalPosInc,
                keywordAtts,
                graphOffsetsAreCorrect,
                payloads,
                flags,
                null
            )
        }

        @JvmOverloads
        @Throws(IOException::class)
        fun assertTokenStreamContents(
            ts: TokenStream,
            output: Array<String>,
            startOffsets: IntArray?,
            endOffsets: IntArray?,
            types: Array<String>?,
            posIncrements: IntArray?,
            posLengths: IntArray?,
            finalOffset: Int?,
            keywordAtts: BooleanArray?,
            graphOffsetsAreCorrect: Boolean,
            boost: FloatArray? = null
        ) {
            assertTokenStreamContents(
                ts,
                output,
                startOffsets,
                endOffsets,
                types,
                posIncrements,
                posLengths,
                finalOffset,
                null,
                keywordAtts,
                graphOffsetsAreCorrect,
                null,
                null,
                boost
            )
        }

        @Throws(IOException::class)
        fun assertTokenStreamContents(
            ts: TokenStream,
            output: Array<String>,
            startOffsets: IntArray,
            endOffsets: IntArray,
            types: Array<String>,
            posIncrements: IntArray,
            posLengths: IntArray,
            finalOffset: Int,
            finalPosInc: Int?,
            keywordAtts: BooleanArray?,
            graphOffsetsAreCorrect: Boolean,
            payloads: Array<ByteArray>
        ) {
            assertTokenStreamContents(
                ts,
                output,
                startOffsets,
                endOffsets,
                types,
                posIncrements,
                posLengths,
                finalOffset,
                finalPosInc,
                keywordAtts,
                graphOffsetsAreCorrect,
                payloads,
                null,
                null
            )
        }

        @Throws(IOException::class)
        fun assertTokenStreamContents(
            ts: TokenStream,
            output: Array<String>,
            startOffsets: IntArray?,
            endOffsets: IntArray?,
            types: Array<String>?,
            posIncrements: IntArray?,
            posLengths: IntArray?,
            finalOffset: Int,
            graphOffsetsAreCorrect: Boolean,
            boost: FloatArray?
        ) {
            assertTokenStreamContents(
                ts,
                output,
                startOffsets,
                endOffsets,
                types,
                posIncrements,
                posLengths,
                finalOffset,
                null,
                graphOffsetsAreCorrect,
                boost
            )
        }

        @Throws(IOException::class)
        fun assertTokenStreamContents(
            ts: TokenStream,
            output: Array<String>,
            startOffsets: IntArray? = null,
            endOffsets: IntArray? = null,
            types: Array<String>? = null,
            posIncrements: IntArray? = null,
            posLengths: IntArray? = null,
            finalOffset: Int? = null,
            graphOffsetsAreCorrect: Boolean = true
        ) {
            assertTokenStreamContents(
                ts,
                output,
                startOffsets,
                endOffsets,
                types,
                posIncrements,
                posLengths,
                finalOffset,
                null,
                graphOffsetsAreCorrect,
                null
            )
        }

        @Throws(IOException::class)
        fun assertTokenStreamContents(
            ts: TokenStream,
            output: Array<String>,
            startOffsets: IntArray?,
            endOffsets: IntArray?,
            types: Array<String>?,
            posIncrements: IntArray?,
            posLengths: IntArray?,
            finalOffset: Int,
            boost: FloatArray?
        ) {
            assertTokenStreamContents(
                ts,
                output,
                startOffsets,
                endOffsets,
                types,
                posIncrements,
                posLengths,
                finalOffset,
                true,
                boost
            )
        }

        @Throws(IOException::class)
        fun assertTokenStreamContents(
            ts: TokenStream,
            output: Array<String>,
            startOffsets: IntArray,
            endOffsets: IntArray,
            types: Array<String>,
            posIncrements: IntArray,
            finalOffset: Int
        ) {
            assertTokenStreamContents(
                ts, output, startOffsets, endOffsets, types, posIncrements, null, finalOffset
            )
        }

        @Throws(IOException::class)
        fun assertTokenStreamContents(
            ts: TokenStream,
            output: Array<String>,
            types: Array<String>
        ) {
            assertTokenStreamContents(ts, output, null, null, types, null, null, null)
        }

        @Throws(IOException::class)
        fun assertTokenStreamContents(
            ts: TokenStream,
            output: Array<String>,
            posIncrements: IntArray
        ) {
            assertTokenStreamContents(ts, output, null, null, null, posIncrements, null, null)
        }

        @Throws(IOException::class)
        fun assertTokenStreamContents(
            ts: TokenStream,
            output: Array<String>,
            startOffsets: IntArray,
            endOffsets: IntArray,
            finalOffset: Int
        ) {
            assertTokenStreamContents(
                ts,
                output,
                startOffsets,
                endOffsets,
                null,
                null,
                null,
                finalOffset
            )
        }

        @Throws(IOException::class)
        fun assertTokenStreamContents(
            ts: TokenStream,
            output: Array<String>,
            startOffsets: IntArray,
            endOffsets: IntArray,
            posIncrements: IntArray
        ) {
            assertTokenStreamContents(
                ts, output, startOffsets, endOffsets, null, posIncrements, null, null
            )
        }

        @Throws(IOException::class)
        fun assertTokenStreamContents(
            ts: TokenStream,
            output: Array<String>,
            startOffsets: IntArray,
            endOffsets: IntArray,
            posIncrements: IntArray,
            finalOffset: Int
        ) {
            assertTokenStreamContents(
                ts, output, startOffsets, endOffsets, null, posIncrements, null, finalOffset
            )
        }

        @Throws(IOException::class)
        fun assertTokenStreamContents(
            ts: TokenStream,
            output: Array<String>,
            startOffsets: IntArray,
            endOffsets: IntArray,
            posIncrements: IntArray,
            posLengths: IntArray,
            finalOffset: Int
        ) {
            assertTokenStreamContents(
                ts, output, startOffsets, endOffsets, null, posIncrements, posLengths, finalOffset
            )
        }

        @Throws(IOException::class)
        fun assertAnalyzesTo(
            a: Analyzer,
            input: String,
            output: Array<String>,
            startOffsets: IntArray,
            endOffsets: IntArray,
            types: Array<String>,
            posIncrements: IntArray
        ) {
            assertTokenStreamContents(
                a.tokenStream("dummy", input),
                output,
                startOffsets,
                endOffsets,
                types,
                posIncrements,
                null,
                input.length
            )
            checkResetException(a, input)
            checkAnalysisConsistency(
                LuceneTestCase.random(),
                a,
                true,
                input
            )
        }


        @Throws(IOException::class)
        fun assertAnalyzesTo(
            a: Analyzer,
            input: String,
            output: Array<String>,
            startOffsets: IntArray? = null,
            endOffsets: IntArray? = null,
            types: Array<String>? = null,
            posIncrements: IntArray? = null,
            posLengths: IntArray? = null,
            boost: FloatArray? = null
        ) {
            assertTokenStreamContents(
                a.tokenStream("dummy", input),
                output,
                startOffsets,
                endOffsets,
                types,
                posIncrements,
                posLengths,
                input.length,
                boost
            )
            checkResetException(a, input)
            checkAnalysisConsistency(
                LuceneTestCase.random(),
                a,
                true,
                input
            )
        }

        @Throws(IOException::class)
        fun assertAnalyzesTo(
            a: Analyzer,
            input: String,
            output: Array<String>,
            startOffsets: IntArray,
            endOffsets: IntArray,
            types: Array<String>,
            posIncrements: IntArray,
            posLengths: IntArray,
            graphOffsetsAreCorrect: Boolean
        ) {
            assertTokenStreamContents(
                a.tokenStream("dummy", input),
                output,
                startOffsets,
                endOffsets,
                types,
                posIncrements,
                posLengths,
                input.length,
                graphOffsetsAreCorrect
            )
            checkResetException(a, input)
            checkAnalysisConsistency(
                LuceneTestCase.random(),
                a,
                true,
                input,
                graphOffsetsAreCorrect
            )
        }

        @Throws(IOException::class)
        fun assertAnalyzesTo(
            a: Analyzer,
            input: String,
            output: Array<String>,
            startOffsets: IntArray,
            endOffsets: IntArray,
            types: Array<String>,
            posIncrements: IntArray,
            posLengths: IntArray,
            graphOffsetsAreCorrect: Boolean,
            payloads: Array<ByteArray>
        ) {
            assertTokenStreamContents(
                a.tokenStream("dummy", input),
                output,
                startOffsets,
                endOffsets,
                types,
                posIncrements,
                posLengths,
                input.length,
                null,
                null,
                graphOffsetsAreCorrect,
                payloads
            )
            checkResetException(a, input)
            checkAnalysisConsistency(
                LuceneTestCase.random(),
                a,
                true,
                input,
                graphOffsetsAreCorrect
            )
        }

        @Throws(IOException::class)
        fun assertAnalyzesTo(
            a: Analyzer,
            input: String,
            output: Array<String>,
            types: Array<String>
        ) {
            assertAnalyzesTo(a, input, output, null, null, types, null, null)
        }

        @Throws(IOException::class)
        fun assertAnalyzesTo(
            a: Analyzer,
            input: String,
            output: Array<String>,
            posIncrements: IntArray
        ) {
            assertAnalyzesTo(a, input, output, null, null, null, posIncrements, null)
        }

        @Throws(IOException::class)
        fun assertAnalyzesToPositions(
            a: Analyzer,
            input: String,
            output: Array<String>,
            posIncrements: IntArray,
            posLengths: IntArray
        ) {
            assertAnalyzesTo(a, input, output, null, null, null, posIncrements, posLengths)
        }

        @Throws(IOException::class)
        fun assertAnalyzesToPositions(
            a: Analyzer,
            input: String,
            output: Array<String>,
            types: Array<String>,
            posIncrements: IntArray,
            posLengths: IntArray
        ) {
            assertAnalyzesTo(a, input, output, null, null, types, posIncrements, posLengths)
        }

        @Throws(IOException::class)
        fun assertAnalyzesTo(
            a: Analyzer,
            input: String,
            output: Array<String>,
            startOffsets: IntArray,
            endOffsets: IntArray,
            posIncrements: IntArray
        ) {
            assertAnalyzesTo(a, input, output, startOffsets, endOffsets, null, posIncrements, null)
        }

        @Throws(IOException::class)
        fun checkResetException(a: Analyzer, input: String) {
            var ts: TokenStream = a.tokenStream("bogus", input)
            try {
                if (ts.incrementToken()) {
                    // System.out.println(ts.reflectAsString(false));
                    fail("didn't get expected exception when reset() not called")
                }
            } catch (expected: IllegalStateException) {
                // ok
            } catch (unexpected: Exception) {
                unexpected.printStackTrace(/*java.lang.System.err*/)
                fail("got wrong exception when reset() not called: $unexpected")
            } finally {
                // consume correctly
                ts.reset()
                while (ts.incrementToken()) {
                }
                ts.end()
                ts.close()
            }

            // check for a missing close()
            ts = a.tokenStream("bogus", input)
            ts.reset()
            while (ts.incrementToken()) {
            }
            ts.end()
            try {
                ts = a.tokenStream("bogus", input)
                fail("didn't get expected exception when close() not called")
            } catch (expected: IllegalStateException) {
                // ok
            } finally {
                ts.close()
            }
        }

        // simple utility method for testing stemmers
        @Throws(IOException::class)
        fun checkOneTerm(a: Analyzer, input: String, expected: String) {
            assertAnalyzesTo(a, input, arrayOf<String>(expected))
        }

        /**
         * utility method for blasting tokenstreams with data to make sure they don't do anything crazy
         *
         * @param simple true if only ascii strings will be used (try to avoid)
         */
        @Throws(IOException::class)
        fun checkRandomData(
            random: Random,
            a: Analyzer,
            iterations: Int,
            simple: Boolean
        ) {
            checkRandomData(random, a, iterations, 20, simple, true)
        }

        /** Asserts that the given stream has expected number of tokens.  */
        @Throws(IOException::class)
        fun assertStreamHasNumberOfTokens(
            ts: TokenStream,
            expectedCount: Int
        ) {
            ts.reset()
            var count = 0
            while (ts.incrementToken()) {
                count++
            }
            ts.end()
            assertEquals(
                expectedCount.toLong(),
                count.toLong(),
                "wrong number of tokens"
            )
        }

        /**
         * utility method for blasting tokenstreams with data to make sure they don't do anything crazy
         */
        /**
         * utility method for blasting tokenstreams with data to make sure they don't do anything crazy
         */
        @JvmOverloads
        @Throws(IOException::class)
        fun checkRandomData(
            random: Random,
            a: Analyzer,
            iterations: Int,
            maxWordLength: Int = 20,
            simple: Boolean = false,
            graphOffsetsAreCorrect: Boolean = true
        ) {
            checkResetException(a, "best effort")
            val seed: Long = random.nextLong()
            val useCharFilter: Boolean = random.nextBoolean()
            var dir: Directory? = null
            var iw: RandomIndexWriter? = null

            // TODO implement following if needed
            /*
            val postingsFormat: String = TestUtil.getPostingsFormat("dummy")
            val codecOk = iterations * maxWordLength < 100000 && !(postingsFormat == "SimpleText")
            if (LuceneTestCase.rarely(random) && codecOk) {
                dir = LuceneTestCase.newFSDirectory(
                    LuceneTestCase.createTempDir("bttc")
                )
                iw = RandomIndexWriter(Random(seed), dir!!, a)
            }*/
            var success = false
            try {
                checkRandomData(
                    Random(seed),
                    a,
                    iterations,
                    maxWordLength,
                    useCharFilter,
                    simple,
                    graphOffsetsAreCorrect,
                    iw
                )
                // now test with multiple threads: note we do the EXACT same thing we did before in each
                // thread,
                // so this should only really fail from another thread if it's an actual thread problem
                val numThreads: Int = TestUtil.nextInt(random, 2, 4)
                val startingGun: CountDownLatch =
                    CountDownLatch(1)
                val threads = kotlin.arrayOfNulls<AnalysisThread>(numThreads)
                for (i in threads.indices) {
                    threads[i] =
                        AnalysisThread(
                            seed,
                            startingGun,
                            a,
                            iterations,
                            maxWordLength,
                            useCharFilter,
                            simple,
                            graphOffsetsAreCorrect,
                            iw
                        )
                }
                for (i in threads.indices) {
                    threads[i]!!.start()
                }
                startingGun.countDown()
                for (i in threads.indices) {
                    try {
                        threads[i]!!.join()
                    } catch (e: InterruptedException) {
                        throw RuntimeException(e)
                    }
                }
                for (i in threads.indices) {
                    if (threads[i]!!.failed) {
                        throw RuntimeException("some thread(s) failed")
                    }
                }
                if (iw != null) {
                    iw.close()
                }
                success = true
            } finally {
                if (success) {
                    IOUtils.close(dir)
                } else {
                    IOUtils.closeWhileHandlingException(dir) // checkindex
                }
            }
        }

        @Throws(IOException::class)
        private fun checkRandomData(
            random: Random,
            a: Analyzer,
            iterations: Int,
            maxWordLength: Int,
            useCharFilter: Boolean,
            simple: Boolean,
            graphOffsetsAreCorrect: Boolean,
            iw: RandomIndexWriter?
        ) {
            var doc: Document? = null
            var field: Field? = null
            var currentField: Field? = null
            val bogus = StringReader("")
            if (iw != null) {
                doc = Document()
                val ft = FieldType(TextField.TYPE_NOT_STORED)
                if (random.nextBoolean()) {
                    ft.setStoreTermVectors(true)
                    ft.setStoreTermVectorOffsets(random.nextBoolean())
                    ft.setStoreTermVectorPositions(random.nextBoolean())
                    if (ft.storeTermVectorPositions()) {
                        ft.setStoreTermVectorPayloads(random.nextBoolean())
                    }
                }
                if (random.nextBoolean()) {
                    ft.setOmitNorms(true)
                }
                when (random.nextInt(4)) {
                    0 -> ft.setIndexOptions(IndexOptions.DOCS)
                    1 -> ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS)
                    2 -> ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
                    else -> ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
                }
                field = Field("dummy", bogus, ft)
                currentField = field
                doc.add(currentField)
            }

            for (i in 0..<iterations) {
                val text: String = TestUtil.randomAnalysisString(
                    random,
                    maxWordLength,
                    simple
                )

                try {
                    checkAnalysisConsistency(
                        random, a, useCharFilter, text, graphOffsetsAreCorrect, currentField
                    )
                    if (iw != null) {
                        if (random.nextInt(7) == 0) {
                            // pile up a multivalued field
                            val ft: IndexableFieldType = field!!.fieldType()
                            currentField = Field("dummy", bogus, ft)
                            doc!!.add(currentField)
                        } else {
                            iw.addDocument<IndexableField>(doc!!)
                            if (doc.getFields().size > 1) {
                                // back to 1 field
                                currentField = field
                                doc.removeFields("dummy")
                                doc.add(currentField!!)
                            }
                        }
                    }
                } catch (t: Throwable) {
                    // TODO: really we should pass a random seed to
                    // checkAnalysisConsistency then print it here too:
                    /*java.lang.System.err.*/println(
                        "TEST FAIL: useCharFilter=" + useCharFilter + " text='" + escape(text) + "'"
                    )
                    Rethrow.rethrow(t)
                }
            }
        }

        fun escape(s: String): String {
            var charUpto = 0
            val sb: StringBuilder = StringBuilder()
            while (charUpto < s.length) {
                val c = s.get(charUpto).code
                if (c == 0xa) {
                    // Strangely, you cannot put \ u000A into Java
                    // sources (not in a comment nor a string
                    // constant)...:
                    sb.append("\\n")
                } else if (c == 0xd) {
                    // ... nor \ u000D:
                    sb.append("\\r")
                } else if (c == '"'.code) {
                    sb.append("\\\"")
                } else if (c == '\\'.code) {
                    sb.append("\\\\")
                } else if (c >= 0x20 && c < 0x80) {
                    sb.append(c.toChar())
                } else {
                    // TODO: we can make ascii easier to read if we
                    // don't escape...
                    /*sb.append(String.format(java.util.Locale.ROOT, "\\u%04x", c))*/
                    val hex = c.toString(16).padStart(4, '0')
                    sb.append("\\u").append(hex)
                }
                charUpto++
            }
            return sb.toString()
        }

        @JvmOverloads
        @Throws(IOException::class)
        fun checkAnalysisConsistency(
            random: Random,
            a: Analyzer,
            useCharFilter: Boolean,
            text: String,
            graphOffsetsAreCorrect: Boolean = true
        ) {
            checkAnalysisConsistency(random, a, useCharFilter, text, graphOffsetsAreCorrect, null)
        }

        @Throws(IOException::class)
        private fun checkAnalysisConsistency(
            random: Random,
            a: Analyzer,
            useCharFilter: Boolean,
            text: String,
            graphOffsetsAreCorrect: Boolean,
            field: Field?
        ) {
            var random: Random = random
            if (VERBOSE) {
                println(
                    (/*java.lang.Thread.currentThread().getName()
                            +*/ ": NOTE: BaseTokenStreamTestCase: get first token stream now text="
                            + text)
                )
            }

            val remainder: Int = random.nextInt(10)
            var reader: Reader = StringReader(text)
            var ts: TokenStream =
                a.tokenStream(
                    "dummy",
                    if (useCharFilter) MockCharFilter(reader, remainder) else reader
                )
            val termAtt: CharTermAttribute = ts.getAttribute(CharTermAttribute::class)
            val offsetAtt: OffsetAttribute = ts.getAttribute(OffsetAttribute::class)
            val posIncAtt: PositionIncrementAttribute = ts.getAttribute(PositionIncrementAttribute::class)
            val posLengthAtt: PositionLengthAttribute = ts.getAttribute(PositionLengthAttribute::class)
            val typeAtt: TypeAttribute = ts.getAttribute(TypeAttribute::class)
            val tokens: MutableList<String> = mutableListOf()
            val types: MutableList<String> = mutableListOf()
            val positions: MutableList<Int> = mutableListOf()
            val positionLengths: MutableList<Int> = mutableListOf()
            val startOffsets: MutableList<Int> = mutableListOf()
            val endOffsets: MutableList<Int> = mutableListOf()
            ts.reset()

            // First pass: save away "correct" tokens
            while (ts.incrementToken()) {
                assertNotNull(termAtt, "has no CharTermAttribute")
                tokens.add(termAtt.toString())
                if (typeAtt != null) types.add(typeAtt.type())
                if (posIncAtt != null) positions.add(posIncAtt.getPositionIncrement())
                if (posLengthAtt != null) positionLengths.add(posLengthAtt.positionLength)
                if (offsetAtt != null) {
                    startOffsets.add(offsetAtt.startOffset())
                    endOffsets.add(offsetAtt.endOffset())
                }
            }
            ts.end()
            ts.close()

            // verify reusing is "reproducable" and also get the normal tokenstream sanity checks
            if (!tokens.isEmpty()) {
                // KWTokenizer (for example) can produce a token
                // even when input is length 0:

                if (text.length != 0) {
                    // (Optional) second pass: do something evil:

                    val evilness: Int = random.nextInt(50)
                    if (evilness == 17) {
                        if (LuceneTestCase.VERBOSE) {
                            println(
                                /*java.lang.Thread.currentThread().getName()
                                        +*/ ": NOTE: BaseTokenStreamTestCase: re-run analysis w/ exception"
                            )
                        }

                        // Throw an errant exception from the Reader:
                        val evilReader: MockReaderWrapper =
                            MockReaderWrapper(random, StringReader(text))
                        evilReader.throwExcAfterChar(random.nextInt(text.length + 1))
                        reader = evilReader

                        try {
                            // NOTE: some Tokenizers go and read characters
                            // when you call .setReader(Reader), eg
                            // PatternTokenizer.  This is a bit
                            // iffy... (really, they should only
                            // pull from the Reader when you call
                            // .incremenToken(), I think), but we
                            // currently allow it, so, we must call
                            // a.tokenStream inside the try since we may
                            // hit the exc on init:
                            ts =
                                a.tokenStream(
                                    "dummy",
                                    if (useCharFilter) MockCharFilter(reader, remainder) else reader
                                )
                            ts.reset()
                            while (ts.incrementToken()) {
                            }
                            fail("did not hit exception")
                        } catch (re: RuntimeException) {
                            assertTrue(MockReaderWrapper.isMyEvilException(re))
                        }
                        try {
                            ts.end()
                        } catch (ise: IllegalStateException) {
                            // Catch & ignore MockTokenizer's
                            // anger...
                            if (ise.message!!.contains("end() called in wrong state=")) {
                                // OK
                            } else {
                                throw ise
                            }
                        }
                        ts.close()
                    } else if (evilness == 7) {
                        // Only consume a subset of the tokens:
                        val numTokensToRead: Int = random.nextInt(tokens.size)
                        if (LuceneTestCase.VERBOSE) {
                            println(
                                (/*java.lang.Thread.currentThread().getName()
                                        + */": NOTE: BaseTokenStreamTestCase: re-run analysis, only consuming "
                                        + numTokensToRead
                                        + " of "
                                        + tokens.size
                                        + " tokens")
                            )
                        }

                        reader = StringReader(text)
                        ts =
                            a.tokenStream(
                                "dummy",
                                if (useCharFilter) MockCharFilter(reader, remainder) else reader
                            )
                        ts.reset()
                        for (tokenCount in 0..<numTokensToRead) {
                            assertTrue(ts.incrementToken())
                        }
                        try {
                            ts.end()
                        } catch (ise: IllegalStateException) {
                            // Catch & ignore MockTokenizer's
                            // anger...
                            if (ise.message!!.contains("end() called in wrong state=")) {
                                // OK
                            } else {
                                throw ise
                            }
                        }
                        ts.close()
                    }
                }
            }

            // Final pass: verify clean tokenization matches
            // results from first pass:
            if (VERBOSE) {
                println(
                    (/*java.lang.Thread.currentThread().getName()
                            +*/ ": NOTE: BaseTokenStreamTestCase: re-run analysis; "
                            + tokens.size
                            + " tokens")
                )
            }
            reader = StringReader(text)

            val seed: Long = random.nextLong()
            random = Random(seed)
            if (random.nextInt(30) == 7) {
                if (LuceneTestCase.VERBOSE) {
                    println(
                        /*java.lang.Thread.currentThread().getName()
                                + */": NOTE: BaseTokenStreamTestCase: using spoon-feed reader"
                    )
                }

                reader = MockReaderWrapper(random, reader)
            }

            ts = a.tokenStream(
                "dummy",
                if (useCharFilter) MockCharFilter(reader, remainder) else reader
            )
            if (typeAtt != null && posIncAtt != null && posLengthAtt != null && offsetAtt != null) {
                // offset + pos + posLength + type
                assertTokenStreamContents(
                    ts,
                    tokens.toTypedArray<String>(),
                    toIntArray(startOffsets),
                    toIntArray(endOffsets),
                    types.toTypedArray<String>(),
                    toIntArray(positions),
                    toIntArray(positionLengths),
                    text.length,
                    graphOffsetsAreCorrect
                )
            } else if (typeAtt != null && posIncAtt != null && offsetAtt != null) {
                // offset + pos + type
                assertTokenStreamContents(
                    ts,
                    tokens.toTypedArray<String>(),
                    toIntArray(startOffsets),
                    toIntArray(endOffsets),
                    types.toTypedArray<String>(),
                    toIntArray(positions),
                    null,
                    text.length,
                    graphOffsetsAreCorrect
                )
            } else if (posIncAtt != null && posLengthAtt != null && offsetAtt != null) {
                // offset + pos + posLength
                assertTokenStreamContents(
                    ts,
                    tokens.toTypedArray<String>(),
                    toIntArray(startOffsets),
                    toIntArray(endOffsets),
                    null,
                    toIntArray(positions),
                    toIntArray(positionLengths),
                    text.length,
                    graphOffsetsAreCorrect
                )
            } else if (posIncAtt != null && offsetAtt != null) {
                // offset + pos
                assertTokenStreamContents(
                    ts,
                    tokens.toTypedArray<String>(),
                    toIntArray(startOffsets),
                    toIntArray(endOffsets),
                    null,
                    toIntArray(positions),
                    null,
                    text.length,
                    graphOffsetsAreCorrect
                )
            } else if (offsetAtt != null) {
                // offset
                assertTokenStreamContents(
                    ts,
                    tokens.toTypedArray<String>(),
                    toIntArray(startOffsets),
                    toIntArray(endOffsets),
                    null,
                    null,
                    null,
                    text.length,
                    graphOffsetsAreCorrect
                )
            } else {
                // terms only
                assertTokenStreamContents(ts, tokens.toTypedArray<String>())
            }

            a.normalize("dummy", text)

            // TODO: what can we do besides testing that the above method does not throw
            if (field != null) {
                reader = StringReader(text)
                random = Random(seed)
                if (random.nextInt(30) == 7) {
                    if (LuceneTestCase.VERBOSE) {
                        println(
                            /*java.lang.Thread.currentThread().getName()
                                    +*/ ": NOTE: BaseTokenStreamTestCase: indexing using spoon-feed reader"
                        )
                    }

                    reader = MockReaderWrapper(random, reader)
                }

                field.setReaderValue(
                    if (useCharFilter) MockCharFilter(
                        reader,
                        remainder
                    ) else reader
                )
            }
        }

        private fun toIntArray(list: MutableList<Int>): IntArray {
            return list.map { obj: Int -> obj }.toIntArray()
        }

        @Throws(IOException::class)
        protected fun whitespaceMockTokenizer(input: Reader): MockTokenizer {
            val mockTokenizer: MockTokenizer =
                MockTokenizer(
                    MockTokenizer.WHITESPACE,
                    false
                )
            mockTokenizer.setReader(input)
            return mockTokenizer
        }

        @Throws(IOException::class)
        protected fun whitespaceMockTokenizer(input: String): MockTokenizer {
            val mockTokenizer: MockTokenizer =
                MockTokenizer(
                    MockTokenizer.WHITESPACE,
                    false
                )
            mockTokenizer.setReader(StringReader(input))
            return mockTokenizer
        }

        @Throws(IOException::class)
        protected fun keywordMockTokenizer(input: Reader): MockTokenizer {
            val mockTokenizer =
                MockTokenizer(
                    MockTokenizer.KEYWORD,
                    false
                )
            mockTokenizer.setReader(input)
            return mockTokenizer
        }

        @Throws(IOException::class)
        protected fun keywordMockTokenizer(input: String): MockTokenizer {
            val mockTokenizer =
                MockTokenizer(
                    MockTokenizer.KEYWORD,
                    false
                )
            mockTokenizer.setReader(StringReader(input))
            return mockTokenizer
        }

        /** Returns a random AttributeFactory impl  */
        /** Returns a random AttributeFactory impl  */
        @JvmOverloads
        fun newAttributeFactory(random: Random = LuceneTestCase.random()): AttributeFactory {
            when (random.nextInt(3)) {
                0 -> return TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY
                1 -> return Token.TOKEN_ATTRIBUTE_FACTORY
                2 -> return AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY
                else -> throw AssertionError("Please fix the Random.nextInt() call above")
            }
        }

        private fun toString(strings: MutableSet<String>): String {
            val stringsList: List<String> = strings.toList().sorted()
            /*java.util.Collections.sort<String>(stringsList)*/
            val b = StringBuilder()
            for (s in stringsList) {
                b.append("  ")
                b.append(s)
                b.append('\n')
            }
            return b.toString()
        }

        /**
         * Enumerates all accepted strings in the token graph created by the analyzer on the provided
         * text, and then asserts that it's equal to the expected strings. Uses [ ] to create an automaton. Asserts the finite strings of the automaton are
         * all and only the given valid strings.
         *
         * @param analyzer analyzer containing the SynonymFilter under test.
         * @param text text to be analyzed.
         * @param expectedStrings all expected finite strings.
         */
        @Throws(IOException::class)
        fun assertGraphStrings(
            analyzer: Analyzer,
            text: String,
            vararg expectedStrings: String
        ) {
            checkAnalysisConsistency(
                LuceneTestCase.random(),
                analyzer,
                true,
                text,
                true
            )
            analyzer.tokenStream("dummy", text).use { tokenStream ->
                assertGraphStrings(tokenStream, *expectedStrings)
            }
        }

        /**
         * Enumerates all accepted strings in the token graph created by the already initialized [ ].
         */
        @Throws(IOException::class)
        fun assertGraphStrings(
            tokenStream: TokenStream,
            vararg expectedStrings: String
        ) {
            val automaton: Automaton = TokenStreamToAutomaton().toAutomaton(tokenStream)
            val actualStringPaths: Set<IntsRef> =
                AutomatonTestUtil.getFiniteStringsRecursive(
                    automaton,
                    -1
                )

            val expectedStringsSet: Set<String> = expectedStrings.toSet()

            val scratchBytesRefBuilder = BytesRefBuilder()
            val actualStrings: MutableSet<String> = mutableSetOf()
            for (ir in actualStringPaths) {
                actualStrings.add(
                    Util.toBytesRef(ir, scratchBytesRefBuilder)
                        .utf8ToString()
                        .replace(TokenStreamToAutomaton.POS_SEP.toChar(), ' ')
                )
            }
            for (s in actualStrings) {
                assertTrue(
                    expectedStringsSet.contains(s),
                    ("Analyzer created unexpected string path: "
                            + s
                            + "\nexpected:\n"
                            + expectedStringsSet.toString()
                            + "\nactual:\n"
                            + toString(actualStrings))
                )
            }
            for (s in expectedStrings) {
                assertTrue(
                    actualStrings.contains(s),
                    ("Analyzer created unexpected string path: "
                            + s
                            + "\nexpected:\n"
                            + expectedStringsSet.toString()
                            + "\nactual:\n"
                            + toString(actualStrings))
                )
            }
        }

        /**
         * Returns all paths accepted by the token stream graph produced by analyzing text with the
         * provided analyzer. The tokens [CharTermAttribute] values are concatenated, and separated
         * with space.
         */
        @Throws(IOException::class)
        fun getGraphStrings(
            analyzer: Analyzer,
            text: String
        ): MutableSet<String> {
            analyzer.tokenStream("dummy", text).use { tokenStream ->
                return getGraphStrings(tokenStream)
            }
        }

        /**
         * Returns all paths accepted by the token stream graph produced by the already initialized [ ].
         */
        @Throws(IOException::class)
        fun getGraphStrings(tokenStream: TokenStream): MutableSet<String> {
            val automaton: Automaton = TokenStreamToAutomaton().toAutomaton(tokenStream)
            val actualStringPaths: Set<IntsRef> =
                AutomatonTestUtil.getFiniteStringsRecursive(
                    automaton,
                    -1
                )
            val scratchBytesRefBuilder = BytesRefBuilder()
            val paths: MutableSet<String> = mutableSetOf()
            for (ir in actualStringPaths) {
                paths.add(
                    Util.toBytesRef(ir, scratchBytesRefBuilder)
                        .utf8ToString()
                        .replace(TokenStreamToAutomaton.POS_SEP.toChar(), ' ')
                )
            }
            return paths
        }

        /** Returns a `String` summary of the tokens this analyzer produces on this text  */
        @Throws(IOException::class)
        fun toString(analyzer: Analyzer, text: String): String {
            analyzer.tokenStream("field", text).use { ts ->
                val b = StringBuilder()
                val termAtt: CharTermAttribute = ts.getAttribute(CharTermAttribute::class)
                val posIncAtt: PositionIncrementAttribute = ts.getAttribute(PositionIncrementAttribute::class)
                val posLengthAtt: PositionLengthAttribute = ts.getAttribute(PositionLengthAttribute::class)
                val offsetAtt: OffsetAttribute = ts.getAttribute(OffsetAttribute::class)
                assertNotNull(offsetAtt)
                ts.reset()
                var pos = -1
                while (ts.incrementToken()) {
                    pos += posIncAtt.getPositionIncrement()
                    b.append(termAtt)
                    b.append(" at pos=")
                    b.append(pos)
                    if (posLengthAtt != null) {
                        b.append(" to pos=")
                        b.append(pos + posLengthAtt.positionLength)
                    }
                    b.append(" offsets=")
                    b.append(offsetAtt.startOffset())
                    b.append('-')
                    b.append(offsetAtt.endOffset())
                    b.append('\n')
                }
                ts.end()
                return b.toString()
            }
        }
    }
}
