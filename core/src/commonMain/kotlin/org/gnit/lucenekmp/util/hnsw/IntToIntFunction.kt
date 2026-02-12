package org.gnit.lucenekmp.util.hnsw

/** Native int to int function  */
fun interface IntToIntFunction {
    fun apply(v: Int): Int
}
