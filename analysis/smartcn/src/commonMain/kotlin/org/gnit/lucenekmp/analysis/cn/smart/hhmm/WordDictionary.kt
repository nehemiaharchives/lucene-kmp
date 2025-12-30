package org.gnit.lucenekmp.analysis.cn.smart.hhmm

import org.gnit.lucenekmp.analysis.cn.smart.Utility
import org.gnit.lucenekmp.jdkport.ByteBuffer

/**
 * SmartChineseAnalyzer Word Dictionary
 *
 * @lucene.experimental
 */
class WordDictionary private constructor() : AbstractDictionary() {
    /** Large prime number for hash function */
    companion object {
        const val PRIME_INDEX_LENGTH: Int = 12071
        private val INSTANCE: WordDictionary by lazy {
            WordDictionary().apply { load() }
        }

        fun getInstance(): WordDictionary = INSTANCE
    }

    private var wordIndexTable: ShortArray = ShortArray(0)
    private var charIndexTable: CharArray = CharArray(0)
    private var wordItemCharArrayTable: Array<Array<CharArray?>?> = emptyArray()
    private var wordItemFrequencyTable: Array<IntArray?> = emptyArray()

    /**
     * Load coredict data from generated Kotlin source.
     */
    fun load() {
        loadFromBytes(coreDictData)
    }

    /**
     * Attempt to load dictionary from provided directory.
     * Not supported in common code.
     */
    fun load(dctFileRoot: String) {
        throw UnsupportedOperationException("Loading from file system is not supported in common code: $dctFileRoot")
    }

    private fun loadFromBytes(bytes: ByteArray) {
        val bb = ByteBuffer.wrap(bytes)
        val wordIndexSize = bb.getInt()
        wordIndexTable = ShortArray(wordIndexSize)
        for (i in 0 until wordIndexSize) {
            wordIndexTable[i] = bb.getShort()
        }
        val charIndexSize = bb.getInt()
        charIndexTable = CharArray(charIndexSize)
        for (i in 0 until charIndexSize) {
            charIndexTable[i] = bb.getShort().toInt().toChar()
        }
        val wordItemTableSize = bb.getInt()
        wordItemCharArrayTable = arrayOfNulls(wordItemTableSize)
        for (i in 0 until wordItemTableSize) {
            val entrySize = bb.getInt()
            if (entrySize < 0) {
                wordItemCharArrayTable[i] = null
            } else {
                val entry = arrayOfNulls<CharArray>(entrySize)
                for (j in 0 until entrySize) {
                    val wordSize = bb.getInt()
                    if (wordSize < 0) {
                        entry[j] = null
                    } else {
                        val chars = CharArray(wordSize)
                        for (k in 0 until wordSize) {
                            chars[k] = bb.getShort().toInt().toChar()
                        }
                        entry[j] = chars
                    }
                }
                wordItemCharArrayTable[i] = entry
            }
        }
        val wordItemFreqSize = bb.getInt()
        wordItemFrequencyTable = arrayOfNulls(wordItemFreqSize)
        for (i in 0 until wordItemFreqSize) {
            val entrySize = bb.getInt()
            if (entrySize < 0) {
                wordItemFrequencyTable[i] = null
            } else {
                val entry = IntArray(entrySize)
                for (j in 0 until entrySize) {
                    entry[j] = bb.getInt()
                }
                wordItemFrequencyTable[i] = entry
            }
        }
    }

    private fun getWordItemTableIndex(c: Char): Short {
        var hash1 = (hash1(c) % PRIME_INDEX_LENGTH).toInt()
        var hash2 = hash2(c) % PRIME_INDEX_LENGTH
        if (hash1 < 0) hash1 = PRIME_INDEX_LENGTH + hash1
        if (hash2 < 0) hash2 = PRIME_INDEX_LENGTH + hash2
        var index = hash1
        var i = 1
        while (charIndexTable[index] != 0.toChar() && charIndexTable[index] != c && i < PRIME_INDEX_LENGTH) {
            index = (hash1 + i * hash2) % PRIME_INDEX_LENGTH
            i++
        }

        return if (i < PRIME_INDEX_LENGTH && charIndexTable[index] == c) {
            index.toShort()
        } else {
            (-1).toShort()
        }
    }

    private fun findInTable(knownHashIndex: Short, charArray: CharArray?): Int {
        if (charArray == null || charArray.isEmpty()) return -1

        val listIndex = wordIndexTable[knownHashIndex.toInt()].toInt()
        val items = wordItemCharArrayTable[listIndex] ?: return -1
        var start = 0
        var end = items.size - 1
        var mid = (start + end) / 2

        while (start <= end) {
            val cmpResult = Utility.compareArray(items[mid], 0, charArray, 1)
            if (cmpResult == 0) return mid
            if (cmpResult < 0) start = mid + 1 else end = mid - 1
            mid = (start + end) / 2
        }
        return -1
    }

    fun getPrefixMatch(charArray: CharArray): Int {
        return getPrefixMatch(charArray, 0)
    }

    fun getPrefixMatch(charArray: CharArray, knownStart: Int): Int {
        val index = getWordItemTableIndex(charArray[0])
        if (index.toInt() == -1) return -1
        val items = wordItemCharArrayTable[wordIndexTable[index.toInt()].toInt()] ?: return -1
        var start = knownStart
        var end = items.size - 1
        var mid = (start + end) / 2

        while (start <= end) {
            val cmpResult = Utility.compareArrayByPrefix(charArray, 1, items[mid], 0)
            if (cmpResult == 0) {
                while (mid >= 0 && Utility.compareArrayByPrefix(charArray, 1, items[mid], 0) == 0) {
                    mid--
                }
                mid++
                return mid
            } else if (cmpResult < 0) {
                end = mid - 1
            } else {
                start = mid + 1
            }
            mid = (start + end) / 2
        }
        return -1
    }

    fun getFrequency(charArray: CharArray): Int {
        val hashIndex = getWordItemTableIndex(charArray[0])
        if (hashIndex.toInt() == -1) return 0
        val itemIndex = findInTable(hashIndex, charArray)
        if (itemIndex != -1) {
            val listIndex = wordIndexTable[hashIndex.toInt()].toInt()
            val freqs = wordItemFrequencyTable[listIndex]
            if (freqs != null) {
                return freqs[itemIndex]
            }
        }
        return 0
    }

    fun isEqual(charArray: CharArray, itemIndex: Int): Boolean {
        val hashIndex = getWordItemTableIndex(charArray[0])
        val listIndex = wordIndexTable[hashIndex.toInt()].toInt()
        val items = wordItemCharArrayTable[listIndex] ?: return false
        return Utility.compareArray(charArray, 1, items[itemIndex], 0) == 0
    }
}
