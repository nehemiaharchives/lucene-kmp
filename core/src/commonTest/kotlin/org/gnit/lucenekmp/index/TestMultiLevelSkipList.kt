@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FilterDirectory
import org.gnit.lucenekmp.store.FilterIndexInput
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * This testcase tests whether multi-level skipping is being used to reduce I/O while skipping
 * through posting lists.
 *
 * Skipping in general is already covered by several other testcases.
 */
class TestMultiLevelSkipList : LuceneTestCase() {
    inner class CountingDirectory(delegate: Directory) : FilterDirectory(delegate) {
        @Throws(IOException::class)
        override fun openInput(fileName: String, context: IOContext): IndexInput {
            var input = super.openInput(fileName, context)
            if (fileName.endsWith(".frq")) input = CountingStream(input)
            return input
        }
    }

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        counter = 0
    }

    @Test
    @Throws(IOException::class)
    fun testSimpleSkip() {
        val dir: Directory = CountingDirectory(ByteBuffersDirectory())
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(PayloadAnalyzer())
                    .setCodec(TestUtil.alwaysPostingsFormat(TestUtil.getDefaultPostingsFormat()))
                    .setMergePolicy(newLogMergePolicy())
            )
        val term = Term("test", "a")
        for (i in 0 until 5000) {
            val d1 = Document()
            d1.add(newTextField(term.field(), term.text(), Field.Store.NO))
            writer.addDocument(d1)
        }
        writer.commit()
        writer.forceMerge(1)
        writer.close()

        val reader = getOnlyLeafReader(DirectoryReader.open(dir))

        for (i in 0 until 2) {
            counter = 0
            val tp = reader.postings(term, PostingsEnum.ALL.toInt())!!
            checkSkipTo(tp, 14, 185) // no skips
            checkSkipTo(tp, 17, 190) // one skip on level 0
            checkSkipTo(tp, 287, 200) // one skip on level 1, two on level 0

            // this test would fail if we had only one skip level,
            // because than more bytes would be read from the freqStream
            checkSkipTo(tp, 4800, 250) // one skip on level 2
        }
        reader.close()
        dir.close()
    }

    @Throws(IOException::class)
    fun checkSkipTo(tp: PostingsEnum, target: Int, maxCounter: Int) {
        tp.advance(target)
        if (maxCounter < counter) {
            fail("Too many bytes read: $counter vs $maxCounter")
        }

        assertEquals(target, tp.docID(), "Wrong document ${tp.docID()} after skipTo target $target")
        assertEquals(1, tp.freq(), "Frequency is not 1: ${tp.freq()}")
        tp.nextPosition()
        val b = tp.payload
        assertEquals(1, b!!.length)
        assertEquals(target.toByte(), b.bytes[b.offset], "Wrong payload for the target $target: ${b.bytes[b.offset]}")
    }

    private class PayloadAnalyzer : Analyzer() {
        private val payloadCount = AtomicInteger(-1)

        override fun createComponents(fieldName: String): TokenStreamComponents {
            val tokenizer: Tokenizer = MockTokenizer(MockTokenizer.WHITESPACE, true)
            return TokenStreamComponents(tokenizer, PayloadFilter(payloadCount, tokenizer))
        }
    }

    private class PayloadFilter(private val payloadCount: AtomicInteger, input: TokenStream) :
        TokenFilter(input) {
        private val payloadAtt = addAttribute(PayloadAttribute::class)

        @Throws(IOException::class)
        override fun incrementToken(): Boolean {
            val hasNext = input.incrementToken()
            if (hasNext) {
                payloadAtt.payload = BytesRef(byteArrayOf(payloadCount.incrementAndFetch().toByte()))
            }
            return hasNext
        }
    }

    private var counter = 0

    // Simply extends IndexInput in a way that we are able to count the number
    // of bytes read
    inner class CountingStream(input: IndexInput) : FilterIndexInput("CountingStream($input)", input) {
        @Throws(IOException::class)
        override fun readByte(): Byte {
            counter++
            return `in`.readByte()
        }

        @Throws(IOException::class)
        override fun readBytes(b: ByteArray, offset: Int, len: Int) {
            counter += len
            `in`.readBytes(b, offset, len)
        }

        override fun clone(): CountingStream {
            return CountingStream(`in`.clone())
        }

        @Throws(IOException::class)
        override fun slice(sliceDescription: String, offset: Long, length: Long): IndexInput {
            return CountingStream(`in`.slice(sliceDescription, offset, length))
        }
    }
}
