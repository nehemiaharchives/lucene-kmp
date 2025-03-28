package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.document.InvertableType
import org.gnit.lucenekmp.document.StoredValue
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.jdkport.Reader


// TODO: how to handle versioning here...?
/**
 * Represents a single field for indexing. IndexWriter consumes Iterable&lt;IndexableField&gt; as a
 * document.
 *
 * @lucene.experimental
 */
interface IndexableField {
    /** Field name  */
    fun name(): String

    /** [IndexableFieldType] describing the properties of this field.  */
    fun fieldType(): IndexableFieldType

    /**
     * Creates the TokenStream used for indexing this field. If appropriate, implementations should
     * use the given Analyzer to create the TokenStreams.
     *
     * @param analyzer Analyzer that should be used to create the TokenStreams from
     * @param reuse TokenStream for a previous instance of this field **name**. This allows custom
     * field types (like StringField and NumericField) that do not use the analyzer to still have
     * good performance. Note: the passed-in type may be inappropriate, for example if you mix up
     * different types of Fields for the same field name. So it's the responsibility of the
     * implementation to check.
     * @return TokenStream value for indexing the document. Should always return a non-null value if
     * the field is to be indexed
     */
    fun tokenStream(analyzer: Analyzer, reuse: TokenStream?): TokenStream?

    /** Non-null if this field has a binary value  */
    fun binaryValue(): BytesRef?

    /** Non-null if this field has a string value  */
    fun stringValue(): String?

    val charSequenceValue: CharSequence?
        /** Non-null if this field has a string value  */
        get() = stringValue()

    /** Non-null if this field has a Reader value  */
    fun readerValue(): Reader?

    /** Non-null if this field has a numeric value  */
    fun numericValue(): Number?

    /**
     * Stored value. This method is called to populate stored fields and must return a non-null value
     * if the field stored.
     */
    fun storedValue(): StoredValue?

    /**
     * Describes how this field should be inverted. This must return a non-null value if the field
     * indexes terms and postings.
     */
    fun invertableType(): InvertableType
}
