package org.gnit.lucenekmp.analysis.ko.dict

import org.gnit.lucenekmp.util.fst.FST

/** Thin wrapper around an FST with root-arc caching for Hangul syllables (11,172 arcs). */
class TokenInfoFST(fst: FST<Long>) : org.gnit.lucenekmp.analysis.morph.TokenInfoFST(fst, 0xD7A3, 0xAC00) {
    /**
     * @lucene.internal for testing only
     */
    internal fun getInternalFST(): FST<Long> = fst
}
