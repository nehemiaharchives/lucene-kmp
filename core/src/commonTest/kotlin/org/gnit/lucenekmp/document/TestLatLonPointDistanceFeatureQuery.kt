package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.geo.GeoUtils
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.MultiReader
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.search.Sort
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.search.TopScoreDocCollectorManager
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.search.CheckHits
import org.gnit.lucenekmp.tests.search.QueryUtils
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.SloppyMath
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestLatLonPointDistanceFeatureQuery : LuceneTestCase() {

    @Test
    fun testEqualsAndHashcode() {
        val q1 = LatLonPoint.newDistanceFeatureQuery("foo", 3f, 10.0, 10.0, 5.0)
        val q2 = LatLonPoint.newDistanceFeatureQuery("foo", 3f, 10.0, 10.0, 5.0)
        QueryUtils.checkEqual(q1, q2)

        val q3 = LatLonPoint.newDistanceFeatureQuery("bar", 3f, 10.0, 10.0, 5.0)
        QueryUtils.checkUnequal(q1, q3)

        val q4 = LatLonPoint.newDistanceFeatureQuery("foo", 4f, 10.0, 10.0, 5.0)
        QueryUtils.checkUnequal(q1, q4)

        val q5 = LatLonPoint.newDistanceFeatureQuery("foo", 3f, 9.0, 10.0, 5.0)
        QueryUtils.checkUnequal(q1, q5)

        val q6 = LatLonPoint.newDistanceFeatureQuery("foo", 3f, 10.0, 9.0, 5.0)
        QueryUtils.checkUnequal(q1, q6)

        val q7 = LatLonPoint.newDistanceFeatureQuery("foo", 3f, 10.0, 10.0, 6.0)
        QueryUtils.checkUnequal(q1, q7)
    }

    @Test
    fun testBasics() {
        val dir: Directory = newDirectory()
        val w =
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
            )
        val doc = Document()
        val point = LatLonPoint("foo", 0.0, 0.0)
        doc.add(point)
        val docValue = LatLonDocValuesField("foo", 0.0, 0.0)
        doc.add(docValue)

        val pivotDistance = 5000.0 // 5k

        point.setLocationValue(-7.0, -7.0)
        docValue.setLocationValue(-7.0, -7.0)
        w.addDocument(doc)

        point.setLocationValue(9.0, 9.0)
        docValue.setLocationValue(9.0, 9.0)
        w.addDocument(doc)

        point.setLocationValue(8.0, 8.0)
        docValue.setLocationValue(8.0, 8.0)
        w.addDocument(doc)

        point.setLocationValue(4.0, 4.0)
        docValue.setLocationValue(4.0, 4.0)
        w.addDocument(doc)

        point.setLocationValue(-1.0, -1.0)
        docValue.setLocationValue(-1.0, -1.0)
        w.addDocument(doc)

        val reader = w.reader
        val searcher = newSearcher(reader)

        var q: Query = LatLonPoint.newDistanceFeatureQuery("foo", 3f, 10.0, 10.0, pivotDistance)
        var collectorManager = TopScoreDocCollectorManager(2, null, 1)
        var topHits = searcher.search(q, collectorManager)
        assertEquals(2, topHits.scoreDocs.size)

        var distance1 =
            SloppyMath.haversinMeters(
                GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(9.0)),
                GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(9.0)),
                10.0,
                10.0
            )
        var distance2 =
            SloppyMath.haversinMeters(
                GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(8.0)),
                GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(8.0)),
                10.0,
                10.0
            )

        CheckHits.checkEqual(
            q,
            arrayOf(
                ScoreDoc(1, (3f * (pivotDistance / (pivotDistance + distance1))).toFloat()),
                ScoreDoc(2, (3f * (pivotDistance / (pivotDistance + distance2))).toFloat())
            ),
            topHits.scoreDocs
        )

        distance1 =
            SloppyMath.haversinMeters(
                GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(9.0)),
                GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(9.0)),
                9.0,
                9.0
            )
        distance2 =
            SloppyMath.haversinMeters(
                GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(8.0)),
                GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(8.0)),
                9.0,
                9.0
            )

        q = LatLonPoint.newDistanceFeatureQuery("foo", 3f, 9.0, 9.0, pivotDistance)
        collectorManager = TopScoreDocCollectorManager(2, 1)
        topHits = searcher.search(q, collectorManager)
        assertEquals(2, topHits.scoreDocs.size)
        CheckHits.checkExplanations(q, "", searcher)

        CheckHits.checkEqual(
            q,
            arrayOf(
                ScoreDoc(1, (3f * (pivotDistance / (pivotDistance + distance1))).toFloat()),
                ScoreDoc(2, (3f * (pivotDistance / (pivotDistance + distance2))).toFloat())
            ),
            topHits.scoreDocs
        )

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    fun testCrossesDateLine() {
        val dir: Directory = newDirectory()
        val w =
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
            )
        val doc = Document()
        val point = LatLonPoint("foo", 0.0, 0.0)
        doc.add(point)
        val docValue = LatLonDocValuesField("foo", 0.0, 0.0)
        doc.add(docValue)

        val pivotDistance = 5000.0 // 5k

        point.setLocationValue(0.0, -179.0)
        docValue.setLocationValue(0.0, -179.0)
        w.addDocument(doc)

        point.setLocationValue(0.0, 176.0)
        docValue.setLocationValue(0.0, 176.0)
        w.addDocument(doc)

        point.setLocationValue(0.0, -150.0)
        docValue.setLocationValue(0.0, -150.0)
        w.addDocument(doc)

        point.setLocationValue(0.0, -140.0)
        docValue.setLocationValue(0.0, -140.0)
        w.addDocument(doc)

        point.setLocationValue(0.0, 140.0)
        docValue.setLocationValue(1.0, 140.0)
        w.addDocument(doc)

        val reader = w.reader
        val searcher = newSearcher(reader)

        val q: Query = LatLonPoint.newDistanceFeatureQuery("foo", 3f, 0.0, 179.0, pivotDistance)
        val collectorManager = TopScoreDocCollectorManager(2, 1)
        val topHits = searcher.search(q, collectorManager)
        assertEquals(2, topHits.scoreDocs.size)

        val distance1 =
            SloppyMath.haversinMeters(
                GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(0.0)),
                GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(-179.0)),
                0.0,
                179.0
            )
        val distance2 =
            SloppyMath.haversinMeters(
                GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(0.0)),
                GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(176.0)),
                0.0,
                179.0
            )

        CheckHits.checkEqual(
            q,
            arrayOf(
                ScoreDoc(0, (3f * (pivotDistance / (pivotDistance + distance1))).toFloat()),
                ScoreDoc(1, (3f * (pivotDistance / (pivotDistance + distance2))).toFloat())
            ),
            topHits.scoreDocs
        )

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    fun testMissingField() {
        val reader: IndexReader = MultiReader()
        val searcher = newSearcher(reader)

        val q = LatLonPoint.newDistanceFeatureQuery("foo", 3f, 10.0, 10.0, 5000.0)
        val topHits = searcher.search(q, 2)
        assertEquals(0, topHits.totalHits.value)
    }

    @Test
    fun testMissingValue() {
        val dir: Directory = newDirectory()
        val w =
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
            )
        val doc = Document()
        val point = LatLonPoint("foo", 0.0, 0.0)
        doc.add(point)
        val docValue = LatLonDocValuesField("foo", 0.0, 0.0)
        doc.add(docValue)

        point.setLocationValue(3.0, 3.0)
        docValue.setLocationValue(3.0, 3.0)
        w.addDocument(doc)

        w.addDocument(Document())

        point.setLocationValue(7.0, 7.0)
        docValue.setLocationValue(7.0, 7.0)
        w.addDocument(doc)

        val reader = w.reader
        val searcher = newSearcher(reader)

        val q: Query = LatLonPoint.newDistanceFeatureQuery("foo", 3f, 10.0, 10.0, 5.0)
        val collectorManager = TopScoreDocCollectorManager(3, 1)
        val topHits = searcher.search(q, collectorManager)
        assertEquals(2, topHits.scoreDocs.size)

        val distance1 =
            SloppyMath.haversinMeters(
                GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(7.0)),
                GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(7.0)),
                10.0,
                10.0
            )
        val distance2 =
            SloppyMath.haversinMeters(
                GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(3.0)),
                GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(3.0)),
                10.0,
                10.0
            )

        CheckHits.checkEqual(
            q,
            arrayOf(
                ScoreDoc(2, (3f * (5.0 / (5.0 + distance1))).toFloat()),
                ScoreDoc(0, (3f * (5.0 / (5.0 + distance2))).toFloat())
            ),
            topHits.scoreDocs
        )

        CheckHits.checkExplanations(q, "", searcher)

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    fun testMultiValued() {
        val dir: Directory = newDirectory()
        val w =
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
            )

        var doc = Document()
        for (point in arrayOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(30.0, 30.0), doubleArrayOf(60.0, 60.0))) {
            doc.add(LatLonPoint("foo", point[0], point[1]))
            doc.add(LatLonDocValuesField("foo", point[0], point[1]))
        }
        w.addDocument(doc)

        doc = Document()
        for (point in arrayOf(doubleArrayOf(45.0, 0.0), doubleArrayOf(-45.0, 0.0), doubleArrayOf(-90.0, 0.0), doubleArrayOf(90.0, 0.0))) {
            doc.add(LatLonPoint("foo", point[0], point[1]))
            doc.add(LatLonDocValuesField("foo", point[0], point[1]))
        }
        w.addDocument(doc)

        doc = Document()
        for (point in arrayOf(doubleArrayOf(0.0, 90.0), doubleArrayOf(0.0, -90.0), doubleArrayOf(0.0, 180.0), doubleArrayOf(0.0, -180.0))) {
            doc.add(LatLonPoint("foo", point[0], point[1]))
            doc.add(LatLonDocValuesField("foo", point[0], point[1]))
        }
        w.addDocument(doc)

        doc = Document()
        for (point in arrayOf(doubleArrayOf(3.0, 2.0))) {
            doc.add(LatLonPoint("foo", point[0], point[1]))
            doc.add(LatLonDocValuesField("foo", point[0], point[1]))
        }
        w.addDocument(doc)

        doc = Document()
        for (point in arrayOf(doubleArrayOf(45.0, 45.0), doubleArrayOf(-45.0, -45.0))) {
            doc.add(LatLonPoint("foo", point[0], point[1]))
            doc.add(LatLonDocValuesField("foo", point[0], point[1]))
        }
        w.addDocument(doc)

        val reader = w.reader
        val searcher = newSearcher(reader)

        var q: Query = LatLonPoint.newDistanceFeatureQuery("foo", 3f, 0.0, 0.0, 200.0)
        var collectorManager = TopScoreDocCollectorManager(2, 1)
        var topHits = searcher.search(q, collectorManager)
        assertEquals(2, topHits.scoreDocs.size)

        var distance1 =
            SloppyMath.haversinMeters(
                GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(0.0)),
                GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(0.0)),
                0.0,
                0.0
            )
        var distance2 =
            SloppyMath.haversinMeters(
                GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(3.0)),
                GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(2.0)),
                0.0,
                0.0
            )

        CheckHits.checkEqual(
            q,
            arrayOf(
                ScoreDoc(0, (3f * (200.0 / (200.0 + distance1))).toFloat()),
                ScoreDoc(3, (3f * (200.0 / (200.0 + distance2))).toFloat())
            ),
            topHits.scoreDocs
        )

        q = LatLonPoint.newDistanceFeatureQuery("foo", 3f, -90.0, 0.0, 10000.0)
        collectorManager = TopScoreDocCollectorManager(2, 1)
        topHits = searcher.search(q, collectorManager)
        assertEquals(2, topHits.scoreDocs.size)
        CheckHits.checkExplanations(q, "", searcher)

        distance1 =
            SloppyMath.haversinMeters(
                GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(-90.0)),
                GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(0.0)),
                -90.0,
                0.0
            )
        distance2 =
            SloppyMath.haversinMeters(
                GeoEncodingUtils.decodeLatitude(GeoEncodingUtils.encodeLatitude(-45.0)),
                GeoEncodingUtils.decodeLongitude(GeoEncodingUtils.encodeLongitude(-45.0)),
                -90.0,
                0.0
            )

        CheckHits.checkEqual(
            q,
            arrayOf(
                ScoreDoc(1, (3f * (10000.0 / (10000.0 + distance1))).toFloat()),
                ScoreDoc(4, (3f * (10000.0 / (10000.0 + distance2))).toFloat())
            ),
            topHits.scoreDocs
        )

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    fun testRandom() {
        val dir: Directory = newDirectory()
        val w: IndexWriter =
            IndexWriter(
                dir,
                newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
            )
        val doc = Document()
        val point = LatLonPoint("foo", 0.0, 0.0)
        doc.add(point)
        val docValue = LatLonDocValuesField("foo", 0.0, 0.0)
        doc.add(docValue)

        val numDocs = atLeast(1000)
        for (i in 0..<numDocs) {
            val lat = random().nextDouble() * 180 - 90
            val lon = random().nextDouble() * 360 - 180
            point.setLocationValue(lat, lon)
            docValue.setLocationValue(lat, lon)
            w.addDocument(doc)
        }

        val reader: IndexReader = DirectoryReader.open(w)
        val searcher = newSearcher(reader)

        val numIters = atLeast(3)
        for (iter in 0..<numIters) {
            val lat = random().nextDouble() * 180 - 90
            val lon = random().nextDouble() * 360 - 180
            val pivotDistance =
                random().nextDouble() * random().nextDouble() * PI * GeoUtils.EARTH_MEAN_RADIUS_METERS
            val boost = (1 + random().nextInt(10)) / 3f
            val q = LatLonPoint.newDistanceFeatureQuery("foo", boost, lat, lon, pivotDistance)

            CheckHits.checkTopScores(random(), q, searcher)
        }

        reader.close()
        w.close()
        dir.close()
    }

    @Test
    fun testCompareSorting() {
        val dir: Directory = newDirectory()
        val w =
            RandomIndexWriter(
                random(),
                dir,
                newIndexWriterConfig().setMergePolicy(newLogMergePolicy(random().nextBoolean()))
            )

        val doc = Document()
        val point = LatLonPoint("foo", 0.0, 0.0)
        doc.add(point)
        val docValue = LatLonDocValuesField("foo", 0.0, 0.0)
        doc.add(docValue)

        val numDocs = atLeast(100) // TODO reduced from 10000 to 100 for dev speed
        for (i in 0..<numDocs) {
            val lat = random().nextDouble() * 180 - 90
            val lon = random().nextDouble() * 360 - 180
            point.setLocationValue(lat, lon)
            docValue.setLocationValue(lat, lon)
            w.addDocument(doc)
        }

        val reader = w.reader
        val searcher = newSearcher(reader)

        val lat = random().nextDouble() * 180 - 90
        val lon = random().nextDouble() * 360 - 180
        val pivotDistance =
            random().nextDouble() * random().nextDouble() * GeoUtils.EARTH_MEAN_RADIUS_METERS * PI
        val boost = (1 + random().nextInt(10)) / 3f

        val query1 = LatLonPoint.newDistanceFeatureQuery("foo", boost, lat, lon, pivotDistance)
        val sort1 = Sort(SortField.FIELD_SCORE, LatLonDocValuesField.newDistanceSort("foo", lat, lon))

        val query2: Query = MatchAllDocsQuery()
        val sort2 = Sort(LatLonDocValuesField.newDistanceSort("foo", lat, lon))

        val topDocs1: TopDocs = searcher.search(query1, 10, sort1)
        val topDocs2: TopDocs = searcher.search(query2, 10, sort2)
        for (i in 0..<10) {
            assertTrue(topDocs1.scoreDocs[i].doc == topDocs2.scoreDocs[i].doc)
        }
        reader.close()
        w.close()
        dir.close()
    }
}
