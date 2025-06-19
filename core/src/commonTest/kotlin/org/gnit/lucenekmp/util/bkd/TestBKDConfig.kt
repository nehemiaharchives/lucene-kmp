package org.gnit.lucenekmp.util.bkd

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.ArrayUtil
import kotlin.test.Test
import kotlin.test.assertTrue

class TestBKDConfig : LuceneTestCase() {

    @Test
    fun testInvalidNumDims() {
        val ex = expectThrows(IllegalArgumentException::class) {
            BKDConfig(0, 0, 8, BKDConfig.DEFAULT_MAX_POINTS_IN_LEAF_NODE)
        }
        assertTrue(ex!!.message!!.contains("numDims must be 1 .. " + BKDConfig.MAX_DIMS))
    }

    @Test
    fun testInvalidNumIndexedDims() {
        run {
            val ex = expectThrows(IllegalArgumentException::class) {
                BKDConfig(1, 0, 8, BKDConfig.DEFAULT_MAX_POINTS_IN_LEAF_NODE)
            }
            assertTrue(ex!!.message!!.contains("numIndexDims must be 1 .. " + BKDConfig.MAX_INDEX_DIMS))
        }
        run {
            val ex = expectThrows(IllegalArgumentException::class) {
                BKDConfig(1, 2, 8, BKDConfig.DEFAULT_MAX_POINTS_IN_LEAF_NODE)
            }
            assertTrue(ex!!.message!!.contains("numIndexDims cannot exceed numDims"))
        }
    }

    @Test
    fun testInvalidBytesPerDim() {
        val ex = expectThrows(IllegalArgumentException::class) {
            BKDConfig(1, 1, 0, BKDConfig.DEFAULT_MAX_POINTS_IN_LEAF_NODE)
        }
        assertTrue(ex!!.message!!.contains("bytesPerDim must be > 0"))
    }

    @Test
    fun testInvalidMaxPointsPerLeafNode() {
        run {
            val ex = expectThrows(IllegalArgumentException::class) {
                BKDConfig(1, 1, 8, -1)
            }
            assertTrue(ex!!.message!!.contains("maxPointsInLeafNode must be > 0"))
        }
        run {
            val ex = expectThrows(IllegalArgumentException::class) {
                BKDConfig(1, 1, 8, ArrayUtil.MAX_ARRAY_LENGTH + 1)
            }
            assertTrue(ex!!.message!!.contains("maxPointsInLeafNode must be <= ArrayUtil.MAX_ARRAY_LENGTH"))
        }
    }
}
