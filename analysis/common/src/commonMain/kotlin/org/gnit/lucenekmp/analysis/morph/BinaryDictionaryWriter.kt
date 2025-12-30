package org.gnit.lucenekmp.analysis.morph

import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.jdkport.BufferedOutputStream
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.OutputStreamDataOutput
import org.gnit.lucenekmp.util.ArrayUtil
import okio.IOException
import okio.Path
import kotlin.reflect.KClass

/** Abstract base dictionary writer class. */
abstract class BinaryDictionaryWriter<T : BinaryDictionary<out MorphData>>(
    private val implClazz: KClass<T>,
    protected val entryWriter: DictionaryEntryWriter
) {
    private var targetMapEndOffset = 0
    private var lastWordId = -1
    private var lastSourceId = -1
    private var targetMap: IntArray = IntArray(8192)
    private var targetMapOffsets: IntArray = IntArray(8192)

    /**
     * put the entry in map
     *
     * @return current position of buffer, which will be wordId of next entry
     */
    open fun put(entry: Array<String>): Int = entryWriter.putEntry(entry)

    /**
     * Write whole dictionary in a directory.
     *
     * @throws IOException if an I/O error occurs writing the dictionary files
     */
    @Throws(IOException::class)
    abstract fun write(baseDir: Path)

    fun addMapping(sourceId: Int, wordId: Int) {
        if (wordId <= lastWordId) {
            throw IllegalStateException("words out of order: $wordId vs lastID: $lastWordId")
        }

        if (sourceId > lastSourceId) {
            targetMapOffsets = ArrayUtil.grow(targetMapOffsets, sourceId + 1)
            for (i in lastSourceId + 1..sourceId) {
                targetMapOffsets[i] = targetMapEndOffset
            }
        } else if (sourceId != lastSourceId) {
            throw IllegalStateException(
                "source ids not in increasing order: lastSourceId=$lastSourceId vs sourceId=$sourceId"
            )
        }

        targetMap = ArrayUtil.grow(targetMap, targetMapEndOffset + 1)
        targetMap[targetMapEndOffset] = wordId
        targetMapEndOffset++

        lastSourceId = sourceId
        lastWordId = wordId
    }

    /**
     * Write dictionary in file. Dictionary format is: [Size of dictionary(int)], [entry:{left
     * id(short)}{right id(short)}{word cost(short)}{length of pos info(short)}{pos info(char)}],
     * [entry], [entry].....
     *
     * @throws IOException if an I/O error occurs writing the dictionary files
     */
    @Throws(IOException::class)
    protected fun write(
        baseDir: Path,
        targetMapCodecHeader: String,
        posDictCodecHeader: String,
        dictCodecHeader: String,
        dictCodecVersion: Int
    ) {
        val baseName = getBaseFileName()
        entryWriter.writeDictionary(
            baseDir.resolve(baseName + BinaryDictionary.DICT_FILENAME_SUFFIX),
            dictCodecHeader,
            dictCodecVersion
        )
        entryWriter.writePosDict(
            baseDir.resolve(baseName + BinaryDictionary.POSDICT_FILENAME_SUFFIX),
            posDictCodecHeader,
            dictCodecVersion
        )
        writeTargetMap(
            baseDir.resolve(baseName + BinaryDictionary.TARGETMAP_FILENAME_SUFFIX),
            targetMapCodecHeader,
            dictCodecVersion
        )
    }

    protected fun getBaseFileName(): String {
        val name = implClazz.qualifiedName
            ?: throw IllegalStateException("Class name unavailable for $implClazz")
        return name.replace('.', '/')
    }

    private fun writeTargetMap(path: Path, targetMapCodecHeader: String, dictCodecVersion: Int) {
        path.parent?.let { Files.createDirectories(it) }
        Files.newOutputStream(path).use { os ->
            BufferedOutputStream(os).use { bos ->
                val out: DataOutput = OutputStreamDataOutput(bos)
                CodecUtil.writeHeader(out, targetMapCodecHeader, dictCodecVersion)

                val numSourceIds = lastSourceId + 1
                out.writeVInt(targetMapEndOffset)
                out.writeVInt(numSourceIds + 1)
                var prev = 0
                var sourceId = 0
                for (ofs in 0 until targetMapEndOffset) {
                    val value = targetMap[ofs]
                    val delta = value - prev
                    if (ofs == targetMapOffsets[sourceId]) {
                        out.writeVInt((delta shl 1) or 0x01)
                        sourceId++
                    } else {
                        out.writeVInt((delta shl 1))
                    }
                    prev += delta
                }
                if (sourceId != numSourceIds) {
                    throw IllegalStateException("sourceId:$sourceId != numSourceIds:$numSourceIds")
                }
            }
        }
    }
}
