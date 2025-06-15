package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput

/**
 * A null FST [Outputs] implementation; use this if you just want to build an FSA.
 */
class NoOutputs private constructor() : Outputs<Any>() {
    override fun common(output1: Any, output2: Any): Any {
        require(output1 === NO_OUTPUT)
        require(output2 === NO_OUTPUT)
        return NO_OUTPUT
    }

    override fun subtract(output: Any, inc: Any): Any {
        require(output === NO_OUTPUT)
        require(inc === NO_OUTPUT)
        return NO_OUTPUT
    }

    override fun add(prefix: Any, output: Any): Any {
        require(prefix === NO_OUTPUT) { "got $prefix" }
        require(output === NO_OUTPUT)
        return NO_OUTPUT
    }


    override fun write(output: Any, out: DataOutput) {
        // no-op
    }

    override fun read(`in`: DataInput): Any {
        return NO_OUTPUT
    }

    override val noOutput: Any
        get() = NO_OUTPUT

    override fun outputToString(output: Any): String = ""

    override fun ramBytesUsed(output: Any): Long = 0L

    override fun toString(): String = "NoOutputs"

    companion object {
        val NO_OUTPUT: Any = object {
            override fun hashCode(): Int = 42
            override fun equals(other: Any?): Boolean = other === this
        }

        val singleton: NoOutputs = NoOutputs()
    }
}
