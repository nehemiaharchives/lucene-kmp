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
package org.gnit.lucenekmp.analysis.ro

import okio.IOException
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharArraySet
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import kotlin.test.Test

class TestRomanianAnalyzer : BaseTokenStreamTestCase() {
    /** This test fails with NPE when the stopwords file is missing in classpath */
    @Test
    fun testResourcesAvailable() {
        RomanianAnalyzer().close()
    }

    /** test stopwords, normalization and stemming */
    @Test
    @Throws(IOException::class)
    fun testBasics() {
        val a: Analyzer = RomanianAnalyzer()
        // stemming
        checkOneTerm(a, "absența", "absenț")
        checkOneTerm(a, "absenți", "absenț")
        // normalization
        checkOneTerm(a, "absenţ", "absenț")
        // stopword
        assertAnalyzesTo(a, "îl", emptyArray())
        a.close()
    }

    /** test use of exclusion set */
    @Test
    @Throws(IOException::class)
    fun testExclude() {
        val exclusionSet = CharArraySet(mutableSetOf<Any>("absența"), false)
        val a: Analyzer = RomanianAnalyzer(RomanianAnalyzer.getDefaultStopSet(), exclusionSet)
        checkOneTerm(a, "absența", "absența")
        checkOneTerm(a, "absenți", "absenț")
        a.close()
    }

    /** blast some random strings through the analyzer */
    @Test
    @Throws(Exception::class)
    fun testRandomStrings() {
        val analyzer = RomanianAnalyzer()
        checkRandomData(random(), analyzer, 200 * RANDOM_MULTIPLIER)
        analyzer.close()
    }
}