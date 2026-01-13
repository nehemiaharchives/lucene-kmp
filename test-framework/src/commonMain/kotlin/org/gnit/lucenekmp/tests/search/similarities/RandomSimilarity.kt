package org.gnit.lucenekmp.tests.search.similarities

import org.gnit.lucenekmp.jdkport.computeIfAbsent
/*import org.gnit.lucenekmp.search.similarities.AfterEffect
import org.gnit.lucenekmp.search.similarities.AfterEffectB
import org.gnit.lucenekmp.search.similarities.AfterEffectL
import org.gnit.lucenekmp.search.similarities.AxiomaticF1EXP
import org.gnit.lucenekmp.search.similarities.AxiomaticF1LOG
import org.gnit.lucenekmp.search.similarities.AxiomaticF2EXP
import org.gnit.lucenekmp.search.similarities.AxiomaticF2LOG*/
import org.gnit.lucenekmp.search.similarities.BM25Similarity
/*import org.gnit.lucenekmp.search.similarities.BasicModel
import org.gnit.lucenekmp.search.similarities.BasicModelG
import org.gnit.lucenekmp.search.similarities.BasicModelIF
import org.gnit.lucenekmp.search.similarities.BasicModelIn
import org.gnit.lucenekmp.search.similarities.BasicModelIne
import org.gnit.lucenekmp.search.similarities.BooleanSimilarity
import org.gnit.lucenekmp.search.similarities.ClassicSimilarity
import org.gnit.lucenekmp.search.similarities.DFISimilarity
import org.gnit.lucenekmp.search.similarities.DFRSimilarity
import org.gnit.lucenekmp.search.similarities.Distribution
import org.gnit.lucenekmp.search.similarities.DistributionLL
import org.gnit.lucenekmp.search.similarities.DistributionSPL
import org.gnit.lucenekmp.search.similarities.IBSimilarity
import org.gnit.lucenekmp.search.similarities.Independence
import org.gnit.lucenekmp.search.similarities.IndependenceChiSquared
import org.gnit.lucenekmp.search.similarities.IndependenceSaturated
import org.gnit.lucenekmp.search.similarities.IndependenceStandardized*/
import org.gnit.lucenekmp.search.similarities.LMDirichletSimilarity
/*import org.gnit.lucenekmp.search.similarities.LMJelinekMercerSimilarity
import org.gnit.lucenekmp.search.similarities.Lambda
import org.gnit.lucenekmp.search.similarities.LambdaDF
import org.gnit.lucenekmp.search.similarities.LambdaTTF
import org.gnit.lucenekmp.search.similarities.Normalization
import org.gnit.lucenekmp.search.similarities.NormalizationH1
import org.gnit.lucenekmp.search.similarities.NormalizationH2
import org.gnit.lucenekmp.search.similarities.NormalizationH3
import org.gnit.lucenekmp.search.similarities.NormalizationZ*/
import org.gnit.lucenekmp.search.similarities.PerFieldSimilarityWrapper
import org.gnit.lucenekmp.search.similarities.Similarity
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random


/**
 * Similarity implementation that randomizes Similarity implementations per-field.
 *
 *
 * The choices are 'sticky', so the selected algorithm is always used for the same field.
 */
class RandomSimilarity(random: Random) : PerFieldSimilarityWrapper() {
    private val knownSims: MutableList<Similarity>
    private val previousMappings: MutableMap<String, Similarity> = mutableMapOf()
    private val perFieldSeed: Int
    private val shouldQueryNorm: Boolean

    /*@Synchronized*/
    override fun get(name: String): Similarity {
        checkNotNull(name)
        return previousMappings.computeIfAbsent(
            name
        ) { f: String ->
            knownSims.get(
                max(
                    0,
                    abs(perFieldSeed xor f.hashCode())
                ) % knownSims.size
            )
        }!!
    }

    init {
        perFieldSeed = random.nextInt()
        shouldQueryNorm = random.nextBoolean()
        knownSims = ArrayList<Similarity>(allSims)
        knownSims.shuffle(random)
    }

    /*@Synchronized*/
    override fun toString(): String {
        return "RandomSimilarity(queryNorm=$shouldQueryNorm): $previousMappings"
    }

    companion object {
        // all the similarities that we rotate through
        /** The DFR basic models to test.  */
        /*private val BASIC_MODELS: Array<BasicModel> = arrayOf<BasicModel>(
            BasicModelG(), BasicModelIF(), BasicModelIn(), BasicModelIne(),
        )

        *//** The DFR aftereffects to test.  *//*
        private val AFTER_EFFECTS: Array<AfterEffect> =
            arrayOf<AfterEffect>(AfterEffectB(), AfterEffectL())

        *//** The DFR normalizations to test.  *//*
        private val NORMALIZATIONS: Array<Normalization> = arrayOf<Normalization>(
            NormalizationH1(),
            NormalizationH2(),
            NormalizationH3(),
            NormalizationZ() // TODO: if we enable NoNormalization, we have to deal with
            // a couple tests (e.g. TestDocBoost, TestSort) that expect length normalization
            // new Normalization.NoNormalization()
        )

        *//** The distributions for IB.  *//*
        private val DISTRIBUTIONS: Array<Distribution> =
            arrayOf<Distribution>(
                DistributionLL(),
                DistributionSPL()
            )

        *//** Lambdas for IB.  *//*
        private val LAMBDAS: Array<Lambda> =
            arrayOf<Lambda>(LambdaDF(), LambdaTTF())

        *//** Independence measures for DFI  *//*
        private val INDEPENDENCE_MEASURES: Array<Independence> = arrayOf<Independence>(
            IndependenceStandardized(), IndependenceSaturated(), IndependenceChiSquared()
        )*/

        private val allSims: MutableList<Similarity>

        init {
            allSims = ArrayList<Similarity>()
            //allSims.add(ClassicSimilarity())
            allSims.add(BM25Similarity())
            /*allSims.add(AxiomaticF1EXP())
            allSims.add(AxiomaticF1LOG())
            allSims.add(AxiomaticF2EXP())
            allSims.add(AxiomaticF2LOG())

            allSims.add(BooleanSimilarity())
            for (basicModel in BASIC_MODELS) {
                for (afterEffect in AFTER_EFFECTS) {
                    for (normalization in NORMALIZATIONS) {
                        allSims.add(DFRSimilarity(basicModel, afterEffect, normalization))
                    }
                }
            }
            for (distribution in DISTRIBUTIONS) {
                for (lambda in LAMBDAS) {
                    for (normalization in NORMALIZATIONS) {
                        allSims.add(IBSimilarity(distribution, lambda, normalization))
                    }
                }
            }*/
            allSims.add(LMDirichletSimilarity())
            /*allSims.add(LMJelinekMercerSimilarity(0.1f))
            allSims.add(LMJelinekMercerSimilarity(0.7f))
            for (independence in INDEPENDENCE_MEASURES) {
                allSims.add(DFISimilarity(independence))
            }*/
        }
    }
}
