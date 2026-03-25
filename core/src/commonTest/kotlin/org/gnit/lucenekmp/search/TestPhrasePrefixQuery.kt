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

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.index.MultiTerms
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals

/** This class tests PhrasePrefixQuery class. */
class TestPhrasePrefixQuery : LuceneTestCase() {
    /** */
    @Test
    @Throws(IOException::class)
    fun testPhrasePrefix() {
        val indexStore = newDirectory()
        val writer = RandomIndexWriter(random(), indexStore)
        val doc1 = Document()
        val doc2 = Document()
        val doc3 = Document()
        val doc4 = Document()
        val doc5 = Document()
        doc1.add(newTextField("body", "blueberry pie", Field.Store.YES))
        doc2.add(newTextField("body", "blueberry strudel", Field.Store.YES))
        doc3.add(newTextField("body", "blueberry pizza", Field.Store.YES))
        doc4.add(newTextField("body", "blueberry chewing gum", Field.Store.YES))
        doc5.add(newTextField("body", "piccadilly circus", Field.Store.YES))
        writer.addDocument(doc1)
        writer.addDocument(doc2)
        writer.addDocument(doc3)
        writer.addDocument(doc4)
        writer.addDocument(doc5)
        val reader = writer.reader
        writer.close()

        val searcher = newSearcher(reader)

        // PhrasePrefixQuery query1 = new PhrasePrefixQuery();
        val query1builder = MultiPhraseQuery.Builder()
        // PhrasePrefixQuery query2 = new PhrasePrefixQuery();
        val query2builder = MultiPhraseQuery.Builder()
        query1builder.add(Term("body", "blueberry"))
        query2builder.add(Term("body", "strawberry"))

        val termsWithPrefix = mutableListOf<Term>()

        // this TermEnum gives "piccadilly", "pie" and "pizza".
        val prefix = "pi"
        val te = MultiTerms.getTerms(reader, "body")!!.iterator()
        te.seekCeil(BytesRef(prefix))
        do {
            val s = te.term()!!.utf8ToString()
            if (s.startsWith(prefix)) {
                termsWithPrefix.add(Term("body", s))
            } else {
                break
            }
        } while (te.next() != null)

        query1builder.add(termsWithPrefix.toTypedArray())
        query2builder.add(termsWithPrefix.toTypedArray())

        var result: Array<ScoreDoc>
        result = searcher.search(query1builder.build(), 1000).scoreDocs
        assertEquals(2, result.size)

        result = searcher.search(query2builder.build(), 1000).scoreDocs
        assertEquals(0, result.size)
        reader.close()
        indexStore.close()
    }
}
