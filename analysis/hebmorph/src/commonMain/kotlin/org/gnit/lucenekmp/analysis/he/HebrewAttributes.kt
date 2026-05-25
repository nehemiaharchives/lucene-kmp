package org.gnit.lucenekmp.analysis.he

import org.gnit.lucenekmp.analysis.tokenattributes.KeywordAttribute
import org.gnit.lucenekmp.util.Attribute
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import org.gnit.lucenekmp.util.AttributeSource

/**
 * This attribute is used to pass info on tokens as parsed and identified
 * by the HebMorph tokenizer
 */
interface HebrewTokenTypeAttribute : Attribute {
    enum class HebrewType {
        Unknown,
        Hebrew,
        NonHebrew,
        Numeric,
        Construct,
        Acronym,
        Mixed,
        Lemma;
    }

    fun setType(type: HebrewType)
    fun getType(): HebrewType
    fun isHebrew(): Boolean
    fun isExact(): Boolean
    fun isNumeric(): Boolean
    fun setExact(isExact: Boolean)
}

class HebrewTokenTypeAttributeImpl : AttributeImpl(), HebrewTokenTypeAttribute {
    private var type = HebrewTokenTypeAttribute.HebrewType.Unknown
    private var isExact = false

    init {
        AttributeSource.registerAttributeInterfaces(
            HebrewTokenTypeAttributeImpl::class,
            arrayOf(HebrewTokenTypeAttribute::class)
        )
    }

    override fun setType(type: HebrewTokenTypeAttribute.HebrewType) {
        this.type = type
    }

    override fun getType(): HebrewTokenTypeAttribute.HebrewType = type

    override fun isHebrew(): Boolean {
        if (type == HebrewTokenTypeAttribute.HebrewType.Hebrew ||
            type == HebrewTokenTypeAttribute.HebrewType.Acronym ||
            type == HebrewTokenTypeAttribute.HebrewType.Construct
        ) {
            return true
        }
        return false
    }

    override fun isNumeric(): Boolean {
        return type == HebrewTokenTypeAttribute.HebrewType.Numeric
    }

    override fun isExact(): Boolean = isExact

    override fun setExact(isExact: Boolean) {
        this.isExact = isExact
    }

    override fun clear() {
        type = HebrewTokenTypeAttribute.HebrewType.Unknown
        isExact = false
    }

    override fun reflectWith(reflector: AttributeReflector) {
        reflector.reflect(KeywordAttribute::class, "isExact", isExact)
        reflector.reflect(KeywordAttribute::class, "type", type)
    }

    override fun copyTo(target: AttributeImpl) {
        (target as HebrewTokenTypeAttribute).setType(type)
        target.setExact(isExact)
    }

    override fun newInstance(): AttributeImpl {
        return HebrewTokenTypeAttributeImpl()
    }
}

/**
 * Reflects the Hebrew Part-of-Speech as detected by HebMorph
 */
interface HebrewPosAttribute : Attribute {
    fun setHebrewToken(hebToken: HebrewToken?)

    enum class PosTag {
        Unknown,
        Verb,
        Noun,
        ProperNoun,
        Adjective,
    }

    fun getPosTag(): PosTag
}

class HebrewPosAttributeImpl : AttributeImpl(), HebrewPosAttribute {
    private var token: HebrewToken? = null

    init {
        AttributeSource.registerAttributeInterfaces(
            HebrewPosAttributeImpl::class,
            arrayOf(HebrewPosAttribute::class)
        )
    }

    override fun setHebrewToken(hebToken: HebrewToken?) {
        this.token = hebToken
    }

    override fun getPosTag(): HebrewPosAttribute.PosTag {
        if (token != null) {
            if (token!!.getMask() == DescFlag.D_VERB) return HebrewPosAttribute.PosTag.Verb
            if (token!!.getMask() == DescFlag.D_NOUN) return HebrewPosAttribute.PosTag.Noun
            if (token!!.getMask() == DescFlag.D_PROPER) return HebrewPosAttribute.PosTag.ProperNoun
            if (token!!.getMask() == DescFlag.D_ADJ) return HebrewPosAttribute.PosTag.Adjective
        }
        return HebrewPosAttribute.PosTag.Unknown
    }

    override fun clear() {
        token = null
    }

    override fun reflectWith(reflector: AttributeReflector) {
        val partOfSpeech = getPosTag()
        reflector.reflect(HebrewPosAttribute::class, "partOfSpeech", partOfSpeech)
    }

    override fun copyTo(target: AttributeImpl) {
        val t = target as HebrewPosAttribute
        t.setHebrewToken(token)
    }

    override fun newInstance(): AttributeImpl {
        return HebrewPosAttributeImpl()
    }
}
