package org.gnit.lucenekmp.search


/**
 * Expert: A ScoreDoc which also contains information about how to sort the referenced document. In
 * addition to the document number and score, this object contains an array of values for the
 * document from the field(s) used to sort. For example, if the sort criteria was to sort by fields
 * "a", "b" then "c", the `fields` object array will have three elements, corresponding
 * respectively to the term values for the document in fields "a", "b" and "c". The class of each
 * element in the array will be either Integer, Float or String depending on the type of values in
 * the terms of each field.
 *
 *
 * Created: Feb 11, 2004 1:23:38 PM
 *
 * @since lucene 1.4
 * @see ScoreDoc
 *
 * @see TopFieldDocs
 */
class FieldDoc : ScoreDoc {
    /**
     * Expert: The values which are used to sort the referenced document. The order of these will
     * match the original sort criteria given by a Sort object. Each Object will have been returned
     * from the `value` method corresponding FieldComparator used to sort this field.
     *
     * @see Sort
     *
     * @see IndexSearcher.search
     */
    var fields: Array<Any?>? = null

    /** Expert: Creates one of these objects with empty sort information.  */
    constructor(doc: Int, score: Float) : super(doc, score)

    /** Expert: Creates one of these objects with the given sort information.  */
    constructor(doc: Int, score: Float, fields: Array<Any?>?) : super(doc, score) {
        this.fields = fields
    }

    /** Expert: Creates one of these objects with the given sort information.  */
    constructor(doc: Int, score: Float, fields: Array<Any?>?, shardIndex: Int) : super(doc, score, shardIndex) {
        this.fields = fields
    }

    // A convenience method for debugging.
    override fun toString(): String {
        // super.toString returns the doc and score information, so just add the
        // fields information
        val sb = StringBuilder(super.toString())
        sb.append(" fields=")
        sb.append(fields.contentToString())
        return sb.toString()
    }
}
