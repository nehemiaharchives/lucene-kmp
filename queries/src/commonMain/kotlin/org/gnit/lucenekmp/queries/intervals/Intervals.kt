/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gnit.lucenekmp.queries.intervals

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CachingTokenFilter
import org.gnit.lucenekmp.analysis.TokenStream
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.FuzzyQuery
import org.gnit.lucenekmp.search.PrefixQuery
import org.gnit.lucenekmp.search.RegexpQuery
import org.gnit.lucenekmp.search.TermRangeQuery
import org.gnit.lucenekmp.search.WildcardQuery
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.automaton.Automaton
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton
import org.gnit.lucenekmp.util.automaton.LevenshteinAutomata
import org.gnit.lucenekmp.util.automaton.Operations
import org.gnit.lucenekmp.util.automaton.RegExp

/**
 * Factory functions for creating [IntervalsSource interval sources].
 *
 * <p>These sources implement minimum-interval algorithms taken from the paper
 * [Efficient Optimally Lazy Algorithms for Minimal-Interval Semantics](https://vigna.di.unimi.it/ftp/papers/EfficientLazy.pdf)
 *
 * <p><em>Note:</em> by default, sources that are sensitive to internal gaps (e.g. `PHRASE`
 * and `MAXGAPS`) will rewrite their sub-sources so that disjunctions of different lengths are
 * pulled up to the top of the interval tree. For example, `PHRASE(or(PHRASE("a", "b", "c"),
 * "b"), "c")` will automatically rewrite itself to `OR(PHRASE("a", "b", "c", "c"),
 * PHRASE("b", "c"))` to ensure that documents containing `"b c"` are matched. This can lead
 * to less efficient queries, as more terms need to be loaded (for example, the `"c"` iterator
 * above is loaded twice), so if you care more about speed than about accuracy you can use the
 * [or] factory method to prevent rewriting.
 */
object Intervals {
    /**
     * The default number of expansions in:
     *
     * <ul>
     *   <li>[multiterm]
     * </ul>
     */
    const val DEFAULT_MAX_EXPANSIONS = 128

    /** Return an [IntervalsSource] exposing intervals for a term */
    fun term(term: BytesRef): IntervalsSource {
        return TermIntervalsSource(term)
    }

    /** Return an [IntervalsSource] exposing intervals for a term */
    fun term(term: String): IntervalsSource {
        return TermIntervalsSource(BytesRef(term))
    }

    /**
     * Return an [IntervalsSource] exposing intervals for a term, filtered by the value of the
     * term's payload at each position
     */
    fun term(term: String, payloadFilter: (BytesRef?) -> Boolean): IntervalsSource {
        return term(BytesRef(term), payloadFilter)
    }

    /**
     * Return an [IntervalsSource] exposing intervals for a term, filtered by the value of the
     * term's payload at each position
     */
    fun term(term: BytesRef, payloadFilter: (BytesRef?) -> Boolean): IntervalsSource {
        return PayloadFilteredTermIntervalsSource(term, payloadFilter)
    }

    /**
     * Return an [IntervalsSource] exposing intervals for a phrase consisting of a list of terms
     */
    fun phrase(vararg terms: String): IntervalsSource {
        if (terms.size == 1) {
            return term(terms[0])
        }
        val sources = arrayOfNulls<IntervalsSource>(terms.size)
        var i = 0
        for (term in terms) {
            sources[i] = term(term)
            i++
        }
        return phrase(*sources.requireNoNulls())
    }

    /**
     * Return an [IntervalsSource] exposing intervals for a phrase consisting of a list of
     * [IntervalsSource interval sources]
     */
    fun phrase(vararg subSources: IntervalsSource): IntervalsSource {
        return BlockIntervalsSource.build(subSources.asList())
    }

    /**
     * Return an [IntervalsSource] over the disjunction of a set of sub-sources
     *
     * <p>Automatically rewrites if wrapped by an interval source that is sensitive to internal gaps
     */
    fun or(vararg subSources: IntervalsSource): IntervalsSource {
        return or(true, subSources.asList())
    }

    /**
     * Return an [IntervalsSource] over the disjunction of a set of sub-sources
     *
     * @param rewrite if `false`, do not rewrite intervals that are sensitive to internal gaps;
     *     this may run more efficiently, but can miss valid hits due to minimization
     * @param subSources the sources to combine
     */
    fun or(rewrite: Boolean, vararg subSources: IntervalsSource): IntervalsSource {
        return or(rewrite, subSources.asList())
    }

    /** Return an [IntervalsSource] over the disjunction of a set of sub-sources */
    fun or(subSources: List<IntervalsSource>): IntervalsSource {
        return or(true, subSources)
    }

    /**
     * Return an [IntervalsSource] over the disjunction of a set of sub-sources
     *
     * @param rewrite if `false`, do not rewrite intervals that are sensitive to internal gaps;
     *     this may run more efficiently, but can miss valid hits due to minimization
     * @param subSources the sources to combine
     */
    fun or(rewrite: Boolean, subSources: List<IntervalsSource>): IntervalsSource {
        return DisjunctionIntervalsSource.create(subSources, rewrite)
    }

    /**
     * Return an [IntervalsSource] over the disjunction of all terms that begin with a prefix
     *
     * @throws IllegalStateException if the prefix expands to more than [DEFAULT_MAX_EXPANSIONS]
     *     terms
     */
    fun prefix(prefix: BytesRef): IntervalsSource {
        return prefix(prefix, DEFAULT_MAX_EXPANSIONS)
    }

    /**
     * Expert: Return an [IntervalsSource] over the disjunction of all terms that begin with a
     * prefix
     *
     * <p>WARNING: Setting `maxExpansions` to higher than the default value of
     * [DEFAULT_MAX_EXPANSIONS] can be both slow and memory-intensive
     *
     * @param prefix the prefix to expand
     * @param maxExpansions the maximum number of terms to expand to
     * @throws IllegalStateException if the prefix expands to more than `maxExpansions` terms
     */
    fun prefix(prefix: BytesRef, maxExpansions: Int): IntervalsSource {
        val ca = CompiledAutomaton(PrefixQuery.toAutomaton(prefix), false, true, true)
        return MultiTermIntervalsSource(ca, maxExpansions, prefix.utf8ToString() + "*")
    }

    /**
     * Return an [IntervalsSource] over the disjunction of all terms that match a wildcard glob
     *
     * @throws IllegalStateException if the wildcard glob expands to more than
     *     [DEFAULT_MAX_EXPANSIONS] terms
     * @see WildcardQuery for glob format
     */
    fun wildcard(wildcard: BytesRef): IntervalsSource {
        return wildcard(wildcard, DEFAULT_MAX_EXPANSIONS)
    }

    /**
     * Expert: Return an [IntervalsSource] over the disjunction of all terms that match a wildcard
     * glob
     *
     * <p>WARNING: Setting `maxExpansions` to higher than the default value of
     * [DEFAULT_MAX_EXPANSIONS] can be both slow and memory-intensive
     *
     * @param wildcard the glob to expand
     * @param maxExpansions the maximum number of terms to expand to
     * @throws IllegalStateException if the wildcard glob expands to more than `maxExpansions`
     *     terms
     * @see WildcardQuery for glob format
     */
    fun wildcard(wildcard: BytesRef, maxExpansions: Int): IntervalsSource {
        val ca =
            CompiledAutomaton(
                WildcardQuery.toAutomaton(
                    Term("", wildcard), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT
                )
            )
        return MultiTermIntervalsSource(ca, maxExpansions, wildcard.utf8ToString())
    }

    /**
     * Return an [IntervalsSource] over the disjunction of all terms that match a regular expression
     *
     * @param regexp regular expression
     * @throws IllegalStateException if the regex expands to more than [DEFAULT_MAX_EXPANSIONS]
     *     terms
     * @see RegexpQuery for regexp format
     */
    fun regexp(regexp: BytesRef): IntervalsSource {
        return regexp(regexp, DEFAULT_MAX_EXPANSIONS)
    }

    /**
     * Expert: Return an [IntervalsSource] over the disjunction of all terms that match a regular
     * expression
     *
     * <p>WARNING: Setting `maxExpansions` to higher than the default value of
     * [DEFAULT_MAX_EXPANSIONS] can be both slow and memory-intensive
     *
     * @param regexp regular expression
     * @param maxExpansions the maximum number of terms to expand to
     * @throws IllegalStateException if the regex expands to more than [DEFAULT_MAX_EXPANSIONS]
     *     terms
     * @see RegexpQuery for regexp format
     */
    fun regexp(regexp: BytesRef, maxExpansions: Int): IntervalsSource {
        var automaton: Automaton = RegExp(Term("", regexp).text()).toAutomaton()
        automaton =
            Operations.determinize(automaton, Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)
        val ca = CompiledAutomaton(automaton, false, true, false)
        return MultiTermIntervalsSource(ca, maxExpansions, regexp.utf8ToString())
    }

    /**
     * Return an [IntervalsSource] over the disjunction of all terms that fall within the given range
     *
     * @param lowerTerm The term text at the lower end of the range; can be `null` to indicate an
     *     open-ended range at this end
     * @param upperTerm The term text at the upper end of the range; can be `null` to indicate an
     *     open-ended range at this end
     * @param includeLower If true, the `lowerTerm` is included in the range
     * @param includeUpper If true, the `upperTerm` is included in the range
     * @throws IllegalStateException if the range expands to more than [DEFAULT_MAX_EXPANSIONS]
     *     terms
     */
    fun range(
        lowerTerm: BytesRef?,
        upperTerm: BytesRef?,
        includeLower: Boolean,
        includeUpper: Boolean
    ): IntervalsSource {
        return range(lowerTerm, upperTerm, includeLower, includeUpper, DEFAULT_MAX_EXPANSIONS)
    }

    /**
     * Expert: Return an [IntervalsSource] over the disjunction of all terms that fall within the
     * given range
     *
     * <p>WARNING: Setting `maxExpansions` to higher than the default value of
     * [DEFAULT_MAX_EXPANSIONS] can be both slow and memory-intensive
     *
     * @param lowerTerm The term text at the lower end of the range; can be `null` to indicate an
     *     open-ended range at this end
     * @param upperTerm The term text at the upper end of the range; can be `null` to indicate an
     *     open-ended range at this end
     * @param includeLower If true, the `lowerTerm` is included in the range
     * @param includeUpper If true, the `upperTerm` is included in the range
     * @param maxExpansions the maximum number of terms to expand to
     * @throws IllegalStateException if the wildcard glob expands to more than `maxExpansions`
     *     terms
     */
    fun range(
        lowerTerm: BytesRef?,
        upperTerm: BytesRef?,
        includeLower: Boolean,
        includeUpper: Boolean,
        maxExpansions: Int
    ): IntervalsSource {
        val automaton: Automaton =
            TermRangeQuery.toAutomaton(lowerTerm, upperTerm, includeLower, includeUpper)
        val ca = CompiledAutomaton(automaton, false, true, true)

        val buffer = StringBuilder()
        buffer.append("{")
        buffer.append(if (lowerTerm == null) "* " else lowerTerm.utf8ToString())
        buffer.append(",")
        buffer.append(if (upperTerm == null) "*" else upperTerm.utf8ToString())
        buffer.append("}")
        return MultiTermIntervalsSource(ca, maxExpansions, buffer.toString())
    }

    /**
     * A fuzzy term [IntervalsSource] matches the disjunction of intervals of terms that are within
     * the specified `maxEdits` from the provided term.
     *
     * @see fuzzyTerm
     * @param term the term to search for
     * @param maxEdits must be `>= 0` and `<=` [LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE], use
     *     [FuzzyQuery.defaultMaxEdits] for the default, if needed.
     */
    fun fuzzyTerm(term: String, maxEdits: Int): IntervalsSource {
        return fuzzyTerm(
            term,
            maxEdits,
            FuzzyQuery.defaultPrefixLength,
            FuzzyQuery.defaultTranspositions,
            DEFAULT_MAX_EXPANSIONS
        )
    }

    /**
     * A fuzzy term [IntervalsSource] matches the disjunction of intervals of terms that are within
     * the specified `maxEdits` from the provided term.
     *
     * <p>The implementation is delegated to a [multiterm] interval source, with an automaton sourced
     * from [FuzzyQuery].
     *
     * @param term the term to search for
     * @param maxEdits must be `>= 0` and `<=` [LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE], use
     *     [FuzzyQuery.defaultMaxEdits] for the default, if needed.
     * @param prefixLength length of common (non-fuzzy) prefix
     * @param maxExpansions the maximum number of terms to match. Setting `maxExpansions` to higher
     *     than the default value of [DEFAULT_MAX_EXPANSIONS] can be both slow and memory-intensive
     * @param transpositions true if transpositions should be treated as a primitive edit operation.
     *     If this is false, comparisons will implement the classic Levenshtein algorithm.
     */
    fun fuzzyTerm(
        term: String,
        maxEdits: Int,
        prefixLength: Int,
        transpositions: Boolean,
        maxExpansions: Int
    ): IntervalsSource {
        return multiterm(
            FuzzyQuery.getFuzzyAutomaton(term, maxEdits, prefixLength, transpositions),
            maxExpansions,
            term + "~" + maxEdits
        )
    }

    /**
     * Expert: Return an [IntervalsSource] over the disjunction of all terms that are accepted by the
     * given automaton
     *
     * @param ca an automaton accepting matching terms
     * @param pattern string representation of the given automaton, mostly used in exception messages
     * @throws IllegalStateException if the automaton accepts more than [DEFAULT_MAX_EXPANSIONS]
     *     terms
     */
    fun multiterm(ca: CompiledAutomaton, pattern: String): IntervalsSource {
        return multiterm(ca, DEFAULT_MAX_EXPANSIONS, pattern)
    }

    /**
     * Expert: Return an [IntervalsSource] over the disjunction of all terms that are accepted by the
     * given automaton
     *
     * <p>WARNING: Setting `maxExpansions` to higher than the default value of
     * [DEFAULT_MAX_EXPANSIONS] can be both slow and memory-intensive
     *
     * @param ca an automaton accepting matching terms
     * @param maxExpansions the maximum number of terms to expand to
     * @param pattern string representation of the given automaton, mostly used in exception messages
     * @throws IllegalStateException if the automaton accepts more than `maxExpansions` terms
     */
    fun multiterm(
        ca: CompiledAutomaton,
        maxExpansions: Int,
        pattern: String
    ): IntervalsSource {
        return MultiTermIntervalsSource(ca, maxExpansions, pattern)
    }

    /**
     * Create an [IntervalsSource] that filters a sub-source by the width of its intervals
     *
     * @param width the maximum width of intervals in the sub-source to filter
     * @param subSource the sub-source to filter
     */
    fun maxwidth(width: Int, subSource: IntervalsSource): IntervalsSource {
        return FilteredIntervalsSource.maxWidth(subSource, width)
    }

    /**
     * Create an [IntervalsSource] that filters a sub-source by its gaps
     *
     * @param gaps the maximum number of gaps in the sub-source to filter
     * @param subSource the sub-source to filter
     */
    fun maxgaps(gaps: Int, subSource: IntervalsSource): IntervalsSource {
        return FilteredIntervalsSource.maxGaps(subSource, gaps)
    }

    /**
     * Create an [IntervalsSource] that wraps another source, extending its intervals by a number of
     * positions before and after.
     *
     * <p>This can be useful for adding defined gaps in a block query; for example, to find 'a b [2
     * arbitrary terms] c', you can call:
     *
     * <pre>
     *   Intervals.phrase(Intervals.term("a"), Intervals.extend(Intervals.term("b"), 0, 2), Intervals.term("c"));
     * </pre>
     *
     * Note that calling [IntervalIterator.gaps] on iterators returned by this source delegates
     * directly to the wrapped iterator, and does not include the extensions.
     *
     * @param source the source to extend
     * @param before how many positions to extend before the delegated interval
     * @param after how many positions to extend after the delegated interval
     */
    fun extend(source: IntervalsSource, before: Int, after: Int): IntervalsSource {
        return ExtendedIntervalsSource(source, before, after)
    }

    /**
     * Create an ordered [IntervalsSource]
     *
     * <p>Returns intervals in which the subsources all appear in the given order
     *
     * @param subSources an ordered set of [IntervalsSource] objects
     */
    fun ordered(vararg subSources: IntervalsSource): IntervalsSource {
        return OrderedIntervalsSource.build(subSources.asList())
    }

    /**
     * Create an unordered [IntervalsSource]. Note that if there are multiple intervals ends at the
     * same position are eligible, only the narrowest one will be returned. For example if asking for
     * `unordered(term("apple"), term("banana"))` on field of "apple wolf apple orange banana", only
     * the "apple orange banana" will be returned.
     *
     * <p>Returns intervals in which all the subsources appear. The subsources may overlap
     *
     * @param subSources an unordered set of [IntervalsSource]s
     */
    fun unordered(vararg subSources: IntervalsSource): IntervalsSource {
        return UnorderedIntervalsSource.build(subSources.asList())
    }

    /**
     * Create an unordered [IntervalsSource] allowing no overlaps between subsources
     *
     * <p>Returns intervals in which both the subsources appear and do not overlap.
     */
    fun unorderedNoOverlaps(a: IntervalsSource, b: IntervalsSource): IntervalsSource {
        return or(ordered(a, b), ordered(b, a))
    }

    /**
     * Create an [IntervalsSource] that always returns intervals from a specific field
     *
     * <p>This is useful for comparing intervals across multiple fields, for example fields that have
     * been analyzed differently, allowing you to search for stemmed terms near unstemmed terms, etc.
     */
    fun fixField(field: String, source: IntervalsSource): IntervalsSource {
        return FixedFieldIntervalsSource(field, source)
    }

    /**
     * Create a non-overlapping IntervalsSource
     *
     * <p>Returns intervals of the minuend that do not overlap with intervals from the subtrahend
     *
     * @param minuend the [IntervalsSource] to filter
     * @param subtrahend the [IntervalsSource] to filter by
     */
    fun nonOverlapping(minuend: IntervalsSource, subtrahend: IntervalsSource): IntervalsSource {
        return NonOverlappingIntervalsSource(minuend, subtrahend)
    }

    /**
     * Returns intervals from a source that overlap with intervals from another source
     *
     * @param source the source to filter
     * @param reference the source to filter by
     */
    fun overlapping(source: IntervalsSource, reference: IntervalsSource): IntervalsSource {
        return OverlappingIntervalsSource(source, reference)
    }

    /**
     * Create a not-within [IntervalsSource]
     *
     * <p>Returns intervals of the minuend that do not appear within a set number of positions of
     * intervals from the subtrahend query
     *
     * @param minuend the [IntervalsSource] to filter
     * @param positions the minimum distance that intervals from the minuend may occur from intervals
     *     of the subtrahend
     * @param subtrahend the [IntervalsSource] to filter by
     */
    fun notWithin(
        minuend: IntervalsSource,
        positions: Int,
        subtrahend: IntervalsSource
    ): IntervalsSource {
        return NonOverlappingIntervalsSource(minuend, extend(subtrahend, positions, positions))
    }

    /**
     * Returns intervals of the source that appear within a set number of positions of intervals from
     * the reference
     *
     * @param source the [IntervalsSource] to filter
     * @param positions the maximum distance that intervals of the source may occur from intervals of
     *     the reference
     * @param reference the [IntervalsSource] to filter by
     */
    fun within(
        source: IntervalsSource,
        positions: Int,
        reference: IntervalsSource
    ): IntervalsSource {
        return containedBy(source, extend(reference, positions, positions))
    }

    /**
     * Create a not-containing [IntervalsSource]
     *
     * <p>Returns intervals from the minuend that do not contain intervals of the subtrahend
     *
     * @param minuend the [IntervalsSource] to filter
     * @param subtrahend the [IntervalsSource] to filter by
     */
    fun notContaining(minuend: IntervalsSource, subtrahend: IntervalsSource): IntervalsSource {
        return NotContainingIntervalsSource.build(minuend, subtrahend)
    }

    /**
     * Create a containing [IntervalsSource]
     *
     * <p>Returns intervals from the big source that contain one or more intervals from the small
     * source
     *
     * @param big the [IntervalsSource] to filter
     * @param small the [IntervalsSource] to filter by
     */
    fun containing(big: IntervalsSource, small: IntervalsSource): IntervalsSource {
        return ContainingIntervalsSource.build(big, small)
    }

    /**
     * Create a not-contained-by [IntervalsSource]
     *
     * <p>Returns intervals from the small [IntervalsSource] that do not appear within intervals from
     * the big [IntervalsSource].
     *
     * @param small the [IntervalsSource] to filter
     * @param big the [IntervalsSource] to filter by
     */
    fun notContainedBy(small: IntervalsSource, big: IntervalsSource): IntervalsSource {
        return NotContainedByIntervalsSource.build(small, big)
    }

    /**
     * Create a contained-by [IntervalsSource]
     *
     * <p>Returns intervals from the small query that appear within intervals of the big query
     *
     * @param small the [IntervalsSource] to filter
     * @param big the [IntervalsSource] to filter by
     */
    fun containedBy(small: IntervalsSource, big: IntervalsSource): IntervalsSource {
        return ContainedByIntervalsSource.build(small, big)
    }

    /** Return intervals that span combinations of intervals from `minShouldMatch` of the sources */
    fun atLeast(minShouldMatch: Int, vararg sources: IntervalsSource): IntervalsSource {
        if (minShouldMatch == sources.size) {
            return unordered(*sources)
        }
        if (minShouldMatch > sources.size) {
            return NoMatchIntervalsSource(
                "Too few sources to match minimum of [" +
                    minShouldMatch +
                    "]: " +
                    sources.contentToString()
            )
        }
        return MinimumShouldMatchIntervalsSource(sources, minShouldMatch)
    }

    /** Returns intervals from the source that appear before intervals from the reference */
    fun before(source: IntervalsSource, reference: IntervalsSource): IntervalsSource {
        return ContainedByIntervalsSource.build(
            source, extend(OffsetIntervalsSource(reference, true), Int.MAX_VALUE, 0)
        )
    }

    /** Returns intervals from the source that appear after intervals from the reference */
    fun after(source: IntervalsSource, reference: IntervalsSource): IntervalsSource {
        return ContainedByIntervalsSource.build(
            source, extend(OffsetIntervalsSource(reference, false), 0, Int.MAX_VALUE)
        )
    }

    /**
     * Returns a source that produces no intervals
     *
     * @param reason A reason string that will appear in the toString output of this source
     */
    fun noIntervals(reason: String): IntervalsSource {
        return NoMatchIntervalsSource(reason)
    }

    /**
     * Returns intervals that correspond to tokens from a [TokenStream] returned for `text` by
     * applying the provided [Analyzer] as if `text` was the content of the given `field`. The
     * intervals can be ordered or unordered and can have optional gaps inside.
     *
     * @param text The text to analyze.
     * @param analyzer The [Analyzer] to use to acquire a [TokenStream] which is then converted into
     *     intervals.
     * @param field The field `text` should be parsed as.
     * @param maxGaps Maximum number of allowed gaps between sub-intervals resulting from tokens.
     * @param ordered Whether sub-intervals should enforce token ordering or not.
     * @return Returns an [IntervalsSource] that matches tokens acquired from analysis of `text`.
     *     Possibly an empty interval source, never `null`.
     * @throws IOException If an I/O exception occurs.
     */
    @Throws(IOException::class)
    fun analyzedText(
        text: String,
        analyzer: Analyzer,
        field: String,
        maxGaps: Int,
        ordered: Boolean
    ): IntervalsSource {
        analyzer.tokenStream(field, text).use { ts ->
            return analyzedText(ts, maxGaps, ordered)
        }
    }

    /**
     * Returns intervals that correspond to tokens from the provided [TokenStream]. This is a
     * low-level counterpart to [analyzedText]. The intervals can be ordered or unordered and can
     * have optional gaps inside.
     *
     * @param tokenStream The token stream to produce intervals for. The token stream may be fully or
     *     partially consumed after returning from this method.
     * @param maxGaps Maximum number of allowed gaps between sub-intervals resulting from tokens.
     * @param ordered Whether sub-intervals should enforce token ordering or not.
     * @return Returns an [IntervalsSource] that matches tokens acquired from analysis of `text`.
     *     Possibly an empty interval source, never `null`.
     * @throws IOException If an I/O exception occurs.
     */
    @Throws(IOException::class)
    fun analyzedText(
        tokenStream: TokenStream,
        maxGaps: Int,
        ordered: Boolean
    ): IntervalsSource {
        val stream: CachingTokenFilter =
            if (tokenStream is CachingTokenFilter) tokenStream else CachingTokenFilter(tokenStream)

        return IntervalBuilder.analyzeText(stream, maxGaps, ordered)
    }

}
