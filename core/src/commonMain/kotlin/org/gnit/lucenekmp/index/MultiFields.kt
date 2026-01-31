package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.util.MergedIterator

/**
 * Provides a single [Fields] term index view over an [IndexReader]. This is useful when
 * you're interacting with an [IndexReader] implementation that consists of sequential
 * sub-readers (eg [DirectoryReader] or [MultiReader]) and you must treat it as a [ ].
 *
 *
 * **NOTE**: for composite readers, you'll get better performance by gathering the sub readers
 * using [IndexReader.getContext] to get the atomic leaves and then operate per-LeafReader,
 * instead of using this class.
 *
 * @lucene.internal
 */
class MultiFields(private val subs: Array<Fields>, subSlices: Array<ReaderSlice>) : Fields() {
    private val subSlices: Array<ReaderSlice>
    private val terms: MutableMap<String, Terms> = mutableMapOf<String, Terms>()

    /** Sole constructor.  */
    init {
        this.subSlices = subSlices
    }

    override fun iterator(): MutableIterator<String> {
        val subIterators: Array<MutableIterator<String?>?> = kotlin.arrayOfNulls<MutableIterator<String?>>(subs.size)
        for (i in subs.indices) {
            subIterators[i] = subs[i].iterator()
        }
        val merged = MergedIterator(*(subIterators as Array<MutableIterator<String>>)) as MutableIterator<String>
        return asUnmodifiableIterator(merged)
    }

    @Throws(IOException::class)
    override fun terms(field: String?): Terms? {
        var result = terms[field]
        if (result != null) return result

        // Lazy init: first time this field is requested, we
        // create & add to terms:
        val subs2: MutableList<Terms> = mutableListOf<Terms>()
        val slices2: MutableList<ReaderSlice> = mutableListOf<ReaderSlice>()

        // Gather all sub-readers that share this field
        for (i in subs.indices) {
            val terms = subs[i].terms(field)
            if (terms != null) {
                subs2.add(terms)
                slices2.add(subSlices[i])
            }
        }
        if (subs2.isNotEmpty()) {
            result =
                MultiTerms(
                    subs2.toTypedArray(), slices2.toTypedArray()
                )
            terms.put(field!!, result)
        }

        return result
    }

    override fun size(): Int {
        return -1
    }
}
