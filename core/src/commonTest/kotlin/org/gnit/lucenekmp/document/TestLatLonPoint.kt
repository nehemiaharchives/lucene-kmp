package org.gnit.lucenekmp.document

import kotlin.test.Test
import kotlin.test.assertEquals
import org.gnit.lucenekmp.tests.util.LuceneTestCase

/** Simple tests for [LatLonPoint]. */
class TestLatLonPoint : LuceneTestCase() {
    @Test
    fun testToString() {
        // looks crazy due to lossiness
        assertEquals(
            "LatLonPoint <field:18.313693958334625,-65.22744401358068>",
            LatLonPoint("field", 18.313694, -65.227444).toString()
        )

        // looks crazy due to lossiness
        assertEquals(
            "field:[18.000000016763806 TO 18.999999999068677],[-65.9999999217689 TO -65.00000006519258]",
            LatLonPoint.newBoxQuery("field", 18.0, 19.0, -66.0, -65.0).toString()
        )

        // distance query does not quantize inputs
        assertEquals(
            "field:18.0,19.0 +/- 25.0 meters",
            LatLonPoint.newDistanceQuery("field", 18.0, 19.0, 25.0).toString()
        )
    }
}
