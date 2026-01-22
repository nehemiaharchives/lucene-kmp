package org.gnit.lucenekmp.store

import okio.IOException
import org.gnit.lucenekmp.internal.tests.TestSecrets
import kotlin.reflect.KClass


/**
 * IndexInput implementation that delegates calls to another IndexInput. This class can be used to
 * add limitations on top of an existing [IndexInput] implementation or to add additional
 * sanity checks for tests. However, if you plan to write your own [IndexInput]
 * implementation, you should consider extending directly [IndexInput] or [DataInput]
 * rather than try to reuse functionality of existing [IndexInput]s by extending this class.
 *
 * @lucene.internal
 */
class FilterIndexInput(resourceDescription: String, `in`: IndexInput) :
    IndexInput(resourceDescription) {
    protected val `in`: IndexInput

    /** Creates a FilterIndexInput with a resource description and wrapped delegate IndexInput  */
    init {
        this.`in` = `in`
    }

    val delegate: IndexInput
        /** Gets the delegate that was passed in on creation  */
        get() = `in`

    @Throws(IOException::class)
    override fun close() {
        `in`.close()
    }

    override val filePointer: Long
        get() = `in`.filePointer

    @Throws(IOException::class)
    override fun seek(pos: Long) {
        `in`.seek(pos)
    }

    override fun length(): Long {
        return `in`.length()
    }

    @Throws(IOException::class)
    override fun slice(
        sliceDescription: String,
        offset: Long,
        length: Long
    ): IndexInput {
        return `in`.slice(sliceDescription, offset, length)
    }

    @Throws(IOException::class)
    override fun readByte(): Byte {
        return `in`.readByte()
    }

    @Throws(IOException::class)
    override fun readBytes(b: ByteArray, offset: Int, len: Int) {
        `in`.readBytes(b, offset, len)
    }

    companion object {
        val TEST_FILTER_INPUTS: /*java.util.concurrent.CopyOnWrite*/ArrayList<KClass<*>> =
            /*java.util.concurrent.CopyOnWrite*/ArrayList<KClass<*>>()

        init {
            TestSecrets.setFilterInputIndexAccess { cls ->
                TEST_FILTER_INPUTS.add(cls)
            }
        }

        /**
         * Unwraps all FilterIndexInputs until the first non-FilterIndexInput IndexInput instance and
         * returns it
         */
        fun unwrap(`in`: IndexInput): IndexInput {
            var `in`: IndexInput = `in`
            while (`in` is FilterIndexInput) {
                `in` = `in`.`in`
            }
            return `in`
        }

        /**
         * Unwraps all test FilterIndexInputs until the first non-test FilterIndexInput IndexInput
         * instance and returns it
         */
        fun unwrapOnlyTest(`in`: IndexInput): IndexInput {
            var `in`: IndexInput = `in`
            while (`in` is FilterIndexInput && TEST_FILTER_INPUTS.contains(`in`::class)) {
                `in` = `in`.`in`
            }
            return `in`
        }
    }
}
