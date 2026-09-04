package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.Polygon
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test

/**
 * random bounding box, line, and polygon query tests for random indexed arrays of [Polygon]
 * types
 */
class TestLatLonMultiPolygonShapeQueries : BaseLatLonShapeTestCase() {

    override fun getShapeType(): Any {
        return ShapeType.POLYGON
    }

    override fun nextShape(): Any {
        val n = random().nextInt(4) + 1
        val polygons = arrayOfNulls<Polygon>(n)
        for (i in 0..<n) {
            var repetitions = 0
            while (true) {
                val p = getShapeType().let { (it as ShapeType).nextShape() } as Polygon
                // polygons are disjoint so CONTAINS works. Note that if we intersect
                // any shape then contains return false.
                if (isDisjoint(polygons, p)) {
                    polygons[i] = p
                    break
                }
                repetitions++
                if (repetitions > 50) {
                    // try again
                    return nextShape()
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        return polygons as Array<Polygon>
    }

    private fun isDisjoint(polygons: Array<Polygon?>, check: Polygon): Boolean {
        // we use bounding boxes so we do not get intersecting polygons.
        for (polygon in polygons) {
            if (polygon != null) {
                if (getEncoder().quantizeY(polygon.minLat) > getEncoder().quantizeY(check.maxLat)
                    || getEncoder().quantizeY(polygon.maxLat) < getEncoder().quantizeY(check.minLat)
                    || getEncoder().quantizeX(polygon.minLon) > getEncoder().quantizeX(check.maxLon)
                    || getEncoder().quantizeX(polygon.maxLon) < getEncoder().quantizeX(check.minLon)
                ) {
                    continue
                }
                return false
            }
        }
        return true
    }

    override fun createIndexableFields(name: String, o: Any): Array<Field> {
        val polygons = o as Array<Polygon>
        val allFields = mutableListOf<Field>()
        for (polygon in polygons) {
            allFields.addAll(LatLonShape.createIndexableFields(name, polygon))
        }
        return allFields.toTypedArray()
    }

    override fun getValidator(): Validator {
        return MultiPolygonValidator(ENCODER)
    }

    class MultiPolygonValidator(encoder: Encoder) : Validator(encoder) {
        var POLYGONVALIDATOR: TestLatLonPolygonShapeQueries.PolygonValidator

        init {
            POLYGONVALIDATOR = TestLatLonPolygonShapeQueries.PolygonValidator(encoder)
        }

        override fun setRelation(relation: QueryRelation): Validator {
            super.setRelation(relation)
            POLYGONVALIDATOR.setRelation(relation)
            return this
        }

        override fun testComponentQuery(query: Component2D, shape: Any): Boolean {
            val polygons = shape as Array<Polygon>
            if (queryRelation == QueryRelation.CONTAINS) {
                return testWithinPolygon(query, polygons)
            }
            for (p in polygons) {
                val b = POLYGONVALIDATOR.testComponentQuery(query, p)
                if (b && queryRelation == QueryRelation.INTERSECTS) {
                    return true
                } else if (b && queryRelation == QueryRelation.CONTAINS) {
                    return true
                } else if (!b && queryRelation == QueryRelation.DISJOINT) {
                    return false
                } else if (!b && queryRelation == QueryRelation.WITHIN) {
                    return false
                }
            }
            return queryRelation != QueryRelation.INTERSECTS && queryRelation != QueryRelation.CONTAINS
        }

        private fun testWithinPolygon(query: Component2D, polygons: Array<Polygon>): Boolean {
            var answer = Component2D.WithinRelation.DISJOINT
            for (p in polygons) {
                val relation = POLYGONVALIDATOR.testWithinPolygon(query, p)
                if (relation == Component2D.WithinRelation.NOTWITHIN) {
                    return false
                } else if (relation == Component2D.WithinRelation.CANDIDATE) {
                    answer = relation
                }
            }
            return answer == Component2D.WithinRelation.CANDIDATE
        }
    }

    // tests inherited from BaseLatLonShapeTestCase

    @Test
    override fun testBoundingBoxQueriesEquivalence() = super.testBoundingBoxQueriesEquivalence()

    @Test
    override fun testBoxQueryEqualsAndHashcode() = super.testBoxQueryEqualsAndHashcode()

    @Test
    override fun testLineQueryEqualsAndHashcode() = super.testLineQueryEqualsAndHashcode()

    @Test
    override fun testPolygonQueryEqualsAndHashcode() = super.testPolygonQueryEqualsAndHashcode()

    // tests inherited from BaseSpatialTestCase

    @LuceneTestCase.Companion.Nightly
    @Test
    override fun testRandomBig() {
        doTestRandom(10) // TODO reduced from 10000 to 10 for dev speed
    }

    @Test
    override fun testSameShapeManyTimes() = super.testSameShapeManyTimes()

    @Test
    override fun testLowCardinalityShapeManyTimes() = super.testLowCardinalityShapeManyTimes()

    @Test
    override fun testRandomTiny() = super.testRandomTiny()

    @Test
    override fun testRandomMedium() = super.testRandomMedium()
}
