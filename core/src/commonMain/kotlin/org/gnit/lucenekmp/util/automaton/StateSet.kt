package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.internal.hppc.BitMixer
import org.gnit.lucenekmp.internal.hppc.IntIntHashMap


/**
 * A thin wrapper of [IntIntHashMap] Maps from state in integer representation to its
 * reference count Whenever the count of a state is 0, that state will be removed from the set
 */
internal class StateSet(capacity: Int) : IntSet() {
    private val inner: IntIntHashMap
    private var hashCode: Long = 0
    private var hashUpdated = true
    private var arrayUpdated = true
    private var arrayCache = IntArray(0)

    init {
        inner = IntIntHashMap(capacity)
    }

    /**
     * Add the state into this set, if it is already there, increase its reference count by 1
     *
     * @param state an integer representing this state
     */
    fun incr(state: Int) {
        if (inner.addTo(state, 1) === 1) {
            keyChanged()
        }
    }

    /**
     * Decrease the reference count of the state, if the count down to 0, remove the state from this
     * set
     *
     * @param state an integer representing this state
     */
    fun decr(state: Int) {
        require(inner.containsKey(state))
        val keyIndex = inner.indexOf(state)
        val count: Int = inner.indexGet(keyIndex) - 1
        if (count == 0) {
            inner.indexRemove(keyIndex)
            keyChanged()
        } else {
            inner.indexReplace(keyIndex, count)
        }
    }

    fun reset() {
        inner.clear()
        keyChanged()
    }

    /**
     * Create a snapshot of this int set associated with a given state. The snapshot will not retain
     * any frequency information about the elements of this set, only existence.
     *
     * @param state the state to associate with the frozen set.
     * @return A new FrozenIntSet with the same values as this set.
     */
    fun freeze(state: Int): FrozenIntSet {
        return FrozenIntSet(this.array, longHashCode(), state)
    }

    private fun keyChanged() {
        hashUpdated = false
        arrayUpdated = false
    }

    override val array: IntArray
        get() {
            if (arrayUpdated) {
                return arrayCache
            }
            arrayCache = IntArray(inner.size())
            var i = 0
            for (key in inner.keys()) {
                arrayCache[i++] = key.value
            }
            // we need to sort this array since "equals" method depend on this
            arrayCache.sort()
            arrayUpdated = true
            return arrayCache
        }

    public override fun size(): Int {
        return inner.size()
    }

    public override fun longHashCode(): Long {
        if (hashUpdated) {
            return hashCode
        }
        hashCode = inner.size().toLong()
        for (key in inner.keys()) {
            hashCode += BitMixer.mix(key.value)
        }
        hashUpdated = true
        return hashCode
    }
}
