package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.Tessellator
import org.gnit.lucenekmp.geo.XYCircle
import org.gnit.lucenekmp.geo.XYEncodingUtils.decode
import org.gnit.lucenekmp.geo.XYEncodingUtils.encode
import org.gnit.lucenekmp.geo.XYGeometry
import org.gnit.lucenekmp.geo.XYLine
import org.gnit.lucenekmp.geo.XYPoint
import org.gnit.lucenekmp.geo.XYPolygon
import org.gnit.lucenekmp.geo.XYRectangle
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.geo.ShapeTestUtil
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil

abstract class BaseXYShapeTestCase : BaseSpatialTestCase() {
    abstract override fun getShapeType(): Any

    override fun nextShape(): Any {
        return (getShapeType() as ShapeType).nextShape()
    }

    /** factory method to create a new bounding box query */
    override fun newRectQuery(
        field: String,
        queryRelation: QueryRelation,
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double
    ): Query {
        return XYShape.newBoxQuery(
            field,
            queryRelation,
            minX.toFloat(),
            maxX.toFloat(),
            minY.toFloat(),
            maxY.toFloat()
        )
    }

    /** factory method to create a new line query */
    override fun newLineQuery(field: String, queryRelation: QueryRelation, vararg lines: Any): Query {
        return XYShape.newLineQuery(field, queryRelation, *Array(lines.size) { i -> lines[i] as XYLine })
    }

    /** factory method to create a new polygon query */
    override fun newPolygonQuery(field: String, queryRelation: QueryRelation, vararg polygons: Any): Query {
        return XYShape.newPolygonQuery(field, queryRelation, *Array(polygons.size) { i -> polygons[i] as XYPolygon })
    }

    override fun newPointsQuery(field: String, queryRelation: QueryRelation, vararg points: Any): Query {
        return XYShape.newPointQuery(field, queryRelation, *Array(points.size) { i -> points[i] as FloatArray })
    }

    override fun newDistanceQuery(field: String, queryRelation: QueryRelation, circle: Any): Query {
        return XYShape.newDistanceQuery(field, queryRelation, circle as XYCircle)
    }

    override fun toPoint2D(vararg points: Any): Component2D {
        val p = Array(points.size) { i -> points[i] as FloatArray }
        val pointArray = Array(points.size) { XYPoint(0f, 0f) }
        for (i in points.indices) {
            pointArray[i] = XYPoint(p[i][0], p[i][1])
        }
        return XYGeometry.create(*pointArray)
    }

    override fun toLine2D(vararg lines: Any): Component2D {
        return XYGeometry.create(*Array(lines.size) { i -> lines[i] as XYLine })
    }

    override fun toPolygon2D(vararg polygons: Any): Component2D {
        return XYGeometry.create(*Array(polygons.size) { i -> polygons[i] as XYPolygon })
    }

    override fun toRectangle2D(minX: Double, maxX: Double, minY: Double, maxY: Double): Component2D {
        return XYGeometry.create(XYRectangle(minX.toFloat(), maxX.toFloat(), minY.toFloat(), maxY.toFloat()))
    }

    override fun toCircle2D(circle: Any): Component2D {
        return XYGeometry.create(circle as XYCircle)
    }

    override fun randomQueryBox(): XYRectangle {
        return ShapeTestUtil.nextBox(random())
    }

    override fun rectMinX(rect: Any): Double {
        return (rect as XYRectangle).minX.toDouble()
    }

    override fun rectMaxX(rect: Any): Double {
        return (rect as XYRectangle).maxX.toDouble()
    }

    override fun rectMinY(rect: Any): Double {
        return (rect as XYRectangle).minY.toDouble()
    }

    override fun rectMaxY(rect: Any): Double {
        return (rect as XYRectangle).maxY.toDouble()
    }

    override fun rectCrossesDateline(rect: Any): Boolean {
        return false
    }

    /** use [ShapeTestUtil.nextPolygon] to create a random line */
    override fun nextLine(): XYLine {
        return ShapeTestUtil.nextLine()
    }

    override fun nextPolygon(): XYPolygon {
        return ShapeTestUtil.nextPolygon()
    }

    override fun nextPoints(): Array<Any> {
        val random = random()
        val numPoints = TestUtil.nextInt(random, 1, 20)
        val points = Array(numPoints) { FloatArray(2) }
        for (i in 0..<numPoints) {
            points[i][0] = ShapeTestUtil.nextFloat(random)
            points[i][1] = ShapeTestUtil.nextFloat(random)
        }
        return Array(numPoints) { i -> points[i] as Any }
    }

    override fun nextCircle(): Any {
        return ShapeTestUtil.nextCircle()
    }

    override fun getEncoder(): Encoder {
        return object : Encoder() {
            override fun decodeX(encoded: Int): Double {
                return decode(encoded).toDouble()
            }

            override fun decodeY(encoded: Int): Double {
                return decode(encoded).toDouble()
            }

            override fun quantizeX(raw: Double): Double {
                return decode(encode(raw.toFloat())).toDouble()
            }

            override fun quantizeXCeil(raw: Double): Double {
                return decode(encode(raw.toFloat())).toDouble()
            }

            override fun quantizeY(raw: Double): Double {
                return decode(encode(raw.toFloat())).toDouble()
            }

            override fun quantizeYCeil(raw: Double): Double {
                return decode(encode(raw.toFloat())).toDouble()
            }
        }
    }

    /** internal shape type for testing different shape types */
    protected enum class ShapeType {
        POINT {
            override fun nextShape(): Any {
                return ShapeTestUtil.nextXYPoint()
            }
        },
        LINE {
            override fun nextShape(): Any {
                return ShapeTestUtil.nextLine()
            }
        },
        POLYGON {
            override fun nextShape(): Any {
                while (true) {
                    val p = ShapeTestUtil.nextPolygon()
                    try {
                        Tessellator.tessellate(p, true)
                        return p
                    } catch (_: IllegalArgumentException) {
                        // if we can't tessellate; then random polygon generator created a malformed shape
                    }
                }
            }
        },
        MIXED {
            override fun nextShape(): Any {
                return RandomPicks.randomFrom(random(), subList).nextShape()
            }
        };

        abstract fun nextShape(): Any

        companion object {
            private val subList: Array<ShapeType> = arrayOf(POINT, LINE, POLYGON)

            private fun random() = LuceneTestCase.random()
        }
    }
}
