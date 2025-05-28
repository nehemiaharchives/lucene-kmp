package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.store.DataInput


/** This exception is thrown when Lucene detects an index that is newer than this Lucene version.  */
class IndexFormatTooNewException
/**
 * Creates an `IndexFormatTooNewException`
 *
 * @param resourceDescription describes the file that was too new
 * @param version the version of the file that was too new
 * @param minVersion the minimum version accepted
 * @param maxVersion the maximum version accepted
 * @lucene.internal
 */(
    /** Returns a description of the file that was too new  */
    val resourceDescription: String,
    /** Returns the version of the file that was too new  */
    val version: Int,
    /** Returns the minimum version accepted  */
    val minVersion: Int,
    /** Returns the maximum version accepted  */
    val maxVersion: Int
) : IOException(
    ("Format version is not supported (resource "
            + resourceDescription
            + "): "
            + version
            + " (needs to be between "
            + minVersion
            + " and "
            + maxVersion
            + ")")
) {
    /**
     * Creates an `IndexFormatTooNewException`
     *
     * @param in the open file that's too new
     * @param version the version of the file that was too new
     * @param minVersion the minimum version accepted
     * @param maxVersion the maximum version accepted
     * @lucene.internal
     */
    constructor(
        `in`: DataInput,
        version: Int,
        minVersion: Int,
        maxVersion: Int
    ) : this(Objects.toString(`in`), version, minVersion, maxVersion)
}
