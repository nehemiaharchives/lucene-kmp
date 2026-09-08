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

import org.gnit.lucenekmp.jdkport.ExecutorService
import org.gnit.lucenekmp.jdkport.Executors
import org.gnit.lucenekmp.facet.sortedset.SortedSetDocValuesReaderState
import org.gnit.lucenekmp.facet.taxonomy.TaxonomyReader
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.util.NamedThreadFactory
import kotlin.test.BeforeTest
import kotlin.test.AfterTest

class TestParallelDrillSideways : TestDrillSideways() {
  private var executor: ExecutorService? = null

  @BeforeTest
  fun prepareExecutor() {
    executor = Executors.newCachedThreadPool(NamedThreadFactory("TestParallelDrillSideways"))
  }

  @AfterTest
  fun shutdownExecutor() {
    executor!!.shutdown()
    executor = null
  }

  override fun getNewDrillSideways(
      searcher: IndexSearcher, config: FacetsConfig, state: SortedSetDocValuesReaderState): DrillSideways {
    return DrillSideways(searcher, config, null, state, executor)
  }

  override fun getNewDrillSideways(
      searcher: IndexSearcher, config: FacetsConfig, taxoReader: TaxonomyReader): DrillSideways {
    return DrillSideways(searcher, config, taxoReader, null, executor)
  }

  override fun getNewDrillSidewaysScoreSubdocsAtOnce(
      searcher: IndexSearcher, config: FacetsConfig, taxoReader: TaxonomyReader): DrillSideways {
    return object : DrillSideways(searcher, config, taxoReader, null, executor) {
      override fun scoreSubDocsAtOnce(): Boolean {
        return true
      }
    }
  }

  override fun getNewDrillSidewaysBuildFacetsResult(
      searcher: IndexSearcher, config: FacetsConfig, taxoReader: TaxonomyReader): DrillSideways {
    return object : DrillSideways(searcher, config, taxoReader, null, executor) {
      override fun buildFacetsResult(
          drillDowns: FacetsCollector?, drillSideways: Array<FacetsCollector>?,
          drillSidewaysDims: Array<String>?): Facets {
        val drillSidewaysFacets = HashMap<String, Facets>()
        val drillDownFacets = getTaxonomyFacetCounts(taxoReader, config, drillDowns!!)
        if (drillSideways != null) {
          for (i in drillSideways.indices) {
            drillSidewaysFacets.put(
                drillSidewaysDims!![i], getTaxonomyFacetCounts(taxoReader, config, drillSideways[i]))
          }
        }
        if (drillSidewaysFacets.isEmpty()) {
          return drillDownFacets
        } else {
          return MultiFacets(drillSidewaysFacets, drillDownFacets)
        }
      }
    }
  }
}
