package org.gnit.lucenekmp.analysis.ja.dict

import org.gnit.lucenekmp.analysis.morph.Dictionary
import org.gnit.lucenekmp.analysis.util.CSVUtil
import org.gnit.lucenekmp.jdkport.BufferedReader
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.jdkport.InputStreamReader
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.util.fst.FSTCompiler
import org.gnit.lucenekmp.util.fst.PositiveIntOutputs
import okio.IOException

/** Class for building a User Dictionary. This class allows for custom segmentation of phrases. */
class UserDictionary private constructor(featureEntries: List<Array<String>>) : Dictionary<UserMorphData> {
    companion object {
        const val INTERNAL_SEPARATOR: String = "\u0000"
        const val CUSTOM_DICTIONARY_WORD_ID_OFFSET: Int = 100000000

        private val LINE_COMMENT = Regex("^#.*$")
        private val WHITESPACE = Regex("\\s")
        private val SPACES = Regex(" +")

        @Throws(IOException::class)
        fun open(reader: InputStream): UserDictionary? = open(InputStreamReader(reader))

        @Throws(IOException::class)
        fun open(reader: Reader): UserDictionary? {
            val br = BufferedReader(reader)
            val entries: MutableList<Array<String>> = ArrayList()
            var line: String?
            while (true) {
                line = br.readLine() ?: break
                line = line.replace(LINE_COMMENT, "")
                if (line.trim().isEmpty()) {
                    continue
                }
                val values = CSVUtil.parse(line)
                entries.add(values)
            }
            return if (entries.isEmpty()) null else UserDictionary(entries)
        }
    }

    private val fst: TokenInfoFST
    private val segmentations: Array<IntArray>
    private val morphAtts: UserMorphData

    init {
        var wordId = CUSTOM_DICTIONARY_WORD_ID_OFFSET
        val sorted = featureEntries.sortedWith { left, right -> left[0].compareTo(right[0]) }
        val data = ArrayList<String>(sorted.size)
        val segmentationList = ArrayList<IntArray>(sorted.size)

        val fstOutput = PositiveIntOutputs.singleton
        val fstCompiler = FSTCompiler.Builder(FST.INPUT_TYPE.BYTE2, fstOutput).build()
        val scratch = IntsRefBuilder()
        var ord = 0L

        for (values in sorted) {
            val surface = WHITESPACE.replace(values[0], "")
            val concatenatedSegment = WHITESPACE.replace(values[1], "")
            val segmentation = SPACES.split(values[1])
            val readings = SPACES.split(values[2])
            val pos = values[3]

            if (segmentation.size != readings.size) {
                throw RuntimeException(
                    "Illegal user dictionary entry ${values[0]} - the number of segmentations (${segmentation.size}) " +
                        " does not the match number of readings (${readings.size})"
                )
            }
            if (surface != concatenatedSegment) {
                throw RuntimeException(
                    "Illegal user dictionary entry ${values[0]} - the concatenated segmentation (${concatenatedSegment}) " +
                        " does not match the surface form (${surface})"
                )
            }

            val wordIdAndLength = IntArray(segmentation.size + 1)
            wordIdAndLength[0] = wordId
            for (i in segmentation.indices) {
                wordIdAndLength[i + 1] = segmentation[i].length
                data.add(readings[i] + INTERNAL_SEPARATOR + pos)
                wordId++
            }

            val token = values[0]
            scratch.growNoCopy(token.length)
            scratch.setLength(token.length)
            for (i in token.indices) {
                scratch.setIntAt(i, token[i].code)
            }
            fstCompiler.add(scratch.get(), ord)
            segmentationList.add(wordIdAndLength)
            ord++
        }

        val fstMeta = fstCompiler.compile()
        val built = FST.fromFSTReader(fstMeta, fstCompiler.getFSTReader())
            ?: throw IllegalStateException("FST compilation produced null")
        fst = TokenInfoFST(built, false)
        morphAtts = UserMorphData(data.toTypedArray())
        segmentations = segmentationList.toTypedArray()
    }

    override fun getMorphAttributes(): UserMorphData = morphAtts

    /**
     * Lookup words in text
     *
     * @return array of {wordId, position, length}
     */
    @Throws(IOException::class)
    fun lookup(chars: CharArray, off: Int, len: Int): Array<IntArray> {
        var matches: MutableList<Match>? = null
        var numResults = 0
        val fstReader = fst.getBytesReader()
        val end = off + len
        var arc = FST.Arc<Long>()
        for (startOffset in off until end) {
            var wordIdAndLength: IntArray? = null
            arc = fst.getFirstArc(arc)
            var output = 0L
            val remaining = end - startOffset
            for (i in 0 until remaining) {
                val ch = chars[startOffset + i].code
                if (fst.findTargetArc(ch, arc, arc, i == 0, fstReader) == null) {
                    break
                }
                output += arc.output() ?: 0L
                if (arc.isFinal) {
                    val finalOutput = output + (arc.nextFinalOutput() ?: 0L)
                    wordIdAndLength = segmentations[finalOutput.toInt()]
                }
            }
            if (wordIdAndLength != null) {
                if (matches == null) {
                    matches = ArrayList()
                }
                matches.add(Match(startOffset - off, wordIdAndLength))
                numResults += wordIdAndLength.size - 1
            }
        }
        if (numResults == 0) {
            return emptyArray()
        }
        val result = Array(numResults) { IntArray(0) }
        var index = 0
        val list = matches ?: emptyList()
        for (match in list) {
            val wordIdAndLength = match.wordIdAndLength
            val wordId = wordIdAndLength[0]
            var position = match.position
            for (j in 1 until wordIdAndLength.size) {
                result[index++] = intArrayOf(wordId + j - 1, position, wordIdAndLength[j])
                position += wordIdAndLength[j]
            }
        }
        return result
    }

    fun getFST(): TokenInfoFST = fst

    fun lookupSegmentation(phraseID: Int): IntArray = segmentations[phraseID]

    private data class Match(val position: Int, val wordIdAndLength: IntArray)
}
