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

import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyReader
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyWriter
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.ReferenceManager
import org.gnit.lucenekmp.search.SearcherFactory
import org.gnit.lucenekmp.search.SearcherManager
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.util.IOUtils

/**
 * Manages near-real-time reopen of both an IndexSearcher and a TaxonomyReader.
 *
 * **NOTE**: If you call [DirectoryTaxonomyWriter.replaceTaxonomy] then you must open a
 * new `SearcherTaxonomyManager` afterwards.
 */
class SearcherTaxonomyManager : ReferenceManager<SearcherTaxonomyManager.SearcherAndTaxonomy> {
    /**
   * Holds a matched pair of [IndexSearcher] and [TaxonomyReader]
   *
   * @param searcher Point-in-time [IndexSearcher].
   * @param taxonomyReader Matching point-in-time [DirectoryTaxonomyReader].
   */
    data class SearcherAndTaxonomy(val searcher: IndexSearcher, val taxonomyReader: DirectoryTaxonomyReader)

    private val searcherFactory: SearcherFactory
    private val taxoEpoch: Long
    private val taxoWriter: DirectoryTaxonomyWriter?

    /** Creates near-real-time searcher and taxonomy reader from the corresponding writers. */
    constructor(writer: IndexWriter, searcherFactory: SearcherFactory?, taxoWriter: DirectoryTaxonomyWriter) :
        this(writer, true, searcherFactory, taxoWriter)

    /**
   * Expert: creates near-real-time searcher and taxonomy reader from the corresponding writers,
   * controlling whether deletes should be applied.
   */
    constructor(writer: IndexWriter, applyAllDeletes: Boolean, searcherFactory: SearcherFactory?, taxoWriter: DirectoryTaxonomyWriter) {
        this.searcherFactory = searcherFactory ?: SearcherFactory()
        this.taxoWriter = taxoWriter
        val taxoReader = DirectoryTaxonomyReader(taxoWriter)
        current = SearcherAndTaxonomy(SearcherManager.getSearcher(this.searcherFactory, DirectoryReader.open(writer, applyAllDeletes, false), null), taxoReader)
        this.taxoEpoch = taxoWriter.getTaxonomyEpoch()
    }

    /**
   * Creates search and taxonomy readers over the corresponding directories.
   *
   * **NOTE:** you should only use this constructor if you commit and call [maybeRefresh] in the same thread. Otherwise it could lead to an unsync'd [IndexSearcher] and [TaxonomyReader] pair.
   */
    constructor(indexDir: Directory, taxoDir: Directory, searcherFactory: SearcherFactory?) {
        this.searcherFactory = searcherFactory ?: SearcherFactory()
        val taxoReader = DirectoryTaxonomyReader(taxoDir)
        current = SearcherAndTaxonomy(SearcherManager.getSearcher(this.searcherFactory, DirectoryReader.open(indexDir), null), taxoReader)
        this.taxoWriter = null
        taxoEpoch = -1
    }

    /**
   * Creates this from already opened [IndexReader] and [DirectoryTaxonomyReader]
   * instances. Note that the incoming readers will be closed when you call [close].
   */
    constructor(reader: IndexReader, taxoReader: DirectoryTaxonomyReader, searcherFactory: SearcherFactory?) {
        this.searcherFactory = searcherFactory ?: SearcherFactory()
        current = SearcherAndTaxonomy(SearcherManager.getSearcher(this.searcherFactory, reader, null), taxoReader)
        this.taxoWriter = null
        taxoEpoch = -1
    }

    override fun decRef(ref: SearcherAndTaxonomy) {
        ref.searcher.indexReader.decRef()
        // This decRef can fail, and then in theory we should
        // tryIncRef the searcher to put back the ref count
        // ... but 1) the below decRef should only fail because
        // it decRef'd to 0 and closed and hit some IOException
        // during close, in which case 2) very likely the
        // searcher was also just closed by the above decRef and
        // a tryIncRef would fail:
        ref.taxonomyReader.decRef()
    }

    override fun tryIncRef(ref: SearcherAndTaxonomy): Boolean {
        if (ref.searcher.indexReader.tryIncRef()) {
            if (ref.taxonomyReader.tryIncRef()) {
                return true
            } else {
                ref.searcher.indexReader.decRef()
            }
        }
        return false
    }

    override fun refreshIfNeeded(ref: SearcherAndTaxonomy): SearcherAndTaxonomy? {
        // Must re-open searcher first, otherwise we may get a
        // new reader that references ords not yet known to the
        // taxonomy reader:
        val r = ref.searcher.indexReader
        val newReader = DirectoryReader.openIfChanged(r as DirectoryReader)
        if (newReader == null) {
            return null
        } else {
            var tr: DirectoryTaxonomyReader?
            try {
                tr = TaxonomyReader.openIfChanged(ref.taxonomyReader)
            } catch (t1: Throwable) {
                try {
                    IOUtils.close(newReader)
                } catch (t2: Throwable) {
                    t2.addSuppressed(t2)
                }
                throw t1
            }
            if (tr == null) {
                ref.taxonomyReader.incRef()
                tr = ref.taxonomyReader
            } else if (taxoWriter != null && taxoWriter.getTaxonomyEpoch() != taxoEpoch) {
                IOUtils.close(newReader, tr)
                throw IllegalStateException("DirectoryTaxonomyWriter.replaceTaxonomy was called, which is not allowed when using SearcherTaxonomyManager")
            }
            return SearcherAndTaxonomy(SearcherManager.getSearcher(searcherFactory, newReader, r), tr)
        }
    }

    override fun getRefCount(reference: SearcherAndTaxonomy): Int = reference.searcher.indexReader.getRefCount()
}
