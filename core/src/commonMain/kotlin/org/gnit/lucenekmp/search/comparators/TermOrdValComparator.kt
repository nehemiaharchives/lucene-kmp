package org.gnit.lucenekmp.search.comparators

import okio.IOException
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.FieldComparator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.LeafFieldComparator
import org.gnit.lucenekmp.search.Pruning
import org.gnit.lucenekmp.search.Scorable
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.PriorityQueue
import kotlin.jvm.JvmName
import kotlin.math.max
import kotlin.math.min


/**
 * Sorts by field's natural Term sort order, using ordinals. This is functionally equivalent to
 * [org.apache.lucene.search.FieldComparator.TermValComparator], but it first resolves the
 * string to their relative ordinal positions (using the index returned by [ ][org.apache.lucene.index.LeafReader.getSortedDocValues]), and does most comparisons using
 * the ordinals. For medium to large results, this comparator will be much faster than [ ]. For very small result sets it may be
 * slower.
 */
open class TermOrdValComparator(
    numHits: Int,
    private val field: String,
    private val sortMissingLast: Boolean,
    private val reverse: Boolean,
    pruning: Pruning
) : FieldComparator<BytesRef>() {
    /* Ords for each slot.
      @lucene.internal */
    val ords: IntArray

    /* Values for each slot.
  @lucene.internal */
    val values: Array<BytesRef?>
    private val tempBRs: Array<BytesRefBuilder?>

    /* Which reader last copied a value into the slot. When
  we compare two slots, we just compare-by-ord if the
  readerGen is the same; else we must compare the
  values (slower).
  @lucene.internal */
    val readerGen: IntArray

    /* Gen of current reader we are on.
  @lucene.internal */
    var currentReaderGen: Int = -1

    /* Bottom value (same as values[bottomSlot] once
    bottomSlot is set).  Cached for faster compares.
   @lucene.internal */
    var bottomValue: BytesRef? = null

    /* Bottom slot, or -1 if queue isn't full yet */
    var bottomSlot: Int = -1

    /** Set by setTopValue.  */
    var topValue: BytesRef? = null

    /** -1 if missing values are sorted first, 1 if they are sorted last  */
    var missingSortCmp: Int = 0

    /** Whether this is the only comparator.  */
    private var singleSort = false

    /** Whether this comparator is allowed to skip documents.  */
    private var canSkipDocuments: Boolean

    /** Whether the collector is done with counting hits so that we can start skipping documents.  */
    private var hitsThresholdReached = false

    /**
     * Creates this, with control over how missing values are sorted. Pass sortMissingLast=true to put
     * missing values at the end.
     */
    init {
        canSkipDocuments = pruning !== Pruning.NONE
        ords = IntArray(numHits)
        values = kotlin.arrayOfNulls<BytesRef>(numHits)
        tempBRs = kotlin.arrayOfNulls<BytesRefBuilder>(numHits)
        readerGen = IntArray(numHits)
        missingSortCmp = if (sortMissingLast) {
            1
        } else {
            -1
        }
    }

    override fun disableSkipping() {
        canSkipDocuments = false
    }

    override fun setSingleSort() {
        singleSort = true
    }

    override fun compare(slot1: Int, slot2: Int): Int {
        if (readerGen[slot1] == readerGen[slot2]) {
            return ords[slot1] - ords[slot2]
        }

        val val1: BytesRef? = values[slot1]
        val val2: BytesRef? = values[slot2]
        if (val1 == null) {
            if (val2 == null) {
                return 0
            }
            return missingSortCmp
        } else if (val2 == null) {
            return -missingSortCmp
        }
        return val1.compareTo(val2)
    }

    /** Retrieves the SortedDocValues for the field in this segment  */
    @Throws(IOException::class)
    protected fun getSortedDocValues(context: LeafReaderContext, field: String): SortedDocValues {
        return DocValues.getSorted(context.reader(), field)
    }

    @Throws(IOException::class)
    override fun getLeafComparator(context: LeafReaderContext): LeafFieldComparator {
        currentReaderGen++
        return this.TermOrdValLeafComparator(context, getSortedDocValues(context, field))
    }

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("setTopValueKt")
    override fun setTopValue(value: BytesRef) {
        // null is fine: it means the last doc of the prior
        // search was missing this value
        topValue = value
        // System.out.println("setTopValue " + topValue);
    }

    override fun value(slot: Int): BytesRef {
        return values[slot]!!
    }

    override fun compareValues(val1: BytesRef, val2: BytesRef): Int {
        if (val1 == null) {
            if (val2 == null) {
                return 0
            }
            return missingSortCmp
        } else if (val2 == null) {
            return -missingSortCmp
        }
        return val1.compareTo(val2)
    }

    private inner class TermOrdValLeafComparator(context: LeafReaderContext, values: SortedDocValues) :
        LeafFieldComparator {
        /* Current reader's doc ord/values. */
        val termsIndex: SortedDocValues = values

        /* True if current bottom slot matches the current reader. */
        var bottomSameReader: Boolean = false

        /* Bottom ord (same as ords[bottomSlot] once bottomSlot is set).  Cached for faster compares. */
        var bottomOrd: Int = 0

        var topSameReader: Boolean = false
        var topOrd: Int = 0

        /** Which ordinal to use for a missing value.  */
        var missingOrd: Int = 0

        private var competitiveIterator: CompetitiveIterator? = null

        private var dense = false

        init {

            missingOrd = if (sortMissingLast) {
                Int.Companion.MAX_VALUE
            } else {
                -1
            }

            if (topValue != null) {
                // Recompute topOrd/SameReader
                val ord: Int = termsIndex.lookupTerm(topValue!!)
                if (ord >= 0) {
                    topSameReader = true
                    topOrd = ord
                } else {
                    topSameReader = false
                    topOrd = -ord - 2
                }
            } else {
                topOrd = missingOrd
                topSameReader = true
            }

            // System.out.println("  getLeafComparator topOrd=" + topOrd + " topSameReader=" +
            // topSameReader);
            if (bottomSlot != -1) {
                // Recompute bottomOrd/SameReader
                setBottom(bottomSlot)
            }

            val enableSkipping: Boolean
            if (!canSkipDocuments) {
                dense = false
                enableSkipping = false
            } else {
                val fieldInfo: FieldInfo? = context.reader().fieldInfos.fieldInfo(field)
                if (fieldInfo == null) {
                    check(termsIndex.valueCount == 0) { "Field [$field] cannot be found in field infos" }
                    dense = false
                    enableSkipping = true
                } else if (fieldInfo.indexOptions === IndexOptions.NONE) {
                    // No terms index
                    dense = false
                    enableSkipping = false
                } else {
                    val terms: Terms? = context.reader().terms(field)
                    dense = terms != null && terms.docCount == context.reader().maxDoc()
                    enableSkipping = if (dense || topValue != null) {
                        true
                    } else if (reverse == sortMissingLast) {
                        // Missing values are always competitive, we can never skip
                        false
                    } else {
                        true
                    }
                }
            }
            competitiveIterator = if (enableSkipping) {
                CompetitiveIterator(context, field, dense, values.termsEnum()!!)
            } else {
                null
            }
            updateCompetitiveIterator()
        }

        @Throws(IOException::class)
        fun getOrdForDoc(doc: Int): Int {
            return if (termsIndex.advanceExact(doc)) {
                termsIndex.ordValue()
            } else {
                -1
            }
        }

        @Throws(IOException::class)
        override fun setHitsThresholdReached() {
            hitsThresholdReached = true
            updateCompetitiveIterator()
        }

        @Throws(IOException::class)
        override fun compareBottom(doc: Int): Int {
            require(bottomSlot != -1)
            var docOrd = getOrdForDoc(doc)
            if (docOrd == -1) {
                docOrd = missingOrd
            }
            return if (bottomSameReader) {
                // ord is precisely comparable, even in the equal case
                bottomOrd - docOrd
            } else if (bottomOrd >= docOrd) {
                // the equals case always means bottom is > doc
                // (because we set bottomOrd to the lower bound in
                // setBottom):
                1
            } else {
                -1
            }
        }

        @Throws(IOException::class)
        override fun copy(slot: Int, doc: Int) {
            var ord = getOrdForDoc(doc)
            if (ord == -1) {
                ord = missingOrd
                values[slot] = null
            } else {
                require(ord >= 0)
                if (tempBRs[slot] == null) {
                    tempBRs[slot] = BytesRefBuilder()
                }
                tempBRs[slot]!!.copyBytes(termsIndex.lookupOrd(ord)!!)
                values[slot] = tempBRs[slot]!!.get()
            }
            ords[slot] = ord
            readerGen[slot] = currentReaderGen
        }

        @Throws(IOException::class)
        override fun setBottom(bottom: Int) {
            bottomSlot = bottom

            bottomValue = values[bottomSlot]
            if (currentReaderGen == readerGen[bottomSlot]) {
                bottomOrd = ords[bottomSlot]
                bottomSameReader = true
            } else {
                if (bottomValue == null) {
                    // missingOrd is null for all segments
                    require(ords[bottomSlot] == missingOrd)
                    bottomOrd = missingOrd
                    bottomSameReader = true
                    readerGen[bottomSlot] = currentReaderGen
                } else {
                    val ord: Int = termsIndex.lookupTerm(bottomValue!!)
                    if (ord < 0) {
                        bottomOrd = -ord - 2
                        bottomSameReader = false
                    } else {
                        bottomOrd = ord
                        // exact value match
                        bottomSameReader = true
                        readerGen[bottomSlot] = currentReaderGen
                        ords[bottomSlot] = bottomOrd
                    }
                }
            }

            updateCompetitiveIterator()
        }

        @Throws(IOException::class)
        override fun compareTop(doc: Int): Int {
            var ord = getOrdForDoc(doc)
            if (ord == -1) {
                ord = missingOrd
            }

            return if (topSameReader) {
                // ord is precisely comparable, even in the equal case
                // System.out.println("compareTop doc=" + doc + " ord=" + ord + " ret=" + (topOrd-ord));
                topOrd - ord
            } else if (ord <= topOrd) {
                // the equals case always means doc is < value
                // (because we set topOrd to the lower bound)
                1
            } else {
                -1
            }
        }

        override fun setScorer(scorer: Scorable) {}

        @Throws(IOException::class)
        fun updateCompetitiveIterator() {
            if (competitiveIterator == null || !hitsThresholdReached || bottomSlot == -1) {
                return
            }
            // This logic to figure out min and max ords is quite complex and verbose, can it be made
            // simpler
            val minOrd: Int
            val maxOrd: Int
            if (!reverse) {
                minOrd = if (topValue != null) {
                    if (topSameReader) {
                        topOrd
                    } else {
                        // In the case when the top value doesn't exist in the segment, topOrd is set as the
                        // previous ord, and we are only interested in values that compare strictly greater than
                        // this.
                        topOrd + 1
                    }
                } else if (sortMissingLast || dense) {
                    0
                } else {
                    // Missing values are still competitive.
                    -1
                }

                maxOrd = if (bottomOrd == missingOrd) {
                    // The queue still contains missing values.
                    if (singleSort) {
                        // If there is no tie breaker, we can start ignoring missing values from now on.
                        termsIndex.valueCount - 1
                    } else {
                        Int.Companion.MAX_VALUE
                    }
                } else if (bottomSameReader) {
                    // If there is no tie breaker, we can start ignoring values that compare equal to the
                    // current top value too.
                    if (singleSort) bottomOrd - 1 else bottomOrd
                } else {
                    bottomOrd
                }
            } else {
                minOrd = if (bottomOrd == missingOrd) {
                    // The queue still contains missing values.
                    if (singleSort) {
                        // If there is no tie breaker, we can start ignoring missing values from now on.
                        0
                    } else {
                        -1
                    }
                } else if (bottomSameReader) {
                    // If there is no tie breaker, we can start ignoring values that compare equal to the
                    // current top value too.
                    if (singleSort) bottomOrd + 1 else bottomOrd
                } else {
                    bottomOrd + 1
                }

                maxOrd = if (topValue != null) {
                    topOrd
                } else if (!sortMissingLast || dense) {
                    termsIndex.valueCount - 1
                } else {
                    Int.Companion.MAX_VALUE
                }
            }

            if (minOrd == -1 || maxOrd == Int.Companion.MAX_VALUE) {
                // Missing values are still competitive, we can't skip yet.
                return
            }
            require(minOrd >= 0)
            require(maxOrd < termsIndex.valueCount)
            competitiveIterator!!.update(minOrd, maxOrd)
        }

        override fun competitiveIterator(): DocIdSetIterator {
            return competitiveIterator!!
        }
    }

    private class PostingsEnumAndOrd(val postings: PostingsEnum, val ord: Int)

    private inner class CompetitiveIterator(
        private val context: LeafReaderContext,
        private val field: String,
        private val dense: Boolean,
        private val docValuesTerms: TermsEnum
    ) : DocIdSetIterator() {

        private val MAX_TERMS = 1024

        private val maxDoc: Int = context.reader().maxDoc()
        private var doc = -1
        private var postings: ArrayDeque<PostingsEnumAndOrd>? = null
        private var docsWithField: DocIdSetIterator? = null
        private var disjunction: PriorityQueue<PostingsEnumAndOrd>? = null

        override fun docID(): Int {
            return doc
        }

        @Throws(IOException::class)
        override fun nextDoc(): Int {
            return advance(docID() + 1)
        }

        @Throws(IOException::class)
        override fun advance(target: Int): Int {
            if (target >= maxDoc) {
                return NO_MORE_DOCS.also { doc = it }
            } else if (disjunction == null) {
                if (docsWithField != null) {
                    // The field is sparse and we're only interested in documents that have a value.
                    require(!dense)
                    return docsWithField!!.advance(target).also { doc = it }
                } else {
                    // We haven't started skipping yet
                    return target.also { doc = it }
                }
            } else {
                var top: PostingsEnumAndOrd? = disjunction!!.top()
                if (top == null) {
                    // priority queue is empty, none of the remaining documents are competitive
                    return NO_MORE_DOCS.also { doc = it }
                }
                while (top!!.postings.docID() < target) {
                    top.postings.advance(target)
                    top = disjunction!!.updateTop()
                }
                return top.postings.docID().also { doc = it }
            }
        }

        @Throws(IOException::class)
        override fun intoBitSet(upTo: Int, bitSet: FixedBitSet, offset: Int) {
            var upTo = upTo
            if (upTo <= doc) {
                return
            }
            // Optimize the case when intersecting the competitive iterator is expensive, which is when it
            // hasn't nailed down a disjunction of competitive terms yet.
            if (disjunction == null) {
                if (docsWithField != null) {
                    docsWithField!!.intoBitSet(upTo, bitSet, offset)
                    doc = docsWithField!!.docID()
                } else {
                    upTo = min(upTo, maxDoc)
                    bitSet.set(doc - offset, upTo - offset)
                    doc = upTo
                }
            } else {
                super.intoBitSet(upTo, bitSet, offset)
            }
        }

        override fun cost(): Long {
            return context.reader().maxDoc().toLong()
        }

        /**
         * Update this iterator to only match postings whose term has an ordinal between `minOrd`
         * included and `maxOrd` included.
         */
        @Throws(IOException::class)
        fun update(minOrd: Int, maxOrd: Int) {
            val maxTerms: Int = min(MAX_TERMS, IndexSearcher.maxClauseCount)
            val size = max(0, maxOrd - minOrd + 1)
            if (size > maxTerms) {
                if (!dense && docsWithField == null) {
                    docsWithField = getSortedDocValues(context, field)
                }
            } else if (postings == null) {
                init(minOrd, maxOrd)
            } else if (size < postings!!.size) {
                // One or more ords got removed
                require(postings!!.isEmpty() || postings!!.first().ord <= minOrd)
                while (!postings!!.isEmpty() && postings!!.first().ord < minOrd) {
                    postings!!.removeFirst()
                }
                require(postings!!.isEmpty() || postings!!.last().ord >= maxOrd)
                while (!postings!!.isEmpty() && postings!!.last().ord > maxOrd) {
                    postings!!.removeLast()
                }
                disjunction!!.clear()
                disjunction!!.addAll(postings!!)
            }
        }

        /**
         * For the first time, this iterator is allowed to skip documents. It needs to pull [ ]s from the terms dictionary of the inverted index and create a priority queue
         * out of them.
         */
        @Throws(IOException::class)
        fun init(minOrd: Int, maxOrd: Int) {
            val size = max(0, maxOrd - minOrd + 1)
            postings = ArrayDeque(size)
            if (size > 0) {
                docValuesTerms.seekExact(minOrd.toLong())
                val minTerm: BytesRef = docValuesTerms.term()!!
                val terms: TermsEnum = context.reader().terms(field)!!.iterator()
                check(terms.seekExact(minTerm)) { "Term $minTerm exists in doc values but not in the terms index" }
                postings!!.add(PostingsEnumAndOrd(terms.postings(null, PostingsEnum.NONE.toInt()), minOrd))
                for (ord in minOrd + 1..maxOrd) {
                    val next: BytesRef? = terms.next()
                    checkNotNull(next) {
                        ("Terms have more than "
                                + ord
                                + " unique terms while doc values have exactly "
                                + ord
                                + " terms")
                    }
                    require(docValuesTerms.seekExact(next) && docValuesTerms.ord() == ord.toLong())
                    postings!!.add(PostingsEnumAndOrd(terms.postings(null, PostingsEnum.NONE.toInt()), ord))
                }
            }
            disjunction =
                object : PriorityQueue<PostingsEnumAndOrd>(size) {
                    override fun lessThan(a: PostingsEnumAndOrd, b: PostingsEnumAndOrd): Boolean {
                        return a.postings.docID() < b.postings.docID()
                    }
                }
            disjunction!!.addAll(postings!!)
        }
    }
}
