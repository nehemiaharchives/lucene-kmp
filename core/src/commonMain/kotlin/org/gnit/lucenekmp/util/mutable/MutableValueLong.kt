package org.gnit.lucenekmp.util.mutable

import org.gnit.lucenekmp.jdkport.assert

/**
 * [MutableValue] implementation of type `long`. When mutating instances of this
 * object, the caller is responsible for ensuring that any instance where `exists` is set
 * to `false` must also `value` set to `0L` for proper operation.
 */
open class MutableValueLong : MutableValue() {
    var value: Long = 0L

    override fun toObject(): Any? {
        assert(exists || 0L == value)
        return if (exists) value else null
    }

    override fun copy(source: MutableValue) {
        val s = source as MutableValueLong
        exists = s.exists
        value = s.value
    }

    override fun duplicate(): MutableValue {
        val v = MutableValueLong()
        v.value = this.value
        v.exists = this.exists
        return v
    }

    override fun equalsSameType(other: Any?): Boolean {
        assert(exists || 0L == value)
        val b = other as MutableValueLong
        return value == b.value && exists == b.exists
    }

    override fun compareSameType(other: Any?): Int {
        assert(exists || 0L == value)
        val b = other as MutableValueLong
        val bv = b.value
        if (value < bv) return -1
        if (value > bv) return 1
        if (exists == b.exists) return 0
        return if (exists) 1 else -1
    }

    override fun hashCode(): Int {
        assert(exists || 0L == value)
        return value.toInt() + (value shr 32).toInt()
    }
}
