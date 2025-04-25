package org.gnit.lucenekmp.jdkport


/**
 * A Map that has a well-defined encounter order, that supports operations at both ends, and
 * that is reversible. The [encounter order](SequencedCollection.html#encounter)
 * of a `SequencedMap` is similar to that of the elements of a [SequencedCollection],
 * but the ordering applies to mappings instead of individual elements.
 *
 *
 * The bulk operations on this map, including the [forEach][.forEach] and the
 * [replaceAll][.replaceAll] methods, operate on this map's mappings in
 * encounter order.
 *
 *
 * The view collections provided by the
 * [keySet][.keySet],
 * [values][.values],
 * [entrySet][.entrySet],
 * [sequencedKeySet][.sequencedKeySet],
 * [sequencedValues][.sequencedValues],
 * and
 * [sequencedEntrySet][.sequencedEntrySet] methods all reflect the encounter order
 * of this map. Even though the return values of the `keySet`, `values`, and
 * `entrySet` methods are not sequenced *types*, the elements
 * in those view collections do reflect the encounter order of this map. Thus, the
 * iterators returned by the statements
 * {@snippet :
 * *     var it1 = sequencedMap.entrySet().iterator();
 * *     var it2 = sequencedMap.sequencedEntrySet().iterator();
 * * }
 * both provide the mappings of `sequencedMap` in that map's encounter order.
 *
 *
 * This interface provides methods to add mappings, to retrieve mappings, and to remove
 * mappings at either end of the map's encounter order.
 *
 *
 * This interface also defines the [.reversed] method, which provides a
 * reverse-ordered [view](Collection.html#view) of this map.
 * In the reverse-ordered view, the concepts of first and last are inverted, as
 * are the concepts of successor and predecessor. The first mapping of this map
 * is the last mapping of the reverse-ordered view, and vice-versa. The successor of some
 * mapping in this map is its predecessor in the reversed view, and vice-versa. All
 * methods that respect the encounter order of the map operate as if the encounter order
 * is inverted. For instance, the [forEach][.forEach] method of the reversed view reports
 * the mappings in order from the last mapping of this map to the first. In addition, all of
 * the view collections of the reversed view also reflect the inverse of this map's
 * encounter order. For example,
 * {@snippet :
 * *     var itr = sequencedMap.reversed().entrySet().iterator();
 * * }
 * provides the mappings of this map in the inverse of the encounter order, that is, from
 * the last mapping to the first mapping. The availability of the `reversed` method,
 * and its impact on the ordering semantics of all applicable methods and views, allow convenient
 * iteration, searching, copying, and streaming of this map's mappings in either forward order or
 * reverse order.
 *
 *
 * A map's reverse-ordered view is generally not serializable, even if the original
 * map is serializable.
 *
 *
 * The [Map.Entry] instances obtained by iterating the [.entrySet] view, the
 * [.sequencedEntrySet] view, and its reverse-ordered view, maintain a connection to the
 * underlying map. This connection is guaranteed only during the iteration. It is unspecified
 * whether the connection is maintained outside of the iteration. If the underlying map permits
 * it, calling an Entry's [setValue][Map.Entry.setValue] method will modify the value of the
 * underlying mapping. It is, however, unspecified whether modifications to the value in the
 * underlying mapping are visible in the `Entry` instance.
 *
 *
 * The methods
 * [.firstEntry],
 * [.lastEntry],
 * [.pollFirstEntry], and
 * [.pollLastEntry]
 * return [Map.Entry] instances that represent snapshots of mappings as
 * of the time of the call. They do *not* support mutation of the
 * underlying map via the optional [setValue][Map.Entry.setValue] method.
 *
 *
 * Depending upon the implementation, the `Entry` instances returned by other
 * means might or might not be connected to the underlying map. For example, consider
 * an `Entry` obtained in the following manner:
 * {@snippet :
 * *     var entry = sequencedMap.sequencedEntrySet().getFirst();
 * * }
 * It is not specified by this interface whether the `setValue` method of the
 * `Entry` thus obtained will update a mapping in the underlying map, or whether
 * it will throw an exception, or whether changes to the underlying map are visible in
 * that `Entry`.
 *
 *
 * This interface has the same requirements on the `equals` and `hashCode`
 * methods as defined by [Map.equals] and [Map.hashCode].
 * Thus, a `Map` and a `SequencedMap` will compare equals if and only
 * if they have equal mappings, irrespective of ordering.
 *
 *
 * This class is a member of the
 * [
 * Java Collections Framework]({@docRoot}/java.base/java/util/package-summary.html#CollectionsFramework).
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 * @since 21
</V></K> */
interface SequencedMap<K, V> : MutableMap<K, V> {
    /**
     * Returns a reverse-ordered [view](Collection.html#view) of this map.
     * The encounter order of mappings in the returned view is the inverse of the encounter
     * order of mappings in this map. The reverse ordering affects all order-sensitive operations,
     * including those on the view collections of the returned view. If the implementation permits
     * modifications to this view, the modifications "write through" to the underlying map.
     * Changes to the underlying map might or might not be visible in this reversed view,
     * depending upon the implementation.
     *
     * @return a reverse-ordered view of this map
     */
    fun reversed(): SequencedMap<K, V>?

    /**
     * Returns the first key-value mapping in this map,
     * or `null` if the map is empty.
     *
     * @implSpec
     * The implementation in this interface obtains the iterator of this map's entrySet.
     * If the iterator has an element, it returns an unmodifiable copy of that element.
     * Otherwise, it returns null.
     *
     * @return the first key-value mapping,
     * or `null` if this map is empty
     */
    fun firstEntry(): Map.Entry<K, V>? {
        val it: MutableIterator<MutableMap.MutableEntry<K, V>?> = entries.iterator()
        return if (it.hasNext()) NullableKeyValueHolder(it.next()!!) else null
    }

    /**
     * Returns the last key-value mapping in this map,
     * or `null` if the map is empty.
     *
     * @implSpec
     * The implementation in this interface obtains the iterator of the entrySet of this map's
     * reversed view. If the iterator has an element, it returns an unmodifiable copy of
     * that element. Otherwise, it returns null.
     *
     * @return the last key-value mapping,
     * or `null` if this map is empty
     */
    fun lastEntry(): Map.Entry<K, V>? {
        val it: MutableIterator<MutableMap.MutableEntry<K, V>?> = reversed()!!.entries.iterator()
        return if (it.hasNext()) NullableKeyValueHolder(it.next()!!) else null
    }

    /**
     * Removes and returns the first key-value mapping in this map,
     * or `null` if the map is empty (optional operation).
     *
     * @implSpec
     * The implementation in this interface obtains the iterator of this map's entrySet.
     * If the iterator has an element, it calls `remove` on the iterator and
     * then returns an unmodifiable copy of that element. Otherwise, it returns null.
     *
     * @return the removed first entry of this map,
     * or `null` if this map is empty
     * @throws UnsupportedOperationException if this collection implementation does not
     * support this operation
     */
    fun pollFirstEntry(): Map.Entry<K, V>? {
        val it: MutableIterator<MutableMap.MutableEntry<K, V>?> = entries.iterator()
        if (it.hasNext()) {
            val entry: NullableKeyValueHolder<K, V> = NullableKeyValueHolder(it.next()!!)
            it.remove()
            return entry
        } else {
            return null
        }
    }

    /**
     * Removes and returns the last key-value mapping in this map,
     * or `null` if the map is empty (optional operation).
     *
     * @implSpec
     * The implementation in this interface obtains the iterator of the entrySet of this map's
     * reversed view. If the iterator has an element, it calls `remove` on the iterator
     * and then returns an unmodifiable copy of that element. Otherwise, it returns null.
     *
     * @return the removed last entry of this map,
     * or `null` if this map is empty
     * @throws UnsupportedOperationException if this collection implementation does not
     * support this operation
     */
    fun pollLastEntry(): Map.Entry<K, V>? {
        val it: MutableIterator<MutableMap.MutableEntry<K, V>?> = reversed()!!.entries.iterator()
        if (it.hasNext()) {
            val entry: NullableKeyValueHolder<K, V> = NullableKeyValueHolder(it.next()!!)
            it.remove()
            return entry
        } else {
            return null
        }
    }

    /**
     * Inserts the given mapping into the map if it is not already present, or replaces the
     * value of a mapping if it is already present (optional operation). After this operation
     * completes normally, the given mapping will be present in this map, and it will be the
     * first mapping in this map's encounter order.
     *
     * @implSpec The implementation in this interface always throws
     * `UnsupportedOperationException`.
     *
     * @param k the key
     * @param v the value
     * @return the value previously associated with k, or null if none
     * @throws UnsupportedOperationException if this collection implementation does not
     * support this operation
     */
    fun putFirst(k: K, v: V): V {
        throw UnsupportedOperationException()
    }

    /**
     * Inserts the given mapping into the map if it is not already present, or replaces the
     * value of a mapping if it is already present (optional operation). After this operation
     * completes normally, the given mapping will be present in this map, and it will be the
     * last mapping in this map's encounter order.
     *
     * @implSpec The implementation in this interface always throws
     * `UnsupportedOperationException`.
     *
     * @param k the key
     * @param v the value
     * @return the value previously associated with k, or null if none
     * @throws UnsupportedOperationException if this collection implementation does not
     * support this operation
     */
    fun putLast(k: K, v: V): V {
        throw UnsupportedOperationException()
    }

    /**
     * Returns a `SequencedSet` view of this map's [keySet][.keySet].
     *
     * @implSpec
     * The implementation in this interface returns a `SequencedSet` instance
     * that behaves as follows. Its [add][SequencedSet.add] and [ ][SequencedSet.addAll] methods throw [UnsupportedOperationException].
     * Its [reversed][SequencedSet.reversed] method returns the [ ][.sequencedKeySet] view of the [reversed][.reversed] view of
     * this map. Each of its other methods calls the corresponding method of the [ ][.keySet] view of this map.
     *
     * @return a `SequencedSet` view of this map's `keySet`
     */
    /*fun sequencedKeySet(): SequencedSet<K>? {
        class SeqKeySet : ViewCollection<K>(), SequencedSet<K> {
            override fun view(): MutableCollection<K> {
                return this@SequencedMap.keys
            }

            override fun reversed(): SequencedSet<K>? {
                return this@SequencedMap.reversed()!!.sequencedKeySet()
            }

            override fun equals(other: Any?): Boolean {
                return view() == other
            }

            override fun hashCode(): Int {
                return view().hashCode()
            }
        }
        return SeqKeySet()
    }*/

    /**
     * Returns a `SequencedCollection` view of this map's [values][.values] collection.
     *
     * @implSpec
     * The implementation in this interface returns a `SequencedCollection` instance
     * that behaves as follows. Its [add][SequencedCollection.add] and [ ][SequencedCollection.addAll] methods throw [UnsupportedOperationException].
     * Its [reversed][SequencedCollection.reversed] method returns the [ ][.sequencedValues] view of the [reversed][.reversed] view of
     * this map. Its [equals][Object.equals] and [hashCode][Object.hashCode] methods
     * are inherited from [Object]. Each of its other methods calls the corresponding
     * method of the [values][.values] view of this map.
     *
     * @return a `SequencedCollection` view of this map's `values` collection
     */
    /*fun sequencedValues(): SequencedCollection<V>? {
        class SeqValues : ViewCollection<V>(), SequencedCollection<V> {
            override fun view(): MutableCollection<V> {
                return this@SequencedMap.values
            }

            override fun reversed(): SequencedCollection<V>? {
                return this@SequencedMap.reversed()!!.sequencedValues()
            }
        }
        return SeqValues()
    }*/

    /**
     * Returns a `SequencedSet` view of this map's [entrySet][.entrySet].
     *
     * @implSpec
     * The implementation in this interface returns a `SequencedSet` instance
     * that behaves as follows. Its [add][SequencedSet.add] and [ ][SequencedSet.addAll] methods throw [UnsupportedOperationException].
     * Its [reversed][SequencedSet.reversed] method returns the [ ][.sequencedEntrySet] view of the [reversed][.reversed] view of
     * this map. Each of its other methods calls the corresponding method of the [ ][.entrySet] view of this map.
     *
     * @return a `SequencedSet` view of this map's `entrySet`
     */
    /*fun sequencedEntrySet(): SequencedSet<MutableMap.MutableEntry<K, V>?>? {
        class SeqEntrySet : ViewCollection<MutableMap.MutableEntry<K, V>?>(),
            SequencedSet<MutableMap.MutableEntry<K, V>?> {
            override fun view(): MutableCollection<MutableMap.MutableEntry<K, V>?> {
                return this@SequencedMap.entries
            }

            override fun reversed(): SequencedSet<MutableMap.MutableEntry<K, V>?>? {
                return this@SequencedMap.reversed()!!.sequencedEntrySet()
            }

            override fun equals(other: Any?): Boolean {
                return view() == other
            }

            override fun hashCode(): Int {
                return view().hashCode()
            }
        }
        return SeqEntrySet()
    }*/
}
