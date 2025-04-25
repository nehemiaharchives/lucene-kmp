package org.gnit.lucenekmp.jdkport

/**
 * An immutable container for a key and a value, both of which are nullable.
 *
 *
 * This is a [value-based]({@docRoot}/java.base/java/lang/doc-files/ValueBased.html)
 * class; programmers should treat instances that are
 * [equal][.equals] as interchangeable and should not
 * use instances for synchronization, or unpredictable behavior may
 * occur. For example, in a future release, synchronization may fail.
 *
 * @apiNote
 * This class is not exported. Instances are created by various Map implementations
 * when they need a Map.Entry that isn't connected to the Map.
 *
 *
 * This class differs from AbstractMap.SimpleImmutableEntry in that it is not
 * serializable and that it is final. This class differs from java.util.KeyValueHolder
 * in that the key and value are nullable.
 *
 *
 * In principle this class could be a variation on KeyValueHolder. However,
 * making that class selectively support nullable keys and values is quite intricate.
 * Various specifications (such as Map.ofEntries and Map.entry) specify non-nullability
 * of the key and the value. Map.Entry.copyOf also requires non-null keys and values;
 * but it simply passes through KeyValueHolder instances, assuming their keys and values
 * are non-nullable. If a KVH with nullable keys and values were introduced, some way
 * to distinguish it would be necessary. This could be done by introducing a subclass
 * (requiring KVH to be made non-final) or by introducing some kind of "mode" field
 * (potentially increasing the size of every KVH instance, though another field could
 * probably fit into the object's padding in most JVMs.) More critically, a mode field
 * would have to be checked in all the right places to get the right behavior.
 *
 *
 * A longer range possibility is to selectively relax the restrictions against nulls in
 * Map.entry and Map.Entry.copyOf. This would also require some intricate specification
 * changes and corresponding implementation changes (e.g., the implementations backing
 * Map.of might still need to reject nulls, and so would Map.ofEntries) but allowing
 * a Map.Entry itself to contain nulls seems beneficial in general. If this is done,
 * merging KeyValueHolder and NullableKeyValueHolder should be reconsidered.
 *
 * @param <K> the key type
 * @param <V> the value type
</V></K> */
class NullableKeyValueHolder<K, V> : MutableMap.MutableEntry<K, V> {
    /**
     * Gets the key from this holder.
     *
     * @return the key, may be null
     */
    override val key: K

    /**
     * Gets the value from this holder.
     *
     * @return the value, may be null
     */
    override val value: V

    /**
     * Constructs a NullableKeyValueHolder.
     *
     * @param k the key, may be null
     * @param v the value, may be null
     */
    constructor(k: K, v: V) {
        key = k
        value = v
    }

    /**
     * Constructs a NullableKeyValueHolder from a Map.Entry. No need for an
     * idempotent copy at this time.
     *
     * @param entry the entry, must not be null
     */
    constructor(entry: MutableMap.MutableEntry<K, V>) {
        key = entry.key
        value = entry.value
    }

    /**
     * Throws [UnsupportedOperationException].
     *
     * @param value ignored
     * @return never returns normally
     */
    override fun setValue(value: V): V {
        throw UnsupportedOperationException("not supported")
    }

    /**
     * Compares the specified object with this entry for equality.
     * Returns `true` if the given object is also a map entry and
     * the two entries' keys and values are equal.
     */
    override fun equals(other: Any?): Boolean {
        return other is MutableMap.MutableEntry<*, *>
                && key == other.key
                && value == other.value
    }

    private fun hash(obj: Any?): Int {
        return obj?.hashCode() ?: 0
    }

    /**
     * Returns the hash code value for this map entry. The hash code
     * is `key.hashCode() ^ value.hashCode()`.
     */
    override fun hashCode(): Int {
        return hash(key) xor hash(value)
    }

    /**
     * Returns a String representation of this map entry.  This
     * implementation returns the string representation of this
     * entry's key followed by the equals character ("`=`")
     * followed by the string representation of this entry's value.
     *
     * @return a String representation of this map entry
     */
    override fun toString(): String {
        return "$key=$value"
    }
}
