package org.gnit.lucenekmp.tests.store

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okio.IOException
import org.gnit.lucenekmp.internal.tests.TestSecrets
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.ReadAdvice
import org.gnit.lucenekmp.util.ThreadInterruptedException

/**
 * hangs onto files a little bit longer (50ms in close). MockDirectoryWrapper acts like windows: you
 * can't delete files open elsewhere. so the idea is to make race conditions for tiny files (like
 * segments) easier to reproduce.
 */
internal class SlowClosingMockIndexInputWrapper(
    dir: MockDirectoryWrapper,
    name: String,
    delegate: IndexInput,
    readAdvice: ReadAdvice,
    confined: Boolean
) : MockIndexInputWrapper(
    dir,
    name,
    delegate,
    null,
    readAdvice,
    confined
) {
    /*@org.apache.lucene.util.SuppressForbidden(reason = "Thread sleep")*/
    override fun close() {
        try {
            /*java.lang.Thread.sleep(50)*/
            runBlocking { delay(50) }
        } catch (ie: /*InterruptedException*/ CancellationException) {
            throw ThreadInterruptedException(ie)
        } finally {
            super.close()
        }
    }

    companion object {
        init {
            TestSecrets.filterInputIndexAccess.addTestFilterType(SlowClosingMockIndexInputWrapper::class)
        }
    }
}
