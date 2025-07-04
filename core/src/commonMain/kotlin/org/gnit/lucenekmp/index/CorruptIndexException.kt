package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput

/** This exception is thrown when Lucene detects an inconsistency in the index.  */
class CorruptIndexException
/** Create exception with a message only  */ constructor(
    /** Returns the original exception message without the corrupted file description.  */
    originalMessage: String,
    /** Returns a description of the file that was corrupted  */
    val resourceDescription: String, cause: Throwable? = null
) : IOException(
    Objects.toString(
        originalMessage
    ) + " (resource=" + resourceDescription + ")", cause
) {

    val originalMessage: String = originalMessage

    /** Create exception with a message only  */
    constructor(message: String, input: DataInput) : this(message, input, null)

    /** Create exception with a message only  */
    constructor(message: String, output: DataOutput) : this(message, output, null)

    /** Create exception with message and root cause.  */
    constructor(message: String, input: DataInput, cause: Throwable?) : this(
        message,
        Objects.toString(input),
        cause
    )

    /** Create exception with message and root cause.  */
    constructor(message: String, output: DataOutput, cause: Throwable?) : this(
        message,
        Objects.toString(output),
        cause
    )

    /** Create exception with message and root cause.  */
}
