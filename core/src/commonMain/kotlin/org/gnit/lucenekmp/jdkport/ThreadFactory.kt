package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable


/**
 * An object that creates new threads on demand.  Using thread factories
 * removes hardwiring of calls to [new Thread][Thread.Thread],
 * enabling applications to use special thread subclasses, priorities, etc.
 *
 *
 *
 * The simplest implementation of this interface is just:
 * <pre> `class SimpleThreadFactory implements ThreadFactory {
 * public Thread newThread(Runnable r) {
 * return new Thread(r);
 * }
 * }`</pre>
 *
 * The [Executors.defaultThreadFactory] method provides a more
 * useful simple implementation, that sets the created thread context
 * to known values before returning it.
 * @since 1.5
 * @author Doug Lea
 * @see Thread.Builder.factory
 */
interface ThreadFactory {
    /**
     * Constructs a new unstarted `Thread` to run the given runnable.
     *
     * @param r a runnable to be executed by new thread instance
     * @return constructed thread, or `null` if the request to
     * create a thread is rejected
     *
     * @see [Inheritance when
     * creating threads](../../lang/Thread.html.inheritance)
     */
    fun newThread(r: Runnable): Job
}
