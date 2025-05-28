package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.DocValuesProducer


/** Abstract base class implementing a [DocValuesProducer] that has no doc values.  */
abstract class EmptyDocValuesProducer
/** Sole constructor  */
protected constructor() : DocValuesProducer() {
    @Throws(IOException::class)
    override fun getNumeric(field: FieldInfo): NumericDocValues {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun getBinary(field: FieldInfo): BinaryDocValues {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun getSorted(field: FieldInfo): SortedDocValues {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun getSortedNumeric(field: FieldInfo): SortedNumericDocValues {
        throw UnsupportedOperationException()
    }

    @Throws(IOException::class)
    override fun getSortedSet(field: FieldInfo): SortedSetDocValues {
        throw UnsupportedOperationException()
    }

    override fun getSkipper(field: FieldInfo): DocValuesSkipper {
        throw UnsupportedOperationException()
    }

    override fun checkIntegrity() {
        throw UnsupportedOperationException()
    }

    /** Closes this doc values producer.  */
    override fun close() {
        throw UnsupportedOperationException()
    }
}
