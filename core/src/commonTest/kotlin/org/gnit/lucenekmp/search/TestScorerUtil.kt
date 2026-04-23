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
import org.gnit.lucenekmp.document.FeatureField
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.ImpactsEnum
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.NoMergePolicy
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.LuceneTestCase.Companion.AwaitsFix
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.SparseFixedBitSet
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestScorerUtil : LuceneTestCase() {

    @Test
    @Throws(IOException::class)
    fun testLikelyFixedBits() {
        assertNull(ScorerUtil.likelyLiveDocs(null))

        val bits1: Bits = SparseFixedBitSet(10)
        assertNotSame(bits1, ScorerUtil.likelyLiveDocs(bits1))
        val bits2: Bits = Bits.MatchAllBits(10)
        assertNotSame(bits2, ScorerUtil.likelyLiveDocs(bits2))
        assertEquals(
            ScorerUtil.likelyLiveDocs(bits1)!!::class,
            ScorerUtil.likelyLiveDocs(bits2)!!::class
        )

        ByteBuffersDirectory().use { dir ->
            IndexWriter(
                dir,
                IndexWriterConfig()
                    .setCodec(TestUtil.getDefaultCodec())
                    .setMergePolicy(NoMergePolicy.INSTANCE)
            ).use { w ->
                var doc = Document()
                doc.add(StringField("id", "1", Store.NO))
                w.addDocument(doc)
                doc = Document()
                doc.add(StringField("id", "2", Store.NO))
                w.addDocument(doc)
                w.deleteDocuments(Term("id", "1"))
                DirectoryReader.open(w).use { reader ->
                    val leafReader: LeafReader = reader.leaves()[0].reader()
                    val acceptDocs = leafReader.liveDocs
                    assertNotNull(acceptDocs)
                    assertSame(acceptDocs, ScorerUtil.likelyLiveDocs(acceptDocs))
                }
            }
        }
    }

    @Ignore
    @Test
    @AwaitsFix(bugUrl = "https://github.com/apache/lucene/issues/14303")
    @Throws(IOException::class)
    fun testLikelyImpactsEnum() {
        val iterator = DocIdSetIterator.all(10)
        assertTrue(ScorerUtil.likelyImpactsEnum(iterator) is FilterDocIdSetIterator)

        ByteBuffersDirectory().use { dir ->
            IndexWriter(dir, IndexWriterConfig().setCodec(TestUtil.getDefaultCodec())).use { w ->
                val doc = Document()
                doc.add(FeatureField("field", "value", 1f))
                w.addDocument(doc)
                DirectoryReader.open(w).use { reader ->
                    val leafReader: LeafReader = reader.leaves()[0].reader()
                    val te: TermsEnum = leafReader.terms("field")!!.iterator()
                    assertTrue(te.seekExact(BytesRef("value")))
                    val ie: ImpactsEnum = te.impacts(PostingsEnum.FREQS.toInt())
                    assertSame(ie, ScorerUtil.likelyImpactsEnum(ie))
                }
            }
        }
    }
}
