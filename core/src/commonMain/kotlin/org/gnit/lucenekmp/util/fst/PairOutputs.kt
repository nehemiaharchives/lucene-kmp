package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.RamUsageEstimator

/**
 * An [Outputs] implementation that pairs two other outputs.
 */
class PairOutputs<A, B>(private val outputs1: Outputs<A>, private val outputs2: Outputs<B>) :
    Outputs<PairOutputs.Pair<A, B>>() {

    private val NO_OUTPUT: Pair<A, B> = Pair(outputs1.noOutput, outputs2.noOutput)

    class Pair<A, B>(val output1: A, val output2: B) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Pair<*, *>) return false
            return output1 == other.output1 && output2 == other.output2
        }

        override fun hashCode(): Int = output1.hashCode() + output2.hashCode()

        override fun toString(): String = "Pair($output1,$output2)"
    }

    fun newPair(a: A, b: B): Pair<A, B> {
        var a2 = a
        var b2 = b
        if (a2 == outputs1.noOutput) a2 = outputs1.noOutput
        if (b2 == outputs2.noOutput) b2 = outputs2.noOutput
        return if (a2 == outputs1.noOutput && b2 == outputs2.noOutput) {
            NO_OUTPUT
        } else Pair(a2, b2)
    }

    override fun common(output1: Pair<A, B>, output2: Pair<A, B>): Pair<A, B> {
        return newPair(
            outputs1.common(output1.output1, output2.output1),
            outputs2.common(output1.output2, output2.output2)
        )
    }

    override fun subtract(output: Pair<A, B>, inc: Pair<A, B>): Pair<A, B> {
        return newPair(
            outputs1.subtract(output.output1, inc.output1),
            outputs2.subtract(output.output2, inc.output2)
        )
    }

    override fun add(prefix: Pair<A, B>, output: Pair<A, B>): Pair<A, B> {
        return newPair(
            outputs1.add(prefix.output1, output.output1),
            outputs2.add(prefix.output2, output.output2)
        )
    }

    override fun write(output: Pair<A, B>, out: DataOutput) {
        outputs1.write(output.output1, out)
        outputs2.write(output.output2, out)
    }

    override fun read(input: DataInput): Pair<A, B> {
        val a = outputs1.read(input)
        val b = outputs2.read(input)
        return newPair(a, b)
    }

    override fun skipOutput(input: DataInput) {
        outputs1.skipOutput(input)
        outputs2.skipOutput(input)
    }

    override val noOutput: Pair<A, B>
        get() = NO_OUTPUT

    override fun outputToString(output: Pair<A, B>): String {
        return "<pair:" + outputs1.outputToString(output.output1) + "," + outputs2.outputToString(output.output2) + ">"
    }

    override fun toString(): String = "PairOutputs<$outputs1,$outputs2>"

    private val BASE_NUM_BYTES = RamUsageEstimator.shallowSizeOf(Pair(null, null))

    override fun ramBytesUsed(output: Pair<A, B>): Long {
        var bytes = BASE_NUM_BYTES
        if (output.output1 != null) bytes += outputs1.ramBytesUsed(output.output1)
        if (output.output2 != null) bytes += outputs2.ramBytesUsed(output.output2)
        return bytes
    }
}

