package org.gnit.lucenekmp.jdkport

import dev.scottpierce.envvar.EnvVar
import kotlinx.coroutines.Job
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * This class consists exclusively of static methods to support groupings of threads.
 */
object ThreadContainers {
    private val JLA: JavaLangAccess = /*SharedSecrets.getJavaLangAccess()*/ JavaLangAccess

    // true if all threads are tracked
    private val TRACK_ALL_THREADS: Boolean

    // the root container
    private val ROOT_CONTAINER: RootContainer

    // the set of thread containers registered with this class
    private val CONTAINER_REGISTRY: MutableSet<WeakReference<ThreadContainer>> = mutableSetOf()
    private val QUEUE: ReferenceQueue<ThreadContainer> = ReferenceQueue()

    init {
        val s: String? = EnvVar["jdk.trackAllThreads"] /*java.lang.System.getProperty("jdk.trackAllThreads")*/
        if (s == null || s.isEmpty() || s.toBoolean()) {
            TRACK_ALL_THREADS = true
            ROOT_CONTAINER = RootContainer.TrackingRootContainer()
        } else {
            TRACK_ALL_THREADS = false
            ROOT_CONTAINER = RootContainer.CountingRootContainer()
        }
    }

    /**
     * Expunge stale entries from the container registry.
     */
    private fun expungeStaleEntries() {
        var key: Any?
        while ((QUEUE.poll().also { key = it }) != null) {
            CONTAINER_REGISTRY.remove(key)
        }
    }

    /**
     * Returns true if all threads are tracked.
     */
    fun trackAllThreads(): Boolean {
        return TRACK_ALL_THREADS
    }

    /**
     * Registers a thread container to be tracked this class, returning a key
     * that is used to remove it from the registry.
     */
    fun registerContainer(container: ThreadContainer?): Any {
        expungeStaleEntries()
        val ref: WeakReference<ThreadContainer> = WeakReference(container, QUEUE as ReferenceQueue<ThreadContainer?>?)
        CONTAINER_REGISTRY.add(ref)
        return ref
    }

    /**
     * Removes a thread container from being tracked by specifying the key
     * returned when the thread container was registered.
     */
    fun deregisterContainer(key: Any) {
        assert(key is WeakReference<*>)
        CONTAINER_REGISTRY.remove(key)
    }

    /**
     * Returns the root thread container.
     */
    fun root(): ThreadContainer {
        return ROOT_CONTAINER
    }

    /**
     * Returns the parent of the given thread container.
     *
     * If the container has an owner then its parent is the enclosing container when
     * nested, or the container that the owner is in, when not nested.
     *
     * If the container does not have an owner then the root container is returned,
     * or null if called with the root container.
     */
    fun parent(container: ThreadContainer): ThreadContainer? {
        val owner: Job? = container.owner()
        if (owner != null) {
            var parent: ThreadContainer? = container.enclosingScope(ThreadContainer::class)
            if (parent != null) return parent
            if ((container(owner).also { parent = it }) != null) return parent
        }
        val root: ThreadContainer = root()
        return if (container !== root) root else null
    }

    /**
     * Returns given thread container's "children".
     */
    fun children(container: ThreadContainer): Sequence<ThreadContainer> {
        // children of registered containers
        val s1: Sequence<ThreadContainer> = CONTAINER_REGISTRY
            .asSequence()
            .mapNotNull { it.get() }
            .filter { it.parent() === container }

        // container may enclose another container
        var s2: Sequence<ThreadContainer> = emptySequence()
        if (container.owner() != null) {
            val next: ThreadContainer? = next(container)
            if (next != null) s2 = sequenceOf(next)
        }

        // the top-most container owned by the threads in the container
        val s3: Sequence<ThreadContainer> = container.threads()
            .mapNotNull { t -> top(t) }

        return sequenceOf(s1, s2, s3).flatten()
    }

    /**
     * Returns the thread container that the given Thread is in or the root
     * container if not started in a container.
     * @throws IllegalStateException if the thread has not been started
     */
    fun container(thread: Job): ThreadContainer? {
        // thread container is set when the thread is started
        if (thread.isActive || thread.isCompleted /*thread.getState() == Job.State.TERMINATED*/) {
            val container: ThreadContainer? = JLA.threadContainer(thread)
            return if (container != null) container else root()
        } else {
            throw IllegalStateException("Thread not started")
        }
    }

    /**
     * Returns the top-most thread container owned by the given thread.
     */
    private fun top(thread: Job): ThreadContainer? {
        var current: StackableScope? = JLA.headStackableScope(thread)
        var top: ThreadContainer? = null
        while (current != null) {
            if (current is ThreadContainer) {
                top = current
            }
            current = current.previous()
        }
        return top
    }

    /**
     * Returns the thread container that the given thread container encloses.
     */
    private fun next(container: ThreadContainer): ThreadContainer? {
        var current: StackableScope? = JLA.headStackableScope(container.owner())
        if (current != null) {
            var next: ThreadContainer? = null
            while (current != null) {
                if (current === container) {
                    return next
                } else if (current is ThreadContainer) {
                    next = current
                }
                current = current.previous()
            }
        }
        return null
    }

    /**
     * Root container that "contains" all platform threads not started in a container.
     * It may include all virtual threads started directly with the Thread API.
     */
    abstract class RootContainer : ThreadContainer(true) {
        override fun parent(): ThreadContainer? {
            return null
        }

        override fun name(): String {
            return "<root>"
        }

        override fun previous(): StackableScope? {
            return null
        }

        override fun toString(): String {
            return name()
        }

        /**
         * Returns the platform threads that are not in the container as these
         * threads are considered to be in the root container.
         */
        protected fun platformThreads(): Sequence<Job> {
            return JLA.allThreads.asSequence()
                .filter { t: Job -> JLA.threadContainer(t) == null }
        }

        /**
         * Root container that tracks all threads.
         */
        class TrackingRootContainer : RootContainer() {
            override fun onStart(thread: Job) {
                /*assert(thread.isVirtual())*/
                VTHREADS.add(thread)
            }

            override fun onExit(thread: Job) {
                /*assert(thread.isVirtual())*/
                VTHREADS.remove(thread)
            }

            override fun threadCount(): Long {
                return (platformThreads().count() + VTHREADS.size).toLong()
            }

            override fun threads(): Sequence<Job> {
                return platformThreads() + VTHREADS.asSequence().filter { it.isActive }
            }

            companion object {
                private val VTHREADS: MutableSet<Job> = mutableSetOf()
            }
        }

        /**
         * Root container that tracks all platform threads and just keeps a
         * count of the virtual threads.
         */
        class CountingRootContainer : RootContainer() {
            @OptIn(ExperimentalAtomicApi::class)
            override fun onStart(thread: Job) {
                /*assert(thread.isVirtual())*/
                VTHREAD_COUNT.addAndFetch(1L)
            }

            @OptIn(ExperimentalAtomicApi::class)
            override fun onExit(thread: Job) {
                /*assert(thread.isVirtual())*/
                VTHREAD_COUNT.addAndFetch(-1L)
            }

            @OptIn(ExperimentalAtomicApi::class)
            override fun threadCount(): Long {
                return platformThreads().count() + VTHREAD_COUNT.load()
            }

            override fun threads(): Sequence<Job> {
                return platformThreads()
            }

            companion object {
                @OptIn(ExperimentalAtomicApi::class)
                private val VTHREAD_COUNT: AtomicLong = AtomicLong(0L) /*java.util.concurrent.atomic.LongAdder =
                    java.util.concurrent.atomic.LongAdder()*/
            }
        }
    }
}
