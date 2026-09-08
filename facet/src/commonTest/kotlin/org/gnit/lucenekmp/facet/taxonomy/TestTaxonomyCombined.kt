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

import kotlin.test.*
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import org.gnit.lucenekmp.jdkport.Thread

import org.gnit.lucenekmp.facet.FacetTestCase
import org.gnit.lucenekmp.facet.SlowDirectory
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyWriter
import org.gnit.lucenekmp.store.Directory

// SimpleText is not enabled by the common test framework.
@OptIn(ExperimentalAtomicApi::class)
class TestTaxonomyCombined : FacetTestCase() {

  /**
   * The following categories will be added to the taxonomy by fillTaxonomy(), and tested by all
   * tests below:
   */
  private val categories = arrayOf(
    arrayOf<String>("Author", "Tom Clancy"),
    arrayOf<String>("Author", "Richard Dawkins"),
    arrayOf<String>("Author", "Richard Adams"),
    arrayOf<String>("Price", "10", "11"),
    arrayOf<String>("Price", "10", "12"),
    arrayOf<String>("Price", "20", "27"),
    arrayOf<String>("Date", "2006", "05"),
    arrayOf<String>("Date", "2005"),
    arrayOf<String>("Date", "2006"),
    arrayOf<String>("Subject", "Nonfiction", "Children", "Animals"),
    arrayOf<String>("Author", "Stephen Jay Gould"),
    arrayOf<String>("Author", "\u05e0\u05d3\u05d1\u3042\u0628"),
    )

  /**
   * When adding the above categories with TaxonomyWriter.addCategory(), the following paths are
   * expected to be returned: (note that currently the full path is not returned, and therefore not
   * tested - rather, just the last component, the ordinal, is returned and tested.
   */
  private val expectedPaths = arrayOf(
    intArrayOf(1, 2),
    intArrayOf(1, 3),
    intArrayOf(1, 4),
    intArrayOf(5, 6, 7),
    intArrayOf(5, 6, 8),
    intArrayOf(5, 9, 10),
    intArrayOf(11, 12, 13),
    intArrayOf(11, 14),
    intArrayOf(11, 12),
    intArrayOf(15, 16, 17, 18),
    intArrayOf(1, 19),
    intArrayOf(1, 20)
    )

  /**
   * The taxonomy index is expected to then contain the following generated categories, with
   * increasing ordinals (note how parent categories are be added automatically when subcategories
   * are added).
   */
  private val expectedCategories = arrayOf(
    arrayOf<String>(), // the root category
    arrayOf<String>("Author"),
    arrayOf<String>("Author", "Tom Clancy"),
    arrayOf<String>("Author", "Richard Dawkins"),
    arrayOf<String>("Author", "Richard Adams"),
    arrayOf<String>("Price"),
    arrayOf<String>("Price", "10"),
    arrayOf<String>("Price", "10", "11"),
    arrayOf<String>("Price", "10", "12"),
    arrayOf<String>("Price", "20"),
    arrayOf<String>("Price", "20", "27"),
    arrayOf<String>("Date"),
    arrayOf<String>("Date", "2006"),
    arrayOf<String>("Date", "2006", "05"),
    arrayOf<String>("Date", "2005"),
    arrayOf<String>("Subject"),
    arrayOf<String>("Subject", "Nonfiction"),
    arrayOf<String>("Subject", "Nonfiction", "Children"),
    arrayOf<String>("Subject", "Nonfiction", "Children", "Animals"),
    arrayOf<String>("Author", "Stephen Jay Gould"),
    arrayOf<String>("Author", "\u05e0\u05d3\u05d1\u3042\u0628"),
    )

  /**
   * fillTaxonomy adds the categories in the categories[] array, and asserts that the additions
   * return exactly the ordinals (in the past - paths) specified in expectedPaths[]. Note that this
   * assumes that fillTaxonomy() is called on an empty taxonomy index. Calling it after something
   * else was already added to the taxonomy index will surely have this method fail.
   */
  fun fillTaxonomy(tw: TaxonomyWriter): Unit  {
    for (i in 0 until categories.size) {
      var ordinal = tw.addCategory(FacetLabel(*categories[i]))
      var expectedOrdinal = expectedPaths[i][expectedPaths[i].size - 1]
      if (ordinal != expectedOrdinal) {
        fail(
            "For category "
                + showcat(categories[i])
                + " expected ordinal "
                + expectedOrdinal
                + ", but got "
                + ordinal)
      }
    }
  }

  fun showcat(path: Array<String>?): String {
    if (path == null) {
      return "<null>"
    }
    if (path.size == 0) {
      return "<empty>"
    }
    if (path.size == 1 && path[0].length == 0) {
      return "<\"\">"
    }
    var sb = StringBuilder(path[0])
    for (i in 1 until path.size) {
      sb.append('/')
      sb.append(path[i])
    }
    return sb.toString()
  }

  private fun showcat(path: FacetLabel?): String {
    if (path == null) {
      return "<null>"
    }
    if (path.length == 0) {
      return "<empty>"
    }
    return "<" + path + ">"
  }

  /**
   * Basic tests for TaxonomyWriter. Basically, we test that IndexWriter.addCategory works, i.e.
   * returns the expected ordinals (this is tested by calling the fillTaxonomy() method above). We
   * do not test here that after writing the index can be read - this will be done in more tests
   * below.
   */
  @Test
  fun testWriter(): Unit  {
    var indexDir = newDirectory()
    var tw = DirectoryTaxonomyWriter(indexDir)
    fillTaxonomy(tw)
    // Also check TaxonomyWriter.getSize() - see that the taxonomy's size
    // is what we expect it to be.
    assertEquals(expectedCategories.size, tw.getSize())
    tw.close()
    indexDir.close()
  }

  /**
   * testWriterTwice is exactly like testWriter, except that after adding all the categories, we add
   * them again, and see that we get the same old ids again - not new categories.
   */
  @Test
  fun testWriterTwice(): Unit  {
    var indexDir = newDirectory()
    var tw = DirectoryTaxonomyWriter(indexDir)
    fillTaxonomy(tw)
    // run fillTaxonomy again - this will try to add the same categories
    // again, and check that we see the same ordinal paths again, not
    // different ones.
    fillTaxonomy(tw)
    // Let's check the number of categories again, to see that no
    // extraneous categories were created:
    assertEquals(expectedCategories.size, tw.getSize())
    tw.close()
    indexDir.close()
  }

  /**
   * testWriterTwice2 is similar to testWriterTwice, except that the index is closed and reopened
   * before attempting to write to it the same categories again. While testWriterTwice can get along
   * with writing and reading correctly just to the cache, testWriterTwice2 checks also the actual
   * disk read part of the writer:
   */
  @Test
  fun testWriterTwice2(): Unit  {
    var indexDir = newDirectory()
    var tw = DirectoryTaxonomyWriter(indexDir)
    fillTaxonomy(tw)
    tw.close()
    tw = DirectoryTaxonomyWriter(indexDir)
    // run fillTaxonomy again - this will try to add the same categories
    // again, and check that we see the same ordinals again, not different
    // ones, and that the number of categories hasn't grown by the new
    // additions
    fillTaxonomy(tw)
    assertEquals(expectedCategories.size, tw.getSize())
    tw.close()
    indexDir.close()
  }

  /**
   * testWriterTwice3 is yet another test which tests creating a taxonomy in two separate writing
   * sessions. This test used to fail because of a bug involving commit(), explained below, and now
   * should succeed.
   */
  @Test
  fun testWriterTwice3(): Unit  {
    var indexDir = newDirectory()
    // First, create and fill the taxonomy
    var tw = DirectoryTaxonomyWriter(indexDir)
    fillTaxonomy(tw)
    tw.close()
    // Now, open the same taxonomy and add the same categories again.
    // After a few categories, the LuceneTaxonomyWriter implementation
    // will stop looking for each category on disk, and rather read them
    // all into memory and close its reader. The bug was that it closed
    // the reader, but forgot that it did (because it didn't set the reader
    // reference to null).
    tw = DirectoryTaxonomyWriter(indexDir)
    fillTaxonomy(tw)
    // Add one new category, just to make commit() do something:
    tw.addCategory(FacetLabel("hi"))
    // Do a commit(). Here was a bug - if tw had a reader open, it should
    // be reopened after the commit. However, in our case the reader should
    // not be open (as explained above) but because it was not set to null,
    // we forgot that, tried to reopen it, and got an AlreadyClosedException.
    tw.commit()
    assertEquals(expectedCategories.size + 1, tw.getSize())
    tw.close()
    indexDir.close()
  }

  /**
   * Another set of tests for the writer, which don't use an array and try to distill the different
   * cases, and therefore may be more helpful for debugging a problem than testWriter() which is
   * hard to know why or where it failed.
   */
  @Test
  fun testWriterSimpler(): Unit  {
    var indexDir = newDirectory()
    var tw = DirectoryTaxonomyWriter(indexDir)
    assertEquals(1, tw.getSize()) // the root only
    // Test that adding a new top-level category works
    assertEquals(1, tw.addCategory(FacetLabel("a")))
    assertEquals(2, tw.getSize())
    // Test that adding the same category again is noticed, and the
    // same ordinal (and not a new one) is returned.
    assertEquals(1, tw.addCategory(FacetLabel("a")))
    assertEquals(2, tw.getSize())
    // Test that adding another top-level category returns a new ordinal,
    // not the same one
    assertEquals(2, tw.addCategory(FacetLabel("b")))
    assertEquals(3, tw.getSize())
    // Test that adding a category inside one of the above adds just one
    // new ordinal:
    assertEquals(3, tw.addCategory(FacetLabel("a", "c")))
    assertEquals(4, tw.getSize())
    // Test that adding the same second-level category doesn't do anything:
    assertEquals(3, tw.addCategory(FacetLabel("a", "c")))
    assertEquals(4, tw.getSize())
    // Test that adding a second-level category with two new components
    // indeed adds two categories
    assertEquals(5, tw.addCategory(FacetLabel("d", "e")))
    assertEquals(6, tw.getSize())
    // Verify that the parents were added above in the order we expected
    assertEquals(4, tw.addCategory(FacetLabel("d")))
    // Similar, but inside a category that already exists:
    assertEquals(7, tw.addCategory(FacetLabel("b", "d", "e")))
    assertEquals(8, tw.getSize())
    // And now inside two levels of categories that already exist:
    assertEquals(8, tw.addCategory(FacetLabel("b", "d", "f")))
    assertEquals(9, tw.getSize())

    tw.close()
    indexDir.close()
  }

  /**
   * Test writing an empty index, and seeing that a reader finds in it the root category, and only
   * it. We check all the methods on that root category return the expected results.
   */
  @Test
  fun testRootOnly(): Unit  {
    var indexDir = newDirectory()
    var tw = DirectoryTaxonomyWriter(indexDir)
    // right after opening the index, it should already contain the
    // root, so have size 1:
    assertEquals(1, tw.getSize())
    tw.close()
    var tr = DirectoryTaxonomyReader(indexDir)
    assertEquals(1, tr.getSize())
    assertEquals(0, tr.getPath(0)!!.length)
    assertEquals(TaxonomyReader.INVALID_ORDINAL, tr.getParallelTaxonomyArrays().parents().get(0))
    assertEquals(0, tr.getOrdinal(FacetLabel()))
    tr.close()
    indexDir.close()
  }

  /**
   * The following test is exactly the same as testRootOnly, except we do not close the writer
   * before opening the reader. We want to see that the root is visible to the reader not only after
   * the writer is closed, but immediately after it is created.
   */
  @Test
  fun testRootOnly2(): Unit  {
    var indexDir = newDirectory()
    var tw = DirectoryTaxonomyWriter(indexDir)
    tw.commit()
    var tr = DirectoryTaxonomyReader(indexDir)
    assertEquals(1, tr.getSize())
    assertEquals(0, tr.getPath(0)!!.length)
    assertEquals(TaxonomyReader.INVALID_ORDINAL, tr.getParallelTaxonomyArrays().parents().get(0))
    assertEquals(0, tr.getOrdinal(FacetLabel()))
    tw.close()
    tr.close()
    indexDir.close()
  }

  /**
   * Basic tests for TaxonomyReader's category <=> ordinal transformations (getSize(),
   * getCategory() and getOrdinal()). We test that after writing the index, it can be read and all
   * the categories and ordinals are there just as we expected them to be.
   */
  @Test
  fun testReaderBasic(): Unit  {
    var indexDir = newDirectory()
    var tw = DirectoryTaxonomyWriter(indexDir)
    fillTaxonomy(tw)
    tw.close()
    var tr = DirectoryTaxonomyReader(indexDir)

    // test TaxonomyReader.getSize():
    assertEquals(expectedCategories.size, tr.getSize())

    // test round trips of ordinal => category => ordinal
    for (i in 0 until tr.getSize()) {
      assertEquals(i, tr.getOrdinal(tr.getPath(i)!!))
    }

    // test TaxonomyReader.getCategory():
    for (i in 1 until tr.getSize()) {
      var expectedCategory = FacetLabel(*expectedCategories[i])
      var category = tr.getPath(i)
      if (!expectedCategory.equals(category)) {
        fail(
            "For ordinal "
                + i
                + " expected category "
                + showcat(expectedCategory)
                + ", but got "
                + showcat(category))
      }
    }
    //  (also test invalid ordinals:)
    expectThrows(IllegalArgumentException::class) { tr.getPath(-1) }
    expectThrows(IllegalArgumentException::class) { tr.getPath(tr.getSize()) }
    expectThrows(IllegalArgumentException::class) { tr.getPath(TaxonomyReader.INVALID_ORDINAL) }

    // test TaxonomyReader.getOrdinal():
    for (i in 1 until expectedCategories.size) {
      var expectedOrdinal = i
      var ordinal = tr.getOrdinal(FacetLabel(*expectedCategories[i]))
      if (expectedOrdinal != ordinal) {
        fail(
            "For category "
                + showcat(expectedCategories[i])
                + " expected ordinal "
                + expectedOrdinal
                + ", but got "
                + ordinal)
      }
    }
    // (also test invalid categories:)
    assertEquals(TaxonomyReader.INVALID_ORDINAL, tr.getOrdinal(FacetLabel("non-existant")))
    assertEquals(
        TaxonomyReader.INVALID_ORDINAL, tr.getOrdinal(FacetLabel("Author", "Jules Verne")))

    tr.close()
    indexDir.close()
  }

  /**
   * Tests for TaxonomyReader's getParent() method. We check it by comparing its results to those we
   * could have gotten by looking at the category string paths (where the parentage is obvious).
   * Note that after testReaderBasic(), we already know we can trust the ordinal <=> category
   * conversions.
   *
   * Note: At the moment, the parent methods in the reader are deprecated, but this does not mean
   * they should not be tested! Until they are removed (*if* they are removed), these tests should
   * remain to see that they still work correctly.
   */
  @Test
  fun testReaderParent(): Unit  {
    var indexDir = newDirectory()
    var tw = DirectoryTaxonomyWriter(indexDir)
    fillTaxonomy(tw)
    tw.close()
    var tr = DirectoryTaxonomyReader(indexDir)

    // check that the parent of the root ordinal is the invalid ordinal:
    var parents = tr.getParallelTaxonomyArrays().parents()
    assertEquals(TaxonomyReader.INVALID_ORDINAL, parents.get(0))

    // check parent of non-root ordinals:
    for (ordinal in 1 until tr.getSize()) {
      var me = tr.getPath(ordinal)!!
      var parentOrdinal = parents.get(ordinal)
      var parent = tr.getPath(parentOrdinal)
      if (parent == null) {
        fail(
            "Parent of "
                + ordinal
                + " is "
                + parentOrdinal
                + ", but this is not a valid category.")
      }
      // verify that the parent is indeed my parent, according to the strings
      if (!me.subpath(me.length - 1).equals(parent)) {
        fail(
            "Got parent "
                + parentOrdinal
                + " for ordinal "
                + ordinal
                + " but categories are "
                + showcat(parent)
                + " and "
                + showcat(me)
                + " respectively.")
      }
    }

    tr.close()
    indexDir.close()
  }

  /**
   * Tests for TaxonomyWriter's getParent() method. We check it by comparing its results to those we
   * could have gotten by looking at the category string paths using a TaxonomyReader (where the
   * parentage is obvious). Note that after testReaderBasic(), we already know we can trust the
   * ordinal <=> category conversions from TaxonomyReader.
   *
   * The difference between testWriterParent1 and testWriterParent2 is that the former closes the
   * taxonomy writer before reopening it, while the latter does not.
   *
   * This test code is virtually identical to that of testReaderParent().
   */
  @Test
  fun testWriterParent1(): Unit  {
    var indexDir = newDirectory()
    var tw = DirectoryTaxonomyWriter(indexDir)
    fillTaxonomy(tw)
    tw.close()
    tw = DirectoryTaxonomyWriter(indexDir)
    var tr = DirectoryTaxonomyReader(indexDir)

    checkWriterParent(tr, tw)

    tw.close()
    tr.close()
    indexDir.close()
  }

  @Test
  fun testWriterParent2(): Unit  {
    var indexDir = newDirectory()
    var tw = DirectoryTaxonomyWriter(indexDir)
    fillTaxonomy(tw)
    tw.commit()
    var tr = DirectoryTaxonomyReader(indexDir)

    checkWriterParent(tr, tw)

    tw.close()
    tr.close()
    indexDir.close()
  }

  private fun checkWriterParent(tr: TaxonomyReader, tw: TaxonomyWriter): Unit  {
    // check that the parent of the root ordinal is the invalid ordinal:
    assertEquals(TaxonomyReader.INVALID_ORDINAL, tw.getParent(0))

    // check parent of non-root ordinals:
    for (ordinal in 1 until tr.getSize()) {
      var me = tr.getPath(ordinal)!!
      var parentOrdinal = tw.getParent(ordinal)
      var parent = tr.getPath(parentOrdinal)
      if (parent == null) {
        fail(
            "Parent of "
                + ordinal
                + " is "
                + parentOrdinal
                + ", but this is not a valid category.")
      }
      // verify that the parent is indeed my parent, according to the
      // strings
      if (!me.subpath(me.length - 1).equals(parent)) {
        fail(
            "Got parent "
                + parentOrdinal
                + " for ordinal "
                + ordinal
                + " but categories are "
                + showcat(parent)
                + " and "
                + showcat(me)
                + " respectively.")
      }
    }

    // check parent of of invalid ordinals:
    expectThrows(IndexOutOfBoundsException::class) { tw.getParent(-1) }
    expectThrows(IndexOutOfBoundsException::class) { tw.getParent(TaxonomyReader.INVALID_ORDINAL) }
    expectThrows(IndexOutOfBoundsException::class) { tw.getParent(tr.getSize()) }
  }

  /**
   * Test TaxonomyReader's child browsing method, getChildrenArrays() This only tests for
   * correctness of the data on one example - we have below further tests on data refresh etc.
   */
  @Test
  fun testChildrenArrays(): Unit  {
    var indexDir = newDirectory()
    var tw = DirectoryTaxonomyWriter(indexDir)
    fillTaxonomy(tw)
    tw.close()
    var tr = DirectoryTaxonomyReader(indexDir)
    var ca = tr.getParallelTaxonomyArrays()
    var youngestChildArray = ca.children()
    assertEquals(tr.getSize(), youngestChildArray.length())
    var olderSiblingArray = ca.siblings()
    assertEquals(tr.getSize(), olderSiblingArray.length())
    for (i in 0 until expectedCategories.size) {
      // find expected children by looking at all expectedCategories
      // for children
      val expectedChildren = ArrayList<Int>()
      for (j in expectedCategories.size - 1 downTo 0) {
        if (expectedCategories[j].size != expectedCategories[i].size + 1) {
          continue // not longer by 1, so can't be a child
        }
        var ischild = true
        for (k in 0 until expectedCategories[i].size) {
          if (!expectedCategories[j][k].equals(expectedCategories[i][k])) {
            ischild = false
            break
          }
        }
        if (ischild) {
          expectedChildren.add(j)
        }
      }
      // check that children and expectedChildren are the same, with the
      // correct reverse (youngest to oldest) order:
      if (expectedChildren.size == 0) {
        assertEquals(TaxonomyReader.INVALID_ORDINAL, youngestChildArray.get(i))
      } else {
        var child = youngestChildArray.get(i)
        assertEquals(expectedChildren.get(0).toInt(), child)
        for (j in 1 until expectedChildren.size) {
          child = olderSiblingArray.get(child)
          assertEquals(expectedChildren.get(j).toInt(), child)
          // if child is INVALID_ORDINAL we should stop, but
          // assertEquals would fail in this case anyway.
        }
        // When we're done comparing, olderSiblingArray should now point
        // to INVALID_ORDINAL, saying there are no more children. If it
        // doesn't, we found too many children...
        assertEquals(-1, olderSiblingArray.get(child))
      }
    }
    tr.close()
    indexDir.close()
  }

  /**
   * Similar to testChildrenArrays, except rather than look at expected results, we test for several
   * "invariants" that the results should uphold, e.g., that a child of a category indeed has this
   * category as its parent. This sort of test can more easily be extended to larger example
   * taxonomies, because we do not need to build the expected list of categories like we did in the
   * above test.
   */
  @Test
  fun testChildrenArraysInvariants(): Unit  {
    var indexDir = newDirectory()
    var tw = DirectoryTaxonomyWriter(indexDir)
    fillTaxonomy(tw)
    tw.close()
    var tr = DirectoryTaxonomyReader(indexDir)
    var ca = tr.getParallelTaxonomyArrays()
    var children = ca.children()
    assertEquals(tr.getSize(), children.length())
    var olderSiblingArray = ca.siblings()
    assertEquals(tr.getSize(), olderSiblingArray.length())

    // test that the "youngest child" of every category is indeed a child:
    var parents = tr.getParallelTaxonomyArrays().parents()
    for (i in 0 until tr.getSize()) {
      var youngestChild = children.get(i)
      if (youngestChild != TaxonomyReader.INVALID_ORDINAL) {
        assertEquals(i, parents.get(youngestChild))
      }
    }

    // test that the "older sibling" of every category is indeed older (lower)
    // (it can also be INVALID_ORDINAL, which is lower than any ordinal)
    for (i in 0 until tr.getSize()) {
      assertTrue(olderSiblingArray.get(i) < i, "olderSiblingArray[" + i + "] should be <" + i)
    }

    // test that the "older sibling" of every category is indeed a sibling
    // (they share the same parent)
    for (i in 0 until tr.getSize()) {
      var sibling = olderSiblingArray.get(i)
      if (sibling == TaxonomyReader.INVALID_ORDINAL) {
        continue
      }
      assertEquals(parents.get(i), parents.get(sibling))
    }

    // And now for slightly more complex (and less "invariant-like"...)
    // tests:

    // test that the "youngest child" is indeed the youngest (so we don't
    // miss the first children in the chain)
    for (i in 0 until tr.getSize()) {
      // Find the really youngest child:
      var j = tr.getSize() - 1
      while (j > i) {
        if (parents.get(j) == i) {
          break // found youngest child
        }
        j--
      }
      if (j == i) { // no child found
        j = TaxonomyReader.INVALID_ORDINAL
      }
      assertEquals(j, children.get(i))
    }

    // test that the "older sibling" is indeed the least oldest one - and
    // not a too old one or -1 (so we didn't miss some children in the
    // middle or the end of the chain).
    for (i in 0 until tr.getSize()) {
      // Find the youngest older sibling:
      var j = i - 1
      while (j >= 0) {
        if (parents.get(j) == parents.get(i)) {
          break // found youngest older sibling
        }
        j--
      }
      if (j < 0) { // no sibling found
        j = TaxonomyReader.INVALID_ORDINAL
      }
      assertEquals(j, olderSiblingArray.get(i))
    }

    tr.close()
    indexDir.close()
  }

  private fun assertArrayEquals(expected: IntArray, actual: ParallelTaxonomyArrays.IntArray): Unit {
    for (i in 0 until expected.size) {
      assertEquals(expected[i], actual.get(i))
    }
  }

  /** Test how getChildrenArrays() deals with the taxonomy's growth: */
  @Test
  fun testChildrenArraysGrowth(): Unit  {
    var indexDir = newDirectory()
    var tw = DirectoryTaxonomyWriter(indexDir)
    tw.addCategory(FacetLabel("hi", "there"))
    tw.commit()
    var tr = DirectoryTaxonomyReader(indexDir)
    var ca = tr.getParallelTaxonomyArrays()
    assertEquals(3, tr.getSize())
    assertEquals(3, ca.siblings().length())
    assertEquals(3, ca.children().length())
    assertArrayEquals(intArrayOf(1, 2, -1), ca.children())
    assertArrayEquals(intArrayOf(-1, -1, -1), ca.siblings())
    tw.addCategory(FacetLabel("hi", "ho"))
    tw.addCategory(FacetLabel("hello"))
    tw.commit()
    // Before refresh, nothing changed..
    var newca = tr.getParallelTaxonomyArrays()
    assertSame(newca, ca) // we got exactly the same object
    assertEquals(3, tr.getSize())
    assertEquals(3, ca.siblings().length())
    assertEquals(3, ca.children().length())
    // After the refresh, things change:
    var newtr = TaxonomyReader.openIfChanged(tr)
    assertNotNull(newtr)
    tr.close()
    tr = newtr
    ca = tr.getParallelTaxonomyArrays()
    assertEquals(5, tr.getSize())
    assertEquals(5, ca.siblings().length())
    assertEquals(5, ca.children().length())
    assertArrayEquals(intArrayOf(4, 3, -1, -1, -1), ca.children())
    assertArrayEquals(intArrayOf(-1, -1, -1, 2, 1), ca.siblings())
    tw.close()
    tr.close()
    indexDir.close()
  }

  // Test that getParentArrays is valid when retrieved during refresh
  @Test
  fun testTaxonomyReaderRefreshRaces(): Unit  {
    // compute base child arrays - after first chunk, and after the other
    var indexDirBase = newDirectory()
    var twBase = DirectoryTaxonomyWriter(indexDirBase)
    twBase.addCategory(FacetLabel("a", "0"))
    var abPath = FacetLabel("a", "b")
    twBase.addCategory(abPath)
    twBase.commit()
    var trBase = DirectoryTaxonomyReader(indexDirBase)

    var ca1 = trBase.getParallelTaxonomyArrays()

    var abOrd = trBase.getOrdinal(abPath)
    var abYoungChildBase1 = ca1.children().get(abOrd)

    var numCategories = atLeast(200)
    for (i in 0 until numCategories) {
      twBase.addCategory(FacetLabel("a", "b", i.toString()))
    }
    twBase.close()

    var newTaxoReader = TaxonomyReader.openIfChanged(trBase)
    assertNotNull(newTaxoReader)
    trBase.close()
    trBase = newTaxoReader

    var ca2 = trBase.getParallelTaxonomyArrays()
    var abYoungChildBase2 = ca2.children().get(abOrd)

    var numRetries = atLeast(10)
    for (retry in 0 until numRetries) {
      assertConsistentYoungestChild(
          abPath, abOrd, abYoungChildBase1, abYoungChildBase2, retry, numCategories)
    }

    trBase.close()
    indexDirBase.close()
  }

  private fun assertConsistentYoungestChild(abPath: FacetLabel, abOrd: Int, abYoungChildBase1: Int, abYoungChildBase2: Int, retry: Int, numCategories: Int): Unit  {
    var indexDir = SlowDirectory(-1, null) // no slowness for initialization
    var tw = DirectoryTaxonomyWriter(indexDir)
    tw.addCategory(FacetLabel("a", "0"))
    tw.addCategory(abPath)
    tw.commit()

    var tr = DirectoryTaxonomyReader(indexDir)
    for (i in 0 until numCategories) {
      var cp = FacetLabel("a", "b", i.toString())
      tw.addCategory(cp)
      assertEquals(TaxonomyReader.INVALID_ORDINAL, tr.getOrdinal(cp), "Ordinal of " + cp + " must be invalid until Taxonomy Reader was refreshed")
    }
    tw.close()

    var stop = AtomicBoolean(false)
    val error = arrayOfNulls<Throwable>(1)
    val retrieval = intArrayOf(0)

    var thread =
        object : Thread() {
          init { setName("Child Arrays Verifier") }
          override fun run(): Unit {
            setPriority(1 + getPriority())
            try {
              while (!stop.load()) {
                var lastOrd = tr.getParallelTaxonomyArrays().parents().length() - 1
                assertNotNull(tr.getPath(lastOrd), "path of last-ord " + lastOrd + " is not found!")
                assertChildrenArrays(tr.getParallelTaxonomyArrays(), retry, retrieval[0]++)
                sleep(10) // don't starve refresh()'s CPU, which sleeps every 50 bytes for 1 ms
              }
            } catch (e: Throwable) {
              error[0] = e
              stop.store(true)
            }
          }

          private fun assertChildrenArrays(ca: ParallelTaxonomyArrays, retry: Int, retrieval: Int): Unit {
            var abYoungChild = ca.children().get(abOrd)
            assertTrue(abYoungChildBase1 == abYoungChild || abYoungChildBase2 == ca.children().get(abOrd), "Retry "
                    + retry
                    + ": retrieval: "
                    + retrieval
                    + ": wrong youngest child for category "
                    + abPath
                    + " (ord="
                    + abOrd
                    + ") - must be either "
                    + abYoungChildBase1
                    + " or "
                    + abYoungChildBase2
                    + " but was: "
                    + abYoungChild)
          }
        }
    thread.start()

    indexDir.setSleepMillis(1) // some delay for refresh
    var newTaxoReader = TaxonomyReader.openIfChanged(tr)
    if (newTaxoReader != null) {
      newTaxoReader.close()
    }

    stop.store(true)
    thread.join()
    assertNull(error[0], "Unexpcted exception at retry "
            + retry
            + " retrieval "
            + retrieval[0]
            + ": \n"
            + stackTraceStr(error[0]))

    tr.close()
  }

  /**
   * Grab the stack trace into a string since the exception was thrown in a thread and we want the
   * assert outside the thread to show the stack trace in case of failure.
   */
  private fun stackTraceStr(error: Throwable?): String {
    if (error == null) {
      return ""
    }
    return error.stackTraceToString()
  }

  /**
   * Test that if separate reader and writer objects are opened, new categories written into the
   * writer are available to a reader only after a commit(). Note that this test obviously doesn't
   * cover all the different concurrency scenarios, all different methods, and so on. We may want to
   * write more tests of this sort.
   *
   * This test simulates what would happen when there are two separate processes, one doing
   * indexing, and the other searching, and each opens its own object (with obviously no connection
   * between the objects) using the same disk files. Note, though, that this test does not test what
   * happens when the two processes do their actual work at exactly the same time. It also doesn't
   * test multi-threading.
   */
  @Test
  fun testSeparateReaderAndWriter(): Unit  {
    var indexDir = newDirectory()
    var tw = DirectoryTaxonomyWriter(indexDir)
    tw.commit()
    var tr = DirectoryTaxonomyReader(indexDir)

    assertEquals(1, tr.getSize()) // the empty taxonomy has size 1 (the root)
    tw.addCategory(FacetLabel("Author"))
    assertEquals(1, tr.getSize()) // still root only...
    assertNull(
        TaxonomyReader.openIfChanged(
            tr)) // this is not enough, because tw.commit() hasn't been done yet
    assertEquals(1, tr.getSize()) // still root only...
    tw.commit()
    assertEquals(1, tr.getSize()) // still root only...
    var newTaxoReader = TaxonomyReader.openIfChanged(tr)
    assertNotNull(newTaxoReader)
    tr.close()
    tr = newTaxoReader

    var author = 1
    try {
      assertEquals(
          TaxonomyReader.ROOT_ORDINAL, tr.getParallelTaxonomyArrays().parents().get(author))
      // ok
    } catch (e: IndexOutOfBoundsException) {
      fail(
          "After category addition, commit() and refresh(), getParent for "
              + author
              + " should NOT throw exception")
    }
    assertEquals(2, tr.getSize()) // finally, see there are two categories

    // now, add another category, and verify that after commit and refresh
    // the parent of this category is correct (this requires the reader
    // to correctly update its prefetched parent vector), and that the
    // old information also wasn't ruined:
    tw.addCategory(FacetLabel("Author", "Richard Dawkins"))
    var dawkins = 2
    tw.commit()
    newTaxoReader = TaxonomyReader.openIfChanged(tr)
    assertNotNull(newTaxoReader)
    tr.close()
    tr = newTaxoReader
    var parents = tr.getParallelTaxonomyArrays().parents()
    assertEquals(author, parents.get(dawkins))
    assertEquals(TaxonomyReader.ROOT_ORDINAL, parents.get(author))
    assertEquals(TaxonomyReader.INVALID_ORDINAL, parents.get(TaxonomyReader.ROOT_ORDINAL))
    assertEquals(3, tr.getSize())
    tw.close()
    tr.close()
    indexDir.close()
  }

  @Test
  fun testSeparateReaderAndWriter2(): Unit  {
    var indexDir = newDirectory()
    var tw = DirectoryTaxonomyWriter(indexDir)
    tw.commit()
    var tr = DirectoryTaxonomyReader(indexDir)

    // Test getOrdinal():
    var author = FacetLabel("Author")

    assertEquals(1, tr.getSize()) // the empty taxonomy has size 1 (the root)
    assertEquals(TaxonomyReader.INVALID_ORDINAL, tr.getOrdinal(author))
    tw.addCategory(author)
    // before commit and refresh, no change:
    assertEquals(TaxonomyReader.INVALID_ORDINAL, tr.getOrdinal(author))
    assertEquals(1, tr.getSize()) // still root only...
    assertNull(
        TaxonomyReader.openIfChanged(
            tr)) // this is not enough, because tw.commit() hasn't been done yet
    assertEquals(TaxonomyReader.INVALID_ORDINAL, tr.getOrdinal(author))
    assertEquals(1, tr.getSize()) // still root only...
    tw.commit()
    // still not enough before refresh:
    assertEquals(TaxonomyReader.INVALID_ORDINAL, tr.getOrdinal(author))
    assertEquals(1, tr.getSize()) // still root only...
    var newTaxoReader = TaxonomyReader.openIfChanged(tr)
    assertNotNull(newTaxoReader)
    tr.close()
    tr = newTaxoReader
    assertEquals(1, tr.getOrdinal(author))
    assertEquals(2, tr.getSize())
    tw.close()
    tr.close()
    indexDir.close()
  }

  /**
   * fillTaxonomyCheckPaths adds the categories in the categories[] array, and asserts that the
   * additions return exactly paths specified in expectedPaths[]. This is the same add
   * fillTaxonomy() but also checks the correctness of getParent(), not just addCategory(). Note
   * that this assumes that fillTaxonomyCheckPaths() is called on an empty taxonomy index. Calling
   * it after something else was already added to the taxonomy index will surely have this method
   * fail.
   */
  fun fillTaxonomyCheckPaths(tw: TaxonomyWriter): Unit  {
    for (i in 0 until categories.size) {
      var ordinal = tw.addCategory(FacetLabel(*categories[i]))
      var expectedOrdinal = expectedPaths[i][expectedPaths[i].size - 1]
      if (ordinal != expectedOrdinal) {
        fail(
            "For category "
                + showcat(categories[i])
                + " expected ordinal "
                + expectedOrdinal
                + ", but got "
                + ordinal)
      }
      for (j in expectedPaths[i].size - 2 downTo 0) {
        ordinal = tw.getParent(ordinal)
        expectedOrdinal = expectedPaths[i][j]
        if (ordinal != expectedOrdinal) {
          fail(
              "For category "
                  + showcat(categories[i])
                  + " expected ancestor level "
                  + (expectedPaths[i].size - 1 - j)
                  + " was "
                  + expectedOrdinal
                  + ", but got "
                  + ordinal)
        }
      }
    }
  }

  // After fillTaxonomy returned successfully, checkPaths() checks that
  // the getParent() calls return as expected, from the table
  fun checkPaths(tw: TaxonomyWriter): Unit  {
    for (i in 0 until categories.size) {
      var ordinal = expectedPaths[i][expectedPaths[i].size - 1]
      for (j in expectedPaths[i].size - 2 downTo 0) {
        ordinal = tw.getParent(ordinal)
        var expectedOrdinal = expectedPaths[i][j]
        if (ordinal != expectedOrdinal) {
          fail(
              "For category "
                  + showcat(categories[i])
                  + " expected ancestor level "
                  + (expectedPaths[i].size - 1 - j)
                  + " was "
                  + expectedOrdinal
                  + ", but got "
                  + ordinal)
        }
      }
      assertEquals(TaxonomyReader.ROOT_ORDINAL, tw.getParent(expectedPaths[i][0]))
    }
    assertEquals(TaxonomyReader.INVALID_ORDINAL, tw.getParent(TaxonomyReader.ROOT_ORDINAL))
  }

  /**
   * Basic test for TaxonomyWriter.getParent(). This is similar to testWriter above, except we also
   * check the parents of the added categories, not just the categories themselves.
   */
  @Test
  fun testWriterCheckPaths(): Unit  {
    var indexDir = newDirectory()
    var tw = DirectoryTaxonomyWriter(indexDir)
    fillTaxonomyCheckPaths(tw)
    // Also check TaxonomyWriter.getSize() - see that the taxonomy's size
    // is what we expect it to be.
    assertEquals(expectedCategories.size, tw.getSize())
    tw.close()
    indexDir.close()
  }

  /**
   * testWriterCheckPaths2 is the path-checking variant of testWriterTwice and testWriterTwice2.
   * After adding all the categories, we add them again, and see that we get the same old ids and
   * paths. We repeat the path checking yet again after closing and opening the index for writing
   * again - to see that the reading of existing data from disk works as well.
   */
  @Test
  fun testWriterCheckPaths2(): Unit  {
    var indexDir = newDirectory()
    var tw = DirectoryTaxonomyWriter(indexDir)
    fillTaxonomy(tw)
    checkPaths(tw)
    fillTaxonomy(tw)
    checkPaths(tw)
    tw.close()

    tw = DirectoryTaxonomyWriter(indexDir)
    checkPaths(tw)
    fillTaxonomy(tw)
    checkPaths(tw)
    tw.close()
    indexDir.close()
  }

  @Test
  fun testNRT(): Unit  {
    var dir = newDirectory()
    var writer = DirectoryTaxonomyWriter(dir)
    var reader = DirectoryTaxonomyReader(writer)

    var cp = FacetLabel("a")
    writer.addCategory(cp)
    var newReader = TaxonomyReader.openIfChanged(reader)
    assertNotNull(newReader, "expected a new instance")
    assertEquals(2, newReader.getSize())
    assertNotSame(TaxonomyReader.INVALID_ORDINAL, newReader.getOrdinal(cp))
    reader.close()
    reader = newReader

    writer.close()
    reader.close()

    dir.close()
  }

  private fun manyCategories(count: Int, roundSize: Int): Array<Array<String>?> {
    val result = arrayOfNulls<Array<String>>(count / roundSize + 1)
    var k = 0
    do {
      k += roundSize
      val round = ArrayList<String>()
      for (i in k - roundSize + 1..minOf(k, count - 1)) {
        round.add("category " + i + " of " + k)
      }
      result[k / roundSize - 1] = round.toTypedArray()
    } while (k <= count)
    return result
  }

  @Test
  fun testThousandsOfCategories(): Unit  {
    var roundSize = random().nextInt(2, 4)
    var size = random().nextInt(16384, 32768)
    var indexDir = newDirectory()
    var tw = DirectoryTaxonomyWriter(indexDir)
    val manyCategories = manyCategories(size, roundSize)
    for (elem in manyCategories) {
      if (elem == null) {
        throw IllegalStateException(
            "Got null array with size = " + size + " and roundSize = " + roundSize)
      } else if (elem.size > 0) {
        tw.addCategory(FacetLabel(*elem))
      }
    }
    tw.close()
    var tr = DirectoryTaxonomyReader(indexDir)
    var ca = tr.getParallelTaxonomyArrays()
    var parents = ca.parents()
    var children = ca.children()
    assertEquals(size, parents.length())
    assertEquals(size, children.length())
    for (j in 1 until size - roundSize step roundSize) {
      // Top level categories all have root as their parent.
      assertEquals(0, parents.get(j))
      for (i in j until j + roundSize - 1) {
        // Children extend in a chain from the top level category.
        // The parent/child relationships are symmetric.
        assertEquals(i + 1, children.get(i))
        if (i > j) {
          assertEquals(i - 1, parents.get(i))
        }
      }
    }
    var siblings = ca.siblings()
    assertEquals(size, siblings.length())
    for (i in 1 until size - roundSize step roundSize) {
      // Each top-level category (after the first) has the previous top-level category as their
      // older sibling.
      assertEquals(i, siblings.get(i + roundSize))
    }
    tr.close()
    indexDir.close()
  }

  //  TODO (Facet): test multiple readers, one writer. Have the multiple readers
  //  using the same object (simulating threads) or different objects
  //  (simulating processes).
}
