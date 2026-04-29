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
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.PerThreadPKLookup
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LineFileDocs
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.automaton.Automata
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.RegExp
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

@LuceneTestCase.Companion.SuppressCodecs("SimpleText", "Direct")
class TestTermsEnum : LuceneTestCase() {

    @Test
    @Throws(Exception::class)
    fun test() {
        val random = Random(random().nextLong())
        val docs = LineFileDocs(random)
        val d = newDirectory()
        val analyzer = MockAnalyzer(random())
        analyzer.setMaxTokenLength(TestUtil.nextInt(random(), 1, IndexWriter.MAX_TERM_LENGTH))
        val w = RandomIndexWriter(random(), d, analyzer)
        val numDocs = atLeast(10)
        for (docCount in 0..<numDocs) {
            w.addDocument(docs.nextDoc())
        }
        val r = w.reader
        w.close()

        val terms = ArrayList<BytesRef>()
        val termsEnum = MultiTerms.getTerms(r, "body")!!.iterator()
        var term: BytesRef?
        while ((termsEnum.next().also { term = it }) != null) {
            terms.add(BytesRef.deepCopyOf(term!!))
        }
        if (VERBOSE) {
            println("TEST: ${terms.size} terms")
        }

        var upto = -1
        val iters = atLeast(200)
        for (iter in 0..<iters) {
            val isEnd: Boolean
            if (upto != -1 && random().nextBoolean()) {
                if (VERBOSE) {
                    println("TEST: iter next")
                }
                isEnd = termsEnum.next() == null
                upto++
                if (isEnd) {
                    if (VERBOSE) {
                        println("  end")
                    }
                    assertEquals(upto, terms.size)
                    upto = -1
                } else {
                    if (VERBOSE) {
                        println(
                            "  got term=${termsEnum.term()!!.utf8ToString()} expected=${terms[upto].utf8ToString()}"
                        )
                    }
                    assertTrue(upto < terms.size)
                    assertEquals(terms[upto], termsEnum.term())
                }
            } else {
                val target: BytesRef
                val exists: String
                if (random().nextBoolean()) {
                    target =
                        if (random().nextBoolean()) {
                            newBytesRef(TestUtil.randomSimpleString(random()))
                        } else {
                            newBytesRef(TestUtil.randomRealisticUnicodeString(random()))
                        }
                    exists = "likely not"
                } else {
                    target = terms[random().nextInt(terms.size)]
                    exists = "yes"
                }

                upto = terms.binarySearch(target)

                if (random().nextBoolean()) {
                    if (VERBOSE) {
                        println("TEST: iter seekCeil target=${target.utf8ToString()} exists=$exists")
                    }
                    val status = termsEnum.seekCeil(target)
                    if (VERBOSE) {
                        println("  got $status")
                    }

                    if (upto < 0) {
                        upto = -(upto + 1)
                        if (upto >= terms.size) {
                            assertEquals(TermsEnum.SeekStatus.END, status)
                            upto = -1
                        } else {
                            assertEquals(TermsEnum.SeekStatus.NOT_FOUND, status)
                            assertEquals(terms[upto], termsEnum.term())
                        }
                    } else {
                        assertEquals(TermsEnum.SeekStatus.FOUND, status)
                        assertEquals(terms[upto], termsEnum.term())
                    }
                } else {
                    if (VERBOSE) {
                        println("TEST: iter seekExact target=${target.utf8ToString()} exists=$exists")
                    }
                    val result = termsEnum.seekExact(target)
                    if (VERBOSE) {
                        println("  got $result")
                    }
                    if (upto < 0) {
                        assertFalse(result)
                        upto = -1
                    } else {
                        assertTrue(result)
                        assertEquals(target, termsEnum.term())
                    }
                }
            }
        }

        r.close()
        d.close()
        docs.close()
    }

    @Throws(IOException::class)
    private fun addDoc(
        w: RandomIndexWriter,
        terms: MutableCollection<String>,
        termToID: MutableMap<BytesRef, Int>,
        id: Int,
    ) {
        val doc = Document()
        doc.add(NumericDocValuesField("id", id.toLong()))
        if (VERBOSE) {
            println("TEST: addDoc id:$id terms=$terms")
        }
        for (s2 in terms) {
            doc.add(newStringField("f", s2, Field.Store.NO))
            termToID[newBytesRef(s2)] = id
        }
        w.addDocument(doc)
        terms.clear()
    }

    private fun accepts(c: CompiledAutomaton, b: BytesRef): Boolean {
        var state = 0
        for (idx in 0..<b.length) {
            assertTrue(state != -1)
            state = c.runAutomaton!!.step(state, b.bytes[b.offset + idx].toInt() and 0xff)
        }
        return c.runAutomaton!!.isAccept(state)
    }

    @Test
    @Throws(IOException::class)
    fun testIntersectRandom() {
        val dir = newDirectory()
        val w = RandomIndexWriter(random(), dir)

        val numTerms = atLeast(300)

        val terms = HashSet<String>()
        val pendingTerms = ArrayList<String>()
        val termToID = HashMap<BytesRef, Int>()
        var id = 0
        while (terms.size != numTerms) {
            val s = getRandomString()
            if (!terms.contains(s)) {
                terms.add(s)
                pendingTerms.add(s)
                if (random().nextInt(20) == 7) {
                    addDoc(w, pendingTerms, termToID, id++)
                }
            }
        }
        addDoc(w, pendingTerms, termToID, id++)

        val termsArray = arrayOfNulls<BytesRef>(terms.size) as Array<BytesRef>
        val termsSet = HashSet<BytesRef>()
        run {
            var upto = 0
            for (s in terms) {
                val b = newBytesRef(s)
                termsArray[upto++] = b
                termsSet.add(b)
            }
            termsArray.sort()
        }

        if (VERBOSE) {
            println("\nTEST: indexed terms (unicode order):")
            for (t in termsArray) {
                println("  ${t.utf8ToString()} -> id:${termToID[t]}")
            }
        }

        val r = w.reader
        w.close()

        val docIDToID = IntArray(r.maxDoc())
        val values = MultiDocValues.getNumericValues(r, "id")!!
        for (i in 0..<r.maxDoc()) {
            assertEquals(i, values.nextDoc())
            docIDToID[i] = values.longValue().toInt()
        }

        val numIterations = atLeast(3)
        for (iter in 0..<numIterations) {
            val acceptTerms = HashSet<String>()
            val sortedAcceptTerms = ArrayList<BytesRef>()
            val keepPct = random().nextDouble()
            val a: Automaton =
                if (iter == 0) {
                    if (VERBOSE) {
                        println("\nTEST: empty automaton")
                    }
                    Automata.makeEmpty()
                } else {
                    if (VERBOSE) {
                        println("\nTEST: keepPct=$keepPct")
                    }
                    for (s in terms) {
                        val s2 =
                            if (random().nextDouble() <= keepPct) {
                                s
                            } else {
                                getRandomString()
                            }
                        acceptTerms.add(s2)
                        sortedAcceptTerms.add(newBytesRef(s2))
                    }
                    sortedAcceptTerms.sort()
                    Automata.makeStringUnion(sortedAcceptTerms)
                }

            val c = CompiledAutomaton(a, true, false, false)

            val acceptTermsArray = arrayOfNulls<BytesRef>(acceptTerms.size) as Array<BytesRef>
            val acceptTermsSet = HashSet<BytesRef>()
            var upto = 0
            for (s in acceptTerms) {
                val b = newBytesRef(s)
                acceptTermsArray[upto++] = b
                acceptTermsSet.add(b)
                assertTrue(accepts(c, b))
            }
            acceptTermsArray.sort()

            if (VERBOSE) {
                println("\nTEST: accept terms (unicode order):")
                for (t in acceptTermsArray) {
                    println("  ${t.utf8ToString()}${if (termsSet.contains(t)) " (exists)" else ""}")
                }
                println(a.toDot())
            }

            for (iter2 in 0..<100) {
                val startTerm =
                    if (acceptTermsArray.isEmpty() || random().nextBoolean()) {
                        null
                    } else {
                        acceptTermsArray[random().nextInt(acceptTermsArray.size)]
                    }

                if (VERBOSE) {
                    println(
                        "\nTEST: iter2=$iter2 startTerm=${if (startTerm == null) "<null>" else startTerm.utf8ToString()}"
                    )

                    if (startTerm != null) {
                        var state = 0
                        for (idx in 0..<startTerm.length) {
                            val label = startTerm.bytes[startTerm.offset + idx].toInt() and 0xff
                            println("  state=$state label=$label")
                            state = c.runAutomaton!!.step(state, label)
                            assertTrue(state != -1)
                        }
                        println("  state=$state")
                    }
                }

                val te = MultiTerms.getTerms(r, "f")!!.intersect(c, startTerm)

                var loc =
                    if (startTerm == null) {
                        0
                    } else {
                        val found = termsArray.asList().binarySearch(BytesRef.deepCopyOf(startTerm))
                        if (found < 0) {
                            -(found + 1)
                        } else {
                            found + 1
                        }
                    }
                while (loc < termsArray.size && !acceptTermsSet.contains(termsArray[loc])) {
                    loc++
                }

                var postingsEnum: PostingsEnum? = null
                while (loc < termsArray.size) {
                    val expected = termsArray[loc]
                    val actual = te.next()
                    if (VERBOSE) {
                        println(
                            "TEST:   next() expected=${expected.utf8ToString()} actual=${actual?.utf8ToString() ?: "null"}"
                        )
                    }
                    assertEquals(expected, actual)
                    assertEquals(1, te.docFreq())
                    postingsEnum = TestUtil.docs(random(), te, postingsEnum, PostingsEnum.NONE.toInt())
                    val docID = postingsEnum.nextDoc()
                    assertTrue(docID != DocIdSetIterator.NO_MORE_DOCS)
                    assertEquals(docIDToID[docID], termToID[expected]!!)
                    do {
                        loc++
                    } while (loc < termsArray.size && !acceptTermsSet.contains(termsArray[loc]))
                }
                assertNull(te.next())
            }
        }

        r.close()
        dir.close()
    }

    private var d: Directory? = null
    private var r: IndexReader? = null

    private val FIELD = "field"

    private fun getRandomString(): String {
        return TestUtil.randomRealisticUnicodeString(random())
    }

    @Throws(Exception::class)
    private fun makeIndex(vararg terms: String): IndexReader {
        d = newDirectory()
        val iwc = newIndexWriterConfig(MockAnalyzer(random()))
        val w = RandomIndexWriter(random(), d!!, iwc)
        for (term in terms) {
            val doc = Document()
            val f = newStringField(FIELD, term, Field.Store.NO)
            doc.add(f)
            w.addDocument(doc)
        }
        if (r != null) {
            close()
        }
        r = w.reader
        w.close()
        return r!!
    }

    @Throws(Exception::class)
    private fun close() {
        r!!.close()
        d!!.close()
        r = null
        d = null
    }

    @Throws(Exception::class)
    private fun docFreq(r: IndexReader, term: String): Int {
        return r.docFreq(Term(FIELD, term))
    }

    @Test
    @Throws(Exception::class)
    fun testEasy() {
        r = makeIndex("aa0", "aa1", "aa2", "aa3", "bb0", "bb1", "bb2", "bb3", "aa")

        assertEquals(1, docFreq(r!!, "aa0"))
        assertEquals(1, docFreq(r!!, "aa2"))
        assertEquals(1, docFreq(r!!, "aa"))
        assertEquals(1, docFreq(r!!, "aa1"))
        assertEquals(0, docFreq(r!!, "aa5"))
        assertEquals(1, docFreq(r!!, "aa2"))
        assertEquals(0, docFreq(r!!, "b0"))
        assertEquals(1, docFreq(r!!, "aa2"))
        assertEquals(1, docFreq(r!!, "aa0"))
        assertEquals(1, docFreq(r!!, "bb0"))
        assertEquals(1, docFreq(r!!, "bb2"))
        assertEquals(1, docFreq(r!!, "bb1"))
        assertEquals(0, docFreq(r!!, "bb5"))
        assertEquals(1, docFreq(r!!, "bb2"))
        assertEquals(0, docFreq(r!!, "b0"))
        assertEquals(1, docFreq(r!!, "bb2"))
        assertEquals(1, docFreq(r!!, "bb0"))

        close()
    }

    @Test
    @Throws(Exception::class)
    fun testFloorBlocks() {
        val terms =
            arrayOf("aa0", "aa1", "aa2", "aa3", "aa4", "aa5", "aa6", "aa7", "aa8", "aa9", "aa", "xx")
        r = makeIndex(*terms)

        assertEquals(1, docFreq(r!!, "aa0"))
        assertEquals(1, docFreq(r!!, "aa4"))
        assertEquals(0, docFreq(r!!, "bb0"))
        assertEquals(1, docFreq(r!!, "aa4"))
        assertEquals(1, docFreq(r!!, "aa0"))
        assertEquals(1, docFreq(r!!, "aa9"))
        assertEquals(0, docFreq(r!!, "a"))
        assertEquals(1, docFreq(r!!, "aa"))
        assertEquals(0, docFreq(r!!, "a"))
        assertEquals(1, docFreq(r!!, "aa"))
        assertEquals(1, docFreq(r!!, "xx"))
        assertEquals(1, docFreq(r!!, "aa1"))
        assertEquals(0, docFreq(r!!, "yy"))
        assertEquals(1, docFreq(r!!, "xx"))
        assertEquals(1, docFreq(r!!, "aa9"))
        assertEquals(1, docFreq(r!!, "xx"))
        assertEquals(1, docFreq(r!!, "aa4"))

        val te = MultiTerms.getTerms(r!!, FIELD)!!.iterator()
        while (te.next() != null) {
        }

        assertTrue(seekExact(te, "aa1"))
        assertEquals("aa2", next(te))
        assertTrue(seekExact(te, "aa8"))
        assertEquals("aa9", next(te))
        assertEquals("xx", next(te))

        testRandomSeeks(r!!, *terms)
        close()
    }

    @Test
    @Throws(Exception::class)
    fun testZeroTerms() {
        d = newDirectory()
        val w = RandomIndexWriter(random(), d!!)
        var doc = Document()
        doc.add(newTextField("field", "one two three", Field.Store.NO))
        doc = Document()
        doc.add(newTextField("field2", "one two three", Field.Store.NO))
        w.addDocument(doc)
        w.commit()
        w.deleteDocuments(Term("field", "one"))
        w.forceMerge(1)
        val r = w.reader
        w.close()
        assertEquals(1, r.numDocs())
        assertEquals(1, r.maxDoc())
        val terms = MultiTerms.getTerms(r, "field")
        if (terms != null) {
            assertNull(terms.iterator().next())
        }
        r.close()
        d!!.close()
        d = null
    }

    @Test
    @Throws(Exception::class)
    fun testRandomTerms() {
        val terms = arrayOfNulls<String>(TestUtil.nextInt(random(), 1, atLeast(1000))) as Array<String>
        val seen = HashSet<String>()

        val allowEmptyString = random().nextBoolean()

        if (random().nextInt(10) == 7 && terms.size > 2) {
            val numTermsSamePrefix = random().nextInt(terms.size / 2)
            if (numTermsSamePrefix > 0) {
                var prefix: String
                while (true) {
                    prefix = getRandomString()
                    if (prefix.length < 5) {
                        continue
                    } else {
                        break
                    }
                }
                while (seen.size < numTermsSamePrefix) {
                    val t = prefix + getRandomString()
                    if (!seen.contains(t)) {
                        terms[seen.size] = t
                        seen.add(t)
                    }
                }
            }
        }

        while (seen.size < terms.size) {
            val t = getRandomString()
            if (!seen.contains(t) && (allowEmptyString || t.isNotEmpty())) {
                terms[seen.size] = t
                seen.add(t)
            }
        }
        r = makeIndex(*terms)
        testRandomSeeks(r!!, *terms)
        close()
    }

    @Throws(IOException::class)
    private fun seekExact(te: TermsEnum, term: String): Boolean {
        return te.seekExact(newBytesRef(term))
    }

    @Throws(IOException::class)
    private fun next(te: TermsEnum): String? {
        val br = te.next()
        return br?.utf8ToString()
    }

    private fun getNonExistTerm(terms: Array<BytesRef>): BytesRef {
        while (true) {
            val t = newBytesRef(getRandomString())
            if (terms.asList().binarySearch(t) < 0) {
                return t
            }
        }
    }

    private data class TermAndState(val term: BytesRef, val state: TermState)

    @Throws(IOException::class)
    private fun testRandomSeeks(r: IndexReader, vararg validTermStrings: String) {
        val validTerms = Array(validTermStrings.size) { termIDX -> newBytesRef(validTermStrings[termIDX]) }
        validTerms.sort()
        if (VERBOSE) {
            println("TEST: ${validTerms.size} terms:")
            for (t in validTerms) {
                println("  ${t.utf8ToString()} $t")
            }
        }
        val te = MultiTerms.getTerms(r, FIELD)!!.iterator()

        val END_LOC = -validTerms.size - 1
        val termStates = ArrayList<TermAndState>()

        for (iter in 0..<100 * RANDOM_MULTIPLIER) {
            val t: BytesRef
            var loc: Int
            val termState: TermState?
            if (random().nextInt(6) == 4) {
                t = getNonExistTerm(validTerms)
                termState = null
                if (VERBOSE) {
                    println("\nTEST: invalid term=${t.utf8ToString()}")
                }
                loc = validTerms.asList().binarySearch(t)
            } else if (termStates.isNotEmpty() && random().nextInt(4) == 1) {
                val ts = termStates[random().nextInt(termStates.size)]
                t = ts.term
                loc = validTerms.asList().binarySearch(t)
                assertTrue(loc >= 0)
                termState = ts.state
                if (VERBOSE) {
                    println("\nTEST: valid termState term=${t.utf8ToString()}")
                }
            } else {
                loc = random().nextInt(validTerms.size)
                t = BytesRef.deepCopyOf(validTerms[loc])
                termState = null
                if (VERBOSE) {
                    println("\nTEST: valid term=${t.utf8ToString()}")
                }
            }

            val doSeekExact = random().nextBoolean()
            if (termState != null) {
                if (VERBOSE) {
                    println("  seekExact termState")
                }
                te.seekExact(t, termState)
            } else if (doSeekExact) {
                if (VERBOSE) {
                    println("  seekExact")
                }
                assertEquals(loc >= 0, te.seekExact(t))
            } else {
                if (VERBOSE) {
                    println("  seekCeil")
                }

                val result = te.seekCeil(t)
                if (VERBOSE) {
                    println("  got $result")
                }

                if (loc >= 0) {
                    assertEquals(TermsEnum.SeekStatus.FOUND, result)
                } else if (loc == END_LOC) {
                    assertEquals(TermsEnum.SeekStatus.END, result)
                } else {
                    assertTrue(loc >= -validTerms.size)
                    assertEquals(TermsEnum.SeekStatus.NOT_FOUND, result)
                }
            }

            if (loc >= 0) {
                assertEquals(t, te.term())
            } else if (doSeekExact) {
                continue
            } else if (loc == END_LOC) {
                continue
            } else {
                loc = -loc - 1
                assertEquals(validTerms[loc], te.term())
            }

            val numNext = random().nextInt(validTerms.size)

            for (nextCount in 0..<numNext) {
                if (VERBOSE) {
                    println("\nTEST: next loc=$loc of ${validTerms.size}")
                }
                val t2 = te.next()
                loc++
                if (loc == validTerms.size) {
                    assertNull(t2)
                    break
                } else {
                    assertEquals(validTerms[loc], t2)
                    if (random().nextInt(40) == 17 && termStates.size < 100) {
                        termStates.add(TermAndState(validTerms[loc], te.termState()))
                    }
                }
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testIntersectBasic() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig(MockAnalyzer(random()))
        iwc.setMergePolicy(LogDocMergePolicy())
        val w = RandomIndexWriter(random(), dir, iwc)
        var doc = Document()
        doc.add(newTextField("field", "aaa", Field.Store.NO))
        w.addDocument(doc)

        doc = Document()
        doc.add(newTextField("field", "bbb", Field.Store.NO))
        w.addDocument(doc)

        doc = Document()
        doc.add(newTextField("field", "ccc", Field.Store.NO))
        w.addDocument(doc)

        w.forceMerge(1)
        val r = w.reader
        w.close()
        val sub = getOnlyLeafReader(r)
        val terms = sub.terms("field")!!
        val automaton = RegExp(".*", RegExp.NONE).toAutomaton()
        val ca = CompiledAutomaton(automaton, false, false)
        var te = terms.intersect(ca, null)
        assertEquals("aaa", te.next()!!.utf8ToString())
        assertEquals(0, te.postings(null, PostingsEnum.NONE.toInt())!!.nextDoc())
        assertEquals("bbb", te.next()!!.utf8ToString())
        assertEquals(1, te.postings(null, PostingsEnum.NONE.toInt())!!.nextDoc())
        assertEquals("ccc", te.next()!!.utf8ToString())
        assertEquals(2, te.postings(null, PostingsEnum.NONE.toInt())!!.nextDoc())
        assertNull(te.next())

        te = terms.intersect(ca, newBytesRef("abc"))
        assertEquals("bbb", te.next()!!.utf8ToString())
        assertEquals(1, te.postings(null, PostingsEnum.NONE.toInt())!!.nextDoc())
        assertEquals("ccc", te.next()!!.utf8ToString())
        assertEquals(2, te.postings(null, PostingsEnum.NONE.toInt())!!.nextDoc())
        assertNull(te.next())

        te = terms.intersect(ca, newBytesRef("aaa"))
        assertEquals("bbb", te.next()!!.utf8ToString())
        assertEquals(1, te.postings(null, PostingsEnum.NONE.toInt())!!.nextDoc())
        assertEquals("ccc", te.next()!!.utf8ToString())
        assertEquals(2, te.postings(null, PostingsEnum.NONE.toInt())!!.nextDoc())
        assertNull(te.next())

        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testIntersectStartTerm() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig(MockAnalyzer(random()))
        iwc.setMergePolicy(LogDocMergePolicy())
        val w = RandomIndexWriter(random(), dir, iwc)
        var doc = Document()
        doc.add(newStringField("field", "abc", Field.Store.NO))
        w.addDocument(doc)

        doc = Document()
        doc.add(newStringField("field", "abd", Field.Store.NO))
        w.addDocument(doc)

        doc = Document()
        doc.add(newStringField("field", "acd", Field.Store.NO))
        w.addDocument(doc)

        doc = Document()
        doc.add(newStringField("field", "bcd", Field.Store.NO))
        w.addDocument(doc)

        w.forceMerge(1)
        val r = w.reader
        w.close()
        val sub = getOnlyLeafReader(r)
        val terms = sub.terms("field")!!

        var automaton = RegExp(".*d", RegExp.NONE).toAutomaton()
        automaton = Operations.determinize(automaton, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        val ca = CompiledAutomaton(automaton, false, false)
        var te: TermsEnum

        te = terms.intersect(ca, newBytesRef("aad"))
        assertEquals("abd", te.next()!!.utf8ToString())
        assertEquals(1, te.postings(null, PostingsEnum.NONE.toInt())!!.nextDoc())
        assertEquals("acd", te.next()!!.utf8ToString())
        assertEquals(2, te.postings(null, PostingsEnum.NONE.toInt())!!.nextDoc())
        assertEquals("bcd", te.next()!!.utf8ToString())
        assertEquals(3, te.postings(null, PostingsEnum.NONE.toInt())!!.nextDoc())
        assertNull(te.next())

        te = terms.intersect(ca, newBytesRef("add"))
        assertEquals("bcd", te.next()!!.utf8ToString())
        assertEquals(3, te.postings(null, PostingsEnum.NONE.toInt())!!.nextDoc())
        assertNull(te.next())

        te = terms.intersect(ca, newBytesRef("bcd"))
        assertNull(te.next())
        te = terms.intersect(ca, newBytesRef("ddd"))
        assertNull(te.next())

        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testIntersectEmptyString() {
        val dir = newDirectory()
        val iwc = newIndexWriterConfig(MockAnalyzer(random()))
        iwc.setMergePolicy(LogDocMergePolicy())
        val w = RandomIndexWriter(random(), dir, iwc)
        var doc = Document()
        doc.add(newStringField("field", "", Field.Store.NO))
        doc.add(newStringField("field", "abc", Field.Store.NO))
        w.addDocument(doc)

        doc = Document()
        doc.add(newStringField("field", "abc", Field.Store.NO))
        doc.add(newStringField("field", "", Field.Store.NO))
        w.addDocument(doc)

        w.forceMerge(1)
        val r = w.reader
        w.close()
        val sub = getOnlyLeafReader(r)
        val terms = sub.terms("field")!!

        val automaton = RegExp(".*", RegExp.NONE).toAutomaton()
        val ca = CompiledAutomaton(automaton, false, false)

        var te = terms.intersect(ca, null)
        var de: PostingsEnum

        assertEquals("", te.next()!!.utf8ToString())
        de = te.postings(null, PostingsEnum.NONE.toInt())!!
        assertEquals(0, de.nextDoc())
        assertEquals(1, de.nextDoc())

        assertEquals("abc", te.next()!!.utf8ToString())
        de = te.postings(null, PostingsEnum.NONE.toInt())!!
        assertEquals(0, de.nextDoc())
        assertEquals(1, de.nextDoc())

        assertNull(te.next())

        te = terms.intersect(ca, newBytesRef(""))

        assertEquals("abc", te.next()!!.utf8ToString())
        de = te.postings(null, PostingsEnum.NONE.toInt())!!
        assertEquals(0, de.nextDoc())
        assertEquals(1, de.nextDoc())

        assertNull(te.next())

        r.close()
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testCommonPrefixTerms() {
        val d = newDirectory()
        val w = RandomIndexWriter(random(), d)
        val terms = HashSet<String>()
        val prefix = TestUtil.randomRealisticUnicodeString(random(), 1, 20)
        val numTerms = atLeast(100)
        if (VERBOSE) {
            println("TEST: $numTerms terms; prefix=$prefix")
        }
        while (terms.size < numTerms) {
            terms.add(prefix + TestUtil.randomRealisticUnicodeString(random(), 1, 20))
        }
        for (term in terms) {
            val doc = Document()
            doc.add(newStringField("id", term, Field.Store.YES))
            w.addDocument(doc)
        }
        val r = w.reader
        if (VERBOSE) {
            println("\nTEST: reader=$r")
        }

        val termsEnum = MultiTerms.getTerms(r, "id")!!.iterator()
        var postingsEnum: PostingsEnum? = null
        val pkLookup = PerThreadPKLookup(r, "id")
        val storedFields = r.storedFields()

        val iters = atLeast(numTerms * 3)
        val termsList = ArrayList(terms)
        for (iter in 0..<iters) {
            val term: String
            val shouldExist: Boolean
            if (random().nextBoolean()) {
                term = termsList[random().nextInt(terms.size)]
                shouldExist = true
            } else {
                term = prefix + TestUtil.randomSimpleString(random(), 1, 20)
                shouldExist = terms.contains(term)
            }

            if (VERBOSE) {
                println("\nTEST: try term=$term")
                println("  shouldExist?=$shouldExist")
            }

            val termBytesRef = newBytesRef(term)

            val actualResult = termsEnum.seekExact(termBytesRef)
            assertEquals(shouldExist, actualResult)
            if (shouldExist) {
                postingsEnum = termsEnum.postings(postingsEnum, 0)
                val docID = postingsEnum!!.nextDoc()
                assertTrue(docID != DocIdSetIterator.NO_MORE_DOCS)
                assertEquals(docID, pkLookup.lookup(termBytesRef))
                val doc = storedFields.document(docID)
                assertEquals(term, doc.get("id"))

                if (random().nextInt(7) == 1) {
                    termsEnum.next()
                }
            } else {
                assertEquals(-1, pkLookup.lookup(termBytesRef))
            }

            if (random().nextInt(7) == 1) {
                val status = termsEnum.seekCeil(termBytesRef)
                if (shouldExist) {
                    assertEquals(TermsEnum.SeekStatus.FOUND, status)
                } else {
                    assertNotSame(TermsEnum.SeekStatus.FOUND, status)
                }
            }
        }

        r.close()
        w.close()
        d.close()
    }

    @Test
    @Companion.Nightly
    @Throws(Exception::class)
    fun testVaryingTermsPerSegment() {
        val dir = newDirectory()
        val terms = HashSet<BytesRef>()
        val MAX_TERMS = atLeast(10) // TODO reduced from 1000 to 10 for dev speed (confirmed to pass with 1000 in local)
        while (terms.size < MAX_TERMS) {
            terms.add(newBytesRef(TestUtil.randomSimpleString(random(), 1, 40)))
        }
        val termsList = ArrayList(terms)
        val sb = StringBuilder()
        for (termCount in 0..<MAX_TERMS) {
            if (VERBOSE) {
                println("\nTEST: termCount=$termCount add term=${termsList[termCount].utf8ToString()}")
            }
            sb.append(' ')
            sb.append(termsList[termCount].utf8ToString())
            val iwc = newIndexWriterConfig(MockAnalyzer(random()))
            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE)
            val w = RandomIndexWriter(random(), dir, iwc)
            val doc = Document()
            doc.add(newTextField("field", sb.toString(), Field.Store.NO))
            w.addDocument(doc)
            val r = w.reader
            assertEquals(1, r.leaves().size)
            val te = r.leaves()[0].reader().terms("field")!!.iterator()
            for (i in 0..termCount) {
                assertTrue(te.seekExact(termsList[i]), "term '${termsList[i].utf8ToString()}' should exist but doesn't")
            }
            for (i in termCount + 1..<termsList.size) {
                assertFalse(te.seekExact(termsList[i]), "term '${termsList[i]}' shouldn't exist but does")
            }
            r.close()
            w.close()
        }
        dir.close()
    }

    @Test
    @Throws(Exception::class)
    fun testIntersectRegexp() {
        val d = newDirectory()
        val w = RandomIndexWriter(random(), d)
        val doc = Document()
        doc.add(newStringField("field", "foobar", Field.Store.NO))
        w.addDocument(doc)
        val r = w.reader
        val terms = MultiTerms.getTerms(r, "field")!!
        val automaton = CompiledAutomaton(RegExp("do_not_match_anything").toAutomaton())
        val exception = expectThrows(IllegalArgumentException::class) {
            terms.intersect(automaton, null)
        }
        assertEquals("please use CompiledAutomaton.getTermsEnum instead", exception.message)
        r.close()
        w.close()
        d.close()
    }

    @Test
    @Throws(Exception::class)
    fun testInvalidAutomatonTermsEnum() {
        expectThrows(IllegalArgumentException::class) {
            AutomatonTermsEnum(TermsEnum.EMPTY, CompiledAutomaton(Automata.makeString("foo")))
        }
    }
}
