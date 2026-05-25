package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.he.datastructures.DictRadix

/**
 * Created by Egozy on 28/12/2014.
 */
enum class DescFlag {
    D_EMPTY,
    D_NOUN,
    D_VERB,
    D_ADJ,
    D_PROPER,
    D_ACRONYM;

    fun getVal(): Int {
        return when (this) {
            D_EMPTY -> 0
            D_NOUN -> 1
            D_VERB -> 2
            D_ADJ -> 3
            D_PROPER -> 4
            D_ACRONYM -> 5
        }
    }

    companion object {
        fun create(`val`: Byte): DescFlag {
            return when (`val`.toInt()) {
                0 -> D_EMPTY
                1 -> D_NOUN
                2 -> D_VERB
                3 -> D_ADJ
                4 -> D_PROPER
                5 -> D_ACRONYM
                else -> throw IllegalArgumentException()
            }
        }
    }
}

enum class PrefixType(private val value: Byte) {
    PS_EMPTY(0),
    PS_B(1),
    PS_L(2),
    PS_VERB(4),
    PS_NONDEF(8),
    PS_IMPER(16),
    PS_MISC(32),
    PS_KL(64),
    PS_ALL(127);

    fun getValue(): Byte {
        return this.value
    }

    companion object {
        fun create(`val`: Byte): PrefixType {
            return when (`val`.toInt()) {
                0 -> PS_EMPTY
                1 -> PS_B
                2 -> PS_L
                4 -> PS_VERB
                8 -> PS_NONDEF
                16 -> PS_IMPER
                32 -> PS_MISC
                64 -> PS_KL
                127 -> PS_ALL
                else -> throw IllegalArgumentException()
            }
        }
    }
}

class MorphData {
    private var lemmas: MutableList<Lemma> = mutableListOf()
    private var prefixes: Short = 0
    private var haltIfFound: Boolean = false

    class Lemma(private val lemma: String?, private val descFlag: DescFlag, private val prefix: PrefixType) {
        fun getDescFlag(): DescFlag = descFlag
        fun getLemma(): String? = lemma
        fun getPrefix(): PrefixType = prefix

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false
            other as Lemma
            if (descFlag != other.descFlag) return false
            if (prefix != other.prefix) return false
            return lemma == other.lemma
        }

        override fun hashCode(): Int {
            val prime = 37
            var result = 1
            result = prime * result + descFlag.hashCode()
            result = prime * result + prefix.getValue()
            result = prime * result + (lemma?.hashCode() ?: 0)
            return result
        }

        override fun toString(): String {
            return "$lemma:$descFlag:$prefix"
        }
    }

    fun setLemmas(lemmas: Array<Lemma>) {
        lemmas.sortWith { l1, l2 -> l1.getDescFlag().getVal() - l2.getDescFlag().getVal() }
        this.lemmas = lemmas.toMutableList()
    }

    fun getLemmas(): Array<Lemma> {
        return lemmas.toTypedArray()
    }

    fun addLemma(lemma: Lemma) {
        lemmas.add(lemma)
        lemmas.sortWith { l1, l2 -> l1.getDescFlag().getVal() - l2.getDescFlag().getVal() }
    }

    fun clearLemmas() {
        lemmas.clear()
    }

    fun setPrefixes(prefixes: Short) {
        this.prefixes = prefixes
    }

    fun getPrefixes(): Int {
        return prefixes.toInt()
    }

    fun haltIfFound(): Boolean {
        return haltIfFound
    }

    fun setHaltIfFound(haltIfFound: Boolean) {
        this.haltIfFound = haltIfFound
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as MorphData
        return lemmas == other.lemmas
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + lemmas.hashCode()
        return result
    }

    override fun toString(): String {
        return "{ prefix=$prefixes lemmas=$lemmas}"
    }
}

open class Token(private var text: String, private var isNumeric: Boolean = false) {
    fun getText(): String = text
    fun setText(text: String) {
        this.text = text
    }

    fun isNumeric(): Boolean = isNumeric
    fun setNumeric(isNumeric: Boolean) {
        this.isNumeric = isNumeric
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + if (isNumeric) 1231 else 1237
        result = prime * result + text.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Token) return false
        if (isNumeric != other.isNumeric) return false
        return text == other.text
    }

    override fun toString(): String {
        return "$text isNumeric:($isNumeric)"
    }
}

class HebrewToken : Token, Comparable<Token> {
    private var score = 1.0f
    private var prefixLength: Byte
    private var mask: DescFlag
    private var lemma: String?
    private var prefType: PrefixType

    constructor(_word: String, _prefixLength: Byte, lemma: MorphData.Lemma, _score: Float) :
        this(_word, _prefixLength, lemma.getDescFlag(), lemma.getLemma(), lemma.getPrefix(), _score)

    constructor(_word: String, _prefixLength: Byte, _mask: DescFlag, _lemma: String?, _pref: PrefixType, _score: Float) : super(_word) {
        prefixLength = _prefixLength
        prefType = _pref
        mask = _mask
        lemma = _lemma
        setScore(_score)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (!super.equals(other)) return false
        if (other !is HebrewToken) return false
        if (lemma != other.lemma) return false
        if (mask != other.mask) return false
        if (prefixLength != other.prefixLength) return false
        if (prefType != other.prefType) return false
        return score.toBits() == other.score.toBits()
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = super.hashCode()
        result = prime * result + (lemma?.hashCode() ?: 0)
        result = prime * result + mask.hashCode()
        result = prime * result + prefixLength
        result = prime * result + score.toBits()
        return result
    }

    override fun toString(): String {
        return "$lemma"
    }

    override fun compareTo(other: Token): Int {
        val otherToken = other as? HebrewToken ?: return -1
        return getScore().compareTo(otherToken.getScore())
    }

    fun setScore(score: Float) {
        this.score = score
    }

    fun getScore(): Float = score

    fun setMask(mask: DescFlag) {
        this.mask = mask
    }

    fun getMask(): DescFlag = mask

    fun getPrefixLength(): Byte = prefixLength

    fun getLemma(): String? = lemma
}

enum class WordType {
    HEBREW,
    HEBREW_WITH_PREFIX,
    HEBREW_TOLERATED,
    HEBREW_TOLERATED_WITH_PREFIX,
    NON_HEBREW,
    UNRECOGNIZED,
    CUSTOM,
    CUSTOM_WITH_PREFIX,
}

class Reference<T>(var ref: T) {
    companion object {
        fun <T> reference(initialVal: T): Reference<T> {
            return Reference(initialVal)
        }
    }
}

object HebrewCharacters {
    var ALEPH = '\u05D0'
    var BET = '\u05D1'
    var GIMMEL = '\u05D2'
    var DALET = '\u05D3'
    var HE = '\u05D4'
    var VAV = '\u05D5'
    var ZAYIN = '\u05D6'
    var HET = '\u05D7'
    var TET = '\u05D8'
    var YOD = '\u05D9'
    var KAF_FINAL = '\u05DA'
    var KAF = '\u05DB'
    var LAMED = '\u05DC'
    var MEM_FINAL = '\u05DD'
    var MEM = '\u05DE'
    var NUN_FINAL = '\u05DF'
    var NUN = '\u05E0'
    var SAMEKH = '\u05E1'
    var AYIN = '\u05E2'
    var PE_FINAL = '\u05E3'
    var PE = '\u05E4'
    var TSADI = '\u05E5'
    var TSADI_FINAL = '\u05E6'
    var QUF = '\u05E7'
    var RESH = '\u05E8'
    var SHIN = '\u05E9'
    var TAV = '\u05EA'
    var GERESH = '\u05F3'
    var GERSHAYIM = '\u05F4'

    private val alternateCharacters = hashMapOf(
        '\uFB20' to AYIN,
        '\uFB21' to ALEPH,
        '\uFB22' to DALET,
        '\uFB23' to HE,
        '\uFB24' to KAF,
        '\uFB25' to LAMED,
        '\uFB26' to MEM_FINAL,
        '\uFB27' to RESH,
        '\uFB28' to TAV,
    )

    fun collapseAlternate(ch: Char): Char {
        return alternateCharacters[ch] ?: ch
    }
}

object HebrewUtils {
    val Geresh = charArrayOf('\'', '\u05F3', '\u2018', '\u2019', '\u201B', '\uFF07')
    val Gershayim = charArrayOf('"', '\u05F4', '\u201C', '\u201D', '\u201F', '\u275E', '\uFF02')
    val Makaf = charArrayOf('-', '\u2012', '\u2013', '\u2014', '\u2015', '\u05BE')
    val CharsFollowingPrefixes = concatenateCharArrays(Geresh, Gershayim, Makaf)
    val LettersAcceptingGeresh = charArrayOf('ז', 'ג', 'ץ', 'צ', 'ח')

    fun isOfChars(c: Char, options: CharArray): Boolean {
        for (o in options) {
            if (c == o) return true
        }
        return false
    }

    fun concatenateCharArrays(vararg arrays: CharArray): CharArray {
        var count = 0
        for (a in arrays) count += a.size
        val ret = CharArray(count)
        var offs = 0
        for (a in arrays) {
            a.copyInto(ret, offs, 0, a.size)
            offs += a.size
        }
        return ret
    }

    fun isHebrewLetter(c: Char): Boolean {
        return c.code in 1488..1514
    }

    fun isFinalHebrewLetter(c: Char): Boolean {
        return c == 1507.toChar() || c == 1498.toChar() || c == 1501.toChar() || c == 1509.toChar() || c == 1503.toChar()
    }

    fun isNiqqudChar(c: Char): Boolean {
        return (c.code in 1456..1465) || (c == '\u05C1' || c == '\u05C2' || c == '\u05BB' || c == '\u05BC')
    }
}

object LookupTolerators {
    val TolerateEmKryiaAll: Array<ToleranceFunction> = arrayOf(
        TolerateEmKryiaYud(),
        TolerateEmKryiaVav(),
        TolerateNonDoubledConsonantVav()
    )

    fun interface ToleranceFunction {
        fun tolerate(key: CharArray, keyPos: Reference<Byte>, word: String, score: Reference<Float>, curChar: Char): Int?
    }

    class TolerateEmKryiaYud : ToleranceFunction {
        override fun tolerate(key: CharArray, keyPos: Reference<Byte>, word: String, score: Reference<Float>, curChar: Char): Int? {
            val pos = keyPos.ref.toInt()
            if (pos == 0) return null
            if (key[pos] == HebrewCharacters.VAV) return null
            if (curChar != HebrewCharacters.YOD) {
                if (key[pos] == HebrewCharacters.YOD && key[pos - 1] == HebrewCharacters.YOD) {
                    score.ref *= 0.9f
                    keyPos.ref = (pos + 1).toByte()
                    return 0
                }
                if (key[pos] == HebrewCharacters.YOD) {
                    score.ref *= 0.6f
                    keyPos.ref = (pos + 1).toByte()
                    return 0
                }
                return null
            }
            if (key[pos] == HebrewCharacters.YOD) return null
            if (word[word.length - 1] == HebrewCharacters.YOD) {
                if (key[pos - 1] != HebrewCharacters.YOD || ((pos + 1 == key.size) && (key.size <= 3))) return null
                score.ref *= 0.8f
                return 1
            } else if (word[word.length - 1] != HebrewCharacters.VAV) {
                score.ref *= 0.8f
                return 1
            }
            return null
        }
    }

    class TolerateEmKryiaVav : ToleranceFunction {
        override fun tolerate(key: CharArray, keyPos: Reference<Byte>, word: String, score: Reference<Float>, curChar: Char): Int? {
            val pos = keyPos.ref.toInt()
            if (curChar != HebrewCharacters.VAV || pos == 0 || pos + 1 == key.size ||
                key[pos] == HebrewCharacters.YOD || key[pos] == HebrewCharacters.HE || key[pos] == HebrewCharacters.VAV
            ) return null
            val prevChar = word[word.length - 1]
            if (key[pos + 1] != HebrewCharacters.VAV && prevChar != HebrewCharacters.VAV && prevChar != HebrewCharacters.YOD) {
                score.ref *= 0.8f
                return 1
            }
            return null
        }
    }

    class TolerateNonDoubledConsonantVav : ToleranceFunction {
        override fun tolerate(key: CharArray, keyPos: Reference<Byte>, word: String, score: Reference<Float>, curChar: Char): Int? {
            val pos = keyPos.ref.toInt()
            if (curChar == HebrewCharacters.VAV || pos == 0 || pos + 1 == key.size) return null
            if (key[pos] == HebrewCharacters.VAV && word[word.length - 1] == HebrewCharacters.VAV) {
                keyPos.ref = (pos + 1).toByte()
                score.ref *= 0.8f
                return 0
            }
            return null
        }
    }
}

class DictHebMorph {
    private var pref: MutableMap<String, Int> = hashMapOf()
    private var dict: DictRadix<MorphData> = DictRadix()
    private var mds: MutableMap<String, MorphData> = hashMapOf()

    fun addNode(s: String, md: MorphData) {
        mds[s] = md
        dict.addNode(s, md)
    }

    fun addNode(s: CharArray, md: MorphData) {
        addNode(s.concatToString(), md)
    }

    fun getRadix(): DictRadix<MorphData> = dict
    fun getPref(): MutableMap<String, Int> = pref
    fun setPref(prefs: MutableMap<String, Int>) {
        this.pref = prefs
    }
    fun lookup(key: String): MorphData? = mds[key]
    fun clear() {
        dict.clear()
        pref.clear()
        mds.clear()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as DictHebMorph
        return dict == other.dict && pref == other.pref && mds == other.mds
    }
}

internal class HSpellLemma(val lemma: String?, val descFlag: DescFlag, val prefix: PrefixType)
internal class HSpellEntry(val word: String, val prefixes: Int, val lemmas: Array<HSpellLemma>)

class HSpellDictionaryLoader {
    fun dictionaryLoaderName(): String = "hspell"

    fun loadDictionaryFromDefaultPath(): DictHebMorph {
        val dict = DictHebMorph()
        dict.setPref(HSpellGeneratedData.prefixes.toMutableMap())
        for (entry in readEntries(decodeBase64ToBytes(HSpellGeneratedData.entryDataBase64))) {
            val data = MorphData()
            data.setPrefixes(entry.prefixes.toShort())
            data.setLemmas(Array(entry.lemmas.size) { i ->
                val lemma = entry.lemmas[i]
                MorphData.Lemma(lemma.lemma, lemma.descFlag, lemma.prefix)
            })
            dict.addNode(entry.word, data)
        }
        return dict
    }

    private fun readEntries(data: ByteArray): Array<HSpellEntry> {
        val input = GeneratedDataInput(data)
        return Array(input.readInt()) {
            val word = input.readString()
            val prefixes = input.readInt()
            val lemmas = Array(input.readInt()) {
                val lemma = if (input.readBoolean()) input.readString() else null
                val descFlag = DescFlag.create(input.readInt().toByte())
                val prefix = PrefixType.create(input.readInt().toByte())
                HSpellLemma(lemma, descFlag, prefix)
            }
            HSpellEntry(word, prefixes, lemmas)
        }
    }

    private class GeneratedDataInput(private val data: ByteArray) {
        private var pos = 0

        fun readBoolean(): Boolean {
            return data[pos++].toInt() != 0
        }

        fun readInt(): Int {
            val value = ((data[pos].toInt() and 0xff) shl 24) or
                ((data[pos + 1].toInt() and 0xff) shl 16) or
                ((data[pos + 2].toInt() and 0xff) shl 8) or
                (data[pos + 3].toInt() and 0xff)
            pos += 4
            return value
        }

        fun readString(): String {
            val length = readInt()
            val bytes = data.copyOfRange(pos, pos + length)
            pos += length
            return bytes.decodeToString()
        }
    }

    companion object {
        private val BASE64_INV: IntArray = IntArray(256) { -1 }.also { table ->
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            for (i in alphabet.indices) table[alphabet[i].code] = i
            table['='.code] = -2
        }

        private fun decodeBase64ToBytes(data: String): ByteArray {
            var validCount = 0
            for (c in data) {
                if (c == '\n' || c == '\r' || c == ' ' || c == '\t') continue
                validCount++
            }
            val out = ByteArray((validCount * 3) / 4)
            var outPos = 0
            var acc = 0
            var accBits = 0
            for (c in data) {
                val v = if (c.code < 256) BASE64_INV[c.code] else -1
                if (v == -1) continue
                if (v == -2) break
                acc = (acc shl 6) or v
                accBits += 6
                if (accBits >= 8) {
                    accBits -= 8
                    out[outPos++] = (acc shr accBits).toByte()
                }
            }
            return if (outPos == out.size) out else out.copyOf(outPos)
        }
    }
}
