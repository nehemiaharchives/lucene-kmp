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
package org.gnit.lucenekmp.facet.taxonomy

import okio.IOException
import kotlin.test.*
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import org.gnit.lucenekmp.jdkport.Thread

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.facet.FacetField
import org.gnit.lucenekmp.facet.FacetResult
import org.gnit.lucenekmp.facet.FacetTestCase
import org.gnit.lucenekmp.facet.Facets
import org.gnit.lucenekmp.facet.FacetsCollector
import org.gnit.lucenekmp.facet.FacetsCollectorManager
import org.gnit.lucenekmp.facet.FacetsConfig
import org.gnit.lucenekmp.facet.taxonomy.SearcherTaxonomyManager.SearcherAndTaxonomy
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyWriter
import org.gnit.lucenekmp.index.IndexNotFoundException
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.SegmentInfos
import org.gnit.lucenekmp.index.TieredMergePolicy
import org.gnit.lucenekmp.search.MatchAllDocsQuery
import org.gnit.lucenekmp.search.ReferenceManager
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils

// SimpleText is not enabled by the common test framework.
@OptIn(ExperimentalAtomicApi::class)
class TestSearcherTaxonomyManager : FacetTestCase() {

  private class IndexerThread(
      private var w: IndexWriter,
      private var config: FacetsConfig,
      private var tw: TaxonomyWriter,
      private var mgr: ReferenceManager<SearcherAndTaxonomy>?,
      private var ordLimit: Int,
      private var stop: AtomicBoolean
  ) : Thread() {

    override fun run() {
      try {
        var seen = HashSet<String>()
        var paths = ArrayList<String>()
        while (true) {
          var doc = Document()
          var numPaths = TestUtil.nextInt(random(), 1, 5)
          for (i in 0 until numPaths) {
            var path: String
            if (!paths.isEmpty() && random().nextInt(5) != 4) {
              // Use previous path
              path = paths.get(random().nextInt(paths.size))
            } else {
              // Create new path
              path = ""
              while (true) {
                path = TestUtil.randomRealisticUnicodeString(random())
                if (path.length != 0 && !seen.contains(path)) {
                  seen.add(path)
                  paths.add(path)
                  break
                }
              }
            }
            doc.add(FacetField("field", path))
          }
          try {
            w.addDocument(config.build(tw, doc))
            if (mgr != null && random().nextDouble() < 0.02) {
              w.commit()
              tw.commit()
              mgr!!.maybeRefresh()
            }
          } catch (ioe: IOException) {
            throw RuntimeException(ioe)
          }

          if (VERBOSE) {
            println("TW size=" + tw.getSize() + " vs " + ordLimit)
          }

          if (tw.getSize() >= ordLimit) {
            break
          }
        }
      } finally {
        stop.store(true)
      }
    }
  }

  @Test
  fun testNRT() {
    var dir = newDirectory()
    var taxoDir = newDirectory()
    var iwc = newIndexWriterConfig(MockAnalyzer(random()))
    // Don't allow tiny maxBufferedDocs; it can make this
    // test too slow:
    iwc.setMaxBufferedDocs(maxOf(500, iwc.maxBufferedDocs))

    // MockRandom/AlcololicMergePolicy are too slow:
    var tmp = TieredMergePolicy()
    tmp.setFloorSegmentMB(0.001)
    iwc.setMergePolicy(tmp)
    var w = IndexWriter(dir, iwc)
    var tw = DirectoryTaxonomyWriter(taxoDir)
    var config = FacetsConfig()
    config.setMultiValued("field", true)
    var stop = AtomicBoolean(false)

    // How many unique facets to index before stopping:
    var ordLimit = if (TEST_NIGHTLY) 100000 else 6000

    var indexer = IndexerThread(w, config, tw, null, ordLimit, stop)

    var mgr = SearcherTaxonomyManager(w, true, null, tw)

    var reopener =
        object : Thread() {
          override fun run() {
            while (!stop.load()) {
              try {
                // Sleep for up to 20 msec:
                Thread.sleep(random().nextInt(20).toLong())

                if (VERBOSE) {
                  println("TEST: reopen")
                }

                mgr!!.maybeRefresh()

                if (VERBOSE) {
                  println("TEST: reopen done")
                }
              } catch (ioe: Exception) {
                throw RuntimeException(ioe)
              }
            }
          }
        }

    reopener.setName("reopener")
    reopener.start()

    indexer.setName("indexer")
    indexer.start()

    try {
      while (!stop.load()) {
        var pair = mgr.acquire()
        try {
          // System.out.println("search maxOrd=" + pair.taxonomyReader.getSize());
          var sfc =
              pair.searcher.search(MatchAllDocsQuery(), FacetsCollectorManager())
          var facets = getTaxonomyFacetCounts(pair.taxonomyReader, config, sfc!!)
          var result = facets.getTopChildren(10, "field")
          if (pair.searcher.indexReader.numDocs() > 0) {
            // System.out.println(pair.taxonomyReader.getSize());
            assertTrue(result!!.childCount > 0)
            assertTrue(result.labelValues.size > 0)
          }

          // if (VERBOSE) {
          // System.out.println("TEST: facets=" + FacetTestUtils.toString(results.get(0)));
          // }
        } finally {
          mgr.release(pair)
        }
      }
    } finally {
      indexer.join()
      reopener.join()
    }

    if (VERBOSE) {
      println("TEST: now stop")
    }

    w.close()
    IOUtils.close(mgr, tw, taxoDir, dir)
  }

  @Test
  fun testDirectory() {
    var indexDir = newDirectory()
    var taxoDir = newDirectory()
    var w =
        IndexWriter(indexDir, newIndexWriterConfig(MockAnalyzer(random())))
    var tw = DirectoryTaxonomyWriter(taxoDir)
    // first empty commit
    w.commit()
    tw.commit()
    var mgr = SearcherTaxonomyManager(indexDir, taxoDir, null)
    var config = FacetsConfig()
    config.setMultiValued("field", true)
    var stop = AtomicBoolean(false)

    // How many unique facets to index before stopping:
    var ordLimit = if (TEST_NIGHTLY) 100000 else 600

    var indexer = IndexerThread(w, config, tw, mgr, ordLimit, stop)
    indexer.start()

    try {
      while (!stop.load()) {
        var pair = mgr.acquire()
        try {
          // System.out.println("search maxOrd=" + pair.taxonomyReader.getSize());
          var sfc =
              pair.searcher.search(MatchAllDocsQuery(), FacetsCollectorManager())
          var facets = getTaxonomyFacetCounts(pair.taxonomyReader, config, sfc!!)
          var result = facets.getTopChildren(10, "field")
          if (pair.searcher.indexReader.numDocs() > 0) {
            // System.out.println(pair.taxonomyReader.getSize());
            assertTrue(result!!.childCount > 0)
            assertTrue(result.labelValues.size > 0)
          }

          // if (VERBOSE) {
          // System.out.println("TEST: facets=" + FacetTestUtils.toString(results.get(0)));
          // }
        } finally {
          mgr.release(pair)
        }
      }
    } finally {
      indexer.join()
    }

    if (VERBOSE) {
      println("TEST: now stop")
    }

    w.close()
    IOUtils.close(mgr, tw, taxoDir, indexDir)
  }

  @Test
  fun testReplaceTaxonomyNRT() {
    var dir = newDirectory()
    var taxoDir = newDirectory()
    var w = IndexWriter(dir, newIndexWriterConfig(MockAnalyzer(random())))
    var tw = DirectoryTaxonomyWriter(taxoDir)

    var taxoDir2 = newDirectory()
    var tw2 = DirectoryTaxonomyWriter(taxoDir2)
    tw2.close()

    var mgr = SearcherTaxonomyManager(w, true, null, tw)
    w.addDocument(Document())
    tw.replaceTaxonomy(taxoDir2)
    taxoDir2.close()

    expectThrows(IllegalStateException::class) { mgr.maybeRefresh() }

    w.close()
    IOUtils.close(mgr, tw, taxoDir, dir)
  }

  @Test
  fun testReplaceTaxonomyDirectory() {
    var indexDir = newDirectory()
    var taxoDir = newDirectory()
    var w = IndexWriter(indexDir, newIndexWriterConfig(MockAnalyzer(random())))
    var tw = DirectoryTaxonomyWriter(taxoDir)
    w.commit()
    tw.commit()

    var taxoDir2 = newDirectory()
    var tw2 = DirectoryTaxonomyWriter(taxoDir2)
    tw2.addCategory(FacetLabel("a", "b"))
    tw2.close()

    var mgr = SearcherTaxonomyManager(indexDir, taxoDir, null)
    var pair = mgr.acquire()
    try {
      assertEquals(1, pair.taxonomyReader.getSize())
    } finally {
      mgr.release(pair)
    }

    w.addDocument(Document())
    tw.replaceTaxonomy(taxoDir2)
    taxoDir2.close()
    w.commit()
    tw.commit()

    mgr!!.maybeRefresh()
    pair = mgr.acquire()
    try {
      assertEquals(3, pair.taxonomyReader.getSize())
    } finally {
      mgr.release(pair)
    }

    w.close()
    IOUtils.close(mgr, tw, taxoDir, indexDir)
  }

  @Test
  fun testExceptionDuringRefresh() {

    var indexDir = newDirectory()
    var taxoDir = newDirectory()

    var w = IndexWriter(indexDir, newIndexWriterConfig(MockAnalyzer(random())))
    var tw = DirectoryTaxonomyWriter(taxoDir)
    w.commit()
    tw.commit()

    var mgr = SearcherTaxonomyManager(indexDir, taxoDir, null)

    tw.addCategory(FacetLabel("a", "b"))
    w.addDocument(Document())

    tw.commit()
    w.commit()

    // intentionally corrupt the taxo index:
    var infos = SegmentInfos.readLatestCommit(taxoDir)
    taxoDir.deleteFile(infos.segmentsFileName!!)
    expectThrows(IndexNotFoundException::class) { mgr.maybeRefreshBlocking() }
    IOUtils.close(w, tw, mgr, indexDir, taxoDir)
  }
}
