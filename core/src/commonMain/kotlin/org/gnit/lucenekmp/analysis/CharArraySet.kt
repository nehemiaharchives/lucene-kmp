package org.gnit.lucenekmp.analysis


/**
 * A simple class that stores Strings as char[]'s in a hash table. Note that this is not a general
 * purpose class. For example, it cannot remove items from the set, nor does it resize its hash
 * table to be smaller, etc. It is designed to be quick to test if a char[] is in the set without
 * the necessity of converting it to a String first.
 *
 *
 * *Please note:* This class implements [Set][java.util.Set] but does not behave like
 * it should in all cases. The generic type is `Set<Object>`, because you can add any object
 * to it, that has a string representation. The add methods will use [Object.toString] and
 * store the result using a `char[]` buffer. The same behavior have the `contains()`
 * methods. The [.iterator] returns an `Iterator<char[]>`.
 */
open class CharArraySet internal constructor(map: CharArrayMap<Any>) : AbstractMutableSet<Any>() {
    private val map: CharArrayMap<Any> = map

    /**
     * Create set with enough capacity to hold startSize terms
     *
     * @param startSize the initial capacity
     * @param ignoreCase `false` if and only if the set should be case sensitive otherwise
     * `true`.
     */
    constructor(startSize: Int, ignoreCase: Boolean) : this(CharArrayMap(startSize, ignoreCase))

    /**
     * Creates a set from a Collection of objects.
     *
     * @param c a collection whose elements to be placed into the set
     * @param ignoreCase `false` if and only if the set should be case sensitive otherwise
     * `true`.
     */
    constructor(c: MutableCollection<Any>, ignoreCase: Boolean) : this(c.size, ignoreCase) {
        addAll(c)
    }

    /**
     * Clears all entries in this set. This method is supported for reusing, but not [ ][Set.remove].
     */
    override fun clear() {
        map.clear()
    }

    /**
     * true if the `len` chars of `text` starting at `off` are in the
     * set
     */
    fun contains(text: CharArray, off: Int, len: Int): Boolean {
        return map.containsKey(text, off, len)
    }

    /** true if the `CharSequence` is in the set  */
    fun contains(cs: CharSequence): Boolean {
        return map.containsKey(cs)
    }

    override fun contains(o: Any): Boolean {
        return map.containsKey(o)
    }

    override fun add(o: Any): Boolean {
        return map.put(o, PLACEHOLDER) == null
    }

    /** Add this CharSequence into the set  */
    open fun add(text: CharSequence): Boolean {
        return map.put(text, PLACEHOLDER) == null
    }

    /** Add this String into the set  */
    open fun add(text: String): Boolean {
        return map.put(text, PLACEHOLDER) == null
    }

    /**
     * Add this char[] directly to the set. If ignoreCase is true for this Set, the text array will be
     * directly modified. The user should never modify this text array after calling this method.
     */
    open fun add(text: CharArray): Boolean {
        return map.putCharArray(text, PLACEHOLDER) == null
    }

    override val size: Int
        get() = map.size

    /** Returns an [Iterator] for `char[]` instances in this set.  */
    override fun iterator(): MutableIterator<Any> {
        // use the AbstractSet#keySet()'s iterator (to not produce endless recursion)
        return map.originalKeySet().iterator()
    }

    override fun toString(): String {
        val sb = StringBuilder("[")
        for (item in this) {
            if (sb.length > 1) sb.append(", ")
            if (item is CharArray) {
                sb.append(item)
            } else {
                sb.append(item)
            }
        }
        return sb.append(']').toString()
    }

    companion object {
        /** An empty `CharArraySet`.  */
        val EMPTY_SET: CharArraySet = CharArraySet(CharArrayMap.emptyMap<Any>())

        private val PLACEHOLDER = Any()

        /**
         * Returns an unmodifiable [CharArraySet]. This allows to provide unmodifiable views of
         * internal sets for "read-only" use.
         *
         * @param set a set for which the unmodifiable set is returned.
         * @return an new unmodifiable [CharArraySet].
         * @throws NullPointerException if the given set is `null`.
         */
        fun unmodifiableSet(set: CharArraySet): CharArraySet {
            //if (set == null) throw java.lang.NullPointerException("Given set is null")
            if (set === EMPTY_SET) return EMPTY_SET
            if (set.map is CharArrayMap.UnmodifiableCharArrayMap) return set
            return CharArraySet(CharArrayMap.unmodifiableMap(set.map))
        }

        /**
         * Returns a copy of the given set as a [CharArraySet]. If the given set is a [ ] the ignoreCase property will be preserved.
         *
         * @param set a set to copy
         * @return a copy of the given set as a [CharArraySet]. If the given set is a [     ] the ignoreCase property as well as the matchVersion will be of the given set
         * will be preserved.
         */
        fun copy(set: MutableSet<Any>): CharArraySet {
            if (set === EMPTY_SET) return EMPTY_SET
            if (set is CharArraySet) {
                return CharArraySet(CharArrayMap.copy(set.map))
            }
            return CharArraySet(set, false)
        }
    }
}
