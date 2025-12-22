package org.gnit.lucenekmp.jdkport

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.reflect.KClass

/**
 * The LocaleProviderAdapter abstract class.
 *
 * @author Naoto Sato
 * @author Masayoshi Okutsu
 */
@Ported(from = "sun.util.locale.provider.LocaleProviderAdapter")
abstract class LocaleProviderAdapter {
    /**
     * Adapter type.
     */
    enum class Type(
        val adapterClassName: String,
        val utilResourcesPackage: String? = null,
        val textResourcesPackage: String? = null
    ) {
        JRE(
            "sun.util.locale.provider.JRELocaleProviderAdapter",
            "sun.util.resources",
            "sun.text.resources"
        ),
        CLDR(
            "sun.util.cldr.CLDRLocaleProviderAdapter",
            "sun.util.resources.cldr",
            "sun.text.resources.cldr"
        ),
        SPI("sun.util.locale.provider.SPILocaleProviderAdapter"),
        HOST("sun.util.locale.provider.HostLocaleProviderAdapter"),
        FALLBACK(
            "sun.util.locale.provider.FallbackLocaleProviderAdapter",
            "sun.util.resources",
            "sun.text.resources"
        )
    }

    /**
     * A utility method for implementing the default LocaleServiceProvider.isSupportedLocale
     * for the JRE, CLDR, and FALLBACK adapters.
     */
    fun isSupportedProviderLocale(locale: Locale, langtags: Set<String>): Boolean {
        val type = this.adapterType
        assert(type == Type.JRE || type == Type.CLDR || type == Type.FALLBACK)
        return false
    }

    /**
     * Returns the type of this LocaleProviderAdapter
     */
    abstract val adapterType: Type

    /**
     * Getter method for Locale Service Providers.
     */
    abstract fun <P : LocaleServiceProvider> getLocaleServiceProvider(c: KClass<out P>): P

    /**
     * Returns a BreakIteratorProvider for this LocaleProviderAdapter, or null if no
     * BreakIteratorProvider is available.
     *
     * @return a BreakIteratorProvider
     */
    abstract val breakIteratorProvider: BreakIteratorProvider

    /**
     * Returns a CollatorProvider for this LocaleProviderAdapter, or null if no
     * CollatorProvider is available.
     *
     * @return a collatorProvider
     */
    //abstract val collatorProvider: CollatorProvider

    /**
     * Returns a DateFormatProvider for this LocaleProviderAdapter, or null if no
     * DateFormatProvider is available.
     *
     * @return a DateFormatProvider
     */
    //abstract val dateFormatProvider: DateFormatProvider

    /**
     * Returns a DateFormatSymbolsProvider for this LocaleProviderAdapter, or null if no
     * DateFormatSymbolsProvider is available.
     *
     * @return a DateFormatSymbolsProvider
     */
    //abstract val dateFormatSymbolsProvider: DateFormatSymbolsProvider

    /**
     * Returns a DecimalFormatSymbolsProvider for this LocaleProviderAdapter, or null if no
     * DecimalFormatSymbolsProvider is available.
     *
     * @return a DecimalFormatSymbolsProvider
     */
    //abstract val decimalFormatSymbolsProvider: DecimalFormatSymbolsProvider

    /**
     * Returns a NumberFormatProvider for this LocaleProviderAdapter, or null if no
     * NumberFormatProvider is available.
     *
     * @return a NumberFormatProvider
     */
    //abstract val numberFormatProvider: NumberFormatProvider

    /*
     * Getter methods for java.util.spi.* providers
     */
    /**
     * Returns a CurrencyNameProvider for this LocaleProviderAdapter, or null if no
     * CurrencyNameProvider is available.
     *
     * @return a CurrencyNameProvider
     */
    //abstract val currencyNameProvider: CurrencyNameProvider

    /**
     * Returns a LocaleNameProvider for this LocaleProviderAdapter, or null if no
     * LocaleNameProvider is available.
     *
     * @return a LocaleNameProvider
     */
    //abstract val localeNameProvider: LocaleNameProvider

    /**
     * Returns a TimeZoneNameProvider for this LocaleProviderAdapter, or null if no
     * TimeZoneNameProvider is available.
     *
     * @return a TimeZoneNameProvider
     */
    //abstract val timeZoneNameProvider: TimeZoneNameProvider

    /**
     * Returns a CalendarDataProvider for this LocaleProviderAdapter, or null if no
     * CalendarDataProvider is available.
     *
     * @return a CalendarDataProvider
     */
    //abstract val calendarDataProvider: CalendarDataProvider

    /**
     * Returns a CalendarNameProvider for this LocaleProviderAdapter, or null if no
     * CalendarNameProvider is available.
     *
     * @return a CalendarNameProvider
     */
    //abstract val calendarNameProvider: CalendarNameProvider

    /**
     * Returns a CalendarProvider for this LocaleProviderAdapter, or null if no
     * CalendarProvider is available.
     *
     * @return a CalendarProvider
     */
    //abstract val calendarProvider: CalendarProvider

    /**
     * Returns a JavaTimeDateTimePatternProvider for this LocaleProviderAdapter,
     * or null if no JavaTimeDateTimePatternProvider is available.
     *
     * @return a JavaTimeDateTimePatternProvider
     */
    //abstract val javaTimeDateTimePatternProvider: JavaTimeDateTimePatternProvider

    //abstract fun getLocaleResources(locale: Locale): LocaleResources

    abstract val availableLocales: Array<Locale>

    companion object {

        val logger = KotlinLogging.logger {}

        /**
         * Returns the preference order of LocaleProviderAdapter.Type
         */
        /**
         * LocaleProviderAdapter preference list.
         */
        val adapterPreference: List<Type>

        /**
         * LocaleProviderAdapter instances
         */
        private val adapterInstances: MutableMap<Type, LocaleProviderAdapter> = mutableMapOf()

        /**
         * Adapter lookup cache.
         */
        private val adapterCache: MutableMap<KClass<out LocaleServiceProvider>, MutableMap<Locale, LocaleProviderAdapter>> =
            mutableMapOf()

        init {
            //val order: String = GetPropertyAction.privilegedGetProperty("java.locale.providers")
            val typeList: ArrayList<Type> = ArrayList()
            var invalidTypeMessage: String? = null
            var compatWarningMessage: String? = null

            // Check user specified adapter preference
            /*if (order != null && !order.isEmpty()) {
                val types = order.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                for (type in types) {
                    var type = type
                    type = type.trim { it <= ' ' }.uppercase(Locale.ROOT)
                    if (type == "COMPAT" || type == "JRE") {
                        compatWarningMessage = "COMPAT locale provider has been removed"
                    }
                    try {
                        val aType = Type.valueOf(type.trim { it <= ' ' }.uppercase(Locale.ROOT))
                        if (!typeList.contains(aType)) {
                            typeList.add(aType)
                        }
                    } catch (e: IllegalArgumentException) {
                        // construct a log message.
                        invalidTypeMessage =
                            "Invalid locale provider adapter \"$type\" ignored."
                    }
                }
            }*/

            if (typeList.isEmpty()) {
                // Default preference list.
                typeList.add(Type.CLDR)
            }

            // always append FALLBACK
            typeList.add(Type.FALLBACK)

            adapterPreference = typeList

            // Emit logs, if any, after 'adapterPreference' is initialized which is needed
            // for logging.
            if (invalidTypeMessage != null) {
                // could be caused by the user specifying wrong
                // provider name or format in the system property
                logger.info { "${LocaleProviderAdapter::class.qualifiedName} $invalidTypeMessage" }
            }
            if (compatWarningMessage != null) {
                logger.warn { "${LocaleProviderAdapter::class.qualifiedName} $compatWarningMessage" }
            }
        }

        /**
         * Returns the singleton instance for each adapter type
         */
        fun forType(type: Type): LocaleProviderAdapter {
            when (type) {
                Type.JRE, Type.CLDR, Type.SPI, Type.HOST, Type.FALLBACK -> {
                    var adapter: LocaleProviderAdapter? = adapterInstances[type]
                    if (adapter == null) {
                        // TODO need to implement not using reflection for kotlin/common and native
                        try {
                            // lazily load adapters here
                            adapter = JRELocaleProviderAdapter() /*KClass.forName(type.adapterClassName).getDeclaredConstructor().newInstance() as LocaleProviderAdapter*/

                            // TODO only return JRELocaleProviderAdapter for now for BreakIteratorProvider and BreakIterator to be functional. if needed expand to provide more types

                            val cached: LocaleProviderAdapter? =
                                adapterInstances.putIfAbsent(type, adapter)
                            if (cached != null) {
                                adapter = cached
                            }
                        } /*catch (e: java.lang.NoSuchMethodException) {
                            throw ServiceConfigurationError(
                                "Locale provider adapter \"" +
                                        type + "\"cannot be instantiated.", e
                            )
                        } catch (e: InvocationTargetException) {
                            throw ServiceConfigurationError(
                                "Locale provider adapter \"" +
                                        type + "\"cannot be instantiated.", e
                            )
                        } catch (e: KClassNotFoundException) {
                            throw ServiceConfigurationError(
                                "Locale provider adapter \"" +
                                        type + "\"cannot be instantiated.", e
                            )
                        } catch (e: java.lang.IllegalAccessException) {
                            throw ServiceConfigurationError(
                                "Locale provider adapter \"" +
                                        type + "\"cannot be instantiated.", e
                            )
                        } catch (e: java.lang.InstantiationException) {
                            throw ServiceConfigurationError(
                                "Locale provider adapter \"" +
                                        type + "\"cannot be instantiated.", e
                            )
                        } catch (e: java.lang.UnsupportedOperationException) {
                            throw ServiceConfigurationError(
                                "Locale provider adapter \"" +
                                        type + "\"cannot be instantiated.", e
                            )
                        }*/
                        catch(e: Exception){
                            throw Exception(e)
                        }
                    }
                    return adapter
                }

                //else -> throw Error("unknown locale data adapter type")
            }
        }

        fun forJRE(): LocaleProviderAdapter {
            return forType(Type.JRE)
        }

        val resourceBundleBased: LocaleProviderAdapter
            get() {
                for (type in adapterPreference) {
                    if (type == Type.JRE || type == Type.CLDR || type == Type.FALLBACK) {
                        val adapter: LocaleProviderAdapter = forType(type)
                        if (adapter != null) {
                            return adapter
                        }
                    }
                }
                // Shouldn't happen.
                throw Error()
            }

        /**
         * Returns a LocaleProviderAdapter for the given locale service provider that
         * best matches the given locale. This method returns the LocaleProviderAdapter
         * for JRE if none is found for the given locale.
         *
         * @param providerClass the class for the locale service provider
         * @param locale the desired locale.
         * @return a LocaleProviderAdapter
         */
        fun getAdapter(
            providerClass: KClass<out LocaleServiceProvider>,
            locale: Locale
        ): LocaleProviderAdapter {
            var adapter: LocaleProviderAdapter?

            // cache lookup
            var adapterMap: MutableMap<Locale, LocaleProviderAdapter>? = adapterCache[providerClass]
            if (adapterMap != null) {
                if ((adapterMap[locale].also { adapter = it }) != null) {
                    return adapter!!
                }
            } else {
                adapterMap = mutableMapOf()
                adapterCache.putIfAbsent(providerClass, adapterMap)
            }

            // Fast look-up for the given locale
            adapter = findAdapter(providerClass, locale)
            if (adapter != null) {
                adapterMap.putIfAbsent(locale, adapter)
                return adapter
            }

            // Try finding an adapter in the normal candidate locales path of the given locale.
            val lookupLocales: List<Locale> =
                /*ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_DEFAULT)
                    .getCandidateLocales("", locale)*/ listOf(Locale.US) // TODO support more locale later
            for (loc in lookupLocales) {
                if (loc == locale) {
                    // We've already done with this loc.
                    continue
                }
                adapter = findAdapter(providerClass, loc)
                if (adapter != null) {
                    adapterMap.putIfAbsent(locale, adapter)
                    return adapter
                }
            }

            // returns the adapter for FALLBACK as the last resort
            adapterMap.putIfAbsent(locale, forType(Type.FALLBACK))
            return forType(Type.FALLBACK)
        }

        private fun findAdapter(
            providerClass: KClass<out LocaleServiceProvider>,
            locale: Locale
        ): LocaleProviderAdapter? {
            for (type in adapterPreference) {
                val adapter: LocaleProviderAdapter = forType(type)
                if (adapter != null) {
                    val provider: LocaleServiceProvider =
                        adapter.getLocaleServiceProvider(providerClass)
                    if (provider != null) {
                        if (provider.isSupportedLocale(locale)) {
                            return adapter
                        }
                    }
                }
            }
            return null
        }

        fun toLocaleArray(tags: Set<String>): Array<Locale> {
            return tags
                .map{ t ->
                    when (t) {
                        "ja-JP-JP" -> /*JRELocaleConstants.JA_JP_JP*/ Locale("ja", "JP", "JP")
                        "no-NO-NY" -> /*JRELocaleConstants.NO_NO_NY*/ Locale("no", "NO", "NY")
                        "th-TH-TH" -> /*JRELocaleConstants.TH_TH_TH*/ Locale("th", "TH", "TH")
                        else -> Locale.forLanguageTag(t)
                    }
                }
                .distinct().toTypedArray()
        }
    }
}
