package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.jdkport.ClassLoader
import org.gnit.lucenekmp.jdkport.Reader
import kotlin.reflect.KClass


/**
 * Abstract parent class for analysis factories that create [CharFilter] instances.
 *
 * @since 3.1
 */
abstract class CharFilterFactory : AbstractAnalysisFactory {
    /**
     * This static holder class prevents classloading deadlock by delaying init of factories until
     * needed.
     */
    private object Holder {
        private val LOADER: AnalysisSPILoader<CharFilterFactory> = AnalysisSPILoader(CharFilterFactory::class)

        val loader: AnalysisSPILoader<CharFilterFactory>
            get() {
                checkNotNull(LOADER) {
                    ("You tried to lookup a CharFilterFactory by name before all factories could be initialized. "
                            + "This likely happens if you call CharFilterFactory#forName from a CharFilterFactory's ctor.")
                }
                return LOADER
            }
    }

    /** Default ctor for compatibility with SPI  */
    protected constructor() : super()

    /** Initialize this factory via a set of key-value pairs.  */
    protected constructor(args: MutableMap<String, String>) : super(args)

    /** Wraps the given Reader with a CharFilter.  */
    abstract fun create(input: Reader): Reader

    /**
     * Normalize the specified input Reader While the default implementation returns input unchanged,
     * char filters that should be applied at normalization time can delegate to `create`
     * method.
     */
    fun normalize(input: Reader): Reader {
        return input
    }

    companion object {
        /** looks up a charfilter by name from context classpath  */
        fun forName(name: String, args: MutableMap<String, String>): CharFilterFactory {
            return Holder.loader.newInstance(name, args)
        }

        /** looks up a charfilter class by name from context classpath  */
        fun lookupClass(name: String): KClass<out CharFilterFactory> {
            return Holder.loader.lookupClass(name)
        }

        /** returns a list of all available charfilter names  */
        fun availableCharFilters(): MutableSet<String> {
            return Holder.loader.availableServices()
        }

        /** looks up a SPI name for the specified char filter factory  */
        fun findSPIName(serviceClass: KClass<out CharFilterFactory>): String {
            try {
                return AnalysisSPILoader.lookupSPIName(serviceClass)
            } /*catch (e: java.lang.NoSuchFieldException) {
                throw java.lang.IllegalStateException(e)
            } catch (e: java.lang.IllegalAccessException) {
                throw java.lang.IllegalStateException(e)
            } catch (e: java.lang.IllegalStateException) {
                throw java.lang.IllegalStateException(e)
            }*/
            catch (e: Exception) {
                throw IllegalStateException(e)
            }
        }

        /**
         * Reloads the factory list from the given [ClassLoader]. Changes to the factories are
         * visible after the method ends, all iterators ([.availableCharFilters],...) stay
         * consistent.
         *
         *
         * **NOTE:** Only new factories are added, existing ones are never removed or replaced.
         *
         *
         * *This method is expensive and should only be called for discovery of new factories on the
         * given classpath/classloader!*
         */
        fun reloadCharFilters(classloader: ClassLoader) {
            Holder.loader.reload(classloader)
        }
    }
}
