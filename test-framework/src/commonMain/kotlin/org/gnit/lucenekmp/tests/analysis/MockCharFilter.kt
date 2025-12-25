package org.gnit.lucenekmp.tests.analysis

import okio.IOException
import org.gnit.lucenekmp.analysis.CharFilter
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.jdkport.TreeMap
import org.gnit.lucenekmp.jdkport.assert

/**
 * the purpose of this charfilter is to send offsets out of bounds if the analyzer doesn't use
 * correctOffset or does incorrect offset math.
 */
class MockCharFilter(`in`: Reader, val remainder: Int = 0) : CharFilter(`in`) {
    var currentOffset: Int = -1
    var delta: Int = 0
    var bufferedCh: Int = -1

    @Throws(IOException::class)
    override fun read(): Int {
        // we have a buffered character, add an offset correction and return it
        if (bufferedCh >= 0) {
            val ch = bufferedCh
            bufferedCh = -1
            currentOffset++

            addOffCorrectMap(currentOffset, delta - 1)
            delta--
            return ch
        }

        // otherwise actually read one
        val ch: Int = input.read()
        if (ch < 0) return ch

        currentOffset++
        if ((ch % 10) != remainder || Character.isHighSurrogate(ch.toChar())
            || ch.toChar().isLowSurrogate()
        ) {
            return ch
        }

        // we will double this character, so buffer it.
        bufferedCh = ch
        return ch
    }

    @Throws(IOException::class)
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        var numRead = 0
        for (i in off..<off + len) {
            val c = read()
            if (c == -1) break
            cbuf[i] = c.toChar()
            numRead++
        }
        return if (numRead == 0) -1 else numRead
    }

    public override fun correct(currentOff: Int): Int {
        val lastEntry: Map.Entry<Int, Int>? = corrections.lowerEntry(currentOff + 1)
        val ret = if (lastEntry == null) currentOff else currentOff + lastEntry.value
        assert(ret >= 0) { "currentOff=" + currentOff + ",diff=" + (ret - currentOff) }
        return ret
    }

    protected fun addOffCorrectMap(off: Int, cumulativeDiff: Int) {
        corrections[off] = cumulativeDiff
    }

    var corrections: TreeMap<Int, Int> = TreeMap()

    // for testing only
    // for testing only, uses a remainder of 0
    init {
        // TODO: instead of fixed remainder... maybe a fixed
        // random seed?
        require(remainder in 0..<10) { "invalid remainder parameter (must be 0..10): $remainder" }
    }
}
