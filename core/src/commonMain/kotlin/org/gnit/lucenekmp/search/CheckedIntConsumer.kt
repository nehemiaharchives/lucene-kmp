package org.gnit.lucenekmp.search

/** Like [IntConsumer], but may throw checked exceptions.  */
fun interface CheckedIntConsumer<T : Exception> {
    /**
     * Process the given value.
     *
     * @see IntConsumer.accept
     */
    /* T can not be used for kotlin limitation
    @Throws(T::class)*/
    fun accept(value: Int)
}
