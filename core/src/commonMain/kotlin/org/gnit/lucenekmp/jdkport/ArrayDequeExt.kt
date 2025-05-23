package org.gnit.lucenekmp.jdkport

fun <E>ArrayDeque<E>.push(e: E) {
    addFirst(e)
}

fun <E> ArrayDeque<E>.peek(): E{
    return elementAt(0)
}

fun <E> ArrayDeque<E>.pop(): E {
    return removeFirst()
}

fun <E> ArrayDeque<E>.getFirst(): E {
    if (isEmpty()) throw NoSuchElementException()
    return first()
}

/**
 * Returns the last element of this deque.
 * @throws NoSuchElementException if this deque is empty
 */
fun <E> ArrayDeque<E>.getLast(): E {
    if (isEmpty()) throw NoSuchElementException()
    return last()
}

fun <E> ArrayDeque<E>.pollFirst(): E? {
    if (isEmpty()) {
        return null
    }
    return removeFirst()
}

/**
 * Circularly increments i, mod modulus.
 * Precondition and postcondition: 0 <= i < modulus.
 */
fun inc(i: Int, modulus: Int): Int {
    var i = i
    if (++i >= modulus) i = 0
    return i
}


/**
 * Java‑style tail‑to‑head iterator for [ArrayDeque].
 *
 * Matches `Deque.descendingIterator()` but returns [MutableIterator] so you
 * can call [MutableIterator.remove] during traversal.
 */
fun <E> ArrayDeque<E>.descendingIterator(): MutableIterator<E> = object : MutableIterator<E> {
    private var nextIndex = lastIndex          // index of next element to return
    private var lastReturned = -1              // index of element returned by last next()

    override fun hasNext(): Boolean = nextIndex >= 0

    override fun next(): E {
        if (!hasNext()) throw NoSuchElementException()
        lastReturned = nextIndex
        return this@descendingIterator.elementAt(nextIndex--)
    }

    override fun remove() {
        if (lastReturned < 0)                      // next() not called or already removed
            throw IllegalStateException("Call next() before remove()")

        this@descendingIterator.removeAt(lastReturned)   // O(1) amortised, std‑lib method&#8203;:contentReference[oaicite:3]{index=3}
        /* After a deletion indices ≥ lastReturned shift left by 1.
           Since we always iterate downward, the nextIndex is already < lastReturned,
           so no extra adjustment is usually needed.  Re‑sync anyway for safety. */
        if (lastReturned <= nextIndex) nextIndex--
        lastReturned = -1
    }
}