package org.gnit.lucenekmp.analysis.util


/** A StringBuilder that allows one to access the array. */
open class OpenStringBuilder : Appendable, CharSequence {
    protected lateinit var buf: CharArray
    protected var len: Int = 0

    constructor() : this(32)

    constructor(size: Int) {
        buf = CharArray(size)
    }

    constructor(arr: CharArray, len: Int) {
        set(arr, len)
    }

    fun setLength(len: Int) {
        this.len = len
    }

    fun set(arr: CharArray, end: Int) {
        this.buf = arr
        this.len = end
    }

    fun getArray(): CharArray {
        return buf
    }

    fun size(): Int {
        return len
    }

    override val length: Int
        get() = len

    fun capacity(): Int {
        return buf.size
    }

    override fun append(csq: CharSequence?): Appendable {
        return append(csq ?: "", 0, (csq ?: "").length)
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): Appendable {
        val seq = csq ?: ""
        reserve(end - start)
        for (i in start until end) {
            unsafeWrite(seq[i])
        }
        return this
    }

    override fun append(c: Char): Appendable {
        write(c)
        return this
    }

    override fun get(index: Int): Char {
        return buf[index]
    }


    fun setCharAt(index: Int, ch: Char) {
        buf[index] = ch
    }

    override fun subSequence(start: Int, end: Int): CharSequence {
        throw UnsupportedOperationException("todo")
    }

    fun unsafeWrite(b: Char) {
        buf[len++] = b
    }

    fun unsafeWrite(b: Int) {
        unsafeWrite(b.toChar())
    }

    fun unsafeWrite(b: CharArray, off: Int, len: Int) {
        org.gnit.lucenekmp.jdkport.System.arraycopy(b, off, buf, this.len, len)
        this.len += len
    }

    protected fun resize(len: Int) {
        val newbuf = CharArray(kotlin.math.max(buf.size shl 1, len))
        org.gnit.lucenekmp.jdkport.System.arraycopy(buf, 0, newbuf, 0, size())
        buf = newbuf
    }

    fun reserve(num: Int) {
        if (len + num > buf.size) resize(len + num)
    }

    fun write(b: Char) {
        if (len >= buf.size) {
            resize(len + 1)
        }
        unsafeWrite(b)
    }

    fun write(b: Int) {
        write(b.toChar())
    }

    fun write(b: CharArray) {
        write(b, 0, b.size)
    }

    fun write(b: CharArray, off: Int, len: Int) {
        reserve(len)
        unsafeWrite(b, off, len)
    }

    fun write(arr: OpenStringBuilder) {
        write(arr.buf, 0, len)
    }

    fun write(s: String) {
        reserve(s.length)
        val chars = s.toCharArray()
        org.gnit.lucenekmp.jdkport.System.arraycopy(chars, 0, buf, len, chars.size)
        len += chars.size
    }

    fun flush() {}

    fun reset() {
        len = 0
    }

    fun toCharArray(): CharArray {
        val newbuf = CharArray(size())
        org.gnit.lucenekmp.jdkport.System.arraycopy(buf, 0, newbuf, 0, size())
        return newbuf
    }

    override fun toString(): String {
        return buf.concatToString(0, len)
    }
}
