package org.gnit.lucenekmp.codecs.lucene95

import org.gnit.lucenekmp.store.IndexInput

/**
 * Implementors can return the IndexInput from which their values are read. For use by vector
 * quantizers.
 */
interface HasIndexSlice {
    /** Returns an IndexInput from which to read this instance's values.  */
    val slice: IndexInput?
}
