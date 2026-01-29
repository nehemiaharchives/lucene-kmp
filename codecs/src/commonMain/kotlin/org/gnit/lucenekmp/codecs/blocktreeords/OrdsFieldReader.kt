/*
 * Port of org.apache.lucene.codecs.blocktreeords.OrdsFieldReader
 * - preserves member order and public API used by nearby classes
 * - uses kotlin/common equivalents where safe
 */

/** BlockTree's implementation of {@link org.apache.lucene.index.Terms}. */
package org.gnit.lucenekmp.codecs.blocktreeords

import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.automaton.CompiledAutomaton
import org.gnit.lucenekmp.util.fst.FST

internal class OrdsFieldReader(
  internal val parent: OrdsBlockTreeTermsReader,
  internal val fieldInfo: FieldInfo,
  internal val numTerms: Long,
  internal val rootCode: FSTOrdsOutputs.Output?,
  internal val sumTotalTermFreqValue: Long,
  internal val sumDocFreqValue: Long,
  internal val docCountValue: Int,
  internal val indexStartFP: Long,
  indexIn: IndexInput?,
  internal val minTerm: BytesRef?,
  internal val maxTerm: BytesRef?
) : Terms() {
  // computed
  internal val rootBlockFP: Long
  internal val index: FST<FSTOrdsOutputs.Output>?

  init {
    require(numTerms > 0)

    // decode rootBlockFP from rootCode
    val br = rootCode?.bytes()
    rootBlockFP = if (br != null) {
      val inBuf = ByteArrayDataInput(br.bytes, br.offset, br.length)
      inBuf.readVLong() ushr OrdsBlockTreeTermsWriter.OUTPUT_FLAGS_NUM_BITS
    } else {
      0L
    }

    // Read the on-disk FST index when available. This mirrors the upstream Java:
    //   clone = indexIn.clone(); clone.seek(indexStartFP);
    //   index = new FST<>(FST.readMetadata(clone, OrdsBlockTreeTermsWriter.FST_OUTPUTS), clone);
    // We eagerly construct the FST metadata+reader from the provided IndexInput clone.
    index = if (indexIn != null) {
      val clone = indexIn.clone()
      clone.seek(indexStartFP)
      // The Kotlin FST exposes the same metadata-reading helper as Java; use it to
      // create the on-disk FST reader.
      FST(FST.readMetadata(clone, OrdsBlockTreeTermsWriter.FST_OUTPUTS), clone)
    } else {
      null
    }
    
  }

  override val min: BytesRef?
    get() = minTerm

  override val max: BytesRef?
    get() = maxTerm

  override fun hasFreqs(): Boolean = fieldInfo.indexOptions >= IndexOptions.DOCS_AND_FREQS

  override fun hasOffsets(): Boolean =
    fieldInfo.indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS

  override fun hasPositions(): Boolean = fieldInfo.indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS

  override fun hasPayloads(): Boolean = fieldInfo.hasPayloads()

  override fun iterator(): TermsEnum = OrdsSegmentTermsEnum(this)

  override fun size(): Long = numTerms

  override val sumTotalTermFreq: Long
    get() = sumTotalTermFreqValue

  override val sumDocFreq: Long
    get() = sumDocFreqValue

  override val docCount: Int
    get() = docCountValue

  override fun intersect(compiled: CompiledAutomaton, startTerm: BytesRef?): TermsEnum {
    if (compiled.type != CompiledAutomaton.AUTOMATON_TYPE.NORMAL) {
      throw IllegalArgumentException("please use CompiledAutomaton.getTermsEnum instead")
    }
    return OrdsIntersectTermsEnum(this, compiled, startTerm)
  }

  override fun toString(): String =
    "OrdsBlockTreeTerms(terms=$numTerms,postings=$sumDocFreqValue,positions=$sumTotalTermFreqValue,docs=$docCountValue)"
}
