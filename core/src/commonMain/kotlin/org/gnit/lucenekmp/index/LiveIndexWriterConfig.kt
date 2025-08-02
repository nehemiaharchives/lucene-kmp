package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.similarities.Similarity
import org.gnit.lucenekmp.util.InfoStream

class LiveIndexWriterConfig: IndexWriterConfig() {

    /** The comparator for sorting leaf readers.  */
    var leafSorter: Comparator<LeafReader>? = null


    /** [MergePolicy] for selecting merges.  */
    override var mergePolicy: MergePolicy? = null

    var checkPendingFlushOnUpdate: Boolean = true

    fun getInfoStream(): InfoStream {
        throw UnsupportedOperationException("Not implemented yet")
    }

    fun getIndexSort(): Sort {
        throw UnsupportedOperationException("Not implemented yet")
    }

    fun getSimilarity(): Similarity {
        throw UnsupportedOperationException("Not implemented yet")
    }

    fun getAnalyzer(): Analyzer {
        throw UnsupportedOperationException("Not implemented yet")
    }

    fun getSoftDeletesField(): String {
        throw UnsupportedOperationException("Not implemented yet")
    }

    fun getParentField(): String {
        throw UnsupportedOperationException("Not implemented yet")
    }

    fun getUseCompoundFile(): Boolean {
        throw UnsupportedOperationException("Not implemented yet")
    }

    fun getRAMBufferSizeMB(): Double {
        throw UnsupportedOperationException("Not implemented yet")
    }

    fun getMaxBufferedDocs(): Int {
        throw UnsupportedOperationException("Not implemented yet")
    }

    fun getFlushPolicy(): FlushPolicy {
        throw UnsupportedOperationException("Not implemented yet")
    }
}