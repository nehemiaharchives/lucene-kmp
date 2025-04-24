package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.util.StringHelper
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.System

/**
 * An FST [Outputs] implementation where each output is a sequence of bytes.
 *
 * @lucene.experimental
 */
class ByteSequenceOutputs private constructor() : Outputs<BytesRef>() {
    override fun common(output1: BytesRef, output2: BytesRef): BytesRef {
        checkNotNull(output1)
        checkNotNull(output2)

        val mismatchPos: Int =
            Arrays.mismatch(
                output1.bytes,
                output1.offset,
                output1.offset + output1.length,
                output2.bytes,
                output2.offset,
                output2.offset + output2.length
            )

        when (mismatchPos) {
            0 -> {
                // no common prefix
                return NO_OUTPUT
            }
            -1 -> {
                // exactly equals
                return output1
            }
            output1.length -> {
                // output1 is a prefix of output2
                return output1
            }
            output2.length -> {
                // output2 is a prefix of output1
                return output2
            }
            else -> {
                return BytesRef(output1.bytes, output1.offset, mismatchPos)
            }
        }
    }

    override fun subtract(output: BytesRef, inc: BytesRef): BytesRef {
        checkNotNull(output)
        checkNotNull(inc)
        if (inc === NO_OUTPUT) {
            // no prefix removed
            return output
        } else {
            require(StringHelper.startsWith(output, inc))
            if (inc.length == output.length) {
                // entire output removed
                return NO_OUTPUT
            } else {
                require(
                    inc.length < output.length
                ) { "inc.length=" + inc.length + " vs output.length=" + output.length }
                require(inc.length > 0)
                return BytesRef(output.bytes, output.offset + inc.length, output.length - inc.length)
            }
        }
    }

    override fun add(prefix: BytesRef, output: BytesRef): BytesRef {
        checkNotNull(prefix)
        checkNotNull(output)
        if (prefix === NO_OUTPUT) {
            return output
        } else if (output === NO_OUTPUT) {
            return prefix
        } else {
            require(prefix.length > 0)
            require(output.length > 0)
            val result = BytesRef(prefix.length + output.length)
            System.arraycopy(prefix.bytes, prefix.offset, result.bytes, 0, prefix.length)
            System.arraycopy(output.bytes, output.offset, result.bytes, prefix.length, output.length)
            result.length = prefix.length + output.length
            return result
        }
    }

    @Throws(IOException::class)
    override fun write(prefix: BytesRef, out: DataOutput) {
        checkNotNull(prefix)
        out.writeVInt(prefix.length)
        out.writeBytes(prefix.bytes, prefix.offset, prefix.length)
    }

    @Throws(IOException::class)
    override fun read(`in`: DataInput): BytesRef {
        val len: Int = `in`.readVInt()
        if (len == 0) {
            return NO_OUTPUT
        } else {
            val output = BytesRef(len)
            `in`.readBytes(output.bytes, 0, len)
            output.length = len
            return output
        }
    }

    @Throws(IOException::class)
    override fun skipOutput(`in`: DataInput) {
        val len: Int = `in`.readVInt()
        if (len != 0) {
            `in`.skipBytes(len.toLong())
        }
    }

    override val noOutput: BytesRef
        get() = NO_OUTPUT

    override fun outputToString(output: BytesRef): String {
        return output.toString()
    }

    override fun ramBytesUsed(output: BytesRef): Long {
        return BASE_NUM_BYTES + RamUsageEstimator.sizeOf(output.bytes)
    }

    override fun toString(): String {
        return "ByteSequenceOutputs"
    }

    companion object {
        private val NO_OUTPUT: BytesRef = BytesRef()
        val singleton: ByteSequenceOutputs = ByteSequenceOutputs()

        fun getSingleton(): ByteSequenceOutputs {
            return singleton
        }

        private val BASE_NUM_BYTES: Long = RamUsageEstimator.shallowSizeOf(NO_OUTPUT)
    }
}
