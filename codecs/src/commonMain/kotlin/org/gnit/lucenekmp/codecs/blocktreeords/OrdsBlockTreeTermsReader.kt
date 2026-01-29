package org.gnit.lucenekmp.codecs.blocktreeords

import okio.IOException
import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.PostingsReaderBase
import org.gnit.lucenekmp.codecs.blocktreeords.FSTOrdsOutputs.Output
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.jdkport.TreeMap
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils

/**
 * See {@link OrdsBlockTreeTermsWriter}.
 *
 * @lucene.experimental
 */
/** Minimal port of OrdsBlockTreeTermsReader with Java-matching signature. */
internal class OrdsBlockTreeTermsReader(
  internal val postingsReader: PostingsReaderBase,
  state: SegmentReadState
) : FieldsProducer() {
  internal val `in`: IndexInput
  private val fields: TreeMap<String, OrdsFieldReader> = TreeMap()

  init {
    val termsFile = IndexFileNames.segmentFileName(
      state.segmentInfo.name,
      state.segmentSuffix,
      OrdsBlockTreeTermsWriter.TERMS_EXTENSION
    )
    `in` = state.directory.openInput(termsFile, state.context)

    var success = false
    var indexIn: IndexInput? = null
    try {
      val version = CodecUtil.checkIndexHeader(
        `in`,
        OrdsBlockTreeTermsWriter.TERMS_CODEC_NAME,
        OrdsBlockTreeTermsWriter.VERSION_START,
        OrdsBlockTreeTermsWriter.VERSION_CURRENT,
        state.segmentInfo.getId(),
        state.segmentSuffix
      )

      val indexFile = IndexFileNames.segmentFileName(
        state.segmentInfo.name,
        state.segmentSuffix,
        OrdsBlockTreeTermsWriter.TERMS_INDEX_EXTENSION
      )
      indexIn = state.directory.openInput(indexFile, state.context)
      val indexVersion = CodecUtil.checkIndexHeader(
        indexIn,
        OrdsBlockTreeTermsWriter.TERMS_INDEX_CODEC_NAME,
        OrdsBlockTreeTermsWriter.VERSION_START,
        OrdsBlockTreeTermsWriter.VERSION_CURRENT,
        state.segmentInfo.getId(),
        state.segmentSuffix
      )
      if (indexVersion != version) {
        throw CorruptIndexException(
          "mixmatched version files: $`in`=$version,$indexIn=$indexVersion",
          indexIn
        )
      }

      // verify index
      CodecUtil.checksumEntireFile(indexIn)

      // Have PostingsReader init itself
      postingsReader.init(`in`, state)

        // NOTE: data file is too costly to verify checksum against all the bytes on open,
        // but for now we at least verify proper structure of the checksum footer: which looks
        // for FOOTER_MAGIC + algorithmID. This is cheap and can detect some forms of corruption
        // such as file truncation.
      CodecUtil.retrieveChecksum(`in`)

      // Read per-field details
      seekDir(`in`)
      seekDir(indexIn)

      val numFields = `in`.readVInt()
      if (numFields < 0) {
        throw CorruptIndexException("invalid numFields: $numFields", `in`)
      }

      for (i in 0 until numFields) {
        val field = `in`.readVInt()
        val numTerms = `in`.readVLong()
        require(numTerms >= 0)
        val numBytes = `in`.readVInt()
        val code = BytesRef(ByteArray(numBytes))
        `in`.readBytes(code.bytes, 0, numBytes)
        code.length = numBytes
        val rootCode: Output = OrdsBlockTreeTermsWriter.FST_OUTPUTS.newOutput(code, 0, numTerms)
        val fieldInfo: FieldInfo = state.fieldInfos.fieldInfo(field)
          ?: throw CorruptIndexException("field=$field", `in`)
        require(numTerms <= Int.MAX_VALUE)
        val sumTotalTermFreq = `in`.readVLong()
        val sumDocFreq = if (fieldInfo.indexOptions == IndexOptions.DOCS) {
          sumTotalTermFreq
        } else {
          `in`.readVLong()
        }
        val docCount = `in`.readVInt()

        val minTerm = readBytesRef(`in`)
        val maxTerm = readBytesRef(`in`)
        if (docCount < 0 || docCount > state.segmentInfo.maxDoc()) {
          throw CorruptIndexException(
            "invalid docCount: $docCount maxDoc: ${state.segmentInfo.maxDoc()}",
            `in`
          )
        }
        if (sumDocFreq < docCount) {
          throw CorruptIndexException(
            "invalid sumDocFreq: $sumDocFreq docCount: $docCount",
            `in`
          )
        }
        if (sumTotalTermFreq < sumDocFreq) {
          throw CorruptIndexException(
            "invalid sumTotalTermFreq: $sumTotalTermFreq sumDocFreq: $sumDocFreq",
            `in`
          )
        }
        val indexStartFP = indexIn.readVLong()
        val previous = fields.put(
          fieldInfo.name,
          OrdsFieldReader(
            this,
            fieldInfo,
            numTerms,
            rootCode,
            sumTotalTermFreq,
            sumDocFreq,
            docCount,
            indexStartFP,
            indexIn,
            minTerm,
            maxTerm
          )
        )
        if (previous != null) {
          throw CorruptIndexException("duplicate field: ${fieldInfo.name}", `in`)
        }
      }

      indexIn.close()
      success = true
    } finally {
      if (!success) {
        IOUtils.closeWhileHandlingException(indexIn, this)
      }
    }
  }

  private fun readBytesRef(input: IndexInput): BytesRef {
    val bytes = BytesRef()
    bytes.length = input.readVInt()
    bytes.bytes = ByteArray(bytes.length)
    input.readBytes(bytes.bytes, 0, bytes.length)
    return bytes
  }

  @Throws(IOException::class)
  private fun seekDir(input: IndexInput) {
    input.seek(input.length() - CodecUtil.footerLength() - 8)
    val dirOffset = input.readLong()
    input.seek(dirOffset)
  }

  override fun close() {
    try {
      IOUtils.close(`in`, postingsReader)
    } finally {
      fields.clear()
    }
  }

  override fun iterator(): MutableIterator<String> = fields.keys.iterator()

  @Throws(IOException::class)
  override fun terms(field: String?): Terms? {
    if (field == null) return null
    return fields[field]
  }

  override fun size(): Int = fields.size

  @Throws(IOException::class)
  override fun checkIntegrity() {
    // term dictionary
    CodecUtil.checksumEntireFile(`in`)
    // postings
    postingsReader.checkIntegrity()
  }

  override fun toString(): String =
    "${this::class.simpleName}(fields=${fields.size},delegate=$postingsReader)"
}
