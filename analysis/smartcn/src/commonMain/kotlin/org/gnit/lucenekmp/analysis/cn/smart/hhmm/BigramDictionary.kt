package org.gnit.lucenekmp.analysis.cn.smart.hhmm

import org.gnit.lucenekmp.jdkport.ByteBuffer

/**
 * SmartChineseAnalyzer Bigram dictionary.
 *
 * @lucene.experimental
 */
class BigramDictionary private constructor() : AbstractDictionary() {
    companion object {
        const val WORD_SEGMENT_CHAR: Char = '@'
        const val PRIME_BIGRAM_LENGTH: Int = 402137

        private val INSTANCE: BigramDictionary by lazy {
            BigramDictionary().apply { load() }
        }

        fun getInstance(): BigramDictionary = INSTANCE
    }

    private var bigramHashTable: LongArray = LongArray(0)
    private var frequencyTable: IntArray = IntArray(0)
    private var max: Int = 0

    private fun load() {
        loadFromBytes(bigramDictData)
    }

    private fun loadFromBytes(bytes: ByteArray) {
        val bb = ByteBuffer.wrap(bytes)
        val hashSize = bb.getInt()
        bigramHashTable = LongArray(hashSize)
        for (i in 0 until hashSize) {
            bigramHashTable[i] = bb.getLong()
        }
        val freqSize = bb.getInt()
        frequencyTable = IntArray(freqSize)
        for (i in 0 until freqSize) {
            frequencyTable[i] = bb.getInt()
        }
    }

    private fun getAvailableIndex(hashId: Long, carray: CharArray): Int {
        var hash1 = (hashId % PRIME_BIGRAM_LENGTH).toInt()
        var hash2 = hash2(carray) % PRIME_BIGRAM_LENGTH
        if (hash1 < 0) hash1 += PRIME_BIGRAM_LENGTH
        if (hash2 < 0) hash2 += PRIME_BIGRAM_LENGTH
        var index = hash1
        var i = 1
        while (bigramHashTable[index] != 0L && bigramHashTable[index] != hashId && i < PRIME_BIGRAM_LENGTH) {
            index = (hash1 + i * hash2) % PRIME_BIGRAM_LENGTH
            i++
        }
        return if (i < PRIME_BIGRAM_LENGTH && (bigramHashTable[index] == 0L || bigramHashTable[index] == hashId)) {
            index
        } else {
            -1
        }
    }

    private fun getBigramItemIndex(carray: CharArray): Int {
        val hashId = hash1(carray)
        var hash1 = (hashId % PRIME_BIGRAM_LENGTH).toInt()
        var hash2 = hash2(carray) % PRIME_BIGRAM_LENGTH
        if (hash1 < 0) hash1 += PRIME_BIGRAM_LENGTH
        if (hash2 < 0) hash2 += PRIME_BIGRAM_LENGTH
        var index = hash1
        var i = 1
        while (bigramHashTable[index] != 0L && bigramHashTable[index] != hashId && i < PRIME_BIGRAM_LENGTH) {
            index = (hash1 + i * hash2) % PRIME_BIGRAM_LENGTH
            i++
            if (i > max) max = i
        }
        return if (i < PRIME_BIGRAM_LENGTH && bigramHashTable[index] == hashId) index else -1
    }

    fun getFrequency(carray: CharArray): Int {
        val index = getBigramItemIndex(carray)
        return if (index != -1) frequencyTable[index] else 0
    }
}
