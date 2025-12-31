package org.gnit.lucenekmp.analysis.cjk

import okio.IOException
import org.gnit.lucenekmp.analysis.charfilter.BaseCharFilter
import org.gnit.lucenekmp.jdkport.Reader

/**
 * A CharFilter that normalizes CJK width differences:
 * - Folds fullwidth ASCII variants into the equivalent basic latin
 * - Folds halfwidth Katakana variants into the equivalent kana
 *
 * NOTE: This char filter is the exact counterpart of CJKWidthFilter.
 */
class CJKWidthCharFilter(`in`: Reader) : BaseCharFilter(`in`) {

    companion object {
        /** halfwidth kana mappings: 0xFF65-0xFF9D */
        private val KANA_NORM: CharArray = charArrayOf(
            0x30fb.toChar(), 0x30f2.toChar(), 0x30a1.toChar(), 0x30a3.toChar(), 0x30a5.toChar(),
            0x30a7.toChar(), 0x30a9.toChar(), 0x30e3.toChar(), 0x30e5.toChar(), 0x30e7.toChar(),
            0x30c3.toChar(), 0x30fc.toChar(), 0x30a2.toChar(), 0x30a4.toChar(), 0x30a6.toChar(),
            0x30a8.toChar(), 0x30aa.toChar(), 0x30ab.toChar(), 0x30ad.toChar(), 0x30af.toChar(),
            0x30b1.toChar(), 0x30b3.toChar(), 0x30b5.toChar(), 0x30b7.toChar(), 0x30b9.toChar(),
            0x30bb.toChar(), 0x30bd.toChar(), 0x30bf.toChar(), 0x30c1.toChar(), 0x30c4.toChar(),
            0x30c6.toChar(), 0x30c8.toChar(), 0x30ca.toChar(), 0x30cb.toChar(), 0x30cc.toChar(),
            0x30cd.toChar(), 0x30ce.toChar(), 0x30cf.toChar(), 0x30d2.toChar(), 0x30d5.toChar(),
            0x30d8.toChar(), 0x30db.toChar(), 0x30de.toChar(), 0x30df.toChar(), 0x30e0.toChar(),
            0x30e1.toChar(), 0x30e2.toChar(), 0x30e4.toChar(), 0x30e6.toChar(), 0x30e8.toChar(),
            0x30e9.toChar(), 0x30ea.toChar(), 0x30eb.toChar(), 0x30ec.toChar(), 0x30ed.toChar(),
            0x30ef.toChar(), 0x30f3.toChar(), 0x3099.toChar(), 0x309a.toChar()
        )

        /** kana combining diffs: 0x30A6-0x30FD */
        private val KANA_COMBINE_VOICED: ByteArray = byteArrayOf(
            78, 0, 0, 0, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1,
            0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 1,
            0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 8, 8, 8, 8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1
        )

        private val KANA_COMBINE_SEMI_VOICED: ByteArray = byteArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 2, 0, 0, 2,
            0, 0, 2, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        )

        private const val HW_KATAKANA_VOICED_MARK: Int = 0xFF9E
        private const val HW_KATAKANA_SEMI_VOICED_MARK: Int = 0xFF9F
    }

    private var prevChar: Int = -1
    private var inputOff: Int = 0

    @Throws(IOException::class)
    override fun read(): Int {
        while (true) {
            val ch: Int = input.read()
            if (ch == -1) {
                val ret = prevChar
                prevChar = ch
                return ret
            }

            inputOff++
            var ret = -1

            if (ch == HW_KATAKANA_SEMI_VOICED_MARK || ch == HW_KATAKANA_VOICED_MARK) {
                val combinedChar = combineVoiceMark(prevChar, ch)
                if (prevChar != combinedChar) {
                    prevChar = -1
                    val prevCumulativeDiff = getLastCumulativeDiff()
                    addOffCorrectMap(inputOff - 1 - prevCumulativeDiff, prevCumulativeDiff + 1)
                    return combinedChar
                }
            }

            if (prevChar != -1) {
                ret = prevChar
            }

            prevChar = when {
                ch in 0xFF01..0xFF5E -> ch - 0xFEE0
                ch in 0xFF65..0xFF9F -> KANA_NORM[ch - 0xFF65].code
                else -> ch
            }

            if (ret != -1) {
                return ret
            }
        }
    }

    private fun combineVoiceMark(ch: Int, voiceMark: Int): Int {
        var c = ch
        if (c in 0x30A6..0x30FD) {
            c +=
                if (voiceMark == HW_KATAKANA_SEMI_VOICED_MARK)
                    KANA_COMBINE_SEMI_VOICED[c - 0x30A6].toInt()
                else
                    KANA_COMBINE_VOICED[c - 0x30A6].toInt()
        }
        return c
    }

    @Throws(IOException::class)
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        var numRead = 0
        var i = off
        while (i < off + len) {
            val c = read()
            if (c == -1) break
            cbuf[i] = c.toChar()
            numRead++
            i++
        }
        return if (numRead == 0) -1 else numRead
    }
}
