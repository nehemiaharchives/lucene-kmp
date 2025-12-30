package org.gnit.lucenekmp.analysis.morph

import org.gnit.lucenekmp.util.fst.FST
import okio.IOException

/**
 * Thin wrapper around an FST with root-arc caching.
 *
 * Root arcs between cacheFloor and cacheCeiling are cached.
 */
abstract class TokenInfoFST(
    protected val fst: FST<Long>,
    private val cacheCeiling: Int,
    private val cacheFloor: Int
) {
    val NO_OUTPUT: Long

    private val rootCache: Array<FST.Arc<Long>?>

    init {
        require(cacheCeiling >= cacheFloor) {
            "cacheCeiling must be larger than cacheFloor; cacheCeiling=$cacheCeiling, cacheFloor=$cacheFloor"
        }
        NO_OUTPUT = fst.outputs.noOutput
        rootCache = cacheRootArcs()
    }

    @Throws(IOException::class)
    private fun cacheRootArcs(): Array<FST.Arc<Long>?> {
        val rootCache = arrayOfNulls<FST.Arc<Long>>(1 + (cacheCeiling - cacheFloor))
        val firstArc = FST.Arc<Long>()
        fst.getFirstArc(firstArc)
        val arc = FST.Arc<Long>()
        val fstReader = fst.getBytesReader()
        for (i in rootCache.indices) {
            if (fst.findTargetArc(cacheFloor + i, firstArc, arc, fstReader) != null) {
                rootCache[i] = FST.Arc<Long>().copyFrom(arc)
            }
        }
        return rootCache
    }

    @Throws(IOException::class)
    fun findTargetArc(
        ch: Int,
        follow: FST.Arc<Long>,
        arc: FST.Arc<Long>,
        useCache: Boolean,
        fstReader: FST.BytesReader
    ): FST.Arc<Long>? {
        if (useCache && ch >= cacheFloor && ch <= cacheCeiling) {
            val result = rootCache[ch - cacheFloor]
            return if (result == null) {
                null
            } else {
                arc.copyFrom(result)
                arc
            }
        }
        return fst.findTargetArc(ch, follow, arc, fstReader)
    }

    fun getFirstArc(arc: FST.Arc<Long>): FST.Arc<Long> = fst.getFirstArc(arc)

    fun getBytesReader(): FST.BytesReader = fst.getBytesReader()
}
