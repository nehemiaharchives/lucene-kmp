package org.gnit.lucenekmp.geo

import org.gnit.lucenekmp.index.PointValues.Relation;
import org.gnit.lucenekmp.jdkport.compare
import org.gnit.lucenekmp.util.ArrayUtil;
import kotlin.math.max
import kotlin.math.min

/**
 * 2D multi-component geometry implementation represented as an interval tree of components.
 *
 *
 * Construction takes `O(n log n)` time for sorting and tree construction.
 */
internal class ComponentTree private constructor(component: Component2D, splitX: Boolean) :
    Component2D {
    /** minimum Y of this geometry's bounding box area  */
    override var minY: Double
        private set

    /** maximum Y of this geometry's bounding box area  */
    override var maxY: Double
        private set

    /** minimum X of this geometry's bounding box area  */
    override var minX: Double
        private set

    /** maximum X of this geometry's bounding box area  */
    override var maxX: Double
        private set

    // child components, or null. Note internal nodes might mot have
    // a consistent bounding box. Internal nodes should not be accessed
    // outside if this class.
    private var left: Component2D? = null
    private var right: Component2D? = null

    /** which dimension was this node split on  */ // TODO: its implicit based on level, but boolean keeps code simple
    private val splitX: Boolean

    /** root node of edge tree  */
    private val component: Component2D

    init {
        this.minY = component.minY
        this.maxY = component.maxY
        this.minX = component.minX
        this.maxX = component.maxX
        this.component = component
        this.splitX = splitX
    }

    override fun contains(x: Double, y: Double): Boolean {
        if (y <= this.maxY && x <= this.maxX) {
            if (component.contains(x, y)) {
                return true
            }
            if (left != null) {
                if (left!!.contains(x, y)) {
                    return true
                }
            }
            if (right != null
                && ((splitX == false && y >= this.component.minY)
                        || (splitX && x >= this.component.minX))
            ) {
                if (right!!.contains(x, y)) {
                    return true
                }
            }
        }
        return false
    }

    override fun intersectsLine(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        aX: Double,
        aY: Double,
        bX: Double,
        bY: Double
    ): Boolean {
        if (minY <= this.maxY && minX <= this.maxX) {
            if (component.intersectsLine(minX, maxX, minY, maxY, aX, aY, bX, bY)) {
                return true
            }
            if (left != null) {
                if (left!!.intersectsLine(minX, maxX, minY, maxY, aX, aY, bX, bY)) {
                    return true
                }
            }
            if (right != null
                && ((splitX == false && maxY >= this.component.minY)
                        || (splitX && maxX >= this.component.minX))
            ) {
                if (right!!.intersectsLine(minX, maxX, minY, maxY, aX, aY, bX, bY)) {
                    return true
                }
            }
        }
        return false
    }

    override fun intersectsTriangle(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        aX: Double,
        aY: Double,
        bX: Double,
        bY: Double,
        cX: Double,
        cY: Double
    ): Boolean {
        if (minY <= this.maxY && minX <= this.maxX) {
            if (component.intersectsTriangle(minX, maxX, minY, maxY, aX, aY, bX, bY, cX, cY)) {
                return true
            }
            if (left != null) {
                if (left!!.intersectsTriangle(minX, maxX, minY, maxY, aX, aY, bX, bY, cX, cY)) {
                    return true
                }
            }
            if (right != null
                && ((splitX == false && maxY >= this.component.minY)
                        || (splitX && maxX >= this.component.minX))
            ) {
                if (right!!.intersectsTriangle(minX, maxX, minY, maxY, aX, aY, bX, bY, cX, cY)) {
                    return true
                }
            }
        }
        return false
    }

    override fun containsLine(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        aX: Double,
        aY: Double,
        bX: Double,
        bY: Double
    ): Boolean {
        if (minY <= this.maxY && minX <= this.maxX) {
            if (component.containsLine(minX, maxX, minY, maxY, aX, aY, bX, bY)) {
                return true
            }
            if (left != null) {
                if (left!!.containsLine(minX, maxX, minY, maxY, aX, aY, bX, bY)) {
                    return true
                }
            }
            if (right != null
                && ((splitX == false && maxY >= this.component.minY)
                        || (splitX && maxX >= this.component.minX))
            ) {
                if (right!!.containsLine(minX, maxX, minY, maxY, aX, aY, bX, bY)) {
                    return true
                }
            }
        }
        return false
    }

    override fun containsTriangle(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        aX: Double,
        aY: Double,
        bX: Double,
        bY: Double,
        cX: Double,
        cY: Double
    ): Boolean {
        if (minY <= this.maxY && minX <= this.maxX) {
            if (component.containsTriangle(minX, maxX, minY, maxY, aX, aY, bX, bY, cX, cY)) {
                return true
            }
            if (left != null) {
                if (left!!.containsTriangle(minX, maxX, minY, maxY, aX, aY, bX, bY, cX, cY)) {
                    return true
                }
            }
            if (right != null
                && ((splitX == false && maxY >= this.component.minY)
                        || (splitX && maxX >= this.component.minX))
            ) {
                if (right!!.containsTriangle(minX, maxX, minY, maxY, aX, aY, bX, bY, cX, cY)) {
                    return true
                }
            }
        }
        return false
    }

    override fun withinPoint(x: Double, y: Double): Component2D.WithinRelation {
        require(!(left != null || right != null)) { "withinPoint is not supported for shapes with more than one component" }
        return component.withinPoint(x, y)
    }

    override fun withinLine(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        aX: Double,
        aY: Double,
        ab: Boolean,
        bX: Double,
        bY: Double
    ): Component2D.WithinRelation {
        require(!(left != null || right != null)) { "withinLine is not supported for shapes with more than one component" }
        return component.withinLine(minX, maxX, minY, maxY, aX, aY, ab, bX, bY)
    }

    override fun withinTriangle(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double,
        aX: Double,
        aY: Double,
        ab: Boolean,
        bX: Double,
        bY: Double,
        bc: Boolean,
        cX: Double,
        cY: Double,
        ca: Boolean
    ): Component2D.WithinRelation {
        require(!(left != null || right != null)) { "withinTriangle is not supported for shapes with more than one component" }
        return component.withinTriangle(minX, maxX, minY, maxY, aX, aY, ab, bX, bY, bc, cX, cY, ca)
    }

    override fun relate(
        minX: Double,
        maxX: Double,
        minY: Double,
        maxY: Double
    ): Relation {
        if (minY <= this.maxY && minX <= this.maxX) {
            var relation: Relation = component.relate(minX, maxX, minY, maxY)
            if (relation != Relation.CELL_OUTSIDE_QUERY) {
                return relation
            }
            if (left != null) {
                relation = left!!.relate(minX, maxX, minY, maxY)
                if (relation != Relation.CELL_OUTSIDE_QUERY) {
                    return relation
                }
            }
            if (right != null
                && ((splitX == false && maxY >= this.component.minY)
                        || (splitX && maxX >= this.component.minX))
            ) {
                relation = right!!.relate(minX, maxX, minY, maxY)
                if (relation != Relation.CELL_OUTSIDE_QUERY) {
                    return relation
                }
            }
        }
        return Relation.CELL_OUTSIDE_QUERY
    }

    companion object {
        /** Creates tree from provided components  */
        fun create(components: Array<Component2D>): Component2D {
            if (components.size == 1) {
                return components[0]
            }
            val root = createTree(components, 0, components.size - 1, false)!!
            // pull up min values for the root node so it contains a consistent bounding box
            for (component in components) {
                root.minY = min(root.minY, component.minY)
                root.minX = min(root.minX, component.minX)
            }
            return root
        }

        /** Creates tree from sorted components (with range low and high inclusive)  */
        private fun createTree(
            components: Array<Component2D>, low: Int, high: Int, splitX: Boolean
        ): ComponentTree? {
            if (low > high) {
                return null
            }

            val mid = (low + high) ushr 1
            if (low < high) {
                val comparator: Comparator<Component2D>
                if (splitX) {
                    comparator =
                        Comparator { left: Component2D, right: Component2D ->
                            var ret: Int = Double.compare(left.minX, right.minX)
                            if (ret == 0) {
                                ret = Double.compare(left.maxX, right.maxX)
                            }
                            ret
                        }
                } else {
                    comparator =
                        Comparator { left: Component2D, right: Component2D ->
                            var ret: Int = Double.compare(left.minY, right.minY)
                            if (ret == 0) {
                                ret = Double.compare(left.maxY, right.maxY)
                            }
                            ret
                        }
                }
                ArrayUtil.select(
                    components,
                    low,
                    high + 1,
                    mid,
                    comparator
                )
            }
            val newNode = ComponentTree(components[mid], splitX)
            // find children
            newNode.left = createTree(components, low, mid - 1, !splitX)
            newNode.right = createTree(components, mid + 1, high, !splitX)

            // pull up max values to this node
            if (newNode.left != null) {
                newNode.maxX = max(newNode.maxX, newNode.left!!.maxX)
                newNode.maxY = max(newNode.maxY, newNode.left!!.maxY)
            }
            if (newNode.right != null) {
                newNode.maxX = max(newNode.maxX, newNode.right!!.maxX)
                newNode.maxY = max(newNode.maxY, newNode.right!!.maxY)
            }
            return newNode
        }
    }
}
