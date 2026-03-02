package org.gnit.lucenekmp.analysis.tokenattributes

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.test.Test

class TestSimpleAttributeImpl : LuceneTestCase() {

    // this checks using reflection API if the defaults are correct
    @Test
    fun testAttributes() {
        TestUtil.assertAttributeReflection(
            PositionIncrementAttributeImpl(),
            hashMapOf(PositionIncrementAttribute::class.simpleName + "#positionIncrement" to 1)
        )
        TestUtil.assertAttributeReflection(
            PositionLengthAttributeImpl(),
            hashMapOf(PositionLengthAttribute::class.simpleName + "#positionLength" to 1)
        )
        TestUtil.assertAttributeReflection(
            FlagsAttributeImpl(),
            hashMapOf(FlagsAttribute::class.simpleName + "#flags" to 0)
        )
        TestUtil.assertAttributeReflection(
            TypeAttributeImpl(),
            hashMapOf(TypeAttribute::class.simpleName + "#type" to TypeAttribute.DEFAULT_TYPE)
        )
        TestUtil.assertAttributeReflection(
            PayloadAttributeImpl(),
            hashMapOf(PayloadAttribute::class.simpleName + "#payload" to null)
        )
        TestUtil.assertAttributeReflection(
            KeywordAttributeImpl(),
            hashMapOf(KeywordAttribute::class.simpleName + "#keyword" to false)
        )
        TestUtil.assertAttributeReflection(
            OffsetAttributeImpl(),
            hashMapOf(
                OffsetAttribute::class.simpleName + "#startOffset" to 0,
                OffsetAttribute::class.simpleName + "#endOffset" to 0
            )
        )
    }
}
