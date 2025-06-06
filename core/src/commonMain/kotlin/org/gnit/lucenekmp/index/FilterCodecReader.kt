package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.KnnVectorsReader
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.PointsReader
import org.gnit.lucenekmp.codecs.StoredFieldsReader
import org.gnit.lucenekmp.codecs.TermVectorsReader
import org.gnit.lucenekmp.util.Bits

/**
 * A `FilterCodecReader` contains another CodecReader, which it uses as its basic source
 * of data, possibly transforming the data along the way or providing additional functionality.
 *
 *
 * **NOTE**: If this [FilterCodecReader] does not change the content the contained
 * reader, you could consider delegating calls to [.getCoreCacheHelper] and [ ][.getReaderCacheHelper].
 */
abstract class FilterCodecReader(
    /** The underlying CodecReader instance.  */
    protected val `in`: CodecReader
) : CodecReader() {

    override val fieldsReader: StoredFieldsReader
        get() = `in`.fieldsReader

    override val termVectorsReader: TermVectorsReader?
        get() = `in`.termVectorsReader

    override val normsReader: NormsProducer?
        get() = `in`.normsReader

    override val docValuesReader: DocValuesProducer?
        get() = `in`.docValuesReader

    override val postingsReader: FieldsProducer?
        get() = `in`.postingsReader

    override val liveDocs: Bits?
        get() = `in`.liveDocs

    override val fieldInfos: FieldInfos
        get() = `in`.fieldInfos

    override val pointsReader: PointsReader?
        get() = `in`.pointsReader

    override val vectorReader: KnnVectorsReader?
        get() = `in`.vectorReader

    override fun numDocs(): Int {
        return `in`.numDocs()
    }

    override fun maxDoc(): Int {
        return `in`.maxDoc()
    }

    override val metaData: LeafMetaData
        get() = `in`.metaData

    @Throws(IOException::class)
    override fun doClose() {
        `in`.doClose()
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        `in`.checkIntegrity()
    }

    val delegate: CodecReader
        /** Returns the wrapped [CodecReader].  */
        get() = `in`

    companion object {
        /**
         * Get the wrapped instance by `reader` as long as this reader is an instance of [ ].
         */
        fun unwrap(reader: CodecReader): CodecReader {
            var reader: CodecReader = reader
            while (reader is FilterCodecReader) {
                reader = reader.delegate
            }
            return reader
        }

        /** Returns a filtered codec reader with the given live docs and numDocs.  */
        fun wrapLiveDocs(
            reader: CodecReader,
            liveDocs: Bits,
            numDocs: Int
        ): FilterCodecReader {
            return object : FilterCodecReader(reader) {
                override val coreCacheHelper: CacheHelper
                    get() = reader.coreCacheHelper

                override val readerCacheHelper: CacheHelper?
                    get() = null // we are altering live docs

                override val liveDocs
                    get(): Bits {
                        return liveDocs
                    }

                override fun numDocs(): Int {
                    return numDocs
                }
            }
        }
    }
}
