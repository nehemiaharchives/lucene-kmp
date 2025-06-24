package org.gnit.lucenekmp.util


/**
 * Java's builtin ThreadLocal has a serious flaw: it can take an arbitrarily long amount of time to
 * dereference the things you had stored in it, even once the ThreadLocal instance itself is no
 * longer referenced. This is because there is single, master map stored for each thread, which all
 * ThreadLocals share, and that master map only periodically purges "stale" entries.
 *
 *
 * While not technically a memory leak, because eventually the memory will be reclaimed, it can
 * take a long time and you can easily hit OutOfMemoryError because from the GC's standpoint the
 * stale entries are not reclaimable.
 *
 *
 * This class works around that, by only enrolling WeakReference values into the ThreadLocal, and
 * separately holding a hard reference to each stored value. When you call [.close], these
 * hard references are cleared and then GC is freely able to reclaim space by objects stored in it.
 *
 *
 * We can not rely on [ThreadLocal.remove] as it only removes the value for the caller
 * thread, whereas [.close] takes care of all threads. You should not call [.close]
 * until all threads are done using the instance.
 *
 * @lucene.internal
 */
class CloseableThreadLocal<T> : AutoCloseable {
    /*private var t: java.lang.ThreadLocal<java.lang.ref.WeakReference<T>>? =
        java.lang.ThreadLocal<java.lang.ref.WeakReference<T>>()*/
    private var value: T? = null

    // Use a WeakHashMap so that if a Thread exits and is
    // GC'able, its entry may be removed:
    /*private var hardRefs: MutableMap<java.lang.Thread, T>? = java.util.WeakHashMap<java.lang.Thread, T>()*/

    // On each get or set we decrement this; when it hits 0 we
    // purge.  After purge, we set this to
    // PURGE_MULTIPLIER * stillAliveCount.  This keeps
    // amortized cost of purging linear.
    /*private val countUntilPurge: AtomicInteger = AtomicInteger(PURGE_MULTIPLIER)*/

    protected fun initialValue(): T? {
        return null
    }

    fun get(): T? {
        /*val weakRef: java.lang.ref.WeakReference<T> = t.get()
        if (weakRef == null) {
            val iv = initialValue()
            if (iv != null) {
                set(iv)
                return iv
            } else {
                return null
            }
        } else {
            maybePurge()
            return weakRef.get()
        }*/
        return value
    }

    fun set(`object`: T?) {
        /*t.set(java.lang.ref.WeakReference<T>(`object`))

        synchronized(hardRefs) {
            hardRefs!![java.lang.Thread.currentThread()] = `object`
            maybePurge()
        }*/
        value = `object`
    }

    /*private fun maybePurge() {
        if (countUntilPurge.getAndDecrement() == 0) {
            purge()
        }
    }*/

    // Purge dead threads
    /*private fun purge() {
        synchronized(hardRefs) {
            var stillAliveCount = 0
            val it: MutableIterator<java.lang.Thread> = hardRefs!!.keys.iterator()
            while (it.hasNext()) {
                val t: java.lang.Thread = it.next()
                if (!t.isAlive()) {
                    it.remove()
                } else {
                    stillAliveCount++
                }
            }
            var nextCount = (1 + stillAliveCount) * PURGE_MULTIPLIER
            if (nextCount <= 0) {
                // defensive: int overflow!
                nextCount = 1000000
            }
            countUntilPurge.set(nextCount)
        }
    }*/

    override fun close() {
        // Clear the hard refs; then, the only remaining refs to
        // all values we were storing are weak (unless somewhere
        // else is still using them) and so GC may reclaim them:
        /*hardRefs = null*/
        // Take care of the current thread right now; others will be
        // taken care of via the WeakReferences.
        /*if (t != null) {
            t.remove()
        }
        t = null*/
        value = null
    }

    /*companion object {
        // Increase this to decrease frequency of purging in get:
        private const val PURGE_MULTIPLIER = 20
    }*/
}
