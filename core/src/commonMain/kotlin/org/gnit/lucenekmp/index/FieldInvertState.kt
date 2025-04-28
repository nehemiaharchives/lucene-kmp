package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.analysis.tokenattributes.OffsetAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PayloadAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.PositionIncrementAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermFrequencyAttribute
import org.gnit.lucenekmp.analysis.tokenattributes.TermToBytesRefAttribute
import org.gnit.lucenekmp.util.AttributeSource


/**
 * This class tracks the number and position / offset parameters of terms being added to the index.
 * The information collected in this class is also used to calculate the normalization factor for a
 * field.
 *
 * @lucene.experimental
 */
class FieldInvertState
/** Creates {code FieldInvertState} for the specified field name.  */(
    /** Return the version that was used to create the index, or 6 if it was created before 7.0.  */
    val indexCreatedVersionMajor: Int,
    /** Return the field's name  */
    val name: String?,
    /** Get the index options for this field  */
    val indexOptions: IndexOptions?
) {
    /**
     * Get the last processed term position.
     *
     * @return the position
     */
    var position: Int = 0
    /**
     * Get total number of terms in this field.
     *
     * @return the length
     */
    /** Set length value.  */
    var length: Int = 0
    /**
     * Get the number of terms with `positionIncrement == 0`.
     *
     * @return the numOverlap
     */
    /** Set number of terms with `positionIncrement == 0`.  */
    var numOverlap: Int = 0

    /**
     * Get end offset of the last processed term.
     *
     * @return the offset
     */
    var offset: Int = 0

    /**
     * Get the maximum term-frequency encountered for any term in the field. A field containing "the
     * quick brown fox jumps over the lazy dog" would have a value of 2, because "the" appears twice.
     */
    var maxTermFrequency: Int = 0

    /** Return the number of unique terms encountered in this field.  */
    var uniqueTermCount: Int = 0

    // we must track these across field instances (multi-valued case)
    var lastStartOffset: Int = 0
    var lastPosition: Int = 0

    var attributeSource: AttributeSource?
    // TODO: better name?
        /** Sets attributeSource to a new instance.  */
        set(attributeSource: AttributeSource?) {
            if (this.attributeSource !== attributeSource) {
                this.attributeSource = attributeSource
                if (attributeSource == null) {
                    termAttribute = null
                    termFreqAttribute = null
                    posIncrAttribute = null
                    offsetAttribute = null
                    payloadAttribute = null
                } else {
                    termAttribute = attributeSource.getAttribute(TermToBytesRefAttribute::class)
                    termFreqAttribute = attributeSource.addAttribute(TermFrequencyAttribute::class)
                    posIncrAttribute = attributeSource.addAttribute(PositionIncrementAttribute::class)
                    offsetAttribute = attributeSource.addAttribute(OffsetAttribute::class)
                    payloadAttribute = attributeSource.getAttribute(PayloadAttribute::class)
                }
            }
        }
        /**
         * Returns the [AttributeSource] from the [TokenStream] that provided the indexed
         * tokens for this field.
         */
        get(): AttributeSource? {
            return attributeSource
        }

    var offsetAttribute: OffsetAttribute? = null
    var posIncrAttribute: PositionIncrementAttribute? = null
    var payloadAttribute: PayloadAttribute? = null
    var termAttribute: TermToBytesRefAttribute? = null
    var termFreqAttribute: TermFrequencyAttribute? = null

    /** Creates {code FieldInvertState} for the specified field name and values for all fields.  */
    constructor(
        indexCreatedVersionMajor: Int,
        name: String?,
        indexOptions: IndexOptions?,
        position: Int,
        length: Int,
        numOverlap: Int,
        offset: Int,
        maxTermFrequency: Int,
        uniqueTermCount: Int
    ) : this(indexCreatedVersionMajor, name, indexOptions) {
        this.position = position
        this.length = length
        this.numOverlap = numOverlap
        this.offset = offset
        this.maxTermFrequency = maxTermFrequency
        this.uniqueTermCount = uniqueTermCount
    }

    /** Re-initialize the state  */
    fun reset() {
        position = -1
        length = 0
        numOverlap = 0
        offset = 0
        maxTermFrequency = 0
        uniqueTermCount = 0
        lastStartOffset = 0
        lastPosition = 0
    }

}
