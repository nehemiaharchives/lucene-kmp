package org.gnit.lucenekmp.util.mutable

import org.gnit.lucenekmp.jdkport.assert

/**
 * [MutableValue] implementation of type `boolean`. When mutating instances of this
 * object, the caller is responsible for ensuring that any instance where `exists` is set
 * to `false` must also `value` set to `false` for proper
 * operation.
 */
class MutableValueBool : MutableValue() {
    var value: Boolean = false

    override fun toObject(): Any? {
        assert(exists || (false == value))
        return if (exists) value else null
    }

    override fun copy(source: MutableValue) {
        val s = source as MutableValueBool
        value = s.value
        exists = s.exists
    }

    override fun duplicate(): MutableValue {
        val v = MutableValueBool()
        v.value = this.value
        v.exists = this.exists
        return v
    }

    override fun equalsSameType(other: Any?): Boolean {
        assert(exists || (false == value))
        val b = other as MutableValueBool
        return value == b.value && exists == b.exists
    }

    override fun compareSameType(other: Any?): Int {
        assert(exists || (false == value))
        val b = other as MutableValueBool
        if (value != b.value) return if (value) 1 else -1
        if (exists == b.exists) return 0
        return if (exists) 1 else -1
    }

    override fun hashCode(): Int {
        assert(exists || (false == value))
        return if (value) 2 else (if (exists) 1 else 0)
    }
}
