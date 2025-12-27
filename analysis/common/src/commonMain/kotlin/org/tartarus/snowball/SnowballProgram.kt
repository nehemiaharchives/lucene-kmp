package org.tartarus.snowball

/** Base class for a snowball stemmer. */
open class SnowballProgram protected constructor() {
    // current string
    private var current: CharArray = charArrayOf()

    protected var cursor: Int = 0
    protected var length: Int = 0
    protected var limit: Int = 0
    protected var limit_backward: Int = 0
    protected var bra: Int = 0
    protected var ket: Int = 0

    init {
        setCurrent("")
    }

    constructor(other: SnowballProgram) : this() {
        current = other.current
        cursor = other.cursor
        length = other.length
        limit = other.limit
        limit_backward = other.limit_backward
        bra = other.bra
        ket = other.ket
    }

    /** Set the current string. */
    fun setCurrent(value: String) {
        setCurrent(value.toCharArray(), value.length)
    }

    /** Get the current string. */
    fun getCurrent(): String {
        return current.concatToString(0, length)
    }

    /**
     * Set the current string.
     *
     * @param text character array containing input
     * @param length valid length of text.
     */
    fun setCurrent(text: CharArray, length: Int) {
        current = text
        cursor = 0
        this.length = length
        limit = length
        limit_backward = 0
        bra = cursor
        ket = limit
    }

    /** Get the current buffer containing the stem. */
    fun getCurrentBuffer(): CharArray {
        return current
    }

    /** Get the valid length of the current buffer. */
    fun getCurrentBufferLength(): Int {
        return length
    }

    protected fun copy_from(other: SnowballProgram) {
        current = other.current
        cursor = other.cursor
        length = other.length
        limit = other.limit
        limit_backward = other.limit_backward
        bra = other.bra
        ket = other.ket
    }

    protected fun in_grouping(s: CharArray, min: Int, max: Int): Boolean {
        if (cursor >= limit) return false
        var ch = current[cursor]
        if (ch > max.toChar() || ch < min.toChar()) return false
        ch = (ch.code - min).toChar()
        if ((s[ch.code shr 3].code and (0x1 shl (ch.code and 0x7))) == 0) return false
        cursor++
        return true
    }

    protected fun in_grouping_b(s: CharArray, min: Int, max: Int): Boolean {
        if (cursor <= limit_backward) return false
        var ch = current[cursor - 1]
        if (ch > max.toChar() || ch < min.toChar()) return false
        ch = (ch.code - min).toChar()
        if ((s[ch.code shr 3].code and (0x1 shl (ch.code and 0x7))) == 0) return false
        cursor--
        return true
    }

    protected fun out_grouping(s: CharArray, min: Int, max: Int): Boolean {
        if (cursor >= limit) return false
        var ch = current[cursor]
        if (ch > max.toChar() || ch < min.toChar()) {
            cursor++
            return true
        }
        ch = (ch.code - min).toChar()
        if ((s[ch.code shr 3].code and (0x1 shl (ch.code and 0x7))) == 0) {
            cursor++
            return true
        }
        return false
    }

    protected fun out_grouping_b(s: CharArray, min: Int, max: Int): Boolean {
        if (cursor <= limit_backward) return false
        var ch = current[cursor - 1]
        if (ch > max.toChar() || ch < min.toChar()) {
            cursor--
            return true
        }
        ch = (ch.code - min).toChar()
        if ((s[ch.code shr 3].code and (0x1 shl (ch.code and 0x7))) == 0) {
            cursor--
            return true
        }
        return false
    }

    protected fun eq_s(s: CharSequence): Boolean {
        if (limit - cursor < s.length) return false
        for (i in 0 until s.length) {
            if (current[cursor + i] != s[i]) return false
        }
        cursor += s.length
        return true
    }

    protected fun eq_s_b(s: CharSequence): Boolean {
        if (cursor - limit_backward < s.length) return false
        for (i in 0 until s.length) {
            if (current[cursor - s.length + i] != s[i]) return false
        }
        cursor -= s.length
        return true
    }

    protected fun find_among(v: Array<Among>): Int {
        var i = 0
        var j = v.size

        val c = cursor
        val l = limit

        var common_i = 0
        var common_j = 0

        var first_key_inspected = false

        while (true) {
            val k = i + ((j - i) shr 1)
            var diff = 0
            var common = if (common_i < common_j) common_i else common_j
            val w = v[k]
            var i2 = common
            while (i2 < w.s.size) {
                if (c + common == l) {
                    diff = -1
                    break
                }
                diff = current[c + common].code - w.s[i2].code
                if (diff != 0) break
                common++
                i2++
            }
            if (diff < 0) {
                j = k
                common_j = common
            } else {
                i = k
                common_i = common
            }
            if (j - i <= 1) {
                if (i > 0) break
                if (j == i) break
                if (first_key_inspected) break
                first_key_inspected = true
            }
        }
        while (true) {
            val w = v[i]
            if (common_i >= w.s.size) {
                cursor = c + w.s.size
                val method = w.method
                if (method == null) return w.result
                val res = method(this)
                cursor = c + w.s.size
                if (res) return w.result
            }
            i = w.substringI
            if (i < 0) return 0
        }
    }

    protected fun find_among_b(v: Array<Among>): Int {
        var i = 0
        var j = v.size

        val c = cursor
        val lb = limit_backward

        var common_i = 0
        var common_j = 0

        var first_key_inspected = false

        while (true) {
            val k = i + ((j - i) shr 1)
            var diff = 0
            var common = if (common_i < common_j) common_i else common_j
            val w = v[k]
            var i2 = w.s.size - 1 - common
            while (i2 >= 0) {
                if (c - common == lb) {
                    diff = -1
                    break
                }
                diff = current[c - 1 - common].code - w.s[i2].code
                if (diff != 0) break
                common++
                i2--
            }
            if (diff < 0) {
                j = k
                common_j = common
            } else {
                i = k
                common_i = common
            }
            if (j - i <= 1) {
                if (i > 0) break
                if (j == i) break
                if (first_key_inspected) break
                first_key_inspected = true
            }
        }
        while (true) {
            val w = v[i]
            if (common_i >= w.s.size) {
                cursor = c - w.s.size
                val method = w.method
                if (method == null) return w.result
                val res = method(this)
                cursor = c - w.s.size
                if (res) return w.result
            }
            i = w.substringI
            if (i < 0) return 0
        }
    }

    protected fun replace_s(c_bra: Int, c_ket: Int, s: CharSequence): Int {
        val adjustment = s.length - (c_ket - c_bra)
        val newLength = length + adjustment
        if (newLength > current.size) {
            current = current.copyOf(newLength)
        }
        if (adjustment != 0 && c_ket < length) {
            current.copyInto(current, c_bra + s.length, c_ket, length)
        }
        for (i in 0 until s.length) {
            current[c_bra + i] = s[i]
        }
        length += adjustment
        limit += adjustment
        if (cursor >= c_ket) {
            cursor += adjustment
        } else if (cursor > c_bra) {
            cursor = c_bra
        }
        return adjustment
    }

    protected fun slice_check() {
        check(bra >= 0) { "bra=$bra" }
        check(bra <= ket) { "bra=$bra,ket=$ket" }
        check(limit <= length) { "limit=$limit,length=$length" }
        check(ket <= limit) { "ket=$ket,limit=$limit" }
    }

    protected fun slice_from(s: CharSequence) {
        slice_check()
        replace_s(bra, ket, s)
    }

    protected fun slice_del() {
        slice_from("")
    }

    protected fun insert(c_bra: Int, c_ket: Int, s: CharSequence) {
        val adjustment = replace_s(c_bra, c_ket, s)
        if (c_bra <= bra) bra += adjustment
        if (c_bra <= ket) ket += adjustment
    }

    protected fun slice_to(s: StringBuilder) {
        slice_check()
        val len = ket - bra
        s.setLength(0)
        s.appendRange(current, bra, bra + len)
    }

    protected fun assign_to(s: StringBuilder) {
        s.setLength(0)
        s.appendRange(current, 0, limit)
    }
}
