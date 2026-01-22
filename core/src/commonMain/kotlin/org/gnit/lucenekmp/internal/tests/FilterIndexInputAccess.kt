package org.gnit.lucenekmp.internal.tests

import org.gnit.lucenekmp.store.FilterIndexInput
import kotlin.reflect.KClass

/**
 * Access to [org.apache.lucene.store.FilterIndexInput] internals exposed to the test
 * framework.
 *
 * @lucene.internal
 */
fun interface FilterIndexInputAccess {
    /** Adds the given test FilterIndexInput class.  */
    fun addTestFilterType(cls: KClass<out FilterIndexInput>)
}
