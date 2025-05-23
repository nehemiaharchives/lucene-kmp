package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.geo.Component2D

/** Base class for [LatLonGeometry] and [XYGeometry]  */
abstract class Geometry {
    /** get a Component2D from the geometry object  */
    protected abstract fun toComponent2D(): Component2D
}
