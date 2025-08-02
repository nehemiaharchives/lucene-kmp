package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.codecs.Codec

/** Minimal version of Lucene's IndexWriterConfig.
 *  Only the parts required by tests are implemented.
 */
open class IndexWriterConfig {
    var codec: Codec = Codec.default

    open var mergePolicy: MergePolicy? = null

    fun setCodec(codec: Codec): IndexWriterConfig {
        this.codec = codec
        return this
    }

    fun setMergePolicy(mergePolicy: MergePolicy): IndexWriterConfig {
        this.mergePolicy = mergePolicy
        return this
    }

    fun getRAMPerThreadHardLimitMB(): Int {
        throw UnsupportedOperationException("Not implemented yet")
    }

    companion object{

        const val DISABLE_AUTO_FLUSH: Int = -1
    }


}
