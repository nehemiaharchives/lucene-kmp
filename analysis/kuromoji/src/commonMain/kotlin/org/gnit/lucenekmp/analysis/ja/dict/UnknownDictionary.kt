package org.gnit.lucenekmp.analysis.ja.dict

import org.gnit.lucenekmp.analysis.morph.BinaryDictionary
import org.gnit.lucenekmp.jdkport.ByteArrayInputStream
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.util.IOSupplier
import okio.Path

/** Dictionary for unknown-word handling. */
class UnknownDictionary internal constructor(
    targetMapResource: IOSupplier<InputStream>,
    posResource: IOSupplier<InputStream>,
    dictResource: IOSupplier<InputStream>
) : BinaryDictionary<UnknownMorphData>(
    targetMapResource,
    dictResource,
    DictionaryConstants.TARGETMAP_HEADER,
    DictionaryConstants.DICT_HEADER,
    DictionaryConstants.VERSION
) {
    private val characterDefinition = CharacterDefinition.getInstance()
    private val morphAtts: UnknownMorphData = UnknownMorphData(buffer, posResource)

    companion object {
        fun getInstance(): UnknownDictionary = SingletonHolder.INSTANCE

        private object SingletonHolder {
            val INSTANCE: UnknownDictionary = UnknownDictionary(
                { getClassResource(TARGETMAP_FILENAME_SUFFIX) },
                { getClassResource(POSDICT_FILENAME_SUFFIX) },
                { getClassResource(DICT_FILENAME_SUFFIX) }
            )
        }

        private fun getClassResource(suffix: String): InputStream {
            val data = when (suffix) {
                TARGETMAP_FILENAME_SUFFIX -> JapaneseDictionaryData.unknownTargetMap
                POSDICT_FILENAME_SUFFIX -> JapaneseDictionaryData.unknownPosDict
                DICT_FILENAME_SUFFIX -> JapaneseDictionaryData.unknownDict
                else -> throw IllegalArgumentException("Unknown dictionary resource suffix: $suffix")
            }
            return ByteArrayInputStream(data)
        }
    }

    /**
     * Create a [UnknownDictionary] from an external resource path.
     */
    constructor(targetMapFile: Path, posDictFile: Path, dictFile: Path) : this(
        { org.gnit.lucenekmp.jdkport.Files.newInputStream(targetMapFile) },
        { org.gnit.lucenekmp.jdkport.Files.newInputStream(posDictFile) },
        { org.gnit.lucenekmp.jdkport.Files.newInputStream(dictFile) }
    )

    fun getCharacterDefinition(): CharacterDefinition = characterDefinition

    override fun getMorphAttributes(): UnknownMorphData = morphAtts
}
