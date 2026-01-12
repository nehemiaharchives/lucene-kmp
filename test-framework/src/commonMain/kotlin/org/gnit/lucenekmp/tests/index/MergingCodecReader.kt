package org.gnit.lucenekmp.tests.index

import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.StoredFieldsReader
import org.gnit.lucenekmp.index.CodecReader
import org.gnit.lucenekmp.index.FilterCodecReader
import org.gnit.lucenekmp.util.CloseableThreadLocal

/**
 * [CodecReader] wrapper that performs all reads using the merging instance of the index
 * formats.
 */
class MergingCodecReader  // TODO: other formats too
/** Wrap the given instance.  */
    (`in`: CodecReader) : FilterCodecReader(`in`) {
        val fieldsReaderCTL: CloseableThreadLocal<StoredFieldsReader> =
        object :
            CloseableThreadLocal<StoredFieldsReader>() {
            override fun initialValue(): StoredFieldsReader {
                return `in`.fieldsReader!!.mergeInstance
            }
        }
        val normsReaderCTL: CloseableThreadLocal<NormsProducer> =
        object :
            CloseableThreadLocal<NormsProducer>() {
            override fun initialValue(): NormsProducer? {
                val norms: NormsProducer? = `in`.normsReader
                if (norms == null) {
                    return null
                } else {
                    return norms.mergeInstance
                }
            }
        }
    val docValuesReaderCTL: CloseableThreadLocal<DocValuesProducer> =
        object :
            CloseableThreadLocal<DocValuesProducer>() {
            override fun initialValue(): DocValuesProducer? {
                val docValues: DocValuesProducer? =
                    `in`.docValuesReader
                if (docValues == null) {
                    return null
                } else {
                    return docValues.mergeInstance
                }
            }
        }
    override val fieldsReader : StoredFieldsReader?
        get()  {
            return fieldsReaderCTL.get()
        }

    override val normsReader: NormsProducer?
        get() {
            return normsReaderCTL.get()
        }

    override val docValuesReader: DocValuesProducer?
        get() {
            return docValuesReaderCTL.get()
        }

    override val coreCacheHelper: CacheHelper?
        get() =// same content, we can delegate
            `in`.coreCacheHelper

    override val readerCacheHelper: CacheHelper?
        get() =// same content, we can delegate
            `in`.readerCacheHelper
}
