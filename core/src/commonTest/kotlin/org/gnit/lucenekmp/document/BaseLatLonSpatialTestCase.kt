package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Circle
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.GeoEncodingUtils.decodeLatitude
import org.gnit.lucenekmp.geo.GeoEncodingUtils.decodeLongitude
import org.gnit.lucenekmp.geo.GeoEncodingUtils.encodeLatitude
import org.gnit.lucenekmp.geo.GeoEncodingUtils.encodeLatitudeCeil
import org.gnit.lucenekmp.geo.GeoEncodingUtils.encodeLongitude
import org.gnit.lucenekmp.geo.GeoEncodingUtils.encodeLongitudeCeil
import org.gnit.lucenekmp.geo.GeoUtils
import org.gnit.lucenekmp.geo.LatLonGeometry
import org.gnit.lucenekmp.geo.Line
import org.gnit.lucenekmp.geo.Point
import org.gnit.lucenekmp.geo.Polygon
import org.gnit.lucenekmp.geo.Rectangle
import org.gnit.lucenekmp.geo.Tessellator
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.tests.geo.GeoTestUtil
import org.gnit.lucenekmp.tests.geo.GeoTestUtil.nextLatitude
import org.gnit.lucenekmp.tests.geo.GeoTestUtil.nextLongitude
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.tests.util.LuceneTestCase

/** Base test case for testing geospatial indexing and search functionality * */
abstract class BaseLatLonSpatialTestCase : BaseSpatialTestCase() {

    abstract override fun getShapeType(): Any

    override fun nextShape(): Any {
        return (getShapeType() as ShapeType).nextShape()
    }

    override fun toLine2D(vararg lines: Any): Component2D {
        return LatLonGeometry.create(*Array(lines.size) { i -> lines[i] as Line })
    }

    override fun toPolygon2D(vararg polygons: Any): Component2D {
        return LatLonGeometry.create(*Array(polygons.size) { i -> polygons[i] as Polygon })
    }

    override fun toRectangle2D(minX: Double, maxX: Double, minY: Double, maxY: Double): Component2D {
        return LatLonGeometry.create(Rectangle(minY, maxY, minX, maxX))
    }

    override fun toPoint2D(vararg points: Any): Component2D {
        val p = Array(points.size) { i -> points[i] as DoubleArray }
        val pointArray = Array(points.size) { Point(0.0, 0.0) }
        for (i in points.indices) {
            pointArray[i] = Point(p[i][0], p[i][1])
        }
        return LatLonGeometry.create(*pointArray)
    }

    override fun toCircle2D(circle: Any): Component2D {
        return LatLonGeometry.create(circle as Circle)
    }

    override fun nextCircle(): Circle {
        val radiusMeters = random().nextDouble() * GeoUtils.EARTH_MEAN_RADIUS_METERS * kotlin.math.PI / 2.0 + 1.0
        return Circle(nextLatitude(), nextLongitude(), radiusMeters)
    }

    override fun randomQueryBox(): Rectangle {
        return GeoTestUtil.nextBox()
    }

    override fun nextPoints(): Array<Any> {
        val numPoints = TestUtil.nextInt(random(), 1, 20)
        val points = Array<Any>(numPoints) { DoubleArray(2) }
        for (i in 0..<numPoints) {
            val point = points[i] as DoubleArray
            point[0] = nextLatitude()
            point[1] = nextLongitude()
        }
        return points
    }

    override fun rectMinX(rect: Any): Double {
        return (rect as Rectangle).minLon
    }

    override fun rectMaxX(rect: Any): Double {
        return (rect as Rectangle).maxLon
    }

    override fun rectMinY(rect: Any): Double {
        return (rect as Rectangle).minLat
    }

    /** factory method to create a new polygon query */
    protected open override fun newPolygonQuery(field: String, queryRelation: QueryRelation, vararg polygons: Any): Query {
        throw UnsupportedOperationException("LatLonShape.newPolygonQuery is not ported yet")
    }

    override fun rectMaxY(rect: Any): Double {
        return (rect as Rectangle).maxLat
    }

    override fun rectCrossesDateline(rect: Any): Boolean {
        return (rect as Rectangle).crossesDateline()
    }

    override fun nextLine(): Line {
        return GeoTestUtil.nextLine()
    }

    override fun nextPolygon(): Polygon {
        return GeoTestUtil.nextPolygon()
    }

    override fun getEncoder(): Encoder {
        return object : Encoder() {
            override fun decodeX(encoded: Int): Double {
                return decodeLongitude(encoded)
            }

            override fun decodeY(encoded: Int): Double {
                return decodeLatitude(encoded)
            }

            override fun quantizeX(raw: Double): Double {
                return decodeLongitude(encodeLongitude(raw))
            }

            override fun quantizeXCeil(raw: Double): Double {
                return decodeLongitude(encodeLongitudeCeil(raw))
            }

            override fun quantizeY(raw: Double): Double {
                return decodeLatitude(encodeLatitude(raw))
            }

            override fun quantizeYCeil(raw: Double): Double {
                return decodeLatitude(encodeLatitudeCeil(raw))
            }
        }
    }

    /** internal shape type for testing different shape types */
    protected enum class ShapeType {
        POINT {
            override fun nextShape(): Any {
                return GeoTestUtil.nextPoint()
            }
        },
        LINE {
            override fun nextShape(): Any {
                return GeoTestUtil.nextLine()
            }
        },
        POLYGON {
            override fun nextShape(): Any {
                while (true) {
                    val p = GeoTestUtil.nextPolygon()
                    try {
                        Tessellator.tessellate(p, random().nextBoolean())
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
