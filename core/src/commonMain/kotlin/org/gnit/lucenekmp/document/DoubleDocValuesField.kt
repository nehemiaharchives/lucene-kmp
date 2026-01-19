package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.jdkport.doubleToRawLongBits


/**
 * Syntactic sugar for encoding doubles as NumericDocValues via [ ][Double.doubleToRawLongBits].
 *
 *
 * Per-document double values can be retrieved via [ ][org.apache.lucene.index.LeafReader.getNumericDocValues].
 *
 *
 * **NOTE**: In most all cases this will be rather inefficient, requiring eight bytes per
 * document. Consider encoding double values yourself with only as much precision as you require.
 */
class DoubleDocValuesField
/**
 * Creates a new DocValues field with the specified 64-bit double value
 *
 * @param name field name
 * @param value 64-bit double value
 * @throws IllegalArgumentException if the field name is null
 */
    (name: String, value: Double) : NumericDocValuesField(name, Double.doubleToRawLongBits(value)
) {
    override fun setDoubleValue(value: Double) {
        super.setLongValue(Double.doubleToRawLongBits(value))
    }

    override fun setLongValue(value: Long) {
        throw IllegalArgumentException("cannot change value type from Double to Long")
    }
}
