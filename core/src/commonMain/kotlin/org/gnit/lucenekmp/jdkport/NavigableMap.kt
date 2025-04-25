package org.gnit.lucenekmp.jdkport


/**
 * A [SortedMap] extended with navigation methods returning the
 * closest matches for given search targets. Methods
 * [.lowerEntry], [.floorEntry], [.ceilingEntry],
 * and [.higherEntry] return `Map.Entry` objects
 * associated with keys respectively less than, less than or equal,
 * greater than or equal, and greater than a given key, returning
 * `null` if there is no such key.  Similarly, methods
 * [.lowerKey], [.floorKey], [.ceilingKey], and
 * [.higherKey] return only the associated keys. All of these
 * methods are designed for locating, not traversing entries.
 *
 *
 * A `NavigableMap` may be accessed and traversed in either
 * ascending or descending key order.  The [.descendingMap]
 * method returns a view of the map with the senses of all relational
 * and directional methods inverted. The performance of ascending
 * operations and views is likely to be faster than that of descending
 * ones.  Methods
 * [subMap(K, boolean, K, boolean)][.subMap],
 * [headMap(K, boolean)][.headMap], and
 * [tailMap(K, boolean)][.tailMap]
 * differ from the like-named `SortedMap` methods in accepting
 * additional arguments describing whether lower and upper bounds are
 * inclusive versus exclusive.  Submaps of any `NavigableMap`
 * must implement the `NavigableMap` interface.
 *
 *
 * This interface additionally defines methods [.firstEntry],
 * [.pollFirstEntry], [.lastEntry], and
 * [.pollLastEntry] that return and/or remove the least and
 * greatest mappings, if any exist, else returning `null`.
 *
 *
 * The methods
 * [.ceilingEntry],
 * [.firstEntry],
 * [.floorEntry],
 * [.higherEntry],
 * [.lastEntry],
 * [.lowerEntry],
 * [.pollFirstEntry], and
 * [.pollLastEntry]
 * return [Map.Entry] instances that represent snapshots of mappings as
 * of the time of the call. They do *not* support mutation of the
 * underlying map via the optional [setValue][Map.Entry.setValue] method.
 *
 *
 * Methods
 * [subMap(K, K)][.subMap],
 * [headMap(K)][.headMap], and
 * [tailMap(K)][.tailMap]
 * are specified to return `SortedMap` to allow existing
 * implementations of `SortedMap` to be compatibly retrofitted to
 * implement `NavigableMap`, but extensions and implementations
 * of this interface are encouraged to override these methods to return
 * `NavigableMap`.  Similarly,
 * [.keySet] can be overridden to return [NavigableSet].
 *
 *
 * This interface is a member of the
 * [
 * Java Collections Framework]({@docRoot}/java.base/java/util/package-summary.html#CollectionsFramework).
 *
 * @author Doug Lea
 * @author Josh Bloch
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 * @since 1.6
</V></K> */
interface NavigableMap<K, V> : SortedMap<K, V> {
    /**
     * Returns a key-value mapping associated with the greatest key
     * strictly less than the given key, or `null` if there is
     * no such key.
     *
     * @param key the key
     * @return an entry with the greatest key less than `key`,
     * or `null` if there is no such key
     * @throws ClassCastException if the specified key cannot be compared
     * with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     * and this map does not permit null keys
     */
    fun lowerEntry(key: K): Map.Entry<K, V>?

    /**
     * Returns the greatest key strictly less than the given key, or
     * `null` if there is no such key.
     *
     * @param key the key
     * @return the greatest key less than `key`,
     * or `null` if there is no such key
     * @throws ClassCastException if the specified key cannot be compared
     * with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     * and this map does not permit null keys
     */
    fun lowerKey(key: K): K?

    /**
     * Returns a key-value mapping associated with the greatest key
     * less than or equal to the given key, or `null` if there
     * is no such key.
     *
     * @param key the key
     * @return an entry with the greatest key less than or equal to
     * `key`, or `null` if there is no such key
     * @throws ClassCastException if the specified key cannot be compared
     * with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     * and this map does not permit null keys
     */
    fun floorEntry(key: K): Map.Entry<K, V>?

    /**
     * Returns the greatest key less than or equal to the given key,
     * or `null` if there is no such key.
     *
     * @param key the key
     * @return the greatest key less than or equal to `key`,
     * or `null` if there is no such key
     * @throws ClassCastException if the specified key cannot be compared
     * with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     * and this map does not permit null keys
     */
    fun floorKey(key: K): K?

    /**
     * Returns a key-value mapping associated with the least key
     * greater than or equal to the given key, or `null` if
     * there is no such key.
     *
     * @param key the key
     * @return an entry with the least key greater than or equal to
     * `key`, or `null` if there is no such key
     * @throws ClassCastException if the specified key cannot be compared
     * with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     * and this map does not permit null keys
     */
    fun ceilingEntry(key: K): Map.Entry<K, V>?

    /**
     * Returns the least key greater than or equal to the given key,
     * or `null` if there is no such key.
     *
     * @param key the key
     * @return the least key greater than or equal to `key`,
     * or `null` if there is no such key
     * @throws ClassCastException if the specified key cannot be compared
     * with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     * and this map does not permit null keys
     */
    fun ceilingKey(key: K): K?

    /**
     * Returns a key-value mapping associated with the least key
     * strictly greater than the given key, or `null` if there
     * is no such key.
     *
     * @param key the key
     * @return an entry with the least key greater than `key`,
     * or `null` if there is no such key
     * @throws ClassCastException if the specified key cannot be compared
     * with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     * and this map does not permit null keys
     */
    fun higherEntry(key: K): Map.Entry<K, V>?

    /**
     * Returns the least key strictly greater than the given key, or
     * `null` if there is no such key.
     *
     * @param key the key
     * @return the least key greater than `key`,
     * or `null` if there is no such key
     * @throws ClassCastException if the specified key cannot be compared
     * with the keys currently in the map
     * @throws NullPointerException if the specified key is null
     * and this map does not permit null keys
     */
    fun higherKey(key: K): K?

    /**
     * Returns a key-value mapping associated with the least
     * key in this map, or `null` if the map is empty.
     *
     * @return an entry with the least key,
     * or `null` if this map is empty
     */
    override fun firstEntry(): Map.Entry<K, V>?

    /**
     * Returns a key-value mapping associated with the greatest
     * key in this map, or `null` if the map is empty.
     *
     * @return an entry with the greatest key,
     * or `null` if this map is empty
     */
    override fun lastEntry(): Map.Entry<K, V>?

    /**
     * Removes and returns a key-value mapping associated with
     * the least key in this map, or `null` if the map is empty
     * (optional operation).
     *
     * @return the removed first entry of this map,
     * or `null` if this map is empty
     *
     * @throws UnsupportedOperationException if the `pollFirstEntry`
     * operation is not supported by this map
     */
    override fun pollFirstEntry(): Map.Entry<K, V>?

    /**
     * Removes and returns a key-value mapping associated with
     * the greatest key in this map, or `null` if the map is empty
     * (optional operation).
     *
     * @return the removed last entry of this map,
     * or `null` if this map is empty
     *
     * @throws UnsupportedOperationException if the `pollLastEntry`
     * operation is not supported by this map
     */
    override fun pollLastEntry(): Map.Entry<K, V>?

    /**
     * Returns a reverse order view of the mappings contained in this map.
     * The descending map is backed by this map, so changes to the map are
     * reflected in the descending map, and vice-versa.  If either map is
     * modified while an iteration over a collection view of either map
     * is in progress (except through the iterator's own `remove`
     * operation), the results of the iteration are undefined.
     *
     *
     * The returned map has an ordering equivalent to
     * [Collections.reverseOrder]`(comparator())`.
     * The expression `m.descendingMap().descendingMap()` returns a
     * view of `m` essentially equivalent to `m`.
     *
     * @return a reverse order view of this map
     */
    fun descendingMap(): NavigableMap<K, V>

    /**
     * Returns a [NavigableSet] view of the keys contained in this map.
     * The set's iterator returns the keys in ascending order.
     * The set is backed by the map, so changes to the map are reflected in
     * the set, and vice-versa.  If the map is modified while an iteration
     * over the set is in progress (except through the iterator's own `remove` operation), the results of the iteration are undefined.  The
     * set supports element removal, which removes the corresponding mapping
     * from the map, via the `Iterator.remove`, `Set.remove`,
     * `removeAll`, `retainAll`, and `clear` operations.
     * It does not support the `add` or `addAll` operations.
     *
     * @return a navigable set view of the keys in this map
     */
    fun navigableKeySet(): NavigableSet<K>

    /**
     * Returns a reverse order [NavigableSet] view of the keys contained in this map.
     * The set's iterator returns the keys in descending order.
     * The set is backed by the map, so changes to the map are reflected in
     * the set, and vice-versa.  If the map is modified while an iteration
     * over the set is in progress (except through the iterator's own `remove` operation), the results of the iteration are undefined.  The
     * set supports element removal, which removes the corresponding mapping
     * from the map, via the `Iterator.remove`, `Set.remove`,
     * `removeAll`, `retainAll`, and `clear` operations.
     * It does not support the `add` or `addAll` operations.
     *
     * @return a reverse order navigable set view of the keys in this map
     */
    fun descendingKeySet(): NavigableSet<K>

    /**
     * Returns a view of the portion of this map whose keys range from
     * `fromKey` to `toKey`.  If `fromKey` and
     * `toKey` are equal, the returned map is empty unless
     * `fromInclusive` and `toInclusive` are both true.  The
     * returned map is backed by this map, so changes in the returned map are
     * reflected in this map, and vice-versa.  The returned map supports all
     * optional map operations that this map supports.
     *
     *
     * The returned map will throw an `IllegalArgumentException`
     * on an attempt to insert a key outside of its range, or to construct a
     * submap either of whose endpoints lie outside its range.
     *
     * @param fromKey low endpoint of the keys in the returned map
     * @param fromInclusive `true` if the low endpoint
     * is to be included in the returned view
     * @param toKey high endpoint of the keys in the returned map
     * @param toInclusive `true` if the high endpoint
     * is to be included in the returned view
     * @return a view of the portion of this map whose keys range from
     * `fromKey` to `toKey`
     * @throws ClassCastException if `fromKey` and `toKey`
     * cannot be compared to one another using this map's comparator
     * (or, if the map has no comparator, using natural ordering).
     * Implementations may, but are not required to, throw this
     * exception if `fromKey` or `toKey`
     * cannot be compared to keys currently in the map.
     * @throws NullPointerException if `fromKey` or `toKey`
     * is null and this map does not permit null keys
     * @throws IllegalArgumentException if `fromKey` is greater than
     * `toKey`; or if this map itself has a restricted
     * range, and `fromKey` or `toKey` lies
     * outside the bounds of the range
     */
    fun subMap(
        fromKey: K, fromInclusive: Boolean,
        toKey: K, toInclusive: Boolean
    ): SortedMap<K, V>

    /**
     * Returns a view of the portion of this map whose keys are less than (or
     * equal to, if `inclusive` is true) `toKey`.  The returned
     * map is backed by this map, so changes in the returned map are reflected
     * in this map, and vice-versa.  The returned map supports all optional
     * map operations that this map supports.
     *
     *
     * The returned map will throw an `IllegalArgumentException`
     * on an attempt to insert a key outside its range.
     *
     * @param toKey high endpoint of the keys in the returned map
     * @param inclusive `true` if the high endpoint
     * is to be included in the returned view
     * @return a view of the portion of this map whose keys are less than
     * (or equal to, if `inclusive` is true) `toKey`
     * @throws ClassCastException if `toKey` is not compatible
     * with this map's comparator (or, if the map has no comparator,
     * if `toKey` does not implement [Comparable]).
     * Implementations may, but are not required to, throw this
     * exception if `toKey` cannot be compared to keys
     * currently in the map.
     * @throws NullPointerException if `toKey` is null
     * and this map does not permit null keys
     * @throws IllegalArgumentException if this map itself has a
     * restricted range, and `toKey` lies outside the
     * bounds of the range
     */
    fun headMap(toKey: K, inclusive: Boolean): SortedMap<K, V>

    /**
     * Returns a view of the portion of this map whose keys are greater than (or
     * equal to, if `inclusive` is true) `fromKey`.  The returned
     * map is backed by this map, so changes in the returned map are reflected
     * in this map, and vice-versa.  The returned map supports all optional
     * map operations that this map supports.
     *
     *
     * The returned map will throw an `IllegalArgumentException`
     * on an attempt to insert a key outside its range.
     *
     * @param fromKey low endpoint of the keys in the returned map
     * @param inclusive `true` if the low endpoint
     * is to be included in the returned view
     * @return a view of the portion of this map whose keys are greater than
     * (or equal to, if `inclusive` is true) `fromKey`
     * @throws ClassCastException if `fromKey` is not compatible
     * with this map's comparator (or, if the map has no comparator,
     * if `fromKey` does not implement [Comparable]).
     * Implementations may, but are not required to, throw this
     * exception if `fromKey` cannot be compared to keys
     * currently in the map.
     * @throws NullPointerException if `fromKey` is null
     * and this map does not permit null keys
     * @throws IllegalArgumentException if this map itself has a
     * restricted range, and `fromKey` lies outside the
     * bounds of the range
     */
    fun tailMap(fromKey: K, inclusive: Boolean): SortedMap<K, V>

    /**
     * {@inheritDoc}
     *
     *
     * Equivalent to `subMap(fromKey, true, toKey, false)`.
     *
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException     {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     */
    override fun subMap(fromKey: K, toKey: K): SortedMap<K, V>

    /**
     * {@inheritDoc}
     *
     *
     * Equivalent to `headMap(toKey, false)`.
     *
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException     {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     */
    override fun headMap(toKey: K): SortedMap<K, V>

    /**
     * {@inheritDoc}
     *
     *
     * Equivalent to `tailMap(fromKey, true)`.
     *
     * @throws ClassCastException       {@inheritDoc}
     * @throws NullPointerException     {@inheritDoc}
     * @throws IllegalArgumentException {@inheritDoc}
     */
    override fun tailMap(fromKey: K): SortedMap<K, V>

    /**
     * {@inheritDoc}
     *
     *
     * This method is equivalent to [descendingMap][.descendingMap].
     *
     * @implSpec
     * The implementation in this interface returns the result of calling the
     * `descendingMap` method.
     *
     * @return a reverse-ordered view of this map, as a `NavigableMap`
     * @since 21
     */
    override fun reversed(): NavigableMap<K, V> {
        return this.descendingMap()
    }
}
