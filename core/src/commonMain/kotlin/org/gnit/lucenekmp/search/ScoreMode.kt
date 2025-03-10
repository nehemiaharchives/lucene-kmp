package org.gnit.lucenekmp.search

enum class ScoreMode(
    private val isExhaustive: Boolean,
    private val needsScores: Boolean
) {
    /** Produced scorers will allow visiting all matches and get their score. */
    COMPLETE(true, true),

    /** Produced scorers will allow visiting all matches but scores won't be available. */
    COMPLETE_NO_SCORES(true, false),

    /**
     * Produced scorers will optionally allow skipping over non-competitive hits using the
     * [Scorer.setMinCompetitiveScore] API.
     */
    TOP_SCORES(false, true),

    /**
     * ScoreMode for top field collectors that can provide their own iterators, to optionally allow to
     * skip for non-competitive docs
     */
    TOP_DOCS(false, false),

    /**
     * ScoreMode for top field collectors that can provide their own iterators, to optionally allow to
     * skip for non-competitive docs. This mode is used when there is a secondary sort by _score.
     */
    TOP_DOCS_WITH_SCORES(false, true);

    /** Whether this [ScoreMode] needs to compute scores. */
    fun needsScores(): Boolean {
        return needsScores
    }

    /**
     * Returns `true` if for this [ScoreMode] it is necessary to process all documents, or
     * `false` if is enough to go through top documents only.
     */
    fun isExhaustive(): Boolean {
        return isExhaustive
    }
}
