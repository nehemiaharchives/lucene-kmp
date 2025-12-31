package org.gnit.lucenekmp.analysis.ja.dict

import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.util.IOSupplier

/** Morphological information for unk dictionary. */
class UnknownMorphData(buffer: ByteBuffer, posResource: IOSupplier<InputStream>) :
    TokenInfoMorphData(buffer, posResource) {

    override fun getReading(morphId: Int, surface: CharArray, off: Int, len: Int): String? = null

    override fun getInflectionType(morphId: Int): String? = null

    override fun getInflectionForm(wordId: Int): String? = null
}
