package org.gnit.lucenekmp.analysis.morfologik

import org.gnit.lucenekmp.util.Attribute

/**
 * Morfologik provides morphosyntactic annotations for surface forms.
 */
interface MorphosyntacticTagsAttribute : Attribute {
    /**
     * Set the POS tags for current lemma.
     */
    fun setTags(tags: List<StringBuilder>?)

    /**
     * Returns the POS tags of the term.
     */
    fun getTags(): List<StringBuilder>?

    /**
     * Clear to default value.
     */
    fun clear()
}
