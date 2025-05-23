package org.gnit.lucenekmp.geo

/** Lat/Lon Geometry object.  */
open class LatLonGeometry : Geometry() {
    /** Creates a Component2D from the provided LatLonGeometry array  */

    companion object {
        fun create(vararg latLonGeometries: LatLonGeometry): Component2D {
            require(latLonGeometries.isNotEmpty()) { "geometries must not be empty" }
            if (latLonGeometries.size == 1) {
                requireNotNull(latLonGeometries[0]) { "geometries[0] must not be null" }
                return latLonGeometries[0].toComponent2D()
            }
            val components = Array(latLonGeometries.size) { latLonGeometries[it].toComponent2D() }
            return ComponentTree.create(components)
        }
    }

    override fun toComponent2D(): Component2D {
        TODO("Not yet implemented")
    }
}
