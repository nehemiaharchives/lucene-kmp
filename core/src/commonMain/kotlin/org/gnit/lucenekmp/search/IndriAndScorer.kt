package org.gnit.lucenekmp.search

/**
 * Combines scores of subscorers. If a subscorer does not contain the docId, a smoothing score is
 * calculated for that document/subscorer combination.
 */
class IndriAndScorer(
    subScorers: MutableList<Scorer>,
    scoreMode: ScoreMode,
    boost: Float
) : IndriDisjunctionScorer(subScorers, scoreMode, boost) {
    override fun score(subScorers: MutableList<Scorer>): Float {
        val docId = this.docID()
        return scoreDoc(subScorers, docId)
    }

    override fun smoothingScore(subScorers: MutableList<Scorer>, docId: Int): Float {
        return scoreDoc(subScorers, docId)
    }

    private fun scoreDoc(subScorers: MutableList<Scorer>, docId: Int): Float {
        var score = 0.0
        var boostSum = 0.0
        for (scorer in subScorers) {
            if (scorer is IndriScorer) {
                val scorerDocId = scorer.docID()
                // If the query exists in the document, score the document
                // Otherwise, compute a smoothing score, which acts like an idf
                // for subqueries/terms
                var tempScore = 0.0
                if (docId == scorerDocId) {
                    tempScore = scorer.score().toDouble()
                } else {
                    tempScore = scorer.smoothingScore(docId).toDouble()
                }
                tempScore *= scorer.getBoost().toDouble()
                score += tempScore
                boostSum += scorer.getBoost().toDouble()
            }
        }
        return if (boostSum == 0.0) {
            0f
        } else {
            (score / boostSum).toFloat()
        }
    }
}
