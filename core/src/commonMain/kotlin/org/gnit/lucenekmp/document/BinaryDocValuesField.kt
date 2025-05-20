package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.util.BytesRef


/**
 * Field that stores a per-document [BytesRef] value.
 *
 *
 * The values are stored directly with no sharing, which is a good fit when the fields don't
 * share (many) values, such as a title field. If values may be shared and sorted it's better to use
 * [SortedDocValuesField]. Here's an example usage:
 *
 * <pre class="prettyprint">
 * document.add(new BinaryDocValuesField(name, new BytesRef("hello")));
</pre> *
 *
 *
 * If you also need to store the value, you should add a separate [StoredField] instance.
 *
 * @see BinaryDocValues
 */
class BinaryDocValuesField(name: String, value: BytesRef) :
    Field(name, TYPE) {
    /**
     * Create a new binary DocValues field.
     *
     * @param name field name
     * @param value binary content
     * @throws IllegalArgumentException if the field name is null
     */
    init {
        fieldsData = value
    }

    companion object {
        /** Type for straight bytes DocValues.  */
        val TYPE: FieldType = FieldType()

        init {
            TYPE.setDocValuesType(DocValuesType.BINARY)
            TYPE.freeze()
        }
    }
}
