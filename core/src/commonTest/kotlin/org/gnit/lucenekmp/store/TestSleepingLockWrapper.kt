package org.gnit.lucenekmp.store

import okio.Path
import org.gnit.lucenekmp.tests.store.BaseLockFactoryTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test

/** Simple tests for SleepingLockWrapper */
class TestSleepingLockWrapper : BaseLockFactoryTestCase() {

    @Throws(Exception::class)
    override fun getDirectory(path: Path): Directory {
        val lockWaitTimeout = TestUtil.nextLong(random(), 20, 100)
        val pollInterval = TestUtil.nextLong(random(), 2, 10)

        val which = random().nextInt(3)
        return when (which) {
            0 -> SleepingLockWrapper(
                newDirectory(random(), SingleInstanceLockFactory()),
                lockWaitTimeout,
                pollInterval
            )

            1 -> SleepingLockWrapper(newFSDirectory(path), lockWaitTimeout, pollInterval)
            else -> SleepingLockWrapper(newFSDirectory(path), lockWaitTimeout, pollInterval)
        }
    }

    // TODO: specific tests to this impl

    // tests inherited from BaseLockFactoryTestCase
    @Test
    override fun testBasics() = super.testBasics()

    @Test
    override fun testDoubleClose() = super.testDoubleClose()

    @Test
    override fun testValidAfterAcquire() = super.testValidAfterAcquire()

    @Test
    override fun testInvalidAfterClose() = super.testInvalidAfterClose()

    @Test
    override fun testObtainConcurrently() = super.testObtainConcurrently()

    @Test
    override fun testStressLocks() = super.testStressLocks()
}
