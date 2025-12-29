package morfologik.stemming

import kotlin.collections.AbstractList
import kotlin.collections.RandomAccess

/**
 * A view over a range of an array.
 */
internal class ArrayViewList<E>(
    array: Array<E>,
    start: Int,
    length: Int
) : AbstractList<E>(), RandomAccess {
    private var a: Array<E> = array
    private var startIndex: Int = start
    private var viewLength: Int = length

    init {
        wrap(array, start, length)
    }

    override val size: Int
        get() = viewLength

    override fun get(index: Int): E {
        return a[startIndex + index]
    }

    override fun indexOf(element: E): Int {
        for (i in startIndex until (startIndex + viewLength)) {
            if (element == a[i]) {
                return i - startIndex
            }
        }
        return -1
    }

    override fun contains(element: E): Boolean {
        return indexOf(element) != -1
    }

    fun wrap(array: Array<E>, start: Int, length: Int) {
        require(start >= 0) { "start must be >= 0" }
        require(length >= 0) { "length must be >= 0" }
        require(start + length <= array.size) { "view exceeds array bounds" }
        this.a = array
        this.startIndex = start
        this.viewLength = length
    }
}
