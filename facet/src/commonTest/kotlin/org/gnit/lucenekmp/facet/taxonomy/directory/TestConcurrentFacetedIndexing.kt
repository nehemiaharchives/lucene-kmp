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
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.test.*
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.facet.FacetField
import org.gnit.lucenekmp.facet.FacetTestCase
import org.gnit.lucenekmp.facet.FacetsConfig
import org.gnit.lucenekmp.facet.taxonomy.FacetLabel
import org.gnit.lucenekmp.facet.taxonomy.writercache.LruTaxonomyWriterCache
import org.gnit.lucenekmp.facet.taxonomy.writercache.TaxonomyWriterCache
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig.OpenMode
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.util.IOUtils

/** Tests concurrent indexing with facets. */
@OptIn(ExperimentalAtomicApi::class)
class TestConcurrentFacetedIndexing : FacetTestCase() {
  companion object {
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

    fun newCategory(): FacetField {
      val r = random()
      val l1 = "l1." + r.nextInt(10) // l1.0-l1.9 (10 categories)
      val l2 = "l2." + r.nextInt(30) // l2.0-l2.29 (30 categories)
      val l3 = "l3." + r.nextInt(100) // l3.0-l3.99 (100 categories)
      return FacetField(l1, l2, l3)
    }

    fun newTaxoWriterCache(ndocs: Int): TaxonomyWriterCache {
      val d = random().nextDouble()
      if (d < 0.7) {
        // same as LruTaxonomyWriterCache but with the default cache size
        return DirectoryTaxonomyWriter.defaultTaxonomyWriterCache()
      } else if (TEST_NIGHTLY && d > 0.98) {
        // this is the slowest, but tests the writer concurrency when no caching is done.
        // only pick it during NIGHTLY tests, and even then, with very low chances.
        return NO_OP_CACHE
      } else {
        // this is slower than UTF8, but less memory consuming, and exercises finding categories on
        // disk too.
        return LruTaxonomyWriterCache(ndocs / 10)
      }
    }
  }

  @Test
  fun testConcurrency() {
    val numDocs = AtomicInt(atLeast(10000))
    val indexDir = newDirectory()
    val taxoDir = newDirectory()
    val values = HashMap<String, String>()
    // Serialize writes to the common Kotlin map, matching ConcurrentHashMap's thread safety.
    val valuesLock = ReentrantLock()
    val iw = IndexWriter(indexDir, newIndexWriterConfig())
    val tw = DirectoryTaxonomyWriter(taxoDir, OpenMode.CREATE, newTaxoWriterCache(numDocs.load()))
    val indexThreads = arrayOfNulls<Thread>(atLeast(4))
    val config = FacetsConfig()
    for (i in 0 until 10) {
      config.setHierarchical("l1." + i, true)
      config.setMultiValued("l1." + i, true)
    }

    for (i in indexThreads.indices) {
      indexThreads[i] = Thread {
        val random = random()
        while (numDocs.decrementAndFetch() > 0) {
          try {
            val doc = Document()
            var numCats = random.nextInt(3) + 1 // 1-3
            while (numCats-- > 0) {
              val ff = newCategory()
              doc.add(ff)
              val label = FacetLabel(ff.dim, *ff.path)
              // add all prefixes to values
              var level = label.length
              while (level > 0) {
                val s = FacetsConfig.pathToString(label.components, level)
                valuesLock.lock()
                try { values[s] = s } finally { valuesLock.unlock() }
                --level
              }
            }
            iw.addDocument(config.build(tw, doc))
          } catch (e: IOException) {
            throw RuntimeException(e)
          }
        }
      }
    }

    for (t in indexThreads) t!!.start()
    for (t in indexThreads) t!!.join()

    val tr = DirectoryTaxonomyReader(tw)
    // +1 for root category
    if (values.size + 1 != tr.getSize()) {
      for (value in values.keys) {
        val label = FacetLabel(*FacetsConfig.stringToPath(value))
        if (tr.getOrdinal(label) == -1) {
          println("FAIL: path=" + label + " not recognized")
        }
      }
      fail("mismatch number of categories")
    }
    val parents = tr.getParallelTaxonomyArrays().parents()
    for (cat in values.keys) {
      val cp = FacetLabel(*FacetsConfig.stringToPath(cat))
      assertTrue(tr.getOrdinal(cp) > 0, "category not found " + cp)
      val level = cp.length
      var parentOrd = 0 // for root, parent is always virtual ROOT (ord=0)
      var path: FacetLabel? = null
      for (i in 0 until level) {
        path = cp.subpath(i + 1)
        val ord = tr.getOrdinal(path)
        assertEquals(parentOrd, parents.get(ord), "invalid parent for cp=" + path)
        parentOrd = ord // next level should have this parent
      }
    }

    iw.close()
    IOUtils.close(tw, tr, taxoDir, indexDir)
  }
}
