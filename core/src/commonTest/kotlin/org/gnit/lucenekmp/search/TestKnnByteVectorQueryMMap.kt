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
package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.store.MMapDirectory
import org.gnit.lucenekmp.tests.store.BaseDirectoryWrapper
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import kotlin.test.Test

class TestKnnByteVectorQueryMMap : TestKnnByteVectorQuery() {
    override fun newDirectoryForTest(): BaseDirectoryWrapper {
        return MockDirectoryWrapper(
            random(),
            MMapDirectory(createTempDir("TestKnnByteVectorQueryMMap"))
        )
    }

    // tests inherited from TestKnnByteVectorQuery
    @Test
    override fun testToString() = super.testToString()

    @Test
    override fun testGetTarget() = super.testGetTarget()

    @Test
    override fun testVectorEncodingMismatch() = super.testVectorEncodingMismatch()

    // tests inherited from BaseKnnVectorQueryTestCase
    @Test
    override fun testEquals() = super.testEquals()

    @Test
    override fun testGetField() = super.testGetField()

    @Test
    override fun testGetK() = super.testGetK()

    @Test
    override fun testGetFilter() = super.testGetFilter()

    @Test
    override fun testEmptyIndex() = super.testEmptyIndex()

    @Test
    override fun testFindAll() = super.testFindAll()

    @Test
    override fun testFindFewer() = super.testFindFewer()

    @Test
    override fun testSearchBoost() = super.testSearchBoost()

    @Test
    override fun testSimpleFilter() = super.testSimpleFilter()

    @Test
    override fun testFilterWithNoVectorMatches() = super.testFilterWithNoVectorMatches()

    @Test
    override fun testDimensionMismatch() = super.testDimensionMismatch()

    @Test
    override fun testNonVectorField() = super.testNonVectorField()

    @Test
    override fun testIllegalArguments() = super.testIllegalArguments()

    @Test
    override fun testDifferentReader() = super.testDifferentReader()

    @Test
    override fun testScoreEuclidean() = super.testScoreEuclidean()

    @Test
    override fun testScoreCosine() = super.testScoreCosine()

    @Test
    override fun testScoreMIP() = super.testScoreMIP()

    @Test
    override fun testExplain() = super.testExplain()

    @Test
    override fun testExplainMultipleSegments() = super.testExplainMultipleSegments()

    @Test
    override fun testSkewedIndex() = super.testSkewedIndex()

    @Test
    override fun testRandomConsistencySingleThreaded() = super.testRandomConsistencySingleThreaded()

    @Test
    override fun testRandomConsistencyMultiThreaded() = super.testRandomConsistencyMultiThreaded()

    @Test
    override fun testRandom() = super.testRandom()

    @Test
    override fun testRandomWithFilter() = super.testRandomWithFilter()

    @Test
    override fun testFilterWithSameScore() = super.testFilterWithSameScore()

    @Test
    override fun testDeletes() = super.testDeletes()

    @Test
    override fun testAllDeletes() = super.testAllDeletes()

    @Test
    override fun testMergeAwayAllValues() = super.testMergeAwayAllValues()

    @Test
    override fun testNoLiveDocsReader() = super.testNoLiveDocsReader()

    @Test
    override fun testBitSetQuery() = super.testBitSetQuery()

    @Test
    override fun testTimeLimitingKnnCollectorManager() = super.testTimeLimitingKnnCollectorManager()

    @Test
    override fun testTimeout() = super.testTimeout()

    @Test
    override fun testSameFieldDifferentFormats() = super.testSameFieldDifferentFormats()
}
