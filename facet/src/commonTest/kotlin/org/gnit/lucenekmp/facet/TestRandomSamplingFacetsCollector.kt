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
package org.gnit.lucenekmp.facet

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field.Store
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.facet.taxonomy.FastTaxonomyFacetCounts
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyWriter
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.util.IOUtils
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestRandomSamplingFacetsCollector : FacetTestCase() {

  // The first 50 chi-square value for p-value=0.05, taken from:
  // http://en.wikibooks.org/wiki/Engineering_Tables/Chi-Squared_Distibution
  private val CHI_SQUARE_VALUES = floatArrayOf(
        0.0f, 3.841f, 5.991f, 7.815f, 9.488f, 11.07f, 12.592f, 14.067f, 15.507f, 16.919f, 18.307f,
        19.675f, 21.026f, 22.362f, 23.685f, 24.996f, 26.296f, 27.587f, 28.869f, 30.144f, 31.41f,
        32.671f, 33.924f, 35.172f, 36.415f, 37.652f, 38.885f, 40.113f, 41.337f, 42.557f, 43.773f,
        44.985f, 46.194f, 47.4f, 48.602f, 49.802f, 50.998f, 52.192f, 53.384f, 54.572f, 55.758f,
        56.942f, 58.124f, 59.304f, 60.481f, 61.656f, 62.83f, 64.001f, 65.171f, 66.339f, 67.505f
      )

  @Test
  fun testRandomSampling() {
    val dir = newDirectory()
    val taxoDir = newDirectory()

    val random = random()
    val taxoWriter = DirectoryTaxonomyWriter(taxoDir)
    val writer = RandomIndexWriter(random, dir)

    val config = FacetsConfig()

    val numCategories = 10
    val numDocs = atLeast(10000)
    for (i in 0 until numDocs) {
      val doc = Document()
      doc.add(StringField("EvenOdd", if (i % 2 == 0) "even" else "odd", Store.NO))
      doc.add(FacetField("iMod10", (i % numCategories).toString()))
      writer.addDocument(config.build(taxoWriter, doc))
    }
    writer.forceMerge(CHI_SQUARE_VALUES.size - 1)

    // NRT open
    val searcher = newSearcher(writer.reader)
    val taxoReader = DirectoryTaxonomyReader(taxoWriter)
    IOUtils.close(writer, taxoWriter)

    // Test empty results
    var fcm =
        RandomSamplingFacetsCollector.createManager(numDocs / 10, random.nextLong())
    // There should be no divisions by zero
    val collectRandomZeroResults =
        searcher.search(TermQuery(Term("EvenOdd", "NeverMatches")), fcm)!!

    // There should be no divisions by zero and no null result
    assertNotNull(collectRandomZeroResults.getMatchingDocs())

    // There should be no results at all
    for (doc in collectRandomZeroResults.getMatchingDocs()) {
      assertEquals(0, doc.totalHits)
    }

    // Now start searching and retrieve results.

    // Use a query to select half of the documents.
    val query = TermQuery(Term("EvenOdd", "even"))

    // 10% of total docs, 20% of the hits
    fcm = RandomSamplingFacetsCollector.createManager(numDocs / 10, random.nextLong())
    val random10Percent = searcher.search(query, fcm)!!

    val matchingDocs = random10Percent.getMatchingDocs()

    // count the total hits and sampled docs, also store the number of sampled
    // docs per segment
    var totalSampledDocs = 0
    var totalHits = 0
    val numSampledDocs = IntArray(matchingDocs.size)
    //    System.out.println("numSegments=" + numSampledDocs.length);
    for (i in 0 until numSampledDocs.size) {
      val md = matchingDocs.get(i)
      val iter = md.bits.iterator()!!
      while (iter.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) ++numSampledDocs[i]
      totalSampledDocs += numSampledDocs[i]
      totalHits += md.totalHits
    }

    // compute the chi-square value for the sampled documents' distribution
    var chi_square = 0f
    for (i in 0 until numSampledDocs.size) {
      val md = matchingDocs.get(i)
      val ei = md.totalHits.toFloat() / totalHits
      if (ei > 0.0f) {
        val oi = numSampledDocs[i].toFloat() / totalSampledDocs
        chi_square += ((ei - oi).toDouble().pow(2.0) / ei).toFloat()
      }
    }

    // Verify that the chi-square value isn't too big. According to
    // http://en.wikipedia.org/wiki/Chi-squared_distribution#Table_of_.CF.872_value_vs_p-value,
    // we basically verify that there is a really small chance of hitting a very
    // bad sample (p-value < 0.05), for n-degrees of freedom. The number 'n' depends
    // on the number of segments.
    assertTrue(
        chi_square < CHI_SQUARE_VALUES[numSampledDocs.size],
        "chisquare not statistically significant enough: " + chi_square)

    // Test amortized counts - should be 5X the sampled count, but maximum numDocs/10
    val random10FacetCounts =
        FastTaxonomyFacetCounts(taxoReader, config, random10Percent)
    val random10Result = random10FacetCounts.getTopChildren(10, "iMod10")!!
    val amortized10Result =
        random10Percent.amortizeFacetCounts(random10Result, config, searcher)!!
    for (i in 0 until amortized10Result.labelValues.size) {
      val amortized = amortized10Result.labelValues[i]
      val sampled = random10Result.labelValues[i]
      // since numDocs may not divide by 10 exactly, allow for some slack in the amortized count
      assertEquals(
          amortized.value.toFloat(),
          minOf(5 * sampled.value.toFloat(), numDocs / 10f),
          1.0f)
    }

    IOUtils.close(searcher.indexReader, taxoReader, dir, taxoDir)
  }
}
