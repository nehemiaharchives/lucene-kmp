package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.search.CollectionStatistics
import org.gnit.lucenekmp.search.Explanation
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.TermStatistics
import org.gnit.lucenekmp.util.SmallFloat

/**
 * Implementation of [Similarity] with the Vector Space Model.
 *
 *
 * Expert: Scoring API.
 *
 *
 * TFIDFSimilarity defines the components of Lucene scoring. Overriding computation of these
 * components is a convenient way to alter Lucene scoring.
 *
 *
 * Suggested reading: [Introduction To
 * Information Retrieval, Chapter 6](http://nlp.stanford.edu/IR-book/html/htmledition/queries-as-vectors-1.html).
 *
 *
 * The following describes how Lucene scoring evolves from underlying information retrieval
 * models to (efficient) implementation. We first brief on *VSM Score*, then derive from it
 * *Lucene's Conceptual Scoring Formula*, from which, finally, evolves *Lucene's Practical
 * Scoring Function* (the latter is connected directly with Lucene classes and methods).
 *
 *
 * Lucene combines [Boolean model
 * (BM) of Information Retrieval](http://en.wikipedia.org/wiki/Standard_Boolean_model) with [
 * Vector Space Model (VSM) of Information Retrieval](http://en.wikipedia.org/wiki/Vector_Space_Model) - documents "approved" by BM are scored by
 * VSM.
 *
 *
 * In VSM, documents and queries are represented as weighted vectors in a multi-dimensional
 * space, where each distinct index term is a dimension, and weights are [Tf-idf](http://en.wikipedia.org/wiki/Tfidf) values.
 *
 *
 * VSM does not require weights to be *Tf-idf* values, but *Tf-idf* values are believed
 * to produce search results of high quality, and so Lucene is using *Tf-idf*. *Tf* and
 * *Idf* are described in more detail below, but for now, for completion, let's just say that
 * for given term *t* and document (or query) *x*, *Tf(t,x)* varies with the number
 * of occurrences of term *t* in *x* (when one increases so does the other) and
 * *idf(t)* similarly varies with the inverse of the number of index documents containing term
 * *t*.
 *
 *
 * *VSM score* of document *d* for query *q* is the [Cosine Similarity](http://en.wikipedia.org/wiki/Cosine_similarity) of the weighted query
 * vectors *V(q)* and *V(d)*: <br></br>
 * &nbsp;<br></br>
 *
 * <table class="padding2" style="border-spacing: 2px; border-collapse: separate; border: 0; width:auto; margin-left:auto; margin-right:auto">
 * <caption>formatting only</caption>
 * <tr><td>
 * <table class="padding1" style="border-spacing: 0px; border-collapse: separate; border: 1px solid; margin-left:auto; margin-right:auto">
 * <caption>formatting only</caption>
 * <tr><td>
 * <table class="padding2" style="border-spacing: 2px; border-collapse: separate; border: 0; margin-left:auto; margin-right:auto">
 * <caption>cosine similarity formula</caption>
 * <tr>
 * <td style="vertical-align: middle; text-align: right" rowspan="1">
 * cosine-similarity(q,d) &nbsp; = &nbsp;
</td> *
 * <td style="vertical-align: middle; text-align: center">
 * <table>
 * <caption>cosine similarity formula</caption>
 * <tr><td style="text-align: center"><small>V(q)&nbsp;&nbsp;V(d)</small></td></tr>
 * <tr><td style="text-align: center"></td></tr>
 * <tr><td style="text-align: center"><small>|V(q)|&nbsp;|V(d)|</small></td></tr>
</table> *
</td> *
</tr> *
</table> *
</td></tr> *
</table> *
</td></tr> *
 * <tr><td>
 * <u style="text-align: center">VSM Score</u>
</td></tr> *
</table> *
 *
 * <br></br>
 * &nbsp;<br></br>
 * Where *V(q)*  *V(d)* is the [dot product](http://en.wikipedia.org/wiki/Dot_product) of the weighted vectors, and
 * *|V(q)|* and *|V(d)|* are their [Euclidean norms](http://en.wikipedia.org/wiki/Euclidean_norm#Euclidean_norm).
 *
 *
 * Note: the above equation can be viewed as the dot product of the normalized weighted vectors,
 * in the sense that dividing *V(q)* by its euclidean norm is normalizing it to a unit vector.
 *
 *
 * Lucene refines *VSM score* for both search quality and usability:
 *
 *
 *  * Normalizing *V(d)* to the unit vector is known to be problematic in that it removes
 * all document length information. For some documents removing this info is probably ok, e.g.
 * a document made by duplicating a certain paragraph *10* times, especially if that
 * paragraph is made of distinct terms. But for a document which contains no duplicated
 * paragraphs, this might be wrong. To avoid this problem, a different document length
 * normalization factor is used, which normalizes to a vector equal to or larger than the unit
 * vector: *doc-len-norm(d)*.
 *  * At indexing, users can specify that certain documents are more important than others, by
 * assigning a document boost. For this, the score of each document is also multiplied by its
 * boost value *doc-boost(d)*.
 *  * Lucene is field based, hence each query term applies to a single field, document length
 * normalization is by the length of the certain field, and in addition to document boost
 * there are also document fields boosts.
 *  * The same field can be added to a document during indexing several times, and so the boost
 * of that field is the multiplication of the boosts of the separate additions (or parts) of
 * that field within the document.
 *  * At search time users can specify boosts to each query, sub-query, and each query term,
 * hence the contribution of a query term to the score of a document is multiplied by the
 * boost of that query term *query-boost(q)*.
 *  * A document may match a multi term query without containing all the terms of that query
 * (this is correct for some of the queries).
 *
 *
 *
 * Under the simplifying assumption of a single field in the index, we get *Lucene's Conceptual
 * scoring formula*: <br></br>
 * &nbsp;<br></br>
 *
 * <table class="padding2" style="border-spacing: 2px; border-collapse: separate; border: 0; width:auto; margin-left:auto; margin-right:auto">
 * <caption>formatting only</caption>
 * <tr><td>
 * <table class="padding1" style="border-spacing: 0px; border-collapse: separate; border: 1px solid; margin-left:auto; margin-right:auto">
 * <caption>formatting only</caption>
 * <tr><td>
 * <table class="padding2" style="border-spacing: 2px; border-collapse: separate; border: 0; margin-left:auto; margin-right:auto">
 * <caption>formatting only</caption>
 * <tr>
 * <td style="vertical-align: middle; text-align: right" rowspan="1">
 * score(q,d) &nbsp; = &nbsp;
 * <span style="color: #CCCC00">query-boost(q)</span>  &nbsp;
</td> *
 * <td style="vertical-align: middle; text-align: center">
 * <table>
 * <caption>Lucene conceptual scoring formula</caption>
 * <tr><td style="text-align: center"><small><span style="color: #993399">V(q)&nbsp;&nbsp;V(d)</span></small></td></tr>
 * <tr><td style="text-align: center"></td></tr>
 * <tr><td style="text-align: center"><small><span style="color: #FF33CC">|V(q)|</span></small></td></tr>
</table> *
</td> *
 * <td style="vertical-align: middle; text-align: right" rowspan="1">
 * &nbsp;  &nbsp; <span style="color: #3399FF">doc-len-norm(d)</span>
 * &nbsp;  &nbsp; <span style="color: #3399FF">doc-boost(d)</span>
</td> *
</tr> *
</table> *
</td></tr> *
</table> *
</td></tr> *
 * <tr><td>
 * <u style="text-align: center">Lucene Conceptual Scoring Formula</u>
</td></tr> *
</table> *
 *
 * <br></br>
 * &nbsp;<br></br>
 *
 *
 * The conceptual formula is a simplification in the sense that (1) terms and documents are
 * fielded and (2) boosts are usually per query term rather than per query.
 *
 *
 * We now describe how Lucene implements this conceptual scoring formula, and derive from it
 * *Lucene's Practical Scoring Function*.
 *
 *
 * For efficient score computation some scoring components are computed and aggregated in
 * advance:
 *
 *
 *  * *Query-boost* for the query (actually for each query term) is known when search
 * starts.
 *  * Query Euclidean norm *|V(q)|* can be computed when search starts, as it is independent
 * of the document being scored. From search optimization perspective, it is a valid question
 * why bother to normalize the query at all, because all scored documents will be multiplied
 * by the same *|V(q)|*, and hence documents ranks (their order by score) will not be
 * affected by this normalization. There are two good reasons to keep this normalization:
 *
 *  * Recall that [Cosine
 * Similarity](http://en.wikipedia.org/wiki/Cosine_similarity) can be used find how similar two documents are. One can use Lucene for
 * e.g. clustering, and use a document as a query to compute its similarity to other
 * documents. In this use case it is important that the score of document *d3* for
 * query *d1* is comparable to the score of document *d3* for query *d2*.
 * In other words, scores of a document for two distinct queries should be comparable.
 * There are other applications that may require this. And this is exactly what
 * normalizing the query vector *V(q)* provides: comparability (to a certain
 * extent) of two or more queries.
 *
 *  * Document length norm *doc-len-norm(d)* and document boost *doc-boost(d)* are
 * known at indexing time. They are computed in advance and their multiplication is saved as a
 * single value in the index: *norm(d)*. (In the equations below, *norm(t in d)*
 * means *norm(field(t) in doc d)* where *field(t)* is the field associated with
 * term *t*.)
 *
 *
 *
 * *Lucene's Practical Scoring Function* is derived from the above. The color codes
 * demonstrate how it relates to those of the *conceptual* formula:
 *
 * <table class="padding2" style="border-spacing: 2px; border-collapse: separate; border: 0; width:auto; margin-left:auto; margin-right:auto">
 * <caption>formatting only</caption>
 * <tr><td>
 * <table style="border-spacing: 2px; border-collapse: separate; border: 2px solid; margin-left:auto; margin-right:auto">
 * <caption>formatting only</caption>
 * <tr><td>
 * <table class="padding2" style="border-spacing: 2px; border-collapse: separate; border: 0; margin-left:auto; margin-right:auto">
 * <caption>Lucene conceptual scoring formula</caption>
 * <tr>
 * <td style="vertical-align: middle; text-align: right" rowspan="1">
 * score(q,d) &nbsp; = &nbsp;
 * <span style="font-size: larger"></span>
</td> *
 * <td style="vertical-align: middle; text-align: right" rowspan="1">
 * <span style="font-size: larger">(</span>
 * <A HREF="#formula_tf"><span style="color: #993399">tf(t in d)</span></A> &nbsp;&nbsp;
 * <A HREF="#formula_idf"><span style="color: #993399">idf(t)</span></A><sup>2</sup> &nbsp;&nbsp;
 * <A HREF="#formula_termBoost"><span style="color: #CCCC00">t.getBoost()</span></A>&nbsp;&nbsp;
 * <A HREF="#formula_norm"><span style="color: #3399FF">norm(t,d)</span></A>
 * <span style="font-size: larger">)</span>
</td> *
</tr> *
 * <tr style="vertical-align: top">
 * <td></td>
 * <td style="text-align: center"><small>t in q</small></td>
 * <td></td>
</tr> *
</table> *
</td></tr> *
</table> *
</td></tr> *
 * <tr><td>
 * <u style="text-align: center">Lucene Practical Scoring Function</u>
</td></tr> *
</table> *
 *
 *
 * where
 *
 *
 *  1. <a id="formula_tf"></a> ***tf(t in d)*** correlates to the term's *frequency*,
 * defined as the number of times term *t* appears in the currently scored document
 * *d*. Documents that have more occurrences of a given term receive a higher score. Note
 * that *tf(t in q)* is assumed to be *1* and therefore it does not appear in this
 * equation, However if a query contains twice the same term, there will be two term-queries
 * with that same term and hence the computation would still be correct (although not very
 * efficient). The default computation for *tf(t in d)* in [       ][org.apache.lucene.search.similarities.ClassicSimilarity.tf] is:
 * <br></br>
 * &nbsp;<br></br>
 * <table class="padding2" style="border-spacing: 2px; border-collapse: separate; border: 0; width:auto; margin-left:auto; margin-right:auto">
 * <caption>term frequency computation</caption>
 * <tr>
 * <td style="vertical-align: middle; text-align: right" rowspan="1">
 * [tf(t in d)][org.apache.lucene.search.similarities.ClassicSimilarity.tf] &nbsp; = &nbsp;
</td> *
 * <td style="vertical-align: top; text-align: center" rowspan="1">
 * frequency<sup><span style="font-size: larger"></span></sup>
</td> *
</tr> *
</table> *
 * <br></br>
 * &nbsp;<br></br>
 *  1. <a id="formula_idf"></a> ***idf(t)*** stands for Inverse Document Frequency. This
 * value correlates to the inverse of *docFreq* (the number of documents in which the
 * term *t* appears). This means rarer terms give higher contribution to the total score.
 * *idf(t)* appears for *t* in both the query and the document, hence it is squared
 * in the equation. The default computation for *idf(t)* in [       ][org.apache.lucene.search.similarities.ClassicSimilarity.idf]
 * is: <br></br>
 * &nbsp;<br></br>
 * <table class="padding2" style="border-spacing: 2px; border-collapse: separate; border: 0; width:auto; margin-left:auto; margin-right:auto">
 * <caption>inverse document frequency computation</caption>
 * <tr>
 * <td style="vertical-align: middle; text-align: right">
 * [idf(t)][org.apache.lucene.search.similarities.ClassicSimilarity.idf]&nbsp; = &nbsp;
</td> *
 * <td style="vertical-align: middle; text-align: center">
 * 1 + log <span style="font-size: larger">(</span>
</td> *
 * <td style="vertical-align: middle; text-align: center">
 * <table>
 * <caption>inverse document frequency computation</caption>
 * <tr><td style="text-align: center"><small>docCount+1</small></td></tr>
 * <tr><td style="text-align: center"></td></tr>
 * <tr><td style="text-align: center"><small>docFreq+1</small></td></tr>
</table> *
</td> *
 * <td style="vertical-align: middle; text-align: center">
 * <span style="font-size: larger">)</span>
</td> *
</tr> *
</table> *
 * <br></br>
 * &nbsp;<br></br>
 *  1. <a id="formula_termBoost"></a> ***t.getBoost()*** is a search time boost of term
 * *t* in the query *q* as specified in the query text (see <A HREF="{@docRoot}/../queryparser/org/apache/lucene/queryparser/classic/package-summary.html#Boosting_a_Term">query
 * syntax</A>), or as set by wrapping with [       ][org.apache.lucene.search.BoostQuery.BoostQuery]. Notice that there is really no direct API for accessing a boost of one term in
 * a multi term query, but rather multi terms are represented in a query as multi [       ] objects, and so the boost of a term in the
 * query is accessible by calling the sub-query [       ][org.apache.lucene.search.BoostQuery.getBoost]. <br></br>
 * &nbsp;<br></br>
 *  1. <a id="formula_norm"></a> ***norm(t,d)*** is an index-time boost factor that solely
 * depends on the number of tokens of this field in the document, so that shorter fields
 * contribute more to the score.
 *
 *
 * @see org.apache.lucene.index.IndexWriterConfig.setSimilarity
 * @see IndexSearcher.setSimilarity
 */
abstract class TFIDFSimilarity : Similarity {
    /** Default constructor: parameter-free  */
    constructor() : super()

    /** Primary constructor.  */
    constructor(discountOverlaps: Boolean) : super(discountOverlaps)

    /**
     * Computes a score factor based on a term or phrase's frequency in a document. This value is
     * multiplied by the [.idf] factor for each term in the query and these products
     * are then summed to form the initial score for a document.
     *
     *
     * Terms and phrases repeated in a document indicate the topic of the document, so
     * implementations of this method usually return larger values when `freq` is large,
     * and smaller values when `freq` is small.
     *
     * @param freq the frequency of a term within a document
     * @return a score factor based on a term's within-document frequency
     */
    abstract fun tf(freq: Float): Float

    /**
     * Computes a score factor for a simple term and returns an explanation for that score factor.
     *
     *
     * The default implementation uses:
     *
     * <pre class="prettyprint">
     * idf(docFreq, docCount);
    </pre> *
     *
     * Note that [CollectionStatistics.docCount] is used instead of [ ][org.apache.lucene.index.IndexReader.numDocs] because also [ ][TermStatistics.docFreq] is used, and when the latter is inaccurate, so is [ ][CollectionStatistics.docCount], and in the same direction. In addition, [ ][CollectionStatistics.docCount] does not skew when fields are sparse.
     *
     * @param collectionStats collection-level statistics
     * @param termStats term-level statistics for the term
     * @return an Explain object that includes both an idf score factor and an explanation for the
     * term.
     */
    open fun idfExplain(
        collectionStats: CollectionStatistics,
        termStats: TermStatistics
    ): Explanation {
        val df: Long = termStats.docFreq
        val docCount: Long = collectionStats.docCount
        val idf = idf(df, docCount)
        return Explanation.match(
            idf,
            "idf(docFreq, docCount)",
            Explanation.match(
                df,
                "docFreq, number of documents containing term"
            ),
            Explanation.match(
                docCount,
                "docCount, total number of documents with field"
            )
        )
    }

    /**
     * Computes a score factor for a phrase.
     *
     *
     * The default implementation sums the idf factor for each term in the phrase.
     *
     * @param collectionStats collection-level statistics
     * @param termStats term-level statistics for the terms in the phrase
     * @return an Explain object that includes both an idf score factor for the phrase and an
     * explanation for each term.
     */
    fun idfExplain(
        collectionStats: CollectionStatistics,
        termStats: Array<TermStatistics>
    ): Explanation {
        var idf = 0.0 // sum into a double before casting into a float
        val subs: MutableList<Explanation> = mutableListOf()
        for (stat in termStats) {
            val idfExplain: Explanation = idfExplain(collectionStats, stat)
            subs.add(idfExplain)
            idf += idfExplain.value.toFloat().toDouble()
        }
        return Explanation.match(idf.toFloat(), "idf(), sum of:", subs)
    }

    /**
     * Computes a score factor based on a term's document frequency (the number of documents which
     * contain the term). This value is multiplied by the [.tf] factor for each term in
     * the query and these products are then summed to form the initial score for a document.
     *
     *
     * Terms that occur in fewer documents are better indicators of topic, so implementations of
     * this method usually return larger values for rare terms, and smaller values for common terms.
     *
     * @param docFreq the number of documents which contain the term
     * @param docCount the total number of documents in the collection
     * @return a score factor based on the term's document frequency
     */
    abstract fun idf(docFreq: Long, docCount: Long): Float

    /**
     * Compute an index-time normalization value for this field instance.
     *
     * @param length the number of terms in the field, optionally [     discounting overlaps][.getDiscountOverlaps]
     * @return a length normalization value
     */
    abstract fun lengthNorm(length: Int): Float

    override fun scorer(
        boost: Float,
        collectionStats: CollectionStatistics,
        vararg termStats: TermStatistics
    ): SimScorer {
        val idf: Explanation =
            if (termStats.size == 1)
                idfExplain(collectionStats, termStats[0])
            else
                idfExplain(collectionStats, termStats as Array<TermStatistics>)
        val normTable = FloatArray(256)
        for (i in 1..255) {
            val norm = lengthNorm(LENGTH_TABLE[i])
            normTable[i] = norm
        }
        normTable[0] = 1f / normTable[255]
        return TFIDFScorer(boost, idf, normTable)
    }

    /**
     * Collection statistics for the TF-IDF model. The only statistic of interest to this model is
     * idf.
     */
    internal inner class TFIDFScorer(
        private val boost: Float,
        /** The idf and its explanation  */
        // TODO: Validate
        private val idf: Explanation,
        val normTable: FloatArray
    ) : SimScorer() {

        private val queryWeight: Float = boost * idf.value.toFloat()

        override fun score(freq: Float, norm: Long): Float {
            val raw = tf(freq) * queryWeight // compute tf(f)*weight
            val normValue = normTable[(norm and 0xFFL).toInt()]
            return raw * normValue // normalize for field
        }

        override fun explain(
            freq: Explanation,
            norm: Long
        ): Explanation {
            return explainScore(freq, norm, normTable)
        }

        private fun explainScore(
            freq: Explanation,
            encodedNorm: Long,
            normTable: FloatArray
        ): Explanation {
            val subs: MutableList<Explanation> = mutableListOf()
            if (boost != 1f) {
                subs.add(Explanation.match(boost, "boost"))
            }
            subs.add(idf)
            val tf: Explanation =
                Explanation.match(
                    tf(freq.value.toFloat()),
                    "tf(freq=" + freq.value + "), with freq of:",
                    freq
                )
            subs.add(tf)

            val norm = normTable[(encodedNorm and 0xFFL).toInt()]

            val fieldNorm: Explanation =
                Explanation.match(norm, "fieldNorm")
            subs.add(fieldNorm)

            return Explanation.match(
                queryWeight * tf.value.toFloat() * norm,
                "score(freq=" + freq.value + "), product of:",
                subs
            )
        }
    }

    companion object {
        /** Cache of decoded bytes.  */
        private val LENGTH_TABLE = IntArray(256)

        init {
            for (i in 0..255) {
                LENGTH_TABLE[i] = SmallFloat.byte4ToInt(i.toByte())
            }
        }
    }
}
