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
import kotlin.reflect.KClass
import kotlin.test.*
import org.gnit.lucenekmp.facet.*
import org.gnit.lucenekmp.facet.taxonomy.FacetLabel
import org.gnit.lucenekmp.facet.taxonomy.SearcherTaxonomyManager
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.mockfile.ExtrasFS.Companion.isExtra
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.jdkport.Thread

// Nefarious FS will delay/stop deletion of index files and this test specifically does that
// KMP filesystem wrappers do not include the suppressed WindowsFS/VirusCheckingFS providers.
class TestAlwaysRefreshDirectoryTaxonomyReader : FacetTestCase() {

  /**
   * Tests the behavior of the [AlwaysRefreshDirectoryTaxonomyReader] by testing if the
   * associated [SearcherTaxonomyManager] can successfully refresh and serve queries if the
   * underlying taxonomy index is changed to an older checkpoint. Ideally, each checkpoint should be
   * self-sufficient and should allow serving search queries when [SearcherTaxonomyManager.maybeRefresh] is called.
   *
   * It does not check whether the private taxoArrays were actually recreated or no. We are
   * (correctly) hiding away that complexity away from the user.
   */
  private fun testAlwaysRefreshDirectoryTaxonomyReader(dtrProducer: (Directory) -> DirectoryTaxonomyReader?, exceptionType: KClass<out Throwable>?) {
    val taxoPath1 = createTempDir()
    val dir1 = newFSDirectory(taxoPath1)

    val tw1 =
        DirectoryTaxonomyWriter(dir1, IndexWriterConfig.OpenMode.CREATE)
    tw1.addCategory(FacetLabel("a"))
    tw1.commit() // commit1

    val taxoPath2 = createTempDir()
    val commit1 = newFSDirectory(taxoPath2)
    // copy all index files from dir1
    for (file in dir1.listAll()) {
      if (isExtra(file) == false) {
        // the test framework creates these devious extra files just to chaos test the edge cases
        commit1.copyFrom(dir1, file, file, IOContext.DEFAULT)
      }
    }

    tw1.addCategory(FacetLabel("b"))
    tw1.commit() // commit2
    tw1.close()

    val dr1 = DirectoryReader.open(dir1)
    val dtr1 = dtrProducer(dir1)!!
    val mgr = SearcherTaxonomyManager(dr1, dtr1, null)

    val config = FacetsConfig()
    var pair = mgr.acquire()
    val sfc = FacetsCollector()
    /*
     * the call flow here initializes {@link DirectoryTaxonomyReader#taxoArrays}. These reused
     * `taxoArrays` form the basis of the inconsistency *
     */
    getTaxonomyFacetCounts(pair.taxonomyReader, config, sfc)

    // now try to go back to checkpoint 1 and refresh the SearcherTaxonomyManager

    // delete all files from commit2
    for (file in dir1.listAll()) {
      dir1.deleteFile(file)
    }

    while (dir1.pendingDeletions.isEmpty() == false) {
      // make the test more robust to the OS taking more time to actually delete files
      Thread.sleep(5)
    }

    // copy all index files from commit1
    for (file in commit1.listAll()) {
      if (isExtra(file) == false) {
        dir1.copyFrom(commit1, file, file, IOContext.DEFAULT)
      }
    }

    if (exceptionType != null) {
      expectThrows(exceptionType) { mgr.maybeRefresh() }
    } else {
      mgr.maybeRefresh()
      pair = mgr.acquire()
      assertEquals(FacetLabel("a"), pair.taxonomyReader.getPath(1))
      assertEquals(-1, pair.taxonomyReader.getOrdinal(FacetLabel("b")))
    }

    mgr.release(pair)
    IOUtils.close(mgr, dtr1, dr1)
    // closing commit1 and dir1 throws exceptions because of checksum mismatches
    IOUtils.deleteFiles(commit1, commit1.listAll().asList())
    IOUtils.deleteFiles(dir1, dir1.listAll().asList())
    IOUtils.close(commit1, dir1)
  }

  @Ignore
  // LUCENE-10482: need to make this work on Windows too
  @Test
  fun testAlwaysRefreshDirectoryTaxonomyReader() {
    testAlwaysRefreshDirectoryTaxonomyReader(
      { dir ->
        try {
          DirectoryTaxonomyReader(dir)
        } catch (e: IOException) {
          println(e.stackTraceToString())
          null
        }
      },
      IndexOutOfBoundsException::class
    )
    testAlwaysRefreshDirectoryTaxonomyReader(
      { dir ->
        try {
          AlwaysRefreshDirectoryTaxonomyReader(dir)
        } catch (e: IOException) {
          println(e.stackTraceToString())
          null
        }
      },
      null
    )
  }

  /**
   * A modified DirectoryTaxonomyReader that always recreates a new [AlwaysRefreshDirectoryTaxonomyReader] instance when [AlwaysRefreshDirectoryTaxonomyReader.doOpenIfChanged] is called. This enables us to easily go
   * forward or backward in time by re-computing the ordinal space during each refresh. This results
   * in an always O(#facet_label) taxonomy array construction time when refresh is called.
   */
  private class AlwaysRefreshDirectoryTaxonomyReader : DirectoryTaxonomyReader {

    constructor(directory: Directory) : super(directory)

    constructor(indexReader: DirectoryReader) : super(indexReader, null, null, null, null)

    override fun doOpenIfChanged(): DirectoryTaxonomyReader? {
      var success = false

      // the getInternalIndexReader() function performs the ensureOpen() check
      val reader = DirectoryReader.openIfChanged(super.getInternalIndexReader())
      if (reader == null) {
        return null // no changes in the directory at all, nothing to do
      }

      try {
        // It is important that we create an AlwaysRefreshDirectoryTaxonomyReader here and not a
        // DirectoryTaxonomyReader.
        // Returning a AlwaysRefreshDirectoryTaxonomyReader ensures that the recreated taxonomy
        // reader also uses the overridden doOpenIfChanged
        // method (that always recomputes values).
        val newTaxonomyReader =
            AlwaysRefreshDirectoryTaxonomyReader(reader)
        success = true
        return newTaxonomyReader
      } finally {
        if (!success) {
          IOUtils.closeWhileHandlingException(reader)
        }
      }
    }
  }
}
