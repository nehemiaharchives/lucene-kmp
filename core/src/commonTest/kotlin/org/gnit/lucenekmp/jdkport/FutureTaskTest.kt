package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.*
import kotlin.test.*

class FutureTaskTest {

    @Test
    fun testCancel() = runBlocking {
        val task = FutureTask(Callable {
            delay(1000)
            "result"
        })
        task.run()
        delay(100)
        assertTrue(task.cancel(true))
        assertTrue(task.isCancelled())
        assertTrue(task.isDone())
    }

    @Test
    fun testIsDone() = runBlocking {
        val task = FutureTask(Callable {
            delay(100)
            "result"
        })
        task.run()
        assertFalse(task.isDone())
        delay(200)
        assertTrue(task.isDone())
    }

    @Test
    fun testIsCancelled() = runBlocking {
        val task = FutureTask(Callable {
            delay(1000)
            "result"
        })
        task.run()
        delay(100)
        task.cancel(true)
        assertTrue(task.isCancelled())
    }

    @Test
    fun testGet() = runBlocking {
        val task = FutureTask(Callable {
            delay(100)
            "result"
        })
        task.run()
        assertEquals("result", task.get())
    }

    @Test
    fun testRun() = runBlocking {
        val task = FutureTask(Callable {
            "result"
        })
        task.run()
        assertEquals("result", task.get())
    }

    @Test
    fun testRunAndReset() = runBlocking {
        val task = object : FutureTask<String>(Callable {
            "result"
        }) {
            override fun runAndReset(): Boolean {
                return super.runAndReset()
            }
        }
        assertTrue(task.runAndReset())
        assertFalse(task.isDone())
    }
}
