package org.gnit.lucenekmp.analysis.ko.dict

import org.gnit.lucenekmp.analysis.ko.POS
import org.gnit.lucenekmp.jdkport.fromCharArray

/** Morphological information for user dictionary. */
class UserMorphData(
    private val segmentations: Array<IntArray?>,
    private val rightIds: ShortArray
) : KoMorphData {
    companion object {
        private const val WORD_COST: Int = -100000
        // NNG left
        private const val LEFT_ID: Short = 1781
    }

    override fun getLeftId(morphId: Int): Int = LEFT_ID.toInt()

    override fun getRightId(morphId: Int): Int = rightIds[morphId].toInt()

    override fun getWordCost(morphId: Int): Int = WORD_COST

    override fun getPOSType(morphId: Int): POS.Type {
        return if (segmentations[morphId] == null) POS.Type.MORPHEME else POS.Type.COMPOUND
    }

    override fun getLeftPOS(morphId: Int): POS.Tag = POS.Tag.NNG

    override fun getRightPOS(morphId: Int): POS.Tag = POS.Tag.NNG

    override fun getReading(morphId: Int): String? = null

    override fun getMorphemes(
        morphId: Int,
        surfaceForm: CharArray,
        off: Int,
        len: Int
    ): Array<KoMorphData.Morpheme>? {
        val segs = segmentations[morphId] ?: return null
        var offset = 0
        val morphemes = Array(segs.size) { KoMorphData.Morpheme(POS.Tag.NNG, "") }
        for (i in segs.indices) {
            morphemes[i] = KoMorphData.Morpheme(POS.Tag.NNG, String.fromCharArray(surfaceForm, off + offset, segs[i]))
            offset += segs[i]
        }
        return morphemes
    }
}
