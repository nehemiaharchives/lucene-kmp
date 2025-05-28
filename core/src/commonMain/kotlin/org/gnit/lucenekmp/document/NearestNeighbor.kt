package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.geo.Rectangle;
import org.gnit.lucenekmp.index.PointValues;
import org.gnit.lucenekmp.internal.hppc.IntArrayList;
import org.gnit.lucenekmp.util.Bits;
import org.gnit.lucenekmp.util.SloppyMath;
import okio.IOException
import org.gnit.lucenekmp.geo.GeoEncodingUtils
import org.gnit.lucenekmp.jdkport.PriorityQueue
import org.gnit.lucenekmp.jdkport.compare
import kotlin.math.min

/** KNN search on top of 2D lat/lon indexed points.  */
internal object NearestNeighbor {
    // TODO: can we somehow share more with, or simply directly use, the
    // LatLonPointDistanceComparator  It's really doing the same thing as
    // our hitQueue...
    @Throws(IOException::class)
    fun nearest(
        pointLat: Double,
        pointLon: Double,
        readers: MutableList<PointValues>,
        liveDocs: MutableList<Bits>,
        docBases: IntArrayList,
        n: Int
    ): Array<NearestHit> {
        // System.out.println("NEAREST: readers=" + readers + " liveDocs=" + liveDocs + " pointLat=" +
        // pointLat + " pointLon=" + pointLon);
        // Holds closest collected points seen so far:
        // TODO: if we used lucene's PQ we could just updateTop instead of poll/offer:

        val hitQueue: PriorityQueue<NearestHit> =
            PriorityQueue(
                n,
                Comparator{ a: NearestHit, b: NearestHit ->
                    // sort by opposite distanceSortKey natural order
                    val cmp: Int = Double.compare(a.distanceSortKey, b.distanceSortKey)
                    if (cmp != 0) {
                        return@Comparator -cmp
                    }
                    b.docID - a.docID
                })

        // Holds all cells, sorted by closest to the point:
        val cellQueue: PriorityQueue<Cell> = PriorityQueue<Cell>()

        val visitor = NearestVisitor(hitQueue, n, pointLat, pointLon)

        // Add root cell for each reader into the queue:
        for (i in readers.indices) {
            val reader: PointValues = readers.get(i)
            val minPackedValue: ByteArray = reader.minPackedValue
            val maxPackedValue: ByteArray = reader.maxPackedValue
            val indexTree: PointValues.PointTree = reader.pointTree

            cellQueue.offer(
                Cell(
                    indexTree,
                    i,
                    reader.minPackedValue,
                    reader.maxPackedValue,
                    approxBestDistance(minPackedValue, maxPackedValue, pointLat, pointLon)
                )
            )
        }

        while (cellQueue.size() > 0) {
            val cell: Cell? = cellQueue.poll()
            // System.out.println("  visit " + cell);
            if (visitor.compare(
                    cell!!.minPacked,
                    cell.maxPacked
                ) == PointValues.Relation.CELL_OUTSIDE_QUERY
            ) {
                continue
            }

            // TODO: if we replace approxBestDistance with actualBestDistance, we can put an opto here to
            // break once this "best" cell is fully outside of the hitQueue bottom's radius:
            if (cell.index.moveToChild() == false) {
                // System.out.println("    leaf");
                // Leaf block: visit all points and possibly collect them:
                visitor.curDocBase = docBases.get(cell.readerIndex)
                visitor.curLiveDocs = liveDocs[cell.readerIndex]
                cell.index.visitDocValues(visitor)
                // System.out.println("    now " + hitQueue.size() + " hits");
            } else {
                // System.out.println("    non-leaf");
                // Non-leaf block: split into two cells and put them back into the queue:

                // we must clone the index so that we can recurse left and right "concurrently":

                val newIndex: PointValues.PointTree = cell.index.clone()

                cellQueue.offer(
                    Cell(
                        newIndex,
                        cell.readerIndex,
                        newIndex.minPackedValue,
                        newIndex.maxPackedValue,
                        approxBestDistance(
                            newIndex.minPackedValue,
                            newIndex.maxPackedValue,
                            pointLat,
                            pointLon
                        )
                    )
                )

                // TODO: we are assuming a binary tree
                if (cell.index.moveToSibling()) {
                    cellQueue.offer(
                        Cell(
                            cell.index,
                            cell.readerIndex,
                            cell.index.minPackedValue,
                            cell.index.maxPackedValue,
                            approxBestDistance(
                                cell.index.minPackedValue,
                                cell.index.maxPackedValue,
                                pointLat,
                                pointLon
                            )
                        )
                    )
                }
            }
        }

        val hits = kotlin.arrayOfNulls<NearestHit>(hitQueue.size())
        var downTo: Int = hitQueue.size() - 1
        while (hitQueue.size() != 0) {
            hits[downTo] = hitQueue.poll()
            downTo--
        }

        return hits as Array<NearestHit>
    }

    // NOTE: incoming args never cross the dateline, since they are a BKD cell
    private fun approxBestDistance(
        minPackedValue: ByteArray, maxPackedValue: ByteArray, pointLat: Double, pointLon: Double
    ): Double {
        val minLat: Double = GeoEncodingUtils.decodeLatitude(minPackedValue, 0)
        val minLon: Double =
            GeoEncodingUtils.decodeLongitude(minPackedValue, Int.SIZE_BYTES)
        val maxLat: Double = GeoEncodingUtils.decodeLatitude(maxPackedValue, 0)
        val maxLon: Double =
            GeoEncodingUtils.decodeLongitude(maxPackedValue, Int.SIZE_BYTES)
        return approxBestDistance(minLat, maxLat, minLon, maxLon, pointLat, pointLon)
    }

    // NOTE: incoming args never cross the dateline, since they are a BKD cell
    private fun approxBestDistance(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        pointLat: Double,
        pointLon: Double
    ): Double {
        // TODO: can we make this the trueBestDistance  I.e., minimum distance between the point and
        // ANY point on the box  we can speed things
        // up if so, but not enrolling any BKD cell whose true best distance is > bottom of the current
        // hit queue

        if (pointLat >= minLat && pointLat <= maxLat && pointLon >= minLon && pointLon <= maxLon) {
            // point is inside the cell!
            return 0.0
        }

        val d1: Double = SloppyMath.haversinSortKey(pointLat, pointLon, minLat, minLon)
        val d2: Double = SloppyMath.haversinSortKey(pointLat, pointLon, minLat, maxLon)
        val d3: Double = SloppyMath.haversinSortKey(pointLat, pointLon, maxLat, maxLon)
        val d4: Double = SloppyMath.haversinSortKey(pointLat, pointLon, maxLat, minLon)
        return min(min(d1, d2), min(d3, d4))
    }

    /**
     * @param distanceSortKey The closest distance from a point in this cell to the query point,
     * computed as a sort key through [SloppyMath.haversinSortKey]. Note that this is an
     * approximation to the closest distance, and there could be a point in the cell that is
     * closer.
     */
    internal class Cell(
        index: PointValues.PointTree,
        val readerIndex: Int,
        minPacked: ByteArray,
        maxPacked: ByteArray,
        val distanceSortKey: Double
    ) : Comparable<Cell> {
        override fun compareTo(other: Cell): Int {
            return Double.compare(distanceSortKey, other.distanceSortKey)
        }

        override fun toString(): String {
            val minLat: Double = GeoEncodingUtils.decodeLatitude(minPacked, 0)
            val minLon: Double =
                GeoEncodingUtils.decodeLongitude(minPacked, Int.SIZE_BYTES)
            val maxLat: Double = GeoEncodingUtils.decodeLatitude(maxPacked, 0)
            val maxLon: Double =
                GeoEncodingUtils.decodeLongitude(maxPacked, Int.SIZE_BYTES)
            return ("Cell(readerIndex="
                    + readerIndex
                    + " "
                    + index.toString()
                    + " lat="
                    + minLat
                    + " TO "
                    + maxLat
                    + ", lon="
                    + minLon
                    + " TO "
                    + maxLon
                    + "; distanceSortKey="
                    + distanceSortKey
                    + ")")
        }

        val index: PointValues.PointTree
        val minPacked: ByteArray
        val maxPacked: ByteArray

        init {
            this.index = index
            var minPacked = minPacked
            var maxPacked = maxPacked
            minPacked = minPacked.copyOf()
            maxPacked = maxPacked.copyOf()
            this.minPacked = minPacked
            this.maxPacked = maxPacked
        }
    }

    private class NearestVisitor(
        hitQueue: PriorityQueue<NearestHit>,
        topN: Int,
        pointLat: Double,
        pointLon: Double
    ) : PointValues.IntersectVisitor {
        var curDocBase: Int = 0
        var curLiveDocs: Bits? = null
        val topN: Int
        val hitQueue: PriorityQueue<NearestHit>
        val pointLat: Double
        val pointLon: Double
        private var setBottomCounter = 0

        private var minLon = Double.Companion.NEGATIVE_INFINITY
        private var maxLon = Double.Companion.POSITIVE_INFINITY
        private var minLat = Double.Companion.NEGATIVE_INFINITY
        private var maxLat = Double.Companion.POSITIVE_INFINITY

        // second set of longitude ranges to check (for cross-dateline case)
        private var minLon2 = Double.Companion.POSITIVE_INFINITY

        init {
            this.hitQueue = hitQueue
            this.topN = topN
            this.pointLat = pointLat
            this.pointLon = pointLon
        }

        override fun visit(docID: Int) {
            throw AssertionError()
        }

        fun maybeUpdateBBox() {
            if (setBottomCounter < 1024 || (setBottomCounter and 0x3F) == 0x3F) {
                val hit: NearestHit? = hitQueue.peek()
                val box: Rectangle =
                    Rectangle.fromPointDistance(
                        pointLat, pointLon, SloppyMath.haversinMeters(hit!!.distanceSortKey)
                    )
                // System.out.println("    update bbox to " + box);
                minLat = box.minLat
                maxLat = box.maxLat
                if (box.crossesDateline()) {
                    // box1
                    minLon = Double.Companion.NEGATIVE_INFINITY
                    maxLon = box.maxLon
                    // box2
                    minLon2 = box.minLon
                } else {
                    minLon = box.minLon
                    maxLon = box.maxLon
                    // disable box2
                    minLon2 = Double.Companion.POSITIVE_INFINITY
                }
            }
            setBottomCounter++
        }

        override fun visit(docID: Int, packedValue: ByteArray) {
            // System.out.println("visit docID=" + docID + " liveDocs=" + curLiveDocs);

            if (curLiveDocs != null && curLiveDocs!!.get(docID) == false) {
                return
            }

            val docLatitude: Double = GeoEncodingUtils.decodeLatitude(packedValue, 0)
            val docLongitude: Double =
                GeoEncodingUtils.decodeLongitude(packedValue, Int.SIZE_BYTES)

            // test bounding box
            if (docLatitude < minLat || docLatitude > maxLat) {
                return
            }
            if ((docLongitude < minLon || docLongitude > maxLon) && (docLongitude < minLon2)) {
                return
            }

            // Use the haversin sort key when comparing hits, as it is faster to compute than the true
            // distance.
            val distanceSortKey: Double =
                SloppyMath.haversinSortKey(pointLat, pointLon, docLatitude, docLongitude)

            // System.out.println("    visit docID=" + docID + " distanceSortKey=" + distanceSortKey + "
            // docLat=" + docLatitude + " docLon=" + docLongitude);
            val fullDocID = curDocBase + docID

            if (hitQueue.size() == topN) {
                // queue already full
                val hit: NearestHit? = hitQueue.peek()
                // System.out.println("      bottom distanceSortKey=" + hit.distanceSortKey);
                // we don't collect docs in order here, so we must also test the tie-break case ourselves:
                if (distanceSortKey < hit!!.distanceSortKey
                    || (distanceSortKey == hit.distanceSortKey && fullDocID < hit.docID)
                ) {
                    hitQueue.poll()
                    hit.docID = fullDocID
                    hit.distanceSortKey = distanceSortKey
                    hitQueue.offer(hit)
                    // System.out.println("      ** keep2, now bottom=" + hit);
                    maybeUpdateBBox()
                }
            } else {
                val hit = NearestHit()
                hit.docID = fullDocID
                hit.distanceSortKey = distanceSortKey
                hitQueue.offer(hit)
                // System.out.println("      ** keep1, now bottom=" + hit);
            }
        }

        override fun compare(
            minPackedValue: ByteArray,
            maxPackedValue: ByteArray
        ): PointValues.Relation {
            val cellMinLat: Double = GeoEncodingUtils.decodeLatitude(minPackedValue, 0)
            val cellMinLon: Double =
                GeoEncodingUtils.decodeLongitude(minPackedValue, Int.SIZE_BYTES)
            val cellMaxLat: Double = GeoEncodingUtils.decodeLatitude(maxPackedValue, 0)
            val cellMaxLon: Double =
                GeoEncodingUtils.decodeLongitude(maxPackedValue, Int.SIZE_BYTES)

            if (cellMaxLat < minLat || maxLat < cellMinLat || ((cellMaxLon < minLon || maxLon < cellMinLon) && cellMaxLon < minLon2)) {
                // this cell is outside our search bbox; don't bother exploring any more
                return PointValues.Relation.CELL_OUTSIDE_QUERY
            }
            return PointValues.Relation.CELL_CROSSES_QUERY
        }
    }

    /** Holds one hit from [NearestNeighbor.nearest]  */
    internal class NearestHit {
        var docID: Int = 0

        /**
         * The distance from the hit to the query point, computed as a sort key through [ ][SloppyMath.haversinSortKey].
         */
        var distanceSortKey: Double = 0.0

        override fun toString(): String {
            return "NearestHit(docID=" + docID + " distanceSortKey=" + distanceSortKey + ")"
        }
    }
}
