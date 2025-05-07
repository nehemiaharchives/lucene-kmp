package org.gnit.lucenekmp.search

import kotlinx.io.IOException


/**
 * Filter a [Scorable], intercepting methods and optionally changing their return values
 *
 *
 * The default implementation simply passes all calls to its delegate, with the exception of
 * [.setMinCompetitiveScore] which defaults to a no-op.
 */
open class FilterScorable
/**
 * Filter a scorer
 *
 * @param `in` the scorer to filter
 */(protected val `in`: Scorable) : Scorable() {
    @Throws(IOException::class)
    override fun score(): Float {
        return `in`.score()
    }

    override val children: MutableCollection<ChildScorable>
        get() = mutableListOf<ChildScorable>(ChildScorable(`in`, "FILTER"))
}
