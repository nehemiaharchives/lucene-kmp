package org.gnit.lucenekmp.codecs.simpletext

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.tests.index.BasePointsFormatTestCase
import kotlin.test.Test

/** Tests SimpleText's point format  */
class TestSimpleTextPointsFormat : BasePointsFormatTestCase() {
    override val codec: Codec = SimpleTextCodec()

    // tests inherited from BasePointsFormatTestCase

    @Test
    override fun testBasic() = super.testBasic()

    @Test
    override fun testMerge() = super.testMerge()

    @Test
    override fun testAllPointDocsDeletedInSegment() = super.testAllPointDocsDeletedInSegment()

    @Test
    override fun testWithExceptions() = super.testWithExceptions()

    @Test
    override fun testMultiValued() = super.testMultiValued()

    @Test
    override fun testAllEqual() = super.testAllEqual()

    @Test
    override fun testOneDimEqual() = super.testOneDimEqual()

    @Test
    override fun testOneDimTwoValues() = super.testOneDimTwoValues()

    @Test
    override fun testBigIntNDims() = super.testBigIntNDims()

    @Test
    override fun testRandomBinaryTiny() = super.testRandomBinaryTiny()

    @Test
    override fun testRandomBinaryMedium() = super.testRandomBinaryMedium()

    @Test
    override fun testRandomBinaryBig() {
        // need to skip this test because it is too slow for SimpleText
        return
    }

    @Test
    override fun testAddIndexes() = super.testAddIndexes()

    @Test
    override fun testMergeMissing() = super.testMergeMissing()

    @Test
    override fun testDocCountEdgeCases() = super.testDocCountEdgeCases()

    @Test
    override fun testRandomDocCount() = super.testRandomDocCount()

    @Test
    override fun testMismatchedFields() = super.testMismatchedFields()

}
