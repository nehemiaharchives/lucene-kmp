package org.gnit.lucenekmp.analysis.ja.tokenattributes

import org.gnit.lucenekmp.analysis.ja.Token
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import org.gnit.lucenekmp.util.AttributeSource

/** Attribute for [Token.getBaseForm]. */
class BaseFormAttributeImpl : AttributeImpl(), BaseFormAttribute {
    companion object {
        init {
            AttributeSource.registerAttributeInterfaces(
                BaseFormAttributeImpl::class,
                arrayOf(BaseFormAttribute::class)
            )
        }
    }

    private var token: Token? = null

    override fun getBaseForm(): String? = token?.getBaseForm()

    override fun setToken(token: Token?) {
        this.token = token
    }

    override fun clear() {
        token = null
    }

    override fun copyTo(target: AttributeImpl) {
        (target as BaseFormAttribute).setToken(token)
    }

    override fun reflectWith(reflector: AttributeReflector) {
        reflector.reflect(BaseFormAttribute::class, "baseForm", getBaseForm())
    }

    override fun newInstance(): AttributeImpl = BaseFormAttributeImpl()
}
