package org.gnit.lucenekmp.index

import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.MockRandomMergePolicy
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.NamedThreadFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Simple test that adds numeric terms, where each term has the docFreq of its integer value, and
 * checks that the docFreq is correct.
 */
//@SuppressCodecs("Direct") // at night this makes like 200k/300k docs and will make Direct's heart beat!
class TestBagOfPostings : LuceneTestCase() {
    
    @Test
    @Throws(Exception::class)
    fun test() {
        val postingsList = ArrayList<String>()
        var numTerms = atLeast(30) // TODO reduced from 300 to 30 for dev speed
        val maxTermsPerDoc = TestUtil.nextInt(random(), 10, 20)

        val isSimpleText = "SimpleText" == TestUtil.getPostingsFormat("field")

        val iwc = newIndexWriterConfig(random(), MockAnalyzer(random()))

        if ((isSimpleText || iwc.mergePolicy is MockRandomMergePolicy)
            && (TEST_NIGHTLY || RANDOM_MULTIPLIER > 1)
        ) {
            // Otherwise test can take way too long (> 2 hours)
            numTerms /= 2
        }

        if (VERBOSE) {
            println("maxTermsPerDoc=$maxTermsPerDoc")
            println("numTerms=$numTerms")
        }

        for (i in 0 until numTerms) {
            val term = i.toString()
            repeat(i) {
                postingsList.add(term)
            }
        }
        postingsList.shuffle(random())

        val postings = ConcurrentLinkedQueue(postingsList)

        val dir: Directory = newFSDirectory(createTempDir("bagofpostings"))
        val iw = RandomIndexWriter(random(), dir, iwc)

        val threadCount = TestUtil.nextInt(random(), 1, 5)
        if (VERBOSE) {
            println("config: ${iw.w.config}")
            println("threadCount=$threadCount")
        }

        val threads = arrayOfNulls<kotlinx.coroutines.Job>(threadCount)
        val startingGun = CountDownLatch(1)
        val threadFactory = NamedThreadFactory("TestBagOfPostings")

        for (threadID in 0 until threadCount) {
            threads[threadID] =
                threadFactory.newThread {
                    try {
                        val document = Document()
                        val field = newTextField("field", "", Field.Store.NO)
                        document.add(field)
                        startingGun.await()
                        while (!postings.isEmpty()) {
                            val text = StringBuilder()
                            val visited = HashSet<String>()
                            var i = 0
                            while (i < maxTermsPerDoc) {
                                val token = postings.poll() ?: break
                                if (visited.contains(token)) {
                                    // Put it back:
                                    postings.add(token)
                                    break
                                }
                                text.append(' ')
                                text.append(token)
                                visited.add(token)
                                i++
                            }
                            field.setStringValue(text.toString())
                            iw.addDocument(document)
                        }
                    } catch (e: Exception) {
                        throw RuntimeException(e)
                    }
                }
        }
        startingGun.countDown()
        for (t in threads) {
            runBlocking {
                t!!.join()
            }
        }

        iw.forceMerge(1)
        val ir = iw.getReader(applyDeletions = true, writeAllDeletes = false)
        assertEquals(1, ir.leaves().size)
        val air = ir.leaves()[0].reader()
        val terms = air.terms("field")
        // numTerms-1 because there cannot be a term 0 with 0 postings:
        assertEquals((numTerms - 1).toLong(), terms!!.size())
        val termsEnum = terms.iterator()
        var term: BytesRef?
        while (termsEnum.next().also { term = it } != null) {
            val value = term!!.utf8ToString().toInt()
            assertEquals(value, termsEnum.docFreq())
            // don't really need to check more than this, as CheckIndex
            // will verify that docFreq == actual number of documents seen
            // from a postingsEnum.
        }
        ir.close()
        iw.close()
        dir.close()
    }

    private class ConcurrentLinkedQueue<E>(elements: Collection<E>) {
        private val lock = ReentrantLock()
        private val deque = ArrayDeque<E>(elements)

        fun add(element: E) {
            lock.lock()
            try {
                deque.addLast(element)
            } finally {
                lock.unlock()
            }
        }

        fun poll(): E? {
            lock.lock()
            try {
                return if (deque.isEmpty()) null else deque.removeFirst()
            } finally {
                lock.unlock()
            }
        }

        fun isEmpty(): Boolean {
            lock.lock()
            try {
                return deque.isEmpty()
            } finally {
                lock.unlock()
            }
        }
    }
}
