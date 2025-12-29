package org.gnit.lucenekmp.analysis.morfologik

import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import org.gnit.lucenekmp.util.AttributeSource

/**
 * Morphosyntactic annotations for surface forms.
 */
class MorphosyntacticTagsAttributeImpl : AttributeImpl(), MorphosyntacticTagsAttribute {
    companion object {
        init {
            AttributeSource.registerAttributeInterfaces(
                MorphosyntacticTagsAttributeImpl::class,
                arrayOf(MorphosyntacticTagsAttribute::class)
            )
        }
    }
    private var tags: List<StringBuilder>? = null

    override fun getTags(): List<StringBuilder>? = tags

    override fun clear() {
        tags = null
    }

    override fun setTags(tags: List<StringBuilder>?) {
        this.tags = tags
    }

    override fun equals(other: Any?): Boolean {
        if (other is MorphosyntacticTagsAttribute) {
            return tags == other.getTags()
        }
        return false
    }

    override fun hashCode(): Int = tags?.hashCode() ?: 0

    override fun copyTo(target: AttributeImpl) {
        var cloned: MutableList<StringBuilder>? = null
        if (tags != null) {
            cloned = ArrayList(tags!!.size)
            for (b in tags!!) {
                cloned.add(StringBuilder(b))
            }
        }
        (target as MorphosyntacticTagsAttribute).setTags(cloned)
    }

    override fun reflectWith(reflector: AttributeReflector) {
        reflector.reflect(MorphosyntacticTagsAttribute::class, "tags", tags)
    }

    override fun newInstance(): AttributeImpl = MorphosyntacticTagsAttributeImpl()
}
