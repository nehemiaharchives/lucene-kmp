package org.gnit.lucenekmp.jdkport


/**
 * An abstract class for service providers that
 * provide concrete implementations of the
 * [BreakIterator][java.text.BreakIterator] class.
 *
 * @since        1.6
 */
@Ported("java.text.spi.BreakIteratorProvider")
abstract class BreakIteratorProvider : LocaleServiceProvider() {
    /**
     * Returns a new `BreakIterator` instance
     * for [word breaks](../BreakIterator.html#word)
     * for the given locale.
     * @param locale the desired locale
     * @return A break iterator for word breaks
     * @throws    NullPointerException if `locale` is null
     * @throws    IllegalArgumentException if `locale` isn't
     * one of the locales returned from
     * [     getAvailableLocales()][java.util.spi.LocaleServiceProvider.getAvailableLocales].
     * @see java.text.BreakIterator.getWordInstance
     */
    abstract fun getWordInstance(locale: Locale): BreakIterator

    /**
     * Returns a new `BreakIterator` instance
     * for [line breaks](../BreakIterator.html#line)
     * for the given locale.
     * @param locale the desired locale
     * @return A break iterator for line breaks
     * @throws    NullPointerException if `locale` is null
     * @throws    IllegalArgumentException if `locale` isn't
     * one of the locales returned from
     * [     getAvailableLocales()][java.util.spi.LocaleServiceProvider.getAvailableLocales].
     * @see java.text.BreakIterator.getLineInstance
     */
    abstract fun getLineInstance(locale: Locale): BreakIterator

    /**
     * Returns a new `BreakIterator` instance
     * for [character breaks](../BreakIterator.html#character)
     * for the given locale.
     * @param locale the desired locale
     * @return A break iterator for character breaks
     * @throws    NullPointerException if `locale` is null
     * @throws    IllegalArgumentException if `locale` isn't
     * one of the locales returned from
     * [     getAvailableLocales()][java.util.spi.LocaleServiceProvider.getAvailableLocales].
     * @see java.text.BreakIterator.getCharacterInstance
     */
    abstract fun getCharacterInstance(locale: Locale): BreakIterator

    /**
     * Returns a new `BreakIterator` instance
     * for [sentence breaks](../BreakIterator.html#sentence)
     * for the given locale.
     * @param locale the desired locale
     * @return A break iterator for sentence breaks
     * @throws    NullPointerException if `locale` is null
     * @throws    IllegalArgumentException if `locale` isn't
     * one of the locales returned from
     * [     getAvailableLocales()][java.util.spi.LocaleServiceProvider.getAvailableLocales].
     * @see java.text.BreakIterator.getSentenceInstance
     */
    abstract fun getSentenceInstance(locale: Locale): BreakIterator
}
