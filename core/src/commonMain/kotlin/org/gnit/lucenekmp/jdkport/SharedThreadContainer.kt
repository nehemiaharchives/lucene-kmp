package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Job
import kotlin.concurrent.Volatile


/**
 * A "shared" thread container. A shared thread container doesn't have an owner
 * and is intended for unstructured uses, e.g. thread pools.
 */
class SharedThreadContainer
/**
 * Initialize a new SharedThreadContainer.
 * @param name the container name, can be null
 */ private constructor(// name of container, used by toString
    private val name: String
) : ThreadContainer( /*shared*/true), AutoCloseable {
    // explicit parent for shared containers
    @Volatile
    private var parentRef: ThreadContainer? = null
    // the virtual threads in the container, created lazily
    @Volatile
    private var virtualThreads: MutableSet<Job>? = null

    // the key for this container in the registry
    @Volatile
    private var key: Any? = null

    // set to true when the container is closed
    @Volatile
    private var closed = false

    override fun name(): String {
        return name
    }

    override fun owner(): Job? {
        return null
    }

    override fun parent(): ThreadContainer? = parentRef

    override fun onStart(thread: Job) {
        // virtual threads needs to be tracked
        if (thread.isVirtual()) {
            var vthreads: MutableSet<Job>? = this.virtualThreads
            if (vthreads == null) {
        // lazily initialize the set
        vthreads = mutableSetOf()
        // publish to field (no CAS in this port)
        this.virtualThreads = vthreads
            }
            vthreads!!.add(thread)
        }
    }

    override fun onExit(thread: Job) {
    if (thread.isVirtual()) virtualThreads?.remove(thread)
    }

    override fun threads(): Sequence<Job> {
        val platformThreads: Sequence<Job> =
            JLA.allThreads.asSequence()
                .filter { t: Job -> JLA.threadContainer(t) === this }
        val vthreads: MutableSet<Job>? = this.virtualThreads
        return if (vthreads == null) {
            platformThreads
        } else {
            platformThreads + vthreads.asSequence().filter { it.isActive }
        }
    }

    /**
     * Starts a thread in this container.
     * @throws IllegalStateException if the container is closed
     */
    fun start(thread: Job) {
        check(!closed)
        JLA.start(thread, this)
    }

    /**
     * Closes this container. Further attempts to start a thread in this container
     * throw IllegalStateException. This method has no impact on threads that are
     * still running or starting around the time that this method is invoked.
     */
    override fun close() {
        if (!closed /*&& CLOSED.compareAndSet(this, false, true)*/) {
            closed = true
            ThreadContainers.deregisterContainer(key!!)
        }
    }

    companion object {
        private val JLA: JavaLangAccess = JavaLangAccess
        //private val CLOSED: java.lang.invoke.VarHandle
        //private val VIRTUAL_THREADS: java.lang.invoke.VarHandle

        init {
            //val l: java.lang.invoke.MethodHandles.Lookup = java.lang.invoke.MethodHandles.lookup()
            //CLOSED = MhUtil.findVarHandle(l, "closed", Boolean::class.javaPrimitiveType)
            //VIRTUAL_THREADS = MhUtil.findVarHandle(l, "virtualThreads", MutableSet::class.java)
        }

        /**
         * Creates a shared thread container with the given parent and name.
         * @throws IllegalArgumentException if the parent has an owner.
         */
        fun create(parent: ThreadContainer, name: String): SharedThreadContainer {
            require(parent.owner() == null) { "parent has owner" }
            val container = SharedThreadContainer(name)
            container.parentRef = parent
            // register the container to allow discovery by serviceability tools
            container.key = ThreadContainers.registerContainer(container)
            return container
        }

        /**
         * Creates a shared thread container with the given name. Its parent will be
         * the root thread container.
         */
        fun create(name: String): SharedThreadContainer {
            return create(ThreadContainers.root(), name)
        }
    }
}

// TODO implement more things if needed
fun Job.isVirtual(): Boolean {
    // Check if the Job is a virtual thread
    return false // In this port, treat coroutines Jobs as platform threads by default
}