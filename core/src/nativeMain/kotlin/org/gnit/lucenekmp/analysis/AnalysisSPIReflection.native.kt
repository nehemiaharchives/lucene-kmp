package org.gnit.lucenekmp.analysis

import kotlin.reflect.KClass

actual object AnalysisSPIReflection {
    actual fun lookupSPIName(service: KClass<out AbstractAnalysisFactory>): String {
        val simpleName = service.simpleName ?: throw IllegalStateException("No SPI name defined.")
        val baseName = stripFactorySuffix(simpleName)
        if (baseName.isEmpty()) {
            throw IllegalStateException("No SPI name defined.")
        }
        val first = baseName[0].lowercaseChar()
        return first + baseName.substring(1)
    }

    actual fun <T : AbstractAnalysisFactory> newFactoryClassInstance(
        clazz: KClass<T>,
        args: MutableMap<String, String>
    ): T {
        val ctor = AnalysisSPIRegistry.constructorFor(clazz as KClass<out AbstractAnalysisFactory>)
        if (ctor != null) {
            @Suppress("UNCHECKED_CAST")
            return ctor(args) as T
        }
        throw UnsupportedOperationException(
            "Factory ${clazz.simpleName ?: "<unknown>"} cannot be instantiated. " +
                "This is likely due to missing Map<String,String> constructor."
        )
    }

    private fun stripFactorySuffix(name: String): String {
        val suffixes = listOf("TokenizerFactory", "TokenFilterFactory", "CharFilterFactory")
        for (suffix in suffixes) {
            if (name.endsWith(suffix) && name.length > suffix.length) {
                return name.removeSuffix(suffix)
            }
        }
        return name
    }
}
