package org.gnit.lucenekmp.tests.analysis

import org.gnit.lucenekmp.analysis.AbstractAnalysisFactory
import org.gnit.lucenekmp.analysis.AnalysisSPIReflection
import org.gnit.lucenekmp.analysis.CharFilterFactory
import org.gnit.lucenekmp.analysis.TokenFilterFactory
import org.gnit.lucenekmp.analysis.TokenizerFactory
import org.gnit.lucenekmp.util.ClasspathResourceLoader
import org.gnit.lucenekmp.util.ResourceLoader
import org.gnit.lucenekmp.util.ResourceLoaderAware
import org.gnit.lucenekmp.util.Version
import kotlin.reflect.KClass
import kotlin.test.assertNull

/**
 * Base class for testing tokenstream factories.
 */
abstract class BaseTokenStreamFactoryTestCase : BaseTokenStreamTestCase() {

    @Throws(Exception::class)
    private fun analysisFactory(
        clazz: KClass<out AbstractAnalysisFactory>,
        matchVersion: Version?,
        loader: ResourceLoader,
        vararg keysAndValues: String
    ): AbstractAnalysisFactory {
        if (keysAndValues.size % 2 == 1) {
            throw IllegalArgumentException("invalid keysAndValues map")
        }
        val args = mutableMapOf<String, String>()
        var i = 0
        while (i < keysAndValues.size) {
            val previous = args.put(keysAndValues[i], keysAndValues[i + 1])
            assertNull(previous, "duplicate values for key: ${keysAndValues[i]}")
            i += 2
        }
        if (matchVersion != null) {
            val previous = args.put("luceneMatchVersion", matchVersion.toString())
            assertNull(previous, "duplicate values for key: luceneMatchVersion")
        }

        @Suppress("UNCHECKED_CAST")
        val factory = AnalysisSPIReflection.newFactoryClassInstance(
            clazz as KClass<AbstractAnalysisFactory>,
            args
        )
        if (factory is ResourceLoaderAware) {
            factory.inform(loader)
        }
        return factory
    }

    /**
     * Returns a fully initialized TokenizerFactory with the specified name and key-value arguments.
     */
    @Throws(Exception::class)
    protected fun tokenizerFactory(name: String, vararg keysAndValues: String): TokenizerFactory {
        return tokenizerFactory(name, Version.LATEST, *keysAndValues)
    }

    /**
     * Returns a fully initialized TokenizerFactory with the specified name and key-value arguments.
     */
    @Throws(Exception::class)
    protected fun tokenizerFactory(
        name: String,
        version: Version,
        vararg keysAndValues: String
    ): TokenizerFactory {
        return tokenizerFactory(name, version, ClasspathResourceLoader(this::class), *keysAndValues)
    }

    /**
     * Returns a fully initialized TokenizerFactory with the specified name, version, resource loader,
     * and key-value arguments.
     */
    @Throws(Exception::class)
    protected fun tokenizerFactory(
        name: String,
        matchVersion: Version,
        loader: ResourceLoader,
        vararg keysAndValues: String
    ): TokenizerFactory {
        return analysisFactory(
            TokenizerFactory.lookupClass(name),
            matchVersion,
            loader,
            *keysAndValues
        ) as TokenizerFactory
    }

    /**
     * Returns a fully initialized TokenFilterFactory with the specified name and key-value arguments.
     */
    @Throws(Exception::class)
    protected fun tokenFilterFactory(
        name: String,
        version: Version,
        vararg keysAndValues: String
    ): TokenFilterFactory {
        return tokenFilterFactory(name, version, ClasspathResourceLoader(this::class), *keysAndValues)
    }

    /**
     * Returns a fully initialized TokenFilterFactory with the specified name and key-value arguments.
     */
    @Throws(Exception::class)
    protected fun tokenFilterFactory(name: String, vararg keysAndValues: String): TokenFilterFactory {
        return tokenFilterFactory(name, Version.LATEST, *keysAndValues)
    }

    /**
     * Returns a fully initialized TokenFilterFactory with the specified name, version, resource loader,
     * and key-value arguments.
     */
    @Throws(Exception::class)
    protected fun tokenFilterFactory(
        name: String,
        matchVersion: Version,
        loader: ResourceLoader,
        vararg keysAndValues: String
    ): TokenFilterFactory {
        return analysisFactory(
            TokenFilterFactory.lookupClass(name),
            matchVersion,
            loader,
            *keysAndValues
        ) as TokenFilterFactory
    }

    /**
     * Returns a fully initialized CharFilterFactory with the specified name and key-value arguments.
     */
    @Throws(Exception::class)
    protected fun charFilterFactory(name: String, vararg keysAndValues: String): CharFilterFactory {
        return charFilterFactory(name, Version.LATEST, ClasspathResourceLoader(this::class), *keysAndValues)
    }

    /**
     * Returns a fully initialized CharFilterFactory with the specified name, version, resource loader,
     * and key-value arguments.
     */
    @Throws(Exception::class)
    protected fun charFilterFactory(
        name: String,
        matchVersion: Version,
        loader: ResourceLoader,
        vararg keysAndValues: String
    ): CharFilterFactory {
        return analysisFactory(
            CharFilterFactory.lookupClass(name),
            matchVersion,
            loader,
            *keysAndValues
        ) as CharFilterFactory
    }
}
