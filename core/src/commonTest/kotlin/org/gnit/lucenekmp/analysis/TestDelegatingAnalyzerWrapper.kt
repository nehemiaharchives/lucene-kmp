package org.gnit.lucenekmp.analysis

import okio.IOException
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockBytesAnalyzer
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals

class TestDelegatingAnalyzerWrapper : LuceneTestCase() {

    @Test
    fun testDelegatesNormalization() {
        val analyzer1 = MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)
        val w1 = object : DelegatingAnalyzerWrapper(Analyzer.GLOBAL_REUSE_STRATEGY) {
            override fun getWrappedAnalyzer(fieldName: String): Analyzer {
                return analyzer1
            }
        }
        assertEquals(BytesRef("Ab C"), w1.normalize("foo", "Ab C"))

        val analyzer2 = MockAnalyzer(random(), MockTokenizer.WHITESPACE, true)
        val w2 = object : DelegatingAnalyzerWrapper(Analyzer.GLOBAL_REUSE_STRATEGY) {
            override fun getWrappedAnalyzer(fieldName: String): Analyzer {
                return analyzer2
            }
        }
        assertEquals(BytesRef("ab c"), w2.normalize("foo", "Ab C"))
    }

    @Test
    fun testDelegatesAttributeFactory() {
        val analyzer1: Analyzer = MockBytesAnalyzer()
        val w1 = object : DelegatingAnalyzerWrapper(Analyzer.GLOBAL_REUSE_STRATEGY) {
            override fun getWrappedAnalyzer(fieldName: String): Analyzer {
                return analyzer1
            }
        }
        assertEquals(BytesRef(byteArrayOf(0x41, 0x00, 0x62, 0x00, 0x20, 0x00, 0x43, 0x00)), w1.normalize("foo", "Ab C"))
    }

    @Test
    fun testDelegatesCharFilter() {
        val analyzer1 = object : Analyzer() {
            override fun initReaderForNormalization(fieldName: String?, reader: Reader): Reader {
                return DummyCharFilter(reader, 'b', 'z')
            }

            override fun createComponents(fieldName: String): TokenStreamComponents {
                val tokenizer = MockTokenizer(attributeFactory(fieldName))
                return TokenStreamComponents(tokenizer)
            }
        }
        val w1 = object : DelegatingAnalyzerWrapper(Analyzer.GLOBAL_REUSE_STRATEGY) {
            override fun getWrappedAnalyzer(fieldName: String): Analyzer {
                return analyzer1
            }
        }
        assertEquals(BytesRef("az c"), w1.normalize("foo", "ab c"))
    }

    private class DummyCharFilter(input: Reader, private val match: Char, private val repl: Char) : CharFilter(input) {

        override fun correct(currentOff: Int): Int {
            return currentOff
        }

        @Throws(IOException::class)
        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            val read = input.read(cbuf, off, len)
            for (i in 0 until read) {
                if (cbuf[off + i] == match) {
                    cbuf[off + i] = repl
                }
            }
            return read
        }
    }
}
