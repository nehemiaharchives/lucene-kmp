package org.gnit.lucenekmp.analysis.morph

import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.InputStreamDataInput
import org.gnit.lucenekmp.util.IOSupplier

/** Character category data. */
abstract class CharacterDefinition(
    charDefResource: IOSupplier<InputStream>,
    charDefCodecHeader: String,
    charDefCodecVersion: Int,
    classCount: Int
) {
    companion object {
        const val FILENAME_SUFFIX: String = ".dat"
    }

    protected val characterCategoryMap: ByteArray = ByteArray(0x10000)
    private val invokeMap: BooleanArray
    private val groupMap: BooleanArray

    init {
        charDefResource.get().use { input ->
            val `in`: DataInput = InputStreamDataInput(input)
            CodecUtil.checkHeader(`in`, charDefCodecHeader, charDefCodecVersion, charDefCodecVersion)
            `in`.readBytes(characterCategoryMap, 0, characterCategoryMap.size)
            invokeMap = BooleanArray(classCount)
            groupMap = BooleanArray(classCount)
            for (i in 0 until classCount) {
                val b = `in`.readByte()
                invokeMap[i] = (b.toInt() and 0x01) != 0
                groupMap[i] = (b.toInt() and 0x02) != 0
            }
        }
    }

    fun getCharacterClass(c: Char): Byte = characterCategoryMap[c.code]

    fun isInvoke(c: Char): Boolean = invokeMap[characterCategoryMap[c.code].toInt()]

    fun isGroup(c: Char): Boolean = groupMap[characterCategoryMap[c.code].toInt()]

    /** Functional interface to lookup character class */
    fun interface LookupCharacterClass {
        /** looks up character class for given class name */
        fun lookupCharacterClass(characterClassName: String): Byte
    }
}
