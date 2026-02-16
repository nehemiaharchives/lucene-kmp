package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.document.ShapeField.QueryRelation
import org.gnit.lucenekmp.geo.Component2D
import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.geo.Geometry
import org.gnit.lucenekmp.geo.LatLonGeometry
import org.gnit.lucenekmp.geo.Line
import org.gnit.lucenekmp.geo.Point
import org.gnit.lucenekmp.index.PointValues.Relation
import org.gnit.lucenekmp.util.NumericUtils

/**
 * Finds all previously indexed geo points that comply the given [QueryRelation] with the
 * specified array of [LatLonGeometry].
 *
 *
 * The field must be indexed using one or more [LatLonPoint] added per document.
 */
internal class LatLonPointQuery
/**
 * Creates a query that matches all indexed shapes to the provided array of [LatLonGeometry]
 */
    (field: String?, queryRelation: QueryRelation, vararg geometries: LatLonGeometry) :
    SpatialQuery(field, queryRelation, *validateGeometry(queryRelation, *geometries)) {
    override fun createComponent2D(vararg geometries: Geometry): Component2D {
        return LatLonGeometry.create(*geometries as Array<LatLonGeometry>)
    }

    override val spatialVisitor: SpatialVisitor
        get() {
            val component2DPredicate: GeoEncodingUtils.Component2DPredicate =
                GeoEncodingUtils.createComponentPredicate(queryComponent2D)
            // bounding box over all geometries, this can speed up tree intersection/cheaply improve
            // approximation for complex multi-geometries
            val minLat: Int = GeoEncodingUtils.encodeLatitude(queryComponent2D.minY)
            val maxLat: Int = GeoEncodingUtils.encodeLatitude(queryComponent2D.maxY)
            val minLon: Int = GeoEncodingUtils.encodeLongitude(queryComponent2D.minX)
            val maxLon: Int = GeoEncodingUtils.encodeLongitude(queryComponent2D.maxX)

            return object : SpatialVisitor() {
                override fun relate(
                    minPackedValue: ByteArray,
                    maxPackedValue: ByteArray
                ): Relation {
                    val latLowerBound: Int = NumericUtils.sortableBytesToInt(minPackedValue, 0)
                    val latUpperBound: Int = NumericUtils.sortableBytesToInt(maxPackedValue, 0)
                    if (latLowerBound > maxLat || latUpperBound < minLat) {
                        // outside of global bounding box range
                        return Relation.CELL_OUTSIDE_QUERY
                    }

                    val lonLowerBound: Int = NumericUtils.sortableBytesToInt(
                        minPackedValue,
                        LatLonPoint.BYTES
                    )
                    val lonUpperBound: Int = NumericUtils.sortableBytesToInt(
                        maxPackedValue,
                        LatLonPoint.BYTES
                    )
                    if (lonLowerBound > maxLon || lonUpperBound < minLon) {
                        // outside of global bounding box range
                        return Relation.CELL_OUTSIDE_QUERY
                    }

                    val cellMinLat: Double = GeoEncodingUtils.decodeLatitude(latLowerBound)
                    val cellMinLon: Double = GeoEncodingUtils.decodeLongitude(lonLowerBound)
                    val cellMaxLat: Double = GeoEncodingUtils.decodeLatitude(latUpperBound)
                    val cellMaxLon: Double = GeoEncodingUtils.decodeLongitude(lonUpperBound)

                    return queryComponent2D.relate(cellMinLon, cellMaxLon, cellMinLat, cellMaxLat)
                }

                override fun intersects(): (ByteArray) -> Boolean /*java.util.function.Predicate<ByteArray>*/ {
                    return { packedValue: ByteArray ->
                        component2DPredicate.test(
                            NumericUtils.sortableBytesToInt(packedValue, 0),
                            NumericUtils.sortableBytesToInt(packedValue, Int.SIZE_BYTES)
                        )
                    }
                }

                override fun within(): (ByteArray) -> Boolean /*java.util.function.Predicate<ByteArray>*/ {
                    return { packedValue: ByteArray ->
                        component2DPredicate.test(
                            NumericUtils.sortableBytesToInt(packedValue, 0),
                            NumericUtils.sortableBytesToInt(packedValue, Int.SIZE_BYTES)
                        )
                    }
                }

                override fun contains(): (ByteArray)-> Component2D.WithinRelation /*java.util.function.Function<ByteArray, Component2D.WithinRelation>*/ {
                    return { packedValue: ByteArray ->
                        queryComponent2D.withinPoint(
                            GeoEncodingUtils.decodeLongitude(
                                NumericUtils.sortableBytesToInt(
                                    packedValue,
                                    Int.SIZE_BYTES
                                )
                            ),
                            GeoEncodingUtils.decodeLatitude(
                                NumericUtils.sortableBytesToInt(
                                    packedValue,
                                    0
                                )
                            )
                        )
                    }
                }
            }
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
        sb.append("[")
        for (i in geometries.indices) {
            sb.append(geometries[i].toString())
            sb.append(',')
        }
        sb.append(']')
        return sb.toString()
    }

    override fun equalsTo(o: Any?): Boolean {
        return super.equalsTo(o) && geometries.contentEquals((o as LatLonPointQuery).geometries)
    }

    override fun hashCode(): Int {
        var hash = super.hashCode()
        hash = 31 * hash + geometries.contentHashCode()
        return hash
    }

    companion object {
        private fun validateGeometry(
            queryRelation: QueryRelation, vararg geometries: LatLonGeometry
        ): Array<LatLonGeometry> {
            if (geometries != null) {
                if (queryRelation == QueryRelation.WITHIN) {
                    for (geometry in geometries) {
                        require(geometry !is Line) {
                            ("LatLonPointQuery does not support "
                                    + QueryRelation.WITHIN
                                    + " queries with line geometries")
                        }
                    }
                }
                if (queryRelation == QueryRelation.CONTAINS) {
                    for (geometry in geometries) {
                        require(geometry is Point) {
                            ("LatLonPointQuery does not support "
                                    + QueryRelation.CONTAINS
                                    + " queries with non-points geometries")
                        }
                    }
                }
            }

            return geometries as Array<LatLonGeometry>
        }
    }
}
