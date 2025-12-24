package org.gnit.lucenekmp.jdkport

import kotlin.reflect.KClass

@Ported(from = "sun.util.locale.provider.JRELocaleProviderAdapter")
class JRELocaleProviderAdapter() : LocaleProviderAdapter() {

    override val adapterType: Type = Type.JRE

    override val availableLocales: Array<Locale> = arrayOf(Locale.US)

    private val langtagSets : MutableMap<String, Set<String>> = mutableMapOf("FormatData" to setOf("en")) // TODO hardcoding for now

    fun getLanguageTagSet(category: String): Set<String> {
        var tagset: Set<String>? = langtagSets[category]
        /*if (tagset == null) {
            tagset = createLanguageTagSet(category)
            val ts = langtagSets.putIfAbsent(category, tagset)
            if (ts != null) {
                tagset = ts
            }
        }*/
        return tagset!!
    }

    /*protected fun createLanguageTagSet(category: String): Set<String> {
        val supportedLocaleString = createSupportedLocaleString(category)
        return if (supportedLocaleString != null) setOf<String>(
            supportedLocaleString.split("\s+".toRegex()).dropLastWhile { it.isEmpty() }) else emptySet<String>()
    }

    private fun createSupportedLocaleString(category: String): String {
        // Directly call Base tags, as we know it's in the base module.
        var supportedLocaleString: String? =
            BaseLocaleDataMetaInfo.getSupportedLocaleString(category)

        // Use ServiceLoader to dynamically acquire installed locales' tags.
        try {
            val nonBaseTags: String? = AccessController.doPrivileged(PrivilegedExceptionAction {
                val tags: java.lang.StringBuilder = java.lang.StringBuilder()
                for (ldmi in ServiceLoader.loadInstalled(LocaleDataMetaInfo::class.java)) {
                    if (ldmi.getType() === Type.JRE) {
                        val t: String? = ldmi.availableLanguageTags(category)
                        if (t != null) {
                            if (!tags.isEmpty()) {
                                tags.append(' ')
                            }
                            tags.append(t)
                        }
                    }
                }
                tags.toString()
            } as PrivilegedExceptionAction<String?>)

            if (nonBaseTags != null) {
                supportedLocaleString += " " + nonBaseTags
            }
        } catch (pae: PrivilegedActionException) {
            throw java.lang.InternalError(pae.getCause())
        }

        return supportedLocaleString
    }*/

    override val breakIteratorProvider: BreakIteratorProvider = BreakIteratorProviderImpl(
        adapterType,
        getLanguageTagSet("FormatData")
    )

    override fun getLocaleServiceProvider(c: KClass<out LocaleServiceProvider>): LocaleServiceProvider {

        /*
        switch (c.getSimpleName()) {
        case "BreakIteratorProvider":
            return (P) getBreakIteratorProvider();
        case "CollatorProvider":
            return (P) getCollatorProvider();
        case "DateFormatProvider":
            return (P) getDateFormatProvider();
        case "DateFormatSymbolsProvider":
            return (P) getDateFormatSymbolsProvider();
        case "DecimalFormatSymbolsProvider":
            return (P) getDecimalFormatSymbolsProvider();
        case "NumberFormatProvider":
            return (P) getNumberFormatProvider();
        case "CurrencyNameProvider":
            return (P) getCurrencyNameProvider();
        case "LocaleNameProvider":
            return (P) getLocaleNameProvider();
        case "TimeZoneNameProvider":
            return (P) getTimeZoneNameProvider();
        case "CalendarDataProvider":
            return (P) getCalendarDataProvider();
        case "CalendarNameProvider":
            return (P) getCalendarNameProvider();
        case "CalendarProvider":
            return (P) getCalendarProvider();
        case "JavaTimeDateTimePatternProvider":
            return (P) getJavaTimeDateTimePatternProvider();
        default:
            throw new InternalError("should not come down here");
        }
        */
        val className = c.simpleName
        if(className == "BreakIteratorProvider"){
            return breakIteratorProvider
        }else{
            throw UnsupportedOperationException("only BreakIteratorProvider is supported for now")
        }
    }
}
