package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.util.Bits

internal abstract class DocValuesLeafReader : LeafReader() {
    override val coreCacheHelper: CacheHelper
        get() {
            throw UnsupportedOperationException()
        }

    @Throws(IOException::class)
    override fun terms(field: String): Terms {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun getNormValues(field: String): NumericDocValues {
        throw UnsupportedOperationException()
    }

    override val liveDocs: Bits
        get() {
            throw UnsupportedOperationException()
        }

    @Throws(IOException::class)
    override fun getPointValues(field: String): PointValues {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun getFloatVectorValues(field: String): FloatVectorValues {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun getByteVectorValues(field: String): ByteVectorValues {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun searchNearestVectors(
        field: String,
        target: FloatArray,
        knnCollector: KnnCollector,
        acceptDocs: Bits
    ) {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun searchNearestVectors(
        field: String,
        target: ByteArray,
        knnCollector: KnnCollector,
        acceptDocs: Bits
    ) {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        throw UnsupportedOperationException()
    }

    override val metaData: LeafMetaData
        get() {
            throw UnsupportedOperationException()
        }

    @Throws(IOException::class)
    override fun termVectors(): TermVectors {
        throw UnsupportedOperationException()
    }

    override fun numDocs(): Int {
        throw UnsupportedOperationException()
    }

    override fun maxDoc(): Int {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun storedFields(): StoredFields {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun doClose() {
        throw UnsupportedOperationException()
    }

    override val readerCacheHelper: CacheHelper
        get() {
            throw UnsupportedOperationException()
        }

    @Throws(IOException::class)
    override fun getDocValuesSkipper(field: String): DocValuesSkipper {
        throw UnsupportedOperationException()
    }
}
