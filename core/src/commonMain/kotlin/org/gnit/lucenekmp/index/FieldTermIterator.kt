package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.util.BytesRefIterator


/**
 * Iterates over terms in across multiple fields. The caller must check [.field] after each
 * [.next] to see if the field changed, but `==` can be used since the iterator
 * implementation ensures it will use the same String instance for a given field.
 */
abstract class FieldTermIterator : BytesRefIterator {
    /**
     * Returns current field. This method should not be called after iteration is done. Note that you
     * may use == to detect a change in field.
     */
    abstract fun field(): String?

    /** Del gen of the current term.  */ // TODO: this is really per-iterator not per term, but when we use MergedPrefixCodedTermsIterator
    // we need to know which iterator we are on
    abstract fun delGen(): Long
}
