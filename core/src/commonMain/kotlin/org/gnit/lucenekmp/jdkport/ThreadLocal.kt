package org.gnit.lucenekmp.jdkport

/**
 * port of java.lang.ThreadLocal
 */
@Ported(from = "java.lang.ThreadLocal")
open class ThreadLocal<T> {
    private object NullSentinel

    private val values = mutableMapOf<Thread, Any?>()
    private val lock = ReentrantLock()

    /**
     * Creates a thread local variable.
     */
    constructor()

    /**
     * Returns the value in the current thread's copy of this
     * thread-local variable. If the variable has no value for the
     * current thread, it is first initialized to the value returned
     * by an invocation of the [initialValue] method.
     *
     * @return the current thread's value of this thread-local
     */
    open fun get(): T? {
        val currentThread = Thread.currentThread()
        val hasValue: Boolean
        val stored: Any?
        lock.lock()
        try {
            hasValue = values.containsKey(currentThread)
            stored = values[currentThread]
        } finally {
            lock.unlock()
        }
        if (!hasValue) {
            val initialValue = initialValue()
            set(initialValue)
            return initialValue
        }
        if (stored === NullSentinel) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return stored as T?
    }

    /**
     * Sets the current thread's copy of this thread-local variable
     * to the specified value. Most subclasses will have no need to
     * override this method, relying solely on the [initialValue]
     * method to set the values of thread-locals.
     *
     * @param value the value to be stored in the current thread's copy of
     * this thread-local.
     */
    open fun set(value: T?) {
        val currentThread = Thread.currentThread()
        lock.lock()
        try {
            values[currentThread] = value ?: NullSentinel
        } finally {
            lock.unlock()
        }
    }

    /**
     * Removes the current thread's value for this thread-local
     * variable.
     */
    open fun remove() {
        val currentThread = Thread.currentThread()
        lock.lock()
        try {
            values.remove(currentThread)
        } finally {
            lock.unlock()
        }
    }

    /**
     * Returns the current thread's "initial value" for this
     * thread-local variable. This method will be invoked the first
     * time a thread accesses the variable with the [get]
     * method, unless the thread previously invoked the [set]
     * method, in which case the `initialValue` method will not
     * be invoked for the thread. Normally, this method is invoked at
     * most once per thread, but it may be invoked again in case of
     * subsequent invocations of [remove] followed by [get].
     *
     * @return the initial value for this thread-local
     */
    protected open fun initialValue(): T? {
        return null
    }

    companion object {
        fun <S> withInitial(supplier: () -> S): ThreadLocal<S> {
            return object : ThreadLocal<S>() {
                override fun initialValue(): S {
                    return supplier()
                }
            }
        }
    }
}
