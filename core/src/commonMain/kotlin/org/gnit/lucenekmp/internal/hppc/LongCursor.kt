package org.gnit.lucenekmp.internal.hppc

/**
 * Forked from HPPC, holding int index and long value.
 *
 * @lucene.internal
 */
class LongCursor {
    /**
     * The current value's index in the container this cursor belongs to. The meaning of this index is
     * defined by the container (usually it will be an index in the underlying storage buffer).
     */
    var index: Int = 0

    /** The current value.  */
    var value: Long = 0L

    override fun toString(): String {
        return "[cursor, index: $index, value: $value]"
    }
}
