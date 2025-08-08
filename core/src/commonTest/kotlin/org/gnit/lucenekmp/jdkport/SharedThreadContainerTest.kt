package org.gnit.lucenekmp.jdkport

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

class SharedThreadContainerTest {

    @Test
    fun createAndName_parentRoot() {
        val container = SharedThreadContainer.create("pool-A")
        assertEquals("pool-A", container.name())
        // parent of shared container is root
        assertEquals(ThreadContainers.root(), container.parent())
        // toString has name/id
        assertTrue(container.toString().startsWith("pool-A/"))
    }

    @Test
    fun startRegistersThreadAndThreadsIterable() = runBlocking {
        val container = SharedThreadContainer.create("C1")

        // start a coroutine Job and register via JLA.start
        val job = launch { /* idle */ }
        // starting associates job with container and notifies onStart
        container.start(job)

        // ThreadContainers.container should resolve to this container
        assertEquals(container, ThreadContainers.container(job))

        // threads() should contain our job (jobs treated as platform threads here)
        val threads = container.threads().toList()
        logger.debug { "threads=$threads" }
        assertTrue(threads.contains(job))

        job.cancel()
    }

    @Test
    fun closePreventsFurtherStart_butDoesNotAffectExisting() = runBlocking {
        val container = SharedThreadContainer.create("C2")
        val job = launch { }
        container.start(job)

        // closing should deregister; future starts should fail
        container.close()

        assertFailsWith<IllegalStateException> {
            container.start(launch { })
        }

        // Existing job remains associated and visible until completion
        assertEquals(container, ThreadContainers.container(job))
        assertTrue(container.threads().toList().contains(job))

        job.cancel()
    }

    @Test
    fun parentAndChildrenRelationships() = runBlocking {
        val parent = SharedThreadContainer.create("P")
        val child = SharedThreadContainer.create(parent, "C")

        assertEquals(parent, child.parent())
        val children = parent.children().toList()
        // child should appear among children (registered containers + threads' top containers)
        assertTrue(children.contains(child))

        // Root has no parent
        assertNull(ThreadContainers.root().parent())
    }

    @Test
    fun threadCountReflectsPlatformThreadsInContainer() = runBlocking {
        val container = SharedThreadContainer.create("CNT")
        val j1 = launch { }
        val j2 = launch { }
        container.start(j1)
        container.start(j2)

        // threadCount counts live threads from threads() sequence
        val count = container.threadCount()
        assertTrue(count >= 2)

        j1.cancel(); j2.cancel()
    }
}
