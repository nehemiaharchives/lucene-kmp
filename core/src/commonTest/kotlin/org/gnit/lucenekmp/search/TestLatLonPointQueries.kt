package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.LatLonPoint
import org.gnit.lucenekmp.document.ShapeField
import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.geo.LatLonGeometry
import org.gnit.lucenekmp.geo.Polygon
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.tests.geo.BaseGeoPointTestCase
import org.gnit.lucenekmp.util.bkd.BKDConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class TestLatLonPointQueries : BaseGeoPointTestCase() {
    override fun addPointToDoc(field: String, doc: Document, lat: Double, lon: Double) {
        doc.add(LatLonPoint(field, lat, lon))
    }

    override fun newRectQuery(
        field: String?, minLat: Double, maxLat: Double, minLon: Double, maxLon: Double
    ): Query {
        return LatLonPoint.newBoxQuery(field, minLat, maxLat, minLon, maxLon)
    }

    override fun newDistanceQuery(
        field: String?, centerLat: Double, centerLon: Double, radiusMeters: Double
    ): Query {
        return LatLonPoint.newDistanceQuery(field, centerLat, centerLon, radiusMeters)
    }

    override fun newPolygonQuery(field: String?, vararg polygons: Polygon): Query {
        return LatLonPoint.newPolygonQuery(field, *polygons)
    }

    override fun newGeometryQuery(field: String, vararg geometry: LatLonGeometry): Query {
        return LatLonPoint.newGeometryQuery(field, ShapeField.QueryRelation.INTERSECTS, *geometry)
    }

    override fun quantizeLat(latRaw: Double): Double {
        return GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(latRaw))
    }

    override fun quantizeLon(lonRaw: Double): Double {
        return GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(lonRaw))
    }

    @Test
    @Throws(IOException::class)
    fun testDistanceQueryWithInvertedIntersection() {
        val numMatchingDocs: Int = atLeast(10 * BKDConfig.DEFAULT_MAX_POINTS_IN_LEAF_NODE)

        newDirectory().use { dir ->
            IndexWriter(dir, newIndexWriterConfig()).use { w ->
                for (i in 0..<numMatchingDocs) {
                    val doc = Document()
                    addPointToDoc("field", doc, 18.313694, -65.227444)
                    w.addDocument(doc)
                }
                // Add a handful of docs that don't match
                for (i in 0..10) {
                    val doc: Document = Document()
                    addPointToDoc("field", doc, 10.0, -65.227444)
                    w.addDocument(doc)
                }
                w.forceMerge(1)
            }
            DirectoryReader.open(dir).use { r ->
                val searcher: IndexSearcher = newSearcher(r)
                assertEquals(numMatchingDocs.toLong(), searcher.count(newDistanceQuery("field", 18.0, -65.0, 50000.0)).toLong())
            }
        }
    }

    // tests inherited from BaseGeoPointTestCase

    @Test
    override fun testIndexExtremeValues() = super.testIndexExtremeValues()

    @Test
    override fun testIndexOutOfRangeValues() = super.testIndexOutOfRangeValues()

    @Test
    override fun testIndexNaNValues() = super.testIndexNaNValues()

    @Test
    override fun testIndexInfValues() = super.testIndexInfValues()

    @Test
    override fun testBoxBasics() = super.testBoxBasics()

    @Test
    override fun testBoxNull() = super.testBoxNull()

    @Test
    override fun testBoxInvalidCoordinates() = super.testBoxInvalidCoordinates()

    @Test
    override fun testDistanceBasics() = super.testDistanceBasics()

    @Test
    override fun testDistanceNull() = super.testDistanceNull()

    @Test
    override fun testDistanceIllegal() = super.testDistanceIllegal()

    @Test
    override fun testDistanceNegative() = super.testDistanceNegative()

    @Test
    override fun testDistanceNaN() = super.testDistanceNaN()

    @Test
    override fun testDistanceInf() = super.testDistanceInf()

    @Test
    override fun testPolygonBasics() = super.testPolygonBasics()

    @Test
    override fun testPolygonHole() = super.testPolygonHole()

    @Test
    override fun testPolygonHoleExcludes() = super.testPolygonHoleExcludes()

    @Test
    override fun testMultiPolygonBasics() = super.testMultiPolygonBasics()

    @Test
    override fun testPolygonNullField() = super.testPolygonNullField()

    @Test
    override fun testSamePointManyTimes() = super.testSamePointManyTimes()

    @Test
    override fun testLowCardinality() = super.testLowCardinality()

    @Test
    override fun testAllLatEqual() = super.testAllLatEqual()

    @Test
    override fun testAllLonEqual() = super.testAllLonEqual()

    @Test
    override fun testMultiValued() = super.testMultiValued()

    @Test
    override fun testRandomTiny() = super.testRandomTiny()

    @Test
    override fun testRandomMedium() = super.testRandomMedium()

    @Test
    override fun testRandomBig() = super.testRandomBig()

    @Test
    override fun testRectBoundariesAreInclusive() = super.testRectBoundariesAreInclusive()

    @Test
    override fun testRandomDistance() = super.testRandomDistance()

    @Test
    override fun testRandomDistanceHuge() = super.testRandomDistanceHuge()

    @Test
    override fun testEquals() = super.testEquals()

    @Test
    override fun testSmallSetRect() = super.testSmallSetRect()

    @Test
    override fun testSmallSetDateline() = super.testSmallSetDateline()

    @Test
    override fun testSmallSetMultiValued() = super.testSmallSetMultiValued()

    @Test
    override fun testSmallSetWholeMap() = super.testSmallSetWholeMap()

    @Test
    override fun testSmallSetPoly() = super.testSmallSetPoly()

    @Test
    override fun testSmallSetPolyWholeMap() = super.testSmallSetPolyWholeMap()

    @Test
    override fun testSmallSetDistance() = super.testSmallSetDistance()

    @Test
    override fun testSmallSetTinyDistance() = super.testSmallSetTinyDistance()

    @Test
    override fun testSmallSetDistanceNotEmpty() = super.testSmallSetDistanceNotEmpty()

    @Test
    override fun testSmallSetHugeDistance() = super.testSmallSetHugeDistance()

    @Test
    override fun testSmallSetDistanceDateline() = super.testSmallSetDistanceDateline()

    @Test
    override fun testNarrowPolygonCloseToNorthPole() = super.testNarrowPolygonCloseToNorthPole()
}
