package org.gnit.lucenekmp.jdkport

/**
 * ported from java.util.Map.computeIfAbsent()
 *
 * If the specified key is not already associated with a value (or is mapped
 * to `null`), attempts to compute its value using the given mapping
 * function and enters it into this map unless `null` (optional operation).
 *
 *
 * If the mapping function returns `null`, no mapping is recorded.
 * If the mapping function itself throws an (unchecked) exception, the
 * exception is rethrown, and no mapping is recorded.  The most
 * common usage is to construct a new object serving as an initial
 * mapped value or memoized result, as in:
 *
 * <pre> `map.computeIfAbsent(key, k -> new Value(f(k)));
`</pre> *
 *
 *
 * Or to implement a multi-value map, `Map<K,Collection<V>>`,
 * supporting multiple values per key:
 *
 * <pre> `map.computeIfAbsent(key, k -> new HashSet<V>()).add(v);
`</pre> *
 *
 *
 * The mapping function should not modify this map during computation.
 *
 * @implSpec
 * The default implementation is equivalent to the following steps for this
 * `map`, then returning the current value or `null` if now
 * absent:
 *
 * <pre> `if (map.get(key) == null) {
 * V newValue = mappingFunction.apply(key);
 * if (newValue != null)
 * map.put(key, newValue);
 * }
`</pre> *
 *
 *
 * The default implementation makes no guarantees about detecting if the
 * mapping function modifies this map during computation and, if
 * appropriate, reporting an error. Non-concurrent implementations should
 * override this method and, on a best-effort basis, throw a
 * `ConcurrentModificationException` if it is detected that the
 * mapping function modifies this map during computation. Concurrent
 * implementations should override this method and, on a best-effort basis,
 * throw an `IllegalStateException` if it is detected that the
 * mapping function modifies this map during computation and as a result
 * computation would never complete.
 *
 *
 * The default implementation makes no guarantees about synchronization
 * or atomicity properties of this method. Any implementation providing
 * atomicity guarantees must override this method and document its
 * concurrency properties. In particular, all implementations of
 * subinterface [java.util.concurrent.ConcurrentMap] must document
 * whether the mapping function is applied once atomically only if the value
 * is not present.
 *
 * @param key key with which the specified value is to be associated
 * @param mappingFunction the mapping function to compute a value
 * @return the current (existing or computed) value associated with
 * the specified key, or null if the computed value is null
 * @throws NullPointerException if the specified key is null and
 * this map does not support null keys, or the mappingFunction
 * is null
 * @throws UnsupportedOperationException if the `computeIfAbsent` operation is not
 * supported by this map ([-restrictions optional][Collection])
 * @throws ClassCastException if the class of the specified key or value
 * prevents it from being stored in this map
 * ([-restrictions optional][Collection])
 * @throws IllegalArgumentException if some property of the specified key
 * or value prevents it from being stored in this map
 * ([-restrictions optional][Collection])
 * @since 1.8
 */
fun <K, V> MutableMap<K, V>.computeIfAbsent(
    key: K,
    mappingFunction: (K)->V /*java.util.function.Function<in K, out V>*/
): V? {
    //java.util.Objects.requireNonNull(mappingFunction)
    var v: V?
    if ((get(key).also { v = it }) == null) {
        val newValue: V
        if ((mappingFunction(key).also { newValue = it }) != null) {
            put(key, newValue)
            return newValue
        }
    }

    return v
}