package org.gnit.lucenekmp.analysis.ja.dict

import org.gnit.lucenekmp.util.fst.FST

/**
 * Thin wrapper around an FST with root-arc caching for Japanese.
 */
class TokenInfoFST(fst: FST<Long>, fasterButMoreRam: Boolean) :
    org.gnit.lucenekmp.analysis.morph.TokenInfoFST(
        fst,
        if (fasterButMoreRam) 0x9FFF else 0x30FF,
        0x3040
    ) {
    /** @lucene.internal for testing only */
    internal fun getInternalFST(): FST<Long> = fst
}
