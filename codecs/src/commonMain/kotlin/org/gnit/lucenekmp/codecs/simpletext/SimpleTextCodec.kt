package org.gnit.lucenekmp.codecs.simpletext

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

/**
 * plain text index format.
 *
 *
 * **FOR RECREATIONAL USE ONLY**
 *
 * @lucene.experimental
 */
class SimpleTextCodec : Codec("SimpleText") {
    private val postings: PostingsFormat = SimpleTextPostingsFormat()
    private val storedFields: StoredFieldsFormat = SimpleTextStoredFieldsFormat()
    private val segmentInfos: SegmentInfoFormat = SimpleTextSegmentInfoFormat()
    private val fieldInfosFormat: FieldInfosFormat = SimpleTextFieldInfosFormat()
    private val vectorsFormat: TermVectorsFormat = SimpleTextTermVectorsFormat()
    private val normsFormat: NormsFormat = SimpleTextNormsFormat()
    private val liveDocs: LiveDocsFormat = SimpleTextLiveDocsFormat()
    private val dvFormat: DocValuesFormat = SimpleTextDocValuesFormat()
    private val compoundFormat: CompoundFormat = SimpleTextCompoundFormat()
    private val pointsFormat: PointsFormat = SimpleTextPointsFormat()
    private val knnVectorsFormat: KnnVectorsFormat = SimpleTextKnnVectorsFormat()

    override fun postingsFormat(): PostingsFormat {
        return postings
    }

    override fun storedFieldsFormat(): StoredFieldsFormat {
        return storedFields
    }

    override fun termVectorsFormat(): TermVectorsFormat {
        return vectorsFormat
    }

    override fun fieldInfosFormat(): FieldInfosFormat {
        return fieldInfosFormat
    }

    override fun segmentInfoFormat(): SegmentInfoFormat {
        return segmentInfos
    }

    override fun normsFormat(): NormsFormat {
        return normsFormat
    }

    override fun liveDocsFormat(): LiveDocsFormat {
        return liveDocs
    }

    override fun docValuesFormat(): DocValuesFormat {
        return dvFormat
    }

    override fun compoundFormat(): CompoundFormat {
        return compoundFormat
    }

    override fun pointsFormat(): PointsFormat {
        return pointsFormat
    }

    override fun knnVectorsFormat(): KnnVectorsFormat {
        return knnVectorsFormat
    }
}
