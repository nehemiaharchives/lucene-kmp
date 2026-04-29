package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestTwoPhaseCommitTool : LuceneTestCase() {

    companion object {
        private class TwoPhaseCommitImpl(
            val failOnPrepare: Boolean,
            val failOnCommit: Boolean,
            val failOnRollback: Boolean
        ) : TwoPhaseCommit {
            var rollbackCalled: Boolean = false

            @Throws(IOException::class)
            override fun prepareCommit(): Long {
                return prepareCommit(null)
            }

            @Throws(IOException::class)
            fun prepareCommit(commitData: MutableMap<String, String>?): Long {
                assertFalse(commitCalled, "commit should not have been called before all prepareCommit were")
                if (failOnPrepare) {
                    throw IOException("failOnPrepare")
                }
                return 1
            }

            @Throws(IOException::class)
            override fun commit(): Long {
                return commit(null)
            }

            @Throws(IOException::class)
            fun commit(commitData: MutableMap<String, String>?): Long {
                commitCalled = true
                if (failOnCommit) {
                    throw RuntimeException("failOnCommit")
                }
                return 1
            }

            @Throws(IOException::class)
            override fun rollback() {
                rollbackCalled = true
                if (failOnRollback) {
                    throw Error("failOnRollback")
                }
            }

            companion object {
                var commitCalled: Boolean = false
            }
        }
    }

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        TwoPhaseCommitImpl.commitCalled = false // reset count before every test
    }

    @Test
    @Throws(Exception::class)
    fun testPrepareThenCommit() {
        // tests that prepareCommit() is called on all objects before commit()
        val objects = Array(2) { TwoPhaseCommitImpl(false, false, false) }

        // following call will fail if commit() is called before all prepare() were
        TwoPhaseCommitTool.execute(*objects)
    }

    @Test
    @Throws(Exception::class)
    fun testRollback() {
        // tests that rollback is called if failure occurs at any stage
        val numObjects: Int = random().nextInt(8) + 3 // between [3, 10]
        val objects = arrayOfNulls<TwoPhaseCommitImpl>(numObjects)
        for (i in objects.indices) {
            val failOnPrepare: Boolean = random().nextBoolean()
            // we should not hit failures on commit usually
            val failOnCommit = random().nextDouble() < 0.05
            val failOnRollback: Boolean = random().nextBoolean()
            objects[i] = TwoPhaseCommitImpl(failOnPrepare, failOnCommit, failOnRollback)
        }

        var anyFailure = false
        try {
            TwoPhaseCommitTool.execute(*objects)
        } catch (t: Throwable) {
            anyFailure = true
        }

        if (anyFailure) {
            // if any failure happened, ensure that rollback was called on all.
            for (tpc in objects) {
                assertTrue(checkNotNull(tpc).rollbackCalled, "rollback was not called while a failure occurred during the 2-phase commit")
            }
        }
    }

    @Test
    @Throws(Exception::class)
    fun testNullTPCs() {
        val numObjects: Int = random().nextInt(4) + 3 // between [3, 6]
        val tpcs: Array<TwoPhaseCommit?> = arrayOfNulls<TwoPhaseCommit>(numObjects)
        var setNull = false
        for (i in tpcs.indices) {
            val isNull = random().nextDouble() < 0.3
            if (isNull) {
                setNull = true
                tpcs[i] = null
            } else {
                tpcs[i] = TwoPhaseCommitImpl(false, false, false)
            }
        }

        if (!setNull) {
            // none of the TPCs were picked to be null, pick one at random
            val idx: Int = random().nextInt(numObjects)
            tpcs[idx] = null
        }

        // following call would fail if TPCTool won't handle null TPCs properly
        TwoPhaseCommitTool.execute(*tpcs)
    }
}
