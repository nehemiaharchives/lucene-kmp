package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import okio.IOException
import kotlin.experimental.and

/**
 * Enumerates all input (BytesRef) + output pairs in an FST.
 *
 * @lucene.experimental
 */
open class BytesRefFSTEnum<T>(fst: FST<T>) : FSTEnum<T>(fst) {
    private val current: BytesRef = BytesRef(10)
    private val result = InputOutput<T>()
    private var target: BytesRef? = null

    /** Holds a single input (BytesRef) + output pair.  */
    class InputOutput<T> {
        var input: BytesRef? = null
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
        // System.out.println("  enum.next");
        doNext()
        return setResult()
    }

    /** Seeks to smallest term that's &gt;= target.  */
    @Throws(IOException::class)
    fun seekCeil(target: BytesRef): InputOutput<T>? {
        this.target = target
        targetLength = target.length
        super.doSeekCeil()
        return setResult()
    }

    /** Seeks to biggest term that's &lt;= target.  */
    @Throws(IOException::class)
    fun seekFloor(target: BytesRef): InputOutput<T>? {
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
    fun seekExact(target: BytesRef): InputOutput<T>? {
        this.target = target
        targetLength = target.length
        if (doSeekExact()) {
            require(upto == 1 + target.length)
            return setResult()
        } else {
            return null
        }
    }

    override fun getTargetLabel(): Int {
        return if (upto - 1 == target!!.length) {
            FST.END_LABEL
        } else {
            (target!!.bytes[target!!.offset + upto - 1] and 0xFF.toByte()).toInt()
        }
    }

    override fun getCurrentLabel(): Int =// current.offset fixed at 1
        (current.bytes[upto] and 0xFF.toByte()).toInt()

    override fun setCurrentLabel(label: Int) {
        current.bytes[upto] = label.toByte()
    }

    override fun grow() {
        current.bytes = ArrayUtil.grow(current.bytes, upto + 1)
    }

    private fun setResult(): InputOutput<T>? {
        if (upto == 0) {
            return null
        } else {
            current.length = upto - 1
            result.output = output[upto]
            return result
        }
    }
}
