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

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.facet.taxonomy.FacetLabel
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.jdkport.withLock

/**
 * Use this [org.gnit.lucenekmp.facet.taxonomy.TaxonomyWriter] to append arbitrary fields to
 * the ordinal documents in the taxonomy. To update the custom data added to the docs, it is
 * required to [reindexWithNewOrdinalData].
 *
 * @lucene.experimental
 */
open class ReindexingEnrichedDirectoryTaxonomyWriter : DirectoryTaxonomyWriter {
    private var ordinalDataAppender: ((FacetLabel, Document) -> Unit)? = null

    /** Create a taxonomy writer that will allow editing the ordinal docs before indexing them. */
    constructor(d: Directory, ordinalDataAppender: (FacetLabel, Document) -> Unit) : super(d) {
        this.ordinalDataAppender = ordinalDataAppender
    }

    /** Add fields specified by the [ordinalDataAppender] to the provided [Document]. */
    override fun enrichOrdinalDocument(d: Document, categoryPath: FacetLabel) {
        ordinalDataAppender?.invoke(categoryPath, d)
    }

    /**
   * Make a list of all labels in the taxonomy. The index of each label in this list is the ordinal
   * which corresponds to it.
   */
    private fun recordPathsInOrder(d: Directory): List<FacetLabel> {
        val paths = ArrayList<FacetLabel>()
        val taxoReader = DirectoryTaxonomyReader(d)
        val taxoIndexReader = taxoReader.getInternalIndexReader()
        for (ctx in taxoIndexReader.leaves()) {
            val leafReader = ctx.reader()
            val ordinals = IntArray(leafReader.maxDoc())
            for (i in ordinals.indices) ordinals[i] = ctx.docBase + i
            val labels = taxoReader.getBulkPath(*ordinals)
            for (label in labels) paths.add(label!!)
        }
        IOUtils.close(taxoReader)
        return paths
    }

    /**
   * Delete the existing taxonomy index and recreate it using new ordinal data. The ordinals
   * themselves will be preserved, so the caller does not need to update references to them in the
   * main index.
   */
    fun reindexWithNewOrdinalData(ordinalDataAppender: (FacetLabel, Document) -> Unit) = lock.withLock {
        ensureOpen()
        this.ordinalDataAppender = ordinalDataAppender
        val d = getDirectory()
        // Record paths in order.
        val ordinalToPath = recordPathsInOrder(d)
        // Delete old taxonomy files.
        deleteAll()
        // Index paths in order - they will use the new appender.
        for (categoryPath in ordinalToPath) addCategory(categoryPath)
        commit()
        Unit
    }
}
