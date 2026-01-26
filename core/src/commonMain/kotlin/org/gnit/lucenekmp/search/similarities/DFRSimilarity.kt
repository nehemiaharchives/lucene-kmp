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
package org.gnit.lucenekmp.search.similarities

import org.gnit.lucenekmp.search.Explanation

/**
 * Implements the <em>divergence from randomness (DFR)</em> framework introduced in Gianni Amati and
 * Cornelis Joost Van Rijsbergen. 2002. Probabilistic models of information retrieval based on
 * measuring the divergence from randomness. ACM Trans. Inf. Syst. 20, 4 (October 2002), 357-389.
 *
 * <p>The DFR scoring formula is composed of three separate components: the <em>basic model</em>,
 * the <em>aftereffect</em> and an additional <em>normalization</em> component, represented by the
 * classes `BasicModel`, `AfterEffect` and `Normalization`, respectively. The
 * names of these classes were chosen to match the names of their counterparts in the Terrier IR
 * engine.
 *
 * <p>To construct a DFRSimilarity, you must specify the implementations for all three components of
 * DFR:
 *
 * <ol>
 *   <li>[BasicModel]: Basic model of information content:
 *       <ul>
 *         <li>[BasicModelG]: Geometric approximation of Bose-Einstein
 *         <li>[BasicModelIn]: Inverse document frequency
 *         <li>[BasicModelIne]: Inverse expected document frequency [mixture of Poisson and
 *             IDF]
 *         <li>[BasicModelIF]: Inverse term frequency [approximation of I(ne)]
 *       </ul>
 *   <li>[AfterEffect]: First normalization of information gain:
 *       <ul>
 *         <li>[AfterEffectL]: Laplace's law of succession
 *         <li>[AfterEffectB]: Ratio of two Bernoulli processes
 *       </ul>
 *   <li>[Normalization]: Second (length) normalization:
 *       <ul>
 *         <li>[NormalizationH1]: Uniform distribution of term frequency
 *         <li>[NormalizationH2]: term frequency density inversely related to length
 *         <li>[NormalizationH3]: term frequency normalization provided by Dirichlet prior
 *         <li>[NormalizationZ]: term frequency normalization provided by a Zipfian relation
 *         <li>[Normalization.NoNormalization]: no second normalization
 *       </ul>
 * </ol>
 *
 * <p>Note that <em>qtf</em>, the multiplicity of term-occurrence in the query, is not handled by
 * this implementation.
 *
 * <p>Note that basic models BE (Limiting form of Bose-Einstein), P (Poisson approximation of the
 * Binomial) and D (Divergence approximation of the Binomial) are not implemented because their
 * formula couldn't be written in a way that makes scores non-decreasing with the normalized term
 * frequency.
 *
 * @see BasicModel
 * @see AfterEffect
 * @see Normalization
 * @lucene.experimental
 */
class DFRSimilarity : SimilarityBase {
    /** The basic model for information content. */
    val basicModel: BasicModel

    /** The first normalization of the information content. */
    val afterEffect: AfterEffect

    /** The term frequency normalization. */
    val normalization: Normalization

    /**
     * Creates DFRSimilarity from the three components and using default discountOverlaps value.
     *
     * <p>Note that `null` values are not allowed: if you want no normalization, instead
     * pass [Normalization.NoNormalization].
     *
     * @param basicModel Basic model of information content
     * @param afterEffect First normalization of information gain
     * @param normalization Second (length) normalization
     */
    constructor(basicModel: BasicModel, afterEffect: AfterEffect, normalization: Normalization) :
        this(basicModel, afterEffect, normalization, true)

    /**
     * Creates DFRSimilarity from the three components and with the specified discountOverlaps value.
     *
     * <p>Note that `null` values are not allowed: if you want no normalization, instead
     * pass [Normalization.NoNormalization].
     *
     * @param basicModel Basic model of information content
     * @param afterEffect First normalization of information gain
     * @param normalization Second (length) normalization
     * @param discountOverlaps True if overlap tokens (tokens with a position of increment of zero)
     * are discounted from the document's length.
     */
    constructor(
        basicModel: BasicModel,
        afterEffect: AfterEffect,
        normalization: Normalization,
        discountOverlaps: Boolean
    ) : super(discountOverlaps) {
        this.basicModel = basicModel
        this.afterEffect = afterEffect
        this.normalization = normalization
    }

    override fun score(stats: BasicStats, freq: Double, docLen: Double): Double {
        val tfn = normalization.tfn(stats, freq, docLen)
        val aeTimes1pTfn = afterEffect.scoreTimes1pTfn(stats)
        return stats.boost * basicModel.score(stats, tfn, aeTimes1pTfn)
    }

    override fun explain(subExpls: MutableList<Explanation>, stats: BasicStats, freq: Double, docLen: Double) {
        if (stats.boost != 1.0) {
            subExpls.add(Explanation.match(stats.boost.toFloat(), "boost, query boost"))
        }

        val normExpl = normalization.explain(stats, freq, docLen)
        val tfn = normalization.tfn(stats, freq, docLen)
        val aeTimes1pTfn = afterEffect.scoreTimes1pTfn(stats)
        subExpls.add(normExpl)
        subExpls.add(basicModel.explain(stats, tfn, aeTimes1pTfn))
        subExpls.add(afterEffect.explain(stats, tfn))
    }

    override fun explain(stats: BasicStats, freq: Explanation, docLen: Double): Explanation {
        val subs: MutableList<Explanation> = ArrayList()
        explain(subs, stats, freq.value.toDouble(), docLen)

        return Explanation.match(
            score(stats, freq.value.toDouble(), docLen).toFloat(),
            "score("
                    + this::class.simpleName
                    + ", freq="
                    + freq.value
                    + "), computed as boost * "
                    + "basicModel.score(stats, tfn) * afterEffect.score(stats, tfn) from:",
            subs
        )
    }

    override fun toString(): String {
        return "DFR $basicModel$afterEffect$normalization"
    }
}
