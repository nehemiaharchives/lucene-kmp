package org.gnit.lucenekmp.analysis.fa

import okio.IOException
import org.gnit.lucenekmp.analysis.CharFilter
import org.gnit.lucenekmp.jdkport.Reader

/** CharFilter that replaces instances of Zero-width non-joiner with an ordinary space. */
class PersianCharFilter(`in`: Reader) : CharFilter(`in`) {
    @Throws(IOException::class)
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        val charsRead = input.read(cbuf, off, len)
        if (charsRead > 0) {
            val end = off + charsRead
            var i = off
            while (i < end) {
                if (cbuf[i] == '\u200C') {
                    cbuf[i] = ' '
                }
                i++
            }
        }
        return charsRead
    }

    // optimized impl: some other charfilters consume with read()
    @Throws(IOException::class)
    override fun read(): Int {
        val ch = input.read()
        return if (ch == '\u200C'.code) {
            ' '.code
        } else {
            ch
        }
    }

    override fun correct(currentOff: Int): Int {
        return currentOff // we don't change the length of the string
    }
}

