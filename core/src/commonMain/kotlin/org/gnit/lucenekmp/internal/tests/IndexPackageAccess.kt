package org.gnit.lucenekmp.internal.tests

import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.Impacts
import org.gnit.lucenekmp.index.IndexReader

/**
 * Access to [org.apache.lucene.index] package internals exposed to the test framework.
 *
 * @lucene.internal
 */
interface IndexPackageAccess {
    fun newCacheKey(): IndexReader.CacheKey

    fun setIndexWriterMaxDocs(limit: Int)

    fun newFieldInfosBuilder(softDeletesFieldName: String, parentFieldName: String): FieldInfosBuilder

    fun checkImpacts(impacts: Impacts, max: Int)

    /** Public type exposing [FieldInfo] internal builders.  */
    interface FieldInfosBuilder {
        fun add(fi: FieldInfo): FieldInfosBuilder

        fun finish(): FieldInfos
    }
}
