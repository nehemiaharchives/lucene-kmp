package morfologik.stemming

/**
 * A generic "stemmer" interface in Morfologik.
 */
interface IStemmer {
    /**
     * Returns a list of [WordData] entries for a given word. The returned
     * list is never null. The returned list and any object it contains are not
     * usable after a subsequent call to this method.
     */
    fun lookup(word: CharSequence): List<WordData>
}
