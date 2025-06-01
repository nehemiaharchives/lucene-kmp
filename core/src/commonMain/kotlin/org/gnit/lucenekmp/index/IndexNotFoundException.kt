package org.gnit.lucenekmp.index

// could not be ported as a subclass of FileNotFoundException because it is final
// import okio.FileNotFoundException

/**
 * Signals that no index was found in the Directory. Possibly because the directory is empty,
 * however can also indicate an index corruption.
 */
class IndexNotFoundException
/** Creates IndexFileNotFoundException with the description message.  */
    (msg: String?) : /*FileNotFound*/Exception(msg)
