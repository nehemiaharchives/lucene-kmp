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
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyWriter
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyWriter.OrdinalMap
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.SlowCodecReaderWrapper
import org.gnit.lucenekmp.store.Directory

/**
 * Utility methods for merging index and taxonomy directories.
 *
 * @lucene.experimental
 */
abstract class TaxonomyMergeUtils private constructor() {
    companion object {
        /**
   * Merges the given taxonomy and index directories and commits the changes to the given writers.
   */
        fun merge(
            srcIndexDir: Directory,
            srcTaxoDir: Directory,
            map: OrdinalMap,
            destIndexWriter: IndexWriter,
            destTaxoWriter: DirectoryTaxonomyWriter,
            srcConfig: FacetsConfig
        ) {
            // merge the taxonomies
            destTaxoWriter.addTaxonomy(srcTaxoDir, map)
            val ordinalMap = map.getMap()
            val reader = DirectoryReader.open(srcIndexDir)
            try {
                val leaves = reader.leaves()
                val numReaders = leaves.size
                val wrappedLeaves = Array(numReaders) { i ->
                    SlowCodecReaderWrapper.wrap(OrdinalMappingLeafReader(leaves[i].reader(), ordinalMap, srcConfig))
                }
                destIndexWriter.addIndexes(*wrappedLeaves)
                // commit changes to taxonomy and index respectively.
                destTaxoWriter.commit()
                destIndexWriter.commit()
            } finally {
                reader.close()
            }
        }
    }
}
