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
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.*
import org.gnit.lucenekmp.facet.FacetTestCase
import org.gnit.lucenekmp.facet.taxonomy.FacetLabel
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyWriter.DiskOrdinalMap
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyWriter.MemoryOrdinalMap
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyWriter.OrdinalMap
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.jdkport.Thread

// SimpleText is not enabled by the common test framework.
@OptIn(ExperimentalAtomicApi::class)
class TestAddTaxonomy : FacetTestCase() {

  private fun dotest(ncats: Int, range: Int) {
    val numCats = AtomicInt(ncats)
    val dirs = arrayOfNulls<Directory>(2)
    for (i in 0 until dirs.size) {
      dirs[i] = newDirectory()
      val tw = DirectoryTaxonomyWriter(dirs[i]!!)
      val addThreads = arrayOfNulls<Thread>(4)
      for (j in 0 until addThreads.size) {
        addThreads[j] =
            Thread {
                  val random = random()
                  while (numCats.addAndFetch(-1) > 0) {
                    val cat = random.nextInt(range).toString()
                    try {
                      tw.addCategory(FacetLabel("a", cat))
                    } catch (e: IOException) {
                      throw RuntimeException(e)
                    }
                  }
                }
      }

      for (t in addThreads) t!!.start()
      for (t in addThreads) t!!.join()
      tw.close()
    }

    val tw = DirectoryTaxonomyWriter(dirs[0]!!)
    val map = randomOrdinalMap()
    tw.addTaxonomy(dirs[1]!!, map)
    tw.close()

    validate(dirs[0]!!, dirs[1]!!, map)

    IOUtils.close(*dirs)
  }

  private fun randomOrdinalMap(): OrdinalMap {
    if (random().nextBoolean()) {
      return DiskOrdinalMap(createTempFile("taxoMap", ""))
    } else {
      return MemoryOrdinalMap()
    }
  }

  private fun validate(dest: Directory, src: Directory, ordMap: OrdinalMap) {
    DirectoryTaxonomyReader(dest).use { destTR ->
      val destSize = destTR.getSize()
      DirectoryTaxonomyReader(src).use { srcTR ->
        val map = ordMap.getMap()

        // validate taxo sizes
        val srcSize = srcTR.getSize()
        assertTrue(destSize >= srcSize, "destination taxonomy expected to be larger than source; dest=" + destSize + " src=" + srcSize)

        // validate that all source categories exist in destination, and their
        // ordinals are as expected.
        for (j in 1 until srcSize) {
          val cp = srcTR.getPath(j)!!
          val destOrdinal = destTR.getOrdinal(cp)
          assertTrue(destOrdinal > 0, cp.toString() + " not found in destination")
          assertEquals(destOrdinal, map[j])
        }
      }
    }
  }

  @Test
  fun testAddEmpty() {
    val dest = newDirectory()
    val destTW = DirectoryTaxonomyWriter(dest)
    destTW.addCategory(FacetLabel("Author", "Rob Pike"))
    destTW.addCategory(FacetLabel("Aardvarks", "Bob"))
    destTW.commit()

    val src = newDirectory()
    DirectoryTaxonomyWriter(src).close() // create an empty taxonomy

    val map = randomOrdinalMap()
    destTW.addTaxonomy(src, map)
    destTW.close()

    validate(dest, src, map)

    IOUtils.close(dest, src)
  }

  @Test
  fun testAddToEmpty() {
    val dest = newDirectory()

    val src = newDirectory()
    val srcTW = DirectoryTaxonomyWriter(src)
    srcTW.addCategory(FacetLabel("Author", "Rob Pike"))
    srcTW.addCategory(FacetLabel("Aardvarks", "Bob"))
    srcTW.close()

    val destTW = DirectoryTaxonomyWriter(dest)
    val map = randomOrdinalMap()
    destTW.addTaxonomy(src, map)
    destTW.close()

    validate(dest, src, map)

    IOUtils.close(dest, src)
  }

  // A more comprehensive and big random test.
  @Ignore // We ran this multiple times and it passes. But this specific test takes more than 7 min becoming botttleneck, so, for now we will ignore this for dev speed
  @Test
  fun testBig() {
    dotest(200, 10000)
    dotest(1000, 20000)
    dotest(400000, 1000000)
  }

  // a reasonable random test
  @Test
  fun testMedium() {
    val random = random()
    val numTests = atLeast(3)
    for (i in 0 until numTests) {
      dotest(TestUtil.nextInt(random, 2, 100), TestUtil.nextInt(random, 100, 1000))
    }
  }

  @Test
  fun testSimple() {
    val dest = newDirectory()
    val tw1 = DirectoryTaxonomyWriter(dest)
    tw1.addCategory(FacetLabel("Author", "Mark Twain"))
    tw1.addCategory(FacetLabel("Animals", "Dog"))
    tw1.addCategory(FacetLabel("Author", "Rob Pike"))

    val src = newDirectory()
    val tw2 = DirectoryTaxonomyWriter(src)
    tw2.addCategory(FacetLabel("Author", "Rob Pike"))
    tw2.addCategory(FacetLabel("Aardvarks", "Bob"))
    tw2.close()

    val map = randomOrdinalMap()

    tw1.addTaxonomy(src, map)
    tw1.close()

    validate(dest, src, map)

    IOUtils.close(dest, src)
  }

  @Test
  fun testConcurrency() {
    // tests that addTaxonomy and addCategory work in parallel
    val numCategories = atLeast(10000)

    // build an input taxonomy index
    val src = newDirectory()
    val tw = DirectoryTaxonomyWriter(src)
    for (i in 0 until numCategories) {
      tw.addCategory(FacetLabel("a", i.toString()))
    }
    tw.close()

    // now add the taxonomy to an empty taxonomy, while adding the categories
    // again, in parallel -- in the end, no duplicate categories should exist.
    val dest = newDirectory()
    val destTW = DirectoryTaxonomyWriter(dest)
    val t =
        Thread {
              for (i in 0 until numCategories) {
                try {
                  destTW.addCategory(FacetLabel("a", i.toString()))
                } catch (e: IOException) {
                  // shouldn't happen - if it does, let the test fail on uncaught exception.
                  throw RuntimeException(e)
                }
              }
            }
    t.start()

    val map = MemoryOrdinalMap()
    destTW.addTaxonomy(src, map)
    t.join()
    destTW.close()

    // now validate

    val dtr = DirectoryTaxonomyReader(dest)
    // +2 to account for the root category + "a"
    assertEquals(numCategories + 2, dtr.getSize())
    val categories = HashSet<FacetLabel>()
    for (i in 1 until dtr.getSize()) {
      val cat = dtr.getPath(i)!!
      assertTrue(categories.add(cat), "category " + cat + " already existed")
    }
    dtr.close()

    IOUtils.close(src, dest)
  }
}
