package org.gnit.lucenekmp.store

/**
 * A MergeInfo provides information required for a MERGE context. It is used as part of an [ ] in case of MERGE context.
 *
 *
 * These values are only estimates and are not the actual values.
 */
data class MergeInfo(
    val totalMaxDoc: Int,
    val estimatedMergeBytes: Long,
    val isExternal: Boolean,
    val mergeMaxNumSegments: Int
)
