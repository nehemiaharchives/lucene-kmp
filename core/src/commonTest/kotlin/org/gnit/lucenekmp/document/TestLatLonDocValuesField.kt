package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals

/** Simple tests for [LatLonDocValuesField] */
class TestLatLonDocValuesField : LuceneTestCase() {
    @Test
    fun testToString() {
        // looks crazy due to lossiness
        assertEquals(
            "LatLonDocValuesField <field:18.313693958334625,-65.22744401358068>",
            LatLonDocValuesField("field", 18.313694, -65.227444).toString()
        )

        // sort field
        assertEquals(
            "<distance:\"field\" latitude=18.0 longitude=19.0>",
            LatLonDocValuesField.newDistanceSort("field", 18.0, 19.0).toString()
        )
    }
}
