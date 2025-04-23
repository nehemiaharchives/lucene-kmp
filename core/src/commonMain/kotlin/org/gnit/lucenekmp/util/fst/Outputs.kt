package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import kotlinx.io.IOException

/**
 * Represents the outputs for an FST, providing the basic algebra required for building and
 * traversing the FST.
 *
 *
 * Note that any operation that returns NO_OUTPUT must return the same singleton object from
 * [.getNoOutput].
 *
 * @lucene.experimental
 */
abstract class Outputs<T> {
    // TODO: maybe change this API to allow for re-use of the
    // output instances -- this is an insane amount of garbage
    // (new object per byte/char/int) if eg used during
    // analysis
    /** Eg common("foobar", "food") -&gt; "foo"  */
    abstract fun common(output1: T, output2: T): T

    /** Eg subtract("foobar", "foo") -&gt; "bar"  */
    abstract fun subtract(output: T, inc: T): T

    /** Eg add("foo", "bar") -&gt; "foobar"  */
    abstract fun add(prefix: T, output: T): T

    /** Encode an output value into a [DataOutput].  */
    @Throws(IOException::class)
    abstract fun write(output: T, out: DataOutput)

    /**
     * Encode an final node output value into a [DataOutput]. By default this just calls [ ][.write].
     */
    @Throws(IOException::class)
    fun writeFinalOutput(output: T, out: DataOutput) {
        write(output, out)
    }

    /** Decode an output value previously written with [.write].  */
    @Throws(IOException::class)
    abstract fun read(`in`: DataInput): T

    /** Skip the output; defaults to just calling [.read] and discarding the result.  */
    @Throws(IOException::class)
    open fun skipOutput(`in`: DataInput) {
        read(`in`)
    }

    /**
     * Decode an output value previously written with [.writeFinalOutput].
     * By default this just calls [.read].
     */
    @Throws(IOException::class)
    fun readFinalOutput(`in`: DataInput): T {
        return read(`in`)
    }

    /**
     * Skip the output previously written with [.writeFinalOutput]; defaults to just calling
     * [.readFinalOutput] and discarding the result.
     */
    @Throws(IOException::class)
    fun skipFinalOutput(`in`: DataInput) {
        skipOutput(`in`)
    }

    /**
     * NOTE: this output is compared with == so you must ensure that all methods return the single
     * object if it's really no output
     */
    abstract val noOutput: T

    abstract fun outputToString(output: T): String

    // TODO: maybe make valid(T output) public...  for asserts
    fun merge(first: T, second: T): T {
        throw UnsupportedOperationException()
    }

    /**
     * Return memory usage for the provided output.
     *
     * @see Accountable
     */
    abstract fun ramBytesUsed(output: T): Long
}
