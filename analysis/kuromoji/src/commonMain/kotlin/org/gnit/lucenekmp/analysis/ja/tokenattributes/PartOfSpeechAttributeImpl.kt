package org.gnit.lucenekmp.analysis.ja.tokenattributes

import org.gnit.lucenekmp.analysis.ja.Token
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import org.gnit.lucenekmp.util.AttributeSource

/** Attribute for [Token.getPartOfSpeech]. */
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

    override fun getPartOfSpeech(): String? = token?.getPartOfSpeech()

    override fun setToken(token: Token?) {
        this.token = token
    }

    override fun clear() {
        token = null
    }

    override fun copyTo(target: AttributeImpl) {
        (target as PartOfSpeechAttribute).setToken(token)
    }

    override fun reflectWith(reflector: AttributeReflector) {
        reflector.reflect(PartOfSpeechAttribute::class, "partOfSpeech", getPartOfSpeech())
    }

    override fun newInstance(): AttributeImpl = PartOfSpeechAttributeImpl()
}
