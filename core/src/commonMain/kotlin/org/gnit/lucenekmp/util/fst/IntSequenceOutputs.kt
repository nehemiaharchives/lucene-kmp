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
        val len1 = output1.length
        val len2 = output2.length
        val limit = min(len1, len2)
        var idx = 0
        while (idx < limit &&
            output1.ints[output1.offset + idx] == output2.ints[output2.offset + idx]) {
            idx++
        }

        return when {
            idx == 0 -> NO_OUTPUT
            idx == len1 && idx == len2 -> output1
            idx == len1 -> output1
            idx == len2 -> output2
            else -> IntsRef(output1.ints, output1.offset, idx)
        }
    }

    override fun subtract(output: IntsRef, inc: IntsRef): IntsRef {
        if (inc === NO_OUTPUT) return output
        if (inc.length == output.length) return NO_OUTPUT
        require(inc.length < output.length) { "inc.length=${inc.length} vs output.length=${output.length}" }
        require(inc.length > 0)
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
        if (len == 0) {
            return
        }
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
        private val BASE_NUM_BYTES = RamUsageEstimator.shallowSizeOf(NO_OUTPUT)
        val singleton: IntSequenceOutputs = IntSequenceOutputs()
    }
}

