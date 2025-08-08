package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlin.time.TimeSource.Monotonic
import kotlin.time.toDuration

class LockSupportTest {

    @Test
    fun park_then_unpark_resumes() = runBlocking {
        val parked = CompletableDeferred<Unit>()
        val resumed = CompletableDeferred<Unit>()

        val j: Job = launch {
            parked.complete(Unit)
            // suspend until unparked
            LockSupport.park("test-blocker")
            resumed.complete(Unit)
        }

        // Wait until the child has reached park
        withTimeout(1_000) { parked.await() }

    // Give the child a moment to suspend inside park to avoid a race
    delay(20)
    // Unpark the parked job and verify it resumes
    LockSupport.unpark(j)

        withTimeout(1_000) { resumed.await() }
        assertTrue(resumed.isCompleted)
    }

    @Test
    fun unpark_before_park_grants_permit() = runBlocking {
        val resumed = CompletableDeferred<Unit>()

        // Launch a coroutine that will park later
        val j: Job = launch {
            // Ensure unpark happens before we attempt to park
            delay(200)
            LockSupport.park("blocker-before")
            resumed.complete(Unit)
        }

        // Call unpark BEFORE the coroutine reaches park()
        LockSupport.unpark(j)

        // If LockSupport models the JDK permit, the later park should not block
        // Current implementation lacks a permit and will time out here (expected failure revealing the bug)
        withTimeout(500) { resumed.await() }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun parkNanos_times_out_after_approximately_the_duration() = runBlocking {
        val resumed = CompletableDeferred<Unit>()
        val target = 60.milliseconds

        val mark = Monotonic.markNow()
        launch {
            LockSupport.parkNanos(target.inWholeNanoseconds)
            resumed.complete(Unit)
        }

        withTimeout(2_000) { resumed.await() }
        val elapsed: Duration = mark.elapsedNow()

        // Should have waited at least close to the target (accounting for timer granularity)
        assertTrue(elapsed >= 40.milliseconds, "elapsed=$elapsed should be >= 40ms")
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun parkUntil_waits_until_deadline() = runBlocking {
        val resumed = CompletableDeferred<Unit>()
        val mark = TimeSource.Monotonic.markNow()

        // Deadline ~75ms in the future
        val deadline = kotlin.time.Clock.System.now() + 75.toDuration(DurationUnit.MILLISECONDS)

        launch {
            LockSupport.parkUntil(deadline)
            resumed.complete(Unit)
        }

        withTimeout(2_000) { resumed.await() }
        val elapsed = mark.elapsedNow()

        // Should be at least ~60ms to account for scheduling jitter
        assertTrue(elapsed >= 60.milliseconds, "elapsed=$elapsed should be >= 60ms")
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun parkNanos_with_blocker_times_out() = runBlocking {
        val resumed = CompletableDeferred<Unit>()
        val target = 50.milliseconds
        val mark = Monotonic.markNow()

        launch {
            LockSupport.parkNanos("blocker", target.inWholeNanoseconds)
            resumed.complete(Unit)
        }

        withTimeout(2_000) { resumed.await() }
        val elapsed = mark.elapsedNow()
        assertTrue(elapsed >= 30.milliseconds, "elapsed=$elapsed should be >= 30ms")
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun parkUntil_with_blocker_waits_until_deadline() = runBlocking {
        val resumed = CompletableDeferred<Unit>()
        val mark = Monotonic.markNow()
        val deadline = kotlin.time.Clock.System.now() + 70.toDuration(DurationUnit.MILLISECONDS)

        launch {
            LockSupport.parkUntil("blocker", deadline)
            resumed.complete(Unit)
        }

        withTimeout(2_000) { resumed.await() }
        val elapsed = mark.elapsedNow()
        assertTrue(elapsed >= 50.milliseconds, "elapsed=$elapsed should be >= 50ms")
    }

    @Test
    fun unpark_null_is_noop() = runBlocking {
        // Should not throw
        LockSupport.unpark(null)
        assertTrue(true)
    }
}
