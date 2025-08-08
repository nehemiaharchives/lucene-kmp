package org.gnit.lucenekmp.jdkport

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.Volatile
import kotlin.reflect.KClass


/**
 * A stackable scope to support structured constructs. The push method is used to
 * push a StackableScope to the current thread's scope stack. The tryPop and
 * popForcefully methods are used to pop the StackableScope from the current thread's
 * scope stack.
 */
open class StackableScope(shared: Boolean) {
    private val owner: Job?

    @Volatile
    private var previous: StackableScope? = null

    /**
     * Creates a stackable scope.
     * @param shared true for a shared scope that cannot be pushed to the stack,
     * false for scope that is owned by the current thread
     */
    init {
        if (shared) {
            this.owner = null
        } else {
            this.owner = runBlocking{ currentCoroutineContext()[Job] }
        }
    }

    /**
     * Creates a stackable scope owned by the current thread.
     */
    constructor() : this(false)

    /**
     * Returns the scope owner or null if not owned.
     */
    open fun owner(): Job? {
    return owner
    }

    /**
     * Pushes this scope onto the current thread's scope stack.
     * @throws WrongThreadException it the current thread is not the owner
     */
    open suspend fun push(): StackableScope {
        if (currentCoroutineContext()[Job] !== owner) throw /*java.lang.WrongThread*/Exception("Not owner")
        previous = head()
        setHead(this)
        return this
    }

    /**
     * Pops this scope from the current thread's scope stack if the scope is
     * at the top of stack.
     * @return true if the pop succeeded, false if this scope is not the top of stack
     * @throws WrongThreadException it the current thread is not the owner
     */
    suspend fun tryPop(): Boolean {
        if (currentCoroutineContext()[Job] !== owner) throw /*java.lang.WrongThread*/Exception("Not owner")
        if (head() === this) {
            setHead(previous)
            previous = null
            return true
        } else {
            return false
        }
    }

    /**
     * Pops this scope from the current thread's scope stack.
     *
     * For well-behaved usages, this scope is at the top of the stack. It is popped
     * from the stack and the method returns `true`.
     *
     * If this scope is not at the top of the stack then this method attempts to
     * close each of the intermediate scopes by invoking their [.tryClose]
     * method. If tryClose succeeds then the scope is removed from the stack. When
     * done, this scope is removed from the stack and `false` is returned.
     *
     * This method does nothing, and returns `false`, if this scope is not
     * on the current thread's scope stack.
     *
     * @return true if this scope was at the top of the stack, otherwise false
     * @throws WrongThreadException it the current thread is not the owner
     */
    suspend fun popForcefully(): Boolean {
        if (currentCoroutineContext()[Job] !== owner) throw /*java.lang.WrongThread*/Exception("Not owner")
        val head = head()
        if (head === this) {
            setHead(previous)
            previous = null
            return true
        }

        // scope is not the top of stack
        if (contains(this)) {
            var current = head
            while (current !== this) {
                val previous = current.previous()
                // attempt to forcefully close the scope and remove from stack
                if (current.tryClose()) {
                    current.unlink()
                }
                current = previous!!
            }
            unlink()
        }
        return false
    }

    /**
     * Returns the scope that encloses this scope.
     */
    fun enclosingScope(): StackableScope? {
        val previous = this.previous
        if (previous != null) return previous
        if (owner != null) return JLA.threadContainer(owner)
        return null
    }

    /**
     * Returns the scope of the given type that encloses this scope.
     */
    fun <T : StackableScope> enclosingScope(type: KClass<T>): T? {
        var current = enclosingScope()
        while (current != null) {
            if (type.isInstance(current)) {
                val tmp = current as T
                return tmp
            }
            current = current.enclosingScope()
        }
        return null
    }

    /**
     * Returns the scope that directly encloses this scope, null if none.
     */
    open fun previous(): StackableScope? {
        return previous
    }

    /**
     * Returns the scope that this scope directly encloses, null if none.
     */
    private fun next(): StackableScope? {
        assert(contains(this))
        var current = head()
        var next: StackableScope? = null
        while (current !== this) {
            next = current
            current = current.previous()!!
        }
        return next
    }

    /**
     * Override this method to close this scope and release its resources.
     * This method should not pop the scope from the stack.
     * This method is guaranteed to execute on the owner thread.
     * @return true if this method closed the scope, false if it failed
     */
    protected open suspend fun tryClose(): Boolean {
        assert(currentCoroutineContext()[Job] === owner)
        return false
    }

    /**
     * Removes this scope from the current thread's scope stack.
     */
    private fun unlink() {
        assert(contains(this))
        val next = next()
        if (next == null) {
            setHead(previous)
        } else {
            next.previous = previous
        }
        previous = null
    }

    companion object {
        private val JLA: JavaLangAccess = /*SharedSecrets.getJavaLangAccess()*/ JavaLangAccess

        /**
         * Pops all scopes from the current thread's scope stack.
         */
        suspend fun popAll() {
            val head: StackableScope? = head()
            if (head != null) {
                var current: StackableScope? = head
                while (current != null) {
                    assert(currentCoroutineContext()[Job] === current.owner())
                    current.tryClose()
                    current = current.previous()
                }
                setHead(null)
            }
        }

        /**
         * Returns true if the given scope is on the current thread's scope stack.
         */
        private fun contains(scope: StackableScope): Boolean {
            //checkNotNull(scope)
            var current: StackableScope? = head()
            while (current != null && current !== scope) {
                current = current.previous()
            }
            return (current === scope)
        }

        /**
         * Returns the head of the current thread's scope stack.
         */
        fun head(): StackableScope {

            val job = runBlocking { currentCoroutineContext()[Job] }!!

            return JLA.headStackableScope(job)!!
        }

        /**
         * Sets the head (top) of the current thread's scope stack.
         */
        private fun setHead(scope: StackableScope?) {
            JLA.headStackableScope = scope
        }
    }
}
