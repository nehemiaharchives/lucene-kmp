package org.gnit.lucenekmp.util


import org.gnit.lucenekmp.jdkport.AtomicInteger
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import okio.IOException
import org.gnit.lucenekmp.jdkport.PrintStream
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * InfoStream implementation over a [PrintStream] such as `System.out`.
 *
 * @lucene.internal
 */
open class PrintStreamInfoStream @OptIn(ExperimentalAtomicApi::class) constructor(
    protected val stream: PrintStream,
    protected val messageID: Int = MESSAGE_ID.fetchAndIncrement()
) : InfoStream() {

    override fun message(component: String, message: String) {
        stream.println(
            (component
                    + " "
                    + messageID
                    + " ["
                    + this.timestamp
                    /*+ "; "
                    + java.lang.Thread.currentThread().getName()*/
                    + "]: "
                    + message)
        )
    }

    override fun isEnabled(component: String): Boolean {
        return true
    }

    @Throws(IOException::class)
    override fun close() {
        /*if (!this.isSystemStream) {
            stream.close()
        }*/
    }

    /*@get:org.apache.lucene.util.SuppressForbidden(reason = "System.out/err detection")
    val isSystemStream: Boolean
        get() = stream === java.lang.System.out || stream === java.lang.System.err*/

    @OptIn(ExperimentalTime::class)
    protected val timestamp: String
        /** Returns the current time as string for insertion into log messages.  */
        get() = Clock.System.now().toString()

    companion object {
        // Used for printing messages
        @OptIn(ExperimentalAtomicApi::class)
        private val MESSAGE_ID: AtomicInteger = AtomicInteger(0)
    }
}
