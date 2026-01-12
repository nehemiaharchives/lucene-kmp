package org.gnit.lucenekmp.tests.search

import okio.IOException
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.Collector
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.FilterCollector
import org.gnit.lucenekmp.search.LeafCollector
import org.gnit.lucenekmp.search.Weight


/**
 * A collector that asserts that it is used correctly.
 *
 * @lucene.internal
 */
class AssertingCollector private constructor(`in`: Collector) :
    FilterCollector(`in`) {
    private var weightSet = false
    private var maxDoc = -1
    private var previousLeafMaxDoc = 0

    // public visibility for drill-sideways testing, since drill-sideways can't directly use
    // AssertingIndexSearcher
    // TODO: this is a pretty hacky workaround. It would be nice to rethink drill-sideways (for
    // multiple reasons) and move this back to pkg-private at some point
    var hasFinishedCollectingPreviousLeaf: Boolean = true

    @Throws(IOException::class)
    override fun getLeafCollector(context: LeafReaderContext): LeafCollector {
        assert(weightSet) { "Set the weight first" }
        assert(context.docBase >= previousLeafMaxDoc)
        previousLeafMaxDoc = context.docBase + context.reader().maxDoc()

        assert(hasFinishedCollectingPreviousLeaf)
        val `in`: LeafCollector = super.getLeafCollector(context)
        hasFinishedCollectingPreviousLeaf = false
        val docBase: Int = context.docBase
        return object : AssertingLeafCollector(
            `in`,
            0,
            DocIdSetIterator.NO_MORE_DOCS
        ) {
            @Throws(IOException::class)
            override fun collect(doc: Int) {
                // check that documents are scored in order globally,
                // not only per segment
                assert(
                    docBase + doc >= maxDoc
                ) {
                    ("collection is not in order: current doc="
                            + (docBase + doc)
                            + " while "
                            + maxDoc
                            + " has already been collected")
                }

                super.collect(doc)
                maxDoc = docBase + doc
            }

            @Throws(IOException::class)
            override fun finish() {
                hasFinishedCollectingPreviousLeaf = true
                super.finish()
            }
        }
    }

    override var weight: Weight?
        get() = `in`.weight
        set(value) {
            assert(weightSet == false) { "Weight set twice" }
            weightSet = true
            checkNotNull(value)
            `in`.weight = value
        }

    companion object {
        /** Wrap the given collector in order to add assertions.  */
        fun wrap(`in`: Collector): AssertingCollector {
            if (`in` is AssertingCollector) {
                return `in`
            }
            return AssertingCollector(`in`)
        }
    }
}
