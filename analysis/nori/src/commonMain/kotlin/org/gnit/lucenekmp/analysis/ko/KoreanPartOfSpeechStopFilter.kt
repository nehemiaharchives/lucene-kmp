package org.gnit.lucenekmp.analysis.ko

import org.gnit.lucenekmp.analysis.FilteringTokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.analysis.ko.tokenattributes.PartOfSpeechAttribute

/**
 * Removes tokens that match a set of part-of-speech tags.
 */
class KoreanPartOfSpeechStopFilter(
    input: TokenStream,
    private val stopTags: Set<POS.Tag> = DEFAULT_STOP_TAGS
) : FilteringTokenFilter(input) {

    private val posAtt: PartOfSpeechAttribute = addAttribute(PartOfSpeechAttribute::class)

    companion object {
        /** Default list of tags to filter. */
        val DEFAULT_STOP_TAGS: Set<POS.Tag> = setOf(
            POS.Tag.EP,
            POS.Tag.EF,
            POS.Tag.EC,
            POS.Tag.ETN,
            POS.Tag.ETM,
            POS.Tag.IC,
            POS.Tag.JKS,
            POS.Tag.JKC,
            POS.Tag.JKG,
            POS.Tag.JKO,
            POS.Tag.JKB,
            POS.Tag.JKV,
            POS.Tag.JKQ,
            POS.Tag.JX,
            POS.Tag.JC,
            POS.Tag.MAG,
            POS.Tag.MAJ,
            POS.Tag.MM,
            POS.Tag.SP,
            POS.Tag.SSC,
            POS.Tag.SSO,
            POS.Tag.SC,
            POS.Tag.SE,
            POS.Tag.XPN,
            POS.Tag.XSA,
            POS.Tag.XSN,
            POS.Tag.XSV,
            POS.Tag.UNA,
            POS.Tag.NA,
            POS.Tag.VSV
        )
    }

    override fun accept(): Boolean {
        val leftPOS = posAtt.getLeftPOS()
        return leftPOS == null || !stopTags.contains(leftPOS)
    }
}
