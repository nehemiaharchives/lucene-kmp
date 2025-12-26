package org.gnit.lucenekmp.analysis

import kotlin.reflect.KClass

expect object AnalysisSPIReflection {
    fun lookupSPIName(service: KClass<out AbstractAnalysisFactory>): String

    fun <T : AbstractAnalysisFactory> newFactoryClassInstance(
        clazz: KClass<T>,
        args: MutableMap<String, String>
    ): T
}
