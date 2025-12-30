package org.gnit.lucenekmp.analysis.ko.dict

import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.util.IOSupplier

/** Morphological information for unk dictionary. */
class UnknownMorphData(
    buffer: ByteBuffer,
    posResource: IOSupplier<InputStream>
) : TokenInfoMorphData(buffer, posResource) {
    override fun getReading(morphId: Int): String? = null

    override fun getMorphemes(
        morphId: Int,
        surfaceForm: CharArray,
        off: Int,
        len: Int
    ): Array<KoMorphData.Morpheme>? = null
}
