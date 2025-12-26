package org.gnit.lucenekmp.analysis

import org.gnit.lucenekmp.jdkport.ClassLoader
import org.gnit.lucenekmp.util.AttributeFactory
import kotlin.reflect.KClass

/**
 * Abstract parent class for analysis factories that create [Tokenizer] instances.
 *
 * @since 3.1
 */
abstract class TokenizerFactory : AbstractAnalysisFactory {
    /**
     * This static holder class prevents classloading deadlock by delaying init of factories until
     * needed.
     */
    private object Holder {
        private val LOADER: AnalysisSPILoader<TokenizerFactory> = AnalysisSPILoader(TokenizerFactory::class)

        val loader: AnalysisSPILoader<TokenizerFactory>
            get() {
                checkNotNull(LOADER) {
                    ("You tried to lookup a TokenizerFactory by name before all factories could be initialized. "
                            + "This likely happens if you call TokenizerFactory#forName from a TokenizerFactory ctor.")
                }
                return LOADER
            }
    }

    /** Default ctor for compatibility with SPI  */
    protected constructor() : super()

    /** Initialize this factory via a set of key-value pairs.  */
    protected constructor(args: MutableMap<String, String>) : super(args)

    /** Creates a TokenStream of the specified input using the default attribute factory.  */
    fun create(): Tokenizer {
        return create(TokenStream.DEFAULT_TOKEN_ATTRIBUTE_FACTORY)
    }

    /** Creates a TokenStream of the specified input using the given AttributeFactory  */
    abstract fun create(factory: AttributeFactory): Tokenizer

    companion object {
        /** looks up a tokenizer by name from context classpath  */
        fun forName(name: String, args: MutableMap<String, String>): TokenizerFactory {
            return Holder.loader.newInstance(name, args)
        }

        /** looks up a tokenizer class by name from context classpath  */
        fun lookupClass(name: String): KClass<out TokenizerFactory> {
            return Holder.loader.lookupClass(name)
        }

        /** returns a list of all available tokenizer names from context classpath  */
        fun availableTokenizers(): MutableSet<String> {
            return Holder.loader.availableServices()
        }

        /** looks up a SPI name for the specified tokenizer factory  */
        fun findSPIName(serviceClass: KClass<out TokenizerFactory>): String {
            try {
                return AnalysisSPILoader.lookupSPIName(serviceClass)
            } /*catch (e: java.lang.NoSuchFieldException) {
                throw java.lang.IllegalStateException(e)
            } catch (e: java.lang.IllegalAccessException) {
                throw java.lang.IllegalStateException(e)
            } catch (e: java.lang.IllegalStateException) {
                throw java.lang.IllegalStateException(e)
            }*/ catch (e: Exception) {
                throw IllegalStateException(e)
            }
        }

        /**
         * Reloads the factory list from the given [ClassLoader]. Changes to the factories are
         * visible after the method ends, all iterators ([.availableTokenizers],...) stay
         * consistent.
         *
         *
         * **NOTE:** Only new factories are added, existing ones are never removed or replaced.
         *
         *
         * *This method is expensive and should only be called for discovery of new factories on the
         * given classpath/classloader!*
         */
        fun reloadTokenizers(classloader: ClassLoader) {
            Holder.loader.reload(classloader)
        }
    }
}
