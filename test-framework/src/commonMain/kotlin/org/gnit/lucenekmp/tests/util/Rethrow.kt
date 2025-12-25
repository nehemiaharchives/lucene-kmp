package org.gnit.lucenekmp.tests.util

/**
 * Sneaky: rethrowing checked exceptions as unchecked ones. Eh, it is sometimes useful...
 *
 *
 * Pulled from [Java Puzzlers](http://www.javapuzzlers.com).
 *
 * @see [http://www.amazon.com/Java-Puzzlers-Traps-Pitfalls-Corner/dp/032133678X](http://www.amazon.com/Java-Puzzlers-Traps-Pitfalls-Corner/dp/032133678X)
 */
object Rethrow {
    /** Rethrows `t` (identical object).  */
    fun rethrow(t: Throwable) {
        rethrow0(t)
    }

    private fun rethrow0(t: Throwable) {
        throw t
    }
}
