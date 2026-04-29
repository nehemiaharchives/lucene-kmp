package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import kotlin.test.Test
import kotlin.test.assertEquals

class TestTermsEnumIndex : LuceneTestCase() {

    @Test
    fun testPrefix8ToComparableUnsignedLong() {
        val b = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        assertEquals(0L, TermsEnumIndex.prefix8ToComparableUnsignedLong(BytesRef(b, 1, 0)))
        assertEquals(4L shl 56, TermsEnumIndex.prefix8ToComparableUnsignedLong(BytesRef(b, 3, 1)))
        assertEquals(
            (4L shl 56) or (5L shl 48),
            TermsEnumIndex.prefix8ToComparableUnsignedLong(BytesRef(b, 3, 2))
        )
        assertEquals(
            (4L shl 56) or (5L shl 48) or (6L shl 40),
            TermsEnumIndex.prefix8ToComparableUnsignedLong(BytesRef(b, 3, 3))
        )
        assertEquals(
            (4L shl 56) or (5L shl 48) or (6L shl 40) or (7L shl 32),
            TermsEnumIndex.prefix8ToComparableUnsignedLong(BytesRef(b, 3, 4))
        )
        assertEquals(
            (4L shl 56) or (5L shl 48) or (6L shl 40) or (7L shl 32) or (8L shl 24),
            TermsEnumIndex.prefix8ToComparableUnsignedLong(BytesRef(b, 3, 5))
        )
        assertEquals(
            (4L shl 56) or (5L shl 48) or (6L shl 40) or (7L shl 32) or (8L shl 24) or (9L shl 16),
            TermsEnumIndex.prefix8ToComparableUnsignedLong(BytesRef(b, 3, 6))
        )
        assertEquals(
            (4L shl 56) or (5L shl 48) or (6L shl 40) or (7L shl 32) or (8L shl 24) or (9L shl 16) or (10L shl 8),
            TermsEnumIndex.prefix8ToComparableUnsignedLong(BytesRef(b, 3, 7))
        )
        assertEquals(
            ((4L shl 56)
                    or (5L shl 48)
                    or (6L shl 40)
                    or (7L shl 32)
                    or (8L shl 24)
                    or (9L shl 16)
                    or (10L shl 8)
                    or 11L),
            TermsEnumIndex.prefix8ToComparableUnsignedLong(BytesRef(b, 3, 8))
        )
        assertEquals(
            ((4L shl 56)
                    or (5L shl 48)
                    or (6L shl 40)
                    or (7L shl 32)
                    or (8L shl 24)
                    or (9L shl 16)
                    or (10L shl 8)
                    or 11L),
            TermsEnumIndex.prefix8ToComparableUnsignedLong(BytesRef(b, 3, 9))
        )
    }
}
