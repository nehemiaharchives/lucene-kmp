package org.gnit.lucenekmp.jdkport

/**
 * port of java.util.concurrent.RejectedExecutionException
 *
 * Exception thrown by an [Executor] when a task cannot be
 * accepted for execution.
 *
 * @since 1.5
 * @author Doug Lea
 */
class RejectedExecutionException : RuntimeException {
    /**
     * Constructs a `RejectedExecutionException` with no detail message.
     * The cause is not initialized, and may subsequently be
     * initialized by a call to [initCause][.initCause].
     */
    constructor()

    /**
     * Constructs a `RejectedExecutionException` with the
     * specified detail message. The cause is not initialized, and may
     * subsequently be initialized by a call to [ ][.initCause].
     *
     * @param message the detail message
     */
    constructor(message: String) : super(message)

    /**
     * Constructs a `RejectedExecutionException` with the
     * specified detail message and cause.
     *
     * @param  message the detail message
     * @param  cause the cause (which is saved for later retrieval by the
     * [.getCause] method)
     */
    constructor(message: String, cause: Throwable) : super(message, cause)

    /**
     * Constructs a `RejectedExecutionException` with the
     * specified cause.  The detail message is set to `(cause ==
     * null  null : cause.toString())` (which typically contains
     * the class and detail message of `cause`).
     *
     * @param  cause the cause (which is saved for later retrieval by the
     * [.getCause] method)
     */
    constructor(cause: Throwable) : super(cause)
}
