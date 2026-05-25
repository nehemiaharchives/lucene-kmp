package org.gnit.lucenekmp.analysis.horn

internal object HornLexicons {
    private val amharicLexicon: Lexicon by lazy { parseLexicon(HornGeneratedData.amharicLexiconData) }
    private val oromoLexicon: Lexicon by lazy { parseLexicon(HornGeneratedData.oromoLexiconData) }
    private val amharicGeezSera: Map<Char, String> by lazy { parseGeezSera(HornGeneratedData.amharicGeezSeraData) }

    fun amharicStem(term: String): String? {
        val roman = if (term.any { it.code > 0x1200 }) toSimplifiedSera(term) else simplifySera(term)
        return amharicLexicon.lookup(roman, AMHARIC_PREFIXES, AMHARIC_SUFFIXES)
    }

    fun oromoStem(term: String): String? {
        return oromoLexicon.lookup(term.lowercase(), emptyArray(), OROMO_SUFFIXES)
    }

    private fun parseLexicon(data: String): Lexicon {
        val words = mutableSetOf<String>()
        val analyses = mutableMapOf<String, String>()
        for (line in data.lineSequence()) {
            if (line.isEmpty()) continue
            val tab = line.indexOf('\t')
            if (tab < 0) {
                words.add(line)
            } else {
                val surface = line.substring(0, tab)
                val lemma = line.substring(tab + 1)
                words.add(lemma)
                analyses[surface] = lemma
            }
        }
        return Lexicon(words, analyses)
    }

    private fun parseGeezSera(data: String): Map<Char, String> {
        val result = mutableMapOf<Char, String>()
        for (line in data.lineSequence()) {
            if (line.isEmpty()) continue
            val tab = line.indexOf('\t')
            if (tab == 1 && tab < line.length - 1) {
                result[line[0]] = line.substring(tab + 1)
            }
        }
        return result
    }

    private fun toSimplifiedSera(term: String): String {
        val builder = StringBuilder()
        for (ch in term) {
            builder.append(amharicGeezSera[ch] ?: ch)
        }
        return simplifySera(builder.toString())
    }

    private fun simplifySera(term: String): String {
        var result = term.replace("^", "")
            .replace('H', 'h')
            .replace('`', '\'')
        result = result.replace("Ke", "!!")
            .replace('K', 'h')
            .replace("!!", "Ke")
        return result
    }

    private data class Lexicon(
        val words: Set<String>,
        val analyses: Map<String, String>
    ) {
        fun lookup(term: String, prefixes: Array<String>, suffixes: Array<String>): String? {
            analyses[term]?.let { return it }
            if (words.contains(term)) return term
            var candidate = stripPrefix(term, prefixes)
            analyses[candidate]?.let { return it }
            if (words.contains(candidate)) return candidate
            repeat(2) {
                candidate = stripSuffix(candidate, suffixes)
                analyses[candidate]?.let { return it }
                if (words.contains(candidate)) return candidate
            }
            return null
        }

        private fun stripPrefix(term: String, prefixes: Array<String>): String {
            for (prefix in prefixes) {
                if (term.length > prefix.length + 2 && term.startsWith(prefix)) {
                    return term.substring(prefix.length)
                }
            }
            return term
        }

        private fun stripSuffix(term: String, suffixes: Array<String>): String {
            for (suffix in suffixes) {
                if (term.length > suffix.length + 2 && term.endsWith(suffix)) {
                    return term.substring(0, term.length - suffix.length)
                }
            }
            return term
        }
    }

    private val AMHARIC_PREFIXES: Array<String> = arrayOf(
        "'nde",
        "yemay",
        "yal",
        "le",
        "be",
        "ke",
        "ye",
        "s"
    )

    private val AMHARIC_SUFFIXES: Array<String> = arrayOf(
        "Wocacnm",
        "Wocacn",
        "Wocnm",
        "Wocn",
        "Wocm",
        "Woc",
        "ocacnm",
        "ocacn",
        "ocnm",
        "ocn",
        "ocm",
        "oc",
        "ac_ew",
        "c_ew",
        "cnm",
        "cn",
        "m",
        "n",
        "s"
    )

    private val OROMO_SUFFIXES: Array<String> = arrayOf(
        "oota",
        "wwan",
        "leen",
        "oota",
        "tti",
        "irra",
        "iin",
        "aan",
        "een",
        "manii",
        "mani",
        "ani",
        "ne",
        "te",
        "tu",
        "ti",
        "ni",
        "n"
    )
}
