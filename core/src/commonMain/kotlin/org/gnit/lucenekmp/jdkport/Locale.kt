package org.gnit.lucenekmp.jdkport

/**
 * ported to keep API surface compatible with Java lucene
 * However, as I ever know, only QueryParserBase and some class are using Locale for the purpose of generating
 * RangeQuery and Locale is used to feed DateFormat to get date instance. This operation can be ignored as
 * we can implement equivalent without using Locale.
 *
 * Also, in initialization process, locale is set to be defaultLocale(), so in most cases no need to to change it.
 *
 * If lucene use Locale more extensively in the future, we may need to implement Locale in detail.
 *
*/
@Ported(from = "java.util.Locale")
class Locale
