package org.gnit.lucenekmp.index

/**
 * An [IndexDeletionPolicy] which keeps all index commits around, never deleting them. This
 * class is a singleton and can be accessed by referencing [.INSTANCE].
 */
class NoDeletionPolicy private constructor() : IndexDeletionPolicy() {
    override fun onCommit(commits: MutableList<out IndexCommit>) {}

    override fun onInit(commits: MutableList<out IndexCommit>) {}

    companion object {
        /** The single instance of this class.  */
        val INSTANCE: IndexDeletionPolicy = NoDeletionPolicy()
    }
}
