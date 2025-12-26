package org.gnit.lucenekmp.analysis.es

import org.gnit.lucenekmp.analysis.CharArraySet

/**
 * Plural Stemmer for Spanish.
 *
 * This stemmer implements the rules described in:
 * http://www.wikilengua.org/index.php/Plural_(formación)
 */
internal class SpanishPluralStemmer {
    fun stem(s: CharArray, len: Int): Int {
        if (len < 4) return len
        removeAccents(s, len)
        if (invariant(s, len)) return len
        if (special(s, len)) return len - 2
        if (s[len - 1] == 's') {
            if (!isVowel(s[len - 2])) {
                return len - 1
            }
            if ((s[len - 4] == 'q'
                    || (s[len - 4] == 'g') && s[len - 3] == 'u'
                    && (s[len - 2] == 'i' || s[len - 2] == 'e'))
            ) {
                return len - 1
            }
            if (isVowel(s[len - 4]) && s[len - 3] == 'r' && s[len - 2] == 'e') {
                return len - 2
            }
            if (isVowel(s[len - 4])
                && (s[len - 3] == 'd' || s[len - 3] == 'l' || s[len - 3] == 'n' || s[len - 3] == 'x')
                && s[len - 2] == 'e'
            ) {
                return len - 2
            }
            if ((s[len - 3] == 'y' || s[len - 3] == 'u') && s[len - 2] == 'e') {
                return len - 2
            }
            if ((s[len - 4] == 'u'
                    || s[len - 4] == 'l'
                    || s[len - 4] == 'r'
                    || s[len - 4] == 't'
                    || s[len - 4] == 'n')
                && s[len - 3] == 'i'
                && s[len - 2] == 'e'
            ) {
                return len - 2
            }
            if (s[len - 3] == 's' && s[len - 2] == 'e') {
                return len - 2
            }
            if (isVowel(s[len - 3]) && s[len - 2] == 'i') {
                s[len - 2] = 'y'
                return len - 1
            }
            if (s[len - 3] == 'd' && s[len - 2] == 'i') {
                s[len - 2] = 'y'
                return len - 1
            }
            if (s[len - 2] == 'e' && s[len - 3] == 'c') {
                s[len - 3] = 'z'
                return len - 2
            }
            if (isVowel(s[len - 2])) {
                return len - 1
            }
        }
        return len
    }

    private fun isVowel(c: Char): Boolean {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u'
    }

    private fun invariant(s: CharArray, len: Int): Boolean {
        return INVARIANTS.contains(s, 0, len)
    }

    private fun special(s: CharArray, len: Int): Boolean {
        return SPECIAL_CASES.contains(s, 0, len)
    }

    private fun removeAccents(s: CharArray, len: Int) {
        for (i in 0 until len) {
            when (s[i]) {
                'à', 'á', 'â', 'ä' -> s[i] = 'a'
                'ò', 'ó', 'ô', 'ö' -> s[i] = 'o'
                'è', 'é', 'ê', 'ë' -> s[i] = 'e'
                'ù', 'ú', 'û', 'ü' -> s[i] = 'u'
                'ì', 'í', 'î', 'ï' -> s[i] = 'i'
            }
        }
    }

    companion object {
        private val INVARIANTS_LIST = arrayOf(
            "abrebotellas",
            "abrecartas",
            "abrelatas",
            "afueras",
            "albatros",
            "albricias",
            "aledaños",
            "alexis",
            "alicates",
            "analisis",
            "andurriales",
            "antitesis",
            "añicos",
            "apendicitis",
            "apocalipsis",
            "arcoiris",
            "aries",
            "bilis",
            "boletus",
            "boris",
            "brindis",
            "cactus",
            "canutas",
            "caries",
            "cascanueces",
            "cascarrabias",
            "ciempies",
            "cifosis",
            "cortaplumas",
            "corpus",
            "cosmos",
            "cosquillas",
            "creces",
            "crisis",
            "cuatrocientas",
            "cuatrocientos",
            "cuelgacapas",
            "cuentacuentos",
            "cuentapasos",
            "cumpleaños",
            "doscientas",
            "doscientos",
            "dosis",
            "enseres",
            "entonces",
            "esponsales",
            "estatus",
            "exequias",
            "fauces",
            "forceps",
            "fotosintesis",
            "gafas",
            "gafotas",
            "gargaras",
            "gris",
            "honorarios",
            "ictus",
            "jueves",
            "lapsus",
            "lavacoches",
            "lavaplatos",
            "limpiabotas",
            "lunes",
            "maitines",
            "martes",
            "mondadientes",
            "novecientas",
            "novecientos",
            "nupcias",
            "ochocientas",
            "ochocientos",
            "pais",
            "paris",
            "parabrisas",
            "paracaidas",
            "parachoques",
            "paraguas",
            "pararrayos",
            "pisapapeles",
            "piscis",
            "portaaviones",
            "portamaletas",
            "portamantas",
            "quinientas",
            "quinientos",
            "quitamanchas",
            "recogepelotas",
            "rictus",
            "rompeolas",
            "sacacorchos",
            "sacapuntas",
            "saltamontes",
            "salvavidas",
            "seis",
            "seiscientas",
            "seiscientos",
            "setecientas",
            "setecientos",
            "sintesis",
            "tenis",
            "tifus",
            "trabalenguas",
            "vacaciones",
            "venus",
            "versus",
            "viacrucis",
            "virus",
            "viveres",
            "volandas"
        )

        private val SPECIAL_CASES_LIST = arrayOf(
            "yoes",
            "noes",
            "sies",
            "clubes",
            "faralaes",
            "albalaes",
            "itemes",
            "albumes",
            "sandwiches",
            "relojes",
            "bojes",
            "contrarreloj",
            "carcajes"
        )

        private val INVARIANTS: CharArraySet
        private val SPECIAL_CASES: CharArraySet

        init {
            val invariantSet = CharArraySet(mutableListOf<Any>(*INVARIANTS_LIST), true)
            INVARIANTS = CharArraySet.unmodifiableSet(invariantSet)
            val specialSet = CharArraySet(mutableListOf<Any>(*SPECIAL_CASES_LIST), true)
            SPECIAL_CASES = CharArraySet.unmodifiableSet(specialSet)
        }
    }
}
