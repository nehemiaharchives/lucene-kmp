package org.gnit.lucenekmp.util.mutable

import org.gnit.lucenekmp.jdkport.assert

/**
 * [MutableValue] implementation of type `float`. When mutating instances of this
 * object, the caller is responsible for ensuring that any instance where `exists` is set
 * to `false` must also `value` set to `0.0F` for proper operation.
 */
class MutableValueFloat : MutableValue() {
    var value: Float = 0.0f

    override fun toObject(): Any? {
        assert(exists || 0.0f == value)
        return if (exists) value else null
    }

    override fun copy(source: MutableValue) {
        val s = source as MutableValueFloat
        value = s.value
        exists = s.exists
    }

    override fun duplicate(): MutableValue {
        val v = MutableValueFloat()
        v.value = this.value
        v.exists = this.exists
        return v
    }

    override fun equalsSameType(other: Any?): Boolean {
        assert(exists || 0.0f == value)
        val b = other as MutableValueFloat
        return value == b.value && exists == b.exists
    }

    override fun compareSameType(other: Any?): Int {
        assert(exists || 0.0f == value)
        val b = other as MutableValueFloat
        val c = value.compareTo(b.value)
        if (c != 0) return c
        if (exists == b.exists) return 0
        return if (exists) 1 else -1
    }

    override fun hashCode(): Int {
        assert(exists || 0.0f == value)
        return value.toBits()
    }
}
