package org.gnit.lucenekmp.index

import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.util.Version


/** This exception is thrown when Lucene detects an index that is too old for this Lucene version  */
class IndexFormatTooOldException : IOException {
    /** Returns a description of the file that was too old  */
    val resourceDescription: String

    /**
     * Returns an optional reason for this exception if the version information was not available.
     * Otherwise `null`
     */
    val reason: String?

    /**
     * Returns the version of the file that was too old. This method will return `null` if
     * an alternative [.getReason] is provided.
     */
    val version: Int?

    /**
     * Returns the minimum version accepted This method will return `null` if an
     * alternative [.getReason] is provided.
     */
    val minVersion: Int?

    /**
     * Returns the maximum version accepted. This method will return `null` if an
     * alternative [.getReason] is provided.
     */
    val maxVersion: Int?

    /**
     * Creates an `IndexFormatTooOldException`.
     *
     * @param resourceDescription describes the file that was too old
     * @param reason the reason for this exception if the version is not available
     * @lucene.internal
     */
    constructor(resourceDescription: String, reason: String) : super(
        ("Format version is not supported (resource "
                + resourceDescription
                + "): "
                + reason
                + ". This version of Lucene only supports indexes created with release "
                + Version.MIN_SUPPORTED_MAJOR
                + ".0 and later by default.")
    ) {
        this.resourceDescription = resourceDescription
        this.reason = reason
        this.version = null
        this.minVersion = null
        this.maxVersion = null
    }

    /**
     * Creates an `IndexFormatTooOldException`.
     *
     * @param in the open file that's too old
     * @param reason the reason for this exception if the version is not available
     * @lucene.internal
     */
    constructor(`in`: DataInput, reason: String) : this(Objects.toString(`in`), reason)

    /**
     * Creates an `IndexFormatTooOldException`.
     *
     * @param resourceDescription describes the file that was too old
     * @param version the version of the file that was too old
     * @param minVersion the minimum version accepted
     * @param maxVersion the maximum version accepted
     * @lucene.internal
     */
    constructor(resourceDescription: String, version: Int, minVersion: Int, maxVersion: Int) : super(
        ("Format version is not supported (resource "
                + resourceDescription
                + "): "
                + version
                + " (needs to be between "
                + minVersion
                + " and "
                + maxVersion
                + "). This version of Lucene only supports indexes created with release "
                + Version.MIN_SUPPORTED_MAJOR
                + ".0 and later.")
    ) {
        this.resourceDescription = resourceDescription
        this.version = version
        this.minVersion = minVersion
        this.maxVersion = maxVersion
        this.reason = null
    }

    /**
     * Creates an `IndexFormatTooOldException`.
     *
     * @param in the open file that's too old
     * @param version the version of the file that was too old
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
