package org.gnit.lucenekmp.util.automaton


internal object CaseFolding {
    /**
     * Generates the set of codepoints which represent the given codepoint that are case-insensitive
     * matches within the Unicode table, which may not always be intuitive for instance Σ, σ, ς do all
     * fold to one another and so would match one another
     *
     *
     * Known special cases derived from generating mappings using a combination of the Unicode
     * 16.0.0 spec: https://www.unicode.org/Public/16.0.0/ucd/CaseFolding.txt and
     * https://www.unicode.org/Public/UCD/latest/ucd/UnicodeData.txt these are only the alternate
     * mappings for each codepoint that are not supported by a transform using Character.toUpperCase
     * or Character.toLowerCase
     *
     * @param codepoint the codepoint for the character to case fold
     * @return an array of characters as codepoints that should match the given codepoint in a
     * case-insensitive context or null if no alternates exist this does not include the given
     * codepoint itself
     */
    fun lookupAlternates(codepoint: Int): IntArray? {
        val alts =
            when (codepoint) {
                0x00049 ->  // I [LATIN CAPITAL LETTER I]
                    intArrayOf(
                        0x00130,  // İ [LATIN CAPITAL LETTER I WITH DOT ABOVE]
                        0x00131,  // ı [LATIN SMALL LETTER DOTLESS I]
                        0x00069,  // i [LATIN SMALL LETTER I]
                    )

                0x0004B ->  // K [LATIN CAPITAL LETTER K]
                    intArrayOf(
                        0x0212A,  // K [KELVIN SIGN]
                        0x0006B,  // k [LATIN SMALL LETTER K]
                    )

                0x00053 ->  // S [LATIN CAPITAL LETTER S]
                    intArrayOf(
                        0x00073,  // s [LATIN SMALL LETTER S]
                        0x0017F,  // ſ [LATIN SMALL LETTER LONG S]
                    )

                0x00069 ->  // i [LATIN SMALL LETTER I]
                    intArrayOf(
                        0x00130,  // İ [LATIN CAPITAL LETTER I WITH DOT ABOVE]
                        0x00131,  // ı [LATIN SMALL LETTER DOTLESS I]
                        0x00049,  // I [LATIN CAPITAL LETTER I]
                    )

                0x0006B ->  // k [LATIN SMALL LETTER K]
                    intArrayOf(
                        0x0212A,  // K [KELVIN SIGN]
                        0x0004B,  // K [LATIN CAPITAL LETTER K]
                    )

                0x00073 ->  // s [LATIN SMALL LETTER S]
                    intArrayOf(
                        0x00053,  // S [LATIN CAPITAL LETTER S]
                        0x0017F,  // ſ [LATIN SMALL LETTER LONG S]
                    )

                0x000B5 ->  // µ [MICRO SIGN]
                    intArrayOf(
                        0x0039C,  // Μ [GREEK CAPITAL LETTER MU]
                        0x003BC,  // μ [GREEK SMALL LETTER MU]
                    )

                0x000C5 ->  // Å [LATIN CAPITAL LETTER A WITH RING ABOVE]
                    intArrayOf(
                        0x000E5,  // å [LATIN SMALL LETTER A WITH RING ABOVE]
                        0x0212B,  // Å [ANGSTROM SIGN]
                    )

                0x000DF ->  // ß [LATIN SMALL LETTER SHARP S]
                    intArrayOf(
                        0x01E9E,  // ẞ [LATIN CAPITAL LETTER SHARP S]
                    )

                0x000E5 ->  // å [LATIN SMALL LETTER A WITH RING ABOVE]
                    intArrayOf(
                        0x000C5,  // Å [LATIN CAPITAL LETTER A WITH RING ABOVE]
                        0x0212B,  // Å [ANGSTROM SIGN]
                    )

                0x02126 ->  // Ω [OHM SIGN]
                    intArrayOf(
                        0x003A9,  // Ω [GREEK CAPITAL LETTER OMEGA]
                        0x003C9,  // ω [GREEK SMALL LETTER OMEGA]
                    )

                0x0212A ->  // K [KELVIN SIGN]
                    intArrayOf(
                        0x0004B,  // K [LATIN CAPITAL LETTER K]
                        0x0006B,  // k [LATIN SMALL LETTER K]
                    )

                0x0212B ->  // Å [ANGSTROM SIGN]
                    intArrayOf(
                        0x000C5,  // Å [LATIN CAPITAL LETTER A WITH RING ABOVE]
                        0x000E5,  // å [LATIN SMALL LETTER A WITH RING ABOVE]
                    )

                0x00130 ->  // İ [LATIN CAPITAL LETTER I WITH DOT ABOVE]
                    intArrayOf(
                        0x00131,  // ı [LATIN SMALL LETTER DOTLESS I]
                        0x00049,  // I [LATIN CAPITAL LETTER I]
                        0x00069,  // i [LATIN SMALL LETTER I]
                    )

                0x00131 ->  // ı [LATIN SMALL LETTER DOTLESS I]
                    intArrayOf(
                        0x00130,  // İ [LATIN CAPITAL LETTER I WITH DOT ABOVE]
                        0x00069,  // i [LATIN SMALL LETTER I]
                        0x00049,  // I [LATIN CAPITAL LETTER I]
                    )

                0x0017F ->  // ſ [LATIN SMALL LETTER LONG S]
                    intArrayOf(
                        0x00053,  // S [LATIN CAPITAL LETTER S]
                        0x00073,  // s [LATIN SMALL LETTER S]
                    )

                0x0019B ->  // ƛ [LATIN SMALL LETTER LAMBDA WITH STROKE]
                    intArrayOf(
                        0x0A7DC,  // Ƛ [LATIN CAPITAL LETTER LAMBDA WITH STROKE]
                    )

                0x001C4 ->  // Ǆ [LATIN CAPITAL LETTER DZ WITH CARON]
                    intArrayOf(
                        0x001C5,  // ǅ [LATIN CAPITAL LETTER D WITH SMALL LETTER Z WITH CARON]
                        0x001C6,  // ǆ [LATIN SMALL LETTER DZ WITH CARON]
                    )

                0x001C5 ->  // ǅ [LATIN CAPITAL LETTER D WITH SMALL LETTER Z WITH CARON]
                    intArrayOf(
                        0x001C4,  // Ǆ [LATIN CAPITAL LETTER DZ WITH CARON]
                        0x001C6,  // ǆ [LATIN SMALL LETTER DZ WITH CARON]
                    )

                0x001C6 ->  // ǆ [LATIN SMALL LETTER DZ WITH CARON]
                    intArrayOf(
                        0x001C4,  // Ǆ [LATIN CAPITAL LETTER DZ WITH CARON]
                        0x001C5,  // ǅ [LATIN CAPITAL LETTER D WITH SMALL LETTER Z WITH CARON]
                    )

                0x001C7 ->  // Ǉ [LATIN CAPITAL LETTER LJ]
                    intArrayOf(
                        0x001C8,  // ǈ [LATIN CAPITAL LETTER L WITH SMALL LETTER J]
                        0x001C9,  // ǉ [LATIN SMALL LETTER LJ]
                    )

                0x001C8 ->  // ǈ [LATIN CAPITAL LETTER L WITH SMALL LETTER J]
                    intArrayOf(
                        0x001C7,  // Ǉ [LATIN CAPITAL LETTER LJ]
                        0x001C9,  // ǉ [LATIN SMALL LETTER LJ]
                    )

                0x001C9 ->  // ǉ [LATIN SMALL LETTER LJ]
                    intArrayOf(
                        0x001C7,  // Ǉ [LATIN CAPITAL LETTER LJ]
                        0x001C8,  // ǈ [LATIN CAPITAL LETTER L WITH SMALL LETTER J]
                    )

                0x001CA ->  // Ǌ [LATIN CAPITAL LETTER NJ]
                    intArrayOf(
                        0x001CB,  // ǋ [LATIN CAPITAL LETTER N WITH SMALL LETTER J]
                        0x001CC,  // ǌ [LATIN SMALL LETTER NJ]
                    )

                0x001CB ->  // ǋ [LATIN CAPITAL LETTER N WITH SMALL LETTER J]
                    intArrayOf(
                        0x001CA,  // Ǌ [LATIN CAPITAL LETTER NJ]
                        0x001CC,  // ǌ [LATIN SMALL LETTER NJ]
                    )

                0x001CC ->  // ǌ [LATIN SMALL LETTER NJ]
                    intArrayOf(
                        0x001CA,  // Ǌ [LATIN CAPITAL LETTER NJ]
                        0x001CB,  // ǋ [LATIN CAPITAL LETTER N WITH SMALL LETTER J]
                    )

                0x001F1 ->  // Ǳ [LATIN CAPITAL LETTER DZ]
                    intArrayOf(
                        0x001F2,  // ǲ [LATIN CAPITAL LETTER D WITH SMALL LETTER Z]
                        0x001F3,  // ǳ [LATIN SMALL LETTER DZ]
                    )

                0x001F2 ->  // ǲ [LATIN CAPITAL LETTER D WITH SMALL LETTER Z]
                    intArrayOf(
                        0x001F1,  // Ǳ [LATIN CAPITAL LETTER DZ]
                        0x001F3,  // ǳ [LATIN SMALL LETTER DZ]
                    )

                0x001F3 ->  // ǳ [LATIN SMALL LETTER DZ]
                    intArrayOf(
                        0x001F1,  // Ǳ [LATIN CAPITAL LETTER DZ]
                        0x001F2,  // ǲ [LATIN CAPITAL LETTER D WITH SMALL LETTER Z]
                    )

                0x00264 ->  // ɤ [LATIN SMALL LETTER RAMS HORN]
                    intArrayOf(
                        0x0A7CB,  // Ɤ [LATIN CAPITAL LETTER RAMS HORN]
                    )

                0x00345 ->  // ͅ [COMBINING GREEK YPOGEGRAMMENI]
                    intArrayOf(
                        0x00399,  // Ι [GREEK CAPITAL LETTER IOTA]
                        0x003B9,  // ι [GREEK SMALL LETTER IOTA]
                        0x01FBE,  // ι [GREEK PROSGEGRAMMENI]
                    )

                0x00390 ->  // ΐ [GREEK SMALL LETTER IOTA WITH DIALYTIKA AND TONOS]
                    intArrayOf(
                        0x01FD3,  // ΐ [GREEK SMALL LETTER IOTA WITH DIALYTIKA AND OXIA]
                    )

                0x00392 ->  // Β [GREEK CAPITAL LETTER BETA]
                    intArrayOf(
                        0x003D0,  // ϐ [GREEK BETA SYMBOL]
                        0x003B2,  // β [GREEK SMALL LETTER BETA]
                    )

                0x00395 ->  // Ε [GREEK CAPITAL LETTER EPSILON]
                    intArrayOf(
                        0x003F5,  // ϵ [GREEK LUNATE EPSILON SYMBOL]
                        0x003B5,  // ε [GREEK SMALL LETTER EPSILON]
                    )

                0x00398 ->  // Θ [GREEK CAPITAL LETTER THETA]
                    intArrayOf(
                        0x003D1,  // ϑ [GREEK THETA SYMBOL]
                        0x003F4,  // ϴ [GREEK CAPITAL THETA SYMBOL]
                        0x003B8,  // θ [GREEK SMALL LETTER THETA]
                    )

                0x00399 ->  // Ι [GREEK CAPITAL LETTER IOTA]
                    intArrayOf(
                        0x00345,  // ͅ [COMBINING GREEK YPOGEGRAMMENI]
                        0x003B9,  // ι [GREEK SMALL LETTER IOTA]
                        0x01FBE,  // ι [GREEK PROSGEGRAMMENI]
                    )

                0x0039A ->  // Κ [GREEK CAPITAL LETTER KAPPA]
                    intArrayOf(
                        0x003F0,  // ϰ [GREEK KAPPA SYMBOL]
                        0x003BA,  // κ [GREEK SMALL LETTER KAPPA]
                    )

                0x0039C ->  // Μ [GREEK CAPITAL LETTER MU]
                    intArrayOf(
                        0x000B5,  // µ [MICRO SIGN]
                        0x003BC,  // μ [GREEK SMALL LETTER MU]
                    )

                0x003A0 ->  // Π [GREEK CAPITAL LETTER PI]
                    intArrayOf(
                        0x003C0,  // π [GREEK SMALL LETTER PI]
                        0x003D6,  // ϖ [GREEK PI SYMBOL]
                    )

                0x003A1 ->  // Ρ [GREEK CAPITAL LETTER RHO]
                    intArrayOf(
                        0x003F1,  // ϱ [GREEK RHO SYMBOL]
                        0x003C1,  // ρ [GREEK SMALL LETTER RHO]
                    )

                0x003A3 ->  // Σ [GREEK CAPITAL LETTER SIGMA]
                    intArrayOf(
                        0x003C2,  // ς [GREEK SMALL LETTER FINAL SIGMA]
                        0x003C3,  // σ [GREEK SMALL LETTER SIGMA]
                    )

                0x003A6 ->  // Φ [GREEK CAPITAL LETTER PHI]
                    intArrayOf(
                        0x003D5,  // ϕ [GREEK PHI SYMBOL]
                        0x003C6,  // φ [GREEK SMALL LETTER PHI]
                    )

                0x003A9 ->  // Ω [GREEK CAPITAL LETTER OMEGA]
                    intArrayOf(
                        0x02126,  // Ω [OHM SIGN]
                        0x003C9,  // ω [GREEK SMALL LETTER OMEGA]
                    )

                0x003B0 ->  // ΰ [GREEK SMALL LETTER UPSILON WITH DIALYTIKA AND TONOS]
                    intArrayOf(
                        0x01FE3,  // ΰ [GREEK SMALL LETTER UPSILON WITH DIALYTIKA AND OXIA]
                    )

                0x003B2 ->  // β [GREEK SMALL LETTER BETA]
                    intArrayOf(
                        0x003D0,  // ϐ [GREEK BETA SYMBOL]
                        0x00392,  // Β [GREEK CAPITAL LETTER BETA]
                    )

                0x003B5 ->  // ε [GREEK SMALL LETTER EPSILON]
                    intArrayOf(
                        0x00395,  // Ε [GREEK CAPITAL LETTER EPSILON]
                        0x003F5,  // ϵ [GREEK LUNATE EPSILON SYMBOL]
                    )

                0x003B8 ->  // θ [GREEK SMALL LETTER THETA]
                    intArrayOf(
                        0x003D1,  // ϑ [GREEK THETA SYMBOL]
                        0x003F4,  // ϴ [GREEK CAPITAL THETA SYMBOL]
                        0x00398,  // Θ [GREEK CAPITAL LETTER THETA]
                    )

                0x003B9 ->  // ι [GREEK SMALL LETTER IOTA]
                    intArrayOf(
                        0x00345,  // ͅ [COMBINING GREEK YPOGEGRAMMENI]
                        0x00399,  // Ι [GREEK CAPITAL LETTER IOTA]
                        0x01FBE,  // ι [GREEK PROSGEGRAMMENI]
                    )

                0x003BA ->  // κ [GREEK SMALL LETTER KAPPA]
                    intArrayOf(
                        0x003F0,  // ϰ [GREEK KAPPA SYMBOL]
                        0x0039A,  // Κ [GREEK CAPITAL LETTER KAPPA]
                    )

                0x003BC ->  // μ [GREEK SMALL LETTER MU]
                    intArrayOf(
                        0x000B5,  // µ [MICRO SIGN]
                        0x0039C,  // Μ [GREEK CAPITAL LETTER MU]
                    )

                0x003C0 ->  // π [GREEK SMALL LETTER PI]
                    intArrayOf(
                        0x003A0,  // Π [GREEK CAPITAL LETTER PI]
                        0x003D6,  // ϖ [GREEK PI SYMBOL]
                    )

                0x003C1 ->  // ρ [GREEK SMALL LETTER RHO]
                    intArrayOf(
                        0x003A1,  // Ρ [GREEK CAPITAL LETTER RHO]
                        0x003F1,  // ϱ [GREEK RHO SYMBOL]
                    )

                0x003C2 ->  // ς [GREEK SMALL LETTER FINAL SIGMA]
                    intArrayOf(
                        0x003A3,  // Σ [GREEK CAPITAL LETTER SIGMA]
                        0x003C3,  // σ [GREEK SMALL LETTER SIGMA]
                    )

                0x003C3 ->  // σ [GREEK SMALL LETTER SIGMA]
                    intArrayOf(
                        0x003C2,  // ς [GREEK SMALL LETTER FINAL SIGMA]
                        0x003A3,  // Σ [GREEK CAPITAL LETTER SIGMA]
                    )

                0x003C6 ->  // φ [GREEK SMALL LETTER PHI]
                    intArrayOf(
                        0x003D5,  // ϕ [GREEK PHI SYMBOL]
                        0x003A6,  // Φ [GREEK CAPITAL LETTER PHI]
                    )

                0x003C9 ->  // ω [GREEK SMALL LETTER OMEGA]
                    intArrayOf(
                        0x02126,  // Ω [OHM SIGN]
                        0x003A9,  // Ω [GREEK CAPITAL LETTER OMEGA]
                    )

                0x003D0 ->  // ϐ [GREEK BETA SYMBOL]
                    intArrayOf(
                        0x00392,  // Β [GREEK CAPITAL LETTER BETA]
                        0x003B2,  // β [GREEK SMALL LETTER BETA]
                    )

                0x003D1 ->  // ϑ [GREEK THETA SYMBOL]
                    intArrayOf(
                        0x003F4,  // ϴ [GREEK CAPITAL THETA SYMBOL]
                        0x00398,  // Θ [GREEK CAPITAL LETTER THETA]
                        0x003B8,  // θ [GREEK SMALL LETTER THETA]
                    )

                0x003D5 ->  // ϕ [GREEK PHI SYMBOL]
                    intArrayOf(
                        0x003A6,  // Φ [GREEK CAPITAL LETTER PHI]
                        0x003C6,  // φ [GREEK SMALL LETTER PHI]
                    )

                0x003D6 ->  // ϖ [GREEK PI SYMBOL]
                    intArrayOf(
                        0x003A0,  // Π [GREEK CAPITAL LETTER PI]
                        0x003C0,  // π [GREEK SMALL LETTER PI]
                    )

                0x003F0 ->  // ϰ [GREEK KAPPA SYMBOL]
                    intArrayOf(
                        0x0039A,  // Κ [GREEK CAPITAL LETTER KAPPA]
                        0x003BA,  // κ [GREEK SMALL LETTER KAPPA]
                    )

                0x003F1 ->  // ϱ [GREEK RHO SYMBOL]
                    intArrayOf(
                        0x003A1,  // Ρ [GREEK CAPITAL LETTER RHO]
                        0x003C1,  // ρ [GREEK SMALL LETTER RHO]
                    )

                0x003F4 ->  // ϴ [GREEK CAPITAL THETA SYMBOL]
                    intArrayOf(
                        0x003D1,  // ϑ [GREEK THETA SYMBOL]
                        0x00398,  // Θ [GREEK CAPITAL LETTER THETA]
                        0x003B8,  // θ [GREEK SMALL LETTER THETA]
                    )

                0x003F5 ->  // ϵ [GREEK LUNATE EPSILON SYMBOL]
                    intArrayOf(
                        0x00395,  // Ε [GREEK CAPITAL LETTER EPSILON]
                        0x003B5,  // ε [GREEK SMALL LETTER EPSILON]
                    )

                0x00412 ->  // В [CYRILLIC CAPITAL LETTER VE]
                    intArrayOf(
                        0x01C80,  // ᲀ [CYRILLIC SMALL LETTER ROUNDED VE]
                        0x00432,  // в [CYRILLIC SMALL LETTER VE]
                    )

                0x00414 ->  // Д [CYRILLIC CAPITAL LETTER DE]
                    intArrayOf(
                        0x01C81,  // ᲁ [CYRILLIC SMALL LETTER LONG-LEGGED DE]
                        0x00434,  // д [CYRILLIC SMALL LETTER DE]
                    )

                0x0041E ->  // О [CYRILLIC CAPITAL LETTER O]
                    intArrayOf(
                        0x01C82,  // ᲂ [CYRILLIC SMALL LETTER NARROW O]
                        0x0043E,  // о [CYRILLIC SMALL LETTER O]
                    )

                0x00421 ->  // С [CYRILLIC CAPITAL LETTER ES]
                    intArrayOf(
                        0x00441,  // с [CYRILLIC SMALL LETTER ES]
                        0x01C83,  // ᲃ [CYRILLIC SMALL LETTER WIDE ES]
                    )

                0x00422 ->  // Т [CYRILLIC CAPITAL LETTER TE]
                    intArrayOf(
                        0x00442,  // т [CYRILLIC SMALL LETTER TE]
                        0x01C84,  // ᲄ [CYRILLIC SMALL LETTER TALL TE]
                        0x01C85,  // ᲅ [CYRILLIC SMALL LETTER THREE-LEGGED TE]
                    )

                0x0042A ->  // Ъ [CYRILLIC CAPITAL LETTER HARD SIGN]
                    intArrayOf(
                        0x01C86,  // ᲆ [CYRILLIC SMALL LETTER TALL HARD SIGN]
                        0x0044A,  // ъ [CYRILLIC SMALL LETTER HARD SIGN]
                    )

                0x00432 ->  // в [CYRILLIC SMALL LETTER VE]
                    intArrayOf(
                        0x01C80,  // ᲀ [CYRILLIC SMALL LETTER ROUNDED VE]
                        0x00412,  // В [CYRILLIC CAPITAL LETTER VE]
                    )

                0x00434 ->  // д [CYRILLIC SMALL LETTER DE]
                    intArrayOf(
                        0x01C81,  // ᲁ [CYRILLIC SMALL LETTER LONG-LEGGED DE]
                        0x00414,  // Д [CYRILLIC CAPITAL LETTER DE]
                    )

                0x0043E ->  // о [CYRILLIC SMALL LETTER O]
                    intArrayOf(
                        0x01C82,  // ᲂ [CYRILLIC SMALL LETTER NARROW O]
                        0x0041E,  // О [CYRILLIC CAPITAL LETTER O]
                    )

                0x00441 ->  // с [CYRILLIC SMALL LETTER ES]
                    intArrayOf(
                        0x00421,  // С [CYRILLIC CAPITAL LETTER ES]
                        0x01C83,  // ᲃ [CYRILLIC SMALL LETTER WIDE ES]
                    )

                0x00442 ->  // т [CYRILLIC SMALL LETTER TE]
                    intArrayOf(
                        0x00422,  // Т [CYRILLIC CAPITAL LETTER TE]
                        0x01C84,  // ᲄ [CYRILLIC SMALL LETTER TALL TE]
                        0x01C85,  // ᲅ [CYRILLIC SMALL LETTER THREE-LEGGED TE]
                    )

                0x0044A ->  // ъ [CYRILLIC SMALL LETTER HARD SIGN]
                    intArrayOf(
                        0x01C86,  // ᲆ [CYRILLIC SMALL LETTER TALL HARD SIGN]
                        0x0042A,  // Ъ [CYRILLIC CAPITAL LETTER HARD SIGN]
                    )

                0x00462 ->  // Ѣ [CYRILLIC CAPITAL LETTER YAT]
                    intArrayOf(
                        0x00463,  // ѣ [CYRILLIC SMALL LETTER YAT]
                        0x01C87,  // ᲇ [CYRILLIC SMALL LETTER TALL YAT]
                    )

                0x00463 ->  // ѣ [CYRILLIC SMALL LETTER YAT]
                    intArrayOf(
                        0x00462,  // Ѣ [CYRILLIC CAPITAL LETTER YAT]
                        0x01C87,  // ᲇ [CYRILLIC SMALL LETTER TALL YAT]
                    )

                0x0A64A ->  // Ꙋ [CYRILLIC CAPITAL LETTER MONOGRAPH UK]
                    intArrayOf(
                        0x01C88,  // ᲈ [CYRILLIC SMALL LETTER UNBLENDED UK]
                        0x0A64B,  // ꙋ [CYRILLIC SMALL LETTER MONOGRAPH UK]
                    )

                0x0A64B ->  // ꙋ [CYRILLIC SMALL LETTER MONOGRAPH UK]
                    intArrayOf(
                        0x01C88,  // ᲈ [CYRILLIC SMALL LETTER UNBLENDED UK]
                        0x0A64A,  // Ꙋ [CYRILLIC CAPITAL LETTER MONOGRAPH UK]
                    )

                0x0A7CB ->  // Ɤ [LATIN CAPITAL LETTER RAMS HORN]
                    intArrayOf(
                        0x00264,  // ɤ [LATIN SMALL LETTER RAMS HORN]
                    )

                0x0A7CC ->  // Ꟍ [LATIN CAPITAL LETTER S WITH DIAGONAL STROKE]
                    intArrayOf(
                        0x0A7CD,  // ꟍ [LATIN SMALL LETTER S WITH DIAGONAL STROKE]
                    )

                0x0A7CD ->  // ꟍ [LATIN SMALL LETTER S WITH DIAGONAL STROKE]
                    intArrayOf(
                        0x0A7CC,  // Ꟍ [LATIN CAPITAL LETTER S WITH DIAGONAL STROKE]
                    )

                0x0A7DA ->  // Ꟛ [LATIN CAPITAL LETTER LAMBDA]
                    intArrayOf(
                        0x0A7DB,  // ꟛ [LATIN SMALL LETTER LAMBDA]
                    )

                0x0A7DB ->  // ꟛ [LATIN SMALL LETTER LAMBDA]
                    intArrayOf(
                        0x0A7DA,  // Ꟛ [LATIN CAPITAL LETTER LAMBDA]
                    )

                0x0A7DC ->  // Ƛ [LATIN CAPITAL LETTER LAMBDA WITH STROKE]
                    intArrayOf(
                        0x0019B,  // ƛ [LATIN SMALL LETTER LAMBDA WITH STROKE]
                    )

                0x0FB05 ->  // ﬅ [LATIN SMALL LIGATURE LONG S T]
                    intArrayOf(
                        0x0FB06,  // ﬆ [LATIN SMALL LIGATURE ST]
                    )

                0x0FB06 ->  // ﬆ [LATIN SMALL LIGATURE ST]
                    intArrayOf(
                        0x0FB05,  // ﬅ [LATIN SMALL LIGATURE LONG S T]
                    )

                0x01C80 ->  // ᲀ [CYRILLIC SMALL LETTER ROUNDED VE]
                    intArrayOf(
                        0x00412,  // В [CYRILLIC CAPITAL LETTER VE]
                        0x00432,  // в [CYRILLIC SMALL LETTER VE]
                    )

                0x01C81 ->  // ᲁ [CYRILLIC SMALL LETTER LONG-LEGGED DE]
                    intArrayOf(
                        0x00414,  // Д [CYRILLIC CAPITAL LETTER DE]
                        0x00434,  // д [CYRILLIC SMALL LETTER DE]
                    )

                0x01C82 ->  // ᲂ [CYRILLIC SMALL LETTER NARROW O]
                    intArrayOf(
                        0x0041E,  // О [CYRILLIC CAPITAL LETTER O]
                        0x0043E,  // о [CYRILLIC SMALL LETTER O]
                    )

                0x01C83 ->  // ᲃ [CYRILLIC SMALL LETTER WIDE ES]
                    intArrayOf(
                        0x00421,  // С [CYRILLIC CAPITAL LETTER ES]
                        0x00441,  // с [CYRILLIC SMALL LETTER ES]
                    )

                0x01C84 ->  // ᲄ [CYRILLIC SMALL LETTER TALL TE]
                    intArrayOf(
                        0x00422,  // Т [CYRILLIC CAPITAL LETTER TE]
                        0x00442,  // т [CYRILLIC SMALL LETTER TE]
                        0x01C85,  // ᲅ [CYRILLIC SMALL LETTER THREE-LEGGED TE]
                    )

                0x01C85 ->  // ᲅ [CYRILLIC SMALL LETTER THREE-LEGGED TE]
                    intArrayOf(
                        0x00422,  // Т [CYRILLIC CAPITAL LETTER TE]
                        0x00442,  // т [CYRILLIC SMALL LETTER TE]
                        0x01C84,  // ᲄ [CYRILLIC SMALL LETTER TALL TE]
                    )

                0x01C86 ->  // ᲆ [CYRILLIC SMALL LETTER TALL HARD SIGN]
                    intArrayOf(
                        0x0042A,  // Ъ [CYRILLIC CAPITAL LETTER HARD SIGN]
                        0x0044A,  // ъ [CYRILLIC SMALL LETTER HARD SIGN]
                    )

                0x01C87 ->  // ᲇ [CYRILLIC SMALL LETTER TALL YAT]
                    intArrayOf(
                        0x00462,  // Ѣ [CYRILLIC CAPITAL LETTER YAT]
                        0x00463,  // ѣ [CYRILLIC SMALL LETTER YAT]
                    )

                0x01C88 ->  // ᲈ [CYRILLIC SMALL LETTER UNBLENDED UK]
                    intArrayOf(
                        0x0A64A,  // Ꙋ [CYRILLIC CAPITAL LETTER MONOGRAPH UK]
                        0x0A64B,  // ꙋ [CYRILLIC SMALL LETTER MONOGRAPH UK]
                    )

                0x01C89 ->  // Ᲊ [CYRILLIC CAPITAL LETTER TJE]
                    intArrayOf(
                        0x01C8A,  // ᲊ [CYRILLIC SMALL LETTER TJE]
                    )

                0x01C8A ->  // ᲊ [CYRILLIC SMALL LETTER TJE]
                    intArrayOf(
                        0x01C89,  // Ᲊ [CYRILLIC CAPITAL LETTER TJE]
                    )

                0x10D51 ->  // 𐵑 [GARAY CAPITAL LETTER CA]
                    intArrayOf(
                        0x10D71,  // 𐵱 [GARAY SMALL LETTER CA]
                    )

                0x10D50 ->  // 𐵐 [GARAY CAPITAL LETTER A]
                    intArrayOf(
                        0x10D70,  // 𐵰 [GARAY SMALL LETTER A]
                    )

                0x10D53 ->  // 𐵓 [GARAY CAPITAL LETTER KA]
                    intArrayOf(
                        0x10D73,  // 𐵳 [GARAY SMALL LETTER KA]
                    )

                0x10D52 ->  // 𐵒 [GARAY CAPITAL LETTER MA]
                    intArrayOf(
                        0x10D72,  // 𐵲 [GARAY SMALL LETTER MA]
                    )

                0x10D55 ->  // 𐵕 [GARAY CAPITAL LETTER JA]
                    intArrayOf(
                        0x10D75,  // 𐵵 [GARAY SMALL LETTER JA]
                    )

                0x10D54 ->  // 𐵔 [GARAY CAPITAL LETTER BA]
                    intArrayOf(
                        0x10D74,  // 𐵴 [GARAY SMALL LETTER BA]
                    )

                0x10D57 ->  // 𐵗 [GARAY CAPITAL LETTER WA]
                    intArrayOf(
                        0x10D77,  // 𐵷 [GARAY SMALL LETTER WA]
                    )

                0x10D56 ->  // 𐵖 [GARAY CAPITAL LETTER SA]
                    intArrayOf(
                        0x10D76,  // 𐵶 [GARAY SMALL LETTER SA]
                    )

                0x10D59 ->  // 𐵙 [GARAY CAPITAL LETTER GA]
                    intArrayOf(
                        0x10D79,  // 𐵹 [GARAY SMALL LETTER GA]
                    )

                0x10D58 ->  // 𐵘 [GARAY CAPITAL LETTER LA]
                    intArrayOf(
                        0x10D78,  // 𐵸 [GARAY SMALL LETTER LA]
                    )

                0x10D5B ->  // 𐵛 [GARAY CAPITAL LETTER XA]
                    intArrayOf(
                        0x10D7B,  // 𐵻 [GARAY SMALL LETTER XA]
                    )

                0x10D5A ->  // 𐵚 [GARAY CAPITAL LETTER DA]
                    intArrayOf(
                        0x10D7A,  // 𐵺 [GARAY SMALL LETTER DA]
                    )

                0x10D5D ->  // 𐵝 [GARAY CAPITAL LETTER TA]
                    intArrayOf(
                        0x10D7D,  // 𐵽 [GARAY SMALL LETTER TA]
                    )

                0x10D5C ->  // 𐵜 [GARAY CAPITAL LETTER YA]
                    intArrayOf(
                        0x10D7C,  // 𐵼 [GARAY SMALL LETTER YA]
                    )

                0x10D5F ->  // 𐵟 [GARAY CAPITAL LETTER NYA]
                    intArrayOf(
                        0x10D7F,  // 𐵿 [GARAY SMALL LETTER NYA]
                    )

                0x10D5E ->  // 𐵞 [GARAY CAPITAL LETTER RA]
                    intArrayOf(
                        0x10D7E,  // 𐵾 [GARAY SMALL LETTER RA]
                    )

                0x10D61 ->  // 𐵡 [GARAY CAPITAL LETTER NA]
                    intArrayOf(
                        0x10D81,  // 𐶁 [GARAY SMALL LETTER NA]
                    )

                0x10D60 ->  // 𐵠 [GARAY CAPITAL LETTER FA]
                    intArrayOf(
                        0x10D80,  // 𐶀 [GARAY SMALL LETTER FA]
                    )

                0x10D63 ->  // 𐵣 [GARAY CAPITAL LETTER HA]
                    intArrayOf(
                        0x10D83,  // 𐶃 [GARAY SMALL LETTER HA]
                    )

                0x10D62 ->  // 𐵢 [GARAY CAPITAL LETTER PA]
                    intArrayOf(
                        0x10D82,  // 𐶂 [GARAY SMALL LETTER PA]
                    )

                0x10D65 ->  // 𐵥 [GARAY CAPITAL LETTER OLD NA]
                    intArrayOf(
                        0x10D85,  // 𐶅 [GARAY SMALL LETTER OLD NA]
                    )

                0x10D64 ->  // 𐵤 [GARAY CAPITAL LETTER OLD KA]
                    intArrayOf(
                        0x10D84,  // 𐶄 [GARAY SMALL LETTER OLD KA]
                    )

                0x10D71 ->  // 𐵱 [GARAY SMALL LETTER CA]
                    intArrayOf(
                        0x10D51,  // 𐵑 [GARAY CAPITAL LETTER CA]
                    )

                0x10D70 ->  // 𐵰 [GARAY SMALL LETTER A]
                    intArrayOf(
                        0x10D50,  // 𐵐 [GARAY CAPITAL LETTER A]
                    )

                0x10D73 ->  // 𐵳 [GARAY SMALL LETTER KA]
                    intArrayOf(
                        0x10D53,  // 𐵓 [GARAY CAPITAL LETTER KA]
                    )

                0x10D72 ->  // 𐵲 [GARAY SMALL LETTER MA]
                    intArrayOf(
                        0x10D52,  // 𐵒 [GARAY CAPITAL LETTER MA]
                    )

                0x10D75 ->  // 𐵵 [GARAY SMALL LETTER JA]
                    intArrayOf(
                        0x10D55,  // 𐵕 [GARAY CAPITAL LETTER JA]
                    )

                0x10D74 ->  // 𐵴 [GARAY SMALL LETTER BA]
                    intArrayOf(
                        0x10D54,  // 𐵔 [GARAY CAPITAL LETTER BA]
                    )

                0x10D77 ->  // 𐵷 [GARAY SMALL LETTER WA]
                    intArrayOf(
                        0x10D57,  // 𐵗 [GARAY CAPITAL LETTER WA]
                    )

                0x10D76 ->  // 𐵶 [GARAY SMALL LETTER SA]
                    intArrayOf(
                        0x10D56,  // 𐵖 [GARAY CAPITAL LETTER SA]
                    )

                0x10D79 ->  // 𐵹 [GARAY SMALL LETTER GA]
                    intArrayOf(
                        0x10D59,  // 𐵙 [GARAY CAPITAL LETTER GA]
                    )

                0x10D78 ->  // 𐵸 [GARAY SMALL LETTER LA]
                    intArrayOf(
                        0x10D58,  // 𐵘 [GARAY CAPITAL LETTER LA]
                    )

                0x10D7B ->  // 𐵻 [GARAY SMALL LETTER XA]
                    intArrayOf(
                        0x10D5B,  // 𐵛 [GARAY CAPITAL LETTER XA]
                    )

                0x10D7A ->  // 𐵺 [GARAY SMALL LETTER DA]
                    intArrayOf(
                        0x10D5A,  // 𐵚 [GARAY CAPITAL LETTER DA]
                    )

                0x10D7D ->  // 𐵽 [GARAY SMALL LETTER TA]
                    intArrayOf(
                        0x10D5D,  // 𐵝 [GARAY CAPITAL LETTER TA]
                    )

                0x10D7C ->  // 𐵼 [GARAY SMALL LETTER YA]
                    intArrayOf(
                        0x10D5C,  // 𐵜 [GARAY CAPITAL LETTER YA]
                    )

                0x10D7F ->  // 𐵿 [GARAY SMALL LETTER NYA]
                    intArrayOf(
                        0x10D5F,  // 𐵟 [GARAY CAPITAL LETTER NYA]
                    )

                0x10D7E ->  // 𐵾 [GARAY SMALL LETTER RA]
                    intArrayOf(
                        0x10D5E,  // 𐵞 [GARAY CAPITAL LETTER RA]
                    )

                0x10D81 ->  // 𐶁 [GARAY SMALL LETTER NA]
                    intArrayOf(
                        0x10D61,  // 𐵡 [GARAY CAPITAL LETTER NA]
                    )

                0x10D80 ->  // 𐶀 [GARAY SMALL LETTER FA]
                    intArrayOf(
                        0x10D60,  // 𐵠 [GARAY CAPITAL LETTER FA]
                    )

                0x10D83 ->  // 𐶃 [GARAY SMALL LETTER HA]
                    intArrayOf(
                        0x10D63,  // 𐵣 [GARAY CAPITAL LETTER HA]
                    )

                0x10D82 ->  // 𐶂 [GARAY SMALL LETTER PA]
                    intArrayOf(
                        0x10D62,  // 𐵢 [GARAY CAPITAL LETTER PA]
                    )

                0x10D85 ->  // 𐶅 [GARAY SMALL LETTER OLD NA]
                    intArrayOf(
                        0x10D65,  // 𐵥 [GARAY CAPITAL LETTER OLD NA]
                    )

                0x10D84 ->  // 𐶄 [GARAY SMALL LETTER OLD KA]
                    intArrayOf(
                        0x10D64,  // 𐵤 [GARAY CAPITAL LETTER OLD KA]
                    )

                0x01E60 ->  // Ṡ [LATIN CAPITAL LETTER S WITH DOT ABOVE]
                    intArrayOf(
                        0x01E61,  // ṡ [LATIN SMALL LETTER S WITH DOT ABOVE]
                        0x01E9B,  // ẛ [LATIN SMALL LETTER LONG S WITH DOT ABOVE]
                    )

                0x01E61 ->  // ṡ [LATIN SMALL LETTER S WITH DOT ABOVE]
                    intArrayOf(
                        0x01E60,  // Ṡ [LATIN CAPITAL LETTER S WITH DOT ABOVE]
                        0x01E9B,  // ẛ [LATIN SMALL LETTER LONG S WITH DOT ABOVE]
                    )

                0x01E9B ->  // ẛ [LATIN SMALL LETTER LONG S WITH DOT ABOVE]
                    intArrayOf(
                        0x01E60,  // Ṡ [LATIN CAPITAL LETTER S WITH DOT ABOVE]
                        0x01E61,  // ṡ [LATIN SMALL LETTER S WITH DOT ABOVE]
                    )

                0x01FBE ->  // ι [GREEK PROSGEGRAMMENI]
                    intArrayOf(
                        0x00345,  // ͅ [COMBINING GREEK YPOGEGRAMMENI]
                        0x00399,  // Ι [GREEK CAPITAL LETTER IOTA]
                        0x003B9,  // ι [GREEK SMALL LETTER IOTA]
                    )

                0x01FD3 ->  // ΐ [GREEK SMALL LETTER IOTA WITH DIALYTIKA AND OXIA]
                    intArrayOf(
                        0x00390,  // ΐ [GREEK SMALL LETTER IOTA WITH DIALYTIKA AND TONOS]
                    )

                0x01FE3 ->  // ΰ [GREEK SMALL LETTER UPSILON WITH DIALYTIKA AND OXIA]
                    intArrayOf(
                        0x003B0,  // ΰ [GREEK SMALL LETTER UPSILON WITH DIALYTIKA AND TONOS]
                    )

                else -> null
            }

        return alts
    }
}
