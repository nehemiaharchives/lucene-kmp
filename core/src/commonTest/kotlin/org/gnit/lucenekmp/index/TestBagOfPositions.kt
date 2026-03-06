package org.gnit.lucenekmp.index

import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.jdkport.CountDownLatch
import org.gnit.lucenekmp.jdkport.LinkedBlockingQueue
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.MockRandomMergePolicy
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.NamedThreadFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Simple test that adds numeric terms, where each term has the totalTermFreq of its integer value,
 * and checks that the totalTermFreq is correct.
 */
// TODO: somehow factor this with BagOfPostings? it's almost the same
//@SuppressCodecs("Direct") // at night this makes like 200k/300k docs and will make Direct's heart beat!
class TestBagOfPositions : LuceneTestCase() {
    @Test
    @Throws(Exception::class)
    fun test() {
        val postingsList = ArrayList<String>()
        var numTerms = atLeast(100)
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

        val postings = LinkedBlockingQueue(postingsList)

        val dir: Directory = newFSDirectory(createTempDir("bagofpositions"))

        val iw = RandomIndexWriter(random(), dir, iwc)

        val threadCount = TestUtil.nextInt(random(), 1, 5)
        if (VERBOSE) {
            println("config: ${iw.w.config}")
            println("threadCount=$threadCount")
        }

        val fieldType = FieldType(TextField.TYPE_NOT_STORED)
        if (random().nextBoolean()) {
            fieldType.setOmitNorms(true)
        }
        val options = random().nextInt(3)
        if (options == 0) {
            fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS) // we dont actually need positions
            fieldType.setStoreTermVectors(
                true
            ) // but enforce term vectors when we do this so we check SOMETHING
        } else if (options == 1) {
            fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS)
        }
        // else just positions

        val threads = arrayOfNulls<kotlinx.coroutines.Job>(threadCount)
        val startingGun = CountDownLatch(1)
        val threadFactory = NamedThreadFactory("TestBagOfPositions")

        for (threadID in 0 until threadCount) {
            val threadRandom = Random(random().nextLong())
            val document = Document()
            val field = Field("field", "", fieldType)
            document.add(field)
            threads[threadID] =
                threadFactory.newThread {
                    try {
                        startingGun.await()
                        while (!postings.isEmpty()) {
                            val text = StringBuilder()
                            val numTerms = threadRandom.nextInt(maxTermsPerDoc)
                            var i = 0
                            while (i < numTerms) {
                                val token = postings.poll() ?: break
                                text.append(' ')
                                text.append(token)
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
            assertEquals(value.toLong(), termsEnum.totalTermFreq())
            // don't really need to check more than this, as CheckIndex
            // will verify that totalTermFreq == total number of positions seen
            // from a postingsEnum.
        }
        ir.close()
        iw.close()
        dir.close()
    }
}
