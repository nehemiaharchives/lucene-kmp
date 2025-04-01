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