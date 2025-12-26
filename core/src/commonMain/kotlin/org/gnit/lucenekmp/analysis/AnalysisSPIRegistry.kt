package org.gnit.lucenekmp.analysis

import kotlin.reflect.KClass

object AnalysisSPIRegistry {
    data class Entry(
        val base: KClass<out AbstractAnalysisFactory>,
        val name: String,
        val service: KClass<out AbstractAnalysisFactory>,
        val ctor: (MutableMap<String, String>) -> AbstractAnalysisFactory
    )

    private val entries = mutableListOf<Entry>()
    private val constructors = mutableMapOf<KClass<out AbstractAnalysisFactory>, (MutableMap<String, String>) -> AbstractAnalysisFactory>()

    fun register(
        base: KClass<out AbstractAnalysisFactory>,
        name: String,
        service: KClass<out AbstractAnalysisFactory>,
        ctor: (MutableMap<String, String>) -> AbstractAnalysisFactory
    ) {
        val existing = entries.any { it.base == base && it.name.equals(name, ignoreCase = true) }
        if (!existing) {
            entries.add(Entry(base, name, service, ctor))
        }
        constructors[service] = ctor
    }

    fun entriesFor(base: KClass<out AbstractAnalysisFactory>): List<Entry> {
        return entries.filter { it.base == base }
    }

    fun constructorFor(service: KClass<out AbstractAnalysisFactory>): ((MutableMap<String, String>) -> AbstractAnalysisFactory)? {
        return constructors[service]
    }
}
