package org.gnit.lucenekmp.jdkport

object Collections {

    /**
     * Swaps the elements at the specified positions in the specified list.
     * (If the specified positions are equal, invoking this method leaves
     * the list unchanged.)
     *
     * @param list The list in which to swap elements.
     * @param i the index of one element to be swapped.
     * @param j the index of the other element to be swapped.
     * @throws IndexOutOfBoundsException if either `i` or `j`
     * is out of range (i &lt; 0 || i &gt;= list.size()
     * || j &lt; 0 || j &gt;= list.size()).
     * @since 1.4
     */
    fun <T>swap(list: MutableList<T>, i: Int, j: Int) {
        // instead of using a raw type here, it's possible to capture
        // the wildcard but it will require a call to a supplementary
        // private method
        val l = list
        l[i] = l.set(j, l[i])
    }
}