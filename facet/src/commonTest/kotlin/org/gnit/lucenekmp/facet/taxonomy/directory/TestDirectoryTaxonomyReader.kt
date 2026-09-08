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
import kotlin.random.Random
import kotlin.test.*
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.tests.util.TestUtil

import org.gnit.lucenekmp.facet.taxonomy.TaxonomyReader.Companion.INVALID_ORDINAL

import org.gnit.lucenekmp.facet.FacetTestCase
import org.gnit.lucenekmp.facet.taxonomy.FacetLabel
import org.gnit.lucenekmp.facet.taxonomy.ParallelTaxonomyArrays
import org.gnit.lucenekmp.facet.taxonomy.TaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.TaxonomyReader.ChildrenIterator
import org.gnit.lucenekmp.facet.taxonomy.TaxonomyWriter
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.index.LogByteSizeMergePolicy
import org.gnit.lucenekmp.index.LogMergePolicy
import org.gnit.lucenekmp.store.AlreadyClosedException
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.util.IOUtils

class TestDirectoryTaxonomyReader : FacetTestCase() {

  private val ILLEGAL_PATH =
      FacetLabel("PATH_THAT_CAUSED_IllegalArgumentException")

  @Test
  fun testCloseAfterIncRef() {
    var dir = newDirectory()
    var ltw = DirectoryTaxonomyWriter(dir)
    ltw.addCategory(FacetLabel("a"))
    ltw.close()

    var ltr = DirectoryTaxonomyReader(dir)
    ltr.incRef()
    ltr!!.close()

    // should not fail as we incRef() before close
    ltr.getSize()
    ltr.decRef()

    dir.close()
  }

  @Test
  fun testCloseTwice() {
    var dir = newDirectory()
    var ltw = DirectoryTaxonomyWriter(dir)
    ltw.addCategory(FacetLabel("a"))
    ltw.close()

    var ltr = DirectoryTaxonomyReader(dir)
    ltr!!.close()
    ltr!!.close() // no exception should be thrown

    dir.close()
  }

  @Test
  fun testOpenIfChangedResult() {
    var dir: Directory? = null
    var ltw: DirectoryTaxonomyWriter? = null
    var ltr: DirectoryTaxonomyReader? = null

    try {
      dir = newDirectory()
      ltw = DirectoryTaxonomyWriter(dir)

      ltw.addCategory(FacetLabel("a"))
      ltw.commit()

      ltr = DirectoryTaxonomyReader(dir)
      assertNull(TaxonomyReader.openIfChanged(ltr), "Nothing has changed")

      ltw.addCategory(FacetLabel("b"))
      ltw.commit()

      var newtr = TaxonomyReader.openIfChanged(ltr)
      assertNotNull(newtr, "changes were committed")
      assertNull(TaxonomyReader.openIfChanged(newtr), "Nothing has changed")
      newtr!!.close()
    } finally {
      IOUtils.close(ltw, ltr, dir)
    }
  }

  @Test
  fun testAlreadyClosed() {
    var dir = newDirectory()
    var ltw = DirectoryTaxonomyWriter(dir)
    ltw.addCategory(FacetLabel("a"))
    ltw.close()

    var ltr = DirectoryTaxonomyReader(dir)
    ltr!!.close()
    expectThrows(AlreadyClosedException::class) { ltr.getSize() }

    dir.close()
  }

  /** recreating a taxonomy should work well with a freshly opened taxonomy reader */
  @Test
  fun testFreshReadRecreatedTaxonomy() {
    doTestReadRecreatedTaxonomy(random(), true)
  }

  @Test
  fun testOpenIfChangedReadRecreatedTaxonomy() {
    doTestReadRecreatedTaxonomy(random(), false)
  }

  private fun doTestReadRecreatedTaxonomy(random: Random, closeReader: Boolean) {
    var dir: Directory? = null
    var tw: TaxonomyWriter? = null
    var tr: TaxonomyReader? = null

    // prepare a few categories
    var n = 10
    val cp = Array(n) { FacetLabel() }
    for (i in 0 until n) {
      cp[i] = FacetLabel("a", (i).toString())
    }

    try {
      dir = newDirectory()

      tw = DirectoryTaxonomyWriter(dir)
      tw.addCategory(FacetLabel("a"))
      tw.close()

      tr = DirectoryTaxonomyReader(dir)
      var baseNumCategories = tr.getSize()

      for (i in 0 until n) {
        var k = random.nextInt(n)
        tw = DirectoryTaxonomyWriter(dir, OpenMode.CREATE)
        for (j in 0..k) {
          tw.addCategory(cp[j])
        }
        tw.close()
        if (closeReader) {
          tr!!.close()
          tr = DirectoryTaxonomyReader(dir)
        } else {
          var newtr = TaxonomyReader.openIfChanged(tr!!)
          assertNotNull(newtr)
          tr!!.close()
          tr = newtr
        }
        assertEquals(baseNumCategories + 1 + k, tr.getSize(), "Wrong #categories in taxonomy (i=" + i + ", k=" + k + ")")
      }
    } finally {
      IOUtils.close(tr, tw, dir)
    }
  }

  @Test
  fun testOpenIfChangedAndRefCount() {
    var dir = ByteBuffersDirectory() // no need for random directories here

    var taxoWriter = DirectoryTaxonomyWriter(dir)
    taxoWriter.addCategory(FacetLabel("a"))
    taxoWriter.commit()

    var taxoReader = DirectoryTaxonomyReader(dir)
    assertEquals(1, taxoReader.getRefCount(), "wrong refCount")

    taxoReader.incRef()
    assertEquals(2, taxoReader.getRefCount(), "wrong refCount")

    taxoWriter.addCategory(FacetLabel("a", "b"))
    taxoWriter.commit()
    var newtr = TaxonomyReader.openIfChanged(taxoReader)
    assertNotNull(newtr)
    taxoReader.close()
    taxoReader = newtr
    assertEquals(1, taxoReader.getRefCount(), "wrong refCount")

    taxoWriter.close()
    taxoReader.close()
    dir.close()
  }

  @Test
  fun testOpenIfChangedManySegments() {
    // test openIfChanged() when the taxonomy contains many segments
    var dir = newDirectory()

    var writer =
        object : DirectoryTaxonomyWriter(dir) {
          override fun createIndexWriterConfig(openMode: OpenMode): IndexWriterConfig {
            var conf = super.createIndexWriterConfig(openMode)
            var lmp = conf.mergePolicy as LogMergePolicy
            lmp.mergeFactor = 2
            return conf
          }
        }
    var reader = DirectoryTaxonomyReader(writer)

    var numRounds = random().nextInt(10) + 10
    var numCategories = 1 // one for root
    for (i in 0 until numRounds) {
      var numCats = random().nextInt(4) + 1
      for (j in 0 until numCats) {
        writer.addCategory(FacetLabel((i).toString(), (j).toString()))
      }
      numCategories += numCats + 1 /* one for round-parent */
      var newtr = TaxonomyReader.openIfChanged(reader)
      assertNotNull(newtr)
      reader.close()
      reader = newtr

      // assert categories
      assertEquals(numCategories, reader.getSize())
      var roundOrdinal = reader.getOrdinal(FacetLabel((i).toString()))
      var parents = reader.getParallelTaxonomyArrays().parents()
      assertEquals(0, parents.get(roundOrdinal)) // round's parent is root
      for (j in 0 until numCats) {
        var ord = reader.getOrdinal(FacetLabel((i).toString(), (j).toString()))
        assertEquals(roundOrdinal, parents.get(ord)) // round's parent is root
      }
    }

    reader.close()
    writer.close()
    dir.close()
  }

  @Test
  fun testOpenIfChangedMergedSegment() {
    // test openIfChanged() when all index segments were merged - used to be
    // a bug in ParentArray, caught by testOpenIfChangedManySegments - only
    // this test is not random
    var dir = newDirectory()

    // hold onto IW to forceMerge
    // note how we don't close it, since DTW will close it.
    var iw =
        IndexWriter(
            dir,
            IndexWriterConfig(MockAnalyzer(random()))
                .setMergePolicy(LogByteSizeMergePolicy()))
    var writer =
        object : DirectoryTaxonomyWriter(dir) {
          override fun openIndexWriter(directory: Directory, config: IndexWriterConfig): IndexWriter {
            return iw
          }
        }

    var reader = DirectoryTaxonomyReader(writer)
    assertEquals(1, reader.getSize())
    assertEquals(1, reader.getParallelTaxonomyArrays().parents().length())

    // add category and call forceMerge -- this should flush IW and merge segments down to 1
    // in ParentArray.initFromReader, this used to fail assuming there are no parents.
    writer.addCategory(FacetLabel("1"))
    iw.forceMerge(1)

    // now calling openIfChanged should trip on the bug
    var newtr = TaxonomyReader.openIfChanged(reader)
    assertNotNull(newtr)
    reader.close()
    reader = newtr
    assertEquals(2, reader.getSize())
    assertEquals(2, reader.getParallelTaxonomyArrays().parents().length())

    reader.close()
    writer.close()
    dir.close()
  }

  @Test
  fun testOpenIfChangedNoChangesButSegmentMerges() {
    // test openIfChanged() when the taxonomy hasn't really changed, but segments
    // were merged. The NRT reader will be reopened, and ParentArray used to assert
    // that the new reader contains more ordinals than were given from the old
    // TaxReader version
    var dir = newDirectory()

    // hold onto IW to forceMerge
    // note how we don't close it, since DTW will close it.
    var iw =
        IndexWriter(
            dir,
            IndexWriterConfig(MockAnalyzer(random()))
                .setMergePolicy(LogByteSizeMergePolicy()))
    var writer =
        object : DirectoryTaxonomyWriter(dir) {
          override fun openIndexWriter(directory: Directory, config: IndexWriterConfig): IndexWriter {
            return iw
          }
        }

    // add a category so that the following DTR open will cause a flush and
    // a new segment will be created
    writer.addCategory(FacetLabel("a"))

    var reader = DirectoryTaxonomyReader(writer)
    assertEquals(2, reader.getSize())
    assertEquals(2, reader.getParallelTaxonomyArrays().parents().length())

    // merge all the segments so that NRT reader thinks there's a change
    iw.forceMerge(1)

    // now calling openIfChanged should trip on the wrong assert in ParetArray's ctor
    var newtr = TaxonomyReader.openIfChanged(reader)
    assertNotNull(newtr)
    reader.close()
    reader = newtr
    assertEquals(2, reader.getSize())
    assertEquals(2, reader.getParallelTaxonomyArrays().parents().length())

    reader.close()
    writer.close()
    dir.close()
  }

  @Test
  fun testOpenIfChangedReuseAfterRecreate() {
    // tests that if the taxonomy is recreated, no data is reused from the previous taxonomy
    var dir = newDirectory()
    var writer = DirectoryTaxonomyWriter(dir)
    var cp_a = FacetLabel("a")
    writer.addCategory(cp_a)
    writer.close()

    var r1 = DirectoryTaxonomyReader(dir)
    // fill r1's caches
    assertPathsAndOrdinals(r1, intArrayOf(1), arrayOf(cp_a))

    // now recreate, add a different category
    writer = DirectoryTaxonomyWriter(dir, OpenMode.CREATE)
    var cp_b = FacetLabel("b")
    writer.addCategory(cp_b)
    writer.close()

    var r2 = TaxonomyReader.openIfChanged(r1)
    assertNotNull(r2)

    // fill r2's caches
    assertPathsAndOrdinals(r2, intArrayOf(1), arrayOf(cp_b))

    // check that r1 doesn't see cp_b
    assertGettingOrdinals(r1, intArrayOf(1, INVALID_ORDINAL), arrayOf(cp_a, cp_b))
    assertGettingPaths(r1, arrayOf(cp_a, ILLEGAL_PATH), intArrayOf(1, 2))

    // check that r2 doesn't see cp_a
    assertGettingOrdinals(r2, intArrayOf(INVALID_ORDINAL, 1), arrayOf(cp_a, cp_b))
    assertGettingPaths(r2, arrayOf(cp_b, ILLEGAL_PATH), intArrayOf(1, 2))

    r2.close()
    r1.close()
    dir.close()
  }

  @Test
  fun testOpenIfChangedReuse() {
    // test the reuse of data from the old DTR instance
    for (nrt in booleanArrayOf(false, true)) {
      var dir = newDirectory()
      var writer = DirectoryTaxonomyWriter(dir)

      var cp_a = FacetLabel("a")
      writer.addCategory(cp_a)
      if (!nrt) writer.commit()

      var r1 =
          if (nrt) DirectoryTaxonomyReader(writer) else DirectoryTaxonomyReader(dir)
      // fill r1's caches
      assertPathsAndOrdinals(r1, intArrayOf(1), arrayOf(cp_a))

      var cp_b = FacetLabel("b")
      writer.addCategory(cp_b)
      if (!nrt) writer.commit()

      var r2 = TaxonomyReader.openIfChanged(r1)
      assertNotNull(r2)

      // add r2's categories to the caches
      assertPathsAndOrdinals(r2, intArrayOf(1, 2), arrayOf(cp_a, cp_b))

      // check that r1 doesn't see cp_b
      assertGettingOrdinals(r1, intArrayOf(1, INVALID_ORDINAL), arrayOf(cp_a, cp_b))
      assertGettingPaths(r1, arrayOf(cp_a, ILLEGAL_PATH), intArrayOf(1, 2))

      r1.close()
      r2.close()
      writer.close()
      dir.close()
    }
  }

  @Test
  fun testOpenIfChangedReplaceTaxonomy() {
    // test openIfChanged when replaceTaxonomy is called, which is equivalent to recreate
    // only can work with NRT as well
    var src = newDirectory()
    var w = DirectoryTaxonomyWriter(src)
    var cp_b = FacetLabel("b")
    w.addCategory(cp_b)
    w.close()

    for (nrt in booleanArrayOf(false, true)) {
      var dir = newDirectory()
      var writer = DirectoryTaxonomyWriter(dir)

      var cp_a = FacetLabel("a")
      writer.addCategory(cp_a)
      if (!nrt) writer.commit()

      var r1 =
          if (nrt) DirectoryTaxonomyReader(writer) else DirectoryTaxonomyReader(dir)
      // fill r1's caches
      assertPathsAndOrdinals(r1, intArrayOf(1), arrayOf(cp_a))

      // now replace taxonomy
      writer.replaceTaxonomy(src)
      if (!nrt) writer.commit()

      var r2 = TaxonomyReader.openIfChanged(r1)
      assertNotNull(r2)

      // fill r2's caches
      assertPathsAndOrdinals(r2, intArrayOf(1), arrayOf(cp_b))

      // check that r1 doesn't see cp_b
      assertGettingOrdinals(r1, intArrayOf(1, INVALID_ORDINAL), arrayOf(cp_a, cp_b))
      assertGettingPaths(r1, arrayOf(cp_a, ILLEGAL_PATH), intArrayOf(1, 2))

      // check that r2 doesn't see cp_a
      assertGettingOrdinals(r2, intArrayOf(INVALID_ORDINAL, 1), arrayOf(cp_a, cp_b))
      assertGettingPaths(r2, arrayOf(cp_b, ILLEGAL_PATH), intArrayOf(1, 2))

      r2.close()
      r1.close()
      writer.close()
      dir.close()
    }

    src.close()
  }

  private fun assertGettingOrdinals(reader: DirectoryTaxonomyReader, expectedOrds: IntArray, sourcePaths: Array<FacetLabel>) {
    // To exercise mix of cache hit and cache misses for getOrdinal and getBulkOrdinals this method:
    // 1. Randomly gets a few ords using sequential calls.
    // 2. Call bulk get method.
    // 3. Continue sequential calls for the remaining items.
    assertEquals(expectedOrds.size, sourcePaths.size)
    var bulkOperationsIteration = random().nextInt(sourcePaths.size)
    var indexesShuffled =
        (0 until sourcePaths.size).toMutableList()
    indexesShuffled.shuffle(random())

    for (i in 0 until bulkOperationsIteration) {
      var nextIndex = indexesShuffled.get(i)
      assertEquals(expectedOrds[nextIndex], reader.getOrdinal(sourcePaths[nextIndex]))
    }

    var bulkOrdResult = reader.getBulkOrdinals(*sourcePaths)
    assertContentEquals(expectedOrds, bulkOrdResult)

    for (i in bulkOperationsIteration until sourcePaths.size) {
      var nextIndex = indexesShuffled.get(i)
      assertEquals(expectedOrds[nextIndex], reader.getOrdinal(sourcePaths[nextIndex]))
    }
  }

  private fun assertGettingPaths(reader: DirectoryTaxonomyReader, expectedPaths: Array<FacetLabel>, sourceOrds: IntArray) {
    // To exercise mix of cache hit and cache misses for getPath and getBulkPath this method:
    // 1. Randomly gets a few paths using sequential calls.
    // 2. Call bulk get method.
    // 3. Continue sequential calls for the remaining items.
    // Note: expectedPaths should refer to ILLEGAL_PATH for ords from sourceOrds that are expected
    // to throw IllegalArgumentException
    assertEquals(expectedPaths.size, sourceOrds.size)
    var bulkOperationsIteration = random().nextInt(sourceOrds.size)
    var indexesShuffled =
        (0 until sourceOrds.size).toMutableList()
    indexesShuffled.shuffle(random())

    var illegalPathExceptionIsExpected =
        expectedPaths.any { x -> x === ILLEGAL_PATH }
    for (i in 0 until bulkOperationsIteration) {
      var nextIndex = indexesShuffled.get(i)
      if (expectedPaths[nextIndex] == ILLEGAL_PATH) {
        expectThrows(IllegalArgumentException::class) { reader.getPath(sourceOrds[nextIndex]) }
      } else {
        assertEquals(expectedPaths[nextIndex], reader.getPath(sourceOrds[nextIndex]))
      }
    }

    if (illegalPathExceptionIsExpected) {
      expectThrows(IllegalArgumentException::class) { reader.getBulkPath(*sourceOrds) }
    } else {
      // clone because getBulkPath changes order of param's elements
      var sourceOrdsCopy = sourceOrds.copyOf()
      var bulkPathsResult = reader.getBulkPath(*sourceOrdsCopy)
      assertContentEquals(expectedPaths, bulkPathsResult.requireNoNulls())
    }

    for (i in bulkOperationsIteration until sourceOrds.size) {
      var nextIndex = indexesShuffled.get(i)
      if (expectedPaths[nextIndex] == ILLEGAL_PATH) {
        expectThrows(IllegalArgumentException::class) { reader.getPath(sourceOrds[nextIndex]) }
      } else {
        assertEquals(expectedPaths[nextIndex], reader.getPath(sourceOrds[nextIndex]))
      }
    }
  }

  private fun assertPathsAndOrdinals(reader: DirectoryTaxonomyReader, ords: IntArray, paths: Array<FacetLabel>) {
    // use this method to assert "symmetric" ordinals and paths: when source ords and paths match
    // expected ords and paths. This works for valid ords and paths that exist in the index.
    assertGettingPaths(reader, paths, ords)
    assertGettingOrdinals(reader, ords, paths)
  }

  @Test
  fun testGetChildren() {
    var dir = newDirectory()
    var taxoWriter = DirectoryTaxonomyWriter(dir)
    var numCategories = atLeast(10)
    var numA = 0
    var numB = 0
    var random = random()
    // add the two categories for which we'll also add children (so asserts are simpler)
    taxoWriter.addCategory(FacetLabel("a"))
    taxoWriter.addCategory(FacetLabel("b"))
    for (i in 0 until numCategories) {
      if (random.nextBoolean()) {
        taxoWriter.addCategory(FacetLabel("a", (i).toString()))
        ++numA
      } else {
        taxoWriter.addCategory(FacetLabel("b", (i).toString()))
        ++numB
      }
    }
    // add category with no children
    taxoWriter.addCategory(FacetLabel("c"))
    taxoWriter.close()

    var taxoReader = DirectoryTaxonomyReader(dir)

    // non existing category
    var it = taxoReader.getChildren(taxoReader.getOrdinal(FacetLabel("invalid")))
    assertEquals(INVALID_ORDINAL, it.next())

    // a category with no children
    it = taxoReader.getChildren(taxoReader.getOrdinal(FacetLabel("c")))
    assertEquals(INVALID_ORDINAL, it.next())

    // arbitrary negative ordinal
    it = taxoReader.getChildren(-2)
    assertEquals(INVALID_ORDINAL, it.next())

    // root's children
    var roots = hashSetOf("a", "b", "c")
    it = taxoReader.getChildren(TaxonomyReader.ROOT_ORDINAL)
    while (!roots.isEmpty()) {
      var root = taxoReader.getPath(it.next())!!
      assertEquals(1, root.length)
      assertTrue(roots.remove(root.components[0]))
    }
    assertEquals(INVALID_ORDINAL, it.next())

    for (i in 0 until 2) {
      var cp = if (i == 0) FacetLabel("a") else FacetLabel("b")
      var ordinal = taxoReader.getOrdinal(cp)
      it = taxoReader.getChildren(ordinal)
      var numChildren = 0
      var child: Int
      while (true) {
        child = it.next()
        if (child == INVALID_ORDINAL) break
        var path = taxoReader.getPath(child)!!
        assertEquals(2, path.length)
        assertEquals(path.components[0], if (i == 0) "a" else "b")
        ++numChildren
      }
      var expected = if (i == 0) numA else numB
      assertEquals(expected, numChildren, "invalid num children")
    }
    taxoReader.close()

    dir.close()
  }

  @Test
  fun testAccountable() {
    var dir = newDirectory()
    var taxoWriter = DirectoryTaxonomyWriter(dir)
    var numCategories = atLeast(10)
    var random = random()
    // add the two categories for which we'll also add children (so asserts are simpler)
    taxoWriter.addCategory(FacetLabel("a"))
    taxoWriter.addCategory(FacetLabel("b"))
    for (i in 0 until numCategories) {
      if (random.nextBoolean()) {
        taxoWriter.addCategory(FacetLabel("a", (i).toString()))
      } else {
        taxoWriter.addCategory(FacetLabel("b", (i).toString()))
      }
    }
    // add category with no children
    taxoWriter.addCategory(FacetLabel("c"))
    taxoWriter.close()

    var taxoReader = DirectoryTaxonomyReader(dir)
    taxoReader.getParallelTaxonomyArrays() // increases memory usage as a side-effect
    assertTrue(taxoReader.ramBytesUsed() > 0)
    assertTrue(taxoReader.childResources.size > 0)
    taxoReader.close()
    dir.close()
  }

  @Test
  fun testGetPathAndOrdinalsRandomMultithreading() {
    var src = newDirectory()
    var w = DirectoryTaxonomyWriter(src)
    var maxNumberOfLabelsToIndex = 1000
    var maxNumberOfUniqueLabelsToIndex = maxNumberOfLabelsToIndex / 2
    var cacheSize = maxNumberOfUniqueLabelsToIndex / 2 // to cause some cache evictions
    val randomArray = Array(TestUtil.nextInt(random(), 1, maxNumberOfLabelsToIndex)) { "" }
    // adding a smaller bound on ints ensures that we will have some duplicate ordinals in random
    // test cases
    for (i in randomArray.indices) { randomArray[i] = random().nextInt(maxNumberOfUniqueLabelsToIndex).toString() }

    val allPaths = Array(randomArray.size) { FacetLabel() }

    for (i in 0 until randomArray.size) {
      allPaths[i] = FacetLabel(randomArray[i])
      w.addCategory(allPaths[i])
      // add random commits to create multiple segments in the index
      if (random().nextBoolean()) {
        w.commit()
      }
    }
    w.commit()
    w.close()

    var r1 = DirectoryTaxonomyReader(src)
    r1.setCacheSize(cacheSize)

    var allOrdinals = r1.getBulkOrdinals(*allPaths)

    // Assert getPath and getBulkPath first, then assert getOrdinal and getBulkOrdinals.
    // Create multiple threads to check result correctness and thread contention in the cache.
    for (assertGettingOrdinals in booleanArrayOf(false, true)) {
      val addThreads = arrayOfNulls<Thread>(TestUtil.nextInt(random(), 1, 12))
      for (z in 0 until addThreads.size) {
        addThreads[z] =
            Thread {
                  // each thread iterates for numThreadIterations times
                  var numThreadIterations = random().nextInt(10)
                  for (threadIterations in 0 until numThreadIterations) {

                    // length of the FacetLabel array that we are going to check
                    var numOfOrdinalsToCheck =
                        TestUtil.nextInt(random(), 1, allOrdinals.size)
                    var ordinals = IntArray(numOfOrdinalsToCheck)
                    val path = Array(numOfOrdinalsToCheck) { FacetLabel() }

                    for (i in 0 until numOfOrdinalsToCheck) {
                      // we deliberately allow it to choose repeat indexes as this will exercise the
                      // cache
                      var ordinalIndex = random().nextInt(allOrdinals.size)
                      ordinals[i] = allOrdinals[ordinalIndex]
                      path[i] = allPaths[ordinalIndex]
                    }

                    try {
                      // main check for correctness is done here
                      if (assertGettingOrdinals) {
                        assertGettingOrdinals(r1, ordinals, path)
                      } else {
                        assertGettingPaths(r1, path, ordinals)
                      }
                    } catch (e: IOException) {
                      // this should ideally never occur, but if it does just rethrow the error to
                      // the
                      // caller
                      throw RuntimeException(e)
                    }
                  }
                }
      }
      for (t in addThreads) t!!.start()
      for (t in addThreads) t!!.join()
    }

    r1.close()
    src.close()
  }
}
