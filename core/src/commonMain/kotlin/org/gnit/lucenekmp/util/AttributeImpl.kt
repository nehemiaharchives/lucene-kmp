package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Cloneable

/**
 * Base class for Attributes that can be added to a [org.apache.lucene.util.AttributeSource].
 *
 *
 * Attributes are used to add data in a dynamic, yet type-safe way to a source of usually
 * streamed objects, e. g. a [org.apache.lucene.analysis.TokenStream].
 *
 *
 * All implementations must list all implemented [Attribute] interfaces in their `implements` clause. `AttributeSource` reflectively identifies all attributes and makes them
 * available to consumers like `TokenStream`s.
 */
abstract class AttributeImpl : Cloneable<AttributeImpl>, Attribute {
    /**
     * Clears the values in this AttributeImpl and resets it to its default value. If this
     * implementation implements more than one Attribute interface it clears all.
     */
    abstract fun clear()

    /**
     * Clears the values in this AttributeImpl and resets it to its value at the end of the field. If
     * this implementation implements more than one Attribute interface it clears all.
     *
     *
     * The default implementation simply calls [.clear]
     */
    open fun end() {
        clear()
    }

    /**
     * This method returns the current attribute values as a string in the following format by calling
     * the [.reflectWith] method:
     *
     *
     *  * *iff `prependAttClass=true`:* `"AttributeClass#key=value,AttributeClass#key=value"`
     *  * *iff `prependAttClass=false`:* `"key=value,key=value"`
     *
     *
     * @see .reflectWith
     */
    fun reflectAsString(prependAttClass: Boolean): String {
        val buffer = StringBuilder()
        reflectWith(
            AttributeReflector { attClass, key, value ->
                if (buffer.isNotEmpty()) {
                    buffer.append(',')
                }
                if (prependAttClass) {
                    buffer.append(attClass.qualifiedName).append('#')
                }
                buffer.append(key).append('=').append(if (value == null) "null" else value)
            })
        return buffer.toString()
    }

    /**
     * This method is for introspection of attributes, it should simply add the key/values this
     * attribute holds to the given [AttributeReflector].
     *
     *
     * Implementations look like this (e.g. for a combined attribute implementation):
     *
     * <pre class="prettyprint">
     * public void reflectWith(AttributeReflector reflector) {
     * reflector.reflect(CharTermAttribute.class, "term", term());
     * reflector.reflect(PositionIncrementAttribute.class, "positionIncrement", getPositionIncrement());
     * }
    </pre> *
     *
     *
     * If you implement this method, make sure that for each invocation, the same set of [ ] interfaces and keys are passed to [AttributeReflector.reflect] in the same
     * order, but possibly different values. So don't automatically exclude e.g. `null`
     * properties!
     *
     * @see .reflectAsString
     */
    abstract fun reflectWith(reflector: AttributeReflector)

    /**
     * Copies the values from this Attribute into the passed-in target attribute. The target
     * implementation must support all the Attributes this implementation supports.
     */
    abstract fun copyTo(target: AttributeImpl)

    /**
     * Creates a new instance of this AttributeImpl.
     * Subclasses should override this to return an empty instance of themselves.
     */
    protected abstract fun newInstance(): AttributeImpl

    /**
     * In most cases the clone is, and should be, deep in order to be able to properly capture the
     * state of all attributes.
     */
    override fun clone(): AttributeImpl {
        val clone = newInstance()
        copyTo(clone)
        return clone
    }
}
