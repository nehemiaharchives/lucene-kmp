package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.util.CharsRef

/**
 * A simple class that stores key Strings as char[]'s in a hash table. Note that this is not a
 * general purpose class. For example, it cannot remove items from the map, nor does it resize its
 * hash table to be smaller, etc. It is designed to be quick to retrieve items by char[] keys
 * without the necessity of converting to a String first.
 */
open class CharArrayMap<V> : AbstractMutableMap<Any, V> {
    private val ignoreCase: Boolean
    private var count = 0

    override val entries: MutableSet<MutableMap.MutableEntry<Any, V>>
        get() = TODO("Not yet implemented")

    // package private because used in CharArraySet's non Set-conform CharArraySetIterator
    var keysArray: Array<CharArray?>

    // package private because used in CharArraySet's non Set-conform CharArraySetIterator
    var valuesArray: Array<V?>

    /**
     * Create map with enough capacity to hold startSize terms
     *
     * @param startSize the initial capacity
     * @param ignoreCase `false` if and only if the set should be case sensitive otherwise
     * `true`.
     */
    constructor(startSize: Int, ignoreCase: Boolean) {
        this.ignoreCase = ignoreCase
        var size = INIT_SIZE
        while (startSize + (startSize shr 2) > size) {
            size = size shl 1
        }
        keysArray = kotlin.arrayOfNulls<CharArray>(size)
        valuesArray = kotlin.arrayOfNulls<Any>(size) as Array<V?>
    }

    /**
     * Creates a map from the mappings in another map.
     *
     * @param c a map whose mappings to be copied
     * @param ignoreCase `false` if and only if the set should be case sensitive otherwise
     * `true`.
     */
    constructor(c: MutableMap<Any, V>, ignoreCase: Boolean) : this(c.size, ignoreCase) {
        putAll(c)
    }

    /** Create set from the supplied map (used internally for readonly maps...)  */
    private constructor(toCopy: CharArrayMap<V>) {
        this.keysArray = toCopy.keysArray
        this.valuesArray = toCopy.valuesArray
        this.ignoreCase = toCopy.ignoreCase
        this.count = toCopy.count
    }

    /**
     * Clears all entries in this map. This method is supported for reusing, but not [ ][Map.remove].
     */
    override fun clear() {
        count = 0
        Arrays.fill(keysArray, null)
        Arrays.fill(valuesArray, null)
    }

    /**
     * true if the `len` chars of `text` starting at `off` are in the
     * [.keySet]
     */
    open fun containsKey(text: CharArray, off: Int, len: Int): Boolean {
        return keysArray[getSlot(text, off, len)] != null
    }

    /** true if the `CharSequence` is in the [.keySet]  */
    open fun containsKey(cs: CharSequence): Boolean {
        return keysArray[getSlot(cs)] != null
    }

    override fun containsKey(o: Any): Boolean {
        if (o is CharArray) {
            return containsKey(o, 0, o.size)
        }
        return containsKey(o.toString())
    }

    /**
     * returns the value of the mapping of `len` chars of `text` starting at
     * `off`
     */
    open fun get(text: CharArray, off: Int, len: Int): V? {
        return valuesArray[getSlot(text, off, len)]
    }

    /** returns the value of the mapping of the chars inside this `CharSequence`  */
    open fun get(cs: CharSequence): V? {
        return valuesArray[getSlot(cs)]
    }

    override fun get(o: Any): V? {
        if (o is CharArray) {
            return get(o, 0, o.size)
        }
        return get(o.toString())
    }

    private fun getSlot(text: CharArray, off: Int, len: Int): Int {
        var code = getHashCode(text, off, len)
        var pos = code and (keysArray.size - 1)
        var text2 = keysArray[pos]
        if (text2 != null && !equals(text, off, len, text2)) {
            val inc = ((code shr 8) + code) or 1
            do {
                code += inc
                pos = code and (keysArray.size - 1)
                text2 = keysArray[pos]
            } while (text2 != null && !equals(text, off, len, text2))
        }
        return pos
    }

    /** Returns true if the String is in the set  */
    private fun getSlot(text: CharSequence): Int {
        var code = getHashCode(text)
        var pos = code and (keysArray.size - 1)
        var text2 = keysArray[pos]
        if (text2 != null && !equals(text, text2)) {
            val inc = ((code shr 8) + code) or 1
            do {
                code += inc
                pos = code and (keysArray.size - 1)
                text2 = keysArray[pos]
            } while (text2 != null && !equals(text, text2))
        }
        return pos
    }

    /** Add the given mapping.  */
    /*fun put(text: CharSequence, value: V?): V? {
        return put(text.toString(), value) // could be more efficient
    }*/

    override fun put(o: Any, value: V): V? {
        if (o is CharArray) {
            return putCharArray(o as CharArray, value)
        }
        return put(o.toString(), value)
    }

    /** Add the given mapping.  */
    /*fun put(text: String, value: V): V? {
        return put(text.toCharArray(), value)
    }*/

    /**
     * Add the given mapping. If ignoreCase is true for this Set, the text array will be directly
     * modified. The user should never modify this text array after calling this method.
     */
    fun putCharArray(text: CharArray, value: V?): V? {
        if (ignoreCase) {
            CharacterUtils.toLowerCase(text, 0, text.size)
        }
        val slot = getSlot(text, 0, text.size)
        if (keysArray[slot] != null) {
            val oldValue = valuesArray[slot]
            valuesArray[slot] = value
            return oldValue
        }
        keysArray[slot] = text
        valuesArray[slot] = value
        count++

        if (count + (count shr 2) > keysArray.size) {
            rehash()
        }

        return null
    }

    private fun rehash() {
        require(keysArray.size == valuesArray.size)
        val newSize = 2 * keysArray.size
        val oldkeys = keysArray
        val oldvalues = valuesArray
        keysArray = kotlin.arrayOfNulls(newSize)
        valuesArray = kotlin.arrayOfNulls<Any>(newSize) as Array<V?>

        for (i in oldkeys.indices) {
            val text = oldkeys[i]
            if (text != null) {
                // todo: could be faster... no need to compare strings on collision
                val slot = getSlot(text, 0, text.size)
                keysArray[slot] = text
                valuesArray[slot] = oldvalues[i]
            }
        }
    }

    private fun equals(text1: CharArray, off: Int, len: Int, text2: CharArray): Boolean {
        if (len != text2.size) return false
        val limit = off + len
        if (ignoreCase) {
            var i = 0
            while (i < len) {
                val codePointAt: Int = Character.codePointAt(text1, off + i, limit)
                if (Character.toLowerCase(codePointAt) != Character.codePointAt(
                        text2,
                        i,
                        text2.size
                    )
                ) return false
                i += Character.charCount(codePointAt)
            }
        } else {
            for (i in 0..<len) {
                if (text1[off + i] != text2[i]) return false
            }
        }
        return true
    }

    private fun equals(text1: CharSequence, text2: CharArray): Boolean {
        val len = text1.length
        if (len != text2.size) return false
        if (ignoreCase) {
            var i = 0
            while (i < len) {
                val codePointAt: Int = Character.codePointAt(text1, i)
                if (Character.toLowerCase(codePointAt) != Character.codePointAt(
                        text2,
                        i,
                        text2.size
                    )
                ) return false
                i += Character.charCount(codePointAt)
            }
        } else {
            for (i in 0..<len) {
                if (text1[i] != text2[i]) return false
            }
        }
        return true
    }

    private fun getHashCode(text: CharArray, offset: Int, len: Int): Int {
        //if (text == null) throw java.lang.NullPointerException()
        if (ignoreCase) {
            val stop = offset + len
            var code = 0
            var i = offset
            while (i < stop) {
                val codePointAt: Int = Character.codePointAt(text, i, stop)
                code = code * 31 + Character.toLowerCase(codePointAt)
                i += Character.charCount(codePointAt)
            }
            return code
        }
        return CharsRef.stringHashCode(text, offset, len)
    }

    private fun getHashCode(text: CharSequence): Int {
        //if (text == null) throw java.lang.NullPointerException()
        var code = 0
        val len = text.length
        if (ignoreCase) {
            var i = 0
            while (i < len) {
                val codePointAt: Int = Character.codePointAt(text, i)
                code = code * 31 + Character.toLowerCase(codePointAt)
                i += Character.charCount(codePointAt)
            }
        } else {
            for (i in 0..<len) {
                code = code * 31 + text[i].code
            }
        }
        return code
    }

    override fun remove(key: Any): V? {
        throw UnsupportedOperationException()
    }

    override val size: Int
        get()= count

    override fun toString(): String {
        val sb = StringBuilder("{")
        for (entry in entries) {
            if (sb.length > 1) sb.append(", ")
            sb.append(entry)
        }
        return sb.append('}').toString()
    }

    private var entrySet: EntrySet? = null

    open fun createEntrySet(): EntrySet {
        return this@CharArrayMap.EntrySet(true)
    }

    fun entrySet(): EntrySet {
        if (entrySet == null) {
            entrySet = createEntrySet()
        }
        return entrySet!!
    }

    // helper for CharArraySet to not produce endless recursion
    fun originalKeySet(): MutableSet<Any> {
        return super.keys
    }

    /**
     * Returns an [CharArraySet] view on the map's keys. The set will use the same `matchVersion` as this map.
     */
    var keySet: CharArraySet?
        get() {
        if (keySet == null) {
            // prevent adding of entries
            keySet =
                object : CharArraySet(this@CharArrayMap as CharArrayMap<Any>) {
                    override fun add(o: Any): Boolean {
                        throw UnsupportedOperationException()
                    }

                    override fun add(text: CharSequence): Boolean {
                        throw UnsupportedOperationException()
                    }

                    override fun add(text: String): Boolean {
                        throw UnsupportedOperationException()
                    }

                    override fun add(text: CharArray): Boolean {
                        throw UnsupportedOperationException()
                    }
                }
        }
        return keySet
    }

    /** public iterator class so efficient methods are exposed to users  */
    inner class EntryIterator(private val allowModify: Boolean) :
        MutableIterator<MutableMap.MutableEntry<Any, V>> {
        private var pos = -1
        private var lastPos = 0

        init {
            goNext()
        }

        private fun goNext() {
            lastPos = pos
            pos++
            while (pos < keysArray.size && keysArray[pos] == null) pos++
        }

        override fun hasNext(): Boolean {
            return pos < keysArray.size
        }

        /** gets the next key... do not modify the returned char[]  */
        fun nextKey(): CharArray? {
            goNext()
            return keysArray[lastPos]
        }

        /** gets the next key as a newly created String object  */
        fun nextKeyString(): String {
            return nextKey()!!.concatToString()
        }

        /** returns the value associated with the last key returned  */
        fun currentValue(): V? {
            return valuesArray[lastPos]
        }

        /** sets the value associated with the last key returned  */
        fun setValue(value: V?): V? {
            if (!allowModify) throw UnsupportedOperationException()
            val old = valuesArray[lastPos]
            valuesArray[lastPos] = value
            return old
        }

        /** use nextCharArray() + currentValue() for better efficiency.  */
        override fun next(): MutableMap.MutableEntry<Any, V> {
            goNext()
            return this@CharArrayMap.MapEntry(lastPos, allowModify)
        }

        override fun remove() {
            throw UnsupportedOperationException()
        }
    }

    private inner class MapEntry(private val pos: Int, private val allowModify: Boolean) :
        MutableMap.MutableEntry<Any, V> {
        override val key: Any
            get() =// we must clone here, as putAll to another CharArrayMap
                // with other case sensitivity flag would corrupt the keys
                keysArray[pos]!!.clone()

        override val value: V
            get() = valuesArray[pos]!!

        override fun setValue(value: V): V {
            if (!allowModify) throw UnsupportedOperationException()
            val old = valuesArray[pos]
            valuesArray[pos] = value
            return old!!
        }

        override fun toString(): String {
            return (keysArray[pos]!!.concatToString() + '='
                    + (if (valuesArray[pos] === this@CharArrayMap) "(this Map)" else valuesArray[pos]))
        }

    }

    /** public EntrySet class so efficient methods are exposed to users  */
    inner class EntrySet(private val allowModify: Boolean) :
        AbstractMutableSet<MutableMap.MutableEntry<Any, V>>() {

        override fun iterator(): EntryIterator {
            return this@CharArrayMap.EntryIterator(allowModify)
        }

        override fun add(element: MutableMap.MutableEntry<Any, V>): Boolean {
            TODO("Not yet implemented")
        }

        override fun contains(o: MutableMap.MutableEntry<Any, V>): Boolean {
            if (o !is MutableMap.MutableEntry<*, *>) return false
            val e = o as MutableMap.MutableEntry<Any, V>
            val key = e.key
            val `val`: Any? = e.value
            val v: Any? = get(key)
            return if (v == null) `val` == null else (v == `val`)
        }

        override fun remove(o: MutableMap.MutableEntry<Any, V>): Boolean {
            throw UnsupportedOperationException()
        }

        override val size: Int
            get() = count


        override fun clear() {
            if (!allowModify) throw UnsupportedOperationException()
            this@CharArrayMap.clear()
        }
    }

    // package private CharArraySet instanceof check in CharArraySet
    internal open class UnmodifiableCharArrayMap<V>(map: CharArrayMap<V>) : CharArrayMap<V>(map) {
        override fun clear() {
            throw UnsupportedOperationException()
        }

        override fun put(o: Any, `val`: V): V? {
            throw UnsupportedOperationException()
        }

        /*fun put(text: CharArray, `val`: V): V? {
            throw UnsupportedOperationException()
        }*/

        /*override fun put(text: CharSequence?, `val`: V?): V? {
            throw UnsupportedOperationException()
        }*/

        /*override fun put(text: String?, `val`: V?): V? {
            throw UnsupportedOperationException()
        }*/

        override fun remove(key: Any): V? {
            throw UnsupportedOperationException()
        }

        override fun createEntrySet(): EntrySet {
            return EntrySet(false)
        }
    }

    /**
     * Empty [org.apache.lucene.analysis.CharArrayMap.UnmodifiableCharArrayMap] optimized for
     * speed. Contains checks will always return `false` or throw NPE if necessary.
     */
    private class EmptyCharArrayMap<V> : UnmodifiableCharArrayMap<V>(CharArrayMap(0, false)) {
        override fun containsKey(text: CharArray, off: Int, len: Int): Boolean {
            //if (text == null) throw java.lang.NullPointerException()
            return false
        }

        override fun containsKey(cs: CharSequence): Boolean {
            //if (cs == null) throw java.lang.NullPointerException()
            return false
        }

        override fun containsKey(o: Any): Boolean {
            //if (o == null) throw java.lang.NullPointerException()
            return false
        }

        override fun get(text: CharArray, off: Int, len: Int): V? {
            //if (text == null) throw java.lang.NullPointerException()
            return null
        }

        override fun get(cs: CharSequence): V? {
            //if (cs == null) throw java.lang.NullPointerException()
            return null
        }

        override fun get(o: Any): V? {
            //if (o == null) throw java.lang.NullPointerException()
            return null
        }
    }

    companion object {
        // private only because missing generics
        private val EMPTY_MAP: CharArrayMap<*> = EmptyCharArrayMap<Any?>()

        private const val INIT_SIZE = 8

        /**
         * Returns an unmodifiable [CharArrayMap]. This allows to provide unmodifiable views of
         * internal map for "read-only" use.
         *
         * @param map a map for which the unmodifiable map is returned.
         * @return an new unmodifiable [CharArrayMap].
         * @throws NullPointerException if the given map is `null`.
         */
        fun <V> unmodifiableMap(map: CharArrayMap<V>): CharArrayMap<V> {
            //if (map == null) throw java.lang.NullPointerException("Given map is null")
            if (map === emptyMap<Any>() || map.isEmpty()) return emptyMap<V>()
            if (map is UnmodifiableCharArrayMap<*>) return map
            return UnmodifiableCharArrayMap(map)
        }

        /**
         * Returns a copy of the given map as a [CharArrayMap]. If the given map is a [ ] the ignoreCase property will be preserved.
         *
         * @param map a map to copy
         * @return a copy of the given map as a [CharArrayMap]. If the given map is a [     ] the ignoreCase property as well as the matchVersion will be of the given map
         * will be preserved.
         */
        fun <V> copy(map: MutableMap<Any, V>): CharArrayMap<V> {
            if (map === EMPTY_MAP) return emptyMap<V>()
            if (map is CharArrayMap<*>) {
                var m = map as CharArrayMap<V>
                // use fast path instead of iterating all values
                // this is even on very small sets ~10 times faster than iterating
                val keys = kotlin.arrayOfNulls<CharArray>(m.keysArray.size)
                System.arraycopy(m.keysArray, 0, keys, 0, keys.size)
                val values = kotlin.arrayOfNulls<Any>(m.valuesArray.size) as Array<V?>
                System.arraycopy(m.valuesArray, 0, values, 0, values.size)
                m = CharArrayMap(m)
                m.keysArray = keys
                m.valuesArray = values
                return m
            }
            return CharArrayMap(map, false)
        }

        /** Returns an empty, unmodifiable map.  */
        fun <V> emptyMap(): CharArrayMap<V> {
            return EMPTY_MAP as CharArrayMap<V>
        }
    }
}
