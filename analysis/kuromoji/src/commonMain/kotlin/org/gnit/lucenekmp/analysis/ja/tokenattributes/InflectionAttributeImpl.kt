package org.gnit.lucenekmp.analysis.ja.tokenattributes

import org.gnit.lucenekmp.analysis.ja.Token
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import org.gnit.lucenekmp.util.AttributeSource

/** Attribute for Kuromoji inflection data. */
class InflectionAttributeImpl : AttributeImpl(), InflectionAttribute {
    companion object {
        init {
            AttributeSource.registerAttributeInterfaces(
                InflectionAttributeImpl::class,
                arrayOf(InflectionAttribute::class)
            )
        }
    }

    private var token: Token? = null

    override fun getInflectionType(): String? = token?.getInflectionType()

    override fun getInflectionForm(): String? = token?.getInflectionForm()

    override fun setToken(token: Token?) {
        this.token = token
    }

    override fun clear() {
        token = null
    }

    override fun copyTo(target: AttributeImpl) {
        (target as InflectionAttribute).setToken(token)
    }

    override fun reflectWith(reflector: AttributeReflector) {
        reflector.reflect(InflectionAttribute::class, "inflectionType", getInflectionType())
        reflector.reflect(InflectionAttribute::class, "inflectionForm", getInflectionForm())
    }

    override fun newInstance(): AttributeImpl = InflectionAttributeImpl()
}
