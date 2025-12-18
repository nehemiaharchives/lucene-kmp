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

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.TermStatistics
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.tests.analysis.CannedTokenStream
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.analysis.Token
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.Test
import kotlin.test.assertEquals

class TestFieldInvertState : LuceneTestCase() {

    /** Similarity holds onto the FieldInvertState for subsequent verification. */
    private class NeverForgetsSimilarity : Similarity() {
        var lastState: FieldInvertState? = null

        override fun computeNorm(state: FieldInvertState): Long {
            lastState = state
            return 1
        }

        override fun scorer(
            boost: Float,
            collectionStats: CollectionStatistics,
            vararg termStats: TermStatistics
        ): SimScorer {
            throw UnsupportedOperationException()
        }

        companion object {
            val INSTANCE = NeverForgetsSimilarity()
        }
    }

    @Test
    fun testBasic() {
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.similarity = NeverForgetsSimilarity.INSTANCE
        val w = IndexWriter(dir, iwc)
        val doc = Document()
        val field = Field(
            "field",
            CannedTokenStream(
                Token("a", 0, 1),
                Token("b", 2, 3),
                Token("c", 4, 5)
            ),
            TextField.TYPE_NOT_STORED
        )
        doc.add(field)
        w.addDocument(doc)
        val fis = NeverForgetsSimilarity.INSTANCE.lastState!!
        assertEquals(1, fis.maxTermFrequency)
        assertEquals(3, fis.uniqueTermCount)
        assertEquals(0, fis.numOverlap)
        assertEquals(3, fis.length)
        IOUtils.close(w, dir)
    }

    @Test
    fun testRandom() {
        val numUniqueTokens = TestUtil.nextInt(random(), 1, 25)
        val dir: Directory = newDirectory()
        val iwc = IndexWriterConfig(MockAnalyzer(random()))
        iwc.similarity = NeverForgetsSimilarity.INSTANCE
        val w = IndexWriter(dir, iwc)
        val doc = Document()

        val numTokens = atLeast(10000)
        val tokens = Array(numTokens) { Token() }
        val counts = mutableMapOf<Char, Int>()
        var numStacked = 0
        var maxTermFreq = 0
        var pos = -1
        for (i in 0 until numTokens) {
            val tokenChar = ('a'.code + random().nextInt(numUniqueTokens)).toChar()
            val oldCount = counts[tokenChar]
            val newCount = if (oldCount == null) 1 else 1 + oldCount
            counts[tokenChar] = newCount
            maxTermFreq = maxOf(maxTermFreq, newCount)

            val token = Token(tokenChar.toString(), 2 * i, 2 * i + 1)
            if (i > 0 && random().nextInt(7) == 3) {
                token.setPositionIncrement(0)
                numStacked++
            } else {
                pos++
            }
            tokens[i] = token
        }

        val field = Field("field", CannedTokenStream(*tokens), TextField.TYPE_NOT_STORED)
        doc.add(field)
        w.addDocument(doc)
        val fis = NeverForgetsSimilarity.INSTANCE.lastState!!
        assertEquals(maxTermFreq, fis.maxTermFrequency)
        assertEquals(counts.size, fis.uniqueTermCount)
        assertEquals(numStacked, fis.numOverlap)
        assertEquals(numTokens, fis.length)
        assertEquals(pos, fis.position)

        IOUtils.close(w, dir)
    }

    private fun newDirectory(): Directory = ByteBuffersDirectory()
}

