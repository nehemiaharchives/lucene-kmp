package org.gnit.lucenekmp.store

import kotlin.jvm.JvmRecord

/**
 * A FlushInfo provides information required for a FLUSH context. It is used as part of an [ ] in case of FLUSH context.
 *
 *
 * These values are only estimates and are not the actual values.
 */
data class FlushInfo(val numDocs: Int, val estimatedSegmentSize: Long)
