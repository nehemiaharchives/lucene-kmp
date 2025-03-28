package org.gnit.lucenekmp.util

import kotlin.reflect.KClass

/**
 * This interface is used to reflect contents of [AttributeSource] or [AttributeImpl].
 */
fun interface AttributeReflector {
    /**
     * This method gets called for every property in an [AttributeImpl]/[AttributeSource]
     * passing the class name of the [Attribute], a key and the actual value. E.g., an
     * invocation of [ ][org.apache.lucene.analysis.tokenattributes.CharTermAttributeImpl.reflectWith] would call this
     * method once using `org.apache.lucene.analysis.tokenattributes.CharTermAttribute.class` as
     * attribute class, `"term"` as key and the actual value as a String.
     */
    fun reflect(attClass: KClass<out Attribute>, key: String, value: Any)
}
