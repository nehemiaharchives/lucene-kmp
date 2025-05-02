package org.gnit.lucenekmp.codecs

import kotlinx.io.IOException

import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.document.StoredValue
import org.gnit.lucenekmp.index.DocIDMerger
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.MergeState
import org.gnit.lucenekmp.index.StoredFieldDataInput
import org.gnit.lucenekmp.index.StoredFieldVisitor
import org.gnit.lucenekmp.util.Accountable
import org.gnit.lucenekmp.util.BytesRef

/**
 * Codec API for writing stored fields:
 *
 *
 *  1. For every document, [.startDocument] is called, informing the Codec that a new
 * document has started.
 *  1. `writeField` is called for each field in the document.
 *  1. After all documents have been written, [.finish] is called for
 * verification/sanity-checks.
 *  1. Finally the writer is closed ([.close])
 *
 *
 * @lucene.experimental
 */
abstract class StoredFieldsWriter
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : AutoCloseable, Accountable {
    /**
     * Called before writing the stored fields of the document. `writeField` will be called for
     * each stored field. Note that this is called even if the document has no stored fields.
     */
    @Throws(IOException::class)
    abstract fun startDocument()

    /** Called when a document and all its fields have been added.  */
    @Throws(IOException::class)
    open fun finishDocument() {
    }

    /** Writes a stored int value.  */
    @Throws(IOException::class)
    abstract fun writeField(info: FieldInfo?, value: Int)

    /** Writes a stored long value.  */
    @Throws(IOException::class)
    abstract fun writeField(info: FieldInfo?, value: Long)

    /** Writes a stored float value.  */
    @Throws(IOException::class)
    abstract fun writeField(info: FieldInfo?, value: Float)

    /** Writes a stored double value.  */
    @Throws(IOException::class)
    abstract fun writeField(info: FieldInfo?, value: Double)

    /** Writes a stored binary value from a [StoredFieldDataInput].  */
    @Throws(IOException::class)
    open fun writeField(info: FieldInfo?, value: StoredFieldDataInput) {
        val length: Int = value.length
        val bytes = ByteArray(length)
        value.getDataInput().readBytes(bytes, 0, length)
        writeField(info, BytesRef(bytes, 0, length))
    }

    /** Writes a stored binary value.  */
    @Throws(IOException::class)
    abstract fun writeField(info: FieldInfo?, value: BytesRef)

    /** Writes a stored String value.  */
    @Throws(IOException::class)
    abstract fun writeField(info: FieldInfo?, value: String)

    /**
     * Called before [.close], passing in the number of documents that were written. Note that
     * this is intentionally redundant (equivalent to the number of calls to [.startDocument],
     * but a Codec should check that this is the case to detect the JRE bug described in LUCENE-1282.
     */
    @Throws(IOException::class)
    abstract fun finish(numDocs: Int)

    private class StoredFieldsMergeSub(
        val visitor: MergeVisitor,
        docMap: MergeState.DocMap,
        val reader: StoredFieldsReader,
        private val maxDoc: Int
    ) : DocIDMerger.Sub(docMap) {
        var docID: Int = -1

        override fun nextDoc(): Int {
            docID++
            return if (docID == maxDoc) {
                NO_MORE_DOCS
            } else {
                docID
            }
        }
    }

    /**
     * Merges in the stored fields from the readers in `mergeState`. The default
     * implementation skips over deleted documents, and uses [.startDocument], `writeField`, and [.finish], returning the number of documents that were written.
     * Implementations can override this method for more sophisticated merging (bulk-byte copying,
     * etc).
     */
    @Throws(IOException::class)
    open fun merge(mergeState: MergeState): Int {
        val subs: MutableList<StoredFieldsMergeSub> = ArrayList()
        for (i in 0..<mergeState.storedFieldsReaders.size) {
            val storedFieldsReader: StoredFieldsReader = mergeState.storedFieldsReaders[i]!!
            storedFieldsReader.checkIntegrity()
            subs.add(
                StoredFieldsMergeSub(
                    this.MergeVisitor(mergeState, i),
                    mergeState.docMaps!![i],
                    storedFieldsReader,
                    mergeState.maxDocs[i]
                )
            )
        }

        val docIDMerger: DocIDMerger<StoredFieldsMergeSub> =
            DocIDMerger.of(subs, mergeState.needsIndexSort)

        var docCount = 0
        while (true) {
            val sub: StoredFieldsMergeSub? = docIDMerger.next()
            if (sub == null) {
                break
            }
            require(sub.mappedDocID == docCount)
            startDocument()
            sub.reader.document(sub.docID, sub.visitor)
            finishDocument()
            docCount++
        }
        finish(docCount)
        return docCount
    }

    /**
     * A visitor that adds every field it sees.
     *
     *
     * Use like this:
     *
     * <pre>
     * MergeVisitor visitor = new MergeVisitor(mergeState, readerIndex);
     * for (...) {
     * startDocument();
     * storedFieldsReader.document(docID, visitor);
     * finishDocument();
     * }
    </pre> *
     */
    protected inner class MergeVisitor(mergeState: MergeState, readerIndex: Int) : StoredFieldVisitor() {
        var storedValue: StoredValue? = null
        var currentField: FieldInfo? = null
        var remapper: FieldInfos? = null

        /** Create new merge visitor.  */
        init {
            // if field numbers are aligned, we can save hash lookups
            // on every field access. Otherwise, we need to lookup
            // fieldname each time, and remap to a new number.
            for (fi in mergeState.fieldInfos[readerIndex]!!) {
                val other: FieldInfo? = mergeState.mergeFieldInfos!!.fieldInfo(fi.number)
                if (other == null || other.name != fi.name) {
                    remapper = mergeState.mergeFieldInfos
                    break
                }
            }
        }

        @Throws(IOException::class)
        override fun binaryField(fieldInfo: FieldInfo, value: StoredFieldDataInput) {
            writeField(remap(fieldInfo), value)
        }

        @Throws(IOException::class)
        override fun binaryField(fieldInfo: FieldInfo, value: ByteArray) {
            // TODO: can we avoid new BR here
            writeField(remap(fieldInfo), BytesRef(value))
        }

        @Throws(IOException::class)
        override fun stringField(fieldInfo: FieldInfo, value: String) {
            writeField(
                remap(fieldInfo), requireNotNull(value) {"String value should not be null"}
            )
        }

        @Throws(IOException::class)
        override fun intField(fieldInfo: FieldInfo, value: Int) {
            writeField(remap(fieldInfo), value)
        }

        @Throws(IOException::class)
        override fun longField(fieldInfo: FieldInfo, value: Long) {
            writeField(remap(fieldInfo), value)
        }

        @Throws(IOException::class)
        override fun floatField(fieldInfo: FieldInfo, value: Float) {
            writeField(remap(fieldInfo), value)
        }

        @Throws(IOException::class)
        override fun doubleField(fieldInfo: FieldInfo, value: Double) {
            writeField(remap(fieldInfo), value)
        }

        @Throws(IOException::class)
        override fun needsField(fieldInfo: FieldInfo): Status {
            return Status.YES
        }

        private fun remap(field: FieldInfo): FieldInfo? {
            return if (remapper != null) {
                // field numbers are not aligned, we need to remap to the new field number
                remapper!!.fieldInfo(field.name)
            } else {
                field
            }
        }
    }

    abstract override fun close()
}
