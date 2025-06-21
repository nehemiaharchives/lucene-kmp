package org.gnit.lucenekmp.tests.store

import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FilterDirectory

/**
 * Minimal port of Lucene's BaseDirectoryWrapper.
 * Only provides basic tracking of open state and a toggle for
 * running checkIndex on close. The actual checkIndex logic is not
 * implemented yet.
 */
open class BaseDirectoryWrapper(
    /** Wrapped directory instance. */
    delegate: Directory
) : FilterDirectory(delegate) {

    /** whether this directory is still open */
    private var _isOpen: Boolean = true

    /** whether checkIndex should run on close */
    var checkIndexOnClose: Boolean = true

    /** level passed to checkIndex when closing */
    var levelForCheckOnClose: Int = 0

    override fun close() {
        if (_isOpen) {
            _isOpen = false
            // TODO: implement checkIndex when CheckIndex is ported
        }
        super.close()
    }

    /** Returns true if this directory has not been closed. */
    fun isOpen(): Boolean = _isOpen
}

