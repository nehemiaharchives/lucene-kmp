package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.jdkport.floatToRawIntBits


/**
 * Syntactic sugar for encoding floats as NumericDocValues via [ ][Float.floatToRawIntBits].
 *
 *
 * Per-document floating point values can be retrieved via [ ][org.apache.lucene.index.LeafReader.getNumericDocValues].
 *
 *
 * **NOTE**: In most all cases this will be rather inefficient, requiring four bytes per
 * document. Consider encoding floating point values yourself with only as much precision as you
 * require.
 */
class FloatDocValuesField
/**
 * Creates a new DocValues field with the specified 32-bit float value
 *
 * @param name field name
 * @param value 32-bit float value
 * @throws IllegalArgumentException if the field name is null
 */
    (name: String, value: Float) : NumericDocValuesField(name, Float.floatToRawIntBits(value).toLong()
) {
    override fun setFloatValue(value: Float) {
        super.setLongValue(Float.floatToRawIntBits(value).toLong())
    }

    override fun setLongValue(value: Long) {
        throw IllegalArgumentException("cannot change value type from Float to Long")
    }
}
