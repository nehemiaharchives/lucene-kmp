package org.gnit.lucenekmp.jdkport

import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.random.Random
import kotlin.reflect.KClass

/**
 * port of java.lang.ClassValue
 */
abstract class KClassValue<T : Any>
/**
 * Sole constructor.  (For invocation by subclass constructors, typically
 * implicit.)
 */
protected constructor() {
    /**
     * Computes the given class's derived value for this `ClassValue`.
     *
     *
     * This method will be invoked within the first thread that accesses
     * the value with the [get][.get] method.
     *
     *
     * Normally, this method is invoked at most once per class,
     * but it may be invoked again if there has been a call to
     * [remove][.remove].
     *
     *
     * If this method throws an exception, the corresponding call to `get`
     * will terminate abnormally with that exception, and no class value will be recorded.
     *
     * @param type the type whose class value must be computed
     * @return the newly computed value associated with this `ClassValue`, for the given class or interface
     * @see .get
     *
     * @see .remove
     */
    protected abstract fun computeValue(type: KClass<*>?): T

    /**
     * Returns the value for the given class.
     * If no value has yet been computed, it is obtained by
     * an invocation of the [computeValue][.computeValue] method.
     *
     *
     * The actual installation of the value on the class
     * is performed atomically.
     * At that point, if several racing threads have
     * computed values, one is chosen, and returned to
     * all the racing threads.
     *
     *
     * The `type` parameter is typically a class, but it may be any type,
     * such as an interface, a primitive type (like `int.class`), or `void.class`.
     *
     *
     * In the absence of `remove` calls, a class value has a simple
     * state diagram:  uninitialized and initialized.
     * When `remove` calls are made,
     * the rules for value observation are more complex.
     * See the documentation for [remove][.remove] for more information.
     *
     * @param type the type whose class value must be computed or retrieved
     * @return the current value associated with this `ClassValue`, for the given class or interface
     * @throws NullPointerException if the argument is null
     * @see .remove
     *
     * @see .computeValue
     */
    fun get(type: KClass<*>): T {
        // non-racing this.hashCodeForCache : final int
        val cache: Array<Entry<*>?>
        val e: Entry<T> = ClassValueMap.probeHomeLocation<T>(
            getCacheCarefully(type).also { cache = it },
            this
        )!!
        // racing e : current value <=> stale value from current cache or from stale cache
        // invariant:  e is null or an Entry with readable Entry.version and Entry.value
        if (match(e))  // invariant:  No false positive matches.  False negatives are OK if rare.
        // The key fact that makes this work: if this.version == e.version,
        // then this thread has a right to observe (final) e.value.
            return e.value()
        // The fast path can fail for any of these reasons:
        // 1. no entry has been computed yet
        // 2. hash code collision (before or after reduction mod cache.length)
        // 3. an entry has been removed (either on this type or another)
        // 4. the GC has somehow managed to delete e.version and clear the reference
        return getFromBackup(cache, type)
    }

    /**
     * Removes the associated value for the given class.
     * If this value is subsequently [read][.get] for the same class,
     * its value will be reinitialized by invoking its [computeValue][.computeValue] method.
     * This may result in an additional invocation of the
     * `computeValue` method for the given class.
     *
     *
     * In order to explain the interaction between `get` and `remove` calls,
     * we must model the state transitions of a class value to take into account
     * the alternation between uninitialized and initialized states.
     * To do this, number these states sequentially from zero, and note that
     * uninitialized (or removed) states are numbered with even numbers,
     * while initialized (or re-initialized) states have odd numbers.
     *
     *
     * When a thread `T` removes a class value in state `2N`,
     * nothing happens, since the class value is already uninitialized.
     * Otherwise, the state is advanced atomically to `2N+1`.
     *
     *
     * When a thread `T` queries a class value in state `2N`,
     * the thread first attempts to initialize the class value to state `2N+1`
     * by invoking `computeValue` and installing the resulting value.
     *
     *
     * When `T` attempts to install the newly computed value,
     * if the state is still at `2N`, the class value will be initialized
     * with the computed value, advancing it to state `2N+1`.
     *
     *
     * Otherwise, whether the new state is even or odd,
     * `T` will discard the newly computed value
     * and retry the `get` operation.
     *
     *
     * Discarding and retrying is an important proviso,
     * since otherwise `T` could potentially install
     * a disastrously stale value.  For example:
     *
     *  * `T` calls `CV.get(C)` and sees state `2N`
     *  * `T` quickly computes a time-dependent value `V0` and gets ready to install it
     *  * `T` is hit by an unlucky paging or scheduling event, and goes to sleep for a long time
     *  * ...meanwhile, `T2` also calls `CV.get(C)` and sees state `2N`
     *  * `T2` quickly computes a similar time-dependent value `V1` and installs it on `CV.get(C)`
     *  * `T2` (or a third thread) then calls `CV.remove(C)`, undoing `T2`'s work
     *  *  the previous actions of `T2` are repeated several times
     *  *  also, the relevant computed values change over time: `V1`, `V2`, ...
     *  * ...meanwhile, `T` wakes up and attempts to install `V0`; *this must fail*
     *
     * We can assume in the above scenario that `CV.computeValue` uses locks to properly
     * observe the time-dependent states as it computes `V1`, etc.
     * This does not remove the threat of a stale value, since there is a window of time
     * between the return of `computeValue` in `T` and the installation
     * of the new value.  No user synchronization is possible during this time.
     *
     * @param type the type whose class value must be removed
     * @throws NullPointerException if the argument is null
     */
    fun remove(type: KClass<*>) {
        val map = getMap(type)
        map.removeEntry(this)
    }

    // Possible functionality for JSR 292 MR 1
    /*public*/
    fun put(type: KClass<*>, value: T) {
        val map = getMap(type)
        map.changeEntry(this, value)
    }

    /**
     * Slow tail of ClassValue.get to retry at nearby locations in the cache,
     * or take a slow lock and check the hash table.
     * Called only if the first probe was empty or a collision.
     * This is a separate method, so compilers can process it independently.
     */
    private fun getFromBackup(cache: Array<Entry<*>?>, type: KClass<*>): T {
        val e: Entry<T> = ClassValueMap.probeBackupLocations<T>(cache, this)!!
        if (e != null) return e.value()
        return getFromHashMap(type)
    }

    // Hack to suppress warnings on the (T) cast, which is a no-op.
    fun castEntry(e: Entry<*>?): Entry<T>? {
        return e as Entry<T>?
    }

    /** Called when the fast path of get fails, and cache reprobe also fails.
     */
    private fun getFromHashMap(type: KClass<*>): T {
        // The fail-safe recovery is to fall back to the underlying classValueMap.
        val map = getMap(type)
        while (true) {
            var e: Entry<T>? = map.startEntry(this)
            if (!e!!.isPromise) return e.value()
            try {
                // Try to make a real entry for the promised version.
                e = makeEntry(e.version(), computeValue(type))
            } finally {
                // Whether computeValue throws or returns normally,
                // be sure to remove the empty entry.
                e = map.finishEntry(this, e)
            }
            if (e != null) return e.value()
        }
    }

    /** Check that e is non-null, matches this ClassValue, and is live.  */
    fun match(e: Entry<*>?): Boolean {
        // racing e.version : null (blank) => unique Version token => null (GC-ed version)
        // non-racing this.version : v1 => v2 => ... (updates are read faithfully from volatile)
        return (e != null && e.get() === this.version)
        // invariant:  No false positives on version match.  Null is OK for false negative.
        // invariant:  If version matches, then e.value is readable (final set in Entry.<init>)
    }

    /** Internal hash code for accessing Class.classValueMap.cacheArray.  */
    val hashCodeForCache: Int = /*nextHashCode.getAndAdd(HASH_INCREMENT) and HASH_MASK*/
        (nextHashCode + HASH_INCREMENT) and HASH_MASK

    /**
     * Private key for retrieval of this object from ClassValueMap.
     */
    class Identity

    /**
     * This ClassValue's identity, expressed as an opaque object.
     * The main object `ClassValue.this` is incorrect since
     * subclasses may override `ClassValue.equals`, which
     * could confuse keys in the ClassValueMap.
     */
    val identity: Identity = Identity()

    /**
     * Current version for retrieving this class value from the cache.
     * Any number of computeValue calls can be cached in association with one version.
     * But the version changes when a remove (on any type) is executed.
     * A version change invalidates all cache entries for the affected ClassValue,
     * by marking them as stale.  Stale cache entries do not force another call
     * to computeValue, but they do require a synchronized visit to a backing map.
     *
     *
     * All user-visible state changes on the ClassValue take place under
     * a lock inside the synchronized methods of ClassValueMap.
     * Readers (of ClassValue.get) are notified of such state changes
     * when this.version is bumped to a new token.
     * This variable must be volatile so that an unsynchronized reader
     * will receive the notification without delay.
     *
     *
     * If version were not volatile, one thread T1 could persistently hold onto
     * a stale value this.value == V1, while another thread T2 advances
     * (under a lock) to this.value == V2.  This will typically be harmless,
     * but if T1 and T2 interact causally via some other channel, such that
     * T1's further actions are constrained (in the JMM) to happen after
     * the V2 event, then T1's observation of V1 will be an error.
     *
     *
     * The practical effect of making this.version be volatile is that it cannot
     * be hoisted out of a loop (by an optimizing JIT) or otherwise cached.
     * Some machines may also require a barrier instruction to execute
     * before this.version.
     */
    @Volatile
    private var version: Version<T> = Version<T>(this)
    fun version(): Version<T> {
        return version
    }

    fun bumpVersion() {
        version = Version<T>(this)
    }

    class Version<T : Any>(private val classValue: KClassValue<T>) {
        private val promise: Entry<T> = Entry<T>(this)
        fun classValue(): KClassValue<T> {
            return classValue
        }

        fun promise(): Entry<T> {
            return promise
        }

        val isLive: Boolean
            get() = classValue.version() === this
    }

    /** One binding of a value to a class via a ClassValue.
     * States are:
     *  *  promise if value == Entry.this
     *  *  else dead if version == null
     *  *  else stale if version != classValue.version
     *  *  else live
     * Promises are never put into the cache; they only live in the
     * backing map while a computeValue call is in flight.
     * Once an entry goes stale, it can be reset at any time
     * into the dead state.
     */
    class Entry<T : Any> : WeakReference<Version<T>?> {
        val value: Any? // usually of type T, but sometimes (Entry)this

        constructor(version: Version<T>?, value: T?) : super(version) {
            this.value = value // for a regular entry, value is of type T
        }

        private fun assertNotPromise() {
            require(!isPromise)
        }

        /** For creating a promise.  */
        constructor(version: Version<T>?) : super(version) {
            this.value = this // for a promise, value is not of type T, but Entry!
        }

        /** Fetch the value.  This entry must not be a promise.  */
        fun value(): T {
            assertNotPromise()
            return value as T
        }

        val isPromise: Boolean
            get() = value === this

        fun version(): Version<T>? {
            return get()
        }

        fun classValueOrNull(): KClassValue<T>? {
            val v = version()
            return v?.classValue()
        }

        val isLive: Boolean
            get() {
                val v = version() ?: return false
                if (v.isLive) return true
                clear()
                return false
            }

        fun refreshVersion(v2: Version<T>?): Entry<T> {
            assertNotPromise()
            val e2// if !isPromise, type is T
                    = Entry(v2, value as T)
            clear()
            // value = null -- caller must drop
            return e2
        }

        companion object {
            val DEAD_ENTRY: Entry<*> = Entry<Any>(null, null)
        }
    }

    // The following class could also be top level and non-public:
    /** A backing map for all ClassValues.
     * Gives a fully serialized "true state" for each pair (ClassValue cv, Class type).
     * Also manages an unserialized fast-path cache.
     */
    class ClassValueMap(override val entries: MutableSet<Map.Entry<Identity?, Entry<*>?>> = mutableSetOf()) : AbstractMap<Identity?, Entry<*>?>() {
        lateinit var cache: Array<Entry<*>?>
            private set
        private var cacheLoad = 0
        private var cacheLoadLimit = 0

        /** Initiate a query.  Store a promise (placeholder) if there is no value yet.  */
        fun <T : Any> startEntry(classValue: KClassValue<T>): Entry<T> {
            var e// one map has entries for all value types <T>
                    = get(classValue.identity) as Entry<T>?
            val v = classValue.version()
            if (e == null) {
                e = v.promise()
                // The presence of a promise means that a value is pending for v.
                // Eventually, finishEntry will overwrite the promise.
                put(classValue.identity, e)
                // Note that the promise is never entered into the cache!
                return e
            } else if (e.isPromise) {
                // Somebody else has asked the same question.
                // Let the races begin!
                if (e.version() !== v) {
                    e = v.promise()
                    put(classValue.identity, e)
                }
                return e
            } else {
                // there is already a completed entry here; report it
                if (e.version() !== v) {
                    // There is a stale but valid entry here; make it fresh again.
                    // Once an entry is in the hash table, we don't care what its version is.
                    e = e.refreshVersion(v)
                    put(classValue.identity, e)
                }
                // Add to the cache, to enable the fast path, next time.
                checkCacheLoad()
                addToCache(classValue, e)
                return e
            }
        }

        /** Finish a query.  Overwrite a matching placeholder.  Drop stale incoming values.  */
        fun <T : Any> finishEntry(classValue: KClassValue<T>, e: Entry<T>): Entry<T>? {
            var e = e
            val e0// one map has entries for all value types <T>
                    = get(classValue.identity) as Entry<T>?
            if (e === e0) {
                // We can get here during exception processing, unwinding from computeValue.
                require(e.isPromise)
                remove(classValue.identity)
                return null
            } else if (e0 != null && e0.isPromise && e0.version() === e.version()) {
                // If e0 matches the intended entry, there has not been a remove call
                // between the previous startEntry and now.  So now overwrite e0.
                val v = classValue.version()
                if (e.version() !== v) e = e.refreshVersion(v)
                put(classValue.identity, e)
                // Add to the cache, to enable the fast path, next time.
                checkCacheLoad()
                addToCache(classValue, e)
                return e
            } else {
                // Some sort of mismatch; caller must try again.
                return null
            }
        }

        /** Remove an entry.  */
        fun removeEntry(classValue: KClassValue<*>) {
            val e: Entry<*>? = remove(classValue.identity)
            if (e == null) {
                // Uninitialized, and no pending calls to computeValue.  No change.
            } else if (e.isPromise) {
                // State is uninitialized, with a pending call to finishEntry.
                // Since remove is a no-op in such a state, keep the promise
                // by putting it back into the map.
                put(classValue.identity, e)
            } else {
                // In an initialized state.  Bump forward, and de-initialize.
                classValue.bumpVersion()
                // Make all cache elements for this guy go stale.
                removeStaleEntries(classValue)
            }
        }

        /** Change the value for an entry.  */
        fun <T : Any> changeEntry(classValue: KClassValue<T>, value: T) {
            val e0// one map has entries for all value types <T>
                    = get(classValue.identity) as Entry<T>?
            val version = classValue.version()
            if (e0 != null) {
                if (e0.version() === version && e0.value() === value)  // no value change => no version change needed
                    return
                classValue.bumpVersion()
                removeStaleEntries(classValue)
            }
            val e = makeEntry(version, value)
            put(classValue.identity, e)
            // Add to the cache, to enable the fast path, next time.
            checkCacheLoad()
            addToCache(classValue, e)
        }

        /** --------
         * Below this line all functions are private, and assume synchronized access.
         * -------- */
        private fun sizeCache(length: Int) {
            require((length and (length - 1)) == 0) // must be power of 2
            cacheLoad = 0
            cacheLoadLimit = (length.toDouble() * CACHE_LOAD_LIMIT / 100).toInt()
            cache = /*arrayOfNulls(length)*/ Array(length) { Entry<Any>(null, null) }
        }

        /** Make sure the cache load stays below its limit, if possible.  */
        private fun checkCacheLoad() {
            if (cacheLoad >= cacheLoadLimit) {
                reduceCacheLoad()
            }
        }

        private fun reduceCacheLoad() {
            removeStaleEntries()
            if (cacheLoad < cacheLoadLimit) return  // win

            val oldCache = cache
            if (oldCache.size > HASH_MASK) return  // lose

            sizeCache(oldCache.size * 2)
            for (e in oldCache) {
                if (e != null && e.isLive) {
                    addToCache(e)
                }
            }
        }

        /** Remove stale entries in the given range.
         * Should be executed under a Map lock.
         */
        private fun removeStaleEntries(cache: Array<Entry<*>?>, begin: Int, count: Int) {
            if (PROBE_LIMIT <= 0) return
            val mask = (cache.size - 1)
            var removed = 0
            for (i in begin..<begin + count) {
                val e = cache[i and mask]
                if (e == null || e.isLive) continue  // skip null and live entries

                var replacement: Entry<*>? = null
                if (PROBE_LIMIT > 1) {
                    // avoid breaking up a non-null run
                    replacement = findReplacement(cache, i)
                }
                cache[i and mask] = replacement!!
                if (replacement == null) removed += 1
            }
            cacheLoad = max(0, cacheLoad - removed)
        }

        /** Clearing a cache slot risks disconnecting following entries
         * from the head of a non-null run, which would allow them
         * to be found via reprobes.  Find an entry after cache[begin]
         * to plug into the hole, or return null if none is needed.
         */
        private fun findReplacement(cache: Array<Entry<*>?>, home1: Int): Entry<*>? {
            var replacement: Entry<*>? = null
            var haveReplacement = -1
            var replacementPos = 0
            val mask = (cache.size - 1)
            for (i2 in home1 + 1..<home1 + PROBE_LIMIT) {
                val e2 = cache[i2 and mask] ?: break
                // End of non-null run.

                if (!e2.isLive) continue  // Doomed anyway.

                val dis2 = entryDislocation(cache, i2, e2)
                if (dis2 == 0) continue  // e2 already optimally placed

                val home2 = i2 - dis2
                if (home2 <= home1) {
                    // e2 can replace entry at cache[home1]
                    if (home2 == home1) {
                        // Put e2 exactly where he belongs.
                        haveReplacement = 1
                        replacementPos = i2
                        replacement = e2
                    } else if (haveReplacement <= 0) {
                        haveReplacement = 0
                        replacementPos = i2
                        replacement = e2
                    }
                    // And keep going, so we can favor larger dislocations.
                }
            }
            if (haveReplacement >= 0) {
                if (cache[(replacementPos + 1) and mask] != null) {
                    // Be conservative, to avoid breaking up a non-null run.
                    cache[replacementPos and mask] = Entry.DEAD_ENTRY
                } else {
                    cache[replacementPos and mask] = null
                    cacheLoad -= 1
                }
            }
            return replacement
        }

        /** Remove stale entries in the range near classValue.  */
        private fun removeStaleEntries(classValue: KClassValue<*>) {
            removeStaleEntries(cache, classValue.hashCodeForCache, PROBE_LIMIT)
        }

        /** Remove all stale entries, everywhere.  */
        private fun removeStaleEntries() {
            val cache = cache
            removeStaleEntries(cache, 0, cache.size + PROBE_LIMIT - 1)
        }

        /** Add the given entry to the cache, in its home location, unless it is out of date.  */
        private fun <T : Any> addToCache(e: Entry<T>) {
            val classValue = e.classValueOrNull()
            if (classValue != null) addToCache(classValue, e)
        }

        /** Add the given entry to the cache, in its home location.  */
        private fun <T : Any> addToCache(classValue: KClassValue<T>, e: Entry<T>) {
            if (PROBE_LIMIT <= 0) return  // do not fill cache

            // Add e to the cache.
            val cache = cache
            val mask = (cache.size - 1)
            val home = classValue.hashCodeForCache and mask
            val e2 = placeInCache(cache, home, e, false) ?: return
            // done

            if (PROBE_LIMIT > 1) {
                // try to move e2 somewhere else in his probe range
                val dis2 = entryDislocation(cache, home, e2)
                val home2 = home - dis2
                for (i2 in home2..<home2 + PROBE_LIMIT) {
                    if (placeInCache(cache, i2 and mask, e2, true) == null) {
                        return
                    }
                }
            }
            // Note:  At this point, e2 is just dropped from the cache.
        }

        /** Store the given entry.  Update cacheLoad, and return any live victim.
         * 'Gently' means return self rather than dislocating a live victim.
         */
        private fun placeInCache(cache: Array<Entry<*>?>, pos: Int, e: Entry<*>, gently: Boolean): Entry<*>? {
            val e2 = overwrittenEntry(cache[pos])
            if (gently && e2 != null) {
                // do not overwrite a live entry
                return e
            } else {
                cache[pos] = e
                return e2
            }
        }

        /** Note an entry that is about to be overwritten.
         * If it is not live, quietly replace it by null.
         * If it is an actual null, increment cacheLoad,
         * because the caller is going to store something
         * in its place.
         */
        private fun <T : Any> overwrittenEntry(e2: Entry<T>?): Entry<T>? {
            if (e2 == null) cacheLoad += 1
            else if (e2.isLive) return e2
            return null
        }

        /** Build a backing map for ClassValues.
         * Also, create an empty cache array and install it on the class.
         */
        init {
            sizeCache(INITIAL_ENTRIES)
        }

        companion object {
            /** Number of entries initially allocated to each type when first used with any ClassValue.
             * It would be pointless to make this much smaller than the Class and ClassValueMap objects themselves.
             * Must be a power of 2.
             */
            private const val INITIAL_ENTRIES = 32

            //| --------
            //| Cache management.
            //| --------
            // Statics do not need synchronization.
            /** Load the cache entry at the given (hashed) location.  */
            fun loadFromCache(cache: Array<Entry<*>?>, i: Int): Entry<*>? {
                // non-racing cache.length : constant
                // racing cache[i & (mask)] : null <=> Entry
                return cache[i and (cache.size - 1)]
                // invariant:  returned value is null or well-constructed (ready to match)
            }

            /** Look in the cache, at the home location for the given ClassValue.  */
            fun <T : Any> probeHomeLocation(cache: Array<Entry<*>?>, classValue: KClassValue<T>): Entry<T>? {
                return classValue.castEntry(loadFromCache(cache, classValue.hashCodeForCache))
            }

            /** Given that first probe was a collision, retry at nearby locations.  */
            fun <T : Any> probeBackupLocations(cache: Array<Entry<*>?>, classValue: KClassValue<T>): Entry<T>? {
                if (PROBE_LIMIT <= 0) return null
                // Probe the cache carefully, in a range of slots.
                val mask = (cache.size - 1)
                val home = (classValue.hashCodeForCache and mask)
                val e2 = cache[home]
                    ?: return null // if nobody is at home, no need to search nearby
                // victim, if we find the real guy
                // assume !classValue.match(e2), but do not assert, because of races
                var pos2 = -1
                for (i in home + 1..<home + PROBE_LIMIT) {
                    val e = cache[i and mask]
                        ?: break // only search within non-null runs
                    if (classValue.match(e)) {
                        // relocate colliding entry e2 (from cache[home]) to first empty slot
                        cache[home] = e
                        if (pos2 >= 0) {
                            cache[i and mask] = Entry.DEAD_ENTRY
                        } else {
                            pos2 = i
                        }
                        cache[pos2 and mask] = (if (entryDislocation(cache, pos2, e2) < PROBE_LIMIT)
                            e2 // put e2 here if it fits
                        else
                            Entry.DEAD_ENTRY)
                        return classValue.castEntry(e)
                    }
                    // Remember first empty slot, if any:
                    if (!e.isLive && pos2 < 0) pos2 = i
                }
                return null
            }

            /** How far out of place is e?  */
            private fun entryDislocation(cache: Array<Entry<*>?>, pos: Int, e: Entry<*>): Int {
                val cv = e.classValueOrNull() ?: return 0
                // entry is not live!

                val mask = (cache.size - 1)
                return (pos - cv.hashCodeForCache) and mask
            }

            /** Percent loading of cache before resize.  */
            private const val CACHE_LOAD_LIMIT = 67 // 0..100

            /** Maximum number of probes to attempt.  */
            private const val PROBE_LIMIT = 6 // 1..
            // N.B.  Set PROBE_LIMIT=0 to disable all fast paths.
        }

        fun put(key: Identity?, value: Entry<*>?) {
            this.entries.add(object : Map.Entry<Identity?, Entry<*>?> {
                override val key: Identity? = key
                override val value: Entry<*>? = value
            })
        }

        fun remove(key: Identity?): Entry<*>? {
            val entry = this.entries.firstOrNull { it.key == key }
            this.entries.remove(entry)
            return entry?.value
        }

        override fun containsKey(key: Identity?): Boolean {
            return get(key) != null
        }

        override fun containsValue(value: Entry<*>?): Boolean {
            return entries.any { it.value == value }
        }
    }

    companion object {
        //| --------
        //| Implementation...
        //| --------

        fun getHardCodedClassValueMap(type: KClass<*>): ClassValueMap {
            // TODO hardcoded class value map

            val classValueMap: ClassValueMap = ClassValueMap()

            return classValueMap
        }


        /** Return the cache, if it exists, else a dummy empty cache.  */
        private fun getCacheCarefully(type: KClass<*>): Array<Entry<*>?> {
            // racing type.classValueMap{.cacheArray} : null => new Entry[X] <=> new Entry[Y]
            val map: ClassValueMap = getHardCodedClassValueMap(type) /*type.classValueMap */?: return EMPTY_CACHE
            val cache = map.cache
            return cache
            // invariant:  returned value is safe to dereference and check for an Entry
        }

        /** Initial, one-element, empty cache used by all Class instances.  Must never be filled.  */
        private val EMPTY_CACHE = emptyArray<Entry<*>?>()

        /** Value stream for hashCodeForCache.  See similar structure in ThreadLocal.  */
        private val nextHashCode: Int = /*java.util.concurrent.atomic.AtomicInteger =
            java.util.concurrent.atomic.AtomicInteger()*/
                Random.nextInt()

        /** Good for power-of-two tables.  See similar structure in ThreadLocal.  */
        private const val HASH_INCREMENT = 0x61c88647

        /** Mask a hash code to be positive but not too large, to prevent wraparound.  */
        const val HASH_MASK: Int = (-1 ushr 2)

        /** Return the backing map associated with this type.  */
        private fun getMap(type: KClass<*>): ClassValueMap {
            // racing type.classValueMap : null (blank) => unique ClassValueMap
            // if a null is observed, a map is created (lazily, synchronously, uniquely)
            // all further access to that map is synchronized
            val map: ClassValueMap = /*type.classValueMap*/ getHardCodedClassValueMap(type)
            if (map != null) return map
            return initializeMap(type)
        }

        private val CRITICAL_SECTION = Any()
        /*private val UNSAFE: jdk.internal.misc.Unsafe = jdk.internal.misc.Unsafe.getUnsafe()*/
        private fun initializeMap(type: KClass<*>): ClassValueMap {
            var map: ClassValueMap
            /*synchronized(CRITICAL_SECTION) {  // private object to avoid deadlocks
                // happens about once per type
                if ((type.classValueMap.also { map = it }) == null) {
                    map = ClassValueMap()
                    // Place a Store fence after construction and before publishing to emulate
                    // ClassValueMap containing final fields. This ensures it can be
                    // published safely in the non-volatile field Class.classValueMap,
                    // since stores to the fields of ClassValueMap will not be reordered
                    // to occur after the store to the field type.classValueMap
                    UNSAFE.storeFence()

                    type.classValueMap = map
                }
            }*/

            map = ClassValueMap()
            return map
        }

        fun <T : Any> makeEntry(explicitVersion: Version<T>?, value: T): Entry<T> {
            // Note that explicitVersion might be different from this.version.
            return Entry(explicitVersion, value)

            // As soon as the Entry is put into the cache, the value will be
            // reachable via a data race (as defined by the Java Memory Model).
            // This race is benign, assuming the value object itself can be
            // read safely by multiple threads.  This is up to the user.
            //
            // The entry and version fields themselves can be safely read via
            // a race because they are either final or have controlled states.
            // If the pointer from the entry to the version is still null,
            // or if the version goes immediately dead and is nulled out,
            // the reader will take the slow path and retry under a lock.
        }
    }
}
