package org.gnit.lucenekmp.analysis.ko.tokenattributes

import org.gnit.lucenekmp.analysis.ko.Token
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import org.gnit.lucenekmp.util.AttributeSource

/**
 * Attribute for Korean reading data.
 */
class ReadingAttributeImpl : AttributeImpl(), ReadingAttribute {
    companion object {
        init {
            AttributeSource.registerAttributeInterfaces(
                ReadingAttributeImpl::class,
                arrayOf(ReadingAttribute::class)
            )
        }
    }

    private var token: Token? = null

    override fun getReading(): String? = token?.readingValue

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
    }

    override fun newInstance(): AttributeImpl = ReadingAttributeImpl()
}
