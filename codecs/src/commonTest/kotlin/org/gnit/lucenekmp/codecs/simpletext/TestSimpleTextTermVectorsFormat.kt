package org.gnit.lucenekmp.codecs.simpletext

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.tests.index.BaseTermVectorsFormatTestCase
import kotlin.reflect.KClass
import kotlin.test.Test

class TestSimpleTextTermVectorsFormat : BaseTermVectorsFormatTestCase() {
    override val readPastLastPositionExceptionClass: KClass<out Throwable>
        get() = AssertionError::class

    override val codec: Codec
        get() = SimpleTextCodec()

    // tests inherited from BaseTermVectorsFormatTestCase

    @Test
    override fun testRareVectors() = super.testRareVectors()

    @Test
    override fun testHighFreqs() = super.testHighFreqs()

    @Test
    override fun testLotsOfFields() = super.testLotsOfFields()

    @Test
    override fun testMixedOptions() = super.testMixedOptions()

    @Test
    override fun testRandom() = super.testRandom()

    @Test
    override fun testMerge() = super.testMerge()

    @Test
    override fun testMergeWithDeletes() = super.testMergeWithDeletes()

    @Test
    override fun testMergeWithIndexSort() = super.testMergeWithIndexSort()

    @Test
    override fun testMergeWithIndexSortAndDeletes() = super.testMergeWithIndexSortAndDeletes()

    @Test
    override fun testClone() = super.testClone()

    @Test
    override fun testPostingsEnumFreqs() = super.testPostingsEnumFreqs()

    @Test
    override fun testPostingsEnumPositions() = super.testPostingsEnumPositions()

    @Test
    override fun testPostingsEnumOffsets() = super.testPostingsEnumOffsets()

    @Test
    override fun testPostingsEnumOffsetsWithoutPositions() = super.testPostingsEnumOffsetsWithoutPositions()

    @Test
    override fun testPostingsEnumPayloads() = super.testPostingsEnumPayloads()

    @Test
    override fun testPostingsEnumAll() = super.testPostingsEnumAll()

}
