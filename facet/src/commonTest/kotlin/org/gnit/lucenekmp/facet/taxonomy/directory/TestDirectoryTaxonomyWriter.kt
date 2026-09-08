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
package org.gnit.lucenekmp.facet.taxonomy.directory

import okio.IOException
import kotlin.test.*
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.ReentrantLock

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.facet.DrillDownQuery
import org.gnit.lucenekmp.facet.FacetField
import org.gnit.lucenekmp.facet.FacetTestCase
import org.gnit.lucenekmp.facet.FacetsConfig
import org.gnit.lucenekmp.facet.taxonomy.FacetLabel
import org.gnit.lucenekmp.facet.taxonomy.ParallelTaxonomyArrays
import org.gnit.lucenekmp.facet.taxonomy.TaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyWriter.MemoryOrdinalMap
import org.gnit.lucenekmp.facet.taxonomy.writercache.LruTaxonomyWriterCache
import org.gnit.lucenekmp.facet.taxonomy.writercache.TaxonomyWriterCache
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.index.SegmentInfos
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils

@OptIn(ExperimentalAtomicApi::class)
class TestDirectoryTaxonomyWriter : FacetTestCase() {

  // A No-Op TaxonomyWriterCache which always discards all given categories, and
  // always returns true in put(), to indicate some cache entries were cleared.
  private val NO_OP_CACHE = object : TaxonomyWriterCache {
    override fun close() {}
    override fun get(categoryPath: FacetLabel): Int = -1
    override fun put(categoryPath: FacetLabel, ordinal: Int): Boolean = true
    override fun isFull(): Boolean = true
    override fun clear() {}
    override fun size(): Int = 0
  }

  @Test
  fun testCommit() {
    // Verifies that nothing is committed to the underlying Directory, if
    // commit() wasn't called.
    var dir = newDirectory()
    var ltw =
        DirectoryTaxonomyWriter(dir, OpenMode.CREATE_OR_APPEND, NO_OP_CACHE)
    assertFalse(DirectoryReader.indexExists(dir))
    ltw.commit() // first commit, so that an index will be created
    ltw.addCategory(FacetLabel("a"))

    var r = DirectoryReader.open(dir)
    assertEquals(1, r.numDocs(), "No categories should have been committed to the underlying directory")
    r.close()
    ltw.close()
    dir.close()
  }

  @Test
  fun testCommitUserData() {
    // Verifies taxonomy commit data
    var dir = newDirectory()
    var taxoWriter =
        DirectoryTaxonomyWriter(dir, OpenMode.CREATE_OR_APPEND, NO_OP_CACHE)
    assertTrue(taxoWriter.getCache() == NO_OP_CACHE)
    taxoWriter.addCategory(FacetLabel("a"))
    taxoWriter.addCategory(FacetLabel("b"))
    var userCommitData = HashMap<String, String>()
    userCommitData.put("testing", "1 2 3")
    taxoWriter.setLiveCommitData(userCommitData.entries)
    taxoWriter.close()
    var r = DirectoryReader.open(dir)
    assertEquals(3, r.numDocs(), "2 categories plus root should have been committed to the underlying directory")
    var readUserCommitData = r.indexCommit.userData
    assertTrue("1 2 3".equals(readUserCommitData.get("testing")), "wrong value extracted from commit data")
    assertNotNull(readUserCommitData.get(DirectoryTaxonomyWriter.INDEX_EPOCH), DirectoryTaxonomyWriter.INDEX_EPOCH + " not found in commitData")
    r.close()

    // open DirTaxoWriter again and commit, INDEX_EPOCH should still exist
    // in the commit data, otherwise DirTaxoReader.refresh() might not detect
    // that the taxonomy index has been recreated.
    taxoWriter = DirectoryTaxonomyWriter(dir, OpenMode.CREATE_OR_APPEND, NO_OP_CACHE)
    taxoWriter.addCategory(FacetLabel("c")) // add a category so that commit will happen
    taxoWriter.setLiveCommitData(
        hashMapOf("just" to "data").entries)
    taxoWriter.commit()

    // verify taxoWriter.getCommitData()
    var data = HashMap<String, String>()
    val iter = taxoWriter.getLiveCommitData()
    if (iter != null) {
      for (ent in iter) {
        data.put(ent.key, ent.value)
      }
    }

    assertNotNull(data.get(DirectoryTaxonomyWriter.INDEX_EPOCH), DirectoryTaxonomyWriter.INDEX_EPOCH + " not found in taoxWriter.commitData")
    taxoWriter.close()

    r = DirectoryReader.open(dir)
    readUserCommitData = r.indexCommit.userData
    assertNotNull(readUserCommitData.get(DirectoryTaxonomyWriter.INDEX_EPOCH), DirectoryTaxonomyWriter.INDEX_EPOCH + " not found in commitData")
    r.close()

    dir.close()
  }

  @Test
  fun testRollback() {
    // Verifies that if rollback is called, DTW is closed.
    var dir = newDirectory()
    var dtw = DirectoryTaxonomyWriter(dir)
    assertTrue(dtw.getCache() is LruTaxonomyWriterCache)
    dtw.addCategory(FacetLabel("a"))
    dtw.rollback()
    // should not have succeeded to add a category following rollback.
    expectThrows(AlreadyClosedException::class) {
          dtw.addCategory(FacetLabel("a"))
        }

    dir.close()
  }

  @Test
  fun testRecreateRollback() {
    // Tests rollback with OpenMode.CREATE
    var dir = newDirectory()
    DirectoryTaxonomyWriter(dir).close()
    assertEquals(1L, getEpoch(dir))
    DirectoryTaxonomyWriter(dir, OpenMode.CREATE).rollback()
    assertEquals(1L, getEpoch(dir))

    dir.close()
  }

  @Test
  fun testEnsureOpen() {
    // verifies that an exception is thrown if DTW was closed
    var dir = newDirectory()
    var dtw = DirectoryTaxonomyWriter(dir)
    dtw.close()
    // should not succeed to add a category following close.
    expectThrows(AlreadyClosedException::class) {
          dtw.addCategory(FacetLabel("a"))
        }

    dir.close()
  }

  private fun touchTaxo(taxoWriter: DirectoryTaxonomyWriter, cp: FacetLabel) {
    taxoWriter.addCategory(cp)
    taxoWriter.setLiveCommitData(
        hashMapOf("just" to "data").entries)
    taxoWriter.commit()
  }

  @Test
  fun testRecreateAndRefresh() {
    // DirTaxoWriter lost the INDEX_EPOCH property if it was opened in
    // CREATE_OR_APPEND (or commit(userData) called twice), which could lead to
    // DirTaxoReader succeeding to refresh().
    newDirectory().use { dir ->

      var taxoWriter =
          DirectoryTaxonomyWriter(dir, OpenMode.CREATE_OR_APPEND, NO_OP_CACHE)
      touchTaxo(taxoWriter, FacetLabel("a"))

      var taxoReader = DirectoryTaxonomyReader(dir)

      touchTaxo(taxoWriter, FacetLabel("b"))

      var newtr = TaxonomyReader.openIfChanged(taxoReader)!!
      taxoReader.close()
      taxoReader = newtr
      assertEquals(
          1,
          taxoReader.getCommitUserData().get(DirectoryTaxonomyWriter.INDEX_EPOCH)!!.toInt())

      // now recreate the taxonomy, and check that the epoch is preserved after opening DirTW again.
      taxoWriter.close()

      taxoWriter = DirectoryTaxonomyWriter(dir, OpenMode.CREATE, NO_OP_CACHE)
      touchTaxo(taxoWriter, FacetLabel("c"))
      taxoWriter.close()

      taxoWriter = DirectoryTaxonomyWriter(dir, OpenMode.CREATE_OR_APPEND, NO_OP_CACHE)
      touchTaxo(taxoWriter, FacetLabel("d"))
      taxoWriter.close()

      newtr = TaxonomyReader.openIfChanged(taxoReader)!!
      taxoReader.close()
      taxoReader = newtr
      assertEquals(
          2,
          taxoReader.getCommitUserData().get(DirectoryTaxonomyWriter.INDEX_EPOCH)!!.toInt())
      taxoReader.close()
    }
  }

  @Test
  fun testBackwardsCompatibility() {
    // tests that if the taxonomy index doesn't have the INDEX_EPOCH
    // property (supports pre-3.6 indexes), all still works.
    var dir = newDirectory()

    // create an empty index first, so that DirTaxoWriter initializes indexEpoch to 1.
    IndexWriter(dir, IndexWriterConfig()).close()

    var taxoWriter =
        DirectoryTaxonomyWriter(dir, OpenMode.CREATE_OR_APPEND, NO_OP_CACHE)
    taxoWriter.close()

    var taxoReader = DirectoryTaxonomyReader(dir)
    assertEquals(
        1,
        taxoReader.getCommitUserData().get(DirectoryTaxonomyWriter.INDEX_EPOCH)!!.toInt())
    assertNull(TaxonomyReader.openIfChanged(taxoReader))
    taxoReader.close()

    dir.close()
  }

  @Test
  fun testConcurrency() {
    var ncats =
        if (TEST_NIGHTLY) atLeast(100000) else atLeast(1000) // at night, add many categories
    var range = ncats * 3 // affects the categories selection
    val numCats = AtomicInt(ncats)
    var dir = newDirectory()
    val values = HashMap<String, String>()
    val valuesLock = ReentrantLock()
    var d = random().nextDouble()
    val cache: TaxonomyWriterCache
    if (d < 0.7) {
      // same as LruTaxonomyWriterCache but with the default cache size
      cache = DirectoryTaxonomyWriter.defaultTaxonomyWriterCache()
    } else if (TEST_NIGHTLY && d > 0.98) {
      // this is the slowest, but tests the writer concurrency when no caching is done.
      // only pick it during NIGHTLY tests, and even then, with very low chances.
      cache = NO_OP_CACHE
    } else {
      // this is slower than UTF8, but less memory consuming, and exercises finding categories on
      // disk too.
      cache = LruTaxonomyWriterCache(ncats / 10)
    }
    if (VERBOSE) {
      println("TEST: use cache=" + cache)
    }
    var tw = DirectoryTaxonomyWriter(dir, OpenMode.CREATE, cache)
    val addThreads = arrayOfNulls<Thread>(TestUtil.nextInt(random(), 1, 12))
    for (z in 0 until addThreads.size) {
      addThreads[z] =
          Thread {
                var random = random()
                while (numCats.decrementAndFetch() > 0) {
                  try {
                    var value = random.nextInt(range)
                    var cp =
                        FacetLabel(
                            (value / 1000).toString(),
                            (value / 10000).toString(),
                            (value / 100000).toString(),
                            (value).toString())
                    var ord = tw.addCategory(cp)
                    assertTrue(tw.getParent(ord) != -1, "invalid parent for ordinal " + ord + ", category " + cp)
                    var l1 = FacetsConfig.pathToString(cp.components, 1)
                    var l2 = FacetsConfig.pathToString(cp.components, 2)
                    var l3 = FacetsConfig.pathToString(cp.components, 3)
                    var l4 = FacetsConfig.pathToString(cp.components, 4)
                    valuesLock.lock()
                    try {
                    values.put(l1, l1)
                    values.put(l2, l2)
                    values.put(l3, l3)
                    values.put(l4, l4)
                    } finally { valuesLock.unlock() }
                  } catch (e: IOException) {
                    throw RuntimeException(e)
                  }
                }
              }
    }

    for (t in addThreads) t!!.start()
    for (t in addThreads) t!!.join()
    tw.close()

    var dtr = DirectoryTaxonomyReader(dir)
    // +1 for root category
    if (values.size + 1 != dtr.getSize()) {
      for (value in values.keys) {
        var label = FacetLabel(*FacetsConfig.stringToPath(value))
        if (dtr.getOrdinal(label) == -1) {
          println("FAIL: path=" + label + " not recognized")
        }
      }
      fail("mismatch number of categories")
    }

    var parents = dtr.getParallelTaxonomyArrays().parents()
    for (cat in values.keys) {
      var cp = FacetLabel(*FacetsConfig.stringToPath(cat))
      assertTrue(dtr.getOrdinal(cp) > 0, "category not found " + cp)
      var level = cp.length
      var parentOrd = 0 // for root, parent is always virtual ROOT (ord=0)
      var path = FacetLabel()
      for (i in 0 until level) {
        path = cp.subpath(i + 1)
        var ord = dtr.getOrdinal(path)
        assertEquals(parentOrd, parents.get(ord), "invalid parent for cp=" + path)
        parentOrd = ord // next level should have this parent
      }
    }

    IOUtils.close(dtr, dir)
  }

  private fun getEpoch(taxoDir: Directory): Long {
    var infos = SegmentInfos.readLatestCommit(taxoDir)
    return infos.userData.get(DirectoryTaxonomyWriter.INDEX_EPOCH)!!.toLong()
  }

  @Test
  fun testReplaceTaxonomy() {
    var input = newDirectory()
    var taxoWriter = DirectoryTaxonomyWriter(input)
    var ordA = taxoWriter.addCategory(FacetLabel("a"))
    taxoWriter.close()

    var dir = newDirectory()
    taxoWriter = DirectoryTaxonomyWriter(dir)
    var ordB = taxoWriter.addCategory(FacetLabel("b"))
    taxoWriter.addCategory(FacetLabel("c"))
    taxoWriter.commit()

    var origEpoch = getEpoch(dir)

    // replace the taxonomy with the input one
    taxoWriter.replaceTaxonomy(input)

    // LUCENE-4633: make sure that category "a" is not added again in any case
    taxoWriter.addTaxonomy(input, MemoryOrdinalMap())
    assertEquals(2, taxoWriter.getSize(), "no categories should have been added") // root + 'a'
    assertEquals(ordA, taxoWriter.addCategory(FacetLabel("a")), "category 'a' received new ordinal?")

    // add the same category again -- it should not receive the same ordinal !
    var newOrdB = taxoWriter.addCategory(FacetLabel("b"))
    assertNotSame(ordB, newOrdB, "new ordinal cannot be the original ordinal")
    assertEquals(2, newOrdB, "ordinal should have been 2 since only one category was added by replaceTaxonomy")

    taxoWriter.close()

    var newEpoch = getEpoch(dir)
    assertTrue(origEpoch < newEpoch, "index epoch should have been updated after replaceTaxonomy")

    dir.close()
    input.close()
  }

  @Test
  fun testReaderFreshness() {
    // ensures that the internal index reader is always kept fresh. Previously,
    // this simple scenario failed, if the cache just evicted the category that
    // is being added.
    var dir = newDirectory()
    var taxoWriter =
        DirectoryTaxonomyWriter(dir, OpenMode.CREATE, NO_OP_CACHE)
    var o1 = taxoWriter.addCategory(FacetLabel("a"))
    var o2 = taxoWriter.addCategory(FacetLabel("a"))
    assertTrue(o1 == o2, "ordinal for same category that is added twice should be the same !")
    taxoWriter.close()
    dir.close()
  }

  @Test
  fun testCommitNoEmptyCommits() {
    // LUCENE-4972: DTW used to create empty commits even if no changes were made
    var dir = newDirectory()
    var taxoWriter = DirectoryTaxonomyWriter(dir)
    taxoWriter.addCategory(FacetLabel("a"))
    taxoWriter.commit()

    var gen1 = SegmentInfos.getLastCommitGeneration(dir)
    taxoWriter.commit()
    var gen2 = SegmentInfos.getLastCommitGeneration(dir)
    assertEquals(gen1, gen2, "empty commit should not have changed the index")

    taxoWriter.close()
    dir.close()
  }

  @Test
  fun testCloseNoEmptyCommits() {
    // LUCENE-4972: DTW used to create empty commits even if no changes were made
    var dir = newDirectory()
    var taxoWriter = DirectoryTaxonomyWriter(dir)
    taxoWriter.addCategory(FacetLabel("a"))
    taxoWriter.commit()

    var gen1 = SegmentInfos.getLastCommitGeneration(dir)
    taxoWriter.close()
    var gen2 = SegmentInfos.getLastCommitGeneration(dir)
    assertEquals(gen1, gen2, "empty commit should not have changed the index")

    taxoWriter.close()
    dir.close()
  }

  @Test
  fun testPrepareCommitNoEmptyCommits() {
    // LUCENE-4972: DTW used to create empty commits even if no changes were made
    var dir = newDirectory()
    var taxoWriter = DirectoryTaxonomyWriter(dir)
    taxoWriter.addCategory(FacetLabel("a"))
    taxoWriter.prepareCommit()
    taxoWriter.commit()

    var gen1 = SegmentInfos.getLastCommitGeneration(dir)
    taxoWriter.prepareCommit()
    taxoWriter.commit()
    var gen2 = SegmentInfos.getLastCommitGeneration(dir)
    assertEquals(gen1, gen2, "empty commit should not have changed the index")

    taxoWriter.close()
    dir.close()
  }

  // TODO: this test can hit pathological cases: it adds only a few docs, what is going on?
  @Test
    fun testHugeLabel() {
    if (!TEST_NIGHTLY) return
    var indexDir = newDirectory()
    var taxoDir = newDirectory()
    var indexWriter =
        IndexWriter(indexDir, newIndexWriterConfig(MockAnalyzer(random())))
    var taxoWriter = DirectoryTaxonomyWriter(taxoDir, OpenMode.CREATE)
    var config = FacetsConfig()

    // Add one huge label:
    var bigs: String? = null
    var ordinal = -1

    var len = FacetLabel.MAX_CATEGORY_PATH_LENGTH - 4 // for the dimension and separator
    bigs = TestUtil.randomSimpleString(random(), len, len)
    var ff = FacetField("dim", bigs)
    var cp = FacetLabel("dim", bigs)
    ordinal = taxoWriter.addCategory(cp)
    var doc = Document()
    doc.add(ff)
    indexWriter.addDocument(config.build(taxoWriter, doc))

    // Add tiny ones to cause a re-hash
    for (i in 0 until 3) {
      var s = TestUtil.randomSimpleString(random(), 1, 10)
      taxoWriter.addCategory(FacetLabel("dim", s))
      doc = Document()
      doc.add(FacetField("dim", s))
      indexWriter.addDocument(config.build(taxoWriter, doc))
    }

    // when too large components were allowed to be added, this resulted in a new added category
    assertEquals(ordinal, taxoWriter.addCategory(cp))

    indexWriter.close()
    IOUtils.close(taxoWriter)

    var indexReader = DirectoryReader.open(indexDir)
    var taxoReader = DirectoryTaxonomyReader(taxoDir)
    var searcher = IndexSearcher(indexReader)
    var ddq = DrillDownQuery(FacetsConfig())
    ddq.add("dim", bigs)
    assertEquals(1L, searcher.search(ddq, 10).totalHits.value)

    IOUtils.close(indexReader, taxoReader, indexDir, taxoDir)
  }

  @Test
  fun testReplaceTaxoWithLargeTaxonomy() {
    var srcTaxoDir = newDirectory()
    var targetTaxoDir = newDirectory()

    // build source, large, taxonomy
    var taxoWriter = DirectoryTaxonomyWriter(srcTaxoDir)
    var ord = taxoWriter.addCategory(FacetLabel("A", "1", "1", "1", "1", "1", "1"))
    taxoWriter.close()

    taxoWriter = DirectoryTaxonomyWriter(targetTaxoDir)
    var ordinal = taxoWriter.addCategory(FacetLabel("B", "1"))
    assertEquals(1, taxoWriter.getParent(ordinal)) // call getParent to initialize taxoArrays
    taxoWriter.commit()

    taxoWriter.replaceTaxonomy(srcTaxoDir)
    assertEquals(ord - 1, taxoWriter.getParent(ord))
    taxoWriter.close()

    srcTaxoDir.close()
    targetTaxoDir.close()
  }
}
