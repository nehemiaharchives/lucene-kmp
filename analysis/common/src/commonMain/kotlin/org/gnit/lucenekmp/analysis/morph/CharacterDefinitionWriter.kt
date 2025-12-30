package org.gnit.lucenekmp.analysis.morph

import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.jdkport.BufferedOutputStream
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.OutputStreamDataOutput
import okio.IOException
import okio.Path
import kotlin.reflect.KClass

/** Writes character definition file */
class CharacterDefinitionWriter<T : CharacterDefinition>(
    private val implClazz: KClass<T>,
    defaultValue: Byte,
    private val classCount: Int,
    private val lookupCharClass: CharacterDefinition.LookupCharacterClass
) {
    private val characterCategoryMap: ByteArray = ByteArray(0x10000) { defaultValue }
    private val invokeMap: BooleanArray = BooleanArray(classCount)
    private val groupMap: BooleanArray = BooleanArray(classCount)

    /**
     * Put mapping from unicode code point to character class.
     *
     * @param codePoint code point
     * @param characterClassName character class name
     */
    fun putCharacterCategory(codePoint: Int, characterClassName: String) {
        var name = characterClassName.split(" ")[0]
        if (codePoint == 0x30FB) {
            name = "SYMBOL"
        }
        characterCategoryMap[codePoint] = lookupCharClass.lookupCharacterClass(name)
    }

    fun putInvokeDefinition(characterClassName: String, invoke: Int, group: Int, length: Int) {
        val characterClass = lookupCharClass.lookupCharacterClass(characterClassName)
        invokeMap[characterClass.toInt()] = invoke == 1
        groupMap[characterClass.toInt()] = group == 1
        // length def ignored
    }

    private fun getBaseFileName(): String {
        val name = implClazz.qualifiedName
            ?: throw IllegalStateException("Class name unavailable for ${implClazz}")
        return name.replace('.', '/')
    }

    @Throws(IOException::class)
    fun write(baseDir: Path, charDefCodecHeader: String, charDefCodecVersion: Int) {
        val path = baseDir.resolve(getBaseFileName() + CharacterDefinition.FILENAME_SUFFIX)
        path.parent?.let { Files.createDirectories(it) }
        Files.newOutputStream(path).use { os ->
            BufferedOutputStream(os).use { bos ->
                val out: DataOutput = OutputStreamDataOutput(bos)
                CodecUtil.writeHeader(out, charDefCodecHeader, charDefCodecVersion)
                out.writeBytes(characterCategoryMap, 0, characterCategoryMap.size)
                for (i in 0 until classCount) {
                    val b = ((if (invokeMap[i]) 0x01 else 0x00) or (if (groupMap[i]) 0x02 else 0x00)).toByte()
                    out.writeByte(b)
                }
            }
        }
    }
}
