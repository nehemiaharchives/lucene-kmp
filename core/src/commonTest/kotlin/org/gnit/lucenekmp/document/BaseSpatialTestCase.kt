package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.GeoUtils
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.MultiBits
import org.gnit.lucenekmp.index.MultiDocValues
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SerialMergeScheduler
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.search.FixedBitSetCollector
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomPicks
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Base test case for testing spherical and cartesian geometry indexing and search functionality
 *
 * This class is implemented by BaseXYShapeTestCase for testing XY cartesian geometry and
 * BaseLatLonSpatialTestCase for testing Lat Lon geospatial geometry.
 */
abstract class BaseSpatialTestCase : LuceneTestCase() {

    companion object {
        /** name of the LatLonShape indexed field */
        protected const val FIELD_NAME: String = "shape"

        val POINT_LINE_RELATIONS: Array<QueryRelation> =
            arrayOf(QueryRelation.INTERSECTS, QueryRelation.DISJOINT, QueryRelation.CONTAINS)
    }

    protected val ENCODER: Encoder = getEncoder()
    protected val VALIDATOR: Validator = getValidator()

    // A particularly tricky adversary for BKD tree:
    open fun testSameShapeManyTimes() {
        val numShapes = if (TEST_NIGHTLY) atLeast(50) else atLeast(3)

        // Every doc has 2 points:
        val theShape = nextShape()

        val shapes = Array<Any?>(numShapes) { theShape }

        verify(*shapes)
    }

    // Force low cardinality leaves
    open fun testLowCardinalityShapeManyTimes() {
        val numShapes = atLeast(20)
        val cardinality = TestUtil.nextInt(random(), 2, 20)

        val diffShapes = Array<Any?>(cardinality) { nextShape() }

        val shapes = Array<Any?>(numShapes) { diffShapes[random().nextInt(cardinality)] }

        verify(*shapes)
    }

    open fun testRandomTiny() {
        // Make sure single-leaf-node case is OK:
        doTestRandom(3)  // TODO reduced from 10 to 3 for dev speed
    }

    open fun testRandomMedium() {
        doTestRandom(atLeast(6)) // TODO reduced from 30 to 6 for dev speed
    }

    @LuceneTestCase.Companion.Nightly
    open fun testRandomBig() {
        doTestRandom(20) // TODO reduced from 20000 to 20 for dev speed
    }

    protected open fun doTestRandom(count: Int) {
        val numShapes = atLeast(count)

        if (VERBOSE) {
            println("TEST: number of ${getShapeType()} shapes=$numShapes")
        }

        val shapes = Array<Any?>(numShapes) { null }
        for (id in 0..<numShapes) {
            val x = TestUtil.nextInt(random(), 0, 20)
            if (x == 17) {
                shapes[id] = null
                if (VERBOSE) {
                    println("  id=$id is missing")
                }
            } else {
                // create a new shape
                shapes[id] = nextShape()
            }
        }
        verify(*shapes)
    }

    protected abstract fun getShapeType(): Any

    protected abstract fun nextShape(): Any

    protected abstract fun getEncoder(): Encoder

    /** creates the array of LatLonShape.Triangle values that are used to index the shape */
    protected abstract fun createIndexableFields(field: String, shape: Any): Array<Field>

    /** adds a shape to a provided document */
    private fun addShapeToDoc(field: String, doc: Document, shape: Any) {
        val fields = createIndexableFields(field, shape)
        for (f in fields) {
            doc.add(f)
        }
    }

    /** return a semi-random line used for queries * */
    protected abstract fun nextLine(): Any

    protected abstract fun nextPolygon(): Any

    protected abstract fun randomQueryBox(): Any

    protected abstract fun nextPoints(): Array<Any>

    protected abstract fun nextCircle(): Any

    protected abstract fun rectMinX(rect: Any): Double

    protected abstract fun rectMaxX(rect: Any): Double

    protected abstract fun rectMinY(rect: Any): Double

    protected abstract fun rectMaxY(rect: Any): Double

    protected abstract fun rectCrossesDateline(rect: Any): Boolean

    protected open fun getSupportedQueryRelations(): Array<QueryRelation> {
        return QueryRelation.entries.toTypedArray()
    }

    /**
     * return a semi-random line used for queries
     *
     * note: shapes parameter may be used to ensure some queries intersect indexed shapes
     */
    protected open fun randomQueryLine(vararg shapes: Any?): Any {
        return nextLine()
    }

    protected open fun randomQueryPolygon(): Any {
        return nextPolygon()
    }

    protected open fun randomQueryCircle(): Any {
        return nextCircle()
    }

    /** factory method to create a new bounding box query */
    protected abstract fun newRectQuery(
        field: String,
        queryRelation: QueryRelation,
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double
    ): Query

    /** factory method to create a new line query */
    protected abstract fun newLineQuery(field: String, queryRelation: QueryRelation, vararg lines: Any): Query

    /** factory method to create a new polygon query */
    protected abstract fun newPolygonQuery(field: String, queryRelation: QueryRelation, vararg polygons: Any): Query

    /** factory method to create a new point query */
    protected abstract fun newPointsQuery(field: String, queryRelation: QueryRelation, vararg points: Any): Query

    /** factory method to create a new distance query */
    protected abstract fun newDistanceQuery(field: String, queryRelation: QueryRelation, circle: Any): Query

    protected abstract fun toLine2D(vararg line: Any): Component2D

    protected abstract fun toPolygon2D(vararg polygon: Any): Component2D

    protected abstract fun toPoint2D(vararg points: Any): Component2D

    protected abstract fun toCircle2D(circle: Any): Component2D

    protected abstract fun toRectangle2D(minX: Double, maxX: Double, minY: Double, maxY: Double): Component2D

    private fun verify(vararg shapes: Any?) {
        val iwc: IndexWriterConfig = newIndexWriterConfig()
        iwc.setMergeScheduler(SerialMergeScheduler())
        val mbd = iwc.maxBufferedDocs
        if (mbd != -1 && mbd < shapes.size / 100) {
            iwc.setMaxBufferedDocs(shapes.size / 100)
        }
        val dir: Directory = if (shapes.size > 1000) {
            newFSDirectory(createTempDir(this::class.simpleName!!))
        } else {
            newDirectory()
        }
        val w = IndexWriter(dir, iwc)

        // index random polygons
        indexRandomShapes(w, *shapes)

        // query testing
        val reader: IndexReader = DirectoryReader.open(w)
        // test random bbox queries
        verifyRandomQueries(reader, *shapes)
        IOUtils.close(w, reader, dir)
    }

    protected open fun indexRandomShapes(w: IndexWriter, vararg shapes: Any?) {
        for (id in shapes.indices) {
            val doc = Document()
            doc.add(StringField("id", "$id", Field.Store.NO))
            doc.add(NumericDocValuesField("id", id.toLong()))
            if (shapes[id] != null) {
                addShapeToDoc(FIELD_NAME, doc, shapes[id]!!)
            }
            w.addDocument(doc)
            if (id > 0 && random().nextInt(100) == 42) {
                val idToDelete = random().nextInt(id)
                w.deleteDocuments(Term("id", "$idToDelete"))
                if (VERBOSE) {
                    println("   delete id=$idToDelete")
                }
            }
        }

        if (random().nextBoolean()) {
            w.forceMerge(1)
        }
    }

    protected open fun verifyRandomQueries(reader: IndexReader, vararg shapes: Any?) {
        // test random bbox queries
        verifyRandomBBoxQueries(reader, *shapes)
        // test random line queries
        verifyRandomLineQueries(reader, *shapes)
        // test random polygon queries
        verifyRandomPolygonQueries(reader, *shapes)
        // test random point queries
        verifyRandomPointQueries(reader, *shapes)
        // test random distance queries
        verifyRandomDistanceQueries(reader, *shapes)
    }

    /** test random generated bounding boxes */
    protected open fun verifyRandomBBoxQueries(reader: IndexReader, vararg shapes: Any?) {
        val s: IndexSearcher = newSearcher(reader)

        val iters = scaledIterationCount(shapes.size)

        val liveDocs: Bits? = MultiBits.getLiveDocs(s.indexReader)
        val maxDoc = s.indexReader.maxDoc()

        for (iter in 0..<iters) {
            if (VERBOSE) {
                println("\nTEST: iter=${iter + 1} of $iters s=$s")
            }

            // BBox
            val rect = randomQueryBox()
            val queryRelation = RandomPicks.randomFrom(random(), getSupportedQueryRelations())
            val query =
                newRectQuery(
                    FIELD_NAME,
                    queryRelation,
                    rectMinX(rect),
                    rectMaxX(rect),
                    rectMinY(rect),
                    rectMaxY(rect)
                )

            if (VERBOSE) {
                println("  query=$query, relation=$queryRelation")
            }

            val hits = searchIndex(s, query, maxDoc)

            var failFlag = false
            val docIDToID: NumericDocValues = MultiDocValues.getNumericValues(reader, "id")!!
            for (docID in 0..<maxDoc) {
                assertEquals(docID.toLong(), docIDToID.nextDoc().toLong())
                val id = docIDToID.longValue().toInt()
                val expected: Boolean
                val minX = rectMinX(rect)
                val maxX = rectMaxX(rect)
                val minY = rectMinY(rect)
                val maxY = rectMaxY(rect)
                if (liveDocs != null && !liveDocs.get(docID)) {
                    // document is deleted
                    expected = false
                } else if (shapes[id] == null) {
                    expected = false
                } else {
                    if (queryRelation == QueryRelation.CONTAINS && rectCrossesDateline(rect)) {
                        // For contains we need to call the validator for each section.
                        // It is only expected if both sides are contained.
                        val left = toRectangle2D(minX, GeoUtils.MAX_LON_INCL, minY, maxY)
                        val right = toRectangle2D(GeoUtils.MIN_LON_INCL, maxX, minY, maxY)
                        expected =
                            VALIDATOR.setRelation(queryRelation).testComponentQuery(left, shapes[id]!!)
                                    && VALIDATOR.setRelation(queryRelation).testComponentQuery(right, shapes[id]!!)
                    } else {
                        val component2D = toRectangle2D(minX, maxX, minY, maxY)
                        expected =
                            VALIDATOR.setRelation(queryRelation).testComponentQuery(component2D, shapes[id]!!)
                    }
                }

                if (hits.get(docID) != expected) {
                    val b = StringBuilder()

                    if (expected) {
                        b.append("FAIL: id=$id should match but did not\n")
                    } else {
                        b.append("FAIL: id=$id should not match but did\n")
                    }
                    b.append("  relation=$queryRelation\n")
                    b.append("  query=$query docID=$docID\n")
                    val shape = shapes[id]
                    if (shape is Array<*>) {
                        b.append("  shape=${shape.contentToString()}\n")
                    } else {
                        b.append("  shape=$shape\n")
                    }
                    b.append("  deleted?=${liveDocs != null && !liveDocs.get(docID)}")
                    b.append(
                        "  rect=Rectangle(lat=${ENCODER.quantizeYCeil(rectMinY(rect))} TO ${ENCODER.quantizeY(rectMaxY(rect))}" +
                                " lon=$minX TO ${ENCODER.quantizeX(rectMaxX(rect))})\n"
                    )
                    fail("wrong hit (first of possibly more):\n\n$b")
                }
            }
            if (failFlag) {
                fail("some hits were wrong")
            }
        }
    }

    /** test random generated lines */
    protected open fun verifyRandomLineQueries(reader: IndexReader, vararg shapes: Any?) {
        val s: IndexSearcher = newSearcher(reader)

        val iters = scaledIterationCount(shapes.size)

        val liveDocs: Bits? = MultiBits.getLiveDocs(s.indexReader)
        val maxDoc = s.indexReader.maxDoc()

        for (iter in 0..<iters) {
            if (VERBOSE) {
                println("\nTEST: iter=${iter + 1} of $iters s=$s")
            }

            // line
            val queryLine = randomQueryLine(*shapes)
            val queryLine2D = toLine2D(queryLine)
            val queryRelation = RandomPicks.randomFrom(random(), POINT_LINE_RELATIONS)
            val query = newLineQuery(FIELD_NAME, queryRelation, queryLine)

            if (VERBOSE) {
                println("  query=$query, relation=$queryRelation")
            }

            val hits = searchIndex(s, query, maxDoc)

            val docIDToID: NumericDocValues = MultiDocValues.getNumericValues(reader, "id")!!
            for (docID in 0..<maxDoc) {
                assertEquals(docID.toLong(), docIDToID.nextDoc().toLong())
                val id = docIDToID.longValue().toInt()
                val expected: Boolean = if (liveDocs != null && !liveDocs.get(docID)) {
                    false
                } else if (shapes[id] == null) {
                    false
                } else {
                    VALIDATOR.setRelation(queryRelation).testComponentQuery(queryLine2D, shapes[id]!!)
                }

                if (hits.get(docID) != expected) {
                    val b = StringBuilder()
                    if (expected) {
                        b.append("FAIL: id=$id should match but did not\n")
                    } else {
                        b.append("FAIL: id=$id should not match but did\n")
                    }
                    b.append("  relation=$queryRelation\n")
                    b.append("  query=$query docID=$docID\n")
                    val shape = shapes[id]
                    if (shape is Array<*>) {
                        b.append("  shape=${shape.contentToString()}\n")
                    } else {
                        b.append("  shape=$shape\n")
                    }
                    b.append("  deleted?=${liveDocs != null && !liveDocs.get(docID)}")
                    b.append("  queryPolygon=$queryLine")
                    fail("wrong hit (first of possibly more):\n\n$b")
                }
            }
        }
    }

    /** test random generated polygons */
    protected open fun verifyRandomPolygonQueries(reader: IndexReader, vararg shapes: Any?) {
        val s: IndexSearcher = newSearcher(reader)

        val iters = scaledIterationCount(shapes.size)

        val liveDocs: Bits? = MultiBits.getLiveDocs(s.indexReader)
        val maxDoc = s.indexReader.maxDoc()

        for (iter in 0..<iters) {
            if (VERBOSE) {
                println("\nTEST: iter=${iter + 1} of $iters s=$s")
            }

            // Polygon
            val queryPolygon = randomQueryPolygon()
            val queryPoly2D = toPolygon2D(queryPolygon)
            val queryRelation = RandomPicks.randomFrom(random(), QueryRelation.entries.toTypedArray())
            val query = newPolygonQuery(FIELD_NAME, queryRelation, queryPolygon)

            if (VERBOSE) {
                println("  query=$query, relation=$queryRelation")
            }

            val hits = searchIndex(s, query, maxDoc)

            val docIDToID: NumericDocValues = MultiDocValues.getNumericValues(reader, "id")!!
            for (docID in 0..<maxDoc) {
                assertEquals(docID.toLong(), docIDToID.nextDoc().toLong())
                val id = docIDToID.longValue().toInt()
                val expected: Boolean = if (liveDocs != null && !liveDocs.get(docID)) {
                    false
                } else if (shapes[id] == null) {
                    false
                } else {
                    VALIDATOR.setRelation(queryRelation).testComponentQuery(queryPoly2D, shapes[id]!!)
                }

                if (hits.get(docID) != expected) {
                    val b = StringBuilder()
                    if (expected) {
                        b.append("FAIL: id=$id should match but did not\n")
                    } else {
                        b.append("FAIL: id=$id should not match but did\n")
                    }
                    b.append("  relation=$queryRelation\n")
                    b.append("  query=$query docID=$docID\n")
                    val shape = shapes[id]
                    if (shape is Array<*>) {
                        b.append("  shape=${shape.contentToString()}\n")
                    } else {
                        b.append("  shape=$shape\n")
                    }
                    b.append("  deleted?=${liveDocs != null && !liveDocs.get(docID)}")
                    b.append("  queryPolygon=$queryPolygon")
                    fail("wrong hit (first of possibly more):\n\n$b")
                }
            }
        }
    }

    /** test random generated point queries */
    protected open fun verifyRandomPointQueries(reader: IndexReader, vararg shapes: Any?) {
        val s: IndexSearcher = newSearcher(reader)

        val iters = scaledIterationCount(shapes.size)

        val liveDocs: Bits? = MultiBits.getLiveDocs(s.indexReader)
        val maxDoc = s.indexReader.maxDoc()

        for (iter in 0..<iters) {
            if (VERBOSE) {
                println("\nTEST: iter=${iter + 1} of $iters s=$s")
            }

            val queryPoints = nextPoints()
            val queryRelation = RandomPicks.randomFrom(random(), QueryRelation.entries.toTypedArray())
            val queryPoly2D: Component2D
            val query: Query
            if (queryRelation == QueryRelation.CONTAINS) {
                queryPoly2D = toPoint2D(queryPoints[0])
                query = newPointsQuery(FIELD_NAME, queryRelation, queryPoints[0])
            } else {
                queryPoly2D = toPoint2D(*queryPoints)
                query = newPointsQuery(FIELD_NAME, queryRelation, *queryPoints)
            }

            if (VERBOSE) {
                println("  query=$query, relation=$queryRelation")
            }

            val hits = searchIndex(s, query, maxDoc)

            val docIDToID: NumericDocValues = MultiDocValues.getNumericValues(reader, "id")!!
            for (docID in 0..<maxDoc) {
                assertEquals(docID.toLong(), docIDToID.nextDoc().toLong())
                val id = docIDToID.longValue().toInt()
                val expected: Boolean = if (liveDocs != null && !liveDocs.get(docID)) {
                    false
                } else if (shapes[id] == null) {
                    false
                } else {
                    VALIDATOR.setRelation(queryRelation).testComponentQuery(queryPoly2D, shapes[id]!!)
                }

                if (hits.get(docID) != expected) {
                    val b = StringBuilder()
                    if (expected) {
                        b.append("FAIL: id=$id should match but did not\n")
                    } else {
                        b.append("FAIL: id=$id should not match but did\n")
                    }
                    b.append("  relation=$queryRelation\n")
                    b.append("  query=$query docID=$docID\n")
                    val shape = shapes[id]
                    if (shape is Array<*>) {
                        b.append("  shape=${shape.contentToString()}\n")
                    } else {
                        b.append("  shape=$shape\n")
                    }
                    b.append("  deleted?=${liveDocs != null && !liveDocs.get(docID)}")
                    b.append("  rect=Points(${queryPoints.contentToString()})\n")
                    fail("wrong hit (first of possibly more):\n\n$b")
                }
            }
        }
    }

    /** test random generated circles */
    protected open fun verifyRandomDistanceQueries(reader: IndexReader, vararg shapes: Any?) {
        val s: IndexSearcher = newSearcher(reader)

        val iters = scaledIterationCount(shapes.size)

        val liveDocs: Bits? = MultiBits.getLiveDocs(s.indexReader)
        val maxDoc = s.indexReader.maxDoc()

        for (iter in 0..<iters) {
            if (VERBOSE) {
                println("\nTEST: iter=${iter + 1} of $iters s=$s")
            }

            // Polygon
            val queryCircle = randomQueryCircle()
            val queryCircle2D = toCircle2D(queryCircle)
            val queryRelation = RandomPicks.randomFrom(random(), QueryRelation.entries.toTypedArray())
            val query = newDistanceQuery(FIELD_NAME, queryRelation, queryCircle)

            if (VERBOSE) {
                println("  query=$query, relation=$queryRelation")
            }

            val hits = searchIndex(s, query, maxDoc)

            val docIDToID: NumericDocValues = MultiDocValues.getNumericValues(reader, "id")!!
            for (docID in 0..<maxDoc) {
                assertEquals(docID.toLong(), docIDToID.nextDoc().toLong())
                val id = docIDToID.longValue().toInt()
                val expected: Boolean = if (liveDocs != null && !liveDocs.get(docID)) {
                    false
                } else if (shapes[id] == null) {
                    false
                } else {
                    VALIDATOR.setRelation(queryRelation).testComponentQuery(queryCircle2D, shapes[id]!!)
                }

                if (hits.get(docID) != expected) {
                    val b = StringBuilder()

                    if (expected) {
                        b.append("FAIL: id=$id should match but did not\n")
                    } else {
                        b.append("FAIL: id=$id should not match but did\n")
                    }
                    b.append("  relation=$queryRelation\n")
                    b.append("  query=$query docID=$docID\n")
                    val shape = shapes[id]
                    if (shape is Array<*>) {
                        b.append("  shape=${shape.contentToString()}\n")
                    } else {
                        b.append("  shape=$shape\n")
                    }
                    b.append("  deleted?=${liveDocs != null && !liveDocs.get(docID)}")
                    b.append("  distanceQuery=$queryCircle")
                    fail("wrong hit (first of possibly more):\n\n$b")
                }
            }
        }
    }

    private fun searchIndex(s: IndexSearcher, query: Query, maxDoc: Int): FixedBitSet {
        return s.search(query, FixedBitSetCollector.createManager(maxDoc))
    }

    protected abstract fun getValidator(): Validator

    protected abstract class Encoder {
        abstract fun decodeX(encoded: Int): Double

        abstract fun decodeY(encoded: Int): Double

        abstract fun quantizeX(raw: Double): Double

        abstract fun quantizeXCeil(raw: Double): Double

        abstract fun quantizeY(raw: Double): Double

        abstract fun quantizeYCeil(raw: Double): Double
    }

    private fun scaledIterationCount(shapes: Int): Int {
        return if (shapes < 500) {
            atLeast(50)
        } else if (shapes < 5000) {
            atLeast(25)
        } else if (shapes < 25000) {
            atLeast(5)
        } else {
            atLeast(2)
        }
    }

    /** validator class used to test query results against "ground truth" */
    protected abstract class Validator(protected val encoder: Encoder) {
        protected var queryRelation: QueryRelation = QueryRelation.INTERSECTS

        abstract fun testComponentQuery(line2d: Component2D, shape: Any): Boolean

        open fun setRelation(relation: QueryRelation): Validator {
            queryRelation = relation
            return this
        }

        fun testComponentQuery(query: Component2D, fields: Array<Field>): Boolean {
            val decodedTriangle = ShapeField.DecodedTriangle()
            for (field in fields) {
                val intersects: Boolean
                val contains: Boolean
                ShapeField.decodeTriangle(field.binaryValue()!!.bytes, decodedTriangle)
                when (decodedTriangle.type) {
                    ShapeField.DecodedTriangle.TYPE.POINT -> {
                        val y = encoder.decodeY(decodedTriangle.aY)
                        val x = encoder.decodeX(decodedTriangle.aX)
                        intersects = query.contains(x, y)
                        contains = intersects
                    }

                    ShapeField.DecodedTriangle.TYPE.LINE -> {
                        val aY = encoder.decodeY(decodedTriangle.aY)
                        val aX = encoder.decodeX(decodedTriangle.aX)
                        val bY = encoder.decodeY(decodedTriangle.bY)
                        val bX = encoder.decodeX(decodedTriangle.bX)
                        intersects = query.intersectsLine(aX, aY, bX, bY)
                        contains = query.containsLine(aX, aY, bX, bY)
                    }

                    ShapeField.DecodedTriangle.TYPE.TRIANGLE -> {
                        val aY = encoder.decodeY(decodedTriangle.aY)
                        val aX = encoder.decodeX(decodedTriangle.aX)
                        val bY = encoder.decodeY(decodedTriangle.bY)
                        val bX = encoder.decodeX(decodedTriangle.bX)
                        val cY = encoder.decodeY(decodedTriangle.cY)
                        val cX = encoder.decodeX(decodedTriangle.cX)
                        intersects = query.intersectsTriangle(aX, aY, bX, bY, cX, cY)
                        contains = query.containsTriangle(aX, aY, bX, bY, cX, cY)
                    }
                }
                assertTrue((contains == intersects) || (!contains && intersects))
                if (queryRelation == QueryRelation.DISJOINT && intersects) {
                    return false
                } else if (queryRelation == QueryRelation.WITHIN && !contains) {
                    return false
                } else if (queryRelation == QueryRelation.INTERSECTS && intersects) {
                    return true
                }
            }
            return queryRelation != QueryRelation.INTERSECTS
        }

        protected fun testWithinQuery(query: Component2D, fields: Array<Field>): Component2D.WithinRelation {
            var answer = Component2D.WithinRelation.DISJOINT
            val decodedTriangle = ShapeField.DecodedTriangle()
            for (field in fields) {
                ShapeField.decodeTriangle(field.binaryValue()!!.bytes, decodedTriangle)
                val relation = when (decodedTriangle.type) {
                    ShapeField.DecodedTriangle.TYPE.POINT -> {
                        val y = encoder.decodeY(decodedTriangle.aY)
                        val x = encoder.decodeX(decodedTriangle.aX)
                        query.withinPoint(x, y)
                    }

                    ShapeField.DecodedTriangle.TYPE.LINE -> {
                        val aY = encoder.decodeY(decodedTriangle.aY)
                        val aX = encoder.decodeX(decodedTriangle.aX)
                        val bY = encoder.decodeY(decodedTriangle.bY)
                        val bX = encoder.decodeX(decodedTriangle.bX)
                        query.withinLine(aX, aY, decodedTriangle.ab, bX, bY)
                    }

                    ShapeField.DecodedTriangle.TYPE.TRIANGLE -> {
                        val aY = encoder.decodeY(decodedTriangle.aY)
                        val aX = encoder.decodeX(decodedTriangle.aX)
                        val bY = encoder.decodeY(decodedTriangle.bY)
                        val bX = encoder.decodeX(decodedTriangle.bX)
                        val cY = encoder.decodeY(decodedTriangle.cY)
                        val cX = encoder.decodeX(decodedTriangle.cX)
                        query.withinTriangle(
                            aX,
                            aY,
                            decodedTriangle.ab,
                            bX,
                            bY,
                            decodedTriangle.bc,
                            cX,
                            cY,
                            decodedTriangle.ca
                        )
                    }
                }
                if (relation == Component2D.WithinRelation.NOTWITHIN) {
                    return relation
                } else if (relation == Component2D.WithinRelation.CANDIDATE) {
                    answer = Component2D.WithinRelation.CANDIDATE
                }
            }
            return answer
        }
    }
}
