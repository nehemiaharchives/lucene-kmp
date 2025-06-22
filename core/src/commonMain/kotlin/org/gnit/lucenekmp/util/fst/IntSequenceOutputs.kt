package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.IntsRef
import org.gnit.lucenekmp.util.RamUsageEstimator
import org.gnit.lucenekmp.jdkport.System
import kotlin.math.min

/**
 * An [Outputs] implementation where each output is a sequence of ints.
 */
class IntSequenceOutputs private constructor() : Outputs<IntsRef>() {
    override fun common(output1: IntsRef, output2: IntsRef): IntsRef {
        if (output1 === NO_OUTPUT || output2 === NO_OUTPUT) return NO_OUTPUT
        val len = min(output1.length, output2.length)
        var pos = -1
        for (i in 0 until len) {
            if (output1.ints[output1.offset + i] != output2.ints[output2.offset + i]) {
                pos = i
                break
            }
        }
        if (pos == -1) {
            if (output1.length <= output2.length) return output1 else return output2
        }
        return if (pos == 0) NO_OUTPUT else IntsRef(output1.ints, output1.offset, pos)
    }

    override fun subtract(output: IntsRef, inc: IntsRef): IntsRef {
        if (inc === NO_OUTPUT) return output
        require(output.length >= inc.length)
        if (inc.length == output.length) return NO_OUTPUT
        return IntsRef(output.ints, output.offset + inc.length, output.length - inc.length)
    }

    override fun add(prefix: IntsRef, output: IntsRef): IntsRef {
        if (prefix === NO_OUTPUT) return output
        if (output === NO_OUTPUT) return prefix
        val result = IntArray(prefix.length + output.length)
        System.arraycopy(prefix.ints, prefix.offset, result, 0, prefix.length)
        System.arraycopy(output.ints, output.offset, result, prefix.length, output.length)
        return IntsRef(result, 0, result.size)
    }

    override fun write(output: IntsRef, out: DataOutput) {
        out.writeVInt(output.length)
        for (i in 0 until output.length) {
            out.writeVInt(output.ints[output.offset + i])
        }
    }

    override fun read(input: DataInput): IntsRef {
        val len = input.readVInt()
        return if (len == 0) {
            NO_OUTPUT
        } else {
            val ints = IntArray(len)
            for (i in 0 until len) {
                ints[i] = input.readVInt()
            }
            IntsRef(ints, 0, len)
        }
    }

    override fun skipOutput(input: DataInput) {
        val len = input.readVInt()
        for (i in 0 until len) {
            input.readVInt()
        }
    }

    override val noOutput: IntsRef
        get() = NO_OUTPUT

    override fun outputToString(output: IntsRef): String = output.toString()

    override fun toString(): String = "IntSequenceOutputs"

    override fun ramBytesUsed(output: IntsRef): Long = BASE_NUM_BYTES + RamUsageEstimator.sizeOf(output.ints)

    companion object {
        private val NO_OUTPUT = IntsRef()
        val singleton = IntSequenceOutputs()
        private val BASE_NUM_BYTES = RamUsageEstimator.shallowSizeOf(NO_OUTPUT)
    }
}

