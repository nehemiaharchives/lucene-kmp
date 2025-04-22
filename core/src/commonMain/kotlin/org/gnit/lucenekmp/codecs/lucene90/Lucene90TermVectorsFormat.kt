package org.gnit.lucenekmp.codecs.lucene90

import org.gnit.lucenekmp.codecs.compressing.CompressionMode
import org.gnit.lucenekmp.codecs.lucene90.compressing.Lucene90CompressingTermVectorsFormat

/**
 * Lucene 9.0 [term vectors format][TermVectorsFormat].
 *
 *
 * Very similarly to [Lucene90StoredFieldsFormat], this format is based on compressed
 * chunks of data, with document-level granularity so that a document can never span across distinct
 * chunks. Moreover, data is made as compact as possible:
 *
 *
 *  * textual data is compressed using the very light, [LZ4](http://code.google.com/p/lz4/) compression algorithm,
 *  * binary data is written using fixed-size blocks of [packed ints][PackedInts].
 *
 *
 *
 * Term vectors are stored using two files
 *
 *
 *  * a data file where terms, frequencies, positions, offsets and payloads are stored,
 *  * an index file, loaded into memory, used to locate specific documents in the data file.
 *
 *
 * Looking up term vectors for any document requires at most 1 disk seek.
 *
 *
 * **File formats**
 *
 *
 *  1. <a id="vector_meta"></a>
 *
 * A vector metadata file (extension `.tvm`).
 *
 *  * VectorMeta (.tvm) --&gt; &lt;Header&gt;, PackedIntsVersion, ChunkSize,
 * ChunkIndexMetadata, ChunkCount, DirtyChunkCount, DirtyDocsCount, Footer
 *  * Header --&gt; [IndexHeader][CodecUtil.writeIndexHeader]
 *  * PackedIntsVersion, ChunkSize --&gt; [VInt][DataOutput.writeVInt]
 *  * ChunkCount, DirtyChunkCount, DirtyDocsCount --&gt; [             VLong][DataOutput.writeVLong]
 *  * ChunkIndexMetadata --&gt; [FieldsIndexWriter]
 *  * Footer --&gt; [CodecFooter][CodecUtil.writeFooter]
 *
 *
 * Notes:
 *
 *  * PackedIntsVersion is [PackedInts.VERSION_CURRENT].
 *  * ChunkSize is the number of bytes of terms to accumulate before flushing.
 *  * ChunkCount is not known in advance and is the number of chunks necessary to store all
 * document of the segment.
 *  * DirtyChunkCount is the number of prematurely flushed chunks in the .tvd file.
 *
 *  1. <a id="vector_data"></a>
 *
 * A vector data file (extension `.tvd`). This file stores terms, frequencies,
 * positions, offsets and payloads for every document. Upon writing a new segment, it
 * accumulates data into memory until the buffer used to store terms and payloads grows beyond
 * 4KB. Then it flushes all metadata, terms and positions to disk using [LZ4](http://code.google.com/p/lz4/) compression for terms and payloads and [       ] for positions.
 *
 * Here is a more detailed description of the field data file format:
 *
 *  * VectorData (.tvd) --&gt; &lt;Header&gt;, &lt;Chunk&gt;<sup>ChunkCount</sup>, Footer
 *  * Header --&gt; [IndexHeader][CodecUtil.writeIndexHeader]
 *  * Chunk --&gt; DocBase, ChunkDocs, &lt; NumFields &gt;, &lt; FieldNums &gt;, &lt;
 * FieldNumOffs &gt;, &lt; Flags &gt;, &lt; NumTerms &gt;, &lt; TermLengths &gt;, &lt;
 * TermFreqs &gt;, &lt; Positions &gt;, &lt; StartOffsets &gt;, &lt; Lengths &gt;, &lt;
 * PayloadLengths &gt;, &lt; TermAndPayloads &gt;
 *  * NumFields --&gt; DocNumFields<sup>ChunkDocs</sup>
 *  * FieldNums --&gt; FieldNumDelta<sup>TotalDistincFields</sup>
 *  * Flags --&gt; Bit &lt; FieldFlags &gt;
 *  * FieldFlags --&gt; if Bit==1: Flag<sup>TotalDistinctFields</sup> else
 * Flag<sup>TotalFields</sup>
 *  * NumTerms --&gt; FieldNumTerms<sup>TotalFields</sup>
 *  * TermLengths --&gt; PrefixLength<sup>TotalTerms</sup>
 * SuffixLength<sup>TotalTerms</sup>
 *  * TermFreqs --&gt; TermFreqMinus1<sup>TotalTerms</sup>
 *  * Positions --&gt; PositionDelta<sup>TotalPositions</sup>
 *  * StartOffsets --&gt; (AvgCharsPerTerm<sup>TotalDistinctFields</sup>)
 * StartOffsetDelta<sup>TotalOffsets</sup>
 *  * Lengths --&gt; LengthMinusTermLength<sup>TotalOffsets</sup>
 *  * PayloadLengths --&gt; PayloadLength<sup>TotalPayloads</sup>
 *  * TermAndPayloads --&gt; LZ4-compressed representation of &lt; FieldTermsAndPayLoads
 * &gt;<sup>TotalFields</sup>
 *  * FieldTermsAndPayLoads --&gt; Terms (Payloads)
 *  * DocBase, ChunkDocs, DocNumFields (with ChunkDocs==1) --&gt; [             ][DataOutput.writeVInt]
 *  * AvgCharsPerTerm --&gt; [Int][DataOutput.writeInt]
 *  * DocNumFields (with ChunkDocs&gt;=1), FieldNumOffs --&gt; [PackedInts] array
 *  * FieldNumTerms, PrefixLength, SuffixLength, TermFreqMinus1, PositionDelta,
 * StartOffsetDelta, LengthMinusTermLength, PayloadLength --&gt; [             ]
 *  * Footer --&gt; [CodecFooter][CodecUtil.writeFooter]
 *
 *
 * Notes:
 *
 *  * DocBase is the ID of the first doc of the chunk.
 *  * ChunkDocs is the number of documents in the chunk.
 *  * DocNumFields is the number of fields for each doc.
 *  * FieldNums is a delta-encoded list of the sorted unique field numbers present in the
 * chunk.
 *  * FieldNumOffs is the array of FieldNumOff; array size is the total number of fields in
 * the chunk.
 *  * FieldNumOff is the offset of the field number in FieldNums.
 *  * TotalFields is the total number of fields (sum of the values of NumFields).
 *  * Bit in Flags is a single bit which when true means that fields have the same options
 * for every document in the chunk.
 *  * Flag: a 3-bits int where:
 *
 *  * the first bit means that the field has positions
 *  * the second bit means that the field has offsets
 *  * the third bit means that the field has payloads
 *
 *  * FieldNumTerms is the number of terms for each field.
 *  * TotalTerms is the total number of terms (sum of NumTerms).
 *  * PrefixLength is 0 for the first term of a field, the common prefix with the previous
 * term otherwise.
 *  * SuffixLength is the length of the term minus PrefixLength for every term using.
 *  * TermFreqMinus1 is (frequency - 1) for each term.
 *  * TotalPositions is the sum of frequencies of terms of all fields that have positions.
 *  * PositionDelta is the absolute position for the first position of a term, and the
 * difference with the previous positions for following positions.
 *  * TotalOffsets is the sum of frequencies of terms of all fields that have offsets.
 *  * AvgCharsPerTerm is the average number of chars per term, encoded as a float on 4
 * bytes. They are not present if no field has both positions and offsets enabled.
 *  * StartOffsetDelta is the (startOffset - previousStartOffset - AvgCharsPerTerm *
 * PositionDelta). previousStartOffset is 0 for the first offset and AvgCharsPerTerm is
 * 0 if the field has no positions.
 *  * LengthMinusTermLength is (endOffset - startOffset - termLength).
 *  * TotalPayloads is the sum of frequencies of terms of all fields that have payloads.
 *  * PayloadLength is the payload length encoded.
 *  * Terms is term bytes.
 *  * Payloads is payload bytes (if the field has payloads).
 *
 *  1. <a id="vector_index"></a>
 *
 * An index file (extension `.tvx`).
 *
 *  * VectorIndex (.tvx) --&gt; &lt;Header&gt;, &lt;ChunkIndex&gt;, Footer
 *  * Header --&gt; [IndexHeader][CodecUtil.writeIndexHeader]
 *  * ChunkIndex --&gt; [FieldsIndexWriter]
 *  * Footer --&gt; [CodecFooter][CodecUtil.writeFooter]
 *
 *
 *
 * @lucene.experimental
 */
class Lucene90TermVectorsFormat
/** Sole constructor.  */
    : Lucene90CompressingTermVectorsFormat("Lucene90TermVectorsData", "", CompressionMode.FAST, 1 shl 12, 128, 10)
