package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Job


/**
 * A container of threads.
 */
abstract class ThreadContainer
/**
 * Creates a ThreadContainer.
 * @param shared true for a shared container, false for a container
 * owned by the current thread
 */
protected constructor(shared: Boolean) : StackableScope(shared) {
    /**
     * Return the name of this container, may be null.
     */
    open fun name(): String? {
        return null
    }

    /**
     * Returns the parent of this container or null if this is the root container.
     */
    open fun parent(): ThreadContainer? {
        return ThreadContainers.parent(this)
    }

    /**
     * Return the stream of children of this container.
     */
    fun children(): Sequence<ThreadContainer> {
        return ThreadContainers.children(this)
    }

    /**
     * Return a count of the number of threads in this container.
     */
    open fun threadCount(): Long {
        return threads().map { e: Job -> 1L }.sum()
    }

    /**
     * Returns a stream of the live threads in this container.
     */
    abstract fun threads(): Sequence<Job>

    /**
     * Invoked by Thread::start before the given Thread is started.
     */
    open fun onStart(thread: Job) {
        // do nothing
    }

    /**
     * Invoked when a Thread terminates or starting it fails.
     *
     * For a platform thread, this method is invoked by the thread itself when it
     * terminates. For a virtual thread, this method is invoked on its carrier
     * after the virtual thread has terminated.
     *
     * If starting the Thread failed then this method is invoked on the thread
     * that invoked onStart.
     */
    open fun onExit(thread: Job) {
        // do nothing
    }

    /**
     * The scoped values captured when the thread container was created.
     */
    /*open fun scopedValueBindings(): BindingsSnapshot {
        return null
    }*/

    override fun toString(): String {
        val name = name()
        if (name != null && name.indexOf('@') >= 0) {
            return name
        } else {
            val id: String = this.hashCode().toString()
            return if (name != null) "$name/$id" else id
        }
    }
}
