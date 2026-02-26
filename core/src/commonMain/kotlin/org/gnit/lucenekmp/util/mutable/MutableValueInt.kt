package org.gnit.lucenekmp.util.mutable

import org.gnit.lucenekmp.jdkport.assert

/**
 * [MutableValue] implementation of type `int`. When mutating instances of this
 * object, the caller is responsible for ensuring that any instance where `exists` is set
 * to `false` must also `value` set to `0` for proper operation.
 */
class MutableValueInt : MutableValue() {
    var value: Int = 0

    override fun toObject(): Any? {
        assert(exists || 0 == value)
        return if (exists) value else null
    }

    override fun copy(source: MutableValue) {
        val s = source as MutableValueInt
        value = s.value
        exists = s.exists
    }

    override fun duplicate(): MutableValue {
        val v = MutableValueInt()
        v.value = this.value
        v.exists = this.exists
        return v
    }

    override fun equalsSameType(other: Any?): Boolean {
        assert(exists || 0 == value)
        val b = other as MutableValueInt
        return value == b.value && exists == b.exists
    }

    override fun compareSameType(other: Any?): Int {
        assert(exists || 0 == value)
        val b = other as MutableValueInt
        val ai = value
        val bi = b.value
        if (ai < bi) return -1
        else if (ai > bi) return 1

        if (exists == b.exists) return 0
        return if (exists) 1 else -1
    }

    override fun hashCode(): Int {
        assert(exists || 0 == value)
        // TODO: if used in HashMap, it already mixes the value... maybe use a straight value?
        return (value shr 8) + (value shr 16)
    }
}
