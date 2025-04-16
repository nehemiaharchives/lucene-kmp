package org.gnit.lucenekmp.util

import kotlinx.io.IOException

/**
 * A Runnable that may throw an IOException
 *
 * @see java.lang.Runnable
 */
fun interface IORunnable {
    @Throws(IOException::class)
    fun run()
}
