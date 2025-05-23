package org.gnit.lucenekmp.geo

/** Cartesian Geometry object.  */
open class XYGeometry : Geometry() {
    /** Creates a Component2D from the provided XYGeometries array  */
    companion object {
        fun create(vararg xyGeometries: XYGeometry): Component2D {
            require(xyGeometries.isNotEmpty()) { "geometries must not be empty" }
            if (xyGeometries.size == 1) {
                requireNotNull(xyGeometries[0]) { "geometries[0] must not be null" }
                return xyGeometries[0].toComponent2D()
            }
            val components = Array(xyGeometries.size) { xyGeometries[it].toComponent2D() }
            return ComponentTree.create(components)
        }
    }

    override fun toComponent2D(): Component2D {
        TODO("Not yet implemented")
    }
}
