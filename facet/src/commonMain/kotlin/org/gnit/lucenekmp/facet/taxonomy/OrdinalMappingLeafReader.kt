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

import org.gnit.lucenekmp.facet.FacetsConfig
import org.gnit.lucenekmp.index.FilterLeafReader
import org.gnit.lucenekmp.index.FilterSortedNumericDocValues
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.internal.hppc.IntArrayList
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.jdkport.assert

/**
 * A [org.gnit.lucenekmp.index.FilterLeafReader] for updating facets ordinal references, based
 * on an ordinal map. You should use this code in conjunction with merging taxonomies - after you
 * merge taxonomies, you receive an [OrdinalMap] which maps the 'old' ordinals to the 'new'
 * ones. You can use that map to re-map the doc values which contain the facets information
 * (ordinals) either before or while merging the indexes.
 *
 * For re-mapping the ordinals during index merge, do the following:
 *
 * <pre class="prettyprint">
 * // merge the old taxonomy with the new one.
 * OrdinalMap map = new MemoryOrdinalMap();
 * DirectoryTaxonomyWriter.addTaxonomy(srcTaxoDir, map);
 * int[] ordmap = map.getMap();
 *
 * // Add the index and re-map ordinals on the go
 * DirectoryReader reader = DirectoryReader.open(oldDir);
 * IndexWriterConfig conf = new IndexWriterConfig(VER, ANALYZER);
 * IndexWriter writer = new IndexWriter(newDir, conf);
 * List<LeafReaderContext> leaves = reader.leaves();
 * LeafReader wrappedLeaves[] = new LeafReader[leaves.size()];
 * for (int i = 0; i < leaves.size(); i++) {
 *   wrappedLeaves[i] = new OrdinalMappingLeafReader(leaves.get(i).reader(), ordmap);
 * }
 * writer.addIndexes(new MultiReader(wrappedLeaves));
 * writer.commit();
 * </pre>
 *
 * @lucene.experimental
 */
open class OrdinalMappingLeafReader : FilterLeafReader {
    private inner class OrdinalMappingSortedNumericDocValues(`in`: SortedNumericDocValues) : FilterSortedNumericDocValues(`in`) {
        private val currentValues = IntArrayList(32)
        private var currIndex = 0

        override fun advanceExact(target: Int): Boolean {
            val result = `in`.advanceExact(target)
            if (result) reloadValues()
            return result
        }

        override fun advance(target: Int): Int {
            val result = `in`.advance(target)
            if (result != DocIdSetIterator.NO_MORE_DOCS) reloadValues()
            return result
        }

        override fun nextDoc(): Int {
            val result = `in`.nextDoc()
            if (result != DocIdSetIterator.NO_MORE_DOCS) reloadValues()
            return result
        }

        override fun docValueCount(): Int = currentValues.elementsCount

        private fun reloadValues() {
            currIndex = 0
            currentValues.clear()
            for (i in 0 until `in`.docValueCount()) {
                val value = `in`.nextValue()
                if (value.toInt().toLong() != value) throw ArithmeticException("integer overflow")
                val originalOrd = value.toInt()
                currentValues.add(ordinalMap[originalOrd])
            }
            currentValues.buffer.sort(0, currentValues.elementsCount)
        }

        override fun nextValue(): Long {
            assert(currIndex < currentValues.size())
            val actual = currentValues.get(currIndex)
            currIndex++
            return actual.toLong()
        }
    }

    private val ordinalMap: IntArray
    private val facetFields: MutableSet<String>

    /**
   * Wraps an LeafReader, mapping ordinals according to the ordinalMap, using the provided [FacetsConfig] which was used to build the wrapped reader.
   */
    constructor(`in`: LeafReader, ordinalMap: IntArray, srcConfig: FacetsConfig) : super(`in`) {
        this.ordinalMap = ordinalMap
        facetFields = HashSet()
        for (dc in srcConfig.getDimConfigs().values) facetFields.add(dc.indexFieldName)
        // always add the default indexFieldName. This is because FacetsConfig does
        // not explicitly record dimensions that were indexed under the default
        // DimConfig, unless they have a custom DimConfig.
        facetFields.add(FacetsConfig.DEFAULT_DIM_CONFIG.indexFieldName)
    }

    override fun getSortedNumericDocValues(field: String): SortedNumericDocValues? {
        val original = `in`.getSortedNumericDocValues(field)
        if (original != null && facetFields.contains(field)) {
            // The requested field is a facet ordinals field _and_ it's non-null, so move forward with
            // mapping:
            return OrdinalMappingSortedNumericDocValues(original)
        } else {
            // The requested field either isn't present (null) or isn't a facet ordinals field. Either
            // way, just return the original:
            return original
        }
    }

    override val coreCacheHelper: CacheHelper? get() = null
    override val readerCacheHelper: CacheHelper? get() = null
}
