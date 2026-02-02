package org.gnit.lucenekmp.codecs.blocktreeords

import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.StringHelper
import org.gnit.lucenekmp.util.fst.Outputs
import okio.IOException

/**
 * A custom FST outputs implementation that stores block data (BytesRef), long ordStart, long
 * numTerms.
 */
/**
 * Faithful port of Lucene's {@code FSTOrdsOutputs} from Java → Kotlin.
 *
 * This implementation mirrors the upstream serialization and semantics so
 * `Ords*` readers/writers and FST helpers behave the same as the Java code.
 */
internal class FSTOrdsOutputs : Outputs<FSTOrdsOutputs.Output>() {

  /**
   * @param startOrd Inclusive
   * @param endOrd Inclusive
   */
  class Output(private val _bytes: BytesRef, private val _startOrd: Long, private val _endOrd: Long) {
    // Provide Java-style accessors (the ported code calls .bytes(), .startOrd(), .endOrd())
    fun bytes(): BytesRef = _bytes
    fun startOrd(): Long = _startOrd
    fun endOrd(): Long = _endOrd

    override fun toString(): String {
      val x: Long = if (_endOrd > Long.MAX_VALUE / 2) {
        Long.MAX_VALUE - _endOrd
      } else {
        -_endOrd
      }
      return "$_startOrd to $x"
    }
  }

  companion object {
    private val NO_BYTES: BytesRef = BytesRef()
    val NO_OUTPUT: Output = Output(NO_BYTES, 0L, 0L)
  }

  override fun common(output1: Output, output2: Output): Output {
    val bytes1 = output1.bytes()
    val bytes2 = output2.bytes()

    var pos1 = bytes1.offset
    var pos2 = bytes2.offset
    val stopAt1 = pos1 + kotlin.math.min(bytes1.length, bytes2.length)
    while (pos1 < stopAt1) {
      if (bytes1.bytes[pos1] != bytes2.bytes[pos2]) break
      pos1++
      pos2++
    }

    val prefixBytes: BytesRef = when {
      pos1 == bytes1.offset -> NO_BYTES
      pos1 == bytes1.offset + bytes1.length -> bytes1
      pos2 == bytes2.offset + bytes2.length -> bytes2
      else -> BytesRef(bytes1.bytes, bytes1.offset, pos1 - bytes1.offset)
    }

    return newOutput(
      prefixBytes,
      kotlin.math.min(output1.startOrd(), output2.startOrd()),
      kotlin.math.min(output1.endOrd(), output2.endOrd())
    )
  }

  override fun subtract(output: Output, inc: Output): Output {
    // `output` and `inc` are non-null by signature; no runtime check required
    if (inc === NO_OUTPUT) {
      return output
    } else {
      require(StringHelper.startsWith(output.bytes(), inc.bytes()))
      val suffix: BytesRef = when {
        inc.bytes().length == output.bytes().length -> NO_BYTES
        inc.bytes().length == 0 -> output.bytes()
        else -> {
          require(inc.bytes().length < output.bytes().length) { "inc.length=${inc.bytes().length} vs output.length=${output.bytes().length}" }
          require(inc.bytes().length > 0)
          BytesRef(output.bytes().bytes, output.bytes().offset + inc.bytes().length, output.bytes().length - inc.bytes().length)
        }
      }
      require(output.startOrd() >= inc.startOrd())
      require(output.endOrd() >= inc.endOrd())
      return newOutput(suffix, output.startOrd() - inc.startOrd(), output.endOrd() - inc.endOrd())
    }
  }

  /**
   * Accept nullable prefix/output for caller convenience; treat null as NO_OUTPUT.
   *
   * The upstream Java API uses platform types; implement the nullable override so
   * both null and non-null call sites are supported in Kotlin/common code.
   */
  override fun add(prefix: Output, output: Output): Output {
    if (prefix === NO_OUTPUT) return output
    if (output === NO_OUTPUT) return prefix

    val bytes = BytesRef(prefix.bytes().length + output.bytes().length)
    prefix.bytes().bytes.copyInto(bytes.bytes, 0, prefix.bytes().offset, prefix.bytes().offset + prefix.bytes().length)
    output.bytes().bytes.copyInto(bytes.bytes, prefix.bytes().length, output.bytes().offset, output.bytes().offset + output.bytes().length)
    bytes.length = prefix.bytes().length + output.bytes().length
    return newOutput(bytes, prefix.startOrd() + output.startOrd(), prefix.endOrd() + output.endOrd())
  }

  @Throws(IOException::class)
  override fun write(prefix: Output, output: DataOutput) {
    val br = prefix.bytes()
    output.writeVInt(br.length)
    output.writeBytes(br.bytes, br.offset, br.length)
    output.writeVLong(prefix.startOrd())
    output.writeVLong(prefix.endOrd())
  }

  @Throws(IOException::class)
  override fun read(`in`: DataInput): Output {
    val len = `in`.readVInt()
    val bytes: BytesRef = if (len == 0) {
      NO_BYTES
    } else {
      val b = BytesRef(len)
      `in`.readBytes(b.bytes, 0, len)
      b.length = len
      b
    }

    val startOrd = `in`.readVLong()
    val endOrd = `in`.readVLong()

    return newOutput(bytes, startOrd, endOrd)
  }

  @Throws(IOException::class)
  override fun skipOutput(`in`: DataInput) {
    val len = `in`.readVInt()
    if (len != 0) `in`.skipBytes(len.toLong())
    `in`.readVLong()
    `in`.readVLong()
  }

  @Throws(IOException::class)
  override fun skipFinalOutput(`in`: DataInput) {
    skipOutput(`in`)
  }

  override val noOutput: Output
    get() = NO_OUTPUT

  override fun outputToString(output: Output): String {
    return if ((output.endOrd() == 0L || output.endOrd() == Long.MAX_VALUE) && output.startOrd() == 0L) {
      ""
    } else {
      output.toString()
    }
  }

  fun newOutput(bytes: BytesRef, startOrd: Long, endOrd: Long): Output {
    return if (bytes.length == 0 && startOrd == 0L && endOrd == 0L) NO_OUTPUT else Output(bytes, startOrd, endOrd)
  }

  override fun ramBytesUsed(output: Output): Long {
    return 2 * RamUsageEstimator.NUM_BYTES_OBJECT_HEADER + 2 * Long.SIZE_BYTES / 8 + 2 * RamUsageEstimator.NUM_BYTES_OBJECT_REF + RamUsageEstimator.NUM_BYTES_ARRAY_HEADER + 2 * Int.SIZE_BYTES / 8 + output.bytes().length.toLong()
  }

  override fun toString(): String = "FSTOrdsOutputs"
}
