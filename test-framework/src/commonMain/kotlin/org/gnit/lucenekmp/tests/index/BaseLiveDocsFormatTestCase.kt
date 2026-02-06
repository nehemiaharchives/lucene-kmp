package org.gnit.lucenekmp.tests.index

import okio.IOException
//import org.gnit.lucenekmp.tests.util.LuceneTestCase.Monster // TODO implement if possible and if needed
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.LiveDocsFormat
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.SegmentCommitInfo
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

/** Abstract class that performs basic testing of a codec's [LiveDocsFormat].  */
abstract class BaseLiveDocsFormatTestCase : LuceneTestCase() {
    /** Returns the codec to run tests against  */
    protected abstract val codec: Codec

    private var savedCodec: Codec? = null

    @BeforeTest
    @Throws(Exception::class)
    /*override*/ fun setUp() {
        /*super.setUp()*/
        // set the default codec, so adding test cases to this isn't fragile
        savedCodec = Codec.default
        Codec.default = this.codec
    }

    @AfterTest
    @Throws(Exception::class)
    /*override*/ fun tearDown() {
        Codec.default = savedCodec!! // restore
        /*super.tearDown()*/
    }

    @Throws(IOException::class)
    open fun testDenseLiveDocs() {
        val maxDoc: Int = TestUtil.nextInt(random(), 3, 1000)
        testSerialization(maxDoc, maxDoc - 1, false)
        testSerialization(maxDoc, maxDoc - 1, true)
    }

    @Throws(IOException::class)
    open fun testEmptyLiveDocs() {
        val maxDoc: Int = TestUtil.nextInt(random(), 3, 1000)
        testSerialization(maxDoc, 0, false)
        testSerialization(maxDoc, 0, true)
    }

    @Throws(IOException::class)
    open fun testSparseLiveDocs() {
        val maxDoc: Int = TestUtil.nextInt(random(), 3, 1000)
        testSerialization(maxDoc, 1, false)
        testSerialization(maxDoc, 1, true)
    }

    @Throws(IOException::class)
    open fun testOverflow() {
        if (!TEST_MONSTER) {
            return
        }
        testSerialization(
            IndexWriter.MAX_DOCS,
            IndexWriter.MAX_DOCS - 7,
            false
        )
    }

    @Throws(IOException::class)
    private fun testSerialization(maxDoc: Int, numLiveDocs: Int, fixedBitSet: Boolean) {
        val codec: Codec = Codec.default
        val format: LiveDocsFormat = codec.liveDocsFormat()

        val liveDocs = FixedBitSet(maxDoc)
        if (numLiveDocs > maxDoc / 2) {
            liveDocs.set(0, maxDoc)
            for (i in 0..<maxDoc - numLiveDocs) {
                var clearBit: Int
                do {
                    clearBit = random().nextInt(maxDoc)
                } while (liveDocs.get(clearBit) == false)
                liveDocs.clear(clearBit)
            }
        } else {
            for (i in 0..<numLiveDocs) {
                var setBit: Int
                do {
                    setBit = random().nextInt(maxDoc)
                } while (liveDocs.get(setBit))
                liveDocs.set(setBit)
            }
        }

        val bits: Bits
        if (fixedBitSet) {
            bits = liveDocs
        } else {
            // Make sure the impl doesn't only work with a FixedBitSet
            bits =
                object : Bits {
                    override fun get(index: Int): Boolean {
                        return liveDocs.get(index)
                    }

                    override fun length(): Int {
                        return liveDocs.length()
                    }
                }
        }

        val dir: Directory = newDirectory()
        val si = SegmentInfo(
                dir,
                Version.LATEST,
                Version.LATEST,
                "foo",
                maxDoc,
                random().nextBoolean(),
                false,
                codec,
                mutableMapOf(),
                StringHelper.randomId(),
                mutableMapOf(),
                null
            )
        var sci = SegmentCommitInfo(
                si,
                0,
                0,
                0,
                -1,
                -1,
                StringHelper.randomId()
            )
        format.writeLiveDocs(bits, dir, sci, maxDoc - numLiveDocs, IOContext.DEFAULT)

        sci = SegmentCommitInfo(
            si,
            maxDoc - numLiveDocs,
            0,
            1,
            -1,
            -1,
            StringHelper.randomId()
        )
        val bits2: Bits = format.readLiveDocs(dir, sci, IOContext.READONCE)
        assertEquals(maxDoc.toLong(), bits2.length().toLong())
        for (i in 0..<maxDoc) {
            assertEquals(bits.get(i), bits2.get(i))
        }
        dir.close()
    }
}
