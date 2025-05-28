package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.util.*
import org.gnit.lucenekmp.util.packed.PackedInts
import org.gnit.lucenekmp.util.packed.PackedLongValues


/**
 * Maps per-segment ordinals to/from global ordinal space, using a compact packed-ints
 * representation.
 *
 *
 * **NOTE**: this is a costly operation, as it must merge sort all terms, and may require
 * non-trivial RAM once done. It's better to operate in segment-private ordinal space instead when
 * possible.
 *
 * @lucene.internal
 */
class OrdinalMap private constructor(
    owner: IndexReader.CacheKey?,
    subs: Array<TermsEnum>,
    segmentMap: SegmentMap,
    acceptableOverheadRatio: Float
) : Accountable {
    // TODO: we could also have a utility method to merge Terms[] and use size() as a weight when we
    // need it
    // TODO: use more efficient packed ints structures
    private class TermsEnumPriorityQueue(size: Int) : PriorityQueue<TermsEnumIndex>(size) {
        override fun lessThan(a: TermsEnumIndex, b: TermsEnumIndex): Boolean {
            return a.compareTermTo(b) < 0
        }
    }

    class SegmentMap(weights: LongArray) : Accountable {
        private val newToOld: IntArray
        private val oldToNew: IntArray

        init {
            newToOld = map(weights)
            oldToNew = inverse(newToOld)
            require(newToOld.contentEquals(inverse(oldToNew)))
        }

        fun newToOld(segment: Int): Int {
            return newToOld[segment]
        }

        fun oldToNew(segment: Int): Int {
            return oldToNew[segment]
        }

        override fun ramBytesUsed(): Long {
            return (BASE_RAM_BYTES_USED
                    + RamUsageEstimator.sizeOf(newToOld)
                    + RamUsageEstimator.sizeOf(oldToNew))
        }

        companion object {
            private val BASE_RAM_BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(SegmentMap::class)

            /** Build a map from an index into a sorted view of `weights` to an index into `weights`.  */
            private fun map(weights: LongArray): IntArray {
                val newToOld = IntArray(weights.size)
                for (i in weights.indices) {
                    newToOld[i] = i
                }
                object : InPlaceMergeSorter() {
                    protected override fun swap(i: Int, j: Int) {
                        val tmp = newToOld[i]
                        newToOld[i] = newToOld[j]
                        newToOld[j] = tmp
                    }

                    protected override fun compare(i: Int, j: Int): Int {
                        // j first since we actually want higher weights first
                        return Long.compare(weights[newToOld[j]], weights[newToOld[i]])
                    }
                }.sort(0, weights.size)
                return newToOld
            }

            /** Inverse the map.  */
            private fun inverse(map: IntArray): IntArray {
                val inverse = IntArray(map.size)
                for (i in map.indices) {
                    inverse[map[i]] = i
                }
                return inverse
            }
        }
    }

    /** Cache key of whoever asked for this awful thing  */
    val owner: IndexReader.CacheKey?

    /** Returns the total number of unique terms in global ord space.  */
    // number of global ordinals
    val valueCount: Long

    // globalOrd -> (globalOrd - segmentOrd) where segmentOrd is the ordinal in the first segment
    // that contains this term
    var globalOrdDeltas: LongValues

    // globalOrd -> first segment container
    var firstSegments: LongValues

    // for every segment, segmentOrd -> globalOrd
    val segmentToGlobalOrds: Array<LongValues?>

    // the map from/to segment ids
    val segmentMap: SegmentMap

    // ram usage
    val ramBytesUsed: Long

    /**
     * Here is how the OrdinalMap encodes the mapping from global ords to local segment ords. Assume
     * we have the following global mapping for a doc values field: <br></br>
     * bar -&gt; 0, cat -&gt; 1, dog -&gt; 2, foo -&gt; 3 <br></br>
     * And our index is split into 2 segments with the following local mappings for that same doc
     * values field: <br></br>
     * Segment 0: bar -&gt; 0, foo -&gt; 1 <br></br>
     * Segment 1: cat -&gt; 0, dog -&gt; 1 <br></br>
     * We will then encode delta between the local and global mapping in a packed 2d array keyed by
     * (segmentIndex, segmentOrd). So the following 2d array will be created by OrdinalMap: <br></br>
     * [[0, 2], [1, 1]]
     *
     *
     * The general algorithm for creating an OrdinalMap (skipping over some implementation details
     * and optimizations) is as follows:
     *
     *
     * [1] Create and populate a PQ with ([TermsEnum], index) tuples where index is the
     * position of the termEnum in an array of termEnum's sorted by descending size. The PQ itself
     * will be ordered by [TermsEnum.term]
     *
     *
     * [2] We will iterate through every term in the index now. In order to do so, we will start
     * with the first term at the top of the PQ . We keep track of a global ord, and track the
     * difference between the global ord and [TermsEnum.ord] in ordDeltas, which maps: <br></br>
     * (segmentIndex, [TermsEnum.ord]) -> globalTermOrdinal - [TermsEnum.ord] <br></br>
     * We then call [TermsEnum.next] then update the PQ to iterate (remember the PQ maintains
     * and order based on [TermsEnum.term] which changes on the next() calls). If the current
     * term exists in some other segment, the top of the queue will contain that segment. If not, the
     * top of the queue will contain a segment with the next term in the index and the global ord will
     * also be incremented.
     *
     *
     * [3] We use some information gathered in the previous step to perform optimizations on memory
     * usage and building time in the following steps, for more detail on those, look at the code.
     *
     *
     * [4] We will then populate segmentToGlobalOrds, which maps (segmentIndex, segmentOrd) -&gt;
     * globalOrd. Using the information we tracked in ordDeltas, we can construct this information
     * relatively easily.
     *
     * @param owner For caching purposes
     * @param subs A TermsEnum[], where each index corresponds to a segment
     * @param segmentMap Provides two maps, newToOld which lists segments in descending 'weight' order
     * (see [SegmentMap] for more details) and a oldToNew map which maps each original
     * segment index to their position in newToOld
     * @param acceptableOverheadRatio Acceptable overhead memory usage for some packed data structures
     * @throws IOException throws IOException
     */
    init {
        // create the ordinal mappings by pulling a termsenum over each sub's
        // unique terms, and walking a multitermsenum over those
        this.owner = owner
        this.segmentMap = segmentMap
        // even though we accept an overhead ratio, we keep these ones with COMPACT
        // since they are only used to resolve values given a global ord, which is
        // slow anyway
        val globalOrdDeltas: PackedLongValues.Builder =
            PackedLongValues.monotonicBuilder(PackedInts.COMPACT)
        val firstSegments: PackedLongValues.Builder = PackedLongValues.packedBuilder(PackedInts.COMPACT)
        var firstSegmentBits = 0L
        val ordDeltas = kotlin.arrayOfNulls<PackedLongValues.Builder>(subs.size)
        for (i in ordDeltas.indices) {
            ordDeltas[i] = PackedLongValues.monotonicBuilder(acceptableOverheadRatio)
        }
        val ordDeltaBits = LongArray(subs.size)
        val segmentOrds = LongArray(subs.size)

        // Just merge-sorts by term:
        val queue = TermsEnumPriorityQueue(subs.size)

        for (i in subs.indices) {
            val sub: TermsEnumIndex = TermsEnumIndex(subs[segmentMap.newToOld(i)], i)
            if (sub.next() != null) {
                queue.add(sub)
            }
        }

        val topState = TermsEnumIndex.TermState()

        var globalOrd: Long = 0
        while (queue.size() != 0) {
            var top: TermsEnumIndex = queue.top()
            topState.copyFrom(top)

            var firstSegmentIndex = Int.Companion.MAX_VALUE
            var globalOrdDelta = Long.Companion.MAX_VALUE

            // Advance past this term, recording the per-segment ord deltas:
            while (true) {
                val segmentOrd: Long = top.termsEnum!!.ord()
                val delta = globalOrd - segmentOrd
                val segmentIndex: Int = top.subIndex
                // We compute the least segment where the term occurs. In case the
                // first segment contains most (or better all) values, this will
                // help save significant memory
                if (segmentIndex < firstSegmentIndex) {
                    firstSegmentIndex = segmentIndex
                    globalOrdDelta = delta
                }
                ordDeltaBits[segmentIndex] = ordDeltaBits[segmentIndex] or delta

                // for each per-segment ord, map it back to the global term; the while loop is needed
                // in case the incoming TermsEnums don't have compact ordinals (some ordinal values
                // are skipped), which can happen e.g. with a FilteredTermsEnum:
                require(segmentOrds[segmentIndex] <= segmentOrd)

                // TODO: we could specialize this case (the while loop is not needed when the ords
                // are compact)
                do {
                    ordDeltas[segmentIndex]!!.add(delta)
                    segmentOrds[segmentIndex]++
                } while (segmentOrds[segmentIndex] <= segmentOrd)

                if (top.next() == null) {
                    queue.pop()
                    if (queue.size() == 0) {
                        break
                    }
                    top = queue.top()
                } else {
                    top = queue.updateTop()
                }
                if (top.termEquals(topState) == false) {
                    break
                }
            }

            // for each unique term, just mark the first segment index/delta where it occurs
            firstSegments.add(firstSegmentIndex.toLong())
            firstSegmentBits = firstSegmentBits or firstSegmentIndex.toLong()
            globalOrdDeltas.add(globalOrdDelta)
            globalOrd++
        }

        var ramBytesUsed = BASE_RAM_BYTES_USED + segmentMap.ramBytesUsed()
        this.valueCount = globalOrd

        // If the first segment contains all of the global ords, then we can apply a small optimization
        // and hardcode the first segment indices and global ord deltas as all zeroes.
        if (ordDeltaBits.size > 0 && ordDeltaBits[0] == 0L && firstSegmentBits == 0L) {
            this.firstSegments = LongValues.ZEROES
            this.globalOrdDeltas = LongValues.ZEROES
        } else {
            val packedFirstSegments: PackedLongValues = firstSegments.build()
            val packedGlobalOrdDeltas: PackedLongValues = globalOrdDeltas.build()
            this.firstSegments = packedFirstSegments
            this.globalOrdDeltas = packedGlobalOrdDeltas
            ramBytesUsed += packedFirstSegments.ramBytesUsed() + packedGlobalOrdDeltas.ramBytesUsed()
        }

        // ordDeltas is typically the bottleneck, so let's see what we can do to make it faster
        segmentToGlobalOrds = kotlin.arrayOfNulls<LongValues>(subs.size)
        ramBytesUsed += RamUsageEstimator.shallowSizeOf(segmentToGlobalOrds)
        for (i in ordDeltas.indices) {
            val deltas: PackedLongValues = ordDeltas[i]!!.build()
            if (ordDeltaBits[i] == 0L) {
                // segment ords perfectly match global ordinals
                // likely in case of low cardinalities and large segments
                segmentToGlobalOrds[i] = LongValues.IDENTITY
            } else {
                val bitsRequired =
                    if (ordDeltaBits[i] < 0) 64 else PackedInts.bitsRequired(ordDeltaBits[i])
                val monotonicBits: Long = deltas.ramBytesUsed() * 8
                val packedBits: Long = bitsRequired * deltas.size()
                if (deltas.size() <= Int.Companion.MAX_VALUE
                    && packedBits <= monotonicBits * (1 + acceptableOverheadRatio)
                ) {
                    // monotonic compression mostly adds overhead, let's keep the mapping in plain packed ints
                    val size = deltas.size() as Int
                    val newDeltas: PackedInts.Mutable =
                        PackedInts.getMutable(size, bitsRequired, acceptableOverheadRatio)
                    val it: PackedLongValues.Iterator = deltas.iterator()
                    for (ord in 0..<size) {
                        newDeltas.set(ord, it.next())
                    }
                    require(it.hasNext() == false)
                    segmentToGlobalOrds[i] =
                        object : LongValues() {
                            override fun get(ord: Long): Long {
                                return ord + newDeltas.get(ord.toInt())
                            }
                        }
                    ramBytesUsed += newDeltas.ramBytesUsed()
                } else {
                    segmentToGlobalOrds[i] =
                        object : LongValues() {
                            override fun get(ord: Long): Long {
                                return ord + deltas.get(ord)
                            }
                        }
                    ramBytesUsed += deltas.ramBytesUsed()
                }
                ramBytesUsed += RamUsageEstimator.shallowSizeOf(segmentToGlobalOrds[i])
            }
        }

        this.ramBytesUsed = ramBytesUsed
    }

    /**
     * Given a segment number, return a [LongValues] instance that maps segment ordinals to
     * global ordinals.
     */
    fun getGlobalOrds(segmentIndex: Int): LongValues {
        return segmentToGlobalOrds[segmentMap.oldToNew(segmentIndex)]!!
    }

    /**
     * Given global ordinal, returns the ordinal of the first segment which contains this ordinal (the
     * corresponding to the segment return [.getFirstSegmentNumber]).
     */
    fun getFirstSegmentOrd(globalOrd: Long): Long {
        return globalOrd - globalOrdDeltas.get(globalOrd)
    }

    /** Given a global ordinal, returns the index of the first segment that contains this term.  */
    fun getFirstSegmentNumber(globalOrd: Long): Int {
        return segmentMap.newToOld(firstSegments.get(globalOrd) as Int)
    }

    override fun ramBytesUsed(): Long {
        return ramBytesUsed
    }

    override val childResources: MutableCollection<Accountable>
        get() {
            val resources: MutableList<Accountable> = mutableListOf<Accountable>()
            resources.add(Accountables.namedAccountable("segment map", segmentMap))
            // TODO: would be nice to return the ordinal and segment maps too, but it's not straightforward
            //  because of optimizations.
            return resources
        }

    companion object {
        /**
         * Create an ordinal map that uses the number of unique values of each [SortedDocValues]
         * instance as a weight.
         *
         * @see .build
         */
        @Throws(IOException::class)
        fun build(
            owner: IndexReader.CacheKey?, values: Array<SortedDocValues>, acceptableOverheadRatio: Float
        ): OrdinalMap {
            val subs = kotlin.arrayOfNulls<TermsEnum>(values.size)
            val weights = LongArray(values.size)
            for (i in values.indices) {
                subs[i] = values[i].termsEnum()!!
                weights[i] = values[i].valueCount.toLong()
            }
            return build(owner, subs as Array<TermsEnum>, weights, acceptableOverheadRatio)
        }

        /**
         * Create an ordinal map that uses the number of unique values of each [SortedSetDocValues]
         * instance as a weight.
         *
         * @see .build
         */
        @Throws(IOException::class)
        fun build(
            owner: IndexReader.CacheKey?, values: Array<SortedSetDocValues>, acceptableOverheadRatio: Float
        ): OrdinalMap {
            val subs = kotlin.arrayOfNulls<TermsEnum>(values.size)
            val weights = LongArray(values.size)
            for (i in values.indices) {
                subs[i] = values[i].termsEnum()
                weights[i] = values[i].valueCount
            }
            return build(owner, subs as Array<TermsEnum>, weights, acceptableOverheadRatio)
        }

        /**
         * Creates an ordinal map that allows mapping ords to/from a merged space from `subs`.
         *
         * @param owner a cache key
         * @param subs TermsEnums that support [TermsEnum.ord]. They need not be dense (e.g. can
         * be FilteredTermsEnums}.
         * @param weights a weight for each sub. This is ideally correlated with the number of unique
         * terms that each sub introduces compared to the other subs
         * @throws IOException if an I/O error occurred.
         */
        @Throws(IOException::class)
        fun build(
            owner: IndexReader.CacheKey?, subs: Array<TermsEnum>, weights: LongArray, acceptableOverheadRatio: Float
        ): OrdinalMap {
            require(subs.size == weights.size) { "subs and weights must have the same length" }

            // enums are not sorted, so let's sort to save memory
            val segmentMap = SegmentMap(weights)
            return OrdinalMap(owner, subs, segmentMap, acceptableOverheadRatio)
        }

        private val BASE_RAM_BYTES_USED: Long = RamUsageEstimator.shallowSizeOfInstance(OrdinalMap::class)
    }
}
