package org.gnit.lucenekmp.analysis.morfologik

import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.util.Attribute
import org.gnit.lucenekmp.util.AttributeFactory
import org.gnit.lucenekmp.util.AttributeImpl
import kotlin.reflect.KClass

internal class MorfologikAttributeFactory(
    private val delegate: AttributeFactory = TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY
) : AttributeFactory() {
    override fun createAttributeInstance(attClass: KClass<out Attribute>): AttributeImpl {
        return when (attClass) {
            MorphosyntacticTagsAttribute::class -> MorphosyntacticTagsAttributeImpl()
            else -> delegate.createAttributeInstance(attClass)
        }
    }
}
