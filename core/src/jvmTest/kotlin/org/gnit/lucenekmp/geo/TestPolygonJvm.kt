package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

class TestPolygonJvm : LuceneTestCase() {
    @Test
    fun testPolygonNullPolyLats() {
        val ctor = Polygon::class.java.getConstructor(DoubleArray::class.java, DoubleArray::class.java, Array<Polygon>::class.java)
        expectThrows(java.lang.reflect.InvocationTargetException::class) {
            ctor.newInstance(null, doubleArrayOf(-66.0, -65.0, -65.0, -66.0, -66.0), emptyArray<Polygon>())
        }
    }

    @Test
    fun testPolygonNullPolyLons() {
        val ctor = Polygon::class.java.getConstructor(DoubleArray::class.java, DoubleArray::class.java, Array<Polygon>::class.java)
        expectThrows(java.lang.reflect.InvocationTargetException::class) {
            ctor.newInstance(doubleArrayOf(18.0, 18.0, 19.0, 19.0, 18.0), null, emptyArray<Polygon>())
        }
    }
}
