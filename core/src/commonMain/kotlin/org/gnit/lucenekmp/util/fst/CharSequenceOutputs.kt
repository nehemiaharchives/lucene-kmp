package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.CharsRef
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.System
import okio.IOException

/**
 * An FST [Outputs] implementation where each output is a sequence of characters.
 */
class CharSequenceOutputs private constructor() : Outputs<CharsRef>() {
    override fun common(output1: CharsRef, output2: CharsRef): CharsRef {
        checkNotNull(output1)
        checkNotNull(output2)
        val mismatchPos = Arrays.mismatch(
            output1.chars, output1.offset, output1.offset + output1.length,
            output2.chars, output2.offset, output2.offset + output2.length
        )
        return when (mismatchPos) {
            0 -> NO_OUTPUT
            -1 -> output1
            output1.length -> output1
            output2.length -> output2
            else -> CharsRef(output1.chars, output1.offset, mismatchPos)
        }
    }

    override fun subtract(output: CharsRef, inc: CharsRef): CharsRef {
        checkNotNull(output)
        checkNotNull(inc)
        return if (inc === NO_OUTPUT) {
            output
        } else if (inc.length == output.length) {
            NO_OUTPUT
        } else {
            require(inc.length < output.length) { "inc.length=" + inc.length + " vs output.length=" + output.length }
            require(inc.length > 0)
            CharsRef(output.chars, output.offset + inc.length, output.length - inc.length)
        }
    }

    override fun add(prefix: CharsRef, output: CharsRef): CharsRef {
        checkNotNull(prefix)
        checkNotNull(output)
        return when {
            prefix === NO_OUTPUT -> output
            output === NO_OUTPUT -> prefix
            else -> {
                require(prefix.length > 0)
                require(output.length > 0)
                val result = CharsRef(prefix.length + output.length)
                System.arraycopy(prefix.chars, prefix.offset, result.chars, 0, prefix.length)
                System.arraycopy(output.chars, output.offset, result.chars, prefix.length, output.length)
                result.lengthMutable = prefix.length + output.length
                result
            }
        }
    }

    @Throws(IOException::class)
    override fun write(prefix: CharsRef, out: DataOutput) {
        checkNotNull(prefix)
        out.writeVInt(prefix.length)
        for (i in 0 until prefix.length) {
            out.writeVInt(prefix.chars[prefix.offset + i].code)
        }
    }

    @Throws(IOException::class)
    override fun read(`in`: DataInput): CharsRef {
        val len = `in`.readVInt()
        return if (len == 0) {
            NO_OUTPUT
        } else {
            val output = CharsRef(len)
            for (i in 0 until len) {
                output.chars[i] = `in`.readVInt().toChar()
            }
            output.lengthMutable = len
            output
        }
    }

    @Throws(IOException::class)
    override fun skipOutput(`in`: DataInput) {
        val len = `in`.readVInt()
        for (i in 0 until len) {
            `in`.readVInt()
        }
    }

    override val noOutput: CharsRef
        get() = NO_OUTPUT

    override fun outputToString(output: CharsRef): String = output.toString()

    override fun ramBytesUsed(output: CharsRef): Long {
        return BASE_NUM_BYTES + RamUsageEstimator.sizeOf(output.chars)
    }

    override fun toString(): String = "CharSequenceOutputs"

    companion object {
        private val NO_OUTPUT = CharsRef()
        val singleton = CharSequenceOutputs()
        private val BASE_NUM_BYTES: Long = RamUsageEstimator.shallowSizeOf(NO_OUTPUT)
    }
}
