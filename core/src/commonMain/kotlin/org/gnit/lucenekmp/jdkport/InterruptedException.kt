package org.gnit.lucenekmp.jdkport

/**
 * Thrown when a thread is waiting, sleeping, or otherwise occupied,
 * and the thread is interrupted, either before or during the activity.
 * Occasionally a method may wish to test whether the current
 * thread has been interrupted, and if so, to immediately throw
 * this exception.  The following code can be used to achieve
 * this effect:
 * {@snippet lang=java :
 * * if (Thread.interrupted())  // Clears interrupted status!
 * *     throw new InterruptedException();
 * * }
 *
 * @author  Frank Yellin
 * @see java.lang.Object.wait
 * @see java.lang.Object.wait
 * @see java.lang.Object.wait
 * @see java.lang.Thread.sleep
 * @see java.lang.Thread.interrupt
 * @see java.lang.Thread.interrupted
 * @since   1.0
 */
open class InterruptedException : Exception {
    /**
     * Constructs an `InterruptedException` with no detail  message.
     */
    constructor() : super()

    /**
     * Constructs an `InterruptedException` with the
     * specified detail message.
     *
     * @param   s   the detail message.
     */
    constructor(s: String) : super(s)

}
