package org.gnit.lucenekmp.analysis.br

/** A stemmer for Brazilian Portuguese words. */
class BrazilianStemmer {
    /** Changed term */
    private var TERM: String? = null

    private var CT: String? = null
    private var R1: String? = null
    private var R2: String? = null
    private var RV: String? = null

    /**
     * Stems the given term to an unique `discriminator`.
     *
     * @param term The term that should be stemmed.
     * @return Discriminator for `term`
     */
    fun stem(term: String): String? {
        var altered = false

        createCT(term)

        val ct = CT ?: return null
        if (!isIndexable(ct)) {
            return null
        }
        if (!isStemmable(ct)) {
            return ct
        }

        R1 = getR1(ct)
        R2 = getR1(R1)
        RV = getRV(ct)
        TERM = "$term;$ct"

        altered = step1()
        if (!altered) {
            altered = step2()
        }

        if (altered) {
            step3()
        } else {
            step4()
        }

        step5()

        return CT
    }

    /**
     * Checks a term if it can be processed correctly.
     *
     * @return true if, and only if, the given term consists in letters.
     */
    private fun isStemmable(term: String): Boolean {
        for (c in term.indices) {
            if (!term[c].isLetter()) {
                return false
            }
        }
        return true
    }

    /**
     * Checks a term if it can be processed indexed.
     *
     * @return true if it can be indexed
     */
    private fun isIndexable(term: String): Boolean {
        return (term.length < 30) && (term.length > 2)
    }

    /**
     * See if string is 'a','e','i','o','u'
     *
     * @return true if is vowel
     */
    private fun isVowel(value: Char): Boolean {
        return (value == 'a') || (value == 'e') || (value == 'i') || (value == 'o') || (value == 'u')
    }

    /**
     * Gets R1
     *
     * R1 - is the region after the first non-vowel following a vowel, or is the null region at the
     * end of the word if there is no such non-vowel.
     *
     * @return null or a string representing R1
     */
    private fun getR1(value: String?): String? {
        var j: Int
        val v = value ?: return null
        val i = v.length - 1

        for (jj in 0..<i) {
            if (isVowel(v[jj])) {
                j = jj
                while (j < i) {
                    if (!isVowel(v[j])) {
                        break
                    }
                    j++
                }
                if (j < i) {
                    return v.substring(j + 1)
                }
                return null
            }
        }

        return null
    }

    /**
     * Gets RV
     *
     * RV - IF the second letter is a consonant, RV is the region after the next following vowel,
     *
     * OR if the first two letters are vowels, RV is the region after the next consonant,
     *
     * AND otherwise (consonant-vowel case) RV is the region after the third letter.
     *
     * BUT RV is the end of the word if this positions cannot be found.
     *
     * @return null or a string representing RV
     */
    private fun getRV(value: String?): String? {
        var j: Int
        val v = value ?: return null
        val i = v.length - 1

        if ((i > 0) && !isVowel(v[1])) {
            for (jj in 2..<i) {
                if (isVowel(v[jj])) {
                    j = jj
                    if (j < i) {
                        return v.substring(j + 1)
                    }
                }
            }
        }

        if ((i > 1) && isVowel(v[0]) && isVowel(v[1])) {
            for (jj in 2..<i) {
                if (!isVowel(v[jj])) {
                    j = jj
                    if (j < i) {
                        return v.substring(j + 1)
                    }
                }
            }
        }

        if (i > 2) {
            return v.substring(3)
        }

        return null
    }

    /**
     * 1) Turn to lowercase 2) Remove accents 3) ã -> a ; õ -> o 4) ç -> c
     *
     * @return null or a string transformed
     */
    private fun changeTerm(value: String?): String? {
        var r = ""

        val v = value ?: return null

        val lower = v.lowercase()
        for (j in lower.indices) {
            if ((lower[j] == 'á') || (lower[j] == 'â') || (lower[j] == 'ã')) {
                r += "a"
                continue
            }
            if ((lower[j] == 'é') || (lower[j] == 'ê')) {
                r += "e"
                continue
            }
            if (lower[j] == 'í') {
                r += "i"
                continue
            }
            if ((lower[j] == 'ó') || (lower[j] == 'ô') || (lower[j] == 'õ')) {
                r += "o"
                continue
            }
            if ((lower[j] == 'ú') || (lower[j] == 'ü')) {
                r += "u"
                continue
            }
            if (lower[j] == 'ç') {
                r += "c"
                continue
            }
            if (lower[j] == 'ñ') {
                r += "n"
                continue
            }

            r += lower[j]
        }

        return r
    }

    /**
     * Check if a string ends with a suffix
     *
     * @return true if the string ends with the specified suffix
     */
    private fun suffix(value: String?, suffix: String?): Boolean {
        if ((value == null) || (suffix == null)) {
            return false
        }
        if (suffix.length > value.length) {
            return false
        }
        return value.endsWith(suffix)
    }

    /**
     * Replace a string suffix by another
     *
     * @return the replaced String
     */
    private fun replaceSuffix(value: String?, toReplace: String?, changeTo: String?): String? {
        var vvalue: String?

        if ((value == null) || (toReplace == null) || (changeTo == null)) {
            return value
        }

        vvalue = removeSuffix(value, toReplace)

        if (value == vvalue) {
            return value
        } else {
            return vvalue + changeTo
        }
    }

    /**
     * Remove a string suffix
     *
     * @return the String without the suffix
     */
    private fun removeSuffix(value: String?, toRemove: String?): String? {
        if ((value == null) || (toRemove == null) || !suffix(value, toRemove)) {
            return value
        }

        return value.substring(0, value.length - toRemove.length)
    }

    /**
     * See if a suffix is preceded by a String
     *
     * @return true if the suffix is preceded
     */
    private fun suffixPreceded(value: String?, suffix: String?, preceded: String?): Boolean {
        if ((value == null) || (suffix == null) || (preceded == null) || !suffix(value, suffix)) {
            return false
        }

        return suffix(removeSuffix(value, suffix), preceded)
    }

    /** Creates CT (changed term) , substituting * 'ã' and 'õ' for 'a~' and 'o~'. */
    private fun createCT(term: String) {
        CT = changeTerm(term)
        var ct = CT ?: return

        if (ct.length < 2) return

        if ((ct[0] == '"')
            || (ct[0] == '\'')
            || (ct[0] == '-')
            || (ct[0] == ',')
            || (ct[0] == ';')
            || (ct[0] == '.')
            || (ct[0] == '?')
            || (ct[0] == '!')
        ) {
            ct = ct.substring(1)
            CT = ct
        }

        if (ct.length < 2) return

        if ((ct[ct.length - 1] == '-')
            || (ct[ct.length - 1] == ',')
            || (ct[ct.length - 1] == ';')
            || (ct[ct.length - 1] == '.')
            || (ct[ct.length - 1] == '?')
            || (ct[ct.length - 1] == '!')
            || (ct[ct.length - 1] == '\'')
            || (ct[ct.length - 1] == '"')
        ) {
            CT = ct.substring(0, ct.length - 1)
        }
    }

    /**
     * Standard suffix removal. Search for the longest among the following suffixes, and perform the
     * following actions:
     *
     * @return false if no ending was removed
     */
    private fun step1(): Boolean {
        val ct = CT ?: return false

        if (suffix(ct, "uciones") && suffix(R2, "uciones")) {
            CT = replaceSuffix(ct, "uciones", "u")
            return true
        }

        if (ct.length >= 6) {
            if (suffix(ct, "imentos") && suffix(R2, "imentos")) {
                CT = removeSuffix(ct, "imentos")
                return true
            }
            if (suffix(ct, "amentos") && suffix(R2, "amentos")) {
                CT = removeSuffix(ct, "amentos")
                return true
            }
            if (suffix(ct, "adores") && suffix(R2, "adores")) {
                CT = removeSuffix(ct, "adores")
                return true
            }
            if (suffix(ct, "adoras") && suffix(R2, "adoras")) {
                CT = removeSuffix(ct, "adoras")
                return true
            }
            if (suffix(ct, "logias") && suffix(R2, "logias")) {
                replaceSuffix(ct, "logias", "log")
                return true
            }
            if (suffix(ct, "encias") && suffix(R2, "encias")) {
                CT = replaceSuffix(ct, "encias", "ente")
                return true
            }
            if (suffix(ct, "amente") && suffix(R1, "amente")) {
                CT = removeSuffix(ct, "amente")
                return true
            }
            if (suffix(ct, "idades") && suffix(R2, "idades")) {
                CT = removeSuffix(ct, "idades")
                return true
            }
        }

        if (ct.length >= 5) {
            if (suffix(ct, "acoes") && suffix(R2, "acoes")) {
                CT = removeSuffix(ct, "acoes")
                return true
            }
            if (suffix(ct, "imento") && suffix(R2, "imento")) {
                CT = removeSuffix(ct, "imento")
                return true
            }
            if (suffix(ct, "amento") && suffix(R2, "amento")) {
                CT = removeSuffix(ct, "amento")
                return true
            }
            if (suffix(ct, "adora") && suffix(R2, "adora")) {
                CT = removeSuffix(ct, "adora")
                return true
            }
            if (suffix(ct, "ismos") && suffix(R2, "ismos")) {
                CT = removeSuffix(ct, "ismos")
                return true
            }
            if (suffix(ct, "istas") && suffix(R2, "istas")) {
                CT = removeSuffix(ct, "istas")
                return true
            }
            if (suffix(ct, "logia") && suffix(R2, "logia")) {
                CT = replaceSuffix(ct, "logia", "log")
                return true
            }
            if (suffix(ct, "ucion") && suffix(R2, "ucion")) {
                CT = replaceSuffix(ct, "ucion", "u")
                return true
            }
            if (suffix(ct, "encia") && suffix(R2, "encia")) {
                CT = replaceSuffix(ct, "encia", "ente")
                return true
            }
            if (suffix(ct, "mente") && suffix(R2, "mente")) {
                CT = removeSuffix(ct, "mente")
                return true
            }
            if (suffix(ct, "idade") && suffix(R2, "idade")) {
                CT = removeSuffix(ct, "idade")
                return true
            }
        }

        if (ct.length >= 4) {
            if (suffix(ct, "acao") && suffix(R2, "acao")) {
                CT = removeSuffix(ct, "acao")
                return true
            }
            if (suffix(ct, "ezas") && suffix(R2, "ezas")) {
                CT = removeSuffix(ct, "ezas")
                return true
            }
            if (suffix(ct, "icos") && suffix(R2, "icos")) {
                CT = removeSuffix(ct, "icos")
                return true
            }
            if (suffix(ct, "icas") && suffix(R2, "icas")) {
                CT = removeSuffix(ct, "icas")
                return true
            }
            if (suffix(ct, "ismo") && suffix(R2, "ismo")) {
                CT = removeSuffix(ct, "ismo")
                return true
            }
            if (suffix(ct, "avel") && suffix(R2, "avel")) {
                CT = removeSuffix(ct, "avel")
                return true
            }
            if (suffix(ct, "ivel") && suffix(R2, "ivel")) {
                CT = removeSuffix(ct, "ivel")
                return true
            }
            if (suffix(ct, "ista") && suffix(R2, "ista")) {
                CT = removeSuffix(ct, "ista")
                return true
            }
            if (suffix(ct, "osos") && suffix(R2, "osos")) {
                CT = removeSuffix(ct, "osos")
                return true
            }
            if (suffix(ct, "osas") && suffix(R2, "osas")) {
                CT = removeSuffix(ct, "osas")
                return true
            }
            if (suffix(ct, "ador") && suffix(R2, "ador")) {
                CT = removeSuffix(ct, "ador")
                return true
            }
            if (suffix(ct, "ivas") && suffix(R2, "ivas")) {
                CT = removeSuffix(ct, "ivas")
                return true
            }
            if (suffix(ct, "ivos") && suffix(R2, "ivos")) {
                CT = removeSuffix(ct, "ivos")
                return true
            }
            if (suffix(ct, "iras") && suffix(RV, "iras") && suffixPreceded(ct, "iras", "e")) {
                CT = replaceSuffix(ct, "iras", "ir")
                return true
            }
        }

        if (ct.length >= 3) {
            if (suffix(ct, "eza") && suffix(R2, "eza")) {
                CT = removeSuffix(ct, "eza")
                return true
            }
            if (suffix(ct, "ico") && suffix(R2, "ico")) {
                CT = removeSuffix(ct, "ico")
                return true
            }
            if (suffix(ct, "ica") && suffix(R2, "ica")) {
                CT = removeSuffix(ct, "ica")
                return true
            }
            if (suffix(ct, "oso") && suffix(R2, "oso")) {
                CT = removeSuffix(ct, "oso")
                return true
            }
            if (suffix(ct, "osa") && suffix(R2, "osa")) {
                CT = removeSuffix(ct, "osa")
                return true
            }
            if (suffix(ct, "iva") && suffix(R2, "iva")) {
                CT = removeSuffix(ct, "iva")
                return true
            }
            if (suffix(ct, "ivo") && suffix(R2, "ivo")) {
                CT = removeSuffix(ct, "ivo")
                return true
            }
            if (suffix(ct, "ira") && suffix(RV, "ira") && suffixPreceded(ct, "ira", "e")) {
                CT = replaceSuffix(ct, "ira", "ir")
                return true
            }
        }

        return false
    }

    /**
     * Verb suffixes.
     *
     * Search for the longest among the following suffixes in RV, and if found, delete.
     *
     * @return false if no ending was removed
     */
    private fun step2(): Boolean {
        val rv = RV ?: return false

        if (rv.length >= 7) {
            if (suffix(rv, "issemos")) {
                CT = removeSuffix(CT, "issemos")
                return true
            }
            if (suffix(rv, "essemos")) {
                CT = removeSuffix(CT, "essemos")
                return true
            }
            if (suffix(rv, "assemos")) {
                CT = removeSuffix(CT, "assemos")
                return true
            }
            if (suffix(rv, "ariamos")) {
                CT = removeSuffix(CT, "ariamos")
                return true
            }
            if (suffix(rv, "eriamos")) {
                CT = removeSuffix(CT, "eriamos")
                return true
            }
            if (suffix(rv, "iriamos")) {
                CT = removeSuffix(CT, "iriamos")
                return true
            }
        }

        if (rv.length >= 6) {
            if (suffix(rv, "iremos")) {
                CT = removeSuffix(CT, "iremos")
                return true
            }
            if (suffix(rv, "eremos")) {
                CT = removeSuffix(CT, "eremos")
                return true
            }
            if (suffix(rv, "aremos")) {
                CT = removeSuffix(CT, "aremos")
                return true
            }
            if (suffix(rv, "avamos")) {
                CT = removeSuffix(CT, "avamos")
                return true
            }
            if (suffix(rv, "iramos")) {
                CT = removeSuffix(CT, "iramos")
                return true
            }
            if (suffix(rv, "eramos")) {
                CT = removeSuffix(CT, "eramos")
                return true
            }
            if (suffix(rv, "aramos")) {
                CT = removeSuffix(CT, "aramos")
                return true
            }
            if (suffix(rv, "asseis")) {
                CT = removeSuffix(CT, "asseis")
                return true
            }
            if (suffix(rv, "esseis")) {
                CT = removeSuffix(CT, "esseis")
                return true
            }
            if (suffix(rv, "isseis")) {
                CT = removeSuffix(CT, "isseis")
                return true
            }
            if (suffix(rv, "arieis")) {
                CT = removeSuffix(CT, "arieis")
                return true
            }
            if (suffix(rv, "erieis")) {
                CT = removeSuffix(CT, "erieis")
                return true
            }
            if (suffix(rv, "irieis")) {
                CT = removeSuffix(CT, "irieis")
                return true
            }
        }

        if (rv.length >= 5) {
            if (suffix(rv, "irmos")) {
                CT = removeSuffix(CT, "irmos")
                return true
            }
            if (suffix(rv, "iamos")) {
                CT = removeSuffix(CT, "iamos")
                return true
            }
            if (suffix(rv, "armos")) {
                CT = removeSuffix(CT, "armos")
                return true
            }
            if (suffix(rv, "ermos")) {
                CT = removeSuffix(CT, "ermos")
                return true
            }
            if (suffix(rv, "areis")) {
                CT = removeSuffix(CT, "areis")
                return true
            }
            if (suffix(rv, "ereis")) {
                CT = removeSuffix(CT, "ereis")
                return true
            }
            if (suffix(rv, "ireis")) {
                CT = removeSuffix(CT, "ireis")
                return true
            }
            if (suffix(rv, "asses")) {
                CT = removeSuffix(CT, "asses")
                return true
            }
            if (suffix(rv, "esses")) {
                CT = removeSuffix(CT, "esses")
                return true
            }
            if (suffix(rv, "isses")) {
                CT = removeSuffix(CT, "isses")
                return true
            }
            if (suffix(rv, "astes")) {
                CT = removeSuffix(CT, "astes")
                return true
            }
            if (suffix(rv, "assem")) {
                CT = removeSuffix(CT, "assem")
                return true
            }
            if (suffix(rv, "essem")) {
                CT = removeSuffix(CT, "essem")
                return true
            }
            if (suffix(rv, "issem")) {
                CT = removeSuffix(CT, "issem")
                return true
            }
            if (suffix(rv, "ardes")) {
                CT = removeSuffix(CT, "ardes")
                return true
            }
            if (suffix(rv, "erdes")) {
                CT = removeSuffix(CT, "erdes")
                return true
            }
            if (suffix(rv, "irdes")) {
                CT = removeSuffix(CT, "irdes")
                return true
            }
            if (suffix(rv, "ariam")) {
                CT = removeSuffix(CT, "ariam")
                return true
            }
            if (suffix(rv, "eriam")) {
                CT = removeSuffix(CT, "eriam")
                return true
            }
            if (suffix(rv, "iriam")) {
                CT = removeSuffix(CT, "iriam")
                return true
            }
            if (suffix(rv, "arias")) {
                CT = removeSuffix(CT, "arias")
                return true
            }
            if (suffix(rv, "erias")) {
                CT = removeSuffix(CT, "erias")
                return true
            }
            if (suffix(rv, "irias")) {
                CT = removeSuffix(CT, "irias")
                return true
            }
            if (suffix(rv, "estes")) {
                CT = removeSuffix(CT, "estes")
                return true
            }
            if (suffix(rv, "istes")) {
                CT = removeSuffix(CT, "istes")
                return true
            }
            if (suffix(rv, "areis")) {
                CT = removeSuffix(CT, "areis")
                return true
            }
            if (suffix(rv, "aveis")) {
                CT = removeSuffix(CT, "aveis")
                return true
            }
        }

        if (rv.length >= 4) {
            if (suffix(rv, "aria")) {
                CT = removeSuffix(CT, "aria")
                return true
            }
            if (suffix(rv, "eria")) {
                CT = removeSuffix(CT, "eria")
                return true
            }
            if (suffix(rv, "iria")) {
                CT = removeSuffix(CT, "iria")
                return true
            }
            if (suffix(rv, "asse")) {
                CT = removeSuffix(CT, "asse")
                return true
            }
            if (suffix(rv, "esse")) {
                CT = removeSuffix(CT, "esse")
                return true
            }
            if (suffix(rv, "isse")) {
                CT = removeSuffix(CT, "isse")
                return true
            }
            if (suffix(rv, "aste")) {
                CT = removeSuffix(CT, "aste")
                return true
            }
            if (suffix(rv, "este")) {
                CT = removeSuffix(CT, "este")
                return true
            }
            if (suffix(rv, "iste")) {
                CT = removeSuffix(CT, "iste")
                return true
            }
            if (suffix(rv, "arei")) {
                CT = removeSuffix(CT, "arei")
                return true
            }
            if (suffix(rv, "erei")) {
                CT = removeSuffix(CT, "erei")
                return true
            }
            if (suffix(rv, "irei")) {
                CT = removeSuffix(CT, "irei")
                return true
            }
            if (suffix(rv, "aram")) {
                CT = removeSuffix(CT, "aram")
                return true
            }
            if (suffix(rv, "eram")) {
                CT = removeSuffix(CT, "eram")
                return true
            }
            if (suffix(rv, "iram")) {
                CT = removeSuffix(CT, "iram")
                return true
            }
            if (suffix(rv, "avam")) {
                CT = removeSuffix(CT, "avam")
                return true
            }
            if (suffix(rv, "arem")) {
                CT = removeSuffix(CT, "arem")
                return true
            }
            if (suffix(rv, "erem")) {
                CT = removeSuffix(CT, "erem")
                return true
            }
            if (suffix(rv, "irem")) {
                CT = removeSuffix(CT, "irem")
                return true
            }
            if (suffix(rv, "ando")) {
                CT = removeSuffix(CT, "ando")
                return true
            }
            if (suffix(rv, "endo")) {
                CT = removeSuffix(CT, "endo")
                return true
            }
            if (suffix(rv, "indo")) {
                CT = removeSuffix(CT, "indo")
                return true
            }
            if (suffix(rv, "arao")) {
                CT = removeSuffix(CT, "arao")
                return true
            }
            if (suffix(rv, "erao")) {
                CT = removeSuffix(CT, "erao")
                return true
            }
            if (suffix(rv, "irao")) {
                CT = removeSuffix(CT, "irao")
                return true
            }
            if (suffix(rv, "adas")) {
                CT = removeSuffix(CT, "adas")
                return true
            }
            if (suffix(rv, "idas")) {
                CT = removeSuffix(CT, "idas")
                return true
            }
            if (suffix(rv, "aras")) {
                CT = removeSuffix(CT, "aras")
                return true
            }
            if (suffix(rv, "eras")) {
                CT = removeSuffix(CT, "eras")
                return true
            }
            if (suffix(rv, "iras")) {
                CT = removeSuffix(CT, "iras")
                return true
            }
            if (suffix(rv, "avas")) {
                CT = removeSuffix(CT, "avas")
                return true
            }
            if (suffix(rv, "ares")) {
                CT = removeSuffix(CT, "ares")
                return true
            }
            if (suffix(rv, "eres")) {
                CT = removeSuffix(CT, "eres")
                return true
            }
            if (suffix(rv, "ires")) {
                CT = removeSuffix(CT, "ires")
                return true
            }
            if (suffix(rv, "ados")) {
                CT = removeSuffix(CT, "ados")
                return true
            }
            if (suffix(rv, "idos")) {
                CT = removeSuffix(CT, "idos")
                return true
            }
            if (suffix(rv, "amos")) {
                CT = removeSuffix(CT, "amos")
                return true
            }
            if (suffix(rv, "emos")) {
                CT = removeSuffix(CT, "emos")
                return true
            }
            if (suffix(rv, "imos")) {
                CT = removeSuffix(CT, "imos")
                return true
            }
            if (suffix(rv, "iras")) {
                CT = removeSuffix(CT, "iras")
                return true
            }
            if (suffix(rv, "ieis")) {
                CT = removeSuffix(CT, "ieis")
                return true
            }
        }

        if (rv.length >= 3) {
            if (suffix(rv, "ada")) {
                CT = removeSuffix(CT, "ada")
                return true
            }
            if (suffix(rv, "ida")) {
                CT = removeSuffix(CT, "ida")
                return true
            }
            if (suffix(rv, "ara")) {
                CT = removeSuffix(CT, "ara")
                return true
            }
            if (suffix(rv, "era")) {
                CT = removeSuffix(CT, "era")
                return true
            }
            if (suffix(rv, "ira")) {
                CT = removeSuffix(CT, "ava")
                return true
            }
            if (suffix(rv, "iam")) {
                CT = removeSuffix(CT, "iam")
                return true
            }
            if (suffix(rv, "ado")) {
                CT = removeSuffix(CT, "ado")
                return true
            }
            if (suffix(rv, "ido")) {
                CT = removeSuffix(CT, "ido")
                return true
            }
            if (suffix(rv, "ias")) {
                CT = removeSuffix(CT, "ias")
                return true
            }
            if (suffix(rv, "ais")) {
                CT = removeSuffix(CT, "ais")
                return true
            }
            if (suffix(rv, "eis")) {
                CT = removeSuffix(CT, "eis")
                return true
            }
            if (suffix(rv, "ira")) {
                CT = removeSuffix(CT, "ira")
                return true
            }
            if (suffix(rv, "ear")) {
                CT = removeSuffix(CT, "ear")
                return true
            }
        }

        if (rv.length >= 2) {
            if (suffix(rv, "ia")) {
                CT = removeSuffix(CT, "ia")
                return true
            }
            if (suffix(rv, "ei")) {
                CT = removeSuffix(CT, "ei")
                return true
            }
            if (suffix(rv, "am")) {
                CT = removeSuffix(CT, "am")
                return true
            }
            if (suffix(rv, "em")) {
                CT = removeSuffix(CT, "em")
                return true
            }
            if (suffix(rv, "ar")) {
                CT = removeSuffix(CT, "ar")
                return true
            }
            if (suffix(rv, "er")) {
                CT = removeSuffix(CT, "er")
                return true
            }
            if (suffix(rv, "ir")) {
                CT = removeSuffix(CT, "ir")
                return true
            }
            if (suffix(rv, "as")) {
                CT = removeSuffix(CT, "as")
                return true
            }
            if (suffix(rv, "es")) {
                CT = removeSuffix(CT, "es")
                return true
            }
            if (suffix(rv, "is")) {
                CT = removeSuffix(CT, "is")
                return true
            }
            if (suffix(rv, "eu")) {
                CT = removeSuffix(CT, "eu")
                return true
            }
            if (suffix(rv, "iu")) {
                CT = removeSuffix(CT, "iu")
                return true
            }
            if (suffix(rv, "iu")) {
                CT = removeSuffix(CT, "iu")
                return true
            }
            if (suffix(rv, "ou")) {
                CT = removeSuffix(CT, "ou")
                return true
            }
        }

        return false
    }

    /** Delete suffix 'i' if in RV and preceded by 'c' */
    private fun step3() {
        if (RV == null) return

        if (suffix(RV, "i") && suffixPreceded(RV, "i", "c")) {
            CT = removeSuffix(CT, "i")
        }
    }

    /**
     * Residual suffix
     *
     * If the word ends with one of the suffixes (os a i o á í ó) in RV, delete it
     */
    private fun step4() {
        if (RV == null) return

        if (suffix(RV, "os")) {
            CT = removeSuffix(CT, "os")
            return
        }
        if (suffix(RV, "a")) {
            CT = removeSuffix(CT, "a")
            return
        }
        if (suffix(RV, "i")) {
            CT = removeSuffix(CT, "i")
            return
        }
        if (suffix(RV, "o")) {
            CT = removeSuffix(CT, "o")
            return
        }
    }

    /**
     * If the word ends with one of ( e é ê) in RV,delete it, and if preceded by 'gu' (or 'ci') with
     * the 'u' (or 'i') in RV, delete the 'u' (or 'i')
     *
     * Or if the word ends ç remove the cedilha
     */
    private fun step5() {
        if (RV == null) return

        if (suffix(RV, "e")) {
            if (suffixPreceded(RV, "e", "gu")) {
                CT = removeSuffix(CT, "e")
                CT = removeSuffix(CT, "u")
                return
            }

            if (suffixPreceded(RV, "e", "ci")) {
                CT = removeSuffix(CT, "e")
                CT = removeSuffix(CT, "i")
                return
            }

            CT = removeSuffix(CT, "e")
            return
        }
    }

    /**
     * For log and debug purpose
     *
     * @return TERM, CT, RV, R1 and R2
     */
    fun log(): String {
        return " (TERM = $TERM) (CT = $CT) (RV = $RV) (R1 = $R1) (R2 = $R2)"
    }
}
