package org.gnit.lucenekmp.codecs.lucene90

import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.codecs.LiveDocsFormat
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.SegmentCommitInfo
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.FixedBitSet
import kotlinx.io.IOException
import kotlin.math.min

/**
 * Lucene 9.0 live docs format
 *
 *
 * The .liv file is optional, and only exists when a segment contains deletions.
 *
 *
 * Although per-segment, this file is maintained exterior to compound segment files.
 *
 *
 * Deletions (.liv) --&gt; IndexHeader,Generation,Bits
 *
 *
 *  * SegmentHeader --&gt; [IndexHeader][CodecUtil.writeIndexHeader]
 *  * Bits --&gt; &lt;[Int64][DataOutput.writeLong]&gt; <sup>LongCount</sup>
 *
 */
class Lucene90LiveDocsFormat
/** Sole constructor.  */
    : LiveDocsFormat() {
    @Throws(IOException::class)
    override fun readLiveDocs(dir: Directory, info: SegmentCommitInfo, context: IOContext): Bits {
        val gen: Long = info.delGen
        val name: String = IndexFileNames.fileNameFromGeneration(info.info.name, EXTENSION, gen)!!
        val length: Int = info.info.maxDoc()
        dir.openChecksumInput(name).use { input ->
            var priorE: Throwable? = null
            try {
                CodecUtil.checkIndexHeader(
                    input,
                    CODEC_NAME,
                    VERSION_START,
                    VERSION_CURRENT,
                    info.info.getId(),
                    gen.toString(Character.MAX_RADIX.coerceIn(2, 36))
                )

                val fbs: FixedBitSet = readFixedBitSet(input, length)

                if (fbs.length() - fbs.cardinality() != info.getDelCount()) {
                    throw CorruptIndexException(
                        ("bits.deleted="
                                + (fbs.length() - fbs.cardinality())
                                + " info.delcount="
                                + info.getDelCount()),
                        input
                    )
                }
                return fbs.asReadOnlyBits()
            } catch (exception: Throwable) {
                priorE = exception
            } finally {
                CodecUtil.checkFooter(input, priorE)
            }
        }
        throw AssertionError()
    }

    @Throws(IOException::class)
    private fun readFixedBitSet(input: IndexInput, length: Int): FixedBitSet {
        val data = LongArray(FixedBitSet.bits2words(length))
        input.readLongs(data, 0, data.size)
        return FixedBitSet(data, length)
    }

    @Throws(IOException::class)
    override fun writeLiveDocs(
        bits: Bits, dir: Directory, info: SegmentCommitInfo, newDelCount: Int, context: IOContext
    ) {
        val gen: Long = info.nextDelGen
        val name: String? = IndexFileNames.fileNameFromGeneration(info.info.name, EXTENSION, gen)
        val delCount: Int
        dir.createOutput(name!!, context).use { output ->
            CodecUtil.writeIndexHeader(
                output,
                CODEC_NAME,
                VERSION_CURRENT,
                info.info.getId(),
                gen.toString(Character.MAX_RADIX.coerceIn(2, 36))
            )
            delCount = writeBits(output, bits)
            CodecUtil.writeFooter(output)
        }
        if (delCount != info.getDelCount() + newDelCount) {
            throw CorruptIndexException(
                ("bits.deleted="
                        + delCount
                        + " info.delcount="
                        + info.getDelCount()
                        + " newdelcount="
                        + newDelCount),
                name
            )
        }
    }

    @Throws(IOException::class)
    private fun writeBits(output: IndexOutput, bits: Bits): Int {
        var delCount = 0
        val longCount: Int = FixedBitSet.bits2words(bits.length())
        for (i in 0..<longCount) {
            var currentBits: Long = 0
            var j = i shl 6
            val end: Int = min(j + 63, bits.length() - 1)
            while (j <= end) {
                if (bits.get(j)) {
                    currentBits = currentBits or (1L shl j) // mod 64
                } else {
                    delCount += 1
                }
                ++j
            }
            output.writeLong(currentBits)
        }
        return delCount
    }

    @Throws(IOException::class)
    override fun files(info: SegmentCommitInfo, files: MutableCollection<String>) {
        if (info.hasDeletions()) {
            files.add(IndexFileNames.fileNameFromGeneration(info.info.name, EXTENSION, info.delGen)!!)
        }
    }

    companion object {
        /** extension of live docs  */
        private const val EXTENSION = "liv"

        /** codec of live docs  */
        private const val CODEC_NAME = "Lucene90LiveDocs"

        /** supported version range  */
        private const val VERSION_START = 0

        private const val VERSION_CURRENT = VERSION_START
    }
}
