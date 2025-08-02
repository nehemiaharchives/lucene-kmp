package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.KnnFieldVectorsWriter
import org.gnit.lucenekmp.codecs.KnnVectorsFormat
import org.gnit.lucenekmp.codecs.KnnVectorsWriter
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.InfoStream

/**
 * Streams vector values for indexing to the given codec's vectors writer. The codec's vectors
 * writer is responsible for buffering and processing vectors.
 */
class VectorValuesConsumer(
    private val codec: Codec,
    private val directory: Directory,
    private val segmentInfo: SegmentInfo,
    private val infoStream: InfoStream
) {

    private var accountable: Accountable = Accountable.NULL_ACCOUNTABLE
    private var writer: KnnVectorsWriter? = null

    @Throws(IOException::class)
    private fun initKnnVectorsWriter(fieldName: String) {
        if (writer == null) {
            val fmt: KnnVectorsFormat = codec.knnVectorsFormat()
            checkNotNull(fmt) {
                ("field=\""
                        + fieldName
                        + "\" was indexed as vectors but codec does not support vectors")
            }
            val initialWriteState = SegmentWriteState(
                infoStream,
                directory,
                segmentInfo,
                null,
                null,
                IOContext.DEFAULT
            )
            writer = fmt.fieldsWriter(initialWriteState)
            accountable = writer!!
        }
    }

    @Throws(IOException::class)
    fun addField(fieldInfo: FieldInfo): KnnFieldVectorsWriter<*> {
        initKnnVectorsWriter(fieldInfo.name)
        return writer!!.addField(fieldInfo)
    }

    @Throws(IOException::class)
    fun flush(state: SegmentWriteState, sortMap: Sorter.DocMap) {
        if (writer == null) return
        try {
            writer!!.flush(state.segmentInfo.maxDoc(), sortMap)
            writer!!.finish()
        } finally {
            IOUtils.close(writer)
        }
    }

    fun abort() {
        IOUtils.closeWhileHandlingException(writer)
    }

    fun getAccountable(): Accountable {
        return accountable
    }
}
