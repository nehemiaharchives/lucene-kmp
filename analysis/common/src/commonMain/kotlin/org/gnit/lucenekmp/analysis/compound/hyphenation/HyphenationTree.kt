/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.analysis.compound.hyphenation

import okio.IOException
import org.gnit.lucenekmp.jdkport.InputSource

/**
 * This tree structure stores the hyphenation patterns in an efficient way for fast lookup. It
 * provides the provides the method to hyphenate a word.
 *
 * <p>This class has been taken from the Apache FOP project (http://xmlgraphics.apache.org/fop/).
 * They have been slightly modified.
 */
class HyphenationTree : TernaryTree(), PatternConsumer {

    /** value space: stores the interletter values */
    protected var vspace: ByteVector

    /** This map stores hyphenation exceptions */
    protected var stoplist: HashMap<String, ArrayList<Any>>

    /** This map stores the character classes */
    protected var classmap: TernaryTree

    /** Temporary map to store interletter values on pattern loading. */
    private var ivalues: TernaryTree? = null

    private val patterns: MutableMap<String, String> = LinkedHashMap()
    private val classes: MutableMap<Char, Char> = HashMap()

    init {
        stoplist = HashMap(23) // usually a small table
        classmap = TernaryTree()
        vspace = ByteVector()
        vspace.alloc(1) // this reserves index 0, which we don't use
    }

    /**
     * Packs the values by storing them in 4 bits, two values into a byte Values range is from 0 to 9.
     * We use zero as terminator, so we'll add 1 to the value.
     *
     * @param values a string of digits from '0' to '9' representing the interletter values.
     * @return the index into the vspace array where the packed values are stored.
     */
    protected fun packValues(values: String): Int {
        val n = values.length
        val m = if ((n and 1) == 1) (n shr 1) + 2 else (n shr 1) + 1
        val offset = vspace.alloc(m)
        val va = vspace.getArray()
        for (i in 0..<n) {
            val j = i shr 1
            val v = ((values[i] - '0' + 1) and 0x0f).toByte()
            if ((i and 1) == 1) {
                va[j + offset] = (va[j + offset].toInt() or v.toInt()).toByte()
            } else {
                va[j + offset] = (v.toInt() shl 4).toByte() // big endian
            }
        }
        va[m - 1 + offset] = 0 // terminator
        return offset
    }

    protected fun unpackValues(k0: Int): String {
        var k = k0
        val buf = StringBuilder()
        var v = vspace.get(k++)
        while (v.toInt() != 0) {
            var c = (((v.toInt() and 0xf0) ushr 4) - 1 + '0'.code).toChar()
            buf.append(c)
            c = (v.toInt() and 0x0f).toChar()
            if (c.code == 0) {
                break
            }
            c = (c.code - 1 + '0'.code).toChar()
            buf.append(c)
            v = vspace.get(k++)
        }
        return buf.toString()
    }

    /**
     * Read hyphenation patterns from an XML file.
     *
     * @param source the InputSource for the file
     * @throws IOException In case the parsing fails
     */
    @Throws(IOException::class)
    fun loadPatterns(source: InputSource) {
        val pp = PatternParser(this)
        ivalues = TernaryTree()

        pp.parse(source)

        // patterns/values should be now in the tree
        // let's optimize a bit
        trimToSize()
        vspace.trimToSize()
        classmap.trimToSize()

        // get rid of the auxiliary map
        ivalues = null
    }

    fun findPattern(pat: String): String {
        val value = patterns[pat]
        if (value != null) {
            return value
        }
        return ""
    }

    /** String compare, returns 0 if equal or t is a substring of s */
    protected fun hstrcmp(s: CharArray, si0: Int, t: CharArray, ti0: Int): Int {
        var si = si0
        var ti = ti0
        while (s[si] == t[ti]) {
            if (s[si].code == 0) {
                return 0
            }
            si++
            ti++
        }
        if (t[ti].code == 0) {
            return 0
        }
        return s[si] - t[ti]
    }

    protected fun getValues(k: Int): ByteArray {
        val values = unpackValues(k)
        val res = ByteArray(values.length)
        for (i in res.indices) {
            res[i] = (values[i] - '0').toByte()
        }
        return res
    }

    /**
     * Search for all possible partial matches of word starting at index an update interletter values.
     */
    protected fun searchPatterns(word: CharArray, index: Int, il: ByteArray) {
        var end = index
        while (end < word.size && word[end] != 0.toChar()) {
            end++
        }
        val tail = word.concatToString(index, end)
        for ((pattern, valuesText) in patterns) {
            if (tail.startsWith(pattern)) {
                var j = index
                for (k in 0..<valuesText.length) {
                    val value = (valuesText[k] - '0').toByte()
                    if (j < il.size && value > il[j]) {
                        il[j] = value
                    }
                    j++
                }
            }
        }
    }

    /**
     * Hyphenate word and return a Hyphenation object.
     *
     * @param word the word to be hyphenated
     * @param remainCharCount Minimum number of characters allowed before the hyphenation point.
     * @param pushCharCount Minimum number of characters allowed after the hyphenation point.
     * @return a [Hyphenation] object representing the hyphenated word or null if
     * word is not hyphenated.
     */
    fun hyphenate(word: String, remainCharCount: Int, pushCharCount: Int): Hyphenation? {
        val w = word.toCharArray()
        return hyphenate(w, 0, w.size, remainCharCount, pushCharCount)
    }

    /**
     * Hyphenate word and return an array of hyphenation points.
     *
     * @param w char array that contains the word
     * @param offset Offset to first character in word
     * @param len Length of word
     * @param remainCharCount Minimum number of characters allowed before the hyphenation point.
     * @param pushCharCount Minimum number of characters allowed after the hyphenation point.
     * @return a [Hyphenation] object representing the hyphenated word or null if
     * word is not hyphenated.
     */
    fun hyphenate(
        w: CharArray, offset: Int, len0: Int, remainCharCount: Int, pushCharCount: Int
    ): Hyphenation? {
        var len = len0
        var i: Int
        val word = CharArray(len + 3)

        // normalize word
        var iIgnoreAtBeginning = 0
        var iLength = len
        var bEndOfLetters = false
        i = 1
        while (i <= len) {
            val ch = w[offset + i - 1]
            val nc = classes[ch]
            if (nc == null) { // found a non-letter character ...
                if (i == (1 + iIgnoreAtBeginning)) {
                    // ... before any letter character
                    iIgnoreAtBeginning++
                } else {
                    // ... after a letter character
                    bEndOfLetters = true
                }
                iLength--
            } else {
                if (!bEndOfLetters) {
                    word[i - iIgnoreAtBeginning] = nc
                } else {
                    return null
                }
            }
            i++
        }
        len = iLength
        if (len < (remainCharCount + pushCharCount)) {
            // word is too short to be hyphenated
            return null
        }
        val result = IntArray(len + 1)
        var k = 0

        // check exception list first
        val sw = word.concatToString(1, 1 + len)
        val hw = stoplist[sw]
        if (hw != null) {
            // assume only simple hyphens (Hyphen.pre="-", Hyphen.post = Hyphen.no =
            // null)
            var j = 0
            i = 0
            while (i < hw.size) {
                val o = hw[i]
                // j = index(sw) = letterindex(word)?
                // result[k] = corresponding index(w)
                if (o is String) {
                    j += o.length
                    if (j >= remainCharCount && j < (len - pushCharCount)) {
                        result[k++] = j + iIgnoreAtBeginning
                    }
                }
                i++
            }
        } else {
            // use algorithm to get hyphenation points
            word[0] = '.' // word start marker
            word[len + 1] = '.' // word end marker
            word[len + 2] = 0.toChar() // null terminated
            val il = ByteArray(len + 3) // initialized to zero
            i = 0
            while (i < len + 1) {
                searchPatterns(word, i, il)
                i++
            }

            // hyphenation points are located where interletter value is odd
            // i is letterindex(word),
            // i + 1 is index(word),
            // result[k] = corresponding index(w)
            i = 0
            while (i < len) {
                if (((il[i + 1].toInt() and 1) == 1) && i >= remainCharCount && i <= (len - pushCharCount)) {
                    result[k++] = i + iIgnoreAtBeginning
                }
                i++
            }
        }

        return if (k > 0) {
            val unique = ArrayList<Int>(k)
            for (idx in 0..<k) {
                val point = result[idx]
                if (point > 0 && point < len && (unique.isEmpty() || unique[unique.size - 1] != point)) {
                    unique.add(point)
                }
            }
            k = unique.size
            // trim result array
            val res = IntArray(k + 2)
            for (idx in 0..<k) {
                res[idx + 1] = unique[idx]
            }
            // We add the synthetical hyphenation points
            // at the beginning and end of the word
            res[0] = 0
            res[k + 1] = len
            Hyphenation(res)
        } else {
            null
        }
    }

    /**
     * Add a character class to the tree. It is used by {@link PatternParser} as
     * callback to add character classes. Character classes define the valid word characters for
     * hyphenation. If a word contains a character not defined in any of the classes, it is not
     * hyphenated. It also defines a way to normalize the characters in order to compare them with the
     * stored patterns. Usually pattern files use only lower case characters, in this case a class for
     * letter 'a', for example, should be defined as "aA", the first character being the normalization
     * char.
     */
    override fun addClass(chargroup: String) {
        if (chargroup.isNotEmpty()) {
            val equivChar = chargroup[0]
            val key = CharArray(2)
            key[1] = 0.toChar()
            for (i in 0..<chargroup.length) {
                key[0] = chargroup[i]
                classmap.insert(key, 0, equivChar)
                classes[chargroup[i]] = equivChar
            }
        }
    }

    /**
     * Add an exception to the tree. It is used by {@link PatternParser PatternParser} class as
     * callback to store the hyphenation exceptions.
     *
     * @param word normalized word
     * @param hyphenatedword a vector of alternating strings and {@link Hyphen hyphen} objects.
     */
    override fun addException(word: String, hyphenatedword: ArrayList<Any>) {
        stoplist[word] = hyphenatedword
    }

    /**
     * Add a pattern to the tree. Mainly, to be used by {@link PatternParser PatternParser} class as
     * callback to add a pattern to the tree.
     *
     * @param pattern the hyphenation pattern
     * @param ivalue interletter weight values indicating the desirability and priority of hyphenating
     *     at a given point within the pattern. It should contain only digit characters. (i.e. '0' to
     *     '9').
     */
    override fun addPattern(pattern: String, ivalue: String) {
        var k = ivalues!!.find(ivalue)
        if (k <= 0) {
            k = packValues(ivalue)
            ivalues!!.insert(ivalue, k.toChar())
        }
        insert(pattern, k.toChar())
        patterns[pattern] = ivalue
    }
}
