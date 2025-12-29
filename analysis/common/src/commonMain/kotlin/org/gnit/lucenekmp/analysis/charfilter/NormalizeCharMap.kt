package org.gnit.lucenekmp.analysis.charfilter

import org.gnit.lucenekmp.internal.hppc.CharObjectHashMap
import org.gnit.lucenekmp.jdkport.TreeMap
import org.gnit.lucenekmp.util.CharsRef
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.util.fst.CharSequenceOutputs
import org.gnit.lucenekmp.util.fst.FST
import org.gnit.lucenekmp.util.fst.FSTCompiler
import org.gnit.lucenekmp.util.fst.Outputs
import org.gnit.lucenekmp.util.fst.Util

/**
 * Holds a map of String input to String output, to be used with [MappingCharFilter].
 * Use [Builder] to create this.
 */
class NormalizeCharMap private constructor(val map: FST<CharsRef>?) {
    val cachedRootArcs = CharObjectHashMap<FST.Arc<CharsRef>>()

    init {
        if (map != null) {
            // Pre-cache root arcs:
            val scratchArc = FST.Arc<CharsRef>()
            val fstReader = map.getBytesReader()
            map.getFirstArc(scratchArc)
            if (FST.targetHasArcs(scratchArc)) {
                map.readFirstRealTargetArc(scratchArc.target(), scratchArc, fstReader)
                while (true) {
                    cachedRootArcs.put(scratchArc.label().toChar(), FST.Arc<CharsRef>().copyFrom(scratchArc))
                    if (scratchArc.isLast) {
                        break
                    }
                    map.readNextRealArc(scratchArc, fstReader)
                }
            }
        }
    }

    /**
     * Builds a NormalizeCharMap.
     * Call [add] for all mappings, then [build].
     */
    class Builder {
        private val pendingPairs = TreeMap<String, String>()

        /**
         * Records a replacement to be applied to the input stream.
         * @throws IllegalArgumentException if match is empty or already added.
         */
        fun add(match: String, replacement: String) {
            require(match.isNotEmpty()) { "cannot match the empty string" }
            if (pendingPairs.putIfAbsent(match, replacement) != null) {
                throw IllegalArgumentException("match \"$match\" was already added")
            }
        }

        /** Builds the NormalizeCharMap; call this once you are done calling [add]. */
        fun build(): NormalizeCharMap {
            val map: FST<CharsRef>?
            val outputs: Outputs<CharsRef> = CharSequenceOutputs.singleton
            val fstCompiler = FSTCompiler.Builder(FST.INPUT_TYPE.BYTE2, outputs).build()
            val scratch = IntsRefBuilder()
            for ((key, value) in pendingPairs) {
                fstCompiler.add(Util.toUTF16(key, scratch), CharsRef(value))
            }
            map = FST.fromFSTReader(fstCompiler.compile(), fstCompiler.getFSTReader())
            pendingPairs.clear()
            return NormalizeCharMap(map)
        }
    }
}
