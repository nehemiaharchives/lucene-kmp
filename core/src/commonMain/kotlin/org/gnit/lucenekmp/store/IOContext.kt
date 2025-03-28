package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.util.Constants


/**
 * IOContext holds additional details on the merge/search context. An IOContext object can never be
 * passed as a `null` parameter to either [ ][org.apache.lucene.store.Directory.openInput] or [ ][org.apache.lucene.store.Directory.createOutput]
 *
 * @param context An object of a enumerator Context type
 * @param mergeInfo must be given when `context == MERGE`
 * @param flushInfo must be given when `context == FLUSH`
 * @param readAdvice Advice regarding the read access pattern
 */
class IOContext(context: Context, mergeInfo: MergeInfo?, flushInfo: FlushInfo?, val readAdvice: ReadAdvice) {
    /** Context is an enumerator which specifies the context in which the Directory is being used.  */
    enum class Context {
        /** Context for reads and writes that are associated with a merge.  */
        MERGE,

        /** Context for writes that are associated with a segment flush.  */
        FLUSH,

        /** Default context, can be used for reading or writing.  */
        DEFAULT
    }

    /** Creates a default [IOContext] for reading/writing with the given [ReadAdvice]  */
    private constructor(accessAdvice: ReadAdvice) : this(Context.DEFAULT, null, null, accessAdvice)

    /** Creates an [IOContext] for flushing.  */
    constructor(flushInfo: FlushInfo?) : this(Context.FLUSH, null, flushInfo, ReadAdvice.SEQUENTIAL)

    /** Creates an [IOContext] for merging.  */
    constructor(mergeInfo: MergeInfo?) : this(Context.MERGE, mergeInfo, null, ReadAdvice.SEQUENTIAL)

    /**
     * Return an updated [IOContext] that has the provided [ReadAdvice] if the [ ] is a [Context.DEFAULT] context, otherwise return this existing instance. This
     * helps preserve a [ReadAdvice.SEQUENTIAL] advice for merging, which is always the right
     * choice, while allowing [IndexInput]s open for searching to use arbitrary [ ]s.
     */
    fun withReadAdvice(advice: ReadAdvice): IOContext {
        if (context == Context.DEFAULT) {
            return READADVICE_TO_IOCONTEXT[advice]!!
        } else {
            return this
        }
    }

    val context: Context
    val mergeInfo: MergeInfo?
    val flushInfo: FlushInfo?

    init {
        this.flushInfo = flushInfo
        this.mergeInfo = mergeInfo
        this.context = context

        when (context) {
            Context.MERGE -> requireNotNull(
                mergeInfo
            ){"mergeInfo must not be null if context is MERGE"}

            Context.FLUSH -> requireNotNull(
                flushInfo
            ){ "flushInfo must not be null if context is FLUSH" }

            Context.DEFAULT -> TODO()
        }
        require(
            !((context == Context.FLUSH || context == Context.MERGE)
                    && readAdvice !== ReadAdvice.SEQUENTIAL)
        ) { "The FLUSH and MERGE contexts must use the SEQUENTIAL read access advice" }
    }

    companion object {
        /**
         * A default context for normal reads/writes. Use [.withReadAdvice] to specify
         * another [ReadAdvice].
         *
         *
         * It will use [ReadAdvice.RANDOM] by default, unless set by system property `org.apache.lucene.store.defaultReadAdvice`.
         */
        val DEFAULT: IOContext = IOContext(Constants.DEFAULT_READADVICE)

        /**
         * A default context for reads with [ReadAdvice.SEQUENTIAL].
         *
         *
         * This context should only be used when the read operations will be performed in the same
         * thread as the thread that opens the underlying storage.
         */
        val READONCE: IOContext = IOContext(ReadAdvice.SEQUENTIAL)


        private val READADVICE_TO_IOCONTEXT: Map<ReadAdvice, IOContext> =
                ReadAdvice.entries.associateWith { IOContext(it) }
    }
}
