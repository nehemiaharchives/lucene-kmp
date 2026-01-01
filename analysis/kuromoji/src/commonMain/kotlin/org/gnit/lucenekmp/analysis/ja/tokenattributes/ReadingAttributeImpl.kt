package org.gnit.lucenekmp.analysis.ja.tokenattributes

import org.gnit.lucenekmp.analysis.ja.Token
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import org.gnit.lucenekmp.util.AttributeSource

/** Attribute for Kuromoji reading data. */
class ReadingAttributeImpl : AttributeImpl(), ReadingAttribute {
    companion object {
        init {
            AttributeSource.registerAttributeInterfaces(
                ReadingAttributeImpl::class,
                arrayOf(ReadingAttribute::class)
            )
        }

        fun ensureRegistered() {
            // Forces companion initialization on K/N so AttributeSource sees registrations.
        }
    }

    private var token: Token? = null

    override fun getReading(): String? = token?.getReading()

    override fun getPronunciation(): String? = token?.getPronunciation()

    override fun setToken(token: Token?) {
        this.token = token
    }

    override fun clear() {
        token = null
    }

    override fun copyTo(target: AttributeImpl) {
        (target as ReadingAttribute).setToken(token)
    }

    override fun reflectWith(reflector: AttributeReflector) {
        reflector.reflect(ReadingAttribute::class, "reading", getReading())
        reflector.reflect(ReadingAttribute::class, "pronunciation", getPronunciation())
    }

    override fun newInstance(): AttributeImpl = ReadingAttributeImpl()
}
