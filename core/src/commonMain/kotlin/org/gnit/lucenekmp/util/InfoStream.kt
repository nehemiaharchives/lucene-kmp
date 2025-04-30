package org.gnit.lucenekmp.util


/**
 * Debugging API for Lucene classes such as [IndexWriter] and [SegmentInfos].
 *
 *
 * NOTE: Enabling infostreams may cause performance degradation in some components.
 *
 * @lucene.internal
 */
abstract class InfoStream : AutoCloseable {
    private class NoOutput : InfoStream() {
        override fun message(component: String?, message: String?) {
            require(false) { "message() should not be called when isEnabled returns false" }
        }

        override fun isEnabled(component: String?): Boolean {
            return false
        }

        override fun close() {}
    }

    /** prints a message  */
    abstract fun message(component: String?, message: String?)

    /** returns true if messages are enabled and should be posted to [.message].  */
    abstract fun isEnabled(component: String?): Boolean

    companion object {
        /** Instance of InfoStream that does no logging at all.  */
        val NO_OUTPUT: InfoStream = NoOutput()

        private var defaultInfoStream = NO_OUTPUT

        /*
        TODO do something about this
        @get:Synchronized
        @set:Synchronized*/
        var default: InfoStream
            /**
             * The default `InfoStream` used by a newly instantiated classes.
             *
             * @see .setDefault
             */
            get() = defaultInfoStream
            /**
             * Sets the default `InfoStream` used by a newly instantiated classes. It cannot be `null`, to disable logging use [.NO_OUTPUT].
             *
             * @see .getDefault
             */
            set(infoStream) {
                requireNotNull(infoStream) {
                    ("Cannot set InfoStream default implementation to null. "
                            + "To disable logging use InfoStream.NO_OUTPUT")
                }
                defaultInfoStream = infoStream
            }
    }
}
