package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.StoredFieldsWriter
import org.gnit.lucenekmp.document.StoredValue
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.IOUtils

internal open class StoredFieldsConsumer(
    val codec: Codec,
    val directory: Directory,
    val info: SegmentInfo
) {
    var writer: StoredFieldsWriter? = null

    // this accountable either holds the writer or one that returns null.
    // it's cleaner than checking if the writer is null all over the place
    var accountable: Accountable = Accountable.NULL_ACCOUNTABLE
    private var lastDoc: Int = -1

    @Throws(IOException::class)
    protected open fun initStoredFieldsWriter() {
        if (writer
            == null
        ) { // TODO can we allocate this in the ctor we call start document for every doc
            // anyway
            this.writer = codec.storedFieldsFormat().fieldsWriter(directory, info, IOContext.DEFAULT)
            accountable = writer!!
        }
    }

    @Throws(IOException::class)
    fun startDocument(docID: Int) {
        assert(lastDoc < docID)
        initStoredFieldsWriter()
        while (++lastDoc < docID) {
            writer!!.startDocument()
            writer!!.finishDocument()
        }
        writer!!.startDocument()
    }

    @Throws(IOException::class)
    fun writeField(info: FieldInfo, value: StoredValue) {
        when (value.type) {
            StoredValue.Type.INTEGER -> writer!!.writeField(info, value.getIntValue())
            StoredValue.Type.LONG -> writer!!.writeField(info, value.getLongValue())
            StoredValue.Type.FLOAT -> writer!!.writeField(info, value.getFloatValue())
            StoredValue.Type.DOUBLE -> writer!!.writeField(info, value.getDoubleValue())
            StoredValue.Type.BINARY -> writer!!.writeField(info, value.binaryValue)
            StoredValue.Type.DATA_INPUT -> writer!!.writeField(info, value.dataInputValue!!)
            StoredValue.Type.STRING -> writer!!.writeField(info, value.stringValue)
            else -> throw AssertionError()
        }
    }

    @Throws(IOException::class)
    fun finishDocument() {
        writer!!.finishDocument()
    }

    @Throws(IOException::class)
    fun finish(maxDoc: Int) {
        while (lastDoc < maxDoc - 1) {
            startDocument(lastDoc + 1)
            finishDocument()
        }
    }

    @Throws(IOException::class)
    open fun flush(state: SegmentWriteState, sortMap: Sorter.DocMap) {
        try {
            writer!!.finish(state.segmentInfo.maxDoc())
        } finally {
            IOUtils.close(writer)
        }
    }

    open fun abort() {
        IOUtils.closeWhileHandlingException(writer)
    }
}
