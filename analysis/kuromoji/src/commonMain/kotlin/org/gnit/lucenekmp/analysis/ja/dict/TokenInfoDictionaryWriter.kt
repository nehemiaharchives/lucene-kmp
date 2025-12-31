package org.gnit.lucenekmp.analysis.ja.dict

import org.gnit.lucenekmp.analysis.morph.BinaryDictionaryWriter
import org.gnit.lucenekmp.util.fst.FST
import okio.Path

internal class TokenInfoDictionaryWriter(size: Int) :
    BinaryDictionaryWriter<TokenInfoDictionary>(TokenInfoDictionary::class, TokenInfoDictionaryEntryWriter(size)) {

    private var fst: FST<Long>? = null

    fun setFST(fst: FST<Long>) {
        this.fst = fst
    }

    override fun write(baseDir: Path) {
        super.write(
            baseDir,
            DictionaryConstants.TARGETMAP_HEADER,
            DictionaryConstants.POSDICT_HEADER,
            DictionaryConstants.DICT_HEADER,
            DictionaryConstants.VERSION
        )
        writeFST(baseDir.resolve(getBaseFileName() + TokenInfoDictionary.FST_FILENAME_SUFFIX))
    }

    private fun writeFST(path: Path) {
        val current = fst ?: throw IllegalStateException("FST not set")
        org.gnit.lucenekmp.jdkport.Files.createDirectories(path.parent!!)
        current.save(path)
    }
}
