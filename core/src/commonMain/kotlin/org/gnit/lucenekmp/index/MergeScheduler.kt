package org.gnit.lucenekmp.index


import org.gnit.lucenekmp.index.MergePolicy.OneMerge
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.SameThreadExecutorService
import okio.IOException
import org.gnit.lucenekmp.jdkport.Executor
import org.gnit.lucenekmp.jdkport.ExecutorService

/**
 * Expert: [IndexWriter] uses an instance implementing this interface to execute the merges
 * selected by a [MergePolicy]. The default MergeScheduler is [ ].
 *
 * @lucene.experimental
 */
abstract class MergeScheduler
/** Sole constructor. (For invocation by subclass constructors, typically implicit.)  */
protected constructor() : AutoCloseable {
    private val executor: ExecutorService = SameThreadExecutorService()

    /**
     * Run the merges provided by [MergeSource.getNextMerge].
     *
     * @param mergeSource the [IndexWriter] to obtain the merges from.
     * @param trigger the [MergeTrigger] that caused this merge to happen
     */
    @Throws(IOException::class)
    abstract fun merge(mergeSource: MergeSource, trigger: MergeTrigger)

    /**
     * Wraps the incoming [Directory] so that we can merge-throttle it using [ ].
     */
    fun wrapForMerge(
        merge: OneMerge,
        `in`: Directory
    ): Directory {
        // A no-op by default.
        return `in`
    }

    /**
     * Provides an executor for parallelism during a single merge operation. By default, the method
     * returns a [SameThreadExecutorService] where all intra-merge actions occur in their
     * calling thread.
     */
    fun getIntraMergeExecutor(merge: OneMerge): Executor {
        return executor
    }

    /** Close this MergeScheduler.  */
    override fun close() {
        executor.shutdown()
    }

    /** For messages about merge scheduling  */
    protected var infoStream: InfoStream? = null

    /** IndexWriter calls this on init.  */
    @Throws(IOException::class)
    fun initialize(infoStream: InfoStream, directory: Directory) {
        this.infoStream = infoStream
    }

    /**
     * Returns true if infoStream messages are enabled. This method is usually used in conjunction
     * with [.message]:
     *
     * <pre class="prettyprint">
     * if (verbose()) {
     * message(&quot;your message&quot;);
     * }
    </pre> *
     */
    protected fun verbose(): Boolean {
        return infoStream != null && infoStream!!.isEnabled("MS")
    }

    /**
     * Outputs the given message - this method assumes [.verbose] was called and returned
     * true.
     */
    protected fun message(message: String) {
        infoStream!!.message("MS", message)
    }

    /**
     * Provides access to new merges and executes the actual merge
     *
     * @lucene.experimental
     */
    interface MergeSource {
        /**
         * The [MergeScheduler] calls this method to retrieve the next merge requested by the
         * MergePolicy
         */
        val nextMerge: OneMerge

        /** Does finishing for a merge.  */
        fun onMergeFinished(merge: OneMerge)

        /** Expert: returns true if there are merges waiting to be scheduled.  */
        fun hasPendingMerges(): Boolean

        /** Merges the indicated segments, replacing them in the stack with a single segment.  */
        @Throws(IOException::class)
        fun merge(merge: OneMerge)
    }
}
