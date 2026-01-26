package org.gnit.lucenekmp.tests.store

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.internal.tests.TestSecrets
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.ReadAdvice
import org.gnit.lucenekmp.util.ThreadInterruptedException

/**
 * Takes a while to open files: gives testThreadInterruptDeadlock a chance to find file leaks if
 * opening an input throws exception
 */
internal class SlowOpeningMockIndexInputWrapper /*@org.apache.lucene.util.SuppressForbidden(reason = "Thread sleep")*/ constructor(
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
    init {
        try {
            /*java.lang.Thread.sleep(50)*/
            runBlocking { delay(50) }
        } catch (ie: CancellationException) {
            try {
                super.close()
            } catch (ignore: Throwable) {
                // we didnt open successfully
            }
            throw ThreadInterruptedException(ie)
        }
    }

    companion object {
        init {
            TestSecrets.filterInputIndexAccess.addTestFilterType(SlowOpeningMockIndexInputWrapper::class)
        }
    }
}
