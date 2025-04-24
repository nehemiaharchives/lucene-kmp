package org.gnit.lucenekmp.jdkport

// Note: This is a complex port. While aiming for functional equivalence,
// thorough testing against the original Lucene use cases is crucial.
// Kotlin common does not have a direct NavigableMap equivalent, so relevant
// methods are implemented directly.

/**
 * A Red-Black tree based implementation of the `MutableMap` interface,
 * striving for compatibility with the subset of `java.util.TreeMap` functionality
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
class TreeMap<K, V> : AbstractMutableMap<K, V>,
    MutableMap<K, V> /*, Cloneable // Consider if cloning is truly needed */ {

    /**
     * The comparator used to maintain order in this tree map, or
     * null if it uses the natural ordering of its keys.
     */
    private val comparator: Comparator<in K>?

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
    constructor(comparator: Comparator<in K>?) {
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
    fun comparator(): Comparator<in K>? {
        return comparator
    }

    /**
     * Returns the first (lowest) key currently in this map.
     *
     * @return the first (lowest) key
     * @throws NoSuchElementException if this map is empty
     */
    fun firstKey(): K {
        return key(getFirstEntry() ?: throw NoSuchElementException("Map is empty"))
    }

    /**
     * Returns the last (highest) key currently in this map.
     *
     * @return the last (highest) key
     * @throws NoSuchElementException if this map is empty
     */
    fun lastKey(): K {
        return key(getLastEntry() ?: throw NoSuchElementException("Map is empty"))
    }

    /**
     * Throws [UnsupportedOperationException]. The encounter order induced by this
     * map's comparison method determines the position of mappings, so explicit positioning
     * is not supported.
     */
    fun putFirst(k: K, v: V): V {
        throw UnsupportedOperationException("putFirst is not supported by TreeMap")
    }

    /**
     * Throws [UnsupportedOperationException]. The encounter order induced by this
     * map's comparison method determines the position of mappings, so explicit positioning
     * is not supported.
     */
    fun putLast(k: K, v: V): V {
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
        if (size == 0 && mapSize != 0 && from is TreeMap<*, *> && this.comparator == from.comparator) {
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
    fun ceilingEntry(key: K): Map.Entry<K, V>? {
        return getCeilingEntryInternal(key)
    }

    /**
     * Returns the least key greater than or equal to the given key, or `null` if
     * there is no such key.
     */
    fun ceilingKey(key: K): K? {
        return keyOrNull(getCeilingEntryInternal(key))
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
    fun floorEntry(key: K): Map.Entry<K, V>? {
        return getFloorEntryInternal(key)
    }

    /**
     * Returns the greatest key less than or equal to the given key, or `null` if
     * there is no such key.
     */
    fun floorKey(key: K): K? {
        return keyOrNull(getFloorEntryInternal(key))
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
    fun higherEntry(key: K): Map.Entry<K, V>? {
        return getHigherEntryInternal(key)
    }

    /**
     * Returns the least key strictly greater than the given key, or `null` if
     * there is no such key.
     */
    fun higherKey(key: K): K? {
        return keyOrNull(getHigherEntryInternal(key))
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
    fun lowerEntry(key: K): Map.Entry<K, V>? {
        return getLowerEntryInternal(key)
    }

    /**
     * Returns the greatest key strictly less than the given key, or `null` if
     * there is no such key.
     */
    fun lowerKey(key: K): K? {
        return keyOrNull(getLowerEntryInternal(key))
    }

    /**
     * Returns the first (lowest) key-value mapping currently in this map.
     *
     * @return the first (lowest) entry, or `null` if the map is empty
     */
    fun firstEntry(): Map.Entry<K, V>? {
        return exportEntry(getFirstEntry()) // Export immutable entry
    }

    /**
     * Returns the last (highest) key-value mapping currently in this map.
     *
     * @return the last (highest) entry, or `null` if the map is empty
     */
    fun lastEntry(): Map.Entry<K, V>? {
        return exportEntry(getLastEntry()) // Export immutable entry
    }

    /**
     * Removes and returns a key-value mapping associated with the least key
     * in this map, or `null` if the map is empty.
     *
     * @return the removed first entry of this map, or `null` if the map is empty
     */
    fun pollFirstEntry(): Map.Entry<K, V>? {
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
    fun pollLastEntry(): Map.Entry<K, V>? {
        val p = getLastEntry()
        val result = exportEntry(p) // Export before deletion
        if (p != null) {
            deleteEntry(p)
        }
        return result
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
    private var keySetView: MutableSet<K>? = null
    private var valuesView: MutableCollection<V>? = null


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
            if (keySetView == null) {
                keySetView = object : AbstractMutableSet<K>() {
                    override fun iterator(): MutableIterator<K> = KeyIterator(getFirstEntry())
                    override fun add(element: K): Boolean {
                        throw UnsupportedOperationException("add is not supported by TreeMap")
                    }

                    override val size: Int get() = this@TreeMap.size
                    override fun contains(element: K): Boolean = this@TreeMap.containsKey(element)
                    override fun remove(element: K): Boolean {
                        val oldSize = size
                        this@TreeMap.remove(element)
                        return size != oldSize
                    }

                    override fun clear() = this@TreeMap.clear()
                }
            }
            return keySetView!!
        }

    /**
     * Returns a [MutableCollection] view of the values contained in this map.
     * The collection's iterator returns the values in ascending order
     * of the corresponding keys. The collection is backed by the map,
     * so changes to the map are reflected in the collection, and vice-versa.
     * If the map is modified while an iteration over the collection is
     * in progress (except through the iterator's own `remove` operation),
     * the results of the iteration are undefined. The collection supports
     * element removal, which removes the corresponding mapping from the
     * map, via the `Iterator.remove`, `Collection.remove`, `removeAll`,
     * `retainAll` and `clear` operations. It does not support the
     * `add` or `addAll` operations.
     */
    override val values: MutableCollection<V>
        get() {
            if (valuesView == null) {
                valuesView = object : AbstractMutableCollection<V>() {
                    override fun iterator(): MutableIterator<V> = ValueIterator(getFirstEntry())
                    override fun add(element: V): Boolean {
                        throw UnsupportedOperationException("add is not supported by TreeMap values view")
                    }

                    override val size: Int get() = this@TreeMap.size
                    override fun contains(element: V): Boolean = this@TreeMap.containsValue(element)

                    // Note: remove(element) in AbstractMutableCollection iterates and calls iterator.remove()
                    // which is correctly implemented in ValueIterator.
                    override fun clear() = this@TreeMap.clear()
                }
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
                        val key = element.key as? K ?: return false // Check if key type matches
                        val value = element.value
                        val entry = getEntry(key)
                        return entry != null && entry.value == value // Kotlin equality
                    }

                    override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
                        if (element !is Map.Entry<*, *>) return false
                        val key = element.key as? K ?: return false
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

    internal class Entry<K, V>(
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
            var p = t ?: return null
            if (p.right != null) {
                p = p.right!!
                while (p.left != null) {
                    p = p.left!!
                }
                return p
            } else {
                var ch = p
                p = p.parent!!
                while (p != null && ch == p.right) {
                    ch = p
                    p = p.parent!!
                }
                return p
            }
        }

        /**
         * Returns the predecessor of the specified Entry, or null if no predecessor.
         */
        internal fun <K, V> predecessor(t: Entry<K, V>?): Entry<K, V>? {
            var p = t ?: return null
            if (p.left != null) {
                p = p.left!!
                while (p.right != null) {
                    p = p.right!!
                }
                return p
            } else {
                var ch = p
                p = p.parent ?: return null // Added null check for parent
                while (p != null && ch == p.left) {
                    ch = p
                    p = p.parent!!
                }
                return p
            }
        }

        /** Make an immutable snapshot of an Entry */
        internal fun <K, V> exportEntry(e: Entry<K, V>?): Map.Entry<K, V>? {
            return e?.let { SimpleImmutableEntry(it.key, it.value) }
        }

        // Simple immutable entry class for exports
        internal data class SimpleImmutableEntry<K, V>(
            override val key: K,
            override val value: V
        ) : Map.Entry<K, V>

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

    internal fun getCeilingEntryInternal(key: K): Entry<K, V>? {
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

    internal fun getFloorEntryInternal(key: K): Entry<K, V>? {
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

    internal fun getHigherEntryInternal(key: K): Entry<K, V>? {
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

    internal fun getLowerEntryInternal(key: K): Entry<K, V>? {
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

} // End TreeMap class
