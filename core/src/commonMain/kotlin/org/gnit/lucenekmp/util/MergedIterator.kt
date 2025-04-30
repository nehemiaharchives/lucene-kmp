package org.gnit.lucenekmp.util

/**
 * Provides a merged sorted view from several sorted iterators.
 *
 *
 * If built with `removeDuplicates` set to true and an element appears in multiple
 * iterators then it is deduplicated, that is this iterator returns the sorted union of elements.
 *
 *
 * If built with `removeDuplicates` set to false then all elements in all iterators
 * are returned.
 *
 *
 * Caveats:
 *
 *
 *  * The behavior is undefined if the iterators are not actually sorted.
 *  * Null elements are unsupported.
 *  * If removeDuplicates is set to true and if a single iterator contains duplicates then they
 * will not be deduplicated.
 *  * When elements are deduplicated it is not defined which one is returned.
 *  * If removeDuplicates is set to false then the order in which duplicates are returned isn't
 * defined.
 *
 *
 * @lucene.internal
 */
class MergedIterator<T : Comparable<T>>(
    private val removeDuplicates: Boolean,
    /*vararg iterators: MutableIterator<T>*/
    iterators: Array<MutableIterator<T>>
) : MutableIterator<T>, Iterable<T> {
    private var current: T? = null
    private val queue: TermMergeQueue<T> = TermMergeQueue<T>(iterators.size)
    private val top: Array<SubIterator<T>?>
    private var numTop = 0

    constructor(vararg iterators: MutableIterator<T>) : this(true, iterators as Array<MutableIterator<T>>)

    //constructor(removeDuplicates: Boolean, iteratorArray: Array<MutableIterator<T>>) : this(removeDuplicates, *iteratorArray)

    init {
        top = kotlin.arrayOfNulls<SubIterator<T>>(iterators.size)
        var index = 0
        for (iterator in iterators) {
            if (iterator.hasNext()) {
                val sub = SubIterator<T>()
                sub.current = iterator.next()
                sub.iterator = iterator
                sub.index = index++
                queue.add(sub)
            }
        }
    }

    override fun hasNext(): Boolean {
        if (queue.size() > 0) {
            return true
        }

        for (i in 0..<numTop) {
            if (top[i]!!.iterator!!.hasNext()) {
                return true
            }
        }
        return false
    }

    override fun next(): T {
        // restore queue
        pushTop()

        // gather equal top elements
        if (queue.size() > 0) {
            pullTop()
        } else {
            current = null
        }
        if (current == null) {
            throw NoSuchElementException()
        }
        return current!!
    }

    override fun remove() {
        throw UnsupportedOperationException()
    }

    private fun pullTop() {
        require(numTop == 0)
        top[numTop++] = queue.pop()!!
        if (removeDuplicates) {
            // extract all subs from the queue that have the same top element
            while (queue.size() != 0 && queue.top()!!.current!!.equals(top[0]!!.current)) {
                top[numTop++] = queue.pop()!!
            }
        }
        current = top[0]!!.current
    }

    private fun pushTop() {
        // call next() on each top, and put back into queue
        for (i in 0..<numTop) {
            if (top[i]!!.iterator!!.hasNext()) {
                top[i]!!.current = top[i]!!.iterator!!.next()
                queue.add(top[i]!!)
            } else {
                // no more elements
                top[i]!!.current = null
            }
        }
        numTop = 0
    }

    override fun iterator(): Iterator<T> {
        return this
    }

    private class SubIterator<I : Comparable<I>> {
        var iterator: MutableIterator<I?>? = null
        var current: I? = null
        var index: Int = 0
    }

    private class TermMergeQueue<C : Comparable<C>>
        (size: Int) : PriorityQueue<SubIterator<C>>(size) {
        override fun lessThan(a: SubIterator<C>, b: SubIterator<C>): Boolean {
            val cmp = a.current!!.compareTo(b.current!!)
            if (cmp != 0) {
                return cmp < 0
            } else {
                return a.index < b.index
            }
        }
    }
}
