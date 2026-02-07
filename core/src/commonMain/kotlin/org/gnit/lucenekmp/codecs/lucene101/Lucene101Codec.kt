package org.gnit.lucenekmp.codecs.lucene101

import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.CompoundFormat
import org.gnit.lucenekmp.codecs.DocValuesFormat
import org.gnit.lucenekmp.codecs.FieldInfosFormat
import org.gnit.lucenekmp.codecs.KnnVectorsFormat
import org.gnit.lucenekmp.codecs.LiveDocsFormat
import org.gnit.lucenekmp.codecs.NormsFormat
import org.gnit.lucenekmp.codecs.PointsFormat
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.codecs.SegmentInfoFormat
import org.gnit.lucenekmp.codecs.StoredFieldsFormat
import org.gnit.lucenekmp.codecs.TermVectorsFormat
import org.gnit.lucenekmp.codecs.lucene90.Lucene90CompoundFormat
import org.gnit.lucenekmp.codecs.lucene90.Lucene90DocValuesFormat
import org.gnit.lucenekmp.codecs.lucene90.Lucene90LiveDocsFormat
import org.gnit.lucenekmp.codecs.lucene90.Lucene90NormsFormat
import org.gnit.lucenekmp.codecs.lucene90.Lucene90PointsFormat
import org.gnit.lucenekmp.codecs.lucene90.Lucene90StoredFieldsFormat
import org.gnit.lucenekmp.codecs.lucene90.Lucene90TermVectorsFormat
import org.gnit.lucenekmp.codecs.lucene94.Lucene94FieldInfosFormat
import org.gnit.lucenekmp.codecs.lucene99.Lucene99HnswVectorsFormat
import org.gnit.lucenekmp.codecs.lucene99.Lucene99SegmentInfoFormat
import org.gnit.lucenekmp.codecs.perfield.PerFieldDocValuesFormat
import org.gnit.lucenekmp.codecs.perfield.PerFieldKnnVectorsFormat
import org.gnit.lucenekmp.codecs.perfield.PerFieldPostingsFormat
import kotlin.jvm.JvmOverloads


/**
 * Implements the Lucene 10.1 index format
 *
 *
 * If you want to reuse functionality of this codec in another codec, extend [FilterCodec].
 *
 * @see org.apache.lucene.codecs.lucene101 package documentation for file format details.
 *
 * @lucene.experimental
 */
class Lucene101Codec @JvmOverloads constructor(mode: Mode? = Mode.BEST_SPEED) : Codec("Lucene101") {
    /** Configuration option for the codec.  */
    enum class Mode(val storedMode: Lucene90StoredFieldsFormat.Mode) {
        /** Trade compression ratio for retrieval speed.  */
        BEST_SPEED(Lucene90StoredFieldsFormat.Mode.BEST_SPEED),

        /** Trade retrieval speed for compression ratio.  */
        BEST_COMPRESSION(Lucene90StoredFieldsFormat.Mode.BEST_COMPRESSION);

    }

    private val vectorsFormat: TermVectorsFormat = Lucene90TermVectorsFormat()
    private val fieldInfosFormat: FieldInfosFormat = Lucene94FieldInfosFormat()
    private val segmentInfosFormat: SegmentInfoFormat = Lucene99SegmentInfoFormat()
    private val liveDocsFormat: LiveDocsFormat = Lucene90LiveDocsFormat()
    private val compoundFormat: CompoundFormat = Lucene90CompoundFormat()
    private val normsFormat: NormsFormat = Lucene90NormsFormat()

    private val defaultPostingsFormat: PostingsFormat = Lucene101PostingsFormat()
    private val postingsFormat: PostingsFormat = object : PerFieldPostingsFormat() {
        override fun getPostingsFormatForField(field: String): PostingsFormat {
            return this@Lucene101Codec.getPostingsFormatForField(field)
        }
    }

    private val defaultDVFormat: DocValuesFormat = Lucene90DocValuesFormat()
    private val docValuesFormat: DocValuesFormat = object : PerFieldDocValuesFormat() {
        override fun getDocValuesFormatForField(field: String): DocValuesFormat {
            return this@Lucene101Codec.getDocValuesFormatForField(field)
        }
    }

    private val defaultKnnVectorsFormat: KnnVectorsFormat = Lucene99HnswVectorsFormat()
    private val knnVectorsFormat: KnnVectorsFormat = object : PerFieldKnnVectorsFormat() {
        override fun getKnnVectorsFormatForField(field: String): KnnVectorsFormat {
            return this@Lucene101Codec.getKnnVectorsFormatForField(field)
        }
    }

    private val storedFieldsFormat: StoredFieldsFormat
    init {
        val checkedMode = mode ?: throw NullPointerException()
        storedFieldsFormat = Lucene90StoredFieldsFormat(checkedMode.storedMode)
    }
    /**
     * Instantiates a new codec, specifying the stored fields compression mode to use.
     *
     * @param mode stored fields compression mode to use for newly flushed/merged segments.
     */

    override fun storedFieldsFormat(): StoredFieldsFormat {
        return storedFieldsFormat
    }

    override fun termVectorsFormat(): TermVectorsFormat {
        return vectorsFormat
    }

    override fun postingsFormat(): PostingsFormat {
        return postingsFormat
    }

    override fun fieldInfosFormat(): FieldInfosFormat {
        return fieldInfosFormat
    }

    override fun segmentInfoFormat(): SegmentInfoFormat {
        return segmentInfosFormat
    }

    override fun liveDocsFormat(): LiveDocsFormat {
        return liveDocsFormat
    }

    override fun compoundFormat(): CompoundFormat {
        return compoundFormat
    }

    override fun pointsFormat(): PointsFormat {
        return Lucene90PointsFormat()
    }

    override fun knnVectorsFormat(): KnnVectorsFormat {
        return knnVectorsFormat
    }

    /**
     * Returns the postings format that should be used for writing new segments of `field`.
     *
     *
     * The default implementation always returns "Lucene101".
     *
     *
     * **WARNING:** if you subclass, you are responsible for index backwards compatibility:
     * future version of Lucene are only guaranteed to be able to read the default implementation,
     */
    fun getPostingsFormatForField(field: String): PostingsFormat {
        return defaultPostingsFormat
    }

    /**
     * Returns the docvalues format that should be used for writing new segments of `field`
     * .
     *
     *
     * The default implementation always returns "Lucene90".
     *
     *
     * **WARNING:** if you subclass, you are responsible for index backwards compatibility:
     * future version of Lucene are only guaranteed to be able to read the default implementation.
     */
    fun getDocValuesFormatForField(field: String): DocValuesFormat {
        return defaultDVFormat
    }

    /**
     * Returns the vectors format that should be used for writing new segments of `field`
     *
     *
     * The default implementation always returns "Lucene99HnswVectorsFormat".
     *
     *
     * **WARNING:** if you subclass, you are responsible for index backwards compatibility:
     * future version of Lucene are only guaranteed to be able to read the default implementation.
     */
    fun getKnnVectorsFormatForField(field: String): KnnVectorsFormat {
        return defaultKnnVectorsFormat
    }

    override fun docValuesFormat(): DocValuesFormat {
        return docValuesFormat
    }

    override fun normsFormat(): NormsFormat {
        return normsFormat
    }
}
