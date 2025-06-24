package org.gnit.lucenekmp.tests.analysis

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.tokenattributes.*
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.Attribute
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import org.gnit.lucenekmp.util.BytesRef

/**
 * Partial Kotlin port of Lucene's BaseTokenStreamTestCase.
 * Only the minimal functionality needed by current tests is implemented.
 */
abstract class BaseTokenStreamTestCase : LuceneTestCase() {

    /** Attribute that records if clearAttributes() was called. */
    interface CheckClearAttributesAttribute : Attribute {
        fun getAndResetClearCalled(): Boolean
    }

    /** Simple implementation of [CheckClearAttributesAttribute]. */
    class CheckClearAttributesAttributeImpl : AttributeImpl(), CheckClearAttributesAttribute {
        private var clearCalled = false

        override fun getAndResetClearCalled(): Boolean {
            val result = clearCalled
            clearCalled = false
            return result
        }

        override fun clear() {
            clearCalled = true
        }

        override fun copyTo(target: AttributeImpl) {
            target.clear()
        }

        override fun reflectWith(reflector: AttributeReflector) {
            reflector.reflect(CheckClearAttributesAttribute::class, "clearCalled", clearCalled)
        }

        override fun hashCode(): Int {
            return 76137213 xor clearCalled.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            return other is CheckClearAttributesAttributeImpl && other.clearCalled == clearCalled
        }

        override fun newInstance(): AttributeImpl = CheckClearAttributesAttributeImpl()
    }

    companion object {
        /**
         * Assert the contents of a TokenStream. This is a simplified version of
         * Lucene's method supporting only a subset of parameters.
         */
        @Throws(Exception::class)
        fun assertTokenStreamContents(
            ts: TokenStream,
            output: Array<String>,
            startOffsets: IntArray? = null,
            endOffsets: IntArray? = null,
            types: Array<String>? = null,
            posIncrements: IntArray? = null,
            posLengths: IntArray? = null,
            finalOffset: Int? = null,
            finalPosInc: Int? = null
        ) {
            requireNotNull(output)
            val checkClearAtt = ts.addAttribute(CheckClearAttributesAttribute::class)
            val termAtt = if (output.isNotEmpty()) ts.addAttribute(CharTermAttribute::class) else null
            val offsetAtt = if (startOffsets != null || endOffsets != null || finalOffset != null) ts.addAttribute(OffsetAttribute::class) else null
            val typeAtt = types?.let { ts.addAttribute(TypeAttribute::class) }
            val posIncrAtt = if (posIncrements != null || finalPosInc != null) ts.addAttribute(PositionIncrementAttribute::class) else null
            val posLengthAtt = posLengths?.let { ts.addAttribute(PositionLengthAttribute::class) }
            // these attributes are not yet implemented in the Kotlin port
            val keywordAtt: Any? = null
            val payloadAtt: Any? = null
            val flagsAtt: Any? = null
            val boostAtt: Any? = null

            ts.reset()
            var i = 0
            while (ts.incrementToken()) {
                kotlin.test.assertTrue(i < output.size, "TokenStream has more tokens than expected")
                kotlin.test.assertTrue(checkClearAtt.getAndResetClearCalled(), "clearAttributes() was not called")
                termAtt?.let { kotlin.test.assertEquals(output[i], it.toString(), "term $i") }
                startOffsets?.let { kotlin.test.assertEquals(it[i], offsetAtt!!.startOffset(), "startOffset $i") }
                endOffsets?.let { kotlin.test.assertEquals(it[i], offsetAtt!!.endOffset(), "endOffset $i") }
                types?.let { kotlin.test.assertEquals(it[i], typeAtt!!.type(), "type $i") }
                posIncrements?.let { kotlin.test.assertEquals(it[i], posIncrAtt!!.getPositionIncrement(), "posIncrement $i") }
                posLengths?.let { kotlin.test.assertEquals(it[i], posLengthAtt!!.positionLength, "posLength $i") }
                // keyword attribute not supported yet
                i++
            }
            kotlin.test.assertEquals(output.size, i, "end of stream")
            ts.end()
            finalOffset?.let { kotlin.test.assertEquals(it, offsetAtt!!.endOffset(), "finalOffset") }
            finalPosInc?.let { kotlin.test.assertEquals(it, posIncrAtt!!.getPositionIncrement(), "finalPosInc") }
            ts.close()
        }

        @Throws(Exception::class)
        fun assertTokenStreamContents(ts: TokenStream, output: Array<String>) {
            assertTokenStreamContents(ts, output, null, null, null, null, null, null, null)
        }
    }
}

