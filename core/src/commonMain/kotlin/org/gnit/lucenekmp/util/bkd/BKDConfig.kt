package org.gnit.lucenekmp.util.bkd

import org.gnit.lucenekmp.util.ArrayUtil
import kotlin.jvm.JvmRecord


/**
 * Basic parameters for indexing points on the BKD tree.
 *
 * @param numDims How many dimensions we are storing at the leaf (data) node
 * @param numIndexDims How many dimensions we are indexing in the internal nodes
 * @param bytesPerDim How many bytes each value in each dimension takes.
 * @param maxPointsInLeafNode max points allowed on a Leaf block
 */
@JvmRecord
data class BKDConfig(val numDims: Int, val numIndexDims: Int, val bytesPerDim: Int, val maxPointsInLeafNode: Int) {
    /** numDims * bytesPerDim  */
    fun packedBytesLength(): Int {
        return numDims * bytesPerDim
    }

    /** numIndexDims * bytesPerDim  */
    fun packedIndexBytesLength(): Int {
        return numIndexDims * bytesPerDim
    }

    /** (numDims * bytesPerDim) + Integer.BYTES (packedBytesLength plus docID size)  */
    fun bytesPerDoc(): Int {
        return packedBytesLength() + Int.SIZE_BYTES
    }

    init {
        // Check inputs are on bounds
        require(!(numDims < 1 || numDims > MAX_DIMS)) { "numDims must be 1 .. " + MAX_DIMS + " (got: " + numDims + ")" }
        require(!(numIndexDims < 1 || numIndexDims > MAX_INDEX_DIMS)) { "numIndexDims must be 1 .. " + MAX_INDEX_DIMS + " (got: " + numIndexDims + ")" }
        require(numIndexDims <= numDims) { "numIndexDims cannot exceed numDims (" + numDims + ") (got: " + numIndexDims + ")" }
        require(bytesPerDim > 0) { "bytesPerDim must be > 0; got " + bytesPerDim }
        require(maxPointsInLeafNode > 0) { "maxPointsInLeafNode must be > 0; got " + maxPointsInLeafNode }
        require(!(maxPointsInLeafNode > ArrayUtil.MAX_ARRAY_LENGTH)) {
            ("maxPointsInLeafNode must be <= ArrayUtil.MAX_ARRAY_LENGTH (= "
                    + ArrayUtil.MAX_ARRAY_LENGTH
                    + "); got "
                    + maxPointsInLeafNode)
        }
    }

    companion object {
        /** Default maximum number of point in each leaf block  */
        const val DEFAULT_MAX_POINTS_IN_LEAF_NODE: Int = 512

        /** Maximum number of index dimensions (2 * max index dimensions)  */
        const val MAX_DIMS: Int = 16

        /** Maximum number of index dimensions  */
        const val MAX_INDEX_DIMS: Int = 8
    }
}
