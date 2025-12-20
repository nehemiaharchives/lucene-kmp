package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.IndexSorter
import org.gnit.lucenekmp.index.IndexSorter.DoubleSorter
import org.gnit.lucenekmp.index.IndexSorter.FloatSorter
import org.gnit.lucenekmp.index.IndexSorter.IntSorter
import org.gnit.lucenekmp.index.IndexSorter.LongSorter
import org.gnit.lucenekmp.index.IndexSorter.StringSorter
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SortFieldProvider
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.search.FieldComparator.RelevanceComparator
import org.gnit.lucenekmp.search.FieldComparator.TermValComparator
import org.gnit.lucenekmp.search.comparators.DocComparator
import org.gnit.lucenekmp.search.comparators.DoubleComparator
import org.gnit.lucenekmp.search.comparators.FloatComparator
import org.gnit.lucenekmp.search.comparators.IntComparator
import org.gnit.lucenekmp.search.comparators.LongComparator
import org.gnit.lucenekmp.search.comparators.TermOrdValComparator
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.NumericUtils
import kotlin.jvm.JvmName


/**
 * Stores information about how to sort documents by terms in an individual field. Fields must be
 * indexed in order to sort by them.
 *
 *
 * Sorting on a numeric field that is indexed with both doc values and points may use an
 * optimization to skip non-competitive documents. This optimization relies on the assumption that
 * the same data is stored in these points and doc values.
 *
 *
 * Sorting on a SORTED(_SET) field that is indexed with both doc values and term index may use an
 * optimization to skip non-competitive documents. This optimization relies on the assumption that
 * the same data is stored in these term index and doc values.
 *
 *
 * Created: Feb 11, 2004 1:25:29 PM
 *
 * @since lucene 1.4
 * @see Sort
 */
class SortField {

    /** Specifies the type of the terms to be sorted, or special types such as CUSTOM  */
    enum class Type {

        /**
         * Sort by document score (relevance). Sort values are Float and higher values are at the front.
         */
        SCORE,

        /**
         * Sort by document number (index order). Sort values are Integer and lower values are at the
         * front.
         */
        DOC,

        /**
         * Sort using term values as Strings. Sort values are String and lower values are at the front.
         */
        STRING,

        /**
         * Sort using term values as encoded Integers. Sort values are Integer and lower values are at
         * the front. Fields must either be not indexed, or indexed with [IntPoint].
         */
        INT,

        /**
         * Sort using term values as encoded Floats. Sort values are Float and lower values are at the
         * front. Fields must either be not indexed, or indexed with [FloatPoint].
         */
        FLOAT,

        /**
         * Sort using term values as encoded Longs. Sort values are Long and lower values are at the
         * front. Fields must either be not indexed, or indexed with [LongPoint].
         */
        LONG,

        /**
         * Sort using term values as encoded Doubles. Sort values are Double and lower values are at the
         * front. Fields must either be not indexed, or indexed with [DoublePoint].
         */
        DOUBLE,

        /**
         * Sort using a custom Comparator. Sort values are any Comparable and sorting is done according
         * to natural order.
         */
        CUSTOM,

        /**
         * Sort using term values as Strings, but comparing by value (using String.compareTo) for all
         * comparisons. This is typically slower than [.STRING], which uses ordinals to do the
         * sorting.
         */
        STRING_VAL,

        /**
         * Force rewriting of SortField using [SortField.rewrite] before it can be
         * used for sorting
         */
        REWRITEABLE
    }

    /**
     * Returns the name of the field. Could return `null` if the sort is by SCORE or DOC.
     *
     * @return Name of field, possibly `null`.
     */
    var field: String? = null

    lateinit var type: Type // defaults to determining type dynamically

    /**
     * Returns whether the sort should be reversed.
     *
     * @return True if natural order should be reversed.
     */
    var reverse: Boolean = false // defaults to natural order

    // Used for CUSTOM sort
    private var comparatorSource: FieldComparatorSource? = null

    // Used for 'sortMissingFirst/Last'
    /**
     * Return the value to use for documents that don't have a value. A value of `null`
     * indicates that default should be used.
     */
    protected var missingValue: Any? = null

    /**
     * Enables/disables numeric sort optimization to use the indexed data.
     *
     *
     * Enabled by default. By default, sorting on a numeric field activates point sort optimization
     * that can efficiently skip over non-competitive hits. Sort optimization has a number of
     * requirements, one of which is that SortField.Type matches the Point type with which the field
     * was indexed (e.g. sort on IntPoint field should use Type.INT). Another requirement is
     * that the same data is indexed with points and doc values for the field.
     *
     *
     * By default, sorting on a SORTED(_SET) field activates sort optimization that can efficiently
     * skip over non-competitive hits. Sort optimization requires that the same data is indexed with
     * term index and doc values for the field.
     *
     * @param optimizeSortWithIndexedData providing `false` disables the optimization, in cases
     * where these requirements can't be met.
     */  // Remove in Lucene 10
    /**
     * Returns whether sort optimization should be optimized with indexed data
     *
     * @return whether sort optimization should be optimized with indexed data
     */ // Remove in Lucene 10
    // Indicates if sort should be optimized with indexed data. Set to true by default.
    @get:Deprecated("")
    @set:Deprecated(
        """should only be used for compatibility with 8.x indices that got created with
        inconsistent data across fields, or the wrong sort configuration in the index sort"""
    )
    @Deprecated("")
    var optimizeSortWithIndexedData: Boolean = true

    /**
     * Creates a sort by terms in the given field with the type of term values explicitly given.
     *
     * @param field Name of field to sort by. Can be `null` if `type` is SCORE
     * or DOC.
     * @param type Type of values in the terms.
     */
    constructor(field: String?, type: Type) {
        initFieldType(field, type)
    }

    /**
     * Creates a sort, possibly in reverse, by terms in the given field with the type of term values
     * explicitly given.
     *
     * @param field Name of field to sort by. Can be `null` if `type` is SCORE
     * or DOC.
     * @param type Type of values in the terms.
     * @param reverse True if natural order should be reversed.
     */
    constructor(field: String?, type: Type, reverse: Boolean) {
        initFieldType(field, type)
        this.reverse = reverse
    }

    /** A SortFieldProvider for field sorts  */
    class Provider
    /** Creates a new Provider  */
        : SortFieldProvider(NAME) {

        @Throws(IOException::class)
        override fun readSortField(`in`: DataInput): SortField {
            val sf = SortField(`in`.readString(), readType(`in`), `in`.readInt() == 1)
            if (`in`.readInt() == 1) {
                // missing object
                when (sf.type) {
                    Type.STRING -> {
                        val missingString: Int = `in`.readInt()
                        if (missingString == 1) {
                            sf.setMissingValue(STRING_FIRST)
                        } else {
                            sf.setMissingValue(STRING_LAST)
                        }
                    }

                    Type.INT -> sf.setMissingValue(`in`.readInt())
                    Type.LONG -> sf.setMissingValue(`in`.readLong())
                    Type.FLOAT -> sf.setMissingValue(NumericUtils.sortableIntToFloat(`in`.readInt()))
                    Type.DOUBLE -> sf.setMissingValue(NumericUtils.sortableLongToDouble(`in`.readLong()))
                    Type.CUSTOM, Type.DOC, Type.REWRITEABLE, Type.STRING_VAL, Type.SCORE -> throw IllegalArgumentException(
                        "Cannot deserialize sort of type " + sf.type
                    )

                    else -> throw IllegalArgumentException("Cannot deserialize sort of type " + sf.type)
                }
            }
            return sf
        }

        @Throws(IOException::class)
        override fun writeSortField(sf: SortField, out: DataOutput) {
            sf.serialize(out)
        }

        companion object {
            /** The name this Provider is registered under  */
            const val NAME: String = "SortField"

        }
    }

    @Throws(IOException::class)
    private fun serialize(out: DataOutput) {
        out.writeString(field.orEmpty())
        out.writeString(type.toString())
        out.writeInt(if (reverse) 1 else 0)
        if (missingValue == null) {
            out.writeInt(0)
        } else {
            out.writeInt(1)
            when (type) {
                Type.STRING -> if (missingValue === STRING_LAST) {
                    out.writeInt(0)
                } else if (missingValue === STRING_FIRST) {
                    out.writeInt(1)
                } else {
                    throw IllegalArgumentException(
                        "Cannot serialize missing value of $missingValue for type STRING"
                    )
                }

                Type.INT -> out.writeInt(missingValue as Int)
                Type.LONG -> out.writeLong(missingValue as Long)
                Type.FLOAT -> out.writeInt(NumericUtils.floatToSortableInt(missingValue as Float))
                Type.DOUBLE -> out.writeLong(NumericUtils.doubleToSortableLong(missingValue as Double))
                Type.CUSTOM, Type.DOC, Type.REWRITEABLE, Type.STRING_VAL, Type.SCORE -> throw IllegalArgumentException(
                    "Cannot serialize SortField of type $type"
                )

                else -> throw IllegalArgumentException("Cannot serialize SortField of type $type")
            }
        }
    }

    /** Set the value to use for documents that don't have a value.  */
    @JvmName("setMissingValueKt")
    fun setMissingValue(missingValue: Any) {
        if (type == Type.STRING || type == Type.STRING_VAL) {
            require(!(missingValue !== STRING_FIRST && missingValue !== STRING_LAST)) { "For STRING type, missing value must be either STRING_FIRST or STRING_LAST" }
        } else if (type == Type.INT) {
            require(!(missingValue != null && missingValue::class != Int::class)) {
                ("Missing values for Type.INT can only be of type java.lang.Integer, but got "
                        + missingValue::class)
            }
        } else if (type == Type.LONG) {
            require(!(missingValue != null && missingValue::class != Long::class)) {
                ("Missing values for Type.LONG can only be of type java.lang.Long, but got "
                        + missingValue::class)
            }
        } else if (type == Type.FLOAT) {
            require(!(missingValue != null && missingValue::class != Float::class)) {
                ("Missing values for Type.FLOAT can only be of type java.lang.Float, but got "
                        + missingValue::class)
            }
        } else if (type == Type.DOUBLE) {
            require(!(missingValue != null && missingValue::class != Double::class)) {
                ("Missing values for Type.DOUBLE can only be of type java.lang.Double, but got "
                        + missingValue::class)
            }
        } else {
            throw IllegalArgumentException("Missing value only works for numeric or STRING types")
        }
        this.missingValue = missingValue
    }

    /**
     * Creates a sort with a custom comparison function.
     *
     * @param field Name of field to sort by; cannot be `null`.
     * @param comparator Returns a comparator for sorting hits.
     */
    constructor(field: String?, comparator: FieldComparatorSource) {
        initFieldType(field, Type.CUSTOM)
        this.comparatorSource = comparator
    }

    /**
     * Creates a sort, possibly in reverse, with a custom comparison function.
     *
     * @param field Name of field to sort by; cannot be `null`.
     * @param comparator Returns a comparator for sorting hits.
     * @param reverse True if natural order should be reversed.
     */
    constructor(field: String?, comparator: FieldComparatorSource, reverse: Boolean) {
        initFieldType(field, Type.CUSTOM)
        this.reverse = reverse
        this.comparatorSource = comparator
    }

    // Sets field & type, and ensures field is not NULL unless
    // type is SCORE or DOC
    private fun initFieldType(field: String?, type: Type) {
        this.type = type
        if (field == null) {
            require(!(type != Type.SCORE && type != Type.DOC)) { "field can only be null when type is SCORE or DOC" }
        } else {
            this.field = field
        }
    }

    /**
     * Returns the type of contents in the field.
     *
     * @return One of the constants SCORE, DOC, STRING, INT or FLOAT.
     */
    /*fun getType(): Type {
        return type
    }*/

    /** Returns the [FieldComparatorSource] used for custom sorting  */
    fun getComparatorSource(): FieldComparatorSource? {
        return comparatorSource
    }

    override fun toString(): String {
        val buffer = StringBuilder()
        when (type) {
            Type.SCORE -> buffer.append("<score>")
            Type.DOC -> buffer.append("<doc>")
            Type.STRING -> buffer.append("<string" + ": \"").append(field).append("\">")
            Type.STRING_VAL -> buffer.append("<string_val" + ": \"").append(field).append("\">")
            Type.INT -> buffer.append("<int" + ": \"").append(field).append("\">")
            Type.LONG -> buffer.append("<long: \"").append(field).append("\">")
            Type.FLOAT -> buffer.append("<float" + ": \"").append(field).append("\">")
            Type.DOUBLE -> buffer.append("<double" + ": \"").append(field).append("\">")
            Type.CUSTOM -> buffer
                .append("<custom:\"")
                .append(field)
                .append("\": ")
                .append(comparatorSource)
                .append('>')

            Type.REWRITEABLE -> buffer.append("<rewriteable: \"").append(field).append("\">")
            else -> buffer.append("<: \"").append(field).append("\">")
        }

        if (reverse) buffer.append('!')
        if (missingValue != null) {
            buffer.append(" missingValue=")
            buffer.append(missingValue)
        }

        return buffer.toString()
    }

    /**
     * Returns true if `o` is equal to this. If a [FieldComparatorSource] was
     * provided, it must properly implement equals (unless a singleton is always used).
     */
    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is SortField) return false
        val other: SortField = o as SortField
        return (other.field == this.field
                && other.type == this.type && other.reverse == this.reverse && this.comparatorSource == other.comparatorSource
                && this.missingValue == other.missingValue)
    }

    /**
     * Returns a hash code for this [SortField] instance. If a [FieldComparatorSource] was
     * provided, it must properly implement hashCode (unless a singleton is always used).
     */
    override fun hashCode(): Int {
        return Objects.hash(field, type, reverse, comparatorSource, missingValue)
    }

    private var bytesComparator: Comparator<BytesRef> = naturalOrder()

    fun setBytesComparator(b: Comparator<BytesRef>) {
        bytesComparator = b
    }

    fun getBytesComparator(): Comparator<BytesRef> {
        return bytesComparator
    }

    /**
     * Returns the [FieldComparator] to use for sorting.
     *
     * @lucene.experimental
     * @param numHits number of top hits the queue will store
     * @param pruning controls how can the comparator to skip documents via [     ][LeafFieldComparator.competitiveIterator]
     * @return [FieldComparator] to use when sorting
     */
    fun getComparator(numHits: Int, pruning: Pruning): FieldComparator<*> {
        val fieldComparator: FieldComparator<*>
        when (type) {
            Type.SCORE -> fieldComparator = RelevanceComparator(numHits)
            Type.DOC -> fieldComparator = DocComparator(numHits, reverse, pruning)
            Type.INT -> fieldComparator =
                IntComparator(numHits, field!!, missingValue as Int?, reverse, pruning)

            Type.FLOAT -> fieldComparator =
                FloatComparator(numHits, field!!, missingValue as Float, reverse, pruning)

            Type.LONG -> fieldComparator = LongComparator(numHits, field!!, missingValue as Long, reverse, pruning)
            Type.DOUBLE -> fieldComparator =
                DoubleComparator(numHits, field!!, missingValue as Double, reverse, pruning)

            Type.CUSTOM -> {
                checkNotNull(comparatorSource)
                fieldComparator = comparatorSource!!.newComparator(field!!, numHits, pruning, reverse)
            }

            Type.STRING -> fieldComparator =
                TermOrdValComparator(numHits, field!!, missingValue === STRING_LAST, reverse, pruning)

            Type.STRING_VAL -> fieldComparator =
                TermValComparator(numHits, field!!, missingValue === STRING_LAST)

            Type.REWRITEABLE -> throw IllegalStateException(
                "SortField needs to be rewritten through Sort.rewrite(..) and SortField.rewrite(..)"
            )

            else -> throw IllegalStateException("Illegal sort type: $type")
        }
        if (this.optimizeSortWithIndexedData == false) {
            fieldComparator.disableSkipping()
        }
        return fieldComparator
    }

    /**
     * Rewrites this SortField, returning a new SortField if a change is made. Subclasses should
     * override this define their rewriting behavior when this SortField is of type [ ][Type.REWRITEABLE]
     *
     * @param searcher IndexSearcher to use during rewriting
     * @return New rewritten SortField, or `this` if nothing has changed.
     * @throws IOException Can be thrown by the rewriting
     * @lucene.experimental
     */
    @Throws(IOException::class)
    fun rewrite(searcher: IndexSearcher): SortField {
        return this
    }

    /** Whether the relevance score is needed to sort documents.  */
    fun needsScores(): Boolean {
        return type == Type.SCORE
    }

    /**
     * Returns an [IndexSorter] used for sorting index segments by this SortField.
     *
     *
     * If the SortField cannot be used for index sorting (for example, if it uses scores or other
     * query-dependent values) then this method should return `null`
     *
     *
     * SortFields that implement this method should also implement a companion [ ] to serialize and deserialize the sort in index segment headers
     *
     * @lucene.experimental
     */
    fun getIndexSorter(): IndexSorter? {
        // Early return if field is null
        if (field == null) {
            return null
        }

        val fieldName = field!! // Safe to use non-null assertion as we checked above

        when (type) {
            Type.STRING -> {
                // Handle potentially null missingValue
                val missVal = missingValue ?: STRING_LAST
                return StringSorter(
                    Provider.NAME,
                    missVal,
                    reverse,
                    object : IndexSorter.SortedDocValuesProvider {
                        @Throws(IOException::class)
                        override fun get(reader: LeafReader): SortedDocValues {
                            return DocValues.getSorted(reader, fieldName)
                        }
                    }
                )
            }

            Type.INT -> {
                val missVal = missingValue as? Int ?: 0
                return IntSorter(
                    Provider.NAME,
                    missVal,
                    reverse,
                    object : IndexSorter.NumericDocValuesProvider {
                        @Throws(IOException::class)
                        override fun get(reader: LeafReader): NumericDocValues {
                            return DocValues.getNumeric(reader, fieldName)
                        }
                    }
                )
            }

            Type.LONG -> {
                val missVal = missingValue as? Long ?: 0L
                return LongSorter(
                    Provider.NAME,
                    missVal,
                    reverse,
                    object : IndexSorter.NumericDocValuesProvider {
                        @Throws(IOException::class)
                        override fun get(reader: LeafReader): NumericDocValues {
                            return DocValues.getNumeric(reader, fieldName)
                        }
                    }
                )
            }

            Type.DOUBLE -> {
                val missVal = missingValue as? Double ?: 0.0
                return DoubleSorter(
                    Provider.NAME,
                    missVal,
                    reverse,
                    object : IndexSorter.NumericDocValuesProvider {
                        @Throws(IOException::class)
                        override fun get(reader: LeafReader): NumericDocValues {
                            return DocValues.getNumeric(reader, fieldName)
                        }
                    }
                )
            }

            Type.FLOAT -> {
                val missVal = missingValue as? Float ?: 0.0f
                return FloatSorter(
                    Provider.NAME,
                    missVal,
                    reverse,
                    object : IndexSorter.NumericDocValuesProvider {
                        @Throws(IOException::class)
                        override fun get(reader: LeafReader): NumericDocValues {
                            return DocValues.getNumeric(reader, fieldName)
                        }
                    }
                )
            }

            Type.CUSTOM, Type.DOC, Type.REWRITEABLE, Type.STRING_VAL, Type.SCORE -> return null
        }
    }

    @get:Deprecated("This is a duplicate method for {@code SortField#getOptimizeSortWithIndexedData}.")
    @set:Deprecated(
        """should only be used for compatibility with 8.x indices that got created with
        inconsistent data across fields, or the wrong sort configuration in the index sort. This is
        a duplicate method for {@code SortField#setOptimizeSortWithIndexedData}."""
    )
    var optimizeSortWithPoints: Boolean
        /**
         * Returns whether sort optimization should be optimized with points index
         *
         * @return whether sort optimization should be optimized with points index
         */
        get() = this.optimizeSortWithIndexedData
        /**
         * Enables/disables numeric sort optimization to use the Points index.
         *
         *
         * Enabled by default. By default, sorting on a numeric field activates point sort optimization
         * that can efficiently skip over non-competitive hits. Sort optimization has a number of
         * requirements, one of which is that SortField.Type matches the Point type with which the field
         * was indexed (e.g. sort on IntPoint field should use Type.INT). Another requirement is
         * that the same data is indexed with points and doc values for the field.
         *
         * @param optimizeSortWithPoints providing `false` disables the optimization, in cases where
         * these requirements can't be met.
         */
        set(optimizeSortWithPoints) {
            this.optimizeSortWithIndexedData = optimizeSortWithPoints
        }

    companion object {
        /** Represents sorting by document score (relevance).  */
        val FIELD_SCORE: SortField = SortField(null, Type.SCORE)

        /** Represents sorting by document number (index order).  */
        val FIELD_DOC: SortField = SortField(null, Type.DOC)

        @Throws(IOException::class)
        protected fun readType(`in`: DataInput): Type {
            val type: String = `in`.readString()
            try {
                return Type.valueOf(type)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Can't deserialize SortField - unknown type $type", e)
            }
        }

        /** Pass this to [.setMissingValue] to have missing string values sort first.  */
        val STRING_FIRST: Any = object : Any() {
            override fun toString(): String {
                return "SortField.STRING_FIRST"
            }
        }

        /** Pass this to [.setMissingValue] to have missing string values sort last.  */
        val STRING_LAST: Any = object : Any() {
            override fun toString(): String {
                return "SortField.STRING_LAST"
            }
        }

    }
}
