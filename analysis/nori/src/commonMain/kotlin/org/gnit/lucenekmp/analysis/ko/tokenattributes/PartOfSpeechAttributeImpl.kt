package org.gnit.lucenekmp.analysis.ko.tokenattributes

import org.gnit.lucenekmp.analysis.ko.Token
import org.gnit.lucenekmp.analysis.ko.dict.KoMorphData
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import org.gnit.lucenekmp.util.AttributeSource

/**
 * Part of Speech attributes for Korean.
 */
class PartOfSpeechAttributeImpl : AttributeImpl(), PartOfSpeechAttribute {
    companion object {
        init {
            AttributeSource.registerAttributeInterfaces(
                PartOfSpeechAttributeImpl::class,
                arrayOf(PartOfSpeechAttribute::class)
            )
        }
    }

    private var token: Token? = null

    override fun getPOSType() = token?.posType

    override fun getLeftPOS() = token?.leftPOS

    override fun getRightPOS() = token?.rightPOS

    override fun getMorphemes(): Array<KoMorphData.Morpheme>? = token?.morphemeArray

    override fun setToken(token: Token?) {
        this.token = token
    }

    override fun clear() {
        token = null
    }

    override fun reflectWith(reflector: AttributeReflector) {
        val posName = getPOSType()?.name
        val rightPOS = getRightPOS()?.let { "${it.name}(${it.description()})" }
        val leftPOS = getLeftPOS()?.let { "${it.name}(${it.description()})" }
        reflector.reflect(PartOfSpeechAttribute::class, "posType", posName)
        reflector.reflect(PartOfSpeechAttribute::class, "leftPOS", leftPOS)
        reflector.reflect(PartOfSpeechAttribute::class, "rightPOS", rightPOS)
        reflector.reflect(PartOfSpeechAttribute::class, "morphemes", displayMorphemes(getMorphemes()))
    }

    private fun displayMorphemes(morphemes: Array<KoMorphData.Morpheme>?): String? {
        if (morphemes == null) return null
        val builder = StringBuilder()
        for (morpheme in morphemes) {
            if (builder.isNotEmpty()) {
                builder.append("+")
            }
            builder.append(morpheme.surfaceForm)
                .append('/')
                .append(morpheme.posTag.name)
                .append('(')
                .append(morpheme.posTag.description())
                .append(')')
        }
        return builder.toString()
    }

    override fun copyTo(target: AttributeImpl) {
        (target as PartOfSpeechAttribute).setToken(token)
    }

    override fun newInstance(): AttributeImpl = PartOfSpeechAttributeImpl()
}
