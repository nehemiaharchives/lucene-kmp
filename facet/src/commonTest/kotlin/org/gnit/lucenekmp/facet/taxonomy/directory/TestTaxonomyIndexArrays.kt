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
 *
 */
package org.gnit.lucenekmp.facet.taxonomy.directory

import org.gnit.lucenekmp.facet.taxonomy.FacetLabel
import org.gnit.lucenekmp.facet.taxonomy.TaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.TaxonomyWriter
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertNotSame
import kotlin.test.assertNotEquals

class TestTaxonomyIndexArrays : LuceneTestCase() {

  private fun checkIntArraysEquals(
      expected: TaxonomyIndexArrays.ChunkedIntArray, actual: TaxonomyIndexArrays.ChunkedIntArray) {
    for (i in 0 until expected.values.size - 1) {
      assertSame(expected.values[i], actual.values[i])
    }
    var lastOldChunk = expected.values.size - 1
    for (i in 0 until expected.values[lastOldChunk].size) {
      assertEquals(expected.values[lastOldChunk][i], actual.values[lastOldChunk][i])
    }
  }

  private fun checkInvariants(oldArray: TaxonomyIndexArrays, newArray: TaxonomyIndexArrays) {
    var oldParents = oldArray.parents()
    var newParents = newArray.parents()
    checkIntArraysEquals(oldParents, newParents)
    var oldSiblings = oldArray.siblings()
    var newSiblings = newArray.siblings()
    checkIntArraysEquals(oldSiblings, newSiblings)
  }

  @Test
  fun testRandom() {
    var oldArray =
        TaxonomyIndexArrays(arrayOf(intArrayOf(TaxonomyReader.INVALID_ORDINAL)))
    var numIterations = 100
    var ordinal = 1
    for (i in 0 until numIterations) {
      var newOrdinal = ordinal + random().nextInt(TaxonomyIndexArrays.CHUNK_SIZE)
      var newArray = oldArray.add(newOrdinal, ordinal)
      checkInvariants(oldArray, newArray)
      ordinal = newOrdinal
    }
  }

  @Test
  fun testMultiplesOfChunkSize() {
    var oldArray =
        TaxonomyIndexArrays(arrayOf(intArrayOf(TaxonomyReader.INVALID_ORDINAL)))
    var numIterations = 20
    var ordinal = TaxonomyIndexArrays.CHUNK_SIZE
    for (i in 0 until numIterations) {
      var newOrdinal = ordinal + TaxonomyIndexArrays.CHUNK_SIZE
      var newArray = oldArray.add(newOrdinal, ordinal)
      checkInvariants(oldArray, newArray)
      ordinal = newOrdinal
    }
  }

  @Test
  fun testConstructFromEmptyIndex() {
    var dir = newDirectory()

    // Produce empty index
    IndexWriter(dir, newIndexWriterConfig()).close()

    var reader = DirectoryReader.open(dir)

    var tia = TaxonomyIndexArrays(reader)
    assertEquals(0, tia.parents().length())

    tia = TaxonomyIndexArrays(reader, tia)
    assertEquals(0, tia.parents().length())

    reader.close()
    dir.close()
  }

  @Test
  fun testConstructFromIndex() {
    var dir = newDirectory()
    var taxoWriter = DirectoryTaxonomyWriter(dir)
    lateinit var oldTia: TaxonomyIndexArrays
    lateinit var newTia: TaxonomyIndexArrays
    lateinit var oldParents: TaxonomyIndexArrays.ChunkedIntArray
    lateinit var newParents: TaxonomyIndexArrays.ChunkedIntArray

    // Test 1
    // Add one category. The first TIA will have a non-zero length incomplete chunk.
    taxoWriter.addCategory(FacetLabel("a"))
    taxoWriter.commit()
    DirectoryReader.open(dir).use { reader ->
      oldTia = TaxonomyIndexArrays(reader)
    }

    oldParents = oldTia.parents()
    assertEquals(2, oldParents.length())
    assertEquals(TaxonomyReader.INVALID_ORDINAL, oldParents.get(0))
    assertEquals(TaxonomyReader.ROOT_ORDINAL, oldParents.get(1))

    // Test 2
    // Add enough categories to fill the first chunk.
    for (i in 2 until TaxonomyIndexArrays.CHUNK_SIZE) {
      taxoWriter.addCategory(FacetLabel("a", (i).toString()))
    }
    taxoWriter.commit()
    DirectoryReader.open(dir).use { reader ->
      oldTia = TaxonomyIndexArrays(reader)
    }

    oldParents = oldTia.parents()
    assertEquals(TaxonomyIndexArrays.CHUNK_SIZE, oldParents.length())
    assertEquals(TaxonomyReader.INVALID_ORDINAL, oldParents.get(0))
    assertEquals(TaxonomyReader.ROOT_ORDINAL, oldParents.get(1))
    for (i in 2 until oldParents.length()) {
      assertEquals(1, oldParents.get(i))
    }

    // Test 3
    // Both TIAs have the same parents and siblings arrays.
    oldTia.children() // Initializes children
    DirectoryReader.open(dir).use { reader ->
      newTia = TaxonomyIndexArrays(reader, oldTia)
    }
    checkInvariants(oldTia, newTia)

    // Test 4
    // Add one more category, which will start a new chunk on the new TIA.
    taxoWriter.addCategory(FacetLabel("a", (TaxonomyIndexArrays.CHUNK_SIZE).toString()))
    taxoWriter.commit()
    DirectoryReader.open(dir).use { reader ->
      newTia = TaxonomyIndexArrays(reader, oldTia)
    }

    newParents = newTia.parents()
    assertEquals(1 + TaxonomyIndexArrays.CHUNK_SIZE, newParents.length())
    assertEquals(TaxonomyReader.INVALID_ORDINAL, newParents.get(0))
    assertEquals(TaxonomyReader.ROOT_ORDINAL, newParents.get(1))
    for (i in 2 until newParents.length()) {
      assertEquals(1, newParents.get(i))
    }

    // Test 5
    // Fill the second chunk of the new TIA.
    for (i in 1 until TaxonomyIndexArrays.CHUNK_SIZE) {
      taxoWriter.addCategory(
          FacetLabel("a", (i + TaxonomyIndexArrays.CHUNK_SIZE).toString()))
    }
    taxoWriter.commit()
    DirectoryReader.open(dir).use { reader ->
      newTia = TaxonomyIndexArrays(reader, oldTia)
    }

    newParents = newTia.parents()
    assertEquals(2 * TaxonomyIndexArrays.CHUNK_SIZE, newParents.length())
    assertEquals(TaxonomyReader.INVALID_ORDINAL, newParents.get(0))
    assertEquals(TaxonomyReader.ROOT_ORDINAL, newParents.get(1))
    for (i in 2 until newParents.length()) {
      assertEquals(1, newParents.get(i))
    }

    taxoWriter.close()
    dir.close()
  }

  @Test
  fun testRefresh() {
    var dir = newDirectory()

    // Write two chunks worth of ordinals whose parents are ordinals 1 or 2
    var taxoWriter = DirectoryTaxonomyWriter(dir)
    taxoWriter.addCategory(FacetLabel("a")) // ordinal 1
    taxoWriter.addCategory(FacetLabel("b")) // ordinal 2
    for (i in 0 until 2 * TaxonomyIndexArrays.CHUNK_SIZE) {
      if (i % 2 == 0) {
        taxoWriter.addCategory(FacetLabel("a", (i).toString()))
      } else {
        taxoWriter.addCategory(FacetLabel("b", (i).toString()))
      }
    }
    taxoWriter.commit()

    // Initialize old taxonomy arrays
    var reader = DirectoryReader.open(dir)
    var oldTia = TaxonomyIndexArrays(reader)
    reader.close()
    oldTia.children() // Init the children

    // Write one more batch of ordinals whose parents are ordinals 1 or 2 again.
    for (i in 2 * TaxonomyIndexArrays.CHUNK_SIZE until 3 * TaxonomyIndexArrays.CHUNK_SIZE) {
      if (i % 2 == 0) {
        taxoWriter.addCategory(FacetLabel("a", (i).toString()))
      } else {
        taxoWriter.addCategory(FacetLabel("b", (i).toString()))
      }
    }
    taxoWriter.close()

    // Initialize new taxonomy arrays
    reader = DirectoryReader.open(dir)
    var newTia = TaxonomyIndexArrays(reader, oldTia)
    reader.close()

    // Parents and siblings are unchanged in the old range, children will have changed
    checkInvariants(oldTia, newTia)

    var oldChildren = oldTia.children()
    var newChildren = newTia.children()

    // The first chunk had to be reallocated to rewrite the value for ordinals 1 and 2
    assertNotSame(oldChildren.values[0], newChildren.values[0])
    // The second chunk could stay the same, since none of these ordinals have children
    assertSame(oldChildren.values[1], newChildren.values[1])
    // The third chunk had to be reallocated to grow
    assertNotSame(oldChildren.values[2], newChildren.values[2])

    // Check contents of the first chunk
    for (i in 0 until TaxonomyIndexArrays.CHUNK_SIZE) {
      if (i == 1 || i == 2) {
        assertNotEquals(oldChildren.values[0][i], newChildren.values[0][i])
      } else {
        assertEquals(oldChildren.values[0][i], newChildren.values[0][i])
      }
    }
    // Check contents of the third chunk
    for (i in 0 until oldChildren.values[2].size) {
      assertEquals(oldChildren.values[2][i], newChildren.values[2][i])
    }

    dir.close()
  }
}
