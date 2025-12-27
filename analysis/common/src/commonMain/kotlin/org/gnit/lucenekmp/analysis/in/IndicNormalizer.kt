package org.gnit.lucenekmp.analysis.`in`

import org.gnit.lucenekmp.analysis.util.StemmerUtil.delete
import org.gnit.lucenekmp.jdkport.BitSet

/**
 * Normalizes the Unicode representation of text in Indian languages.
 *
 * Follows guidelines from Unicode 5.2, chapter 6, South Asian Scripts I and graphical
 * decompositions from http://ldc.upenn.edu/myl/IndianScriptsUnicode.html
 */
internal class IndicNormalizer {
    private class ScriptData(val flag: Int, val base: Int) {
        lateinit var decompMask: BitSet
    }

    private enum class IndicBlock(val base: Int, val end: Int) {
        DEVANAGARI(0x0900, 0x097F),
        BENGALI(0x0980, 0x09FF),
        GURMUKHI(0x0A00, 0x0A7F),
        GUJARATI(0x0A80, 0x0AFF),
        ORIYA(0x0B00, 0x0B7F),
        TAMIL(0x0B80, 0x0BFF),
        TELUGU(0x0C00, 0x0C7F),
        KANNADA(0x0C80, 0x0CFF),
        MALAYALAM(0x0D00, 0x0D7F);

        companion object {
            fun of(ch: Char): IndicBlock? {
                val code = ch.code
                return when {
                    code in DEVANAGARI.base..DEVANAGARI.end -> DEVANAGARI
                    code in BENGALI.base..BENGALI.end -> BENGALI
                    code in GURMUKHI.base..GURMUKHI.end -> GURMUKHI
                    code in GUJARATI.base..GUJARATI.end -> GUJARATI
                    code in ORIYA.base..ORIYA.end -> ORIYA
                    code in TAMIL.base..TAMIL.end -> TAMIL
                    code in TELUGU.base..TELUGU.end -> TELUGU
                    code in KANNADA.base..KANNADA.end -> KANNADA
                    code in MALAYALAM.base..MALAYALAM.end -> MALAYALAM
                    else -> null
                }
            }
        }
    }

    companion object {
        private val scripts: MutableMap<IndicBlock, ScriptData> = mutableMapOf(
            IndicBlock.DEVANAGARI to ScriptData(1, 0x0900),
            IndicBlock.BENGALI to ScriptData(2, 0x0980),
            IndicBlock.GURMUKHI to ScriptData(4, 0x0A00),
            IndicBlock.GUJARATI to ScriptData(8, 0x0A80),
            IndicBlock.ORIYA to ScriptData(16, 0x0B00),
            IndicBlock.TAMIL to ScriptData(32, 0x0B80),
            IndicBlock.TELUGU to ScriptData(64, 0x0C00),
            IndicBlock.KANNADA to ScriptData(128, 0x0C80),
            IndicBlock.MALAYALAM to ScriptData(256, 0x0D00)
        )

        private fun flag(block: IndicBlock): Int {
            return scripts[block]!!.flag
        }

        /**
         * Decompositions according to Unicode 5.2, and http://ldc.upenn.edu/myl/IndianScriptsUnicode.html
         *
         * Most of these are not handled by unicode normalization anyway.
         *
         * The numbers here represent offsets into the respective codepages, with -1 representing null
         * and 0xFF representing zero-width joiner.
         */
        private val decompositions: Array<IntArray> = arrayOf(
            /* devanagari, gujarati vowel candra O */
            intArrayOf(0x05, 0x3E, 0x45, 0x11, flag(IndicBlock.DEVANAGARI) or flag(IndicBlock.GUJARATI)),
            /* devanagari short O */
            intArrayOf(0x05, 0x3E, 0x46, 0x12, flag(IndicBlock.DEVANAGARI)),
            /* devanagari, gujarati letter O */
            intArrayOf(0x05, 0x3E, 0x47, 0x13, flag(IndicBlock.DEVANAGARI) or flag(IndicBlock.GUJARATI)),
            /* devanagari letter AI, gujarati letter AU */
            intArrayOf(0x05, 0x3E, 0x48, 0x14, flag(IndicBlock.DEVANAGARI) or flag(IndicBlock.GUJARATI)),
            /* devanagari, bengali, gurmukhi, gujarati, oriya AA */
            intArrayOf(
                0x05,
                0x3E,
                -1,
                0x06,
                flag(IndicBlock.DEVANAGARI) or flag(IndicBlock.BENGALI) or flag(IndicBlock.GURMUKHI) or
                    flag(IndicBlock.GUJARATI) or flag(IndicBlock.ORIYA)
            ),
            /* devanagari letter candra A */
            intArrayOf(0x05, 0x45, -1, 0x72, flag(IndicBlock.DEVANAGARI)),
            /* gujarati vowel candra E */
            intArrayOf(0x05, 0x45, -1, 0x0D, flag(IndicBlock.GUJARATI)),
            /* devanagari letter short A */
            intArrayOf(0x05, 0x46, -1, 0x04, flag(IndicBlock.DEVANAGARI)),
            /* gujarati letter E */
            intArrayOf(0x05, 0x47, -1, 0x0F, flag(IndicBlock.GUJARATI)),
            /* gurmukhi, gujarati letter AI */
            intArrayOf(0x05, 0x48, -1, 0x10, flag(IndicBlock.GURMUKHI) or flag(IndicBlock.GUJARATI)),
            /* devanagari, gujarati vowel candra O */
            intArrayOf(0x05, 0x49, -1, 0x11, flag(IndicBlock.DEVANAGARI) or flag(IndicBlock.GUJARATI)),
            /* devanagari short O */
            intArrayOf(0x05, 0x4A, -1, 0x12, flag(IndicBlock.DEVANAGARI)),
            /* devanagari, gujarati letter O */
            intArrayOf(0x05, 0x4B, -1, 0x13, flag(IndicBlock.DEVANAGARI) or flag(IndicBlock.GUJARATI)),
            /* devanagari letter AI, gurmukhi letter AU, gujarati letter AU */
            intArrayOf(
                0x05,
                0x4C,
                -1,
                0x14,
                flag(IndicBlock.DEVANAGARI) or flag(IndicBlock.GURMUKHI) or flag(IndicBlock.GUJARATI)
            ),
            /* devanagari, gujarati vowel candra O */
            intArrayOf(0x06, 0x45, -1, 0x11, flag(IndicBlock.DEVANAGARI) or flag(IndicBlock.GUJARATI)),
            /* devanagari short O */
            intArrayOf(0x06, 0x46, -1, 0x12, flag(IndicBlock.DEVANAGARI)),
            /* devanagari, gujarati letter O */
            intArrayOf(0x06, 0x47, -1, 0x13, flag(IndicBlock.DEVANAGARI) or flag(IndicBlock.GUJARATI)),
            /* devanagari letter AI, gujarati letter AU */
            intArrayOf(0x06, 0x48, -1, 0x14, flag(IndicBlock.DEVANAGARI) or flag(IndicBlock.GUJARATI)),
            /* malayalam letter II */
            intArrayOf(0x07, 0x57, -1, 0x08, flag(IndicBlock.MALAYALAM)),
            /* devanagari letter UU */
            intArrayOf(0x09, 0x41, -1, 0x0A, flag(IndicBlock.DEVANAGARI)),
            /* tamil, malayalam letter UU (some styles) */
            intArrayOf(0x09, 0x57, -1, 0x0A, flag(IndicBlock.TAMIL) or flag(IndicBlock.MALAYALAM)),
            /* malayalam letter AI */
            intArrayOf(0x0E, 0x46, -1, 0x10, flag(IndicBlock.MALAYALAM)),
            /* devanagari candra E */
            intArrayOf(0x0F, 0x45, -1, 0x0D, flag(IndicBlock.DEVANAGARI)),
            /* devanagari short E */
            intArrayOf(0x0F, 0x46, -1, 0x0E, flag(IndicBlock.DEVANAGARI)),
            /* devanagari AI */
            intArrayOf(0x0F, 0x47, -1, 0x10, flag(IndicBlock.DEVANAGARI)),
            /* oriya AI */
            intArrayOf(0x0F, 0x57, -1, 0x10, flag(IndicBlock.ORIYA)),
            /* malayalam letter OO */
            intArrayOf(0x12, 0x3E, -1, 0x13, flag(IndicBlock.MALAYALAM)),
            /* telugu, kannada letter AU */
            intArrayOf(0x12, 0x4C, -1, 0x14, flag(IndicBlock.TELUGU) or flag(IndicBlock.KANNADA)),
            /* telugu letter OO */
            intArrayOf(0x12, 0x55, -1, 0x13, flag(IndicBlock.TELUGU)),
            /* tamil, malayalam letter AU */
            intArrayOf(0x12, 0x57, -1, 0x14, flag(IndicBlock.TAMIL) or flag(IndicBlock.MALAYALAM)),
            /* oriya letter AU */
            intArrayOf(0x13, 0x57, -1, 0x14, flag(IndicBlock.ORIYA)),
            /* devanagari qa */
            intArrayOf(0x15, 0x3C, -1, 0x58, flag(IndicBlock.DEVANAGARI)),
            /* devanagari, gurmukhi khha */
            intArrayOf(0x16, 0x3C, -1, 0x59, flag(IndicBlock.DEVANAGARI) or flag(IndicBlock.GURMUKHI)),
            /* devanagari, gurmukhi ghha */
            intArrayOf(0x17, 0x3C, -1, 0x5A, flag(IndicBlock.DEVANAGARI) or flag(IndicBlock.GURMUKHI)),
            /* devanagari, gurmukhi za */
            intArrayOf(0x1C, 0x3C, -1, 0x5B, flag(IndicBlock.DEVANAGARI) or flag(IndicBlock.GURMUKHI)),
            /* devanagari dddha, bengali, oriya rra */
            intArrayOf(0x21, 0x3C, -1, 0x5C, flag(IndicBlock.DEVANAGARI) or flag(IndicBlock.BENGALI) or flag(IndicBlock.ORIYA)),
            /* devanagari, bengali, oriya rha */
            intArrayOf(0x22, 0x3C, -1, 0x5D, flag(IndicBlock.DEVANAGARI) or flag(IndicBlock.BENGALI) or flag(IndicBlock.ORIYA)),
            /* malayalam chillu nn */
            intArrayOf(0x23, 0x4D, 0xFF, 0x7A, flag(IndicBlock.MALAYALAM)),
            /* bengali khanda ta */
            intArrayOf(0x24, 0x4D, 0xFF, 0x4E, flag(IndicBlock.BENGALI)),
            /* devanagari nnna */
            intArrayOf(0x28, 0x3C, -1, 0x29, flag(IndicBlock.DEVANAGARI)),
            /* malayalam chillu n */
            intArrayOf(0x28, 0x4D, 0xFF, 0x7B, flag(IndicBlock.MALAYALAM)),
            /* devanagari, gurmukhi fa */
            intArrayOf(0x2B, 0x3C, -1, 0x5E, flag(IndicBlock.DEVANAGARI) or flag(IndicBlock.GURMUKHI)),
            /* devanagari, bengali yya */
            intArrayOf(0x2F, 0x3C, -1, 0x5F, flag(IndicBlock.DEVANAGARI) or flag(IndicBlock.BENGALI)),
            /* telugu letter vocalic R */
            intArrayOf(0x2C, 0x41, 0x41, 0x0B, flag(IndicBlock.TELUGU)),
            /* devanagari rra */
            intArrayOf(0x30, 0x3C, -1, 0x31, flag(IndicBlock.DEVANAGARI)),
            /* malayalam chillu rr */
            intArrayOf(0x30, 0x4D, 0xFF, 0x7C, flag(IndicBlock.MALAYALAM)),
            /* malayalam chillu l */
            intArrayOf(0x32, 0x4D, 0xFF, 0x7D, flag(IndicBlock.MALAYALAM)),
            /* devanagari llla */
            intArrayOf(0x33, 0x3C, -1, 0x34, flag(IndicBlock.DEVANAGARI)),
            /* malayalam chillu ll */
            intArrayOf(0x33, 0x4D, 0xFF, 0x7E, flag(IndicBlock.MALAYALAM)),
            /* telugu letter MA */
            intArrayOf(0x35, 0x41, -1, 0x2E, flag(IndicBlock.TELUGU)),
            /* devanagari, gujarati vowel sign candra O */
            intArrayOf(0x3E, 0x45, -1, 0x49, flag(IndicBlock.DEVANAGARI) or flag(IndicBlock.GUJARATI)),
            /* devanagari vowel sign short O */
            intArrayOf(0x3E, 0x46, -1, 0x4A, flag(IndicBlock.DEVANAGARI)),
            /* devanagari, gujarati vowel sign O */
            intArrayOf(0x3E, 0x47, -1, 0x4B, flag(IndicBlock.DEVANAGARI) or flag(IndicBlock.GUJARATI)),
            /* devanagari, gujarati vowel sign AU */
            intArrayOf(0x3E, 0x48, -1, 0x4C, flag(IndicBlock.DEVANAGARI) or flag(IndicBlock.GUJARATI)),
            /* kannada vowel sign II */
            intArrayOf(0x3F, 0x55, -1, 0x40, flag(IndicBlock.KANNADA)),
            /* gurmukhi vowel sign UU (when stacking) */
            intArrayOf(0x41, 0x41, -1, 0x42, flag(IndicBlock.GURMUKHI)),
            /* tamil, malayalam vowel sign O */
            intArrayOf(0x46, 0x3E, -1, 0x4A, flag(IndicBlock.TAMIL) or flag(IndicBlock.MALAYALAM)),
            /* kannada vowel sign OO */
            intArrayOf(0x46, 0x42, 0x55, 0x4B, flag(IndicBlock.KANNADA)),
            /* kannada vowel sign O */
            intArrayOf(0x46, 0x42, -1, 0x4A, flag(IndicBlock.KANNADA)),
            /* malayalam vowel sign AI (if reordered twice) */
            intArrayOf(0x46, 0x46, -1, 0x48, flag(IndicBlock.MALAYALAM)),
            /* telugu, kannada vowel sign EE */
            intArrayOf(0x46, 0x55, -1, 0x47, flag(IndicBlock.TELUGU) or flag(IndicBlock.KANNADA)),
            /* telugu, kannada vowel sign AI */
            intArrayOf(0x46, 0x56, -1, 0x48, flag(IndicBlock.TELUGU) or flag(IndicBlock.KANNADA)),
            /* tamil, malayalam vowel sign AU */
            intArrayOf(0x46, 0x57, -1, 0x4C, flag(IndicBlock.TAMIL) or flag(IndicBlock.MALAYALAM)),
            /* bengali, oriya vowel sign O, tamil, malayalam vowel sign OO */
            intArrayOf(0x47, 0x3E, -1, 0x4B, flag(IndicBlock.BENGALI) or flag(IndicBlock.ORIYA) or flag(IndicBlock.TAMIL) or flag(IndicBlock.MALAYALAM)),
            /* bengali, oriya vowel sign AU */
            intArrayOf(0x47, 0x57, -1, 0x4C, flag(IndicBlock.BENGALI) or flag(IndicBlock.ORIYA)),
            /* kannada vowel sign OO */
            intArrayOf(0x4A, 0x55, -1, 0x4B, flag(IndicBlock.KANNADA)),
            /* gurmukhi letter I */
            intArrayOf(0x72, 0x3F, -1, 0x07, flag(IndicBlock.GURMUKHI)),
            /* gurmukhi letter II */
            intArrayOf(0x72, 0x40, -1, 0x08, flag(IndicBlock.GURMUKHI)),
            /* gurmukhi letter EE */
            intArrayOf(0x72, 0x47, -1, 0x0F, flag(IndicBlock.GURMUKHI)),
            /* gurmukhi letter U */
            intArrayOf(0x73, 0x41, -1, 0x09, flag(IndicBlock.GURMUKHI)),
            /* gurmukhi letter UU */
            intArrayOf(0x73, 0x42, -1, 0x0A, flag(IndicBlock.GURMUKHI)),
            /* gurmukhi letter OO */
            intArrayOf(0x73, 0x4B, -1, 0x13, flag(IndicBlock.GURMUKHI))
        )

        init {
            for (sd in scripts.values) {
                sd.decompMask = BitSet(0x7F)
                for (i in decompositions.indices) {
                    val ch = decompositions[i][0]
                    val flags = decompositions[i][4]
                    if ((flags and sd.flag) != 0) {
                        sd.decompMask.set(ch)
                    }
                }
            }
        }
    }

    /**
     * Normalizes input text, and returns the new length. The length will always be less than or equal
     * to the existing length.
     */
    fun normalize(text: CharArray, len: Int): Int {
        var length = len
        for (i in 0 until length) {
            val block = IndicBlock.of(text[i]) ?: continue
            val sd = scripts[block] ?: continue
            val ch = text[i].code - sd.base
            if (sd.decompMask.get(ch)) {
                length = compose(ch, block, sd, text, i, length)
            }
        }
        return length
    }

    /** Compose into standard form any compositions in the decompositions table. */
    private fun compose(ch0: Int, block0: IndicBlock, sd: ScriptData, text: CharArray, pos: Int, len: Int): Int {
        if (pos + 1 >= len) return len

        val block1 = IndicBlock.of(text[pos + 1]) ?: return len
        if (block1 != block0) return len

        val ch1 = text[pos + 1].code - sd.base
        var ch2 = -1
        if (pos + 2 < len) {
            if (text[pos + 2] == '\u200D') {
                ch2 = 0xFF
            } else {
                val block2 = IndicBlock.of(text[pos + 2])
                if (block2 == block1) {
                    ch2 = text[pos + 2].code - sd.base
                }
            }
        }

        for (i in decompositions.indices) {
            if (decompositions[i][0] == ch0 && (decompositions[i][4] and sd.flag) != 0) {
                if (decompositions[i][1] == ch1 && (decompositions[i][2] < 0 || decompositions[i][2] == ch2)) {
                    text[pos] = (sd.base + decompositions[i][3]).toChar()
                    var length = delete(text, pos + 1, len)
                    if (decompositions[i][2] >= 0) {
                        length = delete(text, pos + 1, length)
                    }
                    return length
                }
            }
        }

        return len
    }
}
