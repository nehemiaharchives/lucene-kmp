package org.gnit.lucenekmp.util.mutable

/**
 * Base class for all mutable values.
 *
 * @lucene.internal
 */
abstract class MutableValue : Comparable<MutableValue> {
    var exists: Boolean = true

    abstract fun copy(source: MutableValue)

    abstract fun duplicate(): MutableValue

    abstract fun equalsSameType(other: Any?): Boolean

    abstract fun compareSameType(other: Any?): Int

    abstract fun toObject(): Any?

    fun exists(): Boolean {
        return exists
    }

    override fun compareTo(other: MutableValue): Int {
        val c1 = this::class
        val c2 = other::class
        if (c1 != c2) {
            var c = c1.hashCode() - c2.hashCode()
            if (c == 0) {
                c = c1.qualifiedName!!.compareTo(c2.qualifiedName!!)
            }
            return c
        }
        return compareSameType(other)
    }

    override fun equals(other: Any?): Boolean {
        return (other != null && this::class == other::class) && equalsSameType(other)
    }

    abstract override fun hashCode(): Int

    override fun toString(): String {
        return if (exists()) toObject().toString() else "(null)"
    }
}
