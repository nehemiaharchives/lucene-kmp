package org.gnit.lucenekmp.tests.index

import org.gnit.lucenekmp.index.FilterLeafReader
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.Bits

/** Filters the incoming reader and makes all documents appear deleted. */
class AllDeletedFilterReader(`in`: LeafReader) : FilterLeafReader(`in`) {
    override val liveDocs: Bits = Bits.MatchNoBits(`in`.maxDoc())

    init {
        assert(maxDoc() == 0 || hasDeletions())
    }

    override fun numDocs(): Int {
        return 0
    }

    override val coreCacheHelper: CacheHelper?
        get() = `in`.coreCacheHelper

    override val readerCacheHelper: CacheHelper?
        get() = null
}
