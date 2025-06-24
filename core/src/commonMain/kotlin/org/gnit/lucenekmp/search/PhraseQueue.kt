package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.util.PriorityQueue

class PhraseQueue(size: Int) : PriorityQueue<PhrasePositions>(size) {
    override fun lessThan(pp1: PhrasePositions, pp2: PhrasePositions): Boolean {
        if (pp1.position == pp2.position)  // same doc and pp.position, so decide by actual term positions.
        // rely on: pp.position == tp.position - offset.
            if (pp1.offset == pp2.offset) {
                return pp1.ord < pp2.ord
            } else {
                return pp1.offset < pp2.offset
            }
        else {
            return pp1.position < pp2.position
        }
    }
}
