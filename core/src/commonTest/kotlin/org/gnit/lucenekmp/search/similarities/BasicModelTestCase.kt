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

import org.gnit.lucenekmp.jdkport.Math
import org.gnit.lucenekmp.tests.search.similarities.BaseSimilarityTestCase
import kotlin.random.Random

abstract class BasicModelTestCase : BaseSimilarityTestCase() {

    override fun getSimilarity(random: Random): Similarity {
        val afterEffect: AfterEffect =
            when (random.nextInt(2)) {
                0 -> AfterEffectL()
                else -> AfterEffectB()
            }

        // normalization hyper-parameter c
        val c: Float =
            when (random.nextInt(4)) {
                0 -> 0f
                1 -> Float.MIN_VALUE
                2 -> Int.MAX_VALUE.toFloat()
                else -> Int.MAX_VALUE * random.nextFloat()
            }

        // normalization hyper-parameter z
        val z: Float =
            when (random.nextInt(3)) {
                0 -> Float.MIN_VALUE
                1 -> Math.nextDown(0.5f)
                else -> {
                    val zcand = random.nextFloat() / 2
                    if (zcand == 0f) {
                        Math.nextUp(zcand)
                    } else {
                        zcand
                    }
                }
            }

        // dirichlet parameter mu
        val mu: Float =
            when (random.nextInt(4)) {
                0 -> 0f
                1 -> Float.MIN_VALUE
                2 -> Int.MAX_VALUE.toFloat()
                else -> Int.MAX_VALUE * random.nextFloat()
            }

        val normalization: Normalization =
            when (random.nextInt(5)) {
                0 -> Normalization.NoNormalization()
                1 -> NormalizationH1(c)
                2 -> NormalizationH2(c)
                3 -> NormalizationH3(mu)
                else -> NormalizationZ(z)
            }

        return DFRSimilarity(getBasicModel(), afterEffect, normalization)
    }

    /** return BasicModel under test */
    protected abstract fun getBasicModel(): BasicModel
}
