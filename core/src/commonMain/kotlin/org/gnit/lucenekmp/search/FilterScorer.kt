package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.util.Unwrappable
import okio.IOException

/**
 * A `FilterScorer` contains another `Scorer`, which it uses as its basic source of
 * data, possibly transforming the data along the way or providing additional functionality. The
 * class `FilterScorer` itself simply implements all abstract methods of `Scorer` with
 * versions that pass all requests to the contained scorer. Subclasses of `FilterScorer` may
 * further override some of these methods and may also provide additional methods and fields.
 */
abstract class FilterScorer(protected open val `in`: Scorer) : Scorer(), Unwrappable<Scorer> {

    @Throws(IOException::class)
    override fun score(): Float {
        return `in`.score()
    }

    // Leave maxScore abstract on purpose since the goal of this Filter class is
    // to change the way the score is computed.
    override fun docID(): Int {
        return `in`.docID()
    }

    override fun iterator(): DocIdSetIterator {
        return `in`.iterator()
    }

    override fun twoPhaseIterator(): TwoPhaseIterator {
        return `in`.twoPhaseIterator()!!
    }

    override fun unwrap(): Scorer {
        return `in`
    }
}
