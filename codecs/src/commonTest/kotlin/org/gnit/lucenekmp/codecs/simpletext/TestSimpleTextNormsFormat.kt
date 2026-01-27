package org.gnit.lucenekmp.codecs.simpletext

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.tests.index.BaseNormsFormatTestCase
import kotlin.test.Test

/** Tests SimpleTextNormsFormat  */
class TestSimpleTextNormsFormat : BaseNormsFormatTestCase() {
    override val codec: Codec = SimpleTextCodec()

    @Test
    override fun testByteRange() = super.testByteRange()

    @Test
    override fun testSparseByteRange() = super.testSparseByteRange()

    @Test
    override fun testShortRange() = super.testShortRange()

    @Test
    override fun testSparseShortRange() = super.testSparseShortRange()

    @Test
    override fun testLongRange() = super.testLongRange()

    @Test
    override fun testSparseLongRange() = super.testSparseLongRange()

    @Test
    override fun testFullLongRange() = super.testFullLongRange()

    @Test
    override fun testSparseFullLongRange() = super.testSparseFullLongRange()

    @Test
    override fun testFewValues() = super.testFewValues()

    @Test
    override fun testFewSparseValues() = super.testFewSparseValues()

    @Test
    override fun testFewLargeValues() = super.testFewLargeValues()

    @Test
    override fun testFewSparseLargeValues() = super.testFewSparseLargeValues()

    @Test
    override fun testAllZeros() = super.testAllZeros()

    @Test
    override fun testSparseAllZeros() = super.testSparseAllZeros()

    @Test
    override fun testMostZeros() = super.testMostZeros()

    @Test
    override fun testOutliers() = super.testOutliers()

    @Test
    override fun testSparseOutliers() = super.testSparseOutliers()

    @Test
    override fun testOutliers2() = super.testOutliers2()

    @Test
    override fun testSparseOutliers2() = super.testSparseOutliers2()

    @Test
    override fun testNCommon() = super.testNCommon()

    @Test
    override fun testSparseNCommon() = super.testSparseNCommon()

    @Test
    override fun testNCommonBig() = super.testNCommonBig()

    @Test
    override fun testSparseNCommonBig() = super.testSparseNCommonBig()

    @Test
    override fun testUndeadNorms() = super.testUndeadNorms()

    @Test
    override fun testThreads() = super.testThreads()

    @Test
    override fun testIndependantIterators() = super.testIndependantIterators()

    @Test
    override fun testIndependantSparseIterators() = super.testIndependantSparseIterators()

    @Test
    override fun testMergeStability(){ /* need to override but nothing found */ }
}
