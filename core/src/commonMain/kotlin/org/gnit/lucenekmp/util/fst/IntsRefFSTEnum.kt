package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.IntsRef
import okio.IOException

/**
 * Enumerates all input (IntsRef) + output pairs in an FST.
 *
 * @lucene.experimental
 */
class IntsRefFSTEnum<T>(fst: FST<T>) : FSTEnum<T>(fst) {
    private val current: IntsRef = IntsRef(10)
    private val result = InputOutput<T>()
    private var target: IntsRef? = null

    /** Holds a single input (IntsRef) + output pair. */
    class InputOutput<T> {
        var input: IntsRef? = null
        var output: T? = null
    }

    /**
     * doFloor controls the behavior of advance: if it's true doFloor is true, advance positions to
     * the biggest term before target.
     */
    init {
        result.input = current
        current.offset = 1
    }

    fun current(): InputOutput<T> {
        return result
    }

    @Throws(IOException::class)
    fun next(): InputOutput<T>? {
        doNext()
        return setResult()
    }

    /** Seeks to smallest term that's &gt;= target. */
    @Throws(IOException::class)
    fun seekCeil(target: IntsRef): InputOutput<T>? {
        this.target = target
        targetLength = target.length
        super.doSeekCeil()
        return setResult()
    }

    /** Seeks to biggest term that's &lt;= target. */
    @Throws(IOException::class)
    fun seekFloor(target: IntsRef): InputOutput<T>? {
        this.target = target
        targetLength = target.length
        super.doSeekFloor()
        return setResult()
    }

    /**
     * Seeks to exactly this term, returning null if the term doesn't exist. This is faster than using
     * [.seekFloor] or [.seekCeil] because it short-circuits as soon the match is not
     * found.
     */
    @Throws(IOException::class)
    fun seekExact(target: IntsRef): InputOutput<T>? {
        this.target = target
        targetLength = target.length
        return if (doSeekExact()) {
            require(upto == 1 + target.length)
            setResult()
        } else {
            null
        }
    }

    override fun getTargetLabel(): Int {
        return if (upto - 1 == target!!.length) {
            FST.END_LABEL
        } else {
            target!!.ints[target!!.offset + upto - 1]
        }
    }

    override fun getCurrentLabel(): Int = current.ints[upto]

    override fun setCurrentLabel(label: Int) {
        current.ints[upto] = label
    }

    override fun grow() {
        current.ints = ArrayUtil.grow(current.ints, upto + 1)
    }

    private fun setResult(): InputOutput<T>? {
        return if (upto == 0) {
            null
        } else {
            current.length = upto - 1
            result.output = output[upto]
            result
        }
    }
}
