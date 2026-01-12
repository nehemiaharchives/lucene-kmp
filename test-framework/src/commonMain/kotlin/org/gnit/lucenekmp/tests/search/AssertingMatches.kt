package org.gnit.lucenekmp.tests.search

import okio.IOException
import org.gnit.lucenekmp.search.Matches
import org.gnit.lucenekmp.search.MatchesIterator

/** An implementation of [Matches] with additional consistency checks.  */
class AssertingMatches(matches: Matches) :
    Matches {
    private val `in`: Matches = matches

    @Throws(IOException::class)
    override fun getMatches(field: String): MatchesIterator? {
        val mi: MatchesIterator? = `in`.getMatches(field)
        if (mi == null) return null
        return AssertingMatchesIterator(mi)
    }

    override val subMatches: MutableCollection<Matches>
        get() = mutableSetOf(`in`)

    override fun iterator(): Iterator<String> {
        return `in`.iterator()
    }

    companion object {
        fun unWrap(m: Matches): Matches {
            var m: Matches = m
            while (m is AssertingMatches) {
                m = (m.`in`)
            }
            return m
        }
    }
}
