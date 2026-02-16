package org.gnit.lucenekmp.document

import okio.IOException
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.XYEncodingUtils
import org.gnit.lucenekmp.geo.XYGeometry
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.PointValues.IntersectVisitor
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.ConstantScoreScorer
import org.gnit.lucenekmp.search.ConstantScoreWeight
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.QueryVisitor
import org.gnit.lucenekmp.search.ScoreMode
import org.gnit.lucenekmp.search.Scorer
import org.gnit.lucenekmp.search.ScorerSupplier
import org.gnit.lucenekmp.search.Weight
import org.gnit.lucenekmp.util.DocIdSetBuilder
import org.gnit.lucenekmp.util.IntsRef
import kotlin.reflect.cast


/**
 * Finds all previously indexed points that fall within the specified XY geometries.
 *
 *
 * The field must be indexed with using [XYPointField] added per document.
 *
 * @lucene.experimental
 */
internal class XYPointInGeometryQuery(field: String?, vararg xyGeometries: XYGeometry) : Query() {
    /** Returns the query field  */
    val field: String?
    val xyGeometries: Array<XYGeometry>

    init {
        requireNotNull(field) { "field must not be null" }
        require(xyGeometries.isNotEmpty()) { "geometries must not be empty" }
        this.field = field
        this.xyGeometries = xyGeometries.copyOf() as Array<XYGeometry>
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            visitor.visitLeaf(this)
        }
    }

    private fun getIntersectVisitor(result: DocIdSetBuilder, tree: Component2D): IntersectVisitor {
        return object : IntersectVisitor {
            var adder: DocIdSetBuilder.BulkAdder? = null

            override fun grow(count: Int) {
                adder = result.grow(count)
            }

            override fun visit(docID: Int) {
                adder!!.add(docID)
            }

            @Throws(IOException::class)
            override fun visit(iterator: DocIdSetIterator) {
                adder!!.add(iterator)
            }

            override fun visit(ref: IntsRef) {
                adder!!.add(ref)
            }

            override fun visit(docID: Int, packedValue: ByteArray) {
                val x = XYEncodingUtils.decode(packedValue, 0).toDouble()
                val y = XYEncodingUtils.decode(packedValue, Int.SIZE_BYTES).toDouble()
                if (tree.contains(x, y)) {
                    visit(docID)
                }
            }

            @Throws(IOException::class)
            override fun visit(iterator: DocIdSetIterator, packedValue: ByteArray) {
                val x = XYEncodingUtils.decode(packedValue, 0).toDouble()
                val y = XYEncodingUtils.decode(packedValue, Int.SIZE_BYTES).toDouble()
                if (tree.contains(x, y)) {
                    adder!!.add(iterator)
                }
            }

            override fun compare(minPackedValue: ByteArray, maxPackedValue: ByteArray): PointValues.Relation {
                val cellMinX = XYEncodingUtils.decode(minPackedValue, 0).toDouble()
                val cellMinY = XYEncodingUtils.decode(minPackedValue, Int.SIZE_BYTES).toDouble()
                val cellMaxX = XYEncodingUtils.decode(maxPackedValue, 0).toDouble()
                val cellMaxY = XYEncodingUtils.decode(maxPackedValue, Int.SIZE_BYTES).toDouble()
                return tree.relate(cellMinX, cellMaxX, cellMinY, cellMaxY)
            }
        }
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        val tree: Component2D = XYGeometry.create(*xyGeometries)

        return object : ConstantScoreWeight(this, boost) {
            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                val reader: LeafReader = context.reader()
                val values: PointValues? = if(field != null) reader.getPointValues(field) else null
                if (values == null) {
                    // No docs in this segment had any points fields
                    return null
                }
                val fieldInfo: FieldInfo? = reader.fieldInfos.fieldInfo(field)
                if (fieldInfo == null) {
                    // No docs in this segment indexed this field at all
                    return null
                }
                XYPointField.checkCompatible(fieldInfo)

                return object : ScorerSupplier() {
                    var cost: Long = -1
                    val result: DocIdSetBuilder = DocIdSetBuilder(reader.maxDoc(), values)
                    val visitor: IntersectVisitor = getIntersectVisitor(result, tree)

                    @Throws(IOException::class)
                    override fun get(leadCost: Long): Scorer {
                        values.intersect(visitor)
                        return ConstantScoreScorer(score(), scoreMode, result.build().iterator())
                    }

                    override fun cost(): Long {
                        if (cost == -1L) {
                            // Computing the cost may be expensive, so only do it if necessary
                            cost = values.estimateDocCount(visitor)
                            assert(cost >= 0)
                        }
                        return cost
                    }
                }
            }

            override fun isCacheable(ctx: LeafReaderContext): Boolean {
                return true
            }
        }
    }

    val geometries: Array<XYGeometry>
        /** Returns a copy of the internal geometries array  */
        get() = xyGeometries.copyOf()

    override fun hashCode(): Int {
        val prime = 31
        var result: Int = classHash()
        result = prime * result + field.hashCode()
        result = prime * result + xyGeometries.contentHashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && equalsTo(this::class.cast(other))
    }

    private fun equalsTo(other: XYPointInGeometryQuery): Boolean {
        return field == other.field && xyGeometries.contentEquals(other.xyGeometries)
    }

    override fun toString(field: String?): String {
        val sb = StringBuilder()
        sb.append(this::class.simpleName)
        sb.append(':')
        if (this.field == field == false) {
            sb.append(" field=")
            sb.append(this.field)
            sb.append(':')
        }
        sb.append(xyGeometries.contentToString())
        return sb.toString()
    }
}
