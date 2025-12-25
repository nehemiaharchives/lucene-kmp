package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FdLibmTest {

    class LogTest {

        @Test
        fun testCompute(){
            assertEquals(Double.NEGATIVE_INFINITY, FdLibm.Log.compute(0.0))
            assertTrue(FdLibm.Log.compute(-1.0).isNaN())
            assertTrue(FdLibm.Log.compute(Double.NaN).isNaN())
            assertEquals(Double.POSITIVE_INFINITY, FdLibm.Log.compute(Double.POSITIVE_INFINITY))
            assertEquals(0.0, FdLibm.Log.compute(1.0))
            assertEquals(0.6931471805599453, FdLibm.Log.compute(2.0), 1e-7)
        }
    }
}
