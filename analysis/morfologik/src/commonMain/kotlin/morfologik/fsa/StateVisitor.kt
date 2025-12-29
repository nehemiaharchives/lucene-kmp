package morfologik.fsa

/**
 * State visitor.
 */
fun interface StateVisitor {
    fun accept(state: Int): Boolean
}
