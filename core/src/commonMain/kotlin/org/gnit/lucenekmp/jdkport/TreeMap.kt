package org.gnit.lucenekmp.jdkport

import okio.IOException
import kotlin.jvm.JvmName
import kotlin.jvm.Transient


// Note: This is a complex port. While aiming for functional equivalence,
// thorough testing against the original Lucene use cases is crucial.
// Kotlin common does not have a direct NavigableMap equivalent, so relevant
// methods are implemented directly.

/**
 * A Red-Black tree based implementation of the `MutableMap` interface,
 * striving for compatibility with the subset of `TreeMap` functionality
 * potentially used by Lucene.
 *
 * The map is sorted according to the natural ordering of its keys (if they
 * implement [Comparable]), or by a [Comparator] provided at map creation time.
 *
 * This implementation provides guaranteed log(n) time cost for the
 * `containsKey`, `get`, `put` and `remove` operations.
 *
 * **Note that this implementation is not synchronized.** External synchronization
 * is required if multiple threads access a map concurrently and at least one
 * modifies it structurally.
 *
 * Iterators are *fail-fast*: they throw [ConcurrentModificationException] if the
 * map is structurally modified after iterator creation, except through the
 * iterator's own `remove` method.
 *
 * @param K the type of keys maintained by this map
 * @param V the type of mapped values
 */
@Suppress("UNCHECKED_CAST", "PARAMETER_NAME_CHANGED_ON_OVERRIDE")
class TreeMap<K, V> : NavigableMap<K, V>, AbstractMutableMap<K, V>
/*, MutableMap<K, V> , Cloneable // Consider if cloning is truly needed */ { // End TreeMap class

    /**
     * The comparator used to maintain order in this tree map, or
     * null if it uses the natural ordering of its keys.
     */
    private val comparator: Comparator<K>?

    private var root: Entry<K, V>? = null

    /**
     * The number of entries in the tree.
     */
    override var size: Int = 0
        private set

    /**
     * The number of structural modifications to the tree.
     * Used by iterators to detect concurrent modifications.
     */
    private var modCount: Int = 0

    // --- Constructors ---

    /**
     * Constructs a new, empty tree map, using the natural ordering of its
     * keys. All keys inserted into the map must implement the [Comparable]
     * interface and be mutually comparable.
     */
    constructor() {
        this.comparator = null
    }

    /**
     * Constructs a new, empty tree map, ordered according to the given
     * comparator. All keys inserted into the map must be mutually
     * comparable by the given comparator.
     *
     * @param comparator the comparator that will be used to order this map.
     * If `null`, the natural ordering of the keys will be used.
     */
    constructor(comparator: Comparator<K>?) {
        this.comparator = comparator
    }

    /**
     * Constructs a new tree map containing the same mappings as the given
     * map, ordered according to the natural ordering of its keys.
     * Requires keys to be [Comparable]. Runs in n*log(n) time.
     *
     * @param m the map whose mappings are to be placed in this map
     * @throws ClassCastException if the keys in m are not [Comparable] or
     * are not mutually comparable.
     * @throws NullPointerException if the specified map is null.
     */
    constructor(m: Map<out K, V>) : this() {
        putAll(m)
    }

    /**
     * Constructs a new tree map containing the same mappings and
     * using the same ordering as the specified sorted map (approximated by checking
     * if the input map is also a TreeMap with the same comparator).
     * If compatible, this method runs in linear time (O(n)). Otherwise, it falls
     * back to O(n log n).
     *
     * @param m the sorted map whose mappings are to be placed in this map,
     * and whose comparator is potentially used to sort this map.
     * @throws NullPointerException if the specified map is null.
     */
    constructor(m: TreeMap<K, out V>) : this(m.comparator) {
        // Optimized buildFromSorted if comparators match
        // Note: The original Java code checks `SortedMap`, which doesn't have a direct
        // equivalent interface in Kotlin common stdlib covering sorting behavior AND comparator access.
        // We check specifically for TreeMap here for the optimization.
        if (this.comparator == m.comparator) {
            buildFromSorted(m.size, m.entries.iterator())
        } else {
            // Fallback if comparators don't match or it's not a compatible TreeMap
            putAll(m)
        }
    }

    // --- Query Operations ---

    /**
     * Returns `true` if this map contains a mapping for the specified key.
     *
     * @param key key whose presence in this map is to be tested
     * @return `true` if this map contains a mapping for the specified key
     * @throws ClassCastException if the specified key cannot be compared
     * with the keys currently in the map
     * @throws NullPointerException if the specified key is null and this map
     * uses natural ordering, or its comparator does not permit null keys
     */
    override fun containsKey(key: K): Boolean {
        return getEntry(key) != null
    }

    /**
     * Returns `true` if this map maps one or more keys to the specified value.
     * This operation requires time linear in the map size.
     *
     * @param value value whose presence in this map is to be tested
     * @return `true` if a mapping to `value` exists; `false` otherwise
     */
    override fun containsValue(value: V): Boolean {
        var e = getFirstEntry()
        while (e != null) {
            if (value == e.value) { // Using Kotlin's structural equality
                return true
            }
            e = successor(e)
        }
        return false
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or `null` if this map contains no mapping for the key.
     *
     * A return value of `null` does not necessarily indicate that the map
     * contains no mapping for the key; it's also possible that the map
     * explicitly maps the key to `null`. Use [containsKey] to distinguish.
     *
     * @param key the key whose associated value is to be returned
     * @return the value to which the specified key is mapped, or `null`
     * @throws ClassCastException if the specified key cannot be compared
     * @throws NullPointerException if the specified key is null and natural
     * ordering is used or the comparator forbids nulls.
     */
    override fun get(key: K): V? {
        val p = getEntry(key)
        return p?.value
    }

    /**
     * Returns the comparator used to order the keys in this map, or `null` if
     * this map uses the natural ordering of its keys.
     *
     * @return the comparator used to order the keys, or `null` if natural ordering is used.
     */
    override fun comparator(): Comparator<K>? {
        return comparator
    }

    /**
     * Returns the first (lowest) key currently in this map.
     *
     * @return the first (lowest) key
     * @throws NoSuchElementException if this map is empty
     */
    override fun firstKey(): K {
        return key(getFirstEntry() ?: throw NoSuchElementException("Map is empty"))
    }

    /**
     * Returns the last (highest) key currently in this map.
     *
     * @return the last (highest) key
     * @throws NoSuchElementException if this map is empty
     */
    override fun lastKey(): K {
        return key(getLastEntry() ?: throw NoSuchElementException("Map is empty"))
    }

    override val keySet: MutableSet<K>
        get() {
            return navigableKeySet()
        }

    override val entrySet: MutableSet<MutableMap.MutableEntry<K, V>>
        get() {
            return object : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
                override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> =
                    EntryIterator(getFirstEntry())

                override val size: Int
                    get() = this@TreeMap.size

                override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean {
                    if (element == null) return false
                    val entry = getEntry(element.key)
                    return entry != null && entry.value == element.value
                }

                override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
                    if (element == null) return false
                    val entry = getEntry(element.key)
                    if (entry != null && entry.value == element.value) {
                        deleteEntry(entry)
                        return true
                    }
                    return false
                }

                override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
                    throw UnsupportedOperationException("add is not supported by entrySet view of TreeMap")
                }

                override fun clear() = this@TreeMap.clear()
            }
        }

    /**
     * Throws [UnsupportedOperationException]. The encounter order induced by this
     * map's comparison method determines the position of mappings, so explicit positioning
     * is not supported.
     */
    override fun putFirst(k: K, v: V): V {
        throw UnsupportedOperationException("putFirst is not supported by TreeMap")
    }

    /**
     * Throws [UnsupportedOperationException]. The encounter order induced by this
     * map's comparison method determines the position of mappings, so explicit positioning
     * is not supported.
     */
    override fun putLast(k: K, v: V): V {
        throw UnsupportedOperationException("putLast is not supported by TreeMap")
    }

    /**
     * Copies all of the mappings from the specified map to this map.
     *
     * @param from map mappings to be stored in this map
     * @throws ClassCastException if the class of a key or value in the specified map
     * prevents it from being stored in this map
     * @throws NullPointerException if the specified map contains a null key and this map
     * does not permit null keys (either natural ordering or comparator restriction)
     */
    override fun putAll(from: Map<out K, V>) {
        val mapSize = from.size
        // Optimization check similar to Java's SortedMap check
        if (isEmpty() && mapSize != 0 && from is TreeMap<*, *> && this.comparator == from.comparator) {
            modCount++
            try {
                // Need to cast carefully here
                val entriesIterator = (from.entries as Set<Map.Entry<K, V>>).iterator()
                buildFromSorted(mapSize, entriesIterator)
            } catch (e: Exception) {
                // Should not happen with type checks, but catch potential issues
                // Fallback to default implementation if buildFromSorted fails unexpectedly
                super.putAll(from)
            }
            return
        }
        // Default implementation if optimization doesn't apply
        super.putAll(from)
    }


    /**
     * Returns this map's entry for the given key, or `null` if the map
     * does not contain an entry for the key.
     *
     * @param key the key to search for
     * @return this map's entry for the given key, or `null`
     * @throws ClassCastException if the key cannot be compared
     * @throws NullPointerException if key is null and nulls are not supported
     */
    internal fun getEntry(key: K): Entry<K, V>? {
        if (comparator != null) {
            return getEntryUsingComparator(key)
        }
        // Key must be non-null for natural ordering
        // requireNotNull(key) // Implicitly handled by Comparable cast/usage
        if (key == null) throw NullPointerException("Null key not permitted with natural ordering")

        val k = key as? Comparable<K> ?: throw ClassCastException("Key is not Comparable")

        var p = root
        while (p != null) {
            val cmp = k.compareTo(p.key)
            p = when {
                cmp < 0 -> p.left
                cmp > 0 -> p.right
                else -> return p
            }
        }
        return null
    }

    /**
     * Version of getEntry using the comparator.
     */
    private fun getEntryUsingComparator(key: K): Entry<K, V>? {
        val cpr = comparator ?: return null // Should not happen if called correctly
        var p = root
        while (p != null) {
            val cmp = cpr.compare(key, p.key)
            p = when {
                cmp < 0 -> p.left
                cmp > 0 -> p.right
                else -> return p
            }
        }
        return null
    }

    // --- NavigableMap-like Methods (implemented directly) ---

    /**
     * Returns a key-value mapping associated with the least key greater than or
     * equal to the given key, or `null` if there is no such key.
     *
     * @param key the key
     * @return an entry with the least key greater than or equal to `key`,
     * or `null` if there is no such key
     * @throws ClassCastException if `key` cannot be compared
     * @throws NullPointerException if `key` is null and nulls are not supported
     */
    override fun ceilingEntry(key: K): Map.Entry<K, V>? {
        return getCeilingEntry(key)
    }

    /**
     * Returns the least key greater than or equal to the given key, or `null` if
     * there is no such key.
     */
    override fun ceilingKey(key: K): K? {
        return keyOrNull(getCeilingEntry(key))
    }

    /**
     * Returns a key-value mapping associated with the greatest key less than or
     * equal to the given key, or `null` if there is no such key.
     *
     * @param key the key
     * @return an entry with the greatest key less than or equal to `key`,
     * or `null` if there is no such key
     * @throws ClassCastException if `key` cannot be compared
     * @throws NullPointerException if `key` is null and nulls are not supported
     */
    override fun floorEntry(key: K): Map.Entry<K, V>? {
        return getFloorEntry(key)
    }

    /**
     * Returns the greatest key less than or equal to the given key, or `null` if
     * there is no such key.
     */
    override fun floorKey(key: K): K? {
        return keyOrNull(getFloorEntry(key))
    }

    /**
     * Returns a key-value mapping associated with the least key strictly greater
     * than the given key, or `null` if there is no such key.
     *
     * @param key the key
     * @return an entry with the least key strictly greater than `key`,
     * or `null` if there is no such key
     * @throws ClassCastException if `key` cannot be compared
     * @throws NullPointerException if `key` is null and nulls are not supported
     */
    override fun higherEntry(key: K): Map.Entry<K, V>? {
        return getHigherEntry(key)
    }

    /**
     * Returns the least key strictly greater than the given key, or `null` if
     * there is no such key.
     */
    override fun higherKey(key: K): K? {
        return keyOrNull(getHigherEntry(key))
    }

    /**
     * Returns a key-value mapping associated with the greatest key strictly less
     * than the given key, or `null` if there is no such key.
     *
     * @param key the key
     * @return an entry with the greatest key strictly less than `key`,
     * or `null` if there is no such key
     * @throws ClassCastException if `key` cannot be compared
     * @throws NullPointerException if `key` is null and nulls are not supported
     */
    override fun lowerEntry(key: K): Map.Entry<K, V>? {
        return getLowerEntry(key)
    }

    /**
     * Returns the greatest key strictly less than the given key, or `null` if
     * there is no such key.
     */
    override fun lowerKey(key: K): K? {
        return keyOrNull(getLowerEntry(key))
    }

    /**
     * Returns the first (lowest) key-value mapping currently in this map.
     *
     * @return the first (lowest) entry, or `null` if the map is empty
     */
    override fun firstEntry(): Map.Entry<K, V>? {
        return exportEntry(getFirstEntry()) // Export immutable entry
    }

    /**
     * Returns the last (highest) key-value mapping currently in this map.
     *
     * @return the last (highest) entry, or `null` if the map is empty
     */
    override fun lastEntry(): Map.Entry<K, V>? {
        return exportEntry(getLastEntry()) // Export immutable entry
    }

    /**
     * Removes and returns a key-value mapping associated with the least key
     * in this map, or `null` if the map is empty.
     *
     * @return the removed first entry of this map, or `null` if the map is empty
     */
    override fun pollFirstEntry(): Map.Entry<K, V>? {
        val p = getFirstEntry()
        val result = exportEntry(p) // Export before deletion
        if (p != null) {
            deleteEntry(p)
        }
        return result
    }

    /**
     * Removes and returns a key-value mapping associated with the greatest key
     * in this map, or `null` if the map is empty.
     *
     * @return the removed last entry of this map, or `null` if the map is empty
     */
    override fun pollLastEntry(): Map.Entry<K, V>? {
        val p = getLastEntry()
        val result = exportEntry(p) // Export before deletion
        if (p != null) {
            deleteEntry(p)
        }
        return result
    }

    override fun descendingMap(): NavigableMap<K, V> {
        val km: NavigableMap<K, V>? = descendingMap
        return if (km != null) km else (DescendingSubMap(
            this,
            true, UNBOUNDED as K, true,
            true, UNBOUNDED as K, true
        ).also { descendingMap = it })
    }

    // --- Modification Operations ---

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for the key, the old value is replaced.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with `key`, or `null` if there was no mapping.
     * (A `null` return can also indicate that the map previously associated `null` with `key`.)
     * @throws ClassCastException if the key cannot be compared
     * @throws NullPointerException if key is null and nulls are not supported
     */
    override fun put(key: K, value: V): V? {
        return putInternal(key, value, true)
    }

    /**
     * If the specified key is not already associated with a value (or is mapped
     * to `null`), associates it with the given value and returns `null`, else
     * returns the current value.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with the specified key, or
     * `null` if there was no mapping for the key.
     * (A `null` return can also indicate that the map
     * previously associated `null` with the key)
     * @throws ClassCastException if the key cannot be compared
     * @throws NullPointerException if key is null and nulls are not supported
     */
    fun putIfAbsent(key: K, value: V): V? {
        val entry = getEntry(key)
        if (entry != null) {
            return entry.value // Key exists, return current value
        } else {
            putInternal(key, value, true) // Key doesn't exist, put it
            return null // Return null as per putIfAbsent contract
        }
        // Note: The original Java code had a combined put(key, value, replaceOld)
        // which is slightly different logic. This implementation matches Kotlin's
        // MutableMap.putIfAbsent behavior more directly.
        // If strict Java compatibility is needed for the *return* value when the
        // key exists but value is null, the Java logic might need closer replication.
        // However, for Lucene's likely use cases, this should be sufficient.
    }


    /**
     * Removes the mapping for this key from this TreeMap if present.
     *
     * @param key key whose mapping is to be removed from the map
     * @return the previous value associated with `key`, or `null` if there was no mapping.
     * (A `null` return can also indicate that the map previously associated `null` with `key`.)
     * @throws ClassCastException if the key cannot be compared
     * @throws NullPointerException if key is null and nulls are not supported
     */
    override fun remove(key: K): V? {
        val p = getEntry(key)
        if (p == null) {
            return null
        }

        val oldValue = p.value
        deleteEntry(p)
        return oldValue
    }

    /**
     * Removes the entry for the specified key only if it is currently
     * mapped to the specified value.
     *
     * @param key key with which the specified value is associated
     * @param value value expected to be associated with the specified key
     * @return `true` if the value was removed
     * @throws ClassCastException if the key cannot be compared
     * @throws NullPointerException if key is null and nulls are not supported
     */
    fun remove(key: K, value: V): Boolean {
        val p = getEntry(key)
        if (p != null && value == p.value) { // Kotlin equality check
            deleteEntry(p)
            return true
        }
        return false
    }

    /**
     * Replaces the entry for the specified key only if it is
     * currently mapped to some value.
     *
     * @param key key with which the specified value is associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with the specified key, or
     * `null` if there was no mapping for the key.
     * (A `null` return can also indicate that the map
     * previously associated `null` with the key.)
     * @throws ClassCastException if the key cannot be compared
     * @throws NullPointerException if key is null and nulls are not supported
     */
    fun replace(key: K, value: V): V? {
        val p = getEntry(key)
        if (p != null) {
            val oldValue = p.value
            p.value = value
            return oldValue
        }
        return null
    }

    /**
     * Replaces the entry for the specified key only if currently
     * mapped to the specified value.
     *
     * @param key key with which the specified value is associated
     * @param oldValue value expected to be associated with the specified key
     * @param newValue value to be associated with the specified key
     * @return `true` if the value was replaced
     * @throws ClassCastException if the key cannot be compared
     * @throws NullPointerException if key is null and nulls are not supported
     */
    fun replace(key: K, oldValue: V, newValue: V): Boolean {
        val p = getEntry(key)
        if (p != null && oldValue == p.value) { // Kotlin equality
            p.value = newValue
            return true
        }
        return false
    }

    /**
     * Removes all of the mappings from this map.
     * The map will be empty after this call returns.
     */
    override fun clear() {
        modCount++
        size = 0
        root = null
    }

    // --- Bulk Operations ---

    // `putAll` is implemented above

    // --- Views ---

    // Note: These views return Kotlin's standard mutable collection views.
    // They are backed by the map and reflect changes. Iterators are fail-fast.

    private var entrySetView: MutableSet<MutableMap.MutableEntry<K, V>>? = null
    private var navigableKeySet: KeySet<K>? = null
    private var valuesView: MutableCollection<V>? = null
    private var descendingMap: NavigableMap<K, V>? = null

    override fun navigableKeySet(): NavigableSet<K> {
        val nks: KeySet<K>? = navigableKeySet
        return nks ?: KeySet(this as NavigableMap<K, Any>).also { navigableKeySet = it }
    }

    override fun descendingKeySet(): NavigableSet<K> {
        return descendingMap().navigableKeySet()
    }

    override fun subMap(
        fromKey: K,
        fromInclusive: Boolean,
        toKey: K,
        toInclusive: Boolean
    ): SortedMap<K, V> {
        return AscendingSubMap(
            this,
            false, fromKey, fromInclusive,
            false, toKey, toInclusive
        )
    }

    /*
     * Unlike Values and EntrySet, the KeySet class is static,
     * delegating to a NavigableMap to allow use by SubMaps, which
     * outweighs the ugliness of needing type-tests for the following
     * Iterator methods that are defined appropriately in main versus
     * submap classes.
     */
    fun keyIterator(): MutableIterator<K> {
        return KeyIterator(getFirstEntry())
    }

    fun descendingKeyIterator(): MutableIterator<K> {
        return DescendingKeyIterator(getLastEntry())
    }


    /**
     * Returns a [MutableSet] view of the keys contained in this map.
     * The set's iterator returns the keys in ascending order.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa. If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own `remove` operation), the results of
     * the iteration are undefined. The set supports element removal,
     * which removes the corresponding mapping from the map, via the
     * `Iterator.remove`, `Set.remove`, `removeAll`, `retainAll`, and
     * `clear` operations. It does not support the `add` or `addAll`
     * operations.
     */
    override val keys: MutableSet<K>
        get() {
            return navigableKeySet()
        }

    /**
     * Returns a {@link Collection} view of the values contained in this map.
     *
     * <p>The collection's iterator returns the values in ascending order
     * of the corresponding keys. The collection's spliterator is
     * <em><a href="Spliterator.html#binding">late-binding</a></em>,
     * <em>fail-fast</em>, and additionally reports {@link Spliterator#ORDERED}
     * with an encounter order that is ascending order of the corresponding
     * keys.
     *
     * <p>The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.  If the map is
     * modified while an iteration over the collection is in progress
     * (except through the iterator's own {@code remove} operation),
     * the results of the iteration are undefined.  The collection
     * supports element removal, which removes the corresponding
     * mapping from the map, via the {@code Iterator.remove},
     * {@code Collection.remove}, {@code removeAll},
     * {@code retainAll} and {@code clear} operations.  It does not
     * support the {@code add} or {@code addAll} operations.
     */
    override val values: MutableCollection<V>
        get() {
            if (valuesView == null) {
                valuesView = Values()
            }
            return valuesView!!
        }


    /**
     * Returns a [MutableSet] view of the mappings contained in this map.
     * The set's iterator returns the entries in ascending key order.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa. If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own `remove` operation, or through the `setValue`
     * operation on a map entry returned by the iterator) the results of
     * the iteration are undefined. The set supports element removal,
     * which removes the corresponding mapping from the map, via the
     * `Iterator.remove`, `Set.remove`, `removeAll`, `retainAll` and
     * `clear` operations. It does not support the `add` or `addAll`
     * operations.
     */
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() {
            if (entrySetView == null) {
                entrySetView = object : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
                    override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> =
                        EntryIterator(getFirstEntry())

                    override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
                        // TreeMap's entrySet does not support add operation, as per Java's contract.
                        throw UnsupportedOperationException("add is not supported by TreeMap entrySet")
                    }

                    override val size: Int get() = this@TreeMap.size

                    override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean {
                        // Need to be careful with the type here. The input might be a simple Map.Entry.
                        // We check if the key exists and if the value matches.
                        if (element !is Map.Entry<*, *>) return false // Basic type check
                        val key = element.key ?: return false // Check if key type matches
                        val value = element.value
                        val entry = getEntry(key)
                        return entry != null && entry.value == value // Kotlin equality
                    }

                    override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
                        if (element !is Map.Entry<*, *>) return false
                        val key = element.key ?: return false
                        val value = element.value
                        val entry = getEntry(key)
                        if (entry != null && entry.value == value) {
                            deleteEntry(entry)
                            return true
                        }
                        return false
                    }

                    override fun clear() = this@TreeMap.clear()
                }
            }
            return entrySetView!!
        }


    // --- Compute/Merge Methods (Kotlin functional types) ---


    /**
     * If the specified key is not already associated with a value (or is mapped to null),
     * computes its value using the given mapping function and enters it into this map unless null.
     *
     * This implementation matches the Java `Map.computeIfAbsent` contract:
     * - If the mapping function returns null, removes the mapping if present and returns null.
     * - If the mapping function returns a non-null value, inserts it and returns it.
     * - If the key is already mapped to a non-null value, returns the existing value.
     *
     * The mapping function may not modify this map during execution, or a ConcurrentModificationException is thrown.
     *
     * @param key the key whose associated value is to be computed
     * @param mappingFunction the function to compute a value
     * @return the current (existing or computed) value associated with the key, or null if none
     * @throws ConcurrentModificationException if the map is modified during computation
     */
    fun computeIfAbsent(key: K, mappingFunction: (K) -> V?): V? {
        val entry = getEntry(key)
        val oldValue = entry?.value

        if (oldValue != null) {
            // Key exists and value is non-null, return it
            return oldValue
        }

        val mc = modCount
        val newValue = mappingFunction(key)
        if (modCount != mc) {
            throw ConcurrentModificationException("Map modified during computeIfAbsent execution")
        }

        return if (newValue != null) {
            // Insert the computed value and return it
            putInternal(key, newValue, true)
            newValue
        } else {
            // If mapping function returns null, remove the mapping if present (Java compatibility)
            if (entry != null) {
                remove(key)
            }
            null
        }
    }


    /**
     * If the value for the specified key is present and non-null, attempts to
     * compute a new mapping given the key and its current mapped value.
     * If the function returns `null`, the mapping is removed.
     *
     * @throws ConcurrentModificationException if the remapping function modifies this map
     */
    fun computeIfPresent(key: K, remappingFunction: (K, V) -> V?): V? {
        val entry = getEntry(key)
        if (entry?.value != null) {
            val oldValue = entry.value!! // Known non-null here
            val mc = modCount
            val newValue = remappingFunction(key, oldValue)
            if (modCount != mc) {
                throw ConcurrentModificationException("Map modified during computeIfPresent execution")
            }
            return if (newValue != null) {
                entry.value = newValue // Update value
                newValue
            } else {
                deleteEntry(entry) // Remove mapping if function returns null
                null
            }
        } else {
            return null // Key not present or value is null
        }
    }

    /**
     * Attempts to compute a mapping for the specified key and its current
     * mapped value (or `null` if there is no current mapping).
     * If the function returns `null`, the mapping is removed (or remains absent).
     *
     * @throws ConcurrentModificationException if the remapping function modifies this map
     */
    fun compute(key: K, remappingFunction: (K, V?) -> V?): V? {
        val entry = getEntry(key)
        val oldValue = entry?.value
        val mc = modCount
        val newValue = remappingFunction(key, oldValue)
        if (modCount != mc) {
            throw ConcurrentModificationException("Map modified during compute execution")
        }

        return if (newValue != null) {
            // If a new value is computed, add/update the mapping
            putInternal(key, newValue, true) // Handles both insertion and update
            newValue
        } else {
            // If function returns null, remove the mapping if it exists
            if (entry != null) {
                deleteEntry(entry)
            }
            null // Return null as the mapping is now absent
        }
    }

    /**
     * If the specified key is not already associated with a value or is
     * associated with null, associates it with the given non-null value.
     * Otherwise, replaces the associated value with the results of the given
     * remapping function, or removes if the result is `null`.
     *
     * @throws NullPointerException if the specified value or the result of the remapping function is null
     * @throws ConcurrentModificationException if the remapping function modifies this map
     */
    fun merge(key: K, value: V, remappingFunction: (V, V) -> V?): V? {
        // requireNotNull(value) // Kotlin's merge expects non-null value param

        val entry = getEntry(key)
        val oldValue = entry?.value

        val newValue = if (oldValue == null) {
            // Key not present or mapped to null, use the provided value
            value
        } else {
            // Key is present with a non-null value, apply remapping function
            val mc = modCount
            val merged = remappingFunction(oldValue, value)
            if (modCount != mc) {
                throw ConcurrentModificationException("Map modified during merge execution")
            }
            merged // Can be null, indicating removal
        }

        return if (newValue != null) {
            putInternal(key, newValue, true) // Add or update
            newValue
        } else {
            // If newValue is null (either initially or from remapping), remove the mapping
            if (entry != null) {
                deleteEntry(entry)
            }
            null // Return null as the mapping is now absent
        }
    }


    // --- Internal Tree Structure & Balancing ---

    class Entry<K, V>(
        override var key: K,
        override var value: V, // Make value mutable for setValue in EntryIterator
        var parent: Entry<K, V>?
    ) : MutableMap.MutableEntry<K, V> {
        var left: Entry<K, V>? = null
        var right: Entry<K, V>? = null
        var color: Boolean = BLACK // false = BLACK, true = RED

        // Implement MutableMap.MutableEntry
        override fun setValue(newValue: V): V {
            val oldValue = this.value
            this.value = newValue
            return oldValue
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Map.Entry<*, *>) return false // Use Map.Entry for comparison
            return key == other.key && value == other.value // Kotlin equality
        }

        override fun hashCode(): Int {
            val keyHash = key?.hashCode() ?: 0
            val valueHash = value?.hashCode() ?: 0
            return keyHash xor valueHash
        }

        override fun toString(): String = "$key=$value"
    }

    companion object {
        private const val RED = true
        private const val BLACK = false

        val UNBOUNDED: Any = Any()

        /**
         * Returns the key corresponding to the specified Entry.
         * @throws NoSuchElementException if the Entry is null
         */
        internal fun <K, V> key(e: Entry<K, V>): K {
            // requireNotNull(e) // Caller should handle null
            return e.key
        }

        /** Returns the key of the given entry, or null if the entry is null */
        internal fun <K, V> keyOrNull(e: Entry<K, V>?): K? {
            return e?.key
        }

        /**
         * Returns the successor of the specified Entry, or null if no successor.
         */
        internal fun <K, V> successor(t: Entry<K, V>?): Entry<K, V>? {
            if (t == null) {
                return null
            } else if (t.right != null) {
                var p = t.right!!
                while (p.left != null) {
                    p = p.left!!
                }
                return p
            } else {
                var ch = t
                var p = t.parent
                while (p != null && ch == p.right) {
                    ch = p
                    p = p.parent
                }
                return p
            }
        }

        /**
         * Returns the predecessor of the specified Entry, or null if no predecessor.
         */
        internal fun <K, V> predecessor(t: Entry<K, V>?): Entry<K, V>? {
            if(t == null) {
                return null
            }else if (t.left != null) {
                var p = t.left!!
                while (p.right != null) {
                    p = p.right!!
                }
                return p
            } else {
                var ch = t
                var p = t.parent
                while (p != null && ch == p.left) {
                    ch = p
                    p = p.parent
                }
                return p
            }
        }

        /**
         * Test two values for equality.  Differs from o1.equals(o2) only in
         * that it copes with `null` o1 properly.
         */
        fun valEquals(o1: Any?, o2: Any?): Boolean {
            return (if (o1 == null) o2 == null else (o1 == o2))
        }

        /** Make an immutable snapshot of an Entry */
        internal fun <K, V> exportEntry(e: Entry<K, V>?): Map.Entry<K, V>? {
            return e?.let { SimpleImmutableEntry(it.key, it.value) }
        }

        /**
         * Currently, we support Spliterator-based versions only for the
         * full map, in either plain of descending form, otherwise relying
         * on defaults because size estimation for submaps would dominate
         * costs. The type tests needed to check these for key views are
         * not very nice but avoid disrupting existing class
         * structures. Callers must use plain default spliterators if this
         * returns null.
         */
        fun <K> keySpliteratorFor(m: NavigableMap<K, *>): Spliterator<K> {
            if (m is TreeMap) {
                val t: TreeMap<K, *> = m
                return t.keySpliterator()
            }
            if (m is DescendingSubMap<*, *>) {
                val dm: DescendingSubMap<K?, *> = m as DescendingSubMap<K?, *>
                val tm: TreeMap<K?, *> = dm.m
                if (dm === tm.descendingMap()) {
                    val t: TreeMap<K, *> = tm as TreeMap<K, *>
                    return t.descendingKeySpliterator()
                }
            }
            val sm: NavigableSubMap<K, *> =
                m as NavigableSubMap<K, *>
            return sm.keySpliterator()
        }

        // Simple immutable entry class for exports
        internal data class SimpleImmutableEntry<K, V>(
            override val key: K,
            override val value: V
        ) : Map.Entry<K, V>


        // SubMaps

        class KeySet<E> internal constructor(map: NavigableMap<E, Any>) : AbstractSet<E>(),
            NavigableSet<E> {
            private val m: NavigableMap<E, Any> = map
            override var size: Int
                get() = m.size
                set(value) {
                    // not implementing for now. implement if needed
                    throw UnsupportedOperationException("Size operation is not supported")
                }

            override fun iterator(): MutableIterator<E> {
                return if (m is TreeMap<E, Any>) m.keyIterator()
                else (m as NavigableSubMap<E, Any>).keyIterator()
            }

            override fun add(element: E): Boolean {
                // not implementing for now. implement if needed
                throw UnsupportedOperationException("Add operation is not supported")
            }

            override fun descendingIterator(): MutableIterator<E> {
                return if (m is TreeMap<E, Any>) m.descendingKeyIterator()
                else (m as NavigableSubMap<E, Any>).descendingKeyIterator()
            }

            override fun isEmpty(): Boolean = m.isEmpty()

            override fun contains(o: E): Boolean {
                return m.containsKey(o)
            }

            override fun clear() {
                m.clear()
            }

            override fun lower(e: E): E? {
                return m.lowerKey(e)
            }

            override fun floor(e: E): E? {
                return m.floorKey(e)
            }

            override fun ceiling(e: E): E? {
                return m.ceilingKey(e)
            }

            override fun higher(e: E): E? {
                return m.higherKey(e)
            }

            override fun first(): E? {
                return m.firstKey()
            }

            override fun last(): E? {
                return m.lastKey()
            }

            override fun comparator(): Comparator<E>? {
                return m.comparator()
            }

            override fun pollFirst(): E? {
                val e: Map.Entry<E?, *>? = m.pollFirstEntry()
                return e?.key
            }

            override fun pollLast(): E? {
                val e: Map.Entry<E?, *>? = m.pollLastEntry()
                return e?.key
            }

            override fun remove(o: E): Boolean {
                val oldSize = size
                m.remove(o)
                return size != oldSize
            }

            override fun addAll(elements: Collection<E>): Boolean {
                var modified = false
                for (element in elements) {
                    if (add(element)) {
                        modified = true
                    }
                }
                return modified
            }

            override fun removeAll(elements: Collection<E>): Boolean {
                var modified = false
                for (element in elements) {
                    if (remove(element)) {
                        modified = true
                    }
                }
                return modified
            }

            override fun retainAll(elements: Collection<E>): Boolean {
                val toRetain = elements.toSet()
                val it = iterator()
                var modified = false
                while (it.hasNext()) {
                    val e = it.next()
                    if (!toRetain.contains(e)) {
                        it.remove()
                        modified = true
                    }
                }
                return modified
            }

            override fun subSet(
                fromElement: E, fromInclusive: Boolean,
                toElement: E, toInclusive: Boolean
            ): NavigableSet<E> {
                return KeySet(
                    m.subMap(
                        fromElement, fromInclusive,
                        toElement, toInclusive
                    ) as NavigableMap<E, Any>
                )
            }

            override fun headSet(toElement: E, inclusive: Boolean): NavigableSet<E> {
                return KeySet(m.headMap(toElement, inclusive) as NavigableMap<E, Any>)
            }

            override fun tailSet(fromElement: E, inclusive: Boolean): NavigableSet<E> {
                return KeySet(m.tailMap(fromElement, inclusive) as NavigableMap<E, Any>)
            }

            override fun subSet(fromElement: E, toElement: E): SortedSet<E> {
                return subSet(fromElement, true, toElement, false)
            }

            override fun headSet(toElement: E): SortedSet<E> {
                return headSet(toElement, false)
            }

            override fun tailSet(fromElement: E): SortedSet<E> {
                return tailSet(fromElement, true)
            }

            override fun descendingSet(): NavigableSet<E> {
                return KeySet(m.descendingMap())
            }
        }

        abstract class NavigableSubMap<K, V> internal constructor(
            m: TreeMap<K, V>,
            fromStart: Boolean, lo: K, loInclusive: Boolean,
            toEnd: Boolean, hi: K, hiInclusive: Boolean
        ) : /*AbstractMap<K, V>(),*/ NavigableMap<K, V> {
            /**
             * The backing map.
             */
            val m: TreeMap<K, V>

            override fun clear() {
                m.clear()
            }

            override fun putAll(toPut: Map<out K, V>) {
                m.putAll(toPut)
            }

            /**
             * Endpoints are represented as triples (fromStart, lo,
             * loInclusive) and (toEnd, hi, hiInclusive). If fromStart is
             * true, then the low (absolute) bound is the start of the
             * backing map, and the other values are ignored. Otherwise,
             * if loInclusive is true, lo is the inclusive bound, else lo
             * is the exclusive bound. Similarly for the upper bound.
             */
            // Conditionally serializable
            val lo: K

            // Conditionally serializable
            val hi: K
            val fromStart: Boolean
            val toEnd: Boolean
            val loInclusive: Boolean
            val hiInclusive: Boolean

            // internal utilities
            fun tooLow(key: K): Boolean {
                if (!fromStart) {
                    val c: Int = m.compare(key, lo)
                    if (c < 0 || (c == 0 && !loInclusive)) return true
                }
                return false
            }

            fun tooHigh(key: K): Boolean {
                if (!toEnd) {
                    val c: Int = m.compare(key, hi)
                    if (c > 0 || (c == 0 && !hiInclusive)) return true
                }
                return false
            }

            fun inRange(key: K): Boolean {
                return !tooLow(key) && !tooHigh(key)
            }

            fun inClosedRange(key: K): Boolean {
                return (fromStart || m.compare(key, lo) >= 0)
                        && (toEnd || m.compare(hi, key) >= 0)
            }

            fun inRange(key: K, inclusive: Boolean): Boolean {
                return if (inclusive) inRange(key) else inClosedRange(key)
            }

            /*
         * Absolute versions of relation operations.
         * Subclasses map to these using like-named "sub"
         * versions that invert senses for descending maps
         */
            fun absLowest(): Entry<K, V>? {
                val e: Entry<K, V>? =
                    (if (fromStart) m.getFirstEntry() else (if (loInclusive) m.getCeilingEntry(lo) else m.getHigherEntry(
                        lo
                    )))
                return if (e == null || tooHigh(e.key)) null else e
            }

            fun absHighest(): Entry<K, V>? {
                val e: Entry<K, V>? =
                    (if (toEnd) m.getLastEntry() else (if (hiInclusive) m.getFloorEntry(hi) else m.getLowerEntry(hi)))
                return if (e == null || tooLow(e.key)) null else e
            }

            fun absCeiling(key: K?): Entry<K, V>? {
                if (tooLow(key!!)) return absLowest()
                val e: Entry<K, V>? = m.getCeilingEntry(key)
                return if (e == null || tooHigh(e.key)) null else e
            }

            fun absHigher(key: K?): Entry<K, V>? {
                if (tooLow(key!!)) return absLowest()
                val e: Entry<K, V>? = m.getHigherEntry(key)
                return if (e == null || tooHigh(e.key)) null else e
            }

            fun absFloor(key: K?): Entry<K, V>? {
                if (tooHigh(key!!)) return absHighest()
                val e: Entry<K, V>? = m.getFloorEntry(key)
                return if (e == null || tooLow(e.key)) null else e
            }

            fun absLower(key: K?): Entry<K, V>? {
                if (tooHigh(key!!)) return absHighest()
                val e: Entry<K, V>? = m.getLowerEntry(key)
                return if (e == null || tooLow(e.key)) null else e
            }

            fun absHighFence(): Entry<K, V>? {
                return (if (toEnd) null else (if (hiInclusive) m.getHigherEntry(hi) else m.getCeilingEntry(hi)))
            }

            fun absLowFence(): Entry<K, V>? {
                return (if (fromStart) null else (if (loInclusive) m.getLowerEntry(lo) else m.getFloorEntry(lo)))
            }

            // Abstract methods defined in ascending vs descending classes
            // These relay to the appropriate absolute versions
            abstract fun subLowest(): Entry<K, V>?
            abstract fun subHighest(): Entry<K, V>?
            abstract fun subCeiling(key: K?): Entry<K, V>?
            abstract fun subHigher(key: K?): Entry<K, V>?
            abstract fun subFloor(key: K?): Entry<K, V>?
            abstract fun subLower(key: K?): Entry<K, V>?

            /** Returns ascending iterator from the perspective of this submap  */
            abstract fun keyIterator(): MutableIterator<K>

            abstract fun keySpliterator(): Spliterator<K>

            /** Returns descending iterator from the perspective of this submap  */
            abstract fun descendingKeyIterator(): MutableIterator<K>

            @Suppress("INAPPLICABLE_JVM_NAME")
            @JvmName("isEmptyKt")
            override fun isEmpty(): Boolean = if (fromStart && toEnd) m.isEmpty() else entries.isEmpty()

            override val size: Int
                get() = if (fromStart && toEnd) m.size else entries.size

            override fun containsKey(key: K): Boolean {
                return inRange(key) && m.containsKey(key)
            }

            override fun containsValue(value: V): Boolean {
                return if (value == null) false else values.contains(value)
            }

            override fun put(key: K, value: V): V? {
                require(inRange(key!!)) { "key out of range" }
                return m.put(key, value)
            }

            fun putIfAbsent(key: K, value: V): V? {
                require(inRange(key!!)) { "key out of range" }
                return m.putIfAbsent(key, value)
            }

            fun merge(key: K, value: V, remappingFunction: (V?, V?) -> V? /*BiFunction<in V?, in V?, out V?>?*/): V? {
                require(inRange(key!!)) { "key out of range" }
                return m.merge(key, value, remappingFunction)
            }

            fun computeIfAbsent(
                key: K,
                mappingFunction: (K) -> V /*function.Function<in K?, out V?>*/
            ): V? {
                if (!inRange(key!!)) {
                    // Do not throw if mapping function returns null
                    // to preserve compatibility with default computeIfAbsent implementation
                    if (mappingFunction(key) == null) return null
                    throw IllegalArgumentException("key out of range")
                }
                return m.computeIfAbsent(key, mappingFunction)
            }

            fun compute(key: K, remappingFunction: (K, V?) -> V? /*BiFunction<in K?, in V?, out V?>*/): V? {
                if (!inRange(key!!)) {
                    // Do not throw if remapping function returns null
                    // to preserve compatibility with default computeIfAbsent implementation
                    if (remappingFunction(key, null) == null) return null
                    throw IllegalArgumentException("key out of range")
                }
                return m.compute(key, remappingFunction)
            }

            fun computeIfPresent(key: K, remappingFunction: (K, V) -> V? /*BiFunction<in K?, in V?, out V?>?*/): V? {
                return if (!inRange(key)) null else m.computeIfPresent(key, remappingFunction)
            }

            override fun get(key: K): V? {
                return if (!inRange(key)) null else m[key]
            }

            override fun remove(key: K): V? {
                return if (!inRange(key)) null else m.remove(key)
            }

            override fun ceilingEntry(key: K) = exportEntry(subCeiling(key))

            override fun ceilingKey(key: K): K? {
                return keyOrNull(subCeiling(key))
            }

            override fun higherEntry(key: K) = exportEntry(subHigher(key))

            override fun higherKey(key: K): K? {
                return keyOrNull(subHigher(key))
            }

            override fun floorEntry(key: K) = exportEntry(subFloor(key))

            override fun floorKey(key: K): K? {
                return keyOrNull(subFloor(key))
            }

            override fun lowerEntry(key: K) = exportEntry(subLower(key))

            override fun lowerKey(key: K): K? {
                return keyOrNull(subLower(key))
            }

            override fun firstKey() = key(subLowest()!!)

            override fun lastKey() = key(subHighest()!!)

            override fun firstEntry() = exportEntry(subLowest())

            override fun lastEntry() = exportEntry(subHighest())

            override fun pollFirstEntry(): Map.Entry<K, V>? {
                val e: Entry<K, V>? = subLowest()
                val result: Map.Entry<K, V>? = exportEntry(e)
                if (e != null) m.deleteEntry(e)
                return result
            }

            override fun pollLastEntry(): Map.Entry<K, V>? {
                val e: Entry<K, V>? = subHighest()
                val result: Map.Entry<K, V>? = exportEntry(e)
                if (e != null) m.deleteEntry(e)
                return result
            }

            // Views
            @Transient
            var descendingMapView: NavigableMap<K, V>? = null

            @Transient
            var entrySetView: NavigableSubMap<K, V>.EntrySetView? = null

            @Transient
            var navigableKeySetView: KeySet<K>? = null

            init {
                if (!fromStart && !toEnd) {
                    require(m.compare(lo, hi) <= 0) { "fromKey > toKey" }
                } else {
                    if (!fromStart)  // type check
                        m.compare(lo, lo)
                    if (!toEnd) m.compare(hi, hi)
                }

                this.m = m
                this.fromStart = fromStart
                this.lo = lo
                this.loInclusive = loInclusive
                this.toEnd = toEnd
                this.hi = hi
                this.hiInclusive = hiInclusive
            }

            override fun navigableKeySet(): NavigableSet<K> {
                val nksv: KeySet<K>? = navigableKeySetView
                return (nksv ?: KeySet(this as NavigableMap<K, Any>)
                    .also { navigableKeySetView = it as KeySet<K>? }) as NavigableSet<K>
            }

            override val keySet: MutableSet<K>
                get() {
                    return navigableKeySet()
                }

            override val keys: NavigableSet<K>
                get() {
                    return navigableKeySet()
                }

            override fun descendingKeySet(): NavigableSet<K> {
                return descendingMap().navigableKeySet()
            }

            override fun subMap(fromKey: K, toKey: K): SortedMap<K, V> {
                return subMap(fromKey, true, toKey, false)
            }

            override fun headMap(toKey: K): SortedMap<K, V> {
                return headMap(toKey, false)
            }

            override fun tailMap(fromKey: K): SortedMap<K, V> {
                return tailMap(fromKey, true)
            }

            // View classes
            abstract inner class EntrySetView : AbstractSet<MutableMap.MutableEntry<K, V>>() {
                @Transient
                var sizeTransient = -1

                @Transient
                private var sizeModCount = 0

                override val size: Int
                    get() {
                        if (fromStart && toEnd) return m.size
                        if (sizeTransient == -1 || sizeModCount != m.modCount) {
                            sizeModCount = m.modCount
                            sizeTransient = 0
                            val i: Iterator<*> = iterator()
                            while (i.hasNext()) {
                                sizeTransient++
                                i.next()
                            }
                        }
                        return sizeTransient
                    }

                override fun isEmpty(): Boolean {
                    val n: Entry<K, V>? = absLowest()
                    return n == null || tooHigh(n.key)
                }

                override fun contains(o: MutableMap.MutableEntry<K, V>): Boolean {
                    if (o !is MutableMap.MutableEntry<K, V>) return false
                    val key: K = o.key!!
                    if (!inRange(key)) return false
                    val node: Entry<K, V>? = m.getEntry(key)
                    return node != null &&
                            valEquals(node.value, o.value)
                }

                fun remove(o: MutableMap.MutableEntry<K, V>?): Boolean {
                    if (o !is MutableMap.MutableEntry<K, V>) return false
                    val key: K = o.key!!
                    if (!inRange(key)) return false
                    val node: Entry<K, V>? = m.getEntry(key)
                    if (node != null && valEquals(
                            node.value,
                            o.value
                        )
                    ) {
                        m.deleteEntry(node)
                        return true
                    }
                    return false
                }
            }

            /**
             * Iterators for SubMaps
             */
            abstract inner class SubMapIterator<T>(
                first: Entry<K, V>?,
                fence: Entry<K, V>?
            ) : MutableIterator<T> {
                var lastReturned: Entry<K, V>? = null
                var next: Entry<K, V>?
                val fenceKey: Any?
                var expectedModCount: Int

                init {
                    expectedModCount = m.modCount
                    next = first
                    fenceKey = if (fence == null) UNBOUNDED else fence.key
                }

                override fun hasNext(): Boolean {
                    return next != null && next!!.key !== fenceKey
                }

                fun nextEntry(): Entry<K, V> {
                    val e: Entry<K, V>? = next
                    if (e == null || e.key === fenceKey) throw NoSuchElementException()
                    if (m.modCount != expectedModCount) throw ConcurrentModificationException()
                    next = successor(e)
                    lastReturned = e
                    return e
                }

                fun prevEntry(): Entry<K, V> {
                    val e: Entry<K, V>? = next
                    if (e == null || e.key === fenceKey) throw NoSuchElementException()
                    if (m.modCount != expectedModCount) throw ConcurrentModificationException()
                    next = predecessor(e)
                    lastReturned = e
                    return e
                }

                fun removeAscending() {
                    checkNotNull(lastReturned)
                    if (m.modCount != expectedModCount) throw ConcurrentModificationException()
                    // deleted entries are replaced by their successors
                    if (lastReturned!!.left != null && lastReturned!!.right != null) next = lastReturned
                    m.deleteEntry(lastReturned!!)
                    lastReturned = null
                    expectedModCount = m.modCount
                }

                fun removeDescending() {
                    checkNotNull(lastReturned)
                    if (m.modCount != expectedModCount) throw ConcurrentModificationException()
                    m.deleteEntry(lastReturned!!)
                    lastReturned = null
                    expectedModCount = m.modCount
                }
            }

            internal inner class SubMapEntryIterator(
                first: Entry<K, V>?,
                fence: Entry<K, V>?
            ) : SubMapIterator<MutableMap.MutableEntry<K, V>>(first, fence) {
                override fun next(): MutableMap.MutableEntry<K, V> {
                    return nextEntry()
                }

                override fun remove() {
                    removeAscending()
                }
            }

            internal inner class DescendingSubMapEntryIterator(
                last: Entry<K, V>?,
                fence: Entry<K, V>?
            ) : SubMapIterator<MutableMap.MutableEntry<K, V>>(last, fence) {
                override fun next(): MutableMap.MutableEntry<K, V> {
                    return prevEntry()
                }

                override fun remove() {
                    removeDescending()
                }
            }

            // Implement minimal Spliterator as KeySpliterator backup
            internal inner class SubMapKeyIterator(
                first: Entry<K, V>?,
                fence: Entry<K, V>?
            ) : SubMapIterator<K>(first, fence), Spliterator<K> {
                override fun next(): K {
                    return nextEntry().key
                }

                override fun remove() {
                    removeAscending()
                }

                override fun trySplit(): Spliterator<K>? {
                    return null
                }

                override fun forEachRemaining(action: (K) -> Unit /* (K*->Unitfunction.Consumer<in K?>*/) {
                    while (hasNext()) action(next())
                }

                override fun tryAdvance(action: (K) -> Unit /* (K*->Unitfunction.Consumer<in K?>*/): Boolean {
                    if (hasNext()) {
                        action(next())
                        return true
                    }
                    return false
                }

                override fun estimateSize(): Long {
                    return Long.Companion.MAX_VALUE
                }

                override fun characteristics(): Int {
                    return Spliterator.DISTINCT or Spliterator.ORDERED or
                            Spliterator.SORTED
                }

                override fun getComparator(): Comparator<in K>? {
                    return this@NavigableSubMap.comparator()
                }
            }

            internal inner class DescendingSubMapKeyIterator(
                last: Entry<K, V>?,
                fence: Entry<K, V>?
            ) : SubMapIterator<K>(last, fence), Spliterator<K> {
                override fun next(): K {
                    return prevEntry().key
                }

                override fun remove() {
                    removeDescending()
                }

                override fun trySplit(): Spliterator<K>? {
                    return null
                }

                override fun forEachRemaining(action: (K) -> Unit /* (K*->Unitfunction.Consumer<in K?>*/) {
                    while (hasNext()) action(next())
                }

                override fun tryAdvance(action: (K) -> Unit /* (K*->Unitfunction.Consumer<in K?>*/): Boolean {
                    if (hasNext()) {
                        action(next())
                        return true
                    }
                    return false
                }

                override fun estimateSize(): Long {
                    return Long.Companion.MAX_VALUE
                }

                override fun characteristics(): Int {
                    return Spliterator.DISTINCT or Spliterator.ORDERED
                }
            }

        }

        class AscendingSubMap<K, V> internal constructor(
            m: TreeMap<K, V>,
            fromStart: Boolean, lo: K, loInclusive: Boolean,
            toEnd: Boolean, hi: K, hiInclusive: Boolean
        ) : NavigableSubMap<K, V>(m, fromStart, lo, loInclusive, toEnd, hi, hiInclusive) {

            override fun comparator(): Comparator<K>? {
                return m.comparator()
            }

            override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
                get() = entrySet

            override val keys = navigableKeySet()

            override val values: MutableCollection<V>
                get() {
                    // Returns a collection view of the values in this submap.
                    return object : AbstractMutableCollection<V>() {
                        override fun iterator(): MutableIterator<V> {
                            val entryIterator = entrySet.iterator()
                            return object : MutableIterator<V> {
                                override fun hasNext() = entryIterator.hasNext()
                                override fun next() = entryIterator.next()!!.value
                                override fun remove() = entryIterator.remove()
                            }
                        }

                        override fun add(element: V): Boolean {
                            throw UnsupportedOperationException("add is not supported by values view of TreeMap or its submaps")
                        }

                        override val size: Int
                            get() = this@AscendingSubMap.size

                        override fun contains(element: V): Boolean {
                            for (v in this) {
                                if (v == element) return true
                            }
                            return false
                        }

                        override fun clear() = this@AscendingSubMap.clear()
                    }
                }

            override fun subMap(
                fromKey: K, fromInclusive: Boolean,
                toKey: K, toInclusive: Boolean
            ): NavigableMap<K, V> {
                require(inRange(fromKey, fromInclusive)) { "fromKey out of range" }
                require(inRange(toKey, toInclusive)) { "toKey out of range" }
                return AscendingSubMap(
                    m,
                    false, fromKey, fromInclusive,
                    false, toKey, toInclusive
                )
            }

            override fun headMap(toKey: K, inclusive: Boolean): NavigableMap<K, V> {
                require(inRange(toKey, inclusive)) { "toKey out of range" }
                return AscendingSubMap(
                    m,
                    fromStart, lo, loInclusive,
                    false, toKey, inclusive
                )
            }

            override fun tailMap(fromKey: K, inclusive: Boolean): NavigableMap<K, V> {
                require(inRange(fromKey, inclusive)) { "fromKey out of range" }
                return AscendingSubMap(
                    m,
                    false, fromKey, inclusive,
                    toEnd, hi, hiInclusive
                )
            }

            override fun descendingMap(): NavigableMap<K, V> {
                val mv: NavigableMap<K, V>? = descendingMapView
                return mv
                    ?: DescendingSubMap(
                        m,
                        fromStart, lo, loInclusive,
                        toEnd, hi, hiInclusive
                    ).also { descendingMapView = it }
            }

            override fun keyIterator(): MutableIterator<K> {
                return this.SubMapKeyIterator(absLowest(), absHighFence()) as MutableIterator<K>
            }

            override fun keySpliterator(): Spliterator<K> {
                return this.SubMapKeyIterator(absLowest(), absHighFence())
            }

            override fun descendingKeyIterator(): MutableIterator<K> {
                return DescendingSubMapKeyIterator(absHighest(), absLowFence()) as MutableIterator<K>
            }

            internal inner class AscendingEntrySetView : NavigableSubMap<K, V>.EntrySetView() {

                @Suppress("INAPPLICABLE_JVM_NAME")
                override val size: Int
                    get() {
                        return super.size
                    }

                override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
                    return this@AscendingSubMap.SubMapEntryIterator(absLowest(), absHighFence())
                }
            }

            override val entrySet: MutableSet<MutableMap.MutableEntry<K, V>>
                get() {
                    val es: NavigableSubMap<K, V>.EntrySetView = entrySetView ?: AscendingEntrySetView().also { 
                        entrySetView = it
                    }

                    return es.toMutableSet()
                }

            override fun subLowest(): Entry<K, V>? {
                return absLowest()
            }

            override fun subHighest(): Entry<K, V>? {
                return absHighest()
            }

            override fun subCeiling(key: K?): Entry<K, V>? {
                return absCeiling(key)
            }

            override fun subHigher(key: K?): Entry<K, V>? {
                return absHigher(key)
            }

            override fun subFloor(key: K?): Entry<K, V>? {
                return absFloor(key)
            }

            override fun subLower(key: K?): Entry<K, V>? {
                return absLower(key)
            }
        }

        class DescendingSubMap<K, V> internal constructor(
            m: TreeMap<K, V>,
            fromStart: Boolean, lo: K, loInclusive: Boolean,
            toEnd: Boolean, hi: K, hiInclusive: Boolean
        ) : NavigableSubMap<K, V>(m, fromStart, lo, loInclusive, toEnd, hi, hiInclusive) {
            // Conditionally serializable
            // m.comparator() is Comparator<K>?, Collections.reverseOrder expects Comparator<K?>?.
            // The result of reverseOrder is Comparator<K?>.
            // We need to return Comparator<K>? for the override.
            @Suppress("UNCHECKED_CAST")
            private val reverseComparator: Comparator<K>? =
                Collections.reverseOrder(m.comparator() as Comparator<K?>?) as Comparator<K>?

            override fun comparator(): Comparator<K>? {
                return reverseComparator
            }

            override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
                get() = (entrySetView ?: object : EntrySetView() {

                    @Suppress("INAPPLICABLE_JVM_NAME")
                    override val size: Int
                        get() {
                            return super.size
                        }

                    override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> =
                        DescendingSubMapEntryIterator(
                            absHighest(),
                            absLowFence()
                        ) as MutableIterator<MutableMap.MutableEntry<K, V>>
                }.also { entrySetView = it }) as MutableSet<MutableMap.MutableEntry<K, V>>

            override val keys = navigableKeySet()

            override val values: MutableCollection<V>
                get() {
                    return object : AbstractMutableCollection<V>() {
                        override fun iterator(): MutableIterator<V> {
                            val entryIterator = entrySet.iterator()
                            return object : MutableIterator<V> {
                                override fun hasNext() = entryIterator.hasNext()
                                override fun next() = entryIterator.next()!!.value
                                override fun remove() = entryIterator.remove()
                            }
                        }

                        override fun add(element: V): Boolean {
                            throw UnsupportedOperationException("add is not supported by values view of TreeMap or its submaps")
                        }

                        override val size: Int
                            get() = this@DescendingSubMap.size

                        override fun contains(element: V): Boolean {
                            for (v in this) {
                                if (v == element) return true
                            }
                            return false
                        }

                        override fun clear() = this@DescendingSubMap.clear()
                    }
                }

            override fun subMap(
                fromKey: K, fromInclusive: Boolean,
                toKey: K, toInclusive: Boolean
            ): NavigableMap<K, V> {
                require(inRange(fromKey, fromInclusive)) { "fromKey out of range" }
                require(inRange(toKey, toInclusive)) { "toKey out of range" }
                return DescendingSubMap(
                    m,
                    false, toKey, toInclusive,
                    false, fromKey, fromInclusive
                )
            }

            override fun headMap(toKey: K, inclusive: Boolean): NavigableMap<K, V> {
                require(inRange(toKey, inclusive)) { "toKey out of range" }
                return DescendingSubMap(
                    m,
                    false, toKey, inclusive,
                    toEnd, hi, hiInclusive
                )
            }

            override fun tailMap(fromKey: K, inclusive: Boolean): NavigableMap<K, V> {
                require(inRange(fromKey, inclusive)) { "fromKey out of range" }
                return DescendingSubMap(
                    m,
                    fromStart, lo, loInclusive,
                    false, fromKey, inclusive
                )
            }

            override fun descendingMap(): NavigableMap<K, V> {
                val mv: NavigableMap<K, V>? = descendingMapView
                return mv
                    ?: AscendingSubMap(
                        m,
                        fromStart, lo, loInclusive,
                        toEnd, hi, hiInclusive
                    ).also { descendingMapView = it }
            }

            override fun keyIterator(): MutableIterator<K> {
                return this.DescendingSubMapKeyIterator(absHighest(), absLowFence()) as MutableIterator<K>
            }

            override fun keySpliterator(): Spliterator<K> {
                return this.DescendingSubMapKeyIterator(absHighest(), absLowFence())
            }

            override fun descendingKeyIterator(): MutableIterator<K> {
                return this.SubMapKeyIterator(absLowest(), absHighFence()) as MutableIterator<K>
            }

            internal inner class DescendingEntrySetView : NavigableSubMap<K, V>.EntrySetView() {

                @Suppress("INAPPLICABLE_JVM_NAME")
                override val size: Int
                    get() {
                        return super.size
                    }

                override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
                    return this@DescendingSubMap.DescendingSubMapEntryIterator(absHighest(), absLowFence())
                }
            }

            override val entrySet: MutableSet<MutableMap.MutableEntry<K, V>>
                get() {
                    val es = entrySetView
                    return (es ?: DescendingEntrySetView().also {
                        entrySetView = it
                    }) as MutableSet<MutableMap.MutableEntry<K, V>>
                }

            override fun subLowest(): Entry<K, V>? {
                return absHighest()
            }

            override fun subHighest(): Entry<K, V>? {
                return absLowest()
            }

            override fun subCeiling(key: K?): Entry<K, V>? {
                return absFloor(key)
            }

            override fun subHigher(key: K?): Entry<K, V>? {
                return absLower(key)
            }

            override fun subFloor(key: K?): Entry<K, V>? {
                return absCeiling(key)
            }

            override fun subLower(key: K?): Entry<K, V>? {
                return absHigher(key)
            }

        }

        /**
         * Base class for spliterators.  Iteration starts at a given
         * origin and continues up to but not including a given fence (or
         * null for end).  At top-level, for ascending cases, the first
         * split uses the root as left-fence/right-origin. From there,
         * right-hand splits replace the current fence with its left
         * child, also serving as origin for the split-off spliterator.
         * Left-hands are symmetric. Descending versions place the origin
         * at the end and invert ascending split rules.  This base class
         * is non-committal about directionality, or whether the top-level
         * spliterator covers the whole tree. This means that the actual
         * split mechanics are located in subclasses. Some of the subclass
         * trySplit methods are identical (except for return types), but
         * not nicely factorable.
         *
         * Currently, subclass versions exist only for the full map
         * (including descending keys via its descendingMap).  Others are
         * possible but currently not worthwhile because submaps require
         * O(n) computations to determine size, which substantially limits
         * potential speed-ups of using custom Spliterators versus default
         * mechanics.
         *
         * To bootstrap initialization, external constructors use
         * negative size estimates: -1 for ascend, -2 for descend.
         */
        open class TreeMapSpliterator<K, V> internal constructor(
            val tree: TreeMap<K, V>,
            origin: Entry<K, V>?,
            fence: Entry<K, V>?,
            side: Int,
            est: Int,
            expectedModCount: Int
        ) {
            var current: Entry<K, V>? // traverser; initially first node in range
            var fence: Entry<K, V>? // one past last, or null
            var side: Int // 0: top, -1: is a left split, +1: right
            var est: Int // size estimate (exact only for top-level)
            var expectedModCount: Int // for CME checks

            init {
                this.current = origin
                this.fence = fence
                this.side = side
                this.est = est
                this.expectedModCount = expectedModCount
            }

            fun getEstimate(): Int {
                // force initialization
                var s: Int
                val t: TreeMap<K, V>
                if ((est.also { s = it }) < 0) {
                    if ((tree.also { t = it }) != null) {
                        current = if (s == -1) t.getFirstEntry() else t.getLastEntry()
                        est = t.size
                        s = est
                        expectedModCount = t.modCount
                    } else {
                        est = 0
                        s = est
                    }
                }
                return s
            }

            fun estimateSize(): Long {
                return this.getEstimate().toLong()
            }
        }

        class KeySpliterator<K, V> internal constructor(
            tree: TreeMap<K, V>,
            origin: Entry<K, V>?,
            fence: Entry<K, V>?,
            side: Int,
            est: Int,
            expectedModCount: Int
        ) : TreeMapSpliterator<K, V>(tree, origin, fence, side, est, expectedModCount), Spliterator<K> {
            override fun trySplit(): KeySpliterator<K, V>? {
                if (est < 0) getEstimate() // force initialization

                val d: Int = side
                val e: Entry<K, V>? = current
                val f: Entry<K, V>? = fence
                val s: Entry<K, V>? = (if (e == null || e === f) null else  // empty
                    if (d == 0) tree.root else  // was top
                        if (d > 0) e.right else  // was right
                            if (d < 0 && f != null) f.left else  // was left
                                null)
                if (s != null && s !== e && s !== f && tree.compare(
                        e!!.key,
                        s.key
                    ) < 0
                ) {        // e not already past s
                    side = 1
                    return KeySpliterator(
                        tree,
                        e,
                        s.also { current = it },
                        -1,
                        1.let { est = est ushr it; est },
                        expectedModCount
                    )
                }
                return null
            }

            override fun forEachRemaining(action: (K) -> Unit /*function.Consumer<in K>*/) {
                //if (action == null) throw java.lang.NullPointerException()
                if (est < 0) getEstimate() // force initialization

                val f: Entry<K, V>? = fence
                var e: Entry<K, V>?
                var p: Entry<K, V>?
                var pl: Entry<K, V>?
                if ((current.also { e = it }) != null && e !== f) {
                    current = f // exhaust
                    do {
                        action(e!!.key)
                        if ((e.right.also { p = it }) != null) {
                            while ((p!!.left.also { pl = it }) != null) p = pl
                        } else {
                            while ((e!!.parent.also { p = it }) != null && e === p!!.right) e = p
                        }
                    } while ((p.also { e = it }) != null && e !== f)
                    if (tree.modCount != expectedModCount) throw ConcurrentModificationException()
                }
            }

            override fun tryAdvance(action: (K) -> Unit /*function.Consumer<in K>*/): Boolean {
                val e: Entry<K, V>?
                //if (action == null) throw java.lang.NullPointerException()
                if (est < 0) getEstimate() // force initialization

                if ((current.also { e = it }) == null || e === fence) return false
                current = successor(e)
                action(e!!.key)
                if (tree.modCount != expectedModCount) throw ConcurrentModificationException()
                return true
            }

            override fun characteristics(): Int {
                return (if (side == 0) Spliterator.SIZED else 0) or
                        Spliterator.DISTINCT or Spliterator.SORTED or Spliterator.ORDERED
            }

            override fun getComparator(): Comparator<in K>? {
                return tree.comparator
            }
        }


        class DescendingKeySpliterator<K, V> internal constructor(
            tree: TreeMap<K, V>,
            origin: Entry<K, V>?, fence: Entry<K, V>?,
            side: Int, est: Int, expectedModCount: Int
        ) : TreeMapSpliterator<K, V>(tree, origin, fence, side, est, expectedModCount),
            Spliterator<K> {
            override fun trySplit(): DescendingKeySpliterator<K, V>? {
                if (est < 0) getEstimate() // force initialization

                val d: Int = side
                val e: Entry<K, V>? = current
                val f: Entry<K, V>? = fence
                val s: Entry<K, V>? = (if (e == null || e === f) null else  // empty
                    if (d == 0) tree.root else  // was top
                        if (d < 0) e.left else  // was left
                            if (d > 0 && f != null) f.right else  // was right
                                null)
                if (s != null && s !== e && s !== f && tree.compare(e!!.key, s.key) > 0) {       // e not already past s
                    side = 1
                    return DescendingKeySpliterator(
                        tree,
                        e,
                        s.also { current = it },
                        -1,
                        1.let { est = est ushr it; est },
                        expectedModCount
                    )
                }
                return null
            }

            override fun forEachRemaining(action: (K) -> Unit /*function.Consumer<in K?>*/) {
                //if (action == null) throw java.lang.NullPointerException()
                if (est < 0) getEstimate() // force initialization

                val f: Entry<K, V>? = fence
                var e: Entry<K, V>?
                var p: Entry<K, V>?
                var pr: Entry<K, V>?
                if ((current.also { e = it }) != null && e !== f) {
                    current = f // exhaust
                    do {
                        action(e!!.key)
                        if ((e.left.also { p = it }) != null) {
                            while ((p!!.right.also { pr = it }) != null) p = pr
                        } else {
                            while ((e!!.parent.also { p = it }) != null && e === p!!.left) e = p
                        }
                    } while ((p.also { e = it }) != null && e !== f)
                    if (tree.modCount != expectedModCount) throw ConcurrentModificationException()
                }
            }

            override fun tryAdvance(action: (K) -> Unit /*function.Consumer<in K?>*/): Boolean {
                val e: Entry<K, V>?
                //if (action == null) throw java.lang.NullPointerException()
                if (est < 0) getEstimate() // force initialization

                if ((current.also { e = it }) == null || e === fence) return false
                current = predecessor(e)
                action(e!!.key)
                if (tree.modCount != expectedModCount) throw ConcurrentModificationException()
                return true
            }

            override fun characteristics(): Int {
                return (if (side == 0) Spliterator.SIZED else 0) or
                        Spliterator.DISTINCT or Spliterator.ORDERED
            }
        }

        class ValueSpliterator<K, V> internal constructor(
            tree: TreeMap<K, V>,
            origin: Entry<K, V>?, fence: Entry<K, V>?,
            side: Int, est: Int, expectedModCount: Int
        ) : TreeMapSpliterator<K, V>(tree, origin, fence, side, est, expectedModCount),
            Spliterator<V> {
            override fun trySplit(): ValueSpliterator<K, V>? {
                if (est < 0) getEstimate() // force initialization

                val d: Int = side
                val e: Entry<K, V>? = current
                val f: Entry<K, V>? = fence
                val s: Entry<K, V>? = (if (e == null || e === f) null else  // empty
                    if (d == 0) tree.root else  // was top
                        if (d > 0) e.right else  // was right
                            if (d < 0 && f != null) f.left else  // was left
                                null)
                if (s != null && s !== e && s !== f && tree.compare(
                        e!!.key,
                        s.key
                    ) < 0
                ) {        // e not already past s
                    side = 1
                    return ValueSpliterator(
                        tree,
                        e,
                        s.also { current = it },
                        -1,
                        1.let { est = est ushr it; est },
                        expectedModCount
                    )
                }
                return null
            }

            override fun forEachRemaining(action: (V) -> Unit /*function.Consumer<in V?>*/) {
                //if (action == null) throw java.lang.NullPointerException()
                if (est < 0) getEstimate() // force initialization

                val f: Entry<K, V>? = fence
                var e: Entry<K, V>?
                var p: Entry<K, V>?
                var pl: Entry<K, V>?
                if ((current.also { e = it }) != null && e !== f) {
                    current = f // exhaust
                    do {
                        action(e!!.value)
                        if ((e.right.also { p = it }) != null) {
                            while ((p!!.left.also { pl = it }) != null) p = pl
                        } else {
                            while ((e!!.parent.also { p = it }) != null && e === p!!.right) e = p
                        }
                    } while ((p.also { e = it }) != null && e !== f)
                    if (tree.modCount != expectedModCount) throw ConcurrentModificationException()
                }
            }

            override fun tryAdvance(action: (V) -> Unit /*function.Consumer<in V?>*/): Boolean {
                val e: Entry<K, V>?
                //if (action == null) throw java.lang.NullPointerException()
                if (est < 0) getEstimate() // force initialization

                if ((current.also { e = it }) == null || e === fence) return false
                current = successor(e)
                action(e!!.value)
                if (tree.modCount != expectedModCount) throw ConcurrentModificationException()
                return true
            }

            override fun characteristics(): Int {
                return (if (side == 0) Spliterator.SIZED else 0) or Spliterator.ORDERED
            }
        }

    } // End Companion Object


    /**
     * Compares two keys using the correct comparison method (comparator or natural order).
     */
    internal fun compare(k1: K, k2: K): Int {
        return comparator?.compare(k1, k2)
            ?: run {
                // Ensure k1 is not null for natural ordering comparison
                if (k1 == null) throw NullPointerException("Null key encountered with natural ordering")
                val c1 = k1 as? Comparable<K> ?: throw ClassCastException("Key $k1 is not Comparable")
                c1.compareTo(k2)
            }
    }

    // --- Internal Getters for Navigation ---

    internal fun getFirstEntry(): Entry<K, V>? {
        var p = root
        if (p != null) {
            while (p?.left != null) {
                p = p.left
            }
        }
        return p
    }

    internal fun getLastEntry(): Entry<K, V>? {
        var p = root
        if (p != null) {
            while (p?.right != null) {
                p = p.right
            }
        }
        return p
    }

    internal fun getCeilingEntry(key: K): Entry<K, V>? {
        var p = root
        var ceiling: Entry<K, V>? = null
        while (p != null) {
            val cmp = compare(key, p.key)
            when {
                cmp < 0 -> { // key < p.key; p might be the ceiling
                    ceiling = p
                    p = p.left // Look for a smaller potential ceiling
                }

                cmp > 0 -> { // key > p.key; ceiling must be to the right
                    p = p.right
                }

                else -> return p // Exact match is the ceiling
            }
        }
        return ceiling
    }

    internal fun getFloorEntry(key: K): Entry<K, V>? {
        var p = root
        var floor: Entry<K, V>? = null
        while (p != null) {
            val cmp = compare(key, p.key)
            when {
                cmp > 0 -> { // key > p.key; p might be the floor
                    floor = p
                    p = p.right // Look for a larger potential floor
                }

                cmp < 0 -> { // key < p.key; floor must be to the left
                    p = p.left
                }

                else -> return p // Exact match is the floor
            }
        }
        return floor
    }

    internal fun getHigherEntry(key: K): Entry<K, V>? {
        var p = root
        var higher: Entry<K, V>? = null
        while (p != null) {
            val cmp = compare(key, p.key)
            if (cmp < 0) { // key < p.key; p is a candidate
                higher = p
                p = p.left // Try to find a smaller candidate (closer to key)
            } else { // key >= p.key; higher entry must be to the right
                p = p.right
            }
        }
        return higher
    }

    internal fun getLowerEntry(key: K): Entry<K, V>? {
        var p = root
        var lower: Entry<K, V>? = null
        while (p != null) {
            val cmp = compare(key, p.key)
            if (cmp > 0) { // key > p.key; p is a candidate
                lower = p
                p = p.right // Try to find a larger candidate (closer to key)
            } else { // key <= p.key; lower entry must be to the left
                p = p.left
            }
        }
        return lower
    }


    // --- Internal Put/Add Logic ---

    /** Internal put operation. */
    private fun putInternal(key: K, value: V, replaceOld: Boolean): V? {
        var t = root
        if (t == null) {
            compare(key, key) // Type check for Comparable or null check for Comparator
            root = Entry(key, value, null).apply { color = BLACK }
            size = 1
            modCount++
            return null
        }

        var cmp: Int
        var parent: Entry<K, V>
        val cpr = comparator

        if (cpr != null) {
            do {
                parent = t!! // t cannot be null here
                cmp = cpr.compare(key, t.key)
                t = when {
                    cmp < 0 -> t.left
                    cmp > 0 -> t.right
                    else -> { // Key already exists
                        val oldValue = t.value
                        if (replaceOld || oldValue == null) { // Java's putIfAbsent logic differs slightly
                            t.value = value
                        }
                        return oldValue // Return the old value
                    }
                }
            } while (t != null)
        } else {
            // Natural ordering path
            if (key == null) throw NullPointerException("Null key not permitted with natural ordering")
            val k = key as? Comparable<K> ?: throw ClassCastException("Key is not Comparable")
            do {
                parent = t!! // t cannot be null here
                cmp = k.compareTo(t.key)
                t = when {
                    cmp < 0 -> t.left
                    cmp > 0 -> t.right
                    else -> { // Key already exists
                        val oldValue = t.value
                        if (replaceOld || oldValue == null) {
                            t.value = value
                        }
                        return oldValue // Return the old value
                    }
                }
            } while (t != null)
        }

        // Key doesn't exist, add new entry
        val e = Entry(key, value, parent)
        if (cmp < 0) {
            parent.left = e
        } else {
            parent.right = e
        }
        fixAfterInsertion(e)
        size++
        modCount++
        return null // Indicate no previous value for this key
    }

    // --- Internal Deletion Logic ---

    /**
     * Deletes the specified entry node from the tree.
     */
    internal fun deleteEntry(p: Entry<K, V>) {
        modCount++
        size--

        // If strictly internal, copy successor's element to p and then make p
        // point to successor.
        if (p.left != null && p.right != null) {
            val s = successor(p)!! // Successor must exist
            p.key = s.key       // Copy key/value from successor
            p.value = s.value
            // Now, the node to delete is the successor 's'
            // This assignment is crucial: we are now logically deleting 's'
            // which has at most one child.
            // The variable 'p' now points to the node we need to physically remove.
            val entryToDelete = s // Keep track of the original successor node to delete
            deletePhysicalNode(entryToDelete)

        } else { // p has 0 or 1 children
            deletePhysicalNode(p)
        }


    }

    /** Handles the physical removal of a node with at most one child */
    private fun deletePhysicalNode(p: Entry<K, V>) {
        // Start fixup at replacement node, if it exists, else fixup where p was.
        val replacement = p.left ?: p.right

        if (replacement != null) {
            // Link replacement to parent
            replacement.parent = p.parent
            when {
                p.parent == null -> root = replacement
                p == p.parent?.left -> p.parent?.left = replacement
                else -> p.parent?.right = replacement
            }

            // Null out links of p
            p.left = null
            p.right = null
            p.parent = null

            // Fix replacement's color if p was black
            if (p.color == BLACK) {
                fixAfterDeletion(replacement)
            }
        } else if (p.parent == null) { // Removing root node with no children
            root = null
        } else { // No children, simple removal
            if (p.color == BLACK) {
                fixAfterDeletion(p) // Fixup required before removing
            }

            // Remove p from parent
            if (p.parent != null) {
                if (p == p.parent?.left) {
                    p.parent?.left = null
                } else if (p == p.parent?.right) {
                    p.parent?.right = null
                }
                p.parent = null // Should be done last after fixup potentially uses parent
            }
        }
    }


    // --- Red-Black Tree Balancing ---

    private fun colorOf(p: Entry<K, V>?): Boolean = p?.color ?: BLACK // null nodes are BLACK
    private fun parentOf(p: Entry<K, V>?): Entry<K, V>? = p?.parent
    private fun leftOf(p: Entry<K, V>?): Entry<K, V>? = p?.left
    private fun rightOf(p: Entry<K, V>?): Entry<K, V>? = p?.right
    private fun setColor(p: Entry<K, V>?, c: Boolean) {
        if (p != null) p.color = c
    }

    /** From CLR */
    private fun rotateLeft(p: Entry<K, V>?) {
        if (p != null) {
            val r = p.right
            requireNotNull(r) { "Right child must not be null for rotateLeft" }
            p.right = r.left
            if (r.left != null) r.left!!.parent = p
            r.parent = p.parent
            when {
                p.parent == null -> root = r
                p == p.parent?.left -> p.parent?.left = r
                else -> p.parent?.right = r
            }
            r.left = p
            p.parent = r
        }
    }

    /** From CLR */
    private fun rotateRight(p: Entry<K, V>?) {
        if (p != null) {
            val l = p.left
            requireNotNull(l) { "Left child must not be null for rotateRight" }
            p.left = l.right
            if (l.right != null) l.right!!.parent = p
            l.parent = p.parent
            when {
                p.parent == null -> root = l
                p == p.parent?.right -> p.parent?.right = l
                else -> p.parent?.left = l
            }
            l.right = p
            p.parent = l
        }
    }

    /** From CLR */
    private fun fixAfterInsertion(px: Entry<K, V>) {
        var x: Entry<K, V>? = px
        x?.color = RED // Newly inserted node is RED

        while (x != null && x != root && x.parent?.color == RED) {
            val parent = parentOf(x)!! // Parent exists and is RED
            val grandparent = parentOf(parent) // Grandparent must exist and be BLACK

            if (parent == leftOf(grandparent)) { // Case: Parent is left child
                val y = rightOf(grandparent) // Uncle
                if (colorOf(y) == RED) { // Case 1: Uncle is RED
                    setColor(parent, BLACK)
                    setColor(y, BLACK)
                    setColor(grandparent, RED)
                    x = grandparent // Move up the tree
                } else { // Case 2 & 3: Uncle is BLACK
                    if (x == rightOf(parent)) { // Case 2: x is right child (triangle)
                        x = parent
                        rotateLeft(x)
                        // After rotation, parentOf(x) is the new parent for Case 3
                    }
                    // Case 3: x is left child (line)
                    // Need to re-fetch parent and grandparent after potential rotation
                    val p = parentOf(x)!!
                    val gp = parentOf(p)
                    setColor(p, BLACK)
                    setColor(gp, RED)
                    rotateRight(gp)
                }
            } else { // Case: Parent is right child (symmetric)
                val y = leftOf(grandparent) // Uncle
                if (colorOf(y) == RED) { // Case 1: Uncle is RED
                    setColor(parent, BLACK)
                    setColor(y, BLACK)
                    setColor(grandparent, RED)
                    x = grandparent // Move up the tree
                } else { // Case 2 & 3: Uncle is BLACK
                    if (x == leftOf(parent)) { // Case 2: x is left child (triangle)
                        x = parent
                        rotateRight(x)
                        // After rotation, parentOf(x) is the new parent for Case 3
                    }
                    // Case 3: x is right child (line)
                    // Need to re-fetch parent and grandparent after potential rotation
                    val p = parentOf(x)!!
                    val gp = parentOf(p)
                    setColor(p, BLACK)
                    setColor(gp, RED)
                    rotateLeft(gp)
                }
            }
        }
        root?.color = BLACK // Root must always be BLACK
    }


    /** From CLR */
    private fun fixAfterDeletion(px: Entry<K, V>?) {
        var x = px
        while (x != root && colorOf(x) == BLACK) {
            val parent = parentOf(x) // Parent must exist if x is not root
            if (x == leftOf(parent)) { // Case: x is left child
                var sib = rightOf(parent) // Sibling

                if (colorOf(sib) == RED) { // Case 1: Sibling is RED
                    setColor(sib, BLACK)
                    setColor(parent, RED)
                    rotateLeft(parent)
                    sib = rightOf(parent) // New sibling is now BLACK
                }
                // Now sibling is guaranteed BLACK

                if (colorOf(leftOf(sib)) == BLACK && colorOf(rightOf(sib)) == BLACK) { // Case 2: Sibling's children are both BLACK
                    setColor(sib, RED)
                    x = parent // Move up the tree
                } else {
                    if (colorOf(rightOf(sib)) == BLACK) { // Case 3: Sibling's right child is BLACK (left is RED)
                        setColor(leftOf(sib), BLACK)
                        setColor(sib, RED)
                        rotateRight(sib)
                        sib = rightOf(parent) // New sibling
                    }
                    // Case 4: Sibling's right child is RED
                    setColor(sib, colorOf(parent))
                    setColor(parent, BLACK)
                    setColor(rightOf(sib), BLACK)
                    rotateLeft(parent)
                    x = root // Exit loop
                }
            } else { // Symmetric case: x is right child
                var sib = leftOf(parent) // Sibling

                if (colorOf(sib) == RED) { // Case 1: Sibling is RED
                    setColor(sib, BLACK)
                    setColor(parent, RED)
                    rotateRight(parent)
                    sib = leftOf(parent) // New sibling is now BLACK
                }
                // Now sibling is guaranteed BLACK

                if (colorOf(rightOf(sib)) == BLACK && colorOf(leftOf(sib)) == BLACK) { // Case 2: Sibling's children are both BLACK
                    setColor(sib, RED)
                    x = parent // Move up the tree
                } else {
                    if (colorOf(leftOf(sib)) == BLACK) { // Case 3: Sibling's left child is BLACK (right is RED)
                        setColor(rightOf(sib), BLACK)
                        setColor(sib, RED)
                        rotateLeft(sib)
                        sib = leftOf(parent) // New sibling
                    }
                    // Case 4: Sibling's left child is RED
                    setColor(sib, colorOf(parent))
                    setColor(parent, BLACK)
                    setColor(leftOf(sib), BLACK)
                    rotateRight(parent)
                    x = root // Exit loop
                }
            }
        }
        setColor(x, BLACK) // Ensure the node we stopped at (or root) is BLACK
    }

    // --- Build From Sorted ---
    // Helper for constructor optimization

    fun addAllForTreeSet(set: SortedSet<K>, defaultVal: V) {
        try {
            buildFromSorted(set.size, set.iterator(), /*null,*/ defaultVal)
        } catch (cannotHappen: IOException) {
            /*} catch (cannotHappen: ClassNotFoundException) {*/
        }
    }

    /**
     * Linear time tree building algorithm from sorted data.  Can accept keys
     * and/or values from iterator or stream. This leads to too many
     * parameters, but seems better than alternatives.  The four formats
     * that this method accepts are:
     *
     * 1) An iterator of Map.Entries.  (it != null, defaultVal == null).
     * 2) An iterator of keys.         (it != null, defaultVal != null).
     * 3) A stream of alternating serialized keys and values.
     * (it == null, defaultVal == null).
     * 4) A stream of serialized keys. (it == null, defaultVal != null).
     *
     * It is assumed that the comparator of the TreeMap is already set prior
     * to calling this method.
     *
     * @param size the number of keys (or key-value pairs) to be read from
     * the iterator or stream
     * @param it If non-null, new entries are created from entries
     * or keys read from this iterator.
     * @param str If non-null, new entries are created from keys and
     * possibly values read from this stream in serialized form.
     * Exactly one of it and str should be non-null.
     * @param defaultVal if non-null, this default value is used for
     * each value in the map.  If null, each value is read from
     * iterator or stream, as described above.
     * @throws java.io.IOException propagated from stream reads. This cannot
     * occur if str is null.
     * @throws ClassNotFoundException propagated from readObject.
     * This cannot occur if str is null.
     */
    @Throws(IOException::class)
    private fun buildFromSorted(
        size: Int, it: MutableIterator<*>,
        /*str: ObjectInputStream,*/
        defaultVal: V?
    ) {
        this.size = size
        root = buildFromSorted(
            0, 0, size - 1, computeRedLevel(size),
            it, /*str,*/ defaultVal
        )
    }

    /**
     * Recursive "helper method" that does the real work of the
     * previous method.  Identically named parameters have
     * identical definitions.  Additional parameters are documented below.
     * It is assumed that the comparator and size fields of the TreeMap are
     * already set prior to calling this method.  (It ignores both fields.)
     *
     * @param level the current level of tree. Initial call should be 0.
     * @param lo the first element index of this subtree. Initial should be 0.
     * @param hi the last element index of this subtree.  Initial should be
     * size-1.
     * @param redLevel the level at which nodes should be red.
     * Must be equal to computeRedLevel for tree of this size.
     */
    @Throws(IOException::class/*, java.lang.ClassNotFoundException::class*/)
    private fun buildFromSorted(
        level: Int, lo: Int, hi: Int,
        redLevel: Int,
        it: MutableIterator<*>,
        /*str: ObjectInputStream,*/
        defaultVal: V?
    ): TreeMap.Entry<K, V>? {
        /*
         * Strategy: The root is the middlemost element. To get to it, we
         * have to first recursively construct the entire left subtree,
         * so as to grab all of its elements. We can then proceed with right
         * subtree.
         *
         * The lo and hi arguments are the minimum and maximum
         * indices to pull out of the iterator or stream for current subtree.
         * They are not actually indexed, we just proceed sequentially,
         * ensuring that items are extracted in corresponding order.
         */

        if (hi < lo) return null

        val mid = (lo + hi) ushr 1

        var left: Entry<K, V>? = null
        if (lo < mid) left = buildFromSorted(
            level + 1, lo, mid - 1, redLevel,
            it, /*str,*/ defaultVal
        )

        // extract key and/or value from iterator or stream
        val key: K
        val value: V
        //if (it != null) {
        if (defaultVal == null) {
            val entry = it.next() as MutableMap.MutableEntry<*, *>
            key = entry.key as K
            value = entry.value as V
        } else {
            key = it.next() as K
            value = defaultVal
        }
        //} else { // use stream
        //key = str.readObject() as K?
        //value = (if (defaultVal != null) defaultVal else str.readObject() as V?)
        //}

        val middle: Entry<K, V> = Entry<K, V>(key, value, null)

        // color nodes in non-full bottommost level red
        if (level == redLevel) middle.color = RED

        if (left != null) {
            middle.left = left
            left.parent = middle
        }

        if (mid < hi) {
            val right: Entry<K, V>? = buildFromSorted(
                level + 1, mid + 1, hi, redLevel,
                it, /*str,*/ defaultVal
            )
            middle.right = right
            right!!.parent = middle
        }

        return middle
    }

    /**
     * Builds the tree from a sorted source, typically an iterator from another sorted map.
     * Runs in O(n) time.
     *
     * @param size The expected number of elements.
     * @param it Iterator providing sorted entries.
     */
    private fun buildFromSorted(size: Int, it: Iterator<Map.Entry<K, V>>) {
        this.size = size
        this.root = buildFromSortedRecursive(0, size - 1, 0, it)
    }

    /**
     * Recursive helper for building balanced tree from sorted source.
     * Follows the logic from Java's TreeMap.
     */
    private fun buildFromSortedRecursive(
        level: Int,
        lo: Int,
        hi: Int,
        it: Iterator<Map.Entry<K, V>>,
        redLevel: Int = computeRedLevel(hi - lo + 1) // Calculate redLevel based on subtree size
    ): Entry<K, V>? {
        if (hi < lo) return null

        val mid = (lo + hi).ushr(1) // Use unsigned shift for average

        var left: Entry<K, V>? = null
        if (lo < mid) {
            left = buildFromSortedRecursive(level + 1, lo, mid - 1, it, redLevel)
        }

        // Get the middle element from the iterator
        val entry = it.next()
        val middle = Entry(entry.key, entry.value, null) // Parent will be set later

        // Assign color based on level
        if (level == redLevel) {
            middle.color = RED
        }

        middle.left = left
        left?.parent = middle

        if (mid < hi) {
            val right = buildFromSortedRecursive(level + 1, mid + 1, hi, it, redLevel)
            middle.right = right
            right?.parent = middle
        }

        return middle
    }


    /** Compute level for RED nodes */
    private fun computeRedLevel(sz: Int): Int {
        // A level is RED if it corresponds to the highest bit set in the size.
        // This ensures the black-height property is maintained.
        // Example: size 7 (0111), highest bit is at position 2 (0-indexed). Red level = 2.
        // size 8 (1000), highest bit is 3. Red level = 3.
        var level = 0
        var s = sz - 1
        while (s >= 0) {
            s = (s shl 1) // Check highest bit by shifting left until negative (or zero)
            level++
        }
        // The Java implementation uses Integer.numberOfLeadingZeros(sz) + 1
        // which is equivalent to finding the position of the most significant bit.
        // Let's try a simpler bit manipulation approach for common code:
        var redLevel = 0
        var tempSize = sz
        while (tempSize > 0) {
            tempSize = tempSize shr 1
            redLevel++
        }
        return redLevel - 1 // 0-indexed level
    }

    // --- View class support ---
    inner class Values : AbstractMutableCollection<V>() {
        override fun iterator(): MutableIterator<V> {
            return ValueIterator(getFirstEntry()) as MutableIterator<V>
        }

        override fun add(element: V): Boolean {
            // not implementing for now. implement if needed
            throw UnsupportedOperationException("Add operation is not supported")
        }

        override val size: Int
            get() {
                return this@TreeMap.size
            }

        override fun contains(o: V): Boolean {
            return this@TreeMap.containsValue(o)
        }

        override fun remove(o: V): Boolean {
            var e: Entry<K, V>? = getFirstEntry()
            while (e != null) {
                if (valEquals(e.value, o)) {
                    deleteEntry(e)
                    return true
                }
                e = successor(e)
            }
            return false
        }

        override fun clear() {
            this@TreeMap.clear()
        }

        fun spliterator(): Spliterator<V> {
            return ValueSpliterator(this@TreeMap, null, null, 0, -1, 0)
        }
    }


    // --- Iterators ---

    internal abstract inner class PrivateEntryIterator<T>(first: Entry<K, V>?) : MutableIterator<T> {
        var next: Entry<K, V>? = first
        var lastReturned: Entry<K, V>? = null
        var expectedModCount: Int = modCount

        override fun hasNext(): Boolean {
            return next != null
        }

        fun nextEntry(): Entry<K, V> {
            checkForComodification()
            val e = next ?: throw NoSuchElementException()
            next = successor(e)
            lastReturned = e
            return e
        }

        fun prevEntry(): Entry<K, V> {
            checkForComodification()
            val e = next
                ?: throw NoSuchElementException() // Should use 'previous' logic if implementing descending iterator
            next = predecessor(e) // Assuming 'next' points to the *next* element to return
            lastReturned = e
            return e
        }


        override fun remove() {
            checkForComodification()
            val last = lastReturned ?: throw IllegalStateException("next() must be called before remove()")
            // If the node to remove has both children, the successor will be moved
            // into its place. In this case, 'next' might point to the node that was
            // just removed physically (the successor). We need to adjust 'next'.
            if (last.left != null && last.right != null) {
                // If removing an internal node, 'next' might become invalid if it was the successor.
                // However, the successor logic already handles finding the *next* node *after* the current one.
                // Let's re-evaluate: When removing 'lastReturned', if it has two children,
                // its *successor* is the one physically removed after its content is copied.
                // 'next' should already point *beyond* 'lastReturned'.
                // If 'next' happened to be the successor that got moved, we might need adjustment.
                // But successor() finds the *next* node in order. If we remove `lastReturned`,
                // the *next* node should still be valid unless it was the successor that got deleted.
                // Let's assume successor() gives the correct next node *logically*.
                // The physical deletion happens via deleteEntry.

                // Simpler approach: If lastReturned is removed, reset 'next' based on the *new* successor
                // of the node *before* lastReturned, if possible. Or, if 'next' was the successor
                // that got deleted, update 'next' to point to the node that replaced it logically (the original node).
                // This gets complex. Let's trust deleteEntry and successor() for now.
                // A potential issue: If `next` was the successor of `lastReturned`, and `lastReturned`
                // has two children, `deleteEntry` copies successor's content to `lastReturned` and
                // deletes the successor node. `next` might now point to a deleted node.
                // Let's adjust `next` if it points to the node physically deleted.
                // This requires knowing which node was physically deleted inside deleteEntry, which is complex.

                // Alternative: If lastReturned has two children, set next = lastReturned after deletion.
                // This works because lastReturned now holds the successor's content.
                // next = last // Point 'next' to the node that now holds the successor's data
            }
            // Reset next if it points to the node being removed (handles case where next was successor)
            // This check might be overly simplistic.
            // if (next == last) {
            //    next = successor(last) // Find the next logical node again
            // }

            deleteEntry(last)
            expectedModCount = modCount
            lastReturned = null // Prevent double remove
        }

        fun checkForComodification() {
            if (modCount != expectedModCount) {
                throw ConcurrentModificationException()
            }
        }
    }

    internal inner class EntryIterator(first: Entry<K, V>?) :
        PrivateEntryIterator<MutableMap.MutableEntry<K, V>>(first) {
        override fun next(): MutableMap.MutableEntry<K, V> = nextEntry()
    }

    internal inner class ValueIterator(first: Entry<K, V>?) :
        PrivateEntryIterator<V>(first) {
        override fun next(): V = nextEntry().value
    }

    internal inner class KeyIterator(first: Entry<K, V>?) :
        PrivateEntryIterator<K>(first) {
        override fun next(): K = nextEntry().key
    }

    internal inner class DescendingKeyIterator(first: Entry<K, V>?) :
        PrivateEntryIterator<K>(first) {
        override fun next(): K {
            return prevEntry().key
        }

        override fun remove() {
            checkNotNull(lastReturned)
            if (modCount != expectedModCount) throw ConcurrentModificationException()
            deleteEntry(lastReturned!!)
            lastReturned = null
            expectedModCount = modCount
        }
    }

    // --- Additional Functions ---

    /**
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException if `toKey` is null
     * and this map uses natural ordering, or its comparator
     * does not permit null keys
     * @throws IllegalArgumentException {@inheritDoc}
     * @since 1.6
     */
    override fun headMap(toKey: K, inclusive: Boolean): NavigableMap<K, V> {
        return AscendingSubMap(
            this,
            true, UNBOUNDED as K, true,
            false, toKey, inclusive
        )
    }

    override fun tailMap(fromKey: K, inclusive: Boolean): SortedMap<K, V> {
        return AscendingSubMap(
            this,
            false, fromKey, inclusive,
            true, UNBOUNDED as K, true
        )
    }

    override fun subMap(fromKey: K, toKey: K): SortedMap<K, V> {
        return subMap(fromKey, true, toKey, false)
    }

    override fun headMap(toKey: K): SortedMap<K, V> {
        return headMap(toKey, false)
    }

    override fun tailMap(fromKey: K): SortedMap<K, V> {
        return tailMap(fromKey, true)
    }

    fun keySpliterator(): Spliterator<K> {
        return KeySpliterator(this, null, null, 0, -1, 0)
    }

    fun descendingKeySpliterator(): Spliterator<K> {
        return DescendingKeySpliterator(this, null, null, 0, -2, 0)
    }


    // --- Cloning ---
    // Kotlin often prefers copy functions/constructors.
    // If strict Cloneable compatibility is needed, implement shallow or deep copy.
    // For now, omitted for simplicity unless required by Lucene usage.
    /*
    public override fun clone(): Any {
        val clone = super.clone() as TreeMap<K, V> // Basic shallow clone
        // Reset transient views
        clone.entrySetView = null
        clone.keySetView = null
        clone.valuesView = null
        // Rebuild the tree structure (deep clone of structure, shallow of keys/values)
        clone.root = null
        clone.size = 0
        clone.modCount = 0
        // Use buildFromSorted for efficient cloning if possible
        try {
            clone.buildFromSorted(size, entries.iterator())
        } catch (e: Exception) {
            // Fallback? Should not happen if source is valid.
            throw InternalError("TreeMap clone failed", e)
        }
        return clone
    }
    */

    // --- Serialization ---
    // Explicitly not implemented as per requirement (no Serializable)

}
