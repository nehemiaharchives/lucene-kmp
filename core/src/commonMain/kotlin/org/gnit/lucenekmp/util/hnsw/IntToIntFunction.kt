package org.gnit.lucenekmp.util.hnsw

/** Native int to int function  */
interface IntToIntFunction {
    fun apply(v: Int): Int
}
