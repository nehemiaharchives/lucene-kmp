package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.FieldExistsQuery
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet

/**
 * This reader filters out documents that have a doc-values value in the given field and treats
 * these documents as soft-deleted. Hard deleted documents will also be filtered out in the live
 * docs of this reader.
 *
 * @see IndexWriterConfig.setSoftDeletesField
 * @see IndexWriter.softUpdateDocument
 * @see SoftDeletesRetentionMergePolicy
 */
class SoftDeletesDirectoryReaderWrapper @Throws(IOException::class) private constructor(
    `in`: DirectoryReader,
    private val wrapper: SoftDeletesSubReaderWrapper
) : FilterDirectoryReader(`in`, wrapper) {

    private val field: String = wrapper.field
    private val readerCacheHelperField: CacheHelper? =
        `in`.readerCacheHelper?.let { DelegatingCacheHelper(it) }

    constructor(`in`: DirectoryReader, field: String) : this(
        `in`, SoftDeletesSubReaderWrapper(emptyMap(), field)
    )

    @Throws(IOException::class)
    override fun doWrapDirectoryReader(`in`: DirectoryReader): DirectoryReader {
        val readerCache: MutableMap<CacheKey, LeafReader> = HashMap()
        for (reader in sequentialSubReaders) {
            // we try to reuse the live docs instances here if the reader cache key didn't change
            if (reader is SoftDeletesFilterLeafReader && reader.readerCacheHelper != null) {
                readerCache[(reader.reader as LeafReader).readerCacheHelper!!.key] = reader
            } else if (reader is SoftDeletesFilterCodecReader && reader.readerCacheHelper != null) {
                readerCache[(reader.reader as LeafReader).readerCacheHelper!!.key] = reader
            }
        }
        return SoftDeletesDirectoryReaderWrapper(`in`, SoftDeletesSubReaderWrapper(readerCache, field))
    }

    override val readerCacheHelper: CacheHelper?
        get() = readerCacheHelperField

    private class SoftDeletesSubReaderWrapper(
        oldReadersCache: Map<CacheKey, LeafReader>,
        val field: String
    ) : SubReaderWrapper() {
        private val mapping: Map<CacheKey, LeafReader> = oldReadersCache

        override fun wrap(readers: List<out LeafReader>): Array<LeafReader> {
            val wrapped = ArrayList<LeafReader>(readers.size)
            for (reader in readers) {
                val wrap = wrap(reader)
                requireNotNull(wrap)
                if (wrap.numDocs() != 0) {
                    wrapped.add(wrap)
                }
            }
            return wrapped.toTypedArray()
        }

        override fun wrap(reader: LeafReader): LeafReader {
            val readerCacheHelper = reader.readerCacheHelper
            if (readerCacheHelper != null && mapping.containsKey(readerCacheHelper.key)) {
                // if the reader cache helper didn't change and we have it in the cache don't bother
                // creating a new one
                return mapping[readerCacheHelper.key]!!
            }
            return wrap(reader, field)
        }
    }

    companion object {
        @Throws(IOException::class)
        fun wrap(reader: LeafReader, field: String): LeafReader {
            val iterator: DocIdSetIterator? = FieldExistsQuery.getDocValuesDocIdSetIterator(field, reader)
            if (iterator == null) {
                return reader
            }
            val liveDocs: Bits? = reader.liveDocs
            val bits: FixedBitSet = if (liveDocs != null) {
                FixedBitSet.copyOf(liveDocs)
            } else {
                FixedBitSet(reader.maxDoc()).apply { set(0, reader.maxDoc()) }
            }
            val numSoftDeletes = PendingSoftDeletes.applySoftDeletes(iterator, bits)
            if (numSoftDeletes == 0) {
                return reader
            }
            val numDeletes = reader.numDeletedDocs() + numSoftDeletes
            val numDocs = reader.maxDoc() - numDeletes
            assert(assertDocCounts(numDocs, numSoftDeletes, reader))
            return if (reader is CodecReader) {
                SoftDeletesFilterCodecReader(reader, bits, numDocs)
            } else {
                SoftDeletesFilterLeafReader(reader, bits, numDocs)
            }
        }

        private fun assertDocCounts(expectedNumDocs: Int, numSoftDeletes: Int, reader: LeafReader): Boolean {
            if (reader is SegmentReader) {
                val segmentReader: SegmentReader = reader
                val segmentInfo: SegmentCommitInfo = segmentReader.segmentInfo
                if (segmentReader.isNRT == false) {
                    val numDocs = segmentInfo.info.maxDoc() - segmentInfo.getSoftDelCount() - segmentInfo.delCount
                    assert(numDocs == expectedNumDocs) {
                        "numDocs: $numDocs expected: $expectedNumDocs maxDoc: ${segmentInfo.info.maxDoc()} getDelCount: ${segmentInfo.delCount} getSoftDelCount: ${segmentInfo.getSoftDelCount()} numSoftDeletes: $numSoftDeletes reader.numDeletedDocs(): ${reader.numDeletedDocs()}"
                    }
                }
                // in the NRT case we don't have accurate numbers for getDelCount and getSoftDelCount since
                // they might not be flushed to disk when this reader is opened. We don't necessarily flush deleted doc on
                // reopen but we do for docValues.
            }
            return true
        }
    }

    private class SoftDeletesFilterLeafReader(
        val reader: LeafReader,
        private val bits: FixedBitSet,
        private val numDocsVal: Int
    ) : FilterLeafReader(reader) {

        private val readerCacheHelperField: CacheHelper? =
            reader.readerCacheHelper?.let { DelegatingCacheHelper(it) }

        override fun numDocs(): Int {
            return numDocsVal
        }

        override val coreCacheHelper: CacheHelper
            get() = reader.coreCacheHelper

        override val readerCacheHelper: CacheHelper?
            get() = readerCacheHelperField
    }

    private class SoftDeletesFilterCodecReader(
        val reader: CodecReader,
        private val bits: FixedBitSet,
        private val numDocsVal: Int
    ) : FilterCodecReader(reader) {

        private val readerCacheHelperField: CacheHelper? =
            reader.readerCacheHelper?.let { DelegatingCacheHelper(it) }

        override fun numDocs(): Int {
            return numDocsVal
        }

        override val coreCacheHelper: CacheHelper
            get() = reader.coreCacheHelper

        override val readerCacheHelper: CacheHelper?
            get() = readerCacheHelperField
    }
}
