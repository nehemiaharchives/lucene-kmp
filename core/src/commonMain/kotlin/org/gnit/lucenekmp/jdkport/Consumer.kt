package org.gnit.lucenekmp.jdkport

/**
 * port of java.util.function.Consumer
 *
 * Represents an operation that accepts a single input argument and returns no
 * result. Unlike most other functional interfaces, `Consumer` is expected
 * to operate via side-effects.
 *
 *
 * This is a [functional interface](package-summary.html)
 * whose functional method is [.accept].
 *
 * @param <T> the type of the input to the operation
 *
 * @since 1.8
</T> */
fun interface Consumer<T> {
    /**
     * Performs this operation on the given argument.
     *
     * @param t the input argument
     */
    fun accept(t: T)

    /**
     * Returns a composed `Consumer` that performs, in sequence, this
     * operation followed by the `after` operation. If performing either
     * operation throws an exception, it is relayed to the caller of the
     * composed operation.  If performing this operation throws an exception,
     * the `after` operation will not be performed.
     *
     * @param after the operation to perform after this operation
     * @return a composed `Consumer` that performs in sequence this
     * operation followed by the `after` operation
     * @throws NullPointerException if `after` is null
     */
    fun andThen(after: Consumer<in T>): Consumer<T> {
        return Consumer { t: T ->
            accept(t)
            after.accept(t)
        }
    }
}
