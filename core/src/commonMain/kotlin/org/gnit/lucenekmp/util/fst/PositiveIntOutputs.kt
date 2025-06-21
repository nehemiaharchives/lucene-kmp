package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.RamUsageEstimator

/**
 * An FST [Outputs] implementation where each output is a non-negative long value.
 */
class PositiveIntOutputs private constructor() : Outputs<Long>() {
    override fun common(output1: Long, output2: Long): Long {
        check(valid(output1))
        check(valid(output2))
        return if (output1 == NO_OUTPUT || output2 == NO_OUTPUT) {
            NO_OUTPUT
        } else {
            kotlin.math.min(output1, output2)
        }
    }

    override fun subtract(output: Long, inc: Long): Long {
        check(valid(output))
        check(valid(inc))
        check(output >= inc)
        return if (inc == NO_OUTPUT) {
            output
        } else if (output == inc) {
            NO_OUTPUT
        } else {
            output - inc
        }
    }

    override fun add(prefix: Long, output: Long): Long {
        check(valid(prefix))
        check(valid(output))
        return when {
            prefix == NO_OUTPUT -> output
            output == NO_OUTPUT -> prefix
            else -> prefix + output
        }
    }

    override fun write(output: Long, out: DataOutput) {
        check(valid(output))
        out.writeVLong(output)
    }

    override fun read(input: DataInput): Long {
        val v = input.readVLong()
        return if (v == 0L) NO_OUTPUT else v
    }

    private fun valid(o: Long): Boolean {
        require(o == NO_OUTPUT || o > 0L) { "o=$o" }
        return true
    }

    override val noOutput: Long
        get() = NO_OUTPUT

    override fun outputToString(output: Long): String = output.toString()

    override fun toString(): String = "PositiveIntOutputs"

    override fun ramBytesUsed(output: Long): Long = RamUsageEstimator.sizeOf(output)

    companion object {
        private const val NO_OUTPUT: Long = 0L
        val singleton: PositiveIntOutputs = PositiveIntOutputs()
    }
}
