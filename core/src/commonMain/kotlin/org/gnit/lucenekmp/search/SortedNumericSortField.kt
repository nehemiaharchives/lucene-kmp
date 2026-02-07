package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.IndexSorter
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SortFieldProvider
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.comparators.DoubleComparator
import org.gnit.lucenekmp.search.comparators.FloatComparator
import org.gnit.lucenekmp.search.comparators.IntComparator
import org.gnit.lucenekmp.search.comparators.LongComparator
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.NumericUtils

/**
 * SortField for [SortedNumericDocValues].
 *
 *
 * A SortedNumericDocValues contains multiple values for a field, so sorting with this technique
 * "selects" a value as the representative sort value for the document.
 *
 *
 * By default, the minimum value in the list is selected as the sort value, but this can be
 * customized.
 *
 *
 * Like sorting by string, this also supports sorting missing values as first or last, via [ ][.setMissingValue].
 *
 * @see SortedNumericSelector
 */
class SortedNumericSortField(
    field: String,
    override var type: Type,
    reverse: Boolean = false,
    private val selector: SortedNumericSelector.Type = SortedNumericSelector.Type.MIN
) : SortField(field, Type.CUSTOM, reverse) {

    /**
     * Creates a sort, possibly in reverse, specifying how the sort value from the document's set is
     * selected.
     *
     * @param field Name of field to sort by. Must not be null.
     * @param type Type of values
     * @param reverse True if natural order should be reversed.
     * @param selector custom selector type for choosing the sort value from the set.
     */
    /**
     * Creates a sort, possibly in reverse, by the minimum value in the set for the document.
     *
     * @param field Name of field to sort by. Must not be null.
     * @param type Type of values
     * @param reverse True if natural order should be reversed.
     */

    /** A SortFieldProvider for this sort field  */
    class Provider
    /** Creates a new Provider  */
        : SortFieldProvider(NAME) {
        @Throws(IOException::class)
        override fun readSortField(`in`: DataInput): SortField {
            val sf =
                SortedNumericSortField(
                    `in`.readString(),
                    readType(`in`),
                    `in`.readInt() == 1,
                    readSelectorType(`in`)
                )
            if (`in`.readInt() == 1) {
                when (sf.type) {
                    Type.INT -> sf.missingValue = `in`.readInt()
                    Type.LONG -> sf.missingValue = `in`.readLong()
                    Type.FLOAT -> sf.missingValue = NumericUtils.sortableIntToFloat(`in`.readInt())
                    Type.DOUBLE -> sf.missingValue = NumericUtils.sortableLongToDouble(`in`.readLong())
                    Type.CUSTOM, Type.DOC, Type.REWRITEABLE, Type.STRING_VAL, Type.SCORE, Type.STRING -> throw AssertionError()
                    /*else -> throw AssertionError()*/
                }
            }
            return sf
        }

        @Throws(IOException::class)
        override fun writeSortField(
            sf: SortField,
            out: DataOutput
        ) {
            assert(sf is SortedNumericSortField)
            (sf as SortedNumericSortField).serialize(out)
        }

        companion object {
            /** The name this provider is registered under  */
            const val NAME: String = "SortedNumericSortField"
        }
    }

    @Throws(IOException::class)
    private fun serialize(out: DataOutput) {
        out.writeString(field!!)
        out.writeString(type.toString())
        out.writeInt(if (reverse) 1 else 0)
        out.writeInt(selector.ordinal)
        if (missingValue == null) {
            out.writeInt(0)
        } else {
            out.writeInt(1)
            // oh for switch expressions...
            when (type) {
                Type.INT -> out.writeInt(missingValue as Int)
                Type.LONG -> out.writeLong(missingValue as Long)
                Type.FLOAT -> out.writeInt(
                    NumericUtils.floatToSortableInt(
                        missingValue as Float
                    )
                )

                Type.DOUBLE -> out.writeLong(
                    NumericUtils.doubleToSortableLong(
                        missingValue as Double
                    )
                )

                Type.CUSTOM, Type.DOC, Type.REWRITEABLE, Type.STRING_VAL, Type.SCORE, Type.STRING -> throw AssertionError()
                /*else -> throw AssertionError()*/
            }
        }
    }

    val numericType: Type
        /** Returns the numeric type in use for this sort  */
        get() = type

    /** Returns the selector in use for this sort  */
    fun getSelector(): SortedNumericSelector.Type {
        return selector
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = super.hashCode()
        result = prime * result + selector.hashCode()
        result = prime * result + type.hashCode()
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (!super.equals(obj)) return false
        if (this::class != obj!!::class) return false
        val other = obj as SortedNumericSortField
        if (selector != other.selector) return false
        if (type != other.type) return false
        return true
    }

    override fun toString(): String {
        val buffer = StringBuilder()
        buffer.append("<sortednumeric" + ": \"").append(field).append("\">")
        if (reverse) buffer.append('!')
        if (missingValue != null) {
            buffer.append(" missingValue=")
            buffer.append(missingValue)
        }
        buffer.append(" selector=")
        buffer.append(selector)
        buffer.append(" type=")
        buffer.append(type)

        return buffer.toString()
    }

    /*override fun setMissingValue(missingValue: Any) {
        this.missingValue = missingValue
    }*/

    override fun getComparator(
        numHits: Int,
        pruning: Pruning
    ): FieldComparator<*> {
        val fieldComparator: FieldComparator<*>
        // we can use sort optimization with points if selector is MIN or MAX,
        // because we can still build successful iterator over points in this case.
        val isMinOrMax =
            selector == SortedNumericSelector.Type.MAX || selector == SortedNumericSelector.Type.MIN
        when (type) {
            Type.INT -> fieldComparator =
                object : IntComparator(
                    numHits,
                    field!!,
                    missingValue as Int?,
                    reverse,
                    if (isMinOrMax) pruning else Pruning.NONE
                ) {
                    @Throws(IOException::class)
                    override fun getLeafComparator(context: LeafReaderContext): LeafFieldComparator {
                        return object :
                            IntLeafComparator(
                                context
                            ) {
                            @Throws(IOException::class)
                            override fun getNumericDocValues(
                                context: LeafReaderContext, field: String
                            ): NumericDocValues {
                                return SortedNumericSelector.wrap(
                                    DocValues.getSortedNumeric(
                                        context.reader(),
                                        field
                                    ), selector, type
                                )
                            }
                        }
                    }
                }

            Type.FLOAT -> fieldComparator =
                object : FloatComparator(
                    numHits,
                    field!!,
                    missingValue as Float?,
                    reverse,
                    if (isMinOrMax) pruning else Pruning.NONE
                ) {
                    @Throws(IOException::class)
                    override fun getLeafComparator(context: LeafReaderContext): LeafFieldComparator {
                        return object :
                            FloatLeafComparator(
                                context
                            ) {
                            @Throws(IOException::class)
                            override fun getNumericDocValues(
                                context: LeafReaderContext, field: String
                            ): NumericDocValues {
                                return SortedNumericSelector.wrap(
                                    DocValues.getSortedNumeric(
                                        context.reader(),
                                        field
                                    ), selector, type
                                )
                            }
                        }
                    }
                }

            Type.LONG -> fieldComparator =
                object : LongComparator(
                    numHits,
                    field!!,
                    missingValue as Long?,
                    reverse,
                    if (isMinOrMax) pruning else Pruning.NONE
                ) {
                    @Throws(IOException::class)
                    override fun getLeafComparator(context: LeafReaderContext): LeafFieldComparator {
                        return object :
                            LongLeafComparator(
                                context
                            ) {
                            @Throws(IOException::class)
                            override fun getNumericDocValues(
                                context: LeafReaderContext, field: String
                            ): NumericDocValues {
                                return SortedNumericSelector.wrap(
                                    DocValues.getSortedNumeric(
                                        context.reader(),
                                        field
                                    ), selector, type
                                )
                            }
                        }
                    }
                }

            Type.DOUBLE -> fieldComparator =
                object : DoubleComparator(
                    numHits,
                    field!!,
                    missingValue as Double?,
                    reverse,
                    if (isMinOrMax) pruning else Pruning.NONE
                ) {
                    @Throws(IOException::class)
                    override fun getLeafComparator(context: LeafReaderContext): LeafFieldComparator {
                        return object :
                            DoubleLeafComparator(
                                context
                            ) {
                            @Throws(IOException::class)
                            override fun getNumericDocValues(
                                context: LeafReaderContext, field: String
                            ): NumericDocValues {
                                return SortedNumericSelector.wrap(
                                    DocValues.getSortedNumeric(
                                        context.reader(),
                                        field
                                    ), selector, type
                                )
                            }
                        }
                    }
                }

            Type.CUSTOM, Type.DOC, Type.REWRITEABLE, Type.STRING_VAL, Type.SCORE, Type.STRING -> throw AssertionError()
            /*else -> throw AssertionError()*/
        }
        if (optimizeSortWithIndexedData == false) {
            fieldComparator.disableSkipping()
        }
        return fieldComparator
    }

    @Throws(IOException::class)
    private fun getValue(reader: LeafReader): NumericDocValues {
        return SortedNumericSelector.wrap(
            DocValues.getSortedNumeric(reader, field!!), selector, type
        )
    }

    override val indexSorter: IndexSorter
        get() {
            when (type) {
                Type.INT -> return IndexSorter.IntSorter(
                    Provider.NAME,
                    missingValue as Int?,
                    reverse
                ) { reader: LeafReader ->
                    this.getValue(reader)
                }

                Type.LONG -> return IndexSorter.LongSorter(
                    Provider.NAME,
                    missingValue as Long?,
                    reverse
                ) { reader: LeafReader ->
                    this.getValue(reader)
                }

                Type.DOUBLE -> return IndexSorter.DoubleSorter(
                    Provider.NAME,
                    missingValue as Double?,
                    reverse
                ) { reader: LeafReader ->
                    this.getValue(reader)
                }

                Type.FLOAT -> return IndexSorter.FloatSorter(
                    Provider.NAME,
                    missingValue as Float?,
                    reverse
                ) { reader: LeafReader ->
                    this.getValue(reader)
                }

                Type.CUSTOM, Type.DOC, Type.REWRITEABLE, Type.STRING_VAL, Type.STRING, Type.SCORE -> throw AssertionError()
                /*else -> throw AssertionError()*/
            }
        }

    companion object {
        @Throws(IOException::class)
        private fun readSelectorType(`in`: DataInput): SortedNumericSelector.Type {
            val selectorType: Int = `in`.readInt()
            require(selectorType < SortedNumericSelector.Type.entries.toTypedArray().size) { "Can't deserialize SortedNumericSortField - unknown selector type $selectorType" }
            return SortedNumericSelector.Type.entries.toTypedArray()[selectorType]
        }
    }
}
