package org.gnit.lucenekmp.search

import okio.IOException
import org.gnit.lucenekmp.document.KnnFloatVectorField
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.DocValuesType.NONE
import org.gnit.lucenekmp.index.DocValuesType.NUMERIC
import org.gnit.lucenekmp.index.DocValuesType.SORTED
import org.gnit.lucenekmp.index.DocValuesType.SORTED_NUMERIC
import org.gnit.lucenekmp.index.DocValuesType.SORTED_SET
import org.gnit.lucenekmp.index.DocValuesType.BINARY
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.PointValues
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.VectorEncoding.FLOAT32
import org.gnit.lucenekmp.index.VectorEncoding.BYTE


/**
 * A [Query] that matches documents that contain either a [KnnFloatVectorField], [ ] or a field that indexes norms or doc values.
 */
class FieldExistsQuery(val field: String) : Query() {

    override fun toString(field: String?): String {
        return "FieldExistsQuery [field=" + this.field + "]"
    }

    override fun visit(visitor: QueryVisitor) {
        if (visitor.acceptField(field)) {
            visitor.visitLeaf(this)
        }
    }

    override fun equals(other: Any?): Boolean {
        return sameClassAs(other) && field == (other as FieldExistsQuery).field
    }

    override fun hashCode(): Int {
        val prime = 31
        var hash = classHash()
        hash = prime * hash + field.hashCode()
        return hash
    }

    override fun rewrite(indexSearcher: IndexSearcher): Query {
        val reader: IndexReader = indexSearcher.getIndexReader()
        var allReadersRewritable = true

        for (context in reader.leaves()) {
            val leaf: LeafReader = context.reader()
            val fieldInfos: FieldInfos = leaf.fieldInfos
            val fieldInfo: FieldInfo? = fieldInfos.fieldInfo(field)

            if (fieldInfo == null) {
                allReadersRewritable = false
                break
            }

            if (fieldInfo.hasNorms()) { // the field indexes norms
                if (reader.getDocCount(field) != reader.maxDoc()) {
                    allReadersRewritable = false
                    break
                }
            } else if (fieldInfo.vectorDimension != 0) { // the field indexes vectors
                if (getVectorValuesSize(fieldInfo, leaf) != leaf.maxDoc()) {
                    allReadersRewritable = false
                    break
                }
            } else if (fieldInfo.docValuesType
                !== NONE
            ) { // the field indexes doc values or points

                // This optimization is possible due to LUCENE-9334 enforcing a field to always uses the
                // same data structures (all or nothing). Since there's no index statistic to detect when
                // all documents have doc values for a specific field, FieldExistsQuery can only be
                // rewritten to MatchAllDocsQuery for doc values field, when that same field also indexes
                // terms or point values which do have index statistics, and those statistics confirm that
                // all documents in this segment have values terms or point values.

                val terms: Terms? = leaf.terms(field)
                val pointValues: PointValues? = leaf.getPointValues(field)

                if ((terms == null || terms.docCount != leaf.maxDoc())
                    && (pointValues == null || pointValues.docCount != leaf.maxDoc())
                ) {
                    allReadersRewritable = false
                    break
                }
            } else {
                throw IllegalStateException(buildErrorMsg(fieldInfo))
            }
        }
        if (allReadersRewritable) {
            return MatchAllDocsQuery()
        }
        return super.rewrite(indexSearcher)
    }

    override fun createWeight(searcher: IndexSearcher, scoreMode: ScoreMode, boost: Float): Weight {
        return object : ConstantScoreWeight(this, boost) {
            @Throws(IOException::class)
            override fun scorerSupplier(context: LeafReaderContext): ScorerSupplier? {
                val fieldInfos: FieldInfos = context.reader().fieldInfos
                val fieldInfo: FieldInfo? = fieldInfos.fieldInfo(field)
                var iterator: DocIdSetIterator?

                if (fieldInfo == null) {
                    return null
                }

                if (fieldInfo.hasNorms()) { // the field indexes norms
                    iterator = context.reader().getNormValues(field)
                } else if (fieldInfo.vectorDimension != 0) { // the field indexes vectors
                    iterator =
                        when (fieldInfo.vectorEncoding) {
                            FLOAT32 -> context.reader().getFloatVectorValues(field)!!.iterator()
                            BYTE -> context.reader().getByteVectorValues(field)!!.iterator()
                        }
                } else if (fieldInfo.docValuesType !== NONE
                ) { // the field indexes doc values
                    iterator = when (fieldInfo.docValuesType) {
                        NUMERIC -> context.reader().getNumericDocValues(field)
                        BINARY -> context.reader().getBinaryDocValues(field)
                        SORTED -> context.reader().getSortedDocValues(field)
                        SORTED_NUMERIC -> context.reader().getSortedNumericDocValues(field)
                        SORTED_SET -> context.reader().getSortedSetDocValues(field)
                        NONE -> throw AssertionError()
                    }
                } else {
                    throw IllegalStateException(buildErrorMsg(fieldInfo))
                }

                if (iterator == null) {
                    return null
                }
                val scorer = ConstantScoreScorer(score(), scoreMode, iterator)
                return DefaultScorerSupplier(scorer)
            }

            @Throws(IOException::class)
            override fun count(context: LeafReaderContext): Int {
                val reader: LeafReader = context.reader()
                val fieldInfos: FieldInfos = reader.fieldInfos
                val fieldInfo: FieldInfo? = fieldInfos.fieldInfo(field)

                if (fieldInfo == null) {
                    return 0
                }

                if (fieldInfo.hasNorms()) { // the field indexes norms
                    // If every field has a value then we can shortcut
                    if (reader.getDocCount(field) == reader.maxDoc()) {
                        return reader.numDocs()
                    }

                    return super.count(context)
                } else if (fieldInfo.hasVectorValues()) { // the field indexes vectors
                    if (!reader.hasDeletions()) {
                        return getVectorValuesSize(fieldInfo, reader)
                    }
                    return super.count(context)
                } else if (fieldInfo.docValuesType
                    !== NONE
                ) { // the field indexes doc values
                    if (!reader.hasDeletions()) {
                        if (fieldInfo.pointDimensionCount > 0) {
                            val pointValues: PointValues? = reader.getPointValues(field)
                            return pointValues?.docCount ?: 0
                        } else if (fieldInfo.indexOptions !== IndexOptions.NONE) {
                            val terms: Terms? = reader.terms(field)
                            return terms?.docCount ?: 0
                        }
                    }

                    return super.count(context)
                } else {
                    throw IllegalStateException(buildErrorMsg(fieldInfo))
                }
            }

            override fun isCacheable(context: LeafReaderContext): Boolean {
                val fieldInfos: FieldInfos = context.reader().fieldInfos
                val fieldInfo: FieldInfo? = fieldInfos.fieldInfo(field)

                if (fieldInfo != null && fieldInfo.docValuesType !== NONE) {
                    return DocValues.isCacheable(context, field)
                }

                return true
            }
        }
    }

    private fun buildErrorMsg(fieldInfo: FieldInfo): String {
        return ("FieldExistsQuery requires that the field indexes doc values, norms or vectors, but field '"
                + fieldInfo.name
                + "' exists and indexes neither of these data structures")
    }

    @Throws(IOException::class)
    private fun getVectorValuesSize(fi: FieldInfo, reader: LeafReader): Int {
        require(fi.name == field)
        return when (fi.vectorEncoding) {
            FLOAT32 -> {
                val floatVectorValues: FloatVectorValues =
                    checkNotNull(reader.getFloatVectorValues(field)) { "unexpected null float vector values" }
                floatVectorValues.size()
            }

            BYTE -> {
                val byteVectorValues: ByteVectorValues =
                    checkNotNull(reader.getByteVectorValues(field)) { "unexpected null byte vector values" }
                byteVectorValues.size()
            }
        }
    }

    companion object {
        /**
         * Returns a [DocIdSetIterator] from the given field or null if the field doesn't exist in
         * the reader or if the reader has no doc values for the field.
         */
        @Throws(IOException::class)
        fun getDocValuesDocIdSetIterator(field: String, reader: LeafReader): DocIdSetIterator? {
            val fieldInfo: FieldInfo? = reader.fieldInfos.fieldInfo(field)
            val iterator: DocIdSetIterator?
            if (fieldInfo != null) {
                iterator = when (fieldInfo.docValuesType) {
                    NONE -> null
                    NUMERIC -> reader.getNumericDocValues(field)
                    BINARY -> reader.getBinaryDocValues(field)
                    SORTED -> reader.getSortedDocValues(field)
                    SORTED_NUMERIC -> reader.getSortedNumericDocValues(field)
                    SORTED_SET -> reader.getSortedSetDocValues(field)
                }
                return iterator
            }
            return null
        }
    }
}
