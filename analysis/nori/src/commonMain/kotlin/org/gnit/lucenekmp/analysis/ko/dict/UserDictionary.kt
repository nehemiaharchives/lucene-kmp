package org.gnit.lucenekmp.analysis.ko.dict

import org.gnit.lucenekmp.analysis.morph.Dictionary
import org.gnit.lucenekmp.jdkport.BufferedReader
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.jdkport.InputStreamReader
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.util.fst.FSTCompiler
import org.gnit.lucenekmp.util.fst.PositiveIntOutputs
import okio.IOException

/**
 * Class for building a User Dictionary. This class allows for adding custom nouns (세종) or compounds
 * (세종시 세종 시).
 */
class UserDictionary private constructor(entries: List<String>) : Dictionary<UserMorphData> {
    // text -> wordID
    private val fst: TokenInfoFST

    // NNG right
    private val RIGHT_ID: Short = 3533
    // NNG right with hangul and a coda on the last char
    private val RIGHT_ID_T: Short = 3535
    // NNG right with hangul and no coda on the last char
    private val RIGHT_ID_F: Short = 3534

    private val morphAtts: UserMorphData

    companion object {
        @Throws(IOException::class)
        fun open(reader: InputStream): UserDictionary? {
            return open(InputStreamReader(reader))
        }

        @Throws(IOException::class)
        fun open(reader: Reader): UserDictionary? {
            val br = BufferedReader(reader)
            val entries: MutableList<String> = ArrayList()
            var line: String?
            while (true) {
                line = br.readLine()
                if (line == null) break
                // Remove comments
                line = line.replace(Regex("#.*$"), "")
                if (line.trim().isEmpty()) {
                    continue
                }
                entries.add(line)
            }
            return if (entries.isEmpty()) {
                null
            } else {
                UserDictionary(entries)
            }
        }
    }

    init {
        val charDef = CharacterDefinition.getInstance()
        val sortedEntries = entries.sortedWith { a, b ->
            val aToken = a.split(Regex("\\s+")).first()
            val bToken = b.split(Regex("\\s+")).first()
            aToken.compareTo(bToken)
        }

        val fstOutput = PositiveIntOutputs.singleton
        val fstCompiler = FSTCompiler.Builder(FST.INPUT_TYPE.BYTE2, fstOutput).build()
        val scratch = IntsRefBuilder()

        var lastToken: String? = null
        val segmentations: MutableList<IntArray?> = ArrayList(sortedEntries.size)
        var rightIds = ShortArray(sortedEntries.size)
        var ord = 0L
        var entryIndex = 0
        for (entry in sortedEntries) {
            val splits = entry.split(Regex("\\s+"))
            val token = splits[0]
            if (token == lastToken) {
                continue
            }
            val lastChar = entry[entry.length - 1]
            if (charDef.isHangul(lastChar)) {
                rightIds[entryIndex++] = if (charDef.hasCoda(lastChar)) RIGHT_ID_T else RIGHT_ID_F
            } else {
                rightIds[entryIndex++] = RIGHT_ID
            }

            if (splits.size == 1) {
                segmentations.add(null)
            } else {
                val length = IntArray(splits.size - 1)
                var offset = 0
                for (i in 1 until splits.size) {
                    length[i - 1] = splits[i].length
                    offset += splits[i].length
                }
                if (offset > token.length) {
                    throw IllegalArgumentException(
                        "Illegal user dictionary entry $entry - the segmentation is bigger than the surface form ($token)"
                    )
                }
                segmentations.add(length)
            }

            scratch.growNoCopy(token.length)
            scratch.setLength(token.length)
            for (i in token.indices) {
                scratch.setIntAt(i, token[i].code)
            }
            fstCompiler.add(scratch.get(), ord)
            lastToken = token
            ord++
        }

        if (entryIndex < rightIds.size) {
            rightIds = ArrayUtil.copyOfSubArray(rightIds, 0, entryIndex)
        }
        val fstMeta = fstCompiler.compile()
        val fst = FST.fromFSTReader(fstMeta, fstCompiler.getFSTReader())
            ?: throw IllegalStateException("FST compilation produced null")
        this.fst = TokenInfoFST(fst)
        val segsArray: Array<IntArray?> = segmentations.toTypedArray()
        this.morphAtts = UserMorphData(segsArray, rightIds)
    }

    fun getFST(): TokenInfoFST = fst

    override fun getMorphAttributes(): UserMorphData = morphAtts

    /**
     * Lookup words in text
     *
     * @param chars text
     * @param off offset into text
     * @param len length of text
     * @return array of wordId
     */
    @Throws(IOException::class)
    fun lookup(chars: CharArray, off: Int, len: Int): List<Int> {
        val result: MutableList<Int> = ArrayList()
        val fstReader = fst.getBytesReader()

        var arc = FST.Arc<Long>()
        val end = off + len
        for (startOffset in off until end) {
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
                    result.add(finalOutput.toInt())
                }
            }
        }
        return result
    }
}
