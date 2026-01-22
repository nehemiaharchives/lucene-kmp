package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.IndexSorter
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.SortFieldProvider
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.comparators.TermOrdValComparator
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput

/**
 * SortField for [SortedSetDocValues].
 *
 *
 * A SortedSetDocValues contains multiple values for a field, so sorting with this technique
 * "selects" a value as the representative sort value for the document.
 *
 *
 * By default, the minimum value in the set is selected as the sort value, but this can be
 * customized. Selectors other than the default do have some limitations to ensure that all
 * selections happen in constant-time for performance.
 *
 *
 * Like sorting by string, this also supports sorting missing values as first or last, via [ ][.setMissingValue].
 *
 * @see SortedSetSelector
 */
class SortedSetSortField(
    field: String,
    reverse: Boolean,
    val selector: SortedSetSelector.Type = SortedSetSelector.Type.MIN
) : SortField(
    field,
    Type.CUSTOM,
    reverse
) {
    /*if (selector == null) {
        throw NullPointerException()
    }*/

    /**
     * Creates a sort, possibly in reverse, specifying how the sort value from the document's set is
     * selected.
     *
     * @param field Name of field to sort by. Must not be null.
     * @param reverse True if natural order should be reversed.
     * @param selector custom selector type for choosing the sort value from the set.
     *
     * NOTE: selectors other than [SortedSetSelector.Type.MIN] require optional codec
     * support.
     */

    /** A SortFieldProvider for this sort  */
    class Provider
    /** Creates a new Provider  */
        : SortFieldProvider(NAME) {
        @Throws(IOException::class)
        override fun readSortField(`in`: DataInput): SortField {
            val sf: SortField =
                SortedSetSortField(`in`.readString(), `in`.readInt() == 1, readSelectorType(`in`))
            val missingValue: Int = `in`.readInt()
            if (missingValue == 1) {
                sf.missingValue = STRING_FIRST
            } else if (missingValue == 2) {
                sf.missingValue = STRING_LAST
            }
            return sf
        }

        @Throws(IOException::class)
        override fun writeSortField(
            sf: SortField,
            out: DataOutput
        ) {
            assert(sf is SortedSetSortField)
            (sf as SortedSetSortField).serialize(out)
        }

        companion object {
            /** The name this provider is registered under  */
            const val NAME: String = "SortedSetSortField"
        }
    }

    @Throws(IOException::class)
    private fun serialize(out: DataOutput) {
        out.writeString(field!!)
        out.writeInt(if (reverse) 1 else 0)
        out.writeInt(selector.ordinal)
        if (missingValue === STRING_FIRST) {
            out.writeInt(1)
        } else if (missingValue === STRING_LAST) {
            out.writeInt(2)
        } else {
            out.writeInt(0)
        }
    }

    /** Returns the selector in use for this sort  */
    /*fun getSelector(): SortedSetSelector.Type {
        return selector
    }*/

    override fun hashCode(): Int {
        return 31 * super.hashCode() + selector.hashCode()
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (!super.equals(obj)) return false
        if (this::class != obj!!::class) return false
        val other = obj as SortedSetSortField
        if (selector != other.selector) return false
        return true
    }

    override fun toString(): String {
        val buffer = StringBuilder()
        buffer.append("<sortedset" + ": \"").append(field).append("\">")
        if (reverse) buffer.append('!')
        if (missingValue != null) {
            buffer.append(" missingValue=")
            buffer.append(missingValue)
        }
        buffer.append(" selector=")
        buffer.append(selector)

        return buffer.toString()
    }

    /**
     * Set how missing values (the empty set) are sorted.
     *
     *
     * Note that this must be [.STRING_FIRST] or [.STRING_LAST].
     */
    override var missingValue: Any? = null
        set(missingValue) {
            require(!(missingValue !== STRING_FIRST && missingValue !== STRING_LAST)) { "For SORTED_SET type, missing value must be either STRING_FIRST or STRING_LAST" }
            field = missingValue
        }

    override fun getComparator(
        numHits: Int,
        pruning: Pruning
    ): FieldComparator<*> {
        val finalPruning: Pruning =
            if (optimizeSortWithIndexedData) pruning else Pruning.NONE
        return object : TermOrdValComparator(
            numHits,
            field!!,
            missingValue === SortField.STRING_LAST,
            reverse,
            finalPruning
        ) {
            @Throws(IOException::class)
            override fun getSortedDocValues(
                context: LeafReaderContext,
                field: String
            ): SortedDocValues {
                return SortedSetSelector.wrap(
                    DocValues.getSortedSet(
                        context.reader(),
                        field
                    ), selector
                )
            }
        }
    }

    @Throws(IOException::class)
    private fun getValues(reader: LeafReader): SortedDocValues {
        return SortedSetSelector.wrap(
            DocValues.getSortedSet(
                reader,
                field!!
            ), selector
        )
    }

    override val indexSorter: IndexSorter
        get() = IndexSorter.StringSorter(
            Provider.NAME,
            missingValue!!,
            reverse
        ) { reader: LeafReader ->
            this.getValues(reader)
        }

    companion object {
        @Throws(IOException::class)
        private fun readSelectorType(`in`: DataInput): SortedSetSelector.Type {
            val type: Int = `in`.readInt()
            require(type < SortedSetSelector.Type.entries.toTypedArray().size) { "Cannot deserialize SortedSetSortField: unknown selector type $type" }
            return SortedSetSelector.Type.entries.toTypedArray()[type]
        }
    }
}
