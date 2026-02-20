package org.gnit.lucenekmp.codecs.lucene90

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.tests.index.BaseNormsFormatTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test

/** Tests Lucene90NormsFormat */
class TestLucene90NormsFormat : BaseNormsFormatTestCase() {
    val defaultCodec: Codec = TestUtil.getDefaultCodec()

    override val codec: Codec
        get() = defaultCodec

    // tests inherited from BaseNormsFormatTestCase
    @Test
    @Throws(Exception::class)
    override fun testByteRange() = super.testByteRange()

    @Test
    @Throws(Exception::class)
    override fun testSparseByteRange() = super.testSparseByteRange()

    @Test
    @Throws(Exception::class)
    override fun testShortRange() = super.testShortRange()

    @Test
    @Throws(Exception::class)
    override fun testSparseShortRange() = super.testSparseShortRange()

    @Test
    @Throws(Exception::class)
    override fun testLongRange() = super.testLongRange()

    @Test
    @Throws(Exception::class)
    override fun testSparseLongRange() = super.testSparseLongRange()

    @Test
    @Throws(Exception::class)
    override fun testFullLongRange() = super.testFullLongRange()

    @Test
    @Throws(Exception::class)
    override fun testSparseFullLongRange() = super.testSparseFullLongRange()

    @Test
    @Throws(Exception::class)
    override fun testFewValues() = super.testFewValues()

    @Test
    @Throws(Exception::class)
    override fun testFewSparseValues() = super.testFewSparseValues()

    @Test
    @Throws(Exception::class)
    override fun testFewLargeValues() = super.testFewLargeValues()

    @Test
    @Throws(Exception::class)
    override fun testFewSparseLargeValues() = super.testFewSparseLargeValues()

    @Test
    @Throws(Exception::class)
    override fun testAllZeros() = super.testAllZeros()

    @Test
    @Throws(Exception::class)
    override fun testSparseAllZeros() = super.testSparseAllZeros()

    @Test
    @Throws(Exception::class)
    override fun testMostZeros() = super.testMostZeros()

    @Test
    @Throws(Exception::class)
    override fun testOutliers() = super.testOutliers()

    @Test
    @Throws(Exception::class)
    override fun testSparseOutliers() = super.testSparseOutliers()

    @Test
    @Throws(Exception::class)
    override fun testOutliers2() = super.testOutliers2()

    @Test
    @Throws(Exception::class)
    override fun testSparseOutliers2() = super.testSparseOutliers2()

    @Test
    @Throws(Exception::class)
    override fun testNCommon() = super.testNCommon()

    @Test
    @Throws(Exception::class)
    override fun testSparseNCommon() = super.testSparseNCommon()

    @Test
    @Throws(Exception::class)
    override fun testNCommonBig() = super.testNCommonBig()

    @Test
    @Throws(Exception::class)
    override fun testSparseNCommonBig() = super.testSparseNCommonBig()

    @Test
    @Throws(Exception::class)
    override fun testUndeadNorms() = super.testUndeadNorms()

    @Test
    @Throws(Exception::class)
    override fun testThreads() = super.testThreads()

    @Test
    override fun testIndependantIterators() = super.testIndependantIterators()

    @Test
    override fun testIndependantSparseIterators() = super.testIndependantSparseIterators()

}
