package org.gnit.lucenekmp.analysis.ja

import okio.IOException
import org.gnit.lucenekmp.analysis.AbstractAnalysisFactory
import org.gnit.lucenekmp.analysis.Analyzer
import org.gnit.lucenekmp.analysis.CharFilterFactory
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.Tokenizer
import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.analysis.boost.DelimitedBoostTokenFilterFactory
import org.gnit.lucenekmp.analysis.miscellaneous.DelimitedTermFrequencyTokenFilterFactory
import org.gnit.lucenekmp.jdkport.Reader
import org.gnit.lucenekmp.tests.analysis.BaseTokenStreamTestCase
import org.gnit.lucenekmp.tests.analysis.MockTokenizer
/*import org.gnit.lucenekmp.tests.util.LuceneTestCase.Nightly*/
import org.gnit.lucenekmp.tests.util.StringMockResourceLoader
import org.gnit.lucenekmp.util.AttributeFactory
import org.gnit.lucenekmp.util.ResourceLoaderAware
import org.gnit.lucenekmp.util.Version
import kotlin.reflect.KClass
import kotlin.test.Test

/**
 * Sanity check some things about all factories, we do our best to see if we can sanely initialize
 * it with no parameters and smoke test it, etc.
 */
// TODO: this was copied from the analysis/common module ... find a better way to share it!
// TODO: fix this to use CustomAnalyzer instead of its own FactoryAnalyzer
/*@LuceneTestCase.Nightly*/
class TestFactories : BaseTokenStreamTestCase() {

    @Test
    @Throws(IOException::class)
    fun test() {
        for (tokenizer in TokenizerFactory.availableTokenizers()) {
            doTestTokenizer(tokenizer)
        }

        for (tokenFilter in TokenFilterFactory.availableTokenFilters()) {
            doTestTokenFilter(tokenFilter)
        }

        for (charFilter in CharFilterFactory.availableCharFilters()) {
            doTestCharFilter(charFilter)
        }
    }

    @Throws(IOException::class)
    private fun doTestTokenizer(tokenizer: String) {
        val factoryClazz: KClass<out TokenizerFactory> = TokenizerFactory.lookupClass(tokenizer)
        val factory: TokenizerFactory = initialize(factoryClazz) as TokenizerFactory
        if (factory != null) {
            // we managed to fully create an instance. check a few more things:
            if (!EXCLUDE_FACTORIES_RANDOM_DATA.contains(factory::class)) {
                // beast it just a little, it shouldnt throw exceptions:
                // (it should have thrown them in initialize)
                val a: Analyzer = FactoryAnalyzer(factory, null, null)
                checkRandomData(
                    random(),
                    a,
                    3,
                    20,
                    false,
                    false
                )
                a.close()
            }
        }
    }

    @Throws(IOException::class)
    private fun doTestTokenFilter(tokenfilter: String) {
        val factoryClazz: KClass<out TokenFilterFactory> =
            TokenFilterFactory.lookupClass(tokenfilter)
        val factory: TokenFilterFactory =
            initialize(factoryClazz) as TokenFilterFactory
        if (factory != null) {
            // we managed to fully create an instance. check a few more things:
            if (!EXCLUDE_FACTORIES_RANDOM_DATA.contains(factory::class)) {
                // beast it just a little, it shouldnt throw exceptions:
                // (it should have thrown them in initialize)
                val a: Analyzer =
                    FactoryAnalyzer(assertingTokenizer, factory, null)
                checkRandomData(
                    random(),
                    a,
                    3,
                    20,
                    false,
                    false
                )
                a.close()
            }
        }
    }

    @Throws(IOException::class)
    private fun doTestCharFilter(charfilter: String) {
        val factoryClazz: KClass<out CharFilterFactory> =
            CharFilterFactory.lookupClass(charfilter)
        val factory: CharFilterFactory =
            initialize(factoryClazz) as CharFilterFactory
        if (factory != null) {
            // we managed to fully create an instance. check a few more things:
            if (!EXCLUDE_FACTORIES_RANDOM_DATA.contains(factory::class)) {
                // beast it just a little, it shouldnt throw exceptions:
                // (it should have thrown them in initialize)
                val a: Analyzer =
                    FactoryAnalyzer(assertingTokenizer, null, factory)
                checkRandomData(
                    random(),
                    a,
                    3,
                    20,
                    false,
                    false
                )
                a.close()
            }
        }
    }

    /** tries to initialize a factory with no arguments  */
    @Throws(IOException::class)
    private fun initialize(factoryClazz: KClass<out AbstractAnalysisFactory>): AbstractAnalysisFactory? {
        val args: MutableMap<String, String> = HashMap()
        args["luceneMatchVersion"] = Version.LATEST.toString()

        /*val ctor: java.lang.reflect.Constructor<out AbstractAnalysisFactory>
        try {
            ctor = factoryClazz.getConstructor(MutableMap::class.java)
        } catch (e: Exception) {
            throw RuntimeException(
                "factory '" + factoryClazz + "' does not have a proper actor!",
                e
            )
        }*/

        var factory: AbstractAnalysisFactory? = null
        try {
            factory = /*ctor.newInstance(args)*/ TODO("implement something without using reflection")
        } /*catch (e: java.lang.InstantiationException) {
            throw RuntimeException(e)
        } catch (e: java.lang.IllegalAccessException) {
            throw RuntimeException(e)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            if (e.cause is IllegalArgumentException) {
                // it's ok if we dont provide the right parameters to throw this
                return null
            }
        }*/catch (e: Exception){
            return null
        }

        if (factory is ResourceLoaderAware) {
            try {
                (factory as ResourceLoaderAware).inform(
                    StringMockResourceLoader(
                        ""
                    )
                )
            } catch (ignored: IOException) {
                // it's ok if the right files arent available or whatever to throw this
            } catch (ignored: IllegalArgumentException) {
                // is this ok I guess so
            }
        }
        return factory
    }

    // some silly classes just so we can use checkRandomData
    private val assertingTokenizer: TokenizerFactory = object :
        TokenizerFactory(HashMap()) {
        override fun create(factory: AttributeFactory): MockTokenizer {
            return MockTokenizer(factory)
        }
    }

    private class FactoryAnalyzer(
        tokenizer: TokenizerFactory?,
        tokenfilter: TokenFilterFactory?,
        charFilter: CharFilterFactory?
    ) : Analyzer() {
        val tokenizer: TokenizerFactory
        val charFilter: CharFilterFactory?
        val tokenfilter: TokenFilterFactory?

        init {
            checkNotNull(tokenizer)
            this.tokenizer = tokenizer
            this.charFilter = charFilter
            this.tokenfilter = tokenfilter
        }

        override fun createComponents(fieldName: String): TokenStreamComponents {
            val tf: Tokenizer =
                tokenizer.create(newAttributeFactory())
            if (tokenfilter != null) {
                return TokenStreamComponents(
                    tf,
                    tokenfilter.create(tf)
                )
            } else {
                return TokenStreamComponents(tf)
            }
        }

        override fun initReader(
            fieldName: String,
            reader: Reader
        ): Reader {
            if (charFilter != null) {
                return charFilter.create(reader)
            } else {
                return reader
            }
        }
    }

    companion object {
        /** Factories that are excluded from testing it with random data  */
        private val EXCLUDE_FACTORIES_RANDOM_DATA: MutableSet<KClass<out AbstractAnalysisFactory>> =
            mutableSetOf<KClass<out AbstractAnalysisFactory>>(
                DelimitedTermFrequencyTokenFilterFactory::class,
                DelimitedBoostTokenFilterFactory::class
            )
    }
}
