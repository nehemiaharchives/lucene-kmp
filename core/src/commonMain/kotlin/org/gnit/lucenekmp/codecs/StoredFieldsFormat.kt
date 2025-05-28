package org.gnit.lucenekmp.codecs


import okio.IOException
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.SegmentInfo
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext

/** Controls the format of stored fields  */
abstract class StoredFieldsFormat
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() {
    /** Returns a [StoredFieldsReader] to load stored fields.  */
    @Throws(IOException::class)
    abstract fun fieldsReader(
        directory: Directory, si: SegmentInfo, fn: FieldInfos, context: IOContext
    ): StoredFieldsReader

    /** Returns a [StoredFieldsWriter] to write stored fields.  */
    @Throws(IOException::class)
    abstract fun fieldsWriter(
        directory: Directory, si: SegmentInfo, context: IOContext
    ): StoredFieldsWriter
}
