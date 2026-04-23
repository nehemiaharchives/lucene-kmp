package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.TimeUnit
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.Version
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalAtomicApi::class)
class TestMergePolicy : LuceneTestCase() {

    @Test
    @Throws(IOException::class, org.gnit.lucenekmp.jdkport.InterruptedException::class)
    fun testWaitForOneMerge() {
        newDirectory().use { dir ->
            val ms = createRandomMergeSpecification(dir, 1 + random().nextInt(10))
            for (m in ms.merges) {
                assertFalse(m.hasCompletedSuccessfully().isPresent)
            }
            val t =
                Thread {
                    try {
                        for (m in ms.merges) {
                            m.close(true, false) { _ -> }
                        }
                    } catch (e: IOException) {
                        throw AssertionError(e)
                    }
                }
            t.start()
            assertTrue(ms.await(100, TimeUnit.HOURS))
            for (m in ms.merges) {
                assertTrue(m.hasCompletedSuccessfully().get()!!)
            }
            t.join()
        }
    }

    @Test
    @Throws(IOException::class, org.gnit.lucenekmp.jdkport.InterruptedException::class)
    fun testTimeout() {
        newDirectory().use { dir ->
            val ms = createRandomMergeSpecification(dir, 3)
            for (m in ms.merges) {
                assertFalse(m.hasCompletedSuccessfully().isPresent)
            }
            val t =
                Thread {
                    try {
                        ms.merges[0].close(true, false) { _ -> }
                    } catch (e: IOException) {
                        throw AssertionError(e)
                    }
                }
            t.start()
            assertFalse(ms.await(10, TimeUnit.MILLISECONDS))
            assertFalse(ms.merges[1].hasCompletedSuccessfully().isPresent)
            t.join()
        }
    }

    @Test
    @Throws(IOException::class, org.gnit.lucenekmp.jdkport.InterruptedException::class)
    fun testTimeoutLargeNumberOfMerges() {
        newDirectory().use { dir ->
            val ms = createRandomMergeSpecification(dir, 10000)
            for (m in ms.merges) {
                assertFalse(m.hasCompletedSuccessfully().isPresent)
            }
            val i = AtomicInt(0)
            val stop = AtomicBoolean(false)
            val t =
                Thread {
                    while (stop.load() == false) {
                        try {
                            ms.merges[i.fetchAndIncrement()].close(true, false) { _ -> }
                            Thread.sleep(1)
                        } catch (e: IOException) {
                            throw AssertionError(e)
                        } catch (e: org.gnit.lucenekmp.jdkport.InterruptedException) {
                            throw AssertionError(e)
                        }
                    }
                }
            t.start()
            assertFalse(ms.await(10, TimeUnit.MILLISECONDS))
            stop.store(true)
            t.join()
            for (j in 0..<ms.merges.size) {
                if (j < i.load()) {
                    assertTrue(ms.merges[j].hasCompletedSuccessfully().get()!!)
                } else {
                    assertFalse(ms.merges[j].hasCompletedSuccessfully().isPresent)
                }
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testFinishTwice() {
        newDirectory().use { dir ->
            val spec = createRandomMergeSpecification(dir, 1)
            val oneMerge = spec.merges[0]
            oneMerge.close(true, false) { _ -> }
            expectThrows(IllegalStateException::class) { oneMerge.close(false, false) { _ -> } }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testTotalMaxDoc() {
        newDirectory().use { dir ->
            val spec = createRandomMergeSpecification(dir, 1)
            var docs = 0
            val oneMerge = spec.merges[0]
            for (info in oneMerge.segments) {
                docs += info.info.maxDoc()
            }
            assertEquals(docs, oneMerge.totalMaxDoc)
        }
    }

    private fun createRandomMergeSpecification(dir: Directory, numMerges: Int): MergePolicy.MergeSpecification {
        val ms = MergePolicy.MergeSpecification()
        for (ii in 0..<numMerges) {
            val si =
                SegmentInfo(
                    dir,  // dir
                    Version.LATEST,  // version
                    Version.LATEST,  // min version
                    TestUtil.randomSimpleString(random()),  // name
                    random().nextInt(1000),  // maxDoc
                    random().nextBoolean(),  // isCompoundFile
                    false,
                    null,  // codec
                    mutableMapOf(),  // diagnostics
                    TestUtil.randomSimpleString( // id
                        random(), StringHelper.ID_LENGTH, StringHelper.ID_LENGTH
                    ).encodeToByteArray(),
                    mutableMapOf(),  // attributes
                    null /* indexSort */
                )
            val segments = mutableListOf<SegmentCommitInfo>()
            segments.add(SegmentCommitInfo(si, 0, 0, 0, 0, 0, StringHelper.randomId()))
            ms.add(MergePolicy.OneMerge(segments))
        }
        return ms
    }
}
