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
package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.tokenattributes.CharTermAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.MultiTerms
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals

/** Term position unit test. */
class TestPositionIncrement : LuceneTestCase() {
    @Test
    fun testSetPosition() {
        val analyzer: Analyzer =
            object : Analyzer() {
                override fun createComponents(fieldName: String): TokenStreamComponents {
                    return TokenStreamComponents(
                        object : Tokenizer() {
                            // TODO: use CannedTokenStream
                            private val TOKENS = arrayOf("1", "2", "3", "4", "5")
                            private val INCREMENTS = intArrayOf(1, 2, 1, 0, 1)
                            private var i = 0

                            val posIncrAtt: PositionIncrementAttribute =
                                addAttribute(PositionIncrementAttribute::class)
                            val termAtt: CharTermAttribute = addAttribute(CharTermAttribute::class)
                            val offsetAtt: OffsetAttribute = addAttribute(OffsetAttribute::class)

                            override fun incrementToken(): Boolean {
                                if (i == TOKENS.size) return false
                                clearAttributes()
                                termAtt.append(TOKENS[i])
                                offsetAtt.setOffset(i, i)
                                posIncrAtt.setPositionIncrement(INCREMENTS[i])
                                i++
                                return true
                            }

                            override fun reset() {
                                super.reset()
                                this.i = 0
                            }
                        }
                    )
                }
            }
        val store: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), store, analyzer)
        val d = Document()
        d.add(newTextField("field", "bogus", Field.Store.YES))
        writer.addDocument(d)
        val reader: IndexReader = writer.getReader(true, false)
        writer.close()

        val searcher = newSearcher(reader)

        var pos: PostingsEnum? =
            MultiTerms.getTermPostingsEnum(searcher.indexReader, "field", BytesRef("1"))
        pos!!.nextDoc()
        // first token should be at position 0
        assertEquals(0, pos.nextPosition())

        pos = MultiTerms.getTermPostingsEnum(searcher.indexReader, "field", BytesRef("2"))
        pos!!.nextDoc()
        // second token should be at position 2
        assertEquals(2, pos.nextPosition())

        var q: PhraseQuery
        var hits: Array<ScoreDoc>

        q = PhraseQuery("field", "1", "2")
        hits = searcher.search(q, 1000).scoreDocs
        assertEquals(0, hits.size)

        // same as previous, using the builder with implicit positions
        var builder = PhraseQuery.Builder()
        builder.add(Term("field", "1"))
        builder.add(Term("field", "2"))
        q = builder.build()
        hits = searcher.search(q, 1000).scoreDocs
        assertEquals(0, hits.size)

        // same as previous, just specify positions explicitely.
        builder = PhraseQuery.Builder()
        builder.add(Term("field", "1"), 0)
        builder.add(Term("field", "2"), 1)
        q = builder.build()
        hits = searcher.search(q, 1000).scoreDocs
        assertEquals(0, hits.size)

        // specifying correct positions should find the phrase.
        builder = PhraseQuery.Builder()
        builder.add(Term("field", "1"), 0)
        builder.add(Term("field", "2"), 2)
        q = builder.build()
        hits = searcher.search(q, 1000).scoreDocs
        assertEquals(1, hits.size)

        q = PhraseQuery("field", "2", "3")
        hits = searcher.search(q, 1000).scoreDocs
        assertEquals(1, hits.size)

        q = PhraseQuery("field", "3", "4")
        hits = searcher.search(q, 1000).scoreDocs
        assertEquals(0, hits.size)

        // phrase query would find it when correct positions are specified.
        builder = PhraseQuery.Builder()
        builder.add(Term("field", "3"), 0)
        builder.add(Term("field", "4"), 0)
        q = builder.build()
        hits = searcher.search(q, 1000).scoreDocs
        assertEquals(1, hits.size)

        // phrase query should fail for non existing searched term
        // even if there exist another searched terms in the same searched position.
        builder = PhraseQuery.Builder()
        builder.add(Term("field", "3"), 0)
        builder.add(Term("field", "9"), 0)
        q = builder.build()
        hits = searcher.search(q, 1000).scoreDocs
        assertEquals(0, hits.size)

        // multi-phrase query should succed for non existing searched term
        // because there exist another searched terms in the same searched position.
        val mqb = MultiPhraseQuery.Builder()
        mqb.add(arrayOf(Term("field", "3"), Term("field", "9")), 0)
        hits = searcher.search(mqb.build(), 1000).scoreDocs
        assertEquals(1, hits.size)

        q = PhraseQuery("field", "2", "4")
        hits = searcher.search(q, 1000).scoreDocs
        assertEquals(1, hits.size)

        q = PhraseQuery("field", "3", "5")
        hits = searcher.search(q, 1000).scoreDocs
        assertEquals(1, hits.size)

        q = PhraseQuery("field", "4", "5")
        hits = searcher.search(q, 1000).scoreDocs
        assertEquals(1, hits.size)

        q = PhraseQuery("field", "2", "5")
        hits = searcher.search(q, 1000).scoreDocs
        assertEquals(0, hits.size)

        reader.close()
        store.close()
    }
}
