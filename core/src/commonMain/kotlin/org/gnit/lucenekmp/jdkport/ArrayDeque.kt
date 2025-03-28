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