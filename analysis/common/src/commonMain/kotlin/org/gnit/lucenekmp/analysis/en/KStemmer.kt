package org.gnit.lucenekmp.analysis.en

import org.gnit.lucenekmp.analysis.CharArrayMap
import org.gnit.lucenekmp.analysis.util.OpenStringBuilder

/** This class implements the Kstem algorithm. */
internal class KStemmer {
    private val word = OpenStringBuilder()

    private var j = 0
    private var k = 0

    private var matchedEntry: DictEntry? = null
    private var result: String? = null

    private fun penultChar(): Char {
        return word[k - 1]
    }

    private fun isVowel(index: Int): Boolean {
        return !isCons(index)
    }

    private fun isCons(index: Int): Boolean {
        val ch = word[index]
        if (ch == 'a' || ch == 'e' || ch == 'i' || ch == 'o' || ch == 'u') return false
        if (ch != 'y' || index == 0) return true
        return !isCons(index - 1)
    }

    private fun isAlpha(ch: Char): Boolean {
        return ch >= 'a' && ch <= 'z'
    }

    private fun stemLength(): Int {
        return j + 1
    }

    private fun endsIn(s: CharArray): Boolean {
        if (s.size > k) return false
        val r = word.length - s.size
        j = k
        var r1 = r
        var i = 0
        while (i < s.size) {
            if (s[i] != word[r1]) return false
            i++
            r1++
        }
        j = r - 1
        return true
    }

    private fun endsIn(a: Char, b: Char): Boolean {
        if (2 > k) return false
        if (word[k - 1] == a && word[k] == b) {
            j = k - 2
            return true
        }
        return false
    }

    private fun endsIn(a: Char, b: Char, c: Char): Boolean {
        if (3 > k) return false
        if (word[k - 2] == a && word[k - 1] == b && word[k] == c) {
            j = k - 3
            return true
        }
        return false
    }

    private fun endsIn(a: Char, b: Char, c: Char, d: Char): Boolean {
        if (4 > k) return false
        if (word[k - 3] == a && word[k - 2] == b && word[k - 1] == c && word[k] == d) {
            j = k - 4
            return true
        }
        return false
    }

    private fun wordInDict(): DictEntry? {
        if (matchedEntry != null) return matchedEntry
        val e = dict_ht.get(word.getArray(), 0, word.length)
        if (e != null && !e.exception) {
            matchedEntry = e
        }
        return e
    }

    private fun plural() {
        if (word[k] == 's') {
            if (endsIn('i', 'e', 's')) {
                word.setLength(j + 3)
                k--
                if (lookup()) return
                k++
                word.unsafeWrite('s')
                setSuffix("y")
                lookup()
            } else if (endsIn('e', 's')) {
                word.setLength(j + 2)
                k--

                val tryE = j > 0 && !((word[j] == 's') && (word[j - 1] == 's'))
                if (tryE && lookup()) return

                word.setLength(j + 1)
                k--
                if (lookup()) return

                word.unsafeWrite('e')
                k++

                if (!tryE) lookup()
                return
            } else {
                if (word.length > 3 && penultChar() != 's' && !endsIn('o', 'u', 's')) {
                    word.setLength(k)
                    k--
                    lookup()
                }
            }
        }
    }

    private fun setSuffix(s: String) {
        setSuff(s, s.length)
    }

    private fun setSuff(s: String, len: Int) {
        word.setLength(j + 1)
        for (l in 0 until len) {
            word.unsafeWrite(s[l])
        }
        k = j + len
    }

    private fun lookup(): Boolean {
        matchedEntry = dict_ht.get(word.getArray(), 0, word.size())
        return matchedEntry != null
    }

    private fun pastTense() {
        if (word.length <= 4) return

        if (endsIn('i', 'e', 'd')) {
            word.setLength(j + 3)
            k--
            if (lookup()) return
            k++
            word.unsafeWrite('d')
            setSuffix("y")
            lookup()
            return
        }

        if (endsIn('e', 'd') && vowelInStem()) {
            word.setLength(j + 2)
            k = j + 1

            val entry = wordInDict()
            if (entry != null && !entry.exception) return

            word.setLength(j + 1)
            k = j
            if (lookup()) return

            if (doubleC(k)) {
                word.setLength(k)
                k--
                if (lookup()) return
                word.unsafeWrite(word[k])
                k++
                lookup()
                return
            }

            if (word[0] == 'u' && word[1] == 'n') {
                word.unsafeWrite('e')
                word.unsafeWrite('d')
                k = k + 2
                return
            }

            word.setLength(j + 1)
            word.unsafeWrite('e')
            k = j + 1
            return
        }
    }

    private fun doubleC(i: Int): Boolean {
        if (i < 1) return false
        if (word[i] != word[i - 1]) return false
        return isCons(i)
    }

    private fun vowelInStem(): Boolean {
        for (i in 0 until stemLength()) {
            if (isVowel(i)) return true
        }
        return false
    }

    private fun aspect() {
        if (word.length <= 5) return

        if (endsIn('i', 'n', 'g') && vowelInStem()) {
            word.setCharAt(j + 1, 'e')
            word.setLength(j + 2)
            k = j + 1

            val entry = wordInDict()
            if (entry != null && !entry.exception) return

            word.setLength(k)
            k--

            if (lookup()) return

            if (doubleC(k)) {
                k--
                word.setLength(k + 1)
                if (lookup()) return
                word.unsafeWrite(word[k])
                k++
                lookup()
                return
            }

            if (j > 0 && isCons(j) && isCons(j - 1)) {
                k = j
                word.setLength(k + 1)
                return
            }

            word.setLength(j + 1)
            word.unsafeWrite('e')
            k = j + 1
            return
        }
    }

    private fun ityEndings() {
        val oldK = k
        if (endsIn('i', 't', 'y')) {
            word.setLength(j + 1)
            k = j
            if (lookup()) return
            word.unsafeWrite('e')
            k = j + 1
            if (lookup()) return
            word.setCharAt(j + 1, 'i')
            word.append("ty")
            k = oldK

            if (j > 0 && word[j - 1] == 'i' && word[j] == 'l') {
                word.setLength(j - 1)
                word.append("le")
                k = j
                lookup()
                return
            }

            if (j > 0 && word[j - 1] == 'i' && word[j] == 'v') {
                word.setLength(j + 1)
                word.unsafeWrite('e')
                k = j + 1
                lookup()
                return
            }

            if (j > 0 && word[j - 1] == 'a' && word[j] == 'l') {
                word.setLength(j + 1)
                k = j
                lookup()
                return
            }

            if (lookup()) return

            word.setLength(j + 1)
            k = j
            return
        }
    }

    private fun nceEndings() {
        val oldK = k
        if (endsIn('n', 'c', 'e')) {
            val wordChar = word[j]
            if (!(wordChar == 'e' || wordChar == 'a')) return
            word.setLength(j)
            word.unsafeWrite('e')
            k = j
            if (lookup()) return
            word.setLength(j)
            k = j - 1
            if (lookup()) return
            word.unsafeWrite(wordChar)
            word.append("nce")
            k = oldK
        }
    }

    private fun nessEndings() {
        if (endsIn('n', 'e', 's', 's')) {
            word.setLength(j + 1)
            k = j
            if (word[j] == 'i') word.setCharAt(j, 'y')
            lookup()
        }
    }

    private fun ismEndings() {
        if (endsIn('i', 's', 'm')) {
            word.setLength(j + 1)
            k = j
            lookup()
        }
    }

    private fun mentEndings() {
        val oldK = k
        if (endsIn('m', 'e', 'n', 't')) {
            word.setLength(j + 1)
            k = j
            if (lookup()) return
            word.append("ment")
            k = oldK
        }
    }

    private fun izeEndings() {
        val oldK = k
        if (endsIn('i', 'z', 'e')) {
            word.setLength(j + 1)
            k = j
            if (lookup()) return
            word.unsafeWrite('i')

            if (doubleC(j)) {
                word.setLength(j)
                k = j - 1
                if (lookup()) return
                word.unsafeWrite(word[j - 1])
            }

            word.setLength(j + 1)
            word.unsafeWrite('e')
            k = j + 1
            if (lookup()) return
            word.setLength(j + 1)
            word.append("ize")
            k = oldK
        }
    }

    private fun ncyEndings() {
        if (endsIn('n', 'c', 'y')) {
            if (!(word[j] == 'e' || word[j] == 'a')) return
            word.setCharAt(j + 2, 't')
            word.setLength(j + 3)
            k = j + 2

            if (lookup()) return

            word.setCharAt(j + 2, 'c')
            word.unsafeWrite('e')
            k = j + 3
            lookup()
        }
    }

    private fun bleEndings() {
        val oldK = k
        if (endsIn('b', 'l', 'e')) {
            if (!(word[j] == 'a' || word[j] == 'i')) return
            val wordChar = word[j]
            word.setLength(j)
            k = j - 1
            if (lookup()) return
            if (doubleC(k)) {
                word.setLength(k)
                k--
                if (lookup()) return
                k++
                word.unsafeWrite(word[k - 1])
            }
            word.setLength(j)
            word.unsafeWrite('e')
            k = j
            if (lookup()) return
            word.setLength(j)
            word.append("ate")
            k = j + 2
            if (lookup()) return
            word.setLength(j)
            word.unsafeWrite(wordChar)
            word.append("ble")
            k = oldK
        }
    }

    private fun icEndings() {
        if (endsIn('i', 'c')) {
            word.setLength(j + 3)
            word.append("al")
            k = j + 4
            if (lookup()) return

            word.setCharAt(j + 1, 'y')
            word.setLength(j + 2)
            k = j + 1
            if (lookup()) return

            word.setCharAt(j + 1, 'e')
            if (lookup()) return

            word.setLength(j + 1)
            k = j
            if (lookup()) return
            word.append("ic")
            k = j + 2
        }
    }

    private fun ionEndings() {
        val oldK = k
        if (!endsIn('i', 'o', 'n')) {
            return
        }

        if (endsIn(ization)) {
            word.setLength(j + 3)
            word.unsafeWrite('e')
            k = j + 3
            lookup()
            return
        }

        if (endsIn(ition)) {
            word.setLength(j + 1)
            word.unsafeWrite('e')
            k = j + 1
            if (lookup()) return

            word.setLength(j + 1)
            word.append("ition")
            k = oldK
        } else if (endsIn(ation)) {
            word.setLength(j + 3)
            word.unsafeWrite('e')
            k = j + 3
            if (lookup()) return

            word.setLength(j + 1)
            word.unsafeWrite('e')
            k = j + 1
            if (lookup()) return

            word.setLength(j + 1)
            k = j
            if (lookup()) return

            word.setLength(j + 1)
            word.append("ation")
            k = oldK
        }

        if (endsIn(ication)) {
            word.setLength(j + 1)
            word.unsafeWrite('y')
            k = j + 1
            if (lookup()) return

            word.setLength(j + 1)
            word.append("ication")
            k = oldK
        }

        run {
            j = k - 3
            word.setLength(j + 1)
            word.unsafeWrite('e')
            k = j + 1
            if (lookup()) return

            word.setLength(j + 1)
            k = j
            if (lookup()) return

            word.setLength(j + 1)
            word.append("ion")
            k = oldK
        }
    }

    private fun erAndOrEndings() {
        val oldK = k
        if (word[k] != 'r') return

        if (endsIn('i', 'z', 'e', 'r')) {
            word.setLength(j + 4)
            k = j + 3
            lookup()
            return
        }

        if (endsIn('e', 'r') || endsIn('o', 'r')) {
            val wordChar = word[j + 1]
            if (doubleC(j)) {
                word.setLength(j)
                k = j - 1
                if (lookup()) return
                word.unsafeWrite(word[j - 1])
            }

            if (word[j] == 'i') {
                word.setCharAt(j, 'y')
                word.setLength(j + 1)
                k = j
                if (lookup()) return
                word.setCharAt(j, 'i')
                word.unsafeWrite('e')
            }

            if (word[j] == 'e') {
                word.setLength(j)
                k = j - 1
                if (lookup()) return
                word.unsafeWrite('e')
            }

            word.setLength(j + 2)
            k = j + 1
            if (lookup()) return
            word.setLength(j + 1)
            k = j
            if (lookup()) return
            word.unsafeWrite('e')
            k = j + 1
            if (lookup()) return
            word.setLength(j + 1)
            word.unsafeWrite(wordChar)
            word.unsafeWrite('r')
            k = oldK
        }
    }

    private fun lyEndings() {
        val oldK = k
        if (endsIn('l', 'y')) {
            word.setCharAt(j + 2, 'e')
            if (lookup()) return
            word.setCharAt(j + 2, 'y')

            word.setLength(j + 1)
            k = j
            if (lookup()) return

            if (j > 0 && word[j - 1] == 'a' && word[j] == 'l') return
            word.append("ly")
            k = oldK

            if (j > 0 && word[j - 1] == 'a' && word[j] == 'b') {
                word.setCharAt(j + 2, 'e')
                k = j + 2
                return
            }

            if (word[j] == 'i') {
                word.setLength(j)
                word.unsafeWrite('y')
                k = j
                if (lookup()) return
                word.setLength(j)
                word.append("ily")
                k = oldK
            }

            word.setLength(j + 1)
            k = j
        }
    }

    private fun alEndings() {
        val oldK = k
        if (word.length < 4) return
        if (endsIn('a', 'l')) {
            word.setLength(j + 1)
            k = j
            if (lookup()) return

            if (doubleC(j)) {
                word.setLength(j)
                k = j - 1
                if (lookup()) return
                word.unsafeWrite(word[j - 1])
            }

            word.setLength(j + 1)
            word.unsafeWrite('e')
            k = j + 1
            if (lookup()) return

            word.setLength(j + 1)
            word.append("um")
            k = j + 2
            if (lookup()) return

            word.setLength(j + 1)
            word.append("al")
            k = oldK

            if (j > 0 && word[j - 1] == 'i' && word[j] == 'c') {
                word.setLength(j - 1)
                k = j - 2
                if (lookup()) return

                word.setLength(j - 1)
                word.unsafeWrite('y')
                k = j - 1
                if (lookup()) return

                word.setLength(j - 1)
                word.append("ic")
                k = j
                lookup()
                return
            }

            if (word[j] == 'i') {
                word.setLength(j)
                k = j - 1
                if (lookup()) return
                word.append("ial")
                k = oldK
                lookup()
            }
        }
    }

    private fun iveEndings() {
        val oldK = k
        if (endsIn('i', 'v', 'e')) {
            word.setLength(j + 1)
            k = j
            if (lookup()) return

            word.unsafeWrite('e')
            k = j + 1
            if (lookup()) return
            word.setLength(j + 1)
            word.append("ive")
            if (j > 0 && word[j - 1] == 'a' && word[j] == 't') {
                word.setCharAt(j - 1, 'e')
                word.setLength(j)
                k = j - 1
                if (lookup()) return
                word.setLength(j - 1)
                if (lookup()) return

                word.append("ative")
                k = oldK
            }

            word.setCharAt(j + 2, 'o')
            word.setCharAt(j + 3, 'n')
            if (lookup()) return

            word.setCharAt(j + 2, 'v')
            word.setCharAt(j + 3, 'e')
            k = oldK
        }
    }

    fun stem(term: String): String {
        val changed = stem(term.toCharArray(), term.length)
        if (!changed) return term
        return asString()
    }

    fun asString(): String {
        val s = getString()
        return s ?: word.toString()
    }

    fun asCharSequence(): CharSequence {
        return result ?: word
    }

    fun getString(): String? {
        return result
    }

    fun getChars(): CharArray {
        return word.getArray()
    }

    fun getLength(): Int {
        return word.length
    }

    private fun matched(): Boolean {
        return matchedEntry != null
    }

    fun stem(term: CharArray, len: Int): Boolean {
        result = null

        k = len - 1
        if (k <= 1 || k >= MaxWordLen - 1) {
            return false
        }

        var entry = dict_ht.get(term, 0, len)
        if (entry != null) {
            if (entry.root != null) {
                result = entry.root
                return true
            }
            return false
        }

        word.reset()
        word.reserve(len + 10)
        for (i in 0 until len) {
            val ch = term[i]
            if (!isAlpha(ch)) return false
            word.unsafeWrite(ch)
        }

        matchedEntry = null

        while (true) {
            plural()
            if (matched()) break
            pastTense()
            if (matched()) break
            aspect()
            if (matched()) break
            ityEndings()
            if (matched()) break
            nessEndings()
            if (matched()) break
            ionEndings()
            if (matched()) break
            erAndOrEndings()
            if (matched()) break
            lyEndings()
            if (matched()) break
            alEndings()
            if (matched()) break
            entry = wordInDict()
            iveEndings()
            if (matched()) break
            izeEndings()
            if (matched()) break
            mentEndings()
            if (matched()) break
            bleEndings()
            if (matched()) break
            ismEndings()
            if (matched()) break
            icEndings()
            if (matched()) break
            ncyEndings()
            if (matched()) break
            nceEndings()
            matched()
            break
        }

        entry = matchedEntry
        if (entry != null) {
            result = entry.root
        }

        return true
    }

    companion object {
        private const val MaxWordLen = 50

        private val exceptionWords: Array<String> = arrayOf(
            "aide",
            "bathe",
            "caste",
            "cute",
            "dame",
            "dime",
            "doge",
            "done",
            "dune",
            "envelope",
            "gage",
            "grille",
            "grippe",
            "lobe",
            "mane",
            "mare",
            "nape",
            "node",
            "pane",
            "pate",
            "plane",
            "pope",
            "programme",
            "quite",
            "ripe",
            "rote",
            "rune",
            "sage",
            "severe",
            "shoppe",
            "sine",
            "slime",
            "snipe",
            "steppe",
            "suite",
            "swinge",
            "tare",
            "tine",
            "tope",
            "tripe",
            "twine"
        )

        private val supplementDict: Array<String> = arrayOf(
            "aids",
            "applicator",
            "capacitor",
            "digitize",
            "electromagnet",
            "ellipsoid",
            "exosphere",
            "extensible",
            "ferromagnet",
            "graphics",
            "hydromagnet",
            "polygraph",
            "toroid",
            "superconduct",
            "backscatter",
            "connectionism"
        )

        private val properNouns: Array<String> = arrayOf(
            "abrams",
            "achilles",
            "acropolis",
            "adams",
            "agnes",
            "aires",
            "alexander",
            "alexis",
            "alfred",
            "algiers",
            "alps",
            "amadeus",
            "ames",
            "amos",
            "andes",
            "angeles",
            "annapolis",
            "antilles",
            "aquarius",
            "archimedes",
            "arkansas",
            "asher",
            "ashly",
            "athens",
            "atkins",
            "atlantis",
            "avis",
            "bahamas",
            "bangor",
            "barbados",
            "barger",
            "bering",
            "brahms",
            "brandeis",
            "brussels",
            "bruxelles",
            "cairns",
            "camoros",
            "camus",
            "carlos",
            "celts",
            "chalker",
            "charles",
            "cheops",
            "ching",
            "christmas",
            "cocos",
            "collins",
            "columbus",
            "confucius",
            "conners",
            "connolly",
            "copernicus",
            "cramer",
            "cyclops",
            "cygnus",
            "cyprus",
            "dallas",
            "damascus",
            "daniels",
            "davies",
            "davis",
            "decker",
            "denning",
            "dennis",
            "descartes",
            "dickens",
            "doris",
            "douglas",
            "downs",
            "dreyfus",
            "dukakis",
            "dulles",
            "dumfries",
            "ecclesiastes",
            "edwards",
            "emily",
            "erasmus",
            "euphrates",
            "evans",
            "everglades",
            "fairbanks",
            "federales",
            "fisher",
            "fitzsimmons",
            "fleming",
            "forbes",
            "fowler",
            "france",
            "francis",
            "goering",
            "goodling",
            "goths",
            "grenadines",
            "guiness",
            "hades",
            "harding",
            "harris",
            "hastings",
            "hawkes",
            "hawking",
            "hayes",
            "heights",
            "hercules",
            "himalayas",
            "hippocrates",
            "hobbs",
            "holmes",
            "honduras",
            "hopkins",
            "hughes",
            "humphreys",
            "illinois",
            "indianapolis",
            "inverness",
            "iris",
            "iroquois",
            "irving",
            "isaacs",
            "italy",
            "james",
            "jarvis",
            "jeffreys",
            "jesus",
            "jones",
            "josephus",
            "judas",
            "julius",
            "kansas",
            "keynes",
            "kipling",
            "kiwanis",
            "lansing",
            "laos",
            "leeds",
            "levis",
            "leviticus",
            "lewis",
            "louis",
            "maccabees",
            "madras",
            "maimonides",
            "maldive",
            "massachusetts",
            "matthews",
            "mauritius",
            "memphis",
            "mercedes",
            "midas",
            "mingus",
            "minneapolis",
            "mohammed",
            "moines",
            "morris",
            "moses",
            "myers",
            "myknos",
            "nablus",
            "nanjing",
            "nantes",
            "naples",
            "neal",
            "netherlands",
            "nevis",
            "nostradamus",
            "oedipus",
            "olympus",
            "orleans",
            "orly",
            "papas",
            "paris",
            "parker",
            "pauling",
            "peking",
            "pershing",
            "peter",
            "peters",
            "philippines",
            "phineas",
            "pisces",
            "pryor",
            "pythagoras",
            "queens",
            "rabelais",
            "ramses",
            "reynolds",
            "rhesus",
            "rhodes",
            "richards",
            "robins",
            "rodgers",
            "rogers",
            "rubens",
            "sagittarius",
            "seychelles",
            "socrates",
            "texas",
            "thames",
            "thomas",
            "tiberias",
            "tunis",
            "venus",
            "vilnius",
            "wales",
            "warner",
            "wilkins",
            "williams",
            "wyoming",
            "xmas",
            "yonkers",
            "zeus",
            "frances",
            "aarhus",
            "adonis",
            "andrews",
            "angus",
            "antares",
            "aquinas",
            "arcturus",
            "ares",
            "artemis",
            "augustus",
            "ayers",
            "barnabas",
            "barnes",
            "becker",
            "bejing",
            "biggs",
            "billings",
            "boeing",
            "boris",
            "borroughs",
            "briggs",
            "buenos",
            "calais",
            "caracas",
            "cassius",
            "cerberus",
            "ceres",
            "cervantes",
            "chantilly",
            "chartres",
            "chester",
            "connally",
            "conner",
            "coors",
            "cummings",
            "curtis",
            "daedalus",
            "dionysus",
            "dobbs",
            "dolores",
            "edmonds"
        )

        private val directConflations: Array<Array<String>> = arrayOf(
            arrayOf("aging", "age"),
            arrayOf("going", "go"),
            arrayOf("goes", "go"),
            arrayOf("lying", "lie"),
            arrayOf("using", "use"),
            arrayOf("owing", "owe"),
            arrayOf("suing", "sue"),
            arrayOf("dying", "die"),
            arrayOf("tying", "tie"),
            arrayOf("vying", "vie"),
            arrayOf("aged", "age"),
            arrayOf("used", "use"),
            arrayOf("vied", "vie"),
            arrayOf("cued", "cue"),
            arrayOf("died", "die"),
            arrayOf("eyed", "eye"),
            arrayOf("hued", "hue"),
            arrayOf("iced", "ice"),
            arrayOf("lied", "lie"),
            arrayOf("owed", "owe"),
            arrayOf("sued", "sue"),
            arrayOf("toed", "toe"),
            arrayOf("tied", "tie"),
            arrayOf("does", "do"),
            arrayOf("doing", "do"),
            arrayOf("aeronautical", "aeronautics"),
            arrayOf("mathematical", "mathematics"),
            arrayOf("political", "politics"),
            arrayOf("metaphysical", "metaphysics"),
            arrayOf("cylindrical", "cylinder"),
            arrayOf("nazism", "nazi"),
            arrayOf("ambiguity", "ambiguous"),
            arrayOf("barbarity", "barbarous"),
            arrayOf("credulity", "credulous"),
            arrayOf("generosity", "generous"),
            arrayOf("spontaneity", "spontaneous"),
            arrayOf("unanimity", "unanimous"),
            arrayOf("voracity", "voracious"),
            arrayOf("fled", "flee"),
            arrayOf("miscarriage", "miscarry")
        )

        private val countryNationality: Array<Array<String>> = arrayOf(
            arrayOf("afghan", "afghanistan"),
            arrayOf("african", "africa"),
            arrayOf("albanian", "albania"),
            arrayOf("algerian", "algeria"),
            arrayOf("american", "america"),
            arrayOf("andorran", "andorra"),
            arrayOf("angolan", "angola"),
            arrayOf("arabian", "arabia"),
            arrayOf("argentine", "argentina"),
            arrayOf("armenian", "armenia"),
            arrayOf("asian", "asia"),
            arrayOf("australian", "australia"),
            arrayOf("austrian", "austria"),
            arrayOf("azerbaijani", "azerbaijan"),
            arrayOf("azeri", "azerbaijan"),
            arrayOf("bangladeshi", "bangladesh"),
            arrayOf("belgian", "belgium"),
            arrayOf("bermudan", "bermuda"),
            arrayOf("bolivian", "bolivia"),
            arrayOf("bosnian", "bosnia"),
            arrayOf("botswanan", "botswana"),
            arrayOf("brazilian", "brazil"),
            arrayOf("british", "britain"),
            arrayOf("bulgarian", "bulgaria"),
            arrayOf("burmese", "burma"),
            arrayOf("californian", "california"),
            arrayOf("cambodian", "cambodia"),
            arrayOf("canadian", "canada"),
            arrayOf("chadian", "chad"),
            arrayOf("chilean", "chile"),
            arrayOf("chinese", "china"),
            arrayOf("colombian", "colombia"),
            arrayOf("croat", "croatia"),
            arrayOf("croatian", "croatia"),
            arrayOf("cuban", "cuba"),
            arrayOf("cypriot", "cyprus"),
            arrayOf("czechoslovakian", "czechoslovakia"),
            arrayOf("danish", "denmark"),
            arrayOf("egyptian", "egypt"),
            arrayOf("equadorian", "equador"),
            arrayOf("eritrean", "eritrea"),
            arrayOf("estonian", "estonia"),
            arrayOf("ethiopian", "ethiopia"),
            arrayOf("european", "europe"),
            arrayOf("fijian", "fiji"),
            arrayOf("filipino", "philippines"),
            arrayOf("finnish", "finland"),
            arrayOf("french", "france"),
            arrayOf("gambian", "gambia"),
            arrayOf("georgian", "georgia"),
            arrayOf("german", "germany"),
            arrayOf("ghanian", "ghana"),
            arrayOf("greek", "greece"),
            arrayOf("grenadan", "grenada"),
            arrayOf("guamian", "guam"),
            arrayOf("guatemalan", "guatemala"),
            arrayOf("guinean", "guinea"),
            arrayOf("guyanan", "guyana"),
            arrayOf("haitian", "haiti"),
            arrayOf("hawaiian", "hawaii"),
            arrayOf("holland", "dutch"),
            arrayOf("honduran", "honduras"),
            arrayOf("hungarian", "hungary"),
            arrayOf("icelandic", "iceland"),
            arrayOf("indonesian", "indonesia"),
            arrayOf("iranian", "iran"),
            arrayOf("iraqi", "iraq"),
            arrayOf("iraqui", "iraq"),
            arrayOf("irish", "ireland"),
            arrayOf("israeli", "israel"),
            arrayOf("italian", "italy"),
            arrayOf("jamaican", "jamaica"),
            arrayOf("japanese", "japan"),
            arrayOf("jordanian", "jordan"),
            arrayOf("kampuchean", "cambodia"),
            arrayOf("kenyan", "kenya"),
            arrayOf("korean", "korea"),
            arrayOf("kuwaiti", "kuwait"),
            arrayOf("lankan", "lanka"),
            arrayOf("laotian", "laos"),
            arrayOf("latvian", "latvia"),
            arrayOf("lebanese", "lebanon"),
            arrayOf("liberian", "liberia"),
            arrayOf("libyan", "libya"),
            arrayOf("lithuanian", "lithuania"),
            arrayOf("macedonian", "macedonia"),
            arrayOf("madagascan", "madagascar"),
            arrayOf("malaysian", "malaysia"),
            arrayOf("maltese", "malta"),
            arrayOf("mauritanian", "mauritania"),
            arrayOf("mexican", "mexico"),
            arrayOf("micronesian", "micronesia"),
            arrayOf("moldovan", "moldova"),
            arrayOf("monacan", "monaco"),
            arrayOf("mongolian", "mongolia"),
            arrayOf("montenegran", "montenegro"),
            arrayOf("moroccan", "morocco"),
            arrayOf("myanmar", "burma"),
            arrayOf("namibian", "namibia"),
            arrayOf("nepalese", "nepal"),
            // {"netherlands", "dutch"),
            arrayOf("nicaraguan", "nicaragua"),
            arrayOf("nigerian", "nigeria"),
            arrayOf("norwegian", "norway"),
            arrayOf("omani", "oman"),
            arrayOf("pakistani", "pakistan"),
            arrayOf("panamanian", "panama"),
            arrayOf("papuan", "papua"),
            arrayOf("paraguayan", "paraguay"),
            arrayOf("peruvian", "peru"),
            arrayOf("portuguese", "portugal"),
            arrayOf("romanian", "romania"),
            arrayOf("rumania", "romania"),
            arrayOf("rumanian", "romania"),
            arrayOf("russian", "russia"),
            arrayOf("rwandan", "rwanda"),
            arrayOf("samoan", "samoa"),
            arrayOf("scottish", "scotland"),
            arrayOf("serb", "serbia"),
            arrayOf("serbian", "serbia"),
            arrayOf("siam", "thailand"),
            arrayOf("siamese", "thailand"),
            arrayOf("slovakia", "slovak"),
            arrayOf("slovakian", "slovak"),
            arrayOf("slovenian", "slovenia"),
            arrayOf("somali", "somalia"),
            arrayOf("somalian", "somalia"),
            arrayOf("spanish", "spain"),
            arrayOf("swedish", "sweden"),
            arrayOf("swiss", "switzerland"),
            arrayOf("syrian", "syria"),
            arrayOf("taiwanese", "taiwan"),
            arrayOf("tanzanian", "tanzania"),
            arrayOf("texan", "texas"),
            arrayOf("thai", "thailand"),
            arrayOf("tunisian", "tunisia"),
            arrayOf("turkish", "turkey"),
            arrayOf("ugandan", "uganda"),
            arrayOf("ukrainian", "ukraine"),
            arrayOf("uruguayan", "uruguay"),
            arrayOf("uzbek", "uzbekistan"),
            arrayOf("venezuelan", "venezuela"),
            arrayOf("vietnamese", "viet"),
            arrayOf("virginian", "virginia"),
            arrayOf("yemeni", "yemen"),
            arrayOf("yugoslav", "yugoslavia"),
            arrayOf("yugoslavian", "yugoslavia"),
            arrayOf("zambian", "zambia"),
            arrayOf("zealander", "zealand"),
            arrayOf("zimbabwean", "zimbabwe")
        )

        private class DictEntry(val root: String?, val exception: Boolean)

        private val dict_ht: CharArrayMap<DictEntry> = initializeDictHash()

        private val ization = "ization".toCharArray()
        private val ition = "ition".toCharArray()
        private val ation = "ation".toCharArray()
        private val ication = "ication".toCharArray()

        private fun initializeDictHash(): CharArrayMap<DictEntry> {
            var entry: DictEntry

            val d = CharArrayMap<DictEntry>(1000, false)
            for (exceptionWord in exceptionWords) {
                if (!d.containsKey(exceptionWord)) {
                    entry = DictEntry(exceptionWord, true)
                    d.put(exceptionWord, entry)
                } else {
                    throw RuntimeException("Warning: Entry [$exceptionWord] already in dictionary 1")
                }
            }

            for (directConflation in directConflations) {
                if (!d.containsKey(directConflation[0])) {
                    entry = DictEntry(directConflation[1], false)
                    d.put(directConflation[0], entry)
                } else {
                    throw RuntimeException("Warning: Entry [${directConflation[0]}] already in dictionary 2")
                }
            }

            for (strings in countryNationality) {
                if (!d.containsKey(strings[0])) {
                    entry = DictEntry(strings[1], false)
                    d.put(strings[0], entry)
                } else {
                    throw RuntimeException("Warning: Entry [${strings[0]}] already in dictionary 3")
                }
            }

            val defaultEntry = DictEntry(null, false)

            var array = KStemData1.data
            for (s in array) {
                if (!d.containsKey(s)) {
                    d.put(s, defaultEntry)
                } else {
                    throw RuntimeException("Warning: Entry [$s] already in dictionary 4")
                }
            }

            array = KStemData2.data
            for (s in array) {
                if (!d.containsKey(s)) {
                    d.put(s, defaultEntry)
                } else {
                    throw RuntimeException("Warning: Entry [$s] already in dictionary 4")
                }
            }

            array = KStemData3.data
            for (s in array) {
                if (!d.containsKey(s)) {
                    d.put(s, defaultEntry)
                } else {
                    throw RuntimeException("Warning: Entry [$s] already in dictionary 4")
                }
            }

            array = KStemData4.data
            for (s in array) {
                if (!d.containsKey(s)) {
                    d.put(s, defaultEntry)
                } else {
                    throw RuntimeException("Warning: Entry [$s] already in dictionary 4")
                }
            }

            array = KStemData5.data
            for (s in array) {
                if (!d.containsKey(s)) {
                    d.put(s, defaultEntry)
                } else {
                    throw RuntimeException("Warning: Entry [$s] already in dictionary 4")
                }
            }

            array = KStemData6.data
            for (s in array) {
                if (!d.containsKey(s)) {
                    d.put(s, defaultEntry)
                } else {
                    throw RuntimeException("Warning: Entry [$s] already in dictionary 4")
                }
            }

            array = KStemData7.data
            for (s in array) {
                if (!d.containsKey(s)) {
                    d.put(s, defaultEntry)
                } else {
                    throw RuntimeException("Warning: Entry [$s] already in dictionary 4")
                }
            }

            array = KStemData8.data
            for (s in array) {
                if (!d.containsKey(s)) {
                    d.put(s, defaultEntry)
                } else {
                    throw RuntimeException("Warning: Entry [$s] already in dictionary 4")
                }
            }

            for (s in supplementDict) {
                if (!d.containsKey(s)) {
                    d.put(s, defaultEntry)
                } else {
                    throw RuntimeException("Warning: Entry [$s] already in dictionary 5")
                }
            }

            for (properNoun in properNouns) {
                if (!d.containsKey(properNoun)) {
                    d.put(properNoun, defaultEntry)
                } else {
                    throw RuntimeException("Warning: Entry [$properNoun] already in dictionary 6")
                }
            }

            return d
        }
    }
}
