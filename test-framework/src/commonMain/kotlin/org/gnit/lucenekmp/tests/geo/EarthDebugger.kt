package org.gnit.lucenekmp.tests.geo

/**
 * Minimal stub of EarthDebugger used in tests. It provides no-op implementations
 * just so that tests depending on it can compile.
 */
class EarthDebugger {
    fun addRect(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {}
    fun addCircle(centerLat: Double, centerLon: Double, radiusMeters: Double, alsoAddBBox: Boolean) {}
    fun finish(): String = ""
}
