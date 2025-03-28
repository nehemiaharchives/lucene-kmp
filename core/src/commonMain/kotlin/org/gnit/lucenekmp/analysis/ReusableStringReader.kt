package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.jdkport.Reader
import kotlin.math.min

/**
 * Internal class to enable reuse of the string reader by [ ][Analyzer.tokenStream]
 */
class ReusableStringReader : Reader() {
    private var pos = 0
    private var size = 0
    private var s: String? = null

    fun setValue(s: String) {
        this.s = s
        this.size = s.length
        this.pos = 0
    }

    override fun read(): Int {
        if (pos < size) {
            return s!![pos++].code
        } else {
            s = null
            return -1
        }
    }

    override fun read(c: CharArray, off: Int, len: Int): Int {
        var len = len
        if (pos < size) {
            len = min(len, size - pos)
            s!!.toCharArray(c, off, pos, pos + len)
            pos += len
            return len
        } else {
            s = null
            return -1
        }
    }

    override fun close() {
        pos = size // this prevents NPE when reading after close!
        s = null
    }
}
