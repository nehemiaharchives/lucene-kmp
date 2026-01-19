package org.gnit.lucenekmp.internal.tests

//import org.gnit.lucenekmp.index.ConcurrentMergeScheduler
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.SegmentReader
//import org.gnit.lucenekmp.internal.tests.ConcurrentMergeSchedulerAccess
//import org.gnit.lucenekmp.internal.tests.FilterIndexInputAccess
import org.gnit.lucenekmp.internal.tests.IndexPackageAccess
import org.gnit.lucenekmp.internal.tests.IndexWriterAccess
//import org.gnit.lucenekmp.store.FilterIndexInput

/**
 * A set of static methods returning accessors for internal, package-private functionality in
 * Lucene. All getters may only be called by the Lucene Test Framework module. Setters are
 * initialized once on startup.
 */
object TestSecrets {
    /*init {
        val ensureInitialized: java.util.function.Consumer<java.lang.Class<*>> =
            java.util.function.Consumer { clazz: java.lang.Class<*> ->
                try {
                    // A no-op forName here has a side-effect of ensuring the class is loaded and
                    // initialized.
                    // This only happens once. We could just leverage the JLS and invoke a static
                    // method (or a constructor) on the target class but the method below seems simpler.
                    // TODO: In Java 15 there's MethodHandles.lookup().ensureInitialized(clazz)
                    java.lang.Class.forName(clazz.getName())
                } catch (e: java.lang.ClassNotFoundException) {
                    throw java.lang.RuntimeException(e)
                }
            }

        ensureInitialized.accept(ConcurrentMergeScheduler::class)
        ensureInitialized.accept(SegmentReader::class)
        ensureInitialized.accept(IndexWriter::class)
        ensureInitialized.accept(FilterIndexInput::class)
    }*/

    private var indexPackageAccess: IndexPackageAccess? = null

    private var cmsAccess: ConcurrentMergeSchedulerAccess? = null

    private var segmentReaderAccess: SegmentReaderAccess? = null

    private var indexWriterAccess: IndexWriterAccess? = null

    //private var filterIndexInputAccess: FilterIndexInputAccess? = null

    /** Return the accessor to internal secrets for an [IndexReader].  */
    fun getIndexPackageAccess(): IndexPackageAccess {

        // ensureCaller() // TODO implement if needed
        ensureIndexWriterAccessInitialized()
        return requireNotNull(indexPackageAccess)
    }

    /*val concurrentMergeSchedulerAccess: ConcurrentMergeSchedulerAccess
        *//** Return the accessor to internal secrets for an [ConcurrentMergeScheduler].  *//*
        get() {
            ensureCaller()
            return requireNotNull<ConcurrentMergeSchedulerAccess>(cmsAccess)
        }*/

    /** Return the accessor to internal secrets for an [SegmentReader].  */
    /*fun getSegmentReaderAccess(): SegmentReaderAccess {
        ensureCaller()
        return requireNotNull<SegmentReaderAccess>(
            segmentReaderAccess
        )
    }*/

    /** Return the accessor to internal secrets for an [IndexWriter].  */
    fun getIndexWriterAccess(): IndexWriterAccess {

        // ensureCaller() // TODO implement if needed
        ensureIndexWriterAccessInitialized()
        return requireNotNull<IndexWriterAccess>(indexWriterAccess)
    }

    /*val filterInputIndexAccess: FilterIndexInputAccess
        *//** Return the accessor to internal secrets for an [FilterIndexInput].  *//*
        get() {
            ensureCaller()
            return requireNotNull<FilterIndexInputAccess>(filterIndexInputAccess)
        }*/

    /** For internal initialization only.  */
    fun setIndexWriterAccess(indexWriterAccess: IndexWriterAccess) {
        ensureNull(TestSecrets.indexWriterAccess)
        TestSecrets.indexWriterAccess = indexWriterAccess
    }

    /** For internal initialization only.  */
    fun setIndexPackageAccess(indexPackageAccess: IndexPackageAccess) {
        ensureNull(TestSecrets.indexPackageAccess)
        TestSecrets.indexPackageAccess = indexPackageAccess
    }

    /** For internal initialization only.  */
    fun setConcurrentMergeSchedulerAccess(cmsAccess: ConcurrentMergeSchedulerAccess) {
        ensureNull(TestSecrets.cmsAccess)
        TestSecrets.cmsAccess = cmsAccess
    }

    /** For internal initialization only.  */
    fun setSegmentReaderAccess(segmentReaderAccess: SegmentReaderAccess) {
        ensureNull(TestSecrets.segmentReaderAccess as Any?)
        TestSecrets.segmentReaderAccess = segmentReaderAccess
    }

    /** For internal initialization only.  */
    /*fun setFilterInputIndexAccess(filterIndexInputAccess: FilterIndexInputAccess) {
        ensureNull(TestSecrets.filterIndexInputAccess)
        TestSecrets.filterIndexInputAccess = filterIndexInputAccess
    }*/

    private fun ensureNull(ob: Any?) {
        if (ob != null) {
            throw AssertionError(
                "The accessor is already set. It can only be called from inside Lucene Core."
            )
        }
    }

    private fun ensureIndexWriterAccessInitialized() {
        if (indexWriterAccess != null && indexPackageAccess != null) {
            return
        }
        // Touch IndexWriter's companion object to trigger its init block.
        @Suppress("UNUSED_VARIABLE")
        val unused = IndexWriter.actualMaxDocs
    }

    /*private fun ensureCaller() {
        val validCaller: Boolean =
            java.lang.StackWalker.getInstance()
                .walk<Boolean>(
                    java.util.function.Function { s: java.util.stream.Stream<java.lang.StackWalker.StackFrame> ->
                        s.skip(2)
                            .limit(1)
                            .map<String> { obj: java.lang.StackWalker.StackFrame -> obj.getClassName() }
                            .allMatch { c: String -> c!!.startsWith("org.apache.lucene.tests.") }
                    })
        if (!validCaller) {
            throw UnsupportedOperationException(
                "Lucene TestSecrets can only be used by the test-framework."
            )
        }
    }*/
}
