package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.jdkport.ClassLoader
import kotlin.reflect.KClass

/**
 * Abstract parent class for analysis factories that create [ ] instances.
 *
 * @since 3.1
 */
abstract class TokenFilterFactory : AbstractAnalysisFactory {
    /**
     * This static holder class prevents classloading deadlock by delaying init of factories until
     * needed.
     */
    private object Holder {
        private val LOADER: AnalysisSPILoader<TokenFilterFactory> = AnalysisSPILoader(TokenFilterFactory::class)

        val loader: AnalysisSPILoader<TokenFilterFactory>
            get() {
                checkNotNull(LOADER) {
                    ("You tried to lookup a TokenFilterFactory by name before all factories could be initialized. "
                            + "This likely happens if you call TokenFilterFactory#forName from a TokenFilterFactory's ctor.")
                }
                return LOADER
            }
    }

    /** Default ctor for compatibility with SPI  */
    protected constructor() : super()

    /** Initialize this factory via a set of key-value pairs.  */
    protected constructor(args: MutableMap<String, String>) : super(args)

    /** Transform the specified input TokenStream  */
    abstract fun create(input: TokenStream): TokenStream

    /**
     * Normalize the specified input TokenStream While the default implementation returns input
     * unchanged, filters that should be applied at normalization time can delegate to `create`
     * method.
     */
    fun normalize(input: TokenStream): TokenStream {
        return input
    }

    companion object {
        /** looks up a tokenfilter by name from context classpath  */
        fun forName(name: String, args: MutableMap<String, String>): TokenFilterFactory {
            return Holder.loader.newInstance(name, args)
        }

        /** looks up a tokenfilter class by name from context classpath  */
        fun lookupClass(name: String): KClass<out TokenFilterFactory> {
            return Holder.loader.lookupClass(name)
        }

        /** returns a list of all available tokenfilter names from context classpath  */
        fun availableTokenFilters(): MutableSet<String> {
            return Holder.loader.availableServices()
        }

        /** looks up a SPI name for the specified token filter factory  */
        fun findSPIName(serviceClass: KClass<out TokenFilterFactory>): String {
            try {
                return AnalysisSPILoader.lookupSPIName(serviceClass)
            } /*catch (e: java.lang.NoSuchFieldException) {
                throw java.lang.IllegalStateException(e)
            } catch (e: java.lang.IllegalAccessException) {
                throw java.lang.IllegalStateException(e)
            } catch (e: java.lang.IllegalStateException) {
                throw java.lang.IllegalStateException(e)
            }*/
            catch (e: Exception){

            }
            return TODO("implement later")
        }

        /**
         * Reloads the factory list from the given [ClassLoader]. Changes to the factories are
         * visible after the method ends, all iterators ([.availableTokenFilters],...) stay
         * consistent.
         *
         *
         * **NOTE:** Only new factories are added, existing ones are never removed or replaced.
         *
         *
         * *This method is expensive and should only be called for discovery of new factories on the
         * given classpath/classloader!*
         */
        fun reloadTokenFilters(classloader: ClassLoader) {
            Holder.loader.reload(classloader)
        }
    }
}
