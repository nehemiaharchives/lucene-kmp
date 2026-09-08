/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.facet

import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.CollectorManager
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.util.BitDocIdSet
import org.gnit.lucenekmp.util.FixedBitSet
import okio.IOException

/**
 * Collects hits for subsequent faceting, using sampling if needed. Once you've run a search and
 * collect hits into this, instantiate one of the [Facets] subclasses to do the facet
 * counting. Note that this collector does not collect the scores of matching docs (i.e. [FacetsCollector.MatchingDocs.scores]) is `null`.
 *
 * If you require the original set of hits, you can call [getOriginalMatchingDocs].
 * Also, since the counts of the top-facets is based on the sampled set, you can amortize the counts
 * by calling [amortizeFacetCounts].
 */
open class RandomSamplingFacetsCollector : FacetsCollector {
    /**
   * Faster alternative for java.util.Random, inspired by
   * http://dmurphy747.wordpress.com/2011/03/23/xorshift-vs-random- performance-in-java/
   *
   * Has a period of 2^64-1
   */
    private class XORShift64Random(seed: Long) {
        private var x: Long

        /** Creates a xorshift random generator using the provided seed */
        init {
            x = if (seed == 0L) 0xdeadbeef.toInt().toLong() else seed
        }

        /** Get the next random long value */
        fun randomLong(): Long {
            x = x xor (x shl 21)
            x = x xor (x ushr 35)
            x = x xor (x shl 4)
            return x
        }

        /** Get the next random int, between 0 (inclusive) and n (exclusive) */
        fun nextInt(n: Int): Int {
            val res = (randomLong() % n).toInt()
            return if (res < 0) -res else res
        }
    }

    private val sampleSize: Int
    private val random: XORShift64Random

    private var samplingRate = 0.0
    private var sampledDocs: MutableList<MatchingDocs>?
    private var totalHits = NOT_CALCULATED
    private var leftoverBin = NOT_CALCULATED
    private var leftoverIndex = NOT_CALCULATED

    /**
   * Constructor with the given sample size and default seed.
   *
   * @see #RandomSamplingFacetsCollector(int, long)
   */
    constructor(sampleSize: Int) : this(sampleSize, 0)

    /**
   * Constructor with the given sample size and seed.
   *
   * @param sampleSize The preferred sample size. If the number of hits is greater than the size,
   *     sampling will be done using a sample ratio of sampling size / totalN. For example: 1000
   *     hits, sample size = 10 results in samplingRatio of 0.01. If the number of hits is lower, no
   *     sampling is done at all
   * @param seed The random seed. If `0` then a seed will be chosen for you.
   */
    constructor(sampleSize: Int, seed: Long) : super(false) {
        this.sampleSize = sampleSize
        this.random = XORShift64Random(seed)
        this.sampledDocs = null
    }

    /**
   * Returns the sampled list of the matching documents. Note that a [FacetsCollector.MatchingDocs] instance is returned per segment, even if no hits from that
   * segment are included in the sampled set.
   *
   * Note: One or more of the MatchingDocs might be empty (not containing any hits) as result of
   * sampling.
   *
   * Note: `MatchingDocs.totalHits` is copied from the original MatchingDocs, scores is set
   * to `null`
   */
    override fun getMatchingDocs(): MutableList<MatchingDocs> {
        val matchingDocs = super.getMatchingDocs()

        if (totalHits == NOT_CALCULATED) {
            totalHits = 0
            for (md in matchingDocs) {
                totalHits += md.totalHits
            }
        }

        if (totalHits <= sampleSize) {
            return matchingDocs
        }

        if (sampledDocs == null) {
            samplingRate = (1.0 * sampleSize) / totalHits
            sampledDocs = createSampledDocs(matchingDocs)
        }
        return sampledDocs!!
    }

    /** Returns the original matching documents. */
    fun getOriginalMatchingDocs(): MutableList<MatchingDocs> = super.getMatchingDocs()

    /** Create a sampled copy of the matching documents list. */
    private fun createSampledDocs(matchingDocsList: List<MatchingDocs>): MutableList<MatchingDocs> {
        val sampledDocsList = ArrayList<MatchingDocs>(matchingDocsList.size)
        for (docs in matchingDocsList) {
            sampledDocsList.add(createSample(docs))
        }
        return sampledDocsList
    }

    /** Create a sampled of the given hits. */
    private fun createSample(docs: MatchingDocs): MatchingDocs {
        val maxdoc = docs.context.reader().maxDoc()

        // TODO: we could try the WAH8DocIdSet here as well, as the results will be sparse
        val sampleDocs = FixedBitSet(maxdoc)

        val binSize = (1.0 / samplingRate).toInt()

        try {
            var counter = 0
            var limit: Int
            var randomIndex: Int
            if (leftoverBin != NOT_CALCULATED) {
                limit = leftoverBin
                // either NOT_CALCULATED, which means we already sampled from that bin,
                // or the next document to sample
                randomIndex = leftoverIndex
            } else {
                limit = binSize
                randomIndex = random.nextInt(binSize)
            }
            val it = docs.bits.iterator()!!
            var doc = it.nextDoc()
            while (doc != DocIdSetIterator.NO_MORE_DOCS) {
                if (counter == randomIndex) {
                    sampleDocs.set(doc)
                }
                counter++
                if (counter >= limit) {
                    counter = 0
                    limit = binSize
                    randomIndex = random.nextInt(binSize)
                }
                doc = it.nextDoc()
            }

            if (counter == 0) {
                // we either exhausted the bin and the iterator at the same time, or
                // this segment had no results. in the latter case we might want to
                // carry leftover to the next segment as is, but that complicates the
                // code and doesn't seem so important.
                leftoverBin = NOT_CALCULATED
                leftoverIndex = NOT_CALCULATED
            } else {
                leftoverBin = limit - counter
                if (randomIndex > counter) {
                    // the document to sample is in the next bin
                    leftoverIndex = randomIndex - counter
                } else if (randomIndex < counter) {
                    // we sampled a document from the bin, so just skip over remaining
                    // documents in the bin in the next segment.
                    leftoverIndex = NOT_CALCULATED
                }
            }

            return MatchingDocs(docs.context, BitDocIdSet(sampleDocs), docs.totalHits, null)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    /**
   * Note: if you use a counting [Facets] implementation, you can amortize the sampled counts
   * by calling this method. Uses the [FacetsConfig] and the [IndexSearcher] to
   * determine the upper bound for each facet value.
   */
    fun amortizeFacetCounts(res: FacetResult?, config: FacetsConfig, searcher: IndexSearcher): FacetResult? {
        if (res == null || totalHits <= sampleSize) {
            return res
        }

        val fixedLabelValues = arrayOfNulls<LabelAndValue>(res.labelValues.size)
        val reader = searcher.indexReader
        val dimConfig = config.getDimConfig(res.dim)

        // +2 to prepend dimension, append child label
        val childPath = Array(res.path.size + 2) { "" }
        childPath[0] = res.dim

        res.path.copyInto(childPath, 1) // reuse

        for (i in res.labelValues.indices) {
            childPath[res.path.size + 1] = res.labelValues[i].label
            val fullPath = FacetsConfig.pathToString(childPath, childPath.size)
            val max = reader.docFreq(Term(dimConfig.indexFieldName, fullPath))
            var correctedCount = (res.labelValues[i].value.toDouble() / samplingRate).toInt()
            correctedCount = minOf(max, correctedCount)
            fixedLabelValues[i] = LabelAndValue(res.labelValues[i].label, correctedCount)
        }

        // cap the total count on the total number of non-deleted documents in the reader
        var correctedTotalCount = res.value.toInt()
        if (correctedTotalCount > 0) {
            correctedTotalCount = minOf(reader.numDocs(), (res.value.toDouble() / samplingRate).toInt())
        }

        return FacetResult(res.dim, res.path, correctedTotalCount, fixedLabelValues.requireNoNulls(), res.childCount)
    }

    /** Returns the sampling rate that was used. */
    fun getSamplingRate(): Double = samplingRate

    companion object {
        private const val NOT_CALCULATED = -1

        /**
   * Creates a [CollectorManager] for concurrent random sampling through [RandomSamplingFacetsCollector]
   */
        fun createManager(sampleSize: Int, seed: Long):
            CollectorManager<RandomSamplingFacetsCollector, RandomSamplingFacetsCollector> {
            return object : CollectorManager<RandomSamplingFacetsCollector, RandomSamplingFacetsCollector> {
                override fun newCollector(): RandomSamplingFacetsCollector =
                    RandomSamplingFacetsCollector(sampleSize, seed)

                override fun reduce(collectors: MutableCollection<RandomSamplingFacetsCollector>): RandomSamplingFacetsCollector {
                    if (collectors.size == 0) {
                        return RandomSamplingFacetsCollector(sampleSize, seed)
                    }
                    if (collectors.size == 1) {
                        return collectors.iterator().next()
                    }
                    return ReducedRandomSamplingFacetsCollector(sampleSize, seed, collectors)
                }
            }
        }
    }

    private class ReducedRandomSamplingFacetsCollector(
        sampleSize: Int, seed: Long, facetsCollectors: MutableCollection<RandomSamplingFacetsCollector>
    ) : RandomSamplingFacetsCollector(sampleSize, seed) {
        init {
            this.getOriginalMatchingDocs().addAll(FacetsCollectorManager.reduceMatchingDocs(facetsCollectors))
        }
    }
}
