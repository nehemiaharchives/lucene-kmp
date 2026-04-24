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
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.SortedDocValuesField
import org.gnit.lucenekmp.document.StoredField
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.StoredFields
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.UncheckedIOException
import org.gnit.lucenekmp.jdkport.computeIfAbsent
import org.gnit.lucenekmp.jdkport.withLock
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BitSetIterator
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.FixedBitSet
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** random sorting tests */
class TestSortRandom : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun testRandomStringSort() {
        testRandomStringSort(SortField.Type.STRING)
    }

    @Throws(Exception::class)
    private fun testRandomStringSort(type: SortField.Type) {
        val random = Random(random().nextLong())

        val NUM_DOCS = atLeast(100)
        val dir = newDirectory()
        val writer = RandomIndexWriter(random, dir)
        val allowDups = random.nextBoolean()
        val seen = HashSet<String>()
        val maxLength = TestUtil.nextInt(random, 5, 100)
        if (VERBOSE) {
            println("TEST: NUM_DOCS=$NUM_DOCS maxLength=$maxLength allowDups=$allowDups")
        }

        var numDocs = 0
        val docValues = ArrayList<BytesRef?>()
        // TODO: deletions
        while (numDocs < NUM_DOCS) {
            val doc = Document()

            // 10% of the time, the document is missing the value:
            val br: BytesRef?
            if (random().nextInt(10) != 7) {
                val string =
                    if (random.nextBoolean()) {
                        TestUtil.randomSimpleString(random, maxLength)
                    } else {
                        TestUtil.randomUnicodeString(random, maxLength)
                    }

                if (!allowDups) {
                    if (seen.contains(string)) {
                        continue
                    }
                    seen.add(string)
                }

                if (VERBOSE) {
                    println("  $numDocs: s=$string")
                }

                br = BytesRef(string)
                doc.add(SortedDocValuesField("stringdv", br))
                docValues.add(br)
            } else {
                br = null
                if (VERBOSE) {
                    println("  $numDocs: <missing>")
                }
                docValues.add(null)
            }

            doc.add(NumericDocValuesField("id", numDocs.toLong()))
            doc.add(StoredField("id", numDocs))
            writer.addDocument(doc)
            numDocs++

            if (random.nextInt(40) == 17) {
                // force flush
                writer.reader.close()
            }
        }

        val r = writer.reader
        writer.close()
        if (VERBOSE) {
            println("  reader=$r")
        }

        val s = newSearcher(r, false)
        val ITERS = atLeast(100)
        for (iter in 0 until ITERS) {
            val reverse = random.nextBoolean()

            val sf: SortField
            val sortMissingLast: Boolean
            sf = SortField("stringdv", type, reverse)
            sortMissingLast = random().nextBoolean()

            if (sortMissingLast) {
                sf.missingValue = SortField.STRING_LAST
            }

            val sort =
                if (random.nextBoolean()) {
                    Sort(sf)
                } else {
                    Sort(sf, SortField.FIELD_DOC)
                }
            val hitCount = TestUtil.nextInt(random, 1, r.maxDoc() + 20)
            val f = RandomQuery(random.nextLong(), random.nextFloat(), docValues)
            val hits = s.search(f, hitCount, sort, false)

            if (VERBOSE) {
                println(
                    "\nTEST: iter=$iter ${hits.totalHits} hits; topN=$hitCount; reverse=$reverse; sortMissingLast=$sortMissingLast sort=$sort"
                )
            }

            // Compute expected results:
            f.matchValues.sortWith(
                object : Comparator<BytesRef?> {
                    override fun compare(a: BytesRef?, b: BytesRef?): Int {
                        if (a == null) {
                            if (b == null) {
                                return 0
                            }
                            return if (sortMissingLast) {
                                1
                            } else {
                                -1
                            }
                        } else if (b == null) {
                            return if (sortMissingLast) {
                                -1
                            } else {
                                1
                            }
                        } else {
                            return a.compareTo(b)
                        }
                    }
                }
            )

            if (reverse) {
                f.matchValues.reverse()
            }
            val expected = f.matchValues
            if (VERBOSE) {
                println("  expected:")
                for (idx in expected.indices) {
                    val expectedValue = expected[idx]
                    println("    $idx: ${expectedValue?.utf8ToString() ?: "<missing>"}")
                    if (idx == hitCount - 1) {
                        break
                    }
                }
            }

            if (VERBOSE) {
                println("  actual:")
                val storedFields: StoredFields = s.storedFields()
                for (hitIDX in hits.scoreDocs.indices) {
                    val fd = hits.scoreDocs[hitIDX] as FieldDoc
                    val actualValue = fd.fields!![0] as BytesRef?

                    println(
                        "    $hitIDX: ${actualValue?.utf8ToString() ?: "<missing>"} id=${storedFields.document(fd.doc).get("id")}"
                    )
                }
            }
            for (hitIDX in hits.scoreDocs.indices) {
                val fd = hits.scoreDocs[hitIDX] as FieldDoc
                val br = expected[hitIDX]

                val br2 = fd.fields!![0] as BytesRef?

                assertEquals(br, br2)
            }
        }

        r.close()
        dir.close()
    }

    private class RandomQuery(
        private val seed: Long,
        private var density: Float,
        private val docValues: List<BytesRef?>
    ) : Query() {
        val matchValues: MutableList<BytesRef?> =
            ArrayList<BytesRef?>() /*Collections.synchronizedList(new ArrayList<BytesRef>())*/
        private val bitsets: MutableMap<LeafReaderContext, FixedBitSet> =
            HashMap<LeafReaderContext, FixedBitSet>() /*ConcurrentHashMap<LeafReaderContext, FixedBitSet>()*/
        private val lock = ReentrantLock()

        // density should be 0.0 ... 1.0

        override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
            return object : ConstantScoreWeight(this, boost) {
                @Throws(IOException::class)
                override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier {
                    val bits =
                        lock.withLock {
                            bitsets.computeIfAbsent(context) { _ ->
                                val random = Random(context.docBase.toLong() xor seed)
                                val maxDoc = context.reader().maxDoc()
                                try {
                                    val idSource: NumericDocValues? =
                                        DocValues.getNumeric(context.reader(), "id")
                                    assertNotNull(idSource)
                                    val bitset = FixedBitSet(maxDoc)
                                    for (docID in 0 until maxDoc) {
                                        assertEquals(docID, idSource.nextDoc())
                                        if (random.nextFloat() <= density) {
                                            bitset.set(docID)
                                            // System.out.println("  acc id=" + idSource.getInt(docID) + " docID=" +
                                            // docID);
                                            matchValues.add(docValues[idSource.longValue().toInt()])
                                        }
                                    }
                                    bitset
                                } catch (e: IOException) {
                                    throw UncheckedIOException(e)
                                }
                            }!!
                        }
                    // The bitset is built for the whole segment, the first time each leaf is seen. Every
                    // partition iterates through its own set of doc ids, using a separate iterator backed by
                    // the shared bitset.
                    val scorer =
                        ConstantScoreScorer(
                            score(),
                            scoreMode,
                            BitSetIterator(bits, bits.approximateCardinality().toLong())
                        )
                    return DefaultScorerSupplier(scorer)
                }

                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return false
                }
            }
        }

        override fun visit(visitor: QueryVisitor) {}

        override fun toString(field: String?): String {
            return "RandomFilter(density=$density)"
        }

        override fun equals(other: Any?): Boolean {
            return sameClassAs(other) && equalsTo(other as RandomQuery)
        }

        private fun equalsTo(other: RandomQuery): Boolean {
            return seed == other.seed && docValues === other.docValues
        }

        override fun hashCode(): Int {
            var h = Objects.hash(seed, density)
            h = 31 * h + docValues.hashCode()
            h = 31 * h + classHash()
            return h
        }
    }
}
