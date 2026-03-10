package org.gnit.lucenekmp.index

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PlatformTestThread(
    private val block: () -> Unit,
) {
    private var job: Job? = null

    fun start() {
        job = CoroutineScope(Dispatchers.Default).launch { block() }
    }

    fun join()
    {
        runBlocking {
            job?.join()
        }
    }
}
