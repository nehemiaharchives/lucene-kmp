package org.gnit.lucenekmp.search

import okio.IOException

/**
 * A composite [CollectorManager] which wraps a set of [CollectorManager] instances,
 * akin to how [MultiCollector] wraps [Collector] instances.
 */
class MultiCollectorManager @Suppress("UNCHECKED_CAST") constructor(
    vararg collectorManagers: CollectorManager<out Collector, *>?
) : CollectorManager<Collector, Array<Any?>> {

    private val collectorManagers: Array<CollectorManager<Collector, *>>

    init {
        if (collectorManagers.isEmpty()) {
            throw IllegalArgumentException("There must be at least one collector manager")
        }

        for (collectorManager in collectorManagers) {
            if (collectorManager == null) {
                throw IllegalArgumentException("Collector managers should all be non-null")
            }
        }

        this.collectorManagers =
            collectorManagers.map { it as CollectorManager<Collector, *> }.toTypedArray()
    }

    @Throws(IOException::class)
    override fun newCollector(): Collector {
        val collectors = arrayOfNulls<Collector>(collectorManagers.size)
        for (i in collectorManagers.indices) {
            collectors[i] = collectorManagers[i].newCollector()
        }
        return MultiCollector.wrap(*collectors)
    }

    @Throws(IOException::class)
    override fun reduce(collectors: MutableCollection<Collector>): Array<Any?> {
        val size = collectors.size
        val results = arrayOfNulls<Any?>(collectorManagers.size)
        for (i in collectorManagers.indices) {
            val reducibleCollector = ArrayList<Collector>(size)
            for (collector in collectors) {
                // MultiCollector will not actually wrap the collector if only one is provided, so we
                // check the instance type here:
                if (collector is MultiCollector) {
                    reducibleCollector.add(collector.getCollectors()[i])
                } else {
                    reducibleCollector.add(collector)
                }
            }
            results[i] = collectorManagers[i].reduce(reducibleCollector)
        }
        return results
    }
}
