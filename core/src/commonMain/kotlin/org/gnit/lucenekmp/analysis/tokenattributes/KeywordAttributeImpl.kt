package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector

/** Default implementation of [KeywordAttribute]. */
class KeywordAttributeImpl : AttributeImpl(), KeywordAttribute {
    private var keyword: Boolean = false

    override fun newInstance(): AttributeImpl {
        return KeywordAttributeImpl()
    }

    override fun clear() {
        keyword = false
    }

    override fun copyTo(target: AttributeImpl) {
        val attr = target as KeywordAttribute
        attr.isKeyword = keyword
    }

    override fun hashCode(): Int {
        return if (keyword) 31 else 37
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other::class != this::class) return false
        val that = other as KeywordAttributeImpl
        return keyword == that.keyword
    }

    override var isKeyword: Boolean
        get() = keyword
        set(value) {
            keyword = value
        }

    override fun reflectWith(reflector: AttributeReflector) {
        reflector.reflect(KeywordAttribute::class, "keyword", keyword)
    }
}
