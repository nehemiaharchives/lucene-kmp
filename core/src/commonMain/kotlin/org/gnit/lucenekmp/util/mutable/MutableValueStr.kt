package org.gnit.lucenekmp.util.mutable

import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.jdkport.assert

/**
 * [MutableValue] implementation of type [String]. When mutating instances of this
 * object, the caller is responsible for ensuring that any instance where `exists` is set
 * to `false` must also have a `value` with a length set to 0.
 */
class MutableValueStr : MutableValue() {
    var value: BytesRefBuilder = BytesRefBuilder()

    override fun toObject(): Any? {
        assert(exists || 0 == value.length())
        return if (exists) value.get().utf8ToString() else null
    }

    override fun copy(source: MutableValue) {
        val s = source as MutableValueStr
        exists = s.exists
        value.copyBytes(s.value)
    }

    override fun duplicate(): MutableValue {
        val v = MutableValueStr()
        v.value.copyBytes(value)
        v.exists = this.exists
        return v
    }

    override fun equalsSameType(other: Any?): Boolean {
        assert(exists || 0 == value.length())
        val b = other as MutableValueStr
        return value.get() == b.value.get() && exists == b.exists
    }

    override fun compareSameType(other: Any?): Int {
        assert(exists || 0 == value.length())
        val b = other as MutableValueStr
        val c = value.get().compareTo(b.value.get())
        if (c != 0) return c
        if (exists == b.exists) return 0
        return if (exists) 1 else -1
    }

    override fun hashCode(): Int {
        assert(exists || 0 == value.length())
        return value.get().hashCode()
    }
}
