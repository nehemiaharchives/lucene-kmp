package org.gnit.lucenekmp.codecs.lucene90

import kotlinx.io.IOException
import org.gnit.lucenekmp.codecs.NormsConsumer
import org.gnit.lucenekmp.codecs.NormsFormat
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState

/**
 * Lucene 9.0 Score normalization format.
 *
 *
 * Encodes normalization values by encoding each value with the minimum number of bytes needed to
 * represent the range (which can be zero).
 *
 *
 * Files:
 *
 *
 *  1. `.nvd`: Norms data
 *  1. `.nvm`: Norms metadata
 *
 *
 *
 *  1. <a id="nvm"></a>
 *
 * The Norms metadata or .nvm file.
 *
 * For each norms field, this stores metadata, such as the offset into the Norms data
 * (.nvd)
 *
 * Norms metadata (.dvm) --&gt; Header,&lt;Entry&gt;<sup>NumFields</sup>,Footer
 *
 *  * Header --&gt; [IndexHeader][CodecUtil.writeIndexHeader]
 *  * Entry --&gt; FieldNumber, DocsWithFieldAddress, DocsWithFieldLength,
 * NumDocsWithField, BytesPerNorm, NormsAddress
 *  * FieldNumber --&gt; [Int32][DataOutput.writeInt]
 *  * DocsWithFieldAddress --&gt; [Int64][DataOutput.writeLong]
 *  * DocsWithFieldLength --&gt; [Int64][DataOutput.writeLong]
 *  * NumDocsWithField --&gt; [Int32][DataOutput.writeInt]
 *  * BytesPerNorm --&gt; [byte][DataOutput.writeByte]
 *  * NormsAddress --&gt; [Int64][DataOutput.writeLong]
 *  * Footer --&gt; [CodecFooter][CodecUtil.writeFooter]
 *
 *
 * FieldNumber of -1 indicates the end of metadata.
 *
 * NormsAddress is the pointer to the start of the data in the norms data (.nvd), or the
 * singleton value when BytesPerValue = 0. If BytesPerValue is different from 0 then there are
 * NumDocsWithField values to read at that offset.
 *
 * DocsWithFieldAddress is the pointer to the start of the bit set containing documents
 * that have a norm in the norms data (.nvd), or -2 if no documents have a norm value, or -1
 * if all documents have a norm value.
 *
 * DocsWithFieldLength is the number of bytes used to encode the set of documents that have
 * a norm.
 *  1. <a id="nvd"></a>
 *
 * The Norms data or .nvd file.
 *
 * For each Norms field, this stores the actual per-document data (the heavy-lifting)
 *
 * Norms data (.nvd) --&gt; Header,&lt; Data &gt;<sup>NumFields</sup>,Footer
 *
 *  * Header --&gt; [IndexHeader][CodecUtil.writeIndexHeader]
 *  * DocsWithFieldData --&gt; [Bit set of MaxDoc bits][IndexedDISI.writeBitSet]
 *  * NormsData --&gt; [byte][DataOutput.writeByte]<sup>NumDocsWithField *
 * BytesPerValue</sup>
 *  * Footer --&gt; [CodecFooter][CodecUtil.writeFooter]
 *
 *
 *
 * @lucene.experimental
 */
class Lucene90NormsFormat
/** Sole Constructor  */
    : NormsFormat() {
    @Throws(IOException::class)
    override fun normsConsumer(state: SegmentWriteState): NormsConsumer {
        return Lucene90NormsConsumer(
            state, DATA_CODEC, DATA_EXTENSION, METADATA_CODEC, METADATA_EXTENSION
        )
    }

    @Throws(IOException::class)
    override fun normsProducer(state: SegmentReadState): NormsProducer {
        return Lucene90NormsProducer(
            state, DATA_CODEC, DATA_EXTENSION, METADATA_CODEC, METADATA_EXTENSION
        )
    }

    companion object {
        private const val DATA_CODEC = "Lucene90NormsData"
        private const val DATA_EXTENSION = "nvd"
        private const val METADATA_CODEC = "Lucene90NormsMetadata"
        private const val METADATA_EXTENSION = "nvm"
        const val VERSION_START: Int = 0
        const val VERSION_CURRENT: Int = VERSION_START
    }
}
