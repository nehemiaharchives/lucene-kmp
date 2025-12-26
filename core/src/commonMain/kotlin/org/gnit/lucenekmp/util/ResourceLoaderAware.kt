package org.gnit.lucenekmp.util

import okio.IOException

/** Implemented by analysis factories that need access to external resources. */
interface ResourceLoaderAware {
    @Throws(IOException::class)
    fun inform(loader: ResourceLoader)
}
