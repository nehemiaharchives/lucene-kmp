package org.gnit.lucenekmp.jdkport

import org.gnit.lucenekmp.jdkport.Objects.checkFromToIndex

@Ported(from = "jdk.internal.util.regex.Grapheme")
object Grapheme {
    /**
     * Look for the next extended grapheme cluster boundary in a CharSequence.
     * It assumes the start of the char sequence at offset `off` is a boundary.
     *
     *
     * See Unicode Standard Annex #29 Unicode Text Segmentation for the specification
     * for the extended grapheme cluster boundary rules. The following implementation
     * is based on the annex for Unicode version 15.1.
     *
     * @spec http://www.unicode.org/reports/tr29/tr29-43.html
     * @param src the `CharSequence` to be scanned
     * @param off offset to start looking for the next boundary in the src
     * @param limit limit offset in the src (exclusive)
     * @return the next grapheme boundary
     */
    fun nextBoundary(src: CharSequence, off: Int, limit: Int): Int {
        checkFromToIndex(off, limit, src.length)

        var ch0 = Character.codePointAt(src, off)
        var ret = off + Character.charCount(ch0)
        // indicates whether gb11 or gb12 is underway
        var t0 = getType(ch0)
        var riCount = if (t0 == RI) 1 else 0
        var gb11 = t0 == EXTENDED_PICTOGRAPHIC
        while (ret < limit) {
            val ch1 = Character.codePointAt(src, ret)
            val t1 = getType(ch1)

            // GB9c
            if (IndicConjunctBreak.isConsonant(ch0)) {
                val advance = checkIndicConjunctBreak(src, ret, limit)
                if (advance >= 0) {
                    ret += advance
                    continue
                }
            }

            if (gb11 && t0 == ZWJ && t1 == EXTENDED_PICTOGRAPHIC) {
                // continue for gb11
            } else if (riCount % 2 == 1 && t0 == RI && t1 == RI) {
                // continue for gb12
            } else if (rules[t0]!![t1]) {
                if (ret > off) {
                    break
                } else {
                    gb11 = t1 == EXTENDED_PICTOGRAPHIC
                    riCount = 0
                }
            }

            riCount += if (t1 == RI) 1 else 0
            ch0 = ch1
            t0 = t1

            ret += Character.charCount(ch1)
        }
        return ret
    }

    // types
    private const val OTHER = 0
    private const val CR = 1
    private const val LF = 2
    private const val CONTROL = 3
    private const val EXTEND = 4
    private const val ZWJ = 5
    private const val RI = 6
    private const val PREPEND = 7
    private const val SPACINGMARK = 8
    private const val L = 9
    private const val V = 10
    private const val T = 11
    private const val LV = 12
    private const val LVT = 13
    private const val EXTENDED_PICTOGRAPHIC = 14

    private const val FIRST_TYPE = 0
    private const val LAST_TYPE = 14

    private val rules: Array<BooleanArray?>

    init {
        rules = Array<BooleanArray?>(LAST_TYPE + 1) { BooleanArray(LAST_TYPE + 1) }
        // GB 999 Any + Any  -> default
        for (i in FIRST_TYPE..LAST_TYPE) for (j in FIRST_TYPE..LAST_TYPE) rules[i]!![j] = true
        // GB 6 L x (L | V | LV | VT)
        rules[L]!![L] = false
        rules[L]!![V] = false
        rules[L]!![LV] = false
        rules[L]!![LVT] = false
        // GB 7 (LV | V) x (V | T)
        rules[LV]!![V] = false
        rules[LV]!![T] = false
        rules[V]!![V] = false
        rules[V]!![T] = false
        // GB 8 (LVT | T) x T
        rules[LVT]!![T] = false
        rules[T]!![T] = false
        // GB 9 x (Extend|ZWJ)
        // GB 9a x Spacing Mark
        // GB 9b Prepend x
        for (i in FIRST_TYPE..LAST_TYPE) {
            rules[i]!![EXTEND] = false
            rules[i]!![ZWJ] = false
            rules[i]!![SPACINGMARK] = false
            rules[PREPEND]!![i] = false
        }
        // GB 4  (Control | CR | LF) +
        // GB 5  + (Control | CR | LF)
        for (i in FIRST_TYPE..LAST_TYPE) for (j in CR..CONTROL) {
            rules[i]!![j] = true
            rules[j]!![i] = true
        }
        // GB 3 CR x LF
        rules[CR]!![LF] = false
        // GB 11 Exended_Pictographic x (Extend|ZWJ)
        rules[EXTENDED_PICTOGRAPHIC]!![EXTEND] = false
        rules[EXTENDED_PICTOGRAPHIC]!![ZWJ] = false
    }

    // Hangul syllables
    private const val SYLLABLE_BASE = 0xAC00
    private const val LCOUNT = 19
    private const val VCOUNT = 21
    private const val TCOUNT = 28
    private val NCOUNT = VCOUNT * TCOUNT // 588
    private val SCOUNT = LCOUNT * NCOUNT // 11172

    // #tr29: SpacingMark exceptions: The following (which have
    // General_Category = Spacing_Mark and would otherwise be included)
    // are specifically excluded
    fun isExcludedSpacingMark(cp: Int): Boolean {
        return cp == 0x102B || cp == 0x102C || cp == 0x1038 || cp >= 0x1062 && cp <= 0x1064 || cp >= 0x1067 && cp <= 0x106D || cp == 0x1083 || cp >= 0x1087 && cp <= 0x108C || cp == 0x108F || cp >= 0x109A && cp <= 0x109C || cp == 0x1A61 || cp == 0x1A63 || cp == 0x1A64 || cp == 0xAA7B || cp == 0xAA7D
    }

    fun getType(cp: Int): Int {
        if (cp < 0x007F) { // ASCII
            if (cp < 32) { // Control characters
                if (cp == 0x000D) return CR
                if (cp == 0x000A) return LF
                return CONTROL
            }
            return OTHER
        }

        if (Character.isExtendedPictographic(cp)) {
            return EXTENDED_PICTOGRAPHIC
        }

        val type: Byte = Character.getType(cp).toByte()
        when (type) {
            Character.UNASSIGNED -> {
                // NOTE: #tr29 lists "Unassigned and Default_Ignorable_Code_Point" as Control
                // but GraphemeBreakTest.txt lists u+0378/reserved-0378 as "Other"
                // so type it as "Other" to make the test happy
                if (cp == 0x0378) return OTHER

                return CONTROL
            }

            Character.CONTROL, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR, Character.SURROGATE -> return CONTROL
            Character.FORMAT -> {
                if (cp == 0x200C ||
                    cp in 0xE0020..0xE007F
                ) return EXTEND
                if (cp == 0x200D) return ZWJ
                if (cp in 0x0600..0x0605 || cp == 0x06DD || cp == 0x070F || cp == 0x0890 || cp == 0x0891 || cp == 0x08E2 || cp == 0x110BD || cp == 0x110CD) return PREPEND
                return CONTROL
            }

            Character.NON_SPACING_MARK, Character.ENCLOSING_MARK ->             // NOTE:
                // #tr29 "plus a few General_Category = Spacing_Mark needed for
                // canonical equivalence."
                // but for "extended grapheme clusters" support, there is no
                // need actually to diff "extend" and "spackmark" given GB9, GB9a
                return EXTEND

            Character.COMBINING_SPACING_MARK -> {
                if (isExcludedSpacingMark(cp)) return OTHER
                // NOTE:
                // 0x11720 and 0x11721 are mentioned in #tr29 as
                // OTHER_LETTER but it appears their category has been updated to
                // COMBING_SPACING_MARK already (verified in ver.8)
                return SPACINGMARK
            }

            Character.OTHER_SYMBOL -> {
                if (cp in 0x1F1E6..0x1F1FF) return RI
                return OTHER
            }

            Character.MODIFIER_LETTER, Character.MODIFIER_SYMBOL -> {
                // WARNING:
                // not mentioned in #tr29 but listed in GraphemeBreakProperty.txt
                if (cp == 0xFF9E || cp == 0xFF9F || cp in 0x1F3FB..0x1F3FF) return EXTEND
                return OTHER
            }

            Character.OTHER_LETTER -> {
                if (cp == 0x0E33 || cp == 0x0EB3) return SPACINGMARK
                // hangul jamo
                if (cp in 0x1100..0x11FF) {
                    if (cp <= 0x115F) return L
                    if (cp <= 0x11A7) return V
                    return T
                }
                // hangul syllables
                val sindex = cp - SYLLABLE_BASE
                if (sindex in 0..<SCOUNT) {
                    if (sindex % TCOUNT == 0) return LV
                    return LVT
                }
                //  hangul jamo_extended A
                if (cp in 0xA960..0xA97C) return L
                //  hangul jamo_extended B
                if (cp in 0xD7B0..0xD7C6) return V
                if (cp in 0xD7CB..0xD7FB) return T

                // Prepend
                when (cp) {
                    0x0D4E, 0x111C2, 0x111C3, 0x1193F, 0x11941, 0x11A3A, 0x11A84, 0x11A85, 0x11A86, 0x11A87, 0x11A88, 0x11A89, 0x11D46, 0x11F02 -> return PREPEND
                }
            }
        }
        return OTHER
    }

    /**
     * Checks for a possible GB9c Indic Conjunct Break sequence. If it is
     * repetitive, e.g., Consonant1/Linker1/Consonant2/Linker2/Consonant3, only
     * the first part of the sequence (Consonant1/Linker1/Consonant2) is
     * recognized. The rest is analyzed in the next iteration of the grapheme
     * cluster boundary search.
     *
     * @param src the source char sequence
     * @param index the index that points to the starting Linking Consonant
     * @param limit limit to the char sequence
     * @return the advance in index if the indic conjunct break sequence
     * is found, it will be negative if the sequence is not found
     */
    private fun checkIndicConjunctBreak(src: CharSequence?, index: Int, limit: Int): Int {
        var linkerFound = false
        var advance = 0

        while (index + advance < limit) {
            val ch1 = Character.codePointAt(src!!, index + advance)
            advance += Character.charCount(ch1)

            if (IndicConjunctBreak.isLinker(ch1)) {
                linkerFound = true
            } else if (IndicConjunctBreak.isConsonant(ch1)) {
                if (linkerFound) {
                    return advance
                } else {
                    break
                }
            } else if (!IndicConjunctBreak.isExtend(ch1)) {
                break
            }
        }
        return -1
    }
}
