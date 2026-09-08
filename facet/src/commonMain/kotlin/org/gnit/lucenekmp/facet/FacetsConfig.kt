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
package org.gnit.lucenekmp.facet

import org.gnit.lucenekmp.document.*
import org.gnit.lucenekmp.facet.sortedset.SortedSetDocValuesFacetField
import org.gnit.lucenekmp.facet.taxonomy.*
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BitUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IntsRefBuilder
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.withLock
import org.gnit.lucenekmp.jdkport.assert

/**
 * Records per-dimension configuration. By default a dimension is flat, single valued and does not
 * require count for the dimension; use the setters in this class to change these settings for each
 * dim.
 *
 * **NOTE**: this configuration is not saved into the index, but it's vital, and up to the
 * application to ensure, that at search time the provided `FacetsConfig` matches what was
 * used during indexing.
 *
 * @lucene.experimental
 */
open class FacetsConfig {
    // KMP replacement for the Java monitor and internal concurrent-map accesses.
    private val lock = ReentrantLock()
    private val fieldTypes = HashMap<String, DimConfig>()

    // Used only for best-effort detection of app mixing
    // int/float/bytes in a single indexed field:
    private val assocDimTypes = HashMap<String, String>()

    /**
   * Drill down terms indexing option to control whether dimension and sub-path terms should be
   * indexed.
   */
    enum class DrillDownTermsIndexing {
        /**
     * Index no drill down terms. e.g. for FacetField("a", "foo/bar/baz"), we would index no drill
     * down terms at all (not even full-path drill down term).
     */
        NONE,
        /**
     * Index only full-path drill down terms. No dimension nor sub-path indexing. e.g. for `* FacetField("a", "foo/bar/baz")`, we would only index value "a/foo/bar/baz".
     */
        FULL_PATH_ONLY,
        /**
     * Index sub-path and full-path drill down terms. No dimension indexing. e.g. for `* FacetField("a", "foo/bar/baz")`, we would only index values "a/foo", "a/foo/bar", and
     * "a/foo/bar/baz".
     */
        ALL_PATHS_NO_DIM,
        /**
     * Index dimension and full-path drill down terms. No sub-path indexing. e.g. for `* FacetField("a", "foo/bar/baz")`, we would only index values "a" and "a/foo/bar/baz".
     */
        DIMENSION_AND_FULL_PATH,
        /**
     * Index dimension, sub-path and full-path drill down terms. e.g. for FacetField("a",
     * "foo/bar/baz"), we would index all values "a", "a/foo", "a/foo/bar", and "a/foo/bar/baz".
     * This is the default / existing behavior.
     */
        ALL
    }

    /**
   * Holds the configuration for one dimension
   *
   * @lucene.experimental
   */
    class DimConfig {
        /** True if this dimension is hierarchical. */
        var hierarchical = false
        /** True if this dimension is multi-valued. */
        var multiValued = false
        /**
     * True if the count/aggregate for the entire dimension is required, which is unusual (default
     * is false).
     */
        var requireDimCount = false
        /** Default drill down terms indexing option that index all. */
        var drillDownTermsIndexing = DrillDownTermsIndexing.ALL
        /** Actual field where this dimension's facet labels should be indexed */
        var indexFieldName = DEFAULT_INDEX_FIELD_NAME
        /** Default constructor. */
        constructor()
    }

    /** Default constructor. */
    constructor()
/**
   * Get the default configuration for new dimensions. Useful when the dimension is not known
   * beforehand and may need different global default settings, like `multivalue = true`.
   *
   * @return The default configuration to be used for dimensions that are not yet set in the [    FacetsConfig]
   */
    protected open fun getDefaultDimConfig(): DimConfig = DEFAULT_DIM_CONFIG

/** Get the current configuration for a dimension. */
    fun getDimConfig(dimName: String): DimConfig = lock.withLock {
        var dimConfig = fieldTypes[dimName]
        if (dimConfig == null) {
            dimConfig = getDefaultDimConfig()
        }
        dimConfig
    }

/**
   * Returns true if the dimension for provided name has ever been manually configured. The opposite
   * means that dimension is still valid and [DEFAULT_DIM_CONFIG] is being used for it.
   */
    fun isDimConfigured(dimName: String): Boolean = lock.withLock { fieldTypes[dimName] != null }

/** Pass `true` if this dimension is hierarchical (has depth > 1 paths). */
    fun setHierarchical(dimName: String, v: Boolean) = lock.withLock {
        var dimConfig = fieldTypes[dimName]
        if (dimConfig == null) {
            dimConfig = DimConfig()
            fieldTypes[dimName] = dimConfig
        }
        dimConfig.hierarchical = v
    }

/** Pass `true` if this dimension may have more than one value per document. */
    fun setMultiValued(dimName: String, value: Boolean) = lock.withLock {
        var dimConfig = fieldTypes[dimName]
        if (dimConfig == null) {
            dimConfig = DimConfig()
            fieldTypes[dimName] = dimConfig
        }
        dimConfig.multiValued = value
    }

/**
   * Pass `true` if at search time you require accurate counts of the dimension, i.e. how many
   * hits have this dimension.
   */
    fun setRequireDimCount(dimName: String, value: Boolean) = lock.withLock {
        var dimConfig = fieldTypes[dimName]
        if (dimConfig == null) {
            dimConfig = DimConfig()
            fieldTypes[dimName] = dimConfig
        }
        dimConfig.requireDimCount = value
    }

/**
   * Specify which index field name should hold the ordinals for this dimension; this is only used
   * by the taxonomy based facet methods.
   */
    fun setIndexFieldName(dimName: String, indexFieldName: String) = lock.withLock {
        var dimConfig = fieldTypes[dimName]
        if (dimConfig == null) {
            dimConfig = DimConfig()
            fieldTypes[dimName] = dimConfig
        }
        dimConfig.indexFieldName = indexFieldName
    }

/** Specify drill down terms option on the field / dimension. */
    fun setDrillDownTermsIndexing(dimName: String, drillDownTermsIndexing: DrillDownTermsIndexing) = lock.withLock {
        var dimConfig = fieldTypes[dimName]
        if (dimConfig == null) {
            dimConfig = DimConfig()
            fieldTypes[dimName] = dimConfig
        }
        dimConfig.drillDownTermsIndexing = drillDownTermsIndexing
    }

/** Returns map of field name to [DimConfig]. */
    fun getDimConfigs(): MutableMap<String, DimConfig> = fieldTypes

    /**
   * Translates any added [FacetField]s into normal fields for indexing; only use this version
   * if you did not add any taxonomy-based fields ( [FacetField] or [AssociationFacetField]).
   *
   * **NOTE:** you should add the returned document to IndexWriter, not the input one!
   */
    fun build(doc: Document): Document = build(null, doc)

    /**
   * Translates any added [FacetField]s into normal fields for indexing.
   *
   * **NOTE:** you should add the returned document to IndexWriter, not the input one!
   */
    fun build(taxoWriter: TaxonomyWriter?, doc: Document): Document {
        // Find all FacetFields, collated by the actual field:
        val byField = HashMap<String, MutableList<FacetField>>()
        // ... and also all SortedSetDocValuesFacetFields:
        val dvByField = HashMap<String, MutableList<SortedSetDocValuesFacetField>>()
        // ... and also all AssociationFacetFields
        val assocByField = HashMap<String, MutableList<AssociationFacetField>>()
        val seenDims = HashSet<String>()
        for (field in doc) {
            if (field.fieldType() === FacetField.TYPE) {
                val facetField = field as FacetField
                val dimConfig = getDimConfig(facetField.dim)
                if (!dimConfig.multiValued) {
                    checkSeen(seenDims, facetField.dim)
                }
                val indexFieldName = dimConfig.indexFieldName
                val fields = byField.getOrPut(indexFieldName) { mutableListOf() }
                fields.add(facetField)
            }
            if (field.fieldType() === SortedSetDocValuesFacetField.TYPE) {
                val facetField = field as SortedSetDocValuesFacetField
                val dimConfig = getDimConfig(facetField.dim)
                if (!dimConfig.multiValued) {
                    checkSeen(seenDims, facetField.dim)
                }
                val indexFieldName = dimConfig.indexFieldName
                val fields = dvByField.getOrPut(indexFieldName) { mutableListOf() }
                fields.add(facetField)
            }
            if (field.fieldType() === AssociationFacetField.TYPE) {
                val facetField = field as AssociationFacetField
                val dimConfig = getDimConfig(facetField.dim)
                if (!dimConfig.multiValued) {
                    checkSeen(seenDims, facetField.dim)
                }
                if (dimConfig.hierarchical) {
                    throw IllegalArgumentException("AssociationFacetField cannot be hierarchical (dim=\"${facetField.dim}\")")
                }
                if (dimConfig.requireDimCount) {
                    throw IllegalArgumentException("AssociationFacetField cannot requireDimCount (dim=\"${facetField.dim}\")")
                }
                val indexFieldName = dimConfig.indexFieldName
                val fields = assocByField.getOrPut(indexFieldName) { mutableListOf() }
                fields.add(facetField)
                // Best effort: detect mis-matched types in same
                // indexed field:
                val type: String
                if (facetField is IntAssociationFacetField) {
                    type = "int"
                } else if (facetField is FloatAssociationFacetField) {
                    type = "float"
                } else {
                    type = "bytes"
                }
                // NOTE: not thread safe, but this is just best effort:
                val curType = lock.withLock { assocDimTypes[indexFieldName] }
                if (curType == null) {
                    lock.withLock { assocDimTypes[indexFieldName] = type }
                } else if (curType != type) {
                    throw IllegalArgumentException("mixing incompatible types of AssociationFacetField ($curType and $type) in indexed field \"$indexFieldName\"; use FacetsConfig to change the indexFieldName for each dimension")
                }
            }
        }
        val result = Document()
        processFacetFields(taxoWriter, byField, result)
        processSSDVFacetFields(dvByField, result)
        processAssocFacetFields(taxoWriter, assocByField, result)
        for (field in doc.getFields()) {
            val ft = field.fieldType()
            if (ft !== FacetField.TYPE && ft !== SortedSetDocValuesFacetField.TYPE && ft !== AssociationFacetField.TYPE) {
                result.add(field)
            }
        }
        return result
    }

    private fun processFacetFields(taxoWriter: TaxonomyWriter?, byField: Map<String, List<FacetField>>, doc: Document) {
        for (ent in byField.entries) {
            val indexFieldName = ent.key
            val ordinals = IntsRefBuilder()
            for (facetField in ent.value) {
                val dimConfig = getDimConfig(facetField.dim)
                if (facetField.path.size > 1 && !dimConfig.hierarchical) {
                    throw IllegalArgumentException("dimension \"${facetField.dim}\" is not hierarchical yet has ${facetField.path.size} components")
                }
                val facetLabel = FacetLabel(facetField.dim, facetField.path)
                checkTaxoWriter(taxoWriter)
                val ordinal = taxoWriter!!.addCategory(facetLabel)
                ordinals.append(ordinal)
                if (dimConfig.multiValued && (dimConfig.hierarchical || dimConfig.requireDimCount)) {
                    // Add all parents too:
                    var parent = taxoWriter.getParent(ordinal)
                    while (parent > 0) {
                        ordinals.append(parent)
                        parent = taxoWriter.getParent(parent)
                    }
                    if (!dimConfig.requireDimCount) {
                        // Remove last (dimension) ord:
                        ordinals.setLength(ordinals.length() - 1)
                    }
                }
                indexDrillDownTerms(doc, indexFieldName, dimConfig, facetLabel)
            }
            // Dedupe and encode the ordinals. It's not important that we sort here
            // (SortedNumericDocValuesField will handle this internally), but we
            // sort to identify dups (since SNDVF doesn't dedupe):
            val ords = ordinals.get()
            ords.ints.sort(ords.offset, ords.offset + ords.length)
            var prev = -1
            for (i in 0 until ords.length) {
                val ord = ords.ints[ords.offset + i]
                if (ord > prev) {
                    doc.add(SortedNumericDocValuesField(indexFieldName, ord.toLong()))
                    prev = ord
                }
            }
        }
    }

    private fun indexDrillDownTerms(doc: Document, indexFieldName: String, dimConfig: DimConfig, facetLabel: FacetLabel) {
        if (dimConfig.drillDownTermsIndexing != DrillDownTermsIndexing.NONE) {
            // index full-path drill down term
            doc.add(StringField(indexFieldName, pathToString(facetLabel.components, facetLabel.length), Field.Store.NO))
            when (dimConfig.drillDownTermsIndexing) {
                DrillDownTermsIndexing.NONE, DrillDownTermsIndexing.FULL_PATH_ONLY -> {
                    // these two cases are already handled above
                }
                DrillDownTermsIndexing.DIMENSION_AND_FULL_PATH ->
                    doc.add(StringField(indexFieldName, pathToString(facetLabel.components, 1), Field.Store.NO))
                DrillDownTermsIndexing.ALL_PATHS_NO_DIM -> {
                    for (i in 2 until facetLabel.length) {
                        doc.add(StringField(indexFieldName, pathToString(facetLabel.components, i), Field.Store.NO))
                    }
                }
                DrillDownTermsIndexing.ALL -> {
                    for (i in 1 until facetLabel.length) {
                        doc.add(StringField(indexFieldName, pathToString(facetLabel.components, i), Field.Store.NO))
                    }
                }
            }
        }
    }

    private fun processSSDVFacetFields(byField: Map<String, List<SortedSetDocValuesFacetField>>, doc: Document) {
        for (ent in byField.entries) {
            val indexFieldName = ent.key
            for (facetField in ent.value) {
                val facetLabel = FacetLabel(facetField.dim, facetField.path)
                val dimConfig = getDimConfig(facetField.dim)
                // For facet counts:
                if (dimConfig.hierarchical) {
                    // Index each member of the path explicitly. This is required for hierarchical counting
                    // to work properly since we need to ensure every unique path has a corresponding ordinal
                    // in the SSDV field:
                    for (i in 0 until facetLabel.length) {
                        val fullPath = pathToString(facetLabel.components, i + 1)
                        doc.add(SortedSetDocValuesField(indexFieldName, BytesRef(fullPath)))
                    }
                } else {
                    if (facetLabel.length != 2) {
                        throw IllegalArgumentException("dimension \"${facetField.dim}\" is not hierarchical yet has ${facetField.path.size} components")
                    }
                    if (dimConfig.multiValued && dimConfig.requireDimCount) {
                        // If non-hierarchical but multi-valued and requiring dim count, make sure to
                        // explicitly index the dimension so we can get accurate counts for it:
                        doc.add(SortedSetDocValuesField(indexFieldName, BytesRef(facetField.dim)))
                    }
                    val fullPath = pathToString(facetLabel.components, facetLabel.length)
                    doc.add(SortedSetDocValuesField(indexFieldName, BytesRef(fullPath)))
                }
                // For drill-down:
                indexDrillDownTerms(doc, indexFieldName, dimConfig, facetLabel)
            }
        }
    }

    private fun processAssocFacetFields(taxoWriter: TaxonomyWriter?, byField: Map<String, List<AssociationFacetField>>, doc: Document) {
        for (ent in byField.entries) {
            var bytes = ByteArray(16)
            var upto = 0
            val indexFieldName = ent.key
            for (field in ent.value) {
                // NOTE: we don't add parents for associations
                checkTaxoWriter(taxoWriter)
                val facetLabel = FacetLabel(field.dim, field.path)
                val ordinal = taxoWriter!!.addCategory(facetLabel)
                if (upto + 4 > bytes.size) {
                    bytes = ArrayUtil.grow(bytes, upto + 4)
                }
                // big-endian:
                BitUtil.VH_BE_INT.set(bytes, upto, ordinal)
                upto += 4
                if (upto + field.assoc.length > bytes.size) {
                    bytes = ArrayUtil.grow(bytes, upto + field.assoc.length)
                }
                field.assoc.bytes.copyInto(bytes, upto, field.assoc.offset, field.assoc.offset + field.assoc.length)
                upto += field.assoc.length
                indexDrillDownTerms(doc, indexFieldName, getDimConfig(field.dim), facetLabel)
            }
            doc.add(BinaryDocValuesField(indexFieldName, BytesRef(bytes, 0, upto)))
        }
    }

    private fun checkTaxoWriter(taxoWriter: TaxonomyWriter?) {
        if (taxoWriter == null) {
            throw IllegalStateException("a non-null TaxonomyWriter must be provided when indexing FacetField or AssociationFacetField")
        }
    }

    companion object {
        /** Which Lucene field holds the drill-downs and ords (as doc values). */
        const val DEFAULT_INDEX_FIELD_NAME = "\$facets"
        /** Default per-dimension configuration. */
        val DEFAULT_DIM_CONFIG = DimConfig()

        private fun checkSeen(seenDims: MutableSet<String>, dim: String) {
            if (seenDims.contains(dim)) {
                throw IllegalArgumentException("dimension \"$dim\" is not multiValued, but it appears more than once in this document")
            }
            seenDims.add(dim)
        }

        /**
   * Character used to join the category path components together into a single drill down term for
   * indexing. Applications and unit-tests can reference this for creating their own drill-down
   * terms, or use existing APIs (for example, [pathToString]).
   *
   * @lucene.internal
   */
        const val DELIM_CHAR = '\u001F'
        // Escapes any occurrence of the path component inside the label:
        private const val ESCAPE_CHAR = '\u001E'

        /** Turns a dim + path into an encoded string. */
        fun pathToString(dim: String, vararg path: String): String {
            val fullPath = Array(1 + path.size) { "" }
            fullPath[0] = dim
            path.copyInto(fullPath, 1, 0, path.size)
            return pathToString(fullPath, fullPath.size)
        }

        /** Turns a dim + path into an encoded string. */
        fun pathToString(path: Array<out String>): String = pathToString(path, path.size)

        /** Turns the first `length` elements of `path` into an encoded string. */
        fun pathToString(path: Array<out String>, length: Int): String {
            if (length == 0) {
                return ""
            }
            val sb = StringBuilder()
            for (i in 0 until length) {
                val s = path[i]
                if (s.isEmpty()) {
                    throw IllegalArgumentException("each path component must have length > 0 (got: \"\")")
                }
                val numChars = s.length
                for (j in 0 until numChars) {
                    val ch = s[j]
                    if (ch == DELIM_CHAR || ch == ESCAPE_CHAR) {
                        sb.append(ESCAPE_CHAR)
                    }
                    sb.append(ch)
                }
                sb.append(DELIM_CHAR)
            }
            // Trim off last DELIM_CHAR:
            sb.setLength(sb.length - 1)
            return sb.toString()
        }

        /**
   * Turns an encoded string (from a previous call to [pathToString]) back into the original
   * `String[]`.
   */
        fun stringToPath(s: String): Array<String> {
            val parts = mutableListOf<String>()
            val length = s.length
            if (length == 0) {
                return emptyArray()
            }
            val buffer = CharArray(length)
            var upto = 0
            var lastEscape = false
            for (i in 0 until length) {
                val ch = s[i]
                if (lastEscape) {
                    buffer[upto++] = ch
                    lastEscape = false
                } else if (ch == ESCAPE_CHAR) {
                    lastEscape = true
                } else if (ch == DELIM_CHAR) {
                    parts.add(buffer.concatToString(0, upto))
                    upto = 0
                } else {
                    buffer[upto++] = ch
                }
            }
            parts.add(buffer.concatToString(0, upto))
            assert(!lastEscape)
            return parts.toTypedArray()
        }
    }
}
