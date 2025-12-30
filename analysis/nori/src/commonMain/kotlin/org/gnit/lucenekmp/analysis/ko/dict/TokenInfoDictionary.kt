package org.gnit.lucenekmp.analysis.ko.dict

import org.gnit.lucenekmp.analysis.morph.BinaryDictionary
import org.gnit.lucenekmp.jdkport.BufferedInputStream
import org.gnit.lucenekmp.jdkport.ByteArrayInputStream
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.InputStreamDataInput
import org.gnit.lucenekmp.util.IOSupplier
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.util.fst.PositiveIntOutputs
import okio.Path

/**
 * Binary dictionary implementation for a known-word dictionary model: Words are encoded into an FST
 * mapping to a list of wordIDs.
 */
class TokenInfoDictionary private constructor(
    targetMapResource: IOSupplier<InputStream>,
    posResource: IOSupplier<InputStream>,
    dictResource: IOSupplier<InputStream>,
    fstResource: IOSupplier<InputStream>
) : BinaryDictionary<TokenInfoMorphData>(
    targetMapResource,
    dictResource,
    DictionaryConstants.TARGETMAP_HEADER,
    DictionaryConstants.DICT_HEADER,
    DictionaryConstants.VERSION
) {
    companion object {
        const val FST_FILENAME_SUFFIX: String = "\$fst.dat"

        fun getInstance(): TokenInfoDictionary = SingletonHolder.INSTANCE

        internal fun getClassResource(suffix: String): InputStream {
            val data = when (suffix) {
                TARGETMAP_FILENAME_SUFFIX -> KoreanDictionaryData.tokenInfoTargetMap
                POSDICT_FILENAME_SUFFIX -> KoreanDictionaryData.tokenInfoPosDict
                DICT_FILENAME_SUFFIX -> KoreanDictionaryData.tokenInfoDict
                FST_FILENAME_SUFFIX -> KoreanDictionaryData.tokenInfoFst
                else -> throw IllegalArgumentException("Unknown dictionary resource suffix: $suffix")
            }
            return ByteArrayInputStream(data)
        }

        private object SingletonHolder {
            val INSTANCE: TokenInfoDictionary = TokenInfoDictionary(
                { getClassResource(TARGETMAP_FILENAME_SUFFIX) },
                { getClassResource(POSDICT_FILENAME_SUFFIX) },
                { getClassResource(DICT_FILENAME_SUFFIX) },
                { getClassResource(FST_FILENAME_SUFFIX) }
            )
        }
    }

    private val fst: TokenInfoFST
    private val morphAtts: TokenInfoMorphData

    init {
        morphAtts = TokenInfoMorphData(buffer, posResource)
        val fstInput = BufferedInputStream(fstResource.get())
        fstInput.use { input ->
            val `in`: DataInput = InputStreamDataInput(input)
            val metadata = FST.readMetadata(`in`, PositiveIntOutputs.singleton)
            val fst = FST(metadata, `in`)
            this.fst = TokenInfoFST(fst)
        }
    }

    /**
     * Create a [TokenInfoDictionary] from an external resource path.
     */
    constructor(targetMapFile: Path, posDictFile: Path, dictFile: Path, fstFile: Path) : this(
        { org.gnit.lucenekmp.jdkport.Files.newInputStream(targetMapFile) },
        { org.gnit.lucenekmp.jdkport.Files.newInputStream(posDictFile) },
        { org.gnit.lucenekmp.jdkport.Files.newInputStream(dictFile) },
        { org.gnit.lucenekmp.jdkport.Files.newInputStream(fstFile) }
    )

    fun getFST(): TokenInfoFST = fst

    override fun getMorphAttributes(): TokenInfoMorphData = morphAtts
}
