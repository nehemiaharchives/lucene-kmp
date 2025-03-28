package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Cloneable
import org.gnit.lucenekmp.jdkport.KClassValue
import kotlin.reflect.KClass
import kotlin.reflect.cast


/**
 * An AttributeSource contains a list of different [AttributeImpl]s, and methods to add and
 * get them. There can only be a single instance of an attribute in the same AttributeSource
 * instance. This is ensured by passing in the actual type of the Attribute (Class&lt;Attribute&gt;)
 * to the [.addAttribute], which then checks if an instance of that type is already
 * present. If yes, it returns the instance, otherwise it creates a new instance and returns it.
 */
open class AttributeSource {
    /**
     * This class holds the state of an AttributeSource.
     *
     * @see .captureState
     *
     * @see .restoreState
     */
    class State : Cloneable<State> {
        var attribute: AttributeImpl? = null
        var next: State? = null

        override fun clone(): State {
            val clone = State()

            clone.attribute = attribute?.clone()

            if (next != null) {
                clone.next = next!!.clone()
            }

            return clone
        }
    }

    // These two maps must always be in sync!!!
    // So they are private, final and read-only from the outside (read-only iterators)
    private val attributes: MutableMap<KClass<out Attribute>, AttributeImpl>
    private val attributeImpls: MutableMap<KClass<out AttributeImpl>, AttributeImpl>
    private val currentState: Array<State?>

    private val factory: AttributeFactory

    /**
     * An AttributeSource using the default attribute factory [ ][AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY].
     */
    constructor() : this(AttributeFactory.DEFAULT_ATTRIBUTE_FACTORY)

    /** An AttributeSource that uses the same attributes as the supplied one.  */
    constructor(input: AttributeSource) {
        /*java.util.Objects.requireNonNull<AttributeSource>(input, "input AttributeSource must not be null")*/
        this.attributes = input.attributes
        this.attributeImpls = input.attributeImpls
        this.currentState = input.currentState
        this.factory = input.factory
    }

    /**
     * An AttributeSource using the supplied [AttributeFactory] for creating new [ ] instances.
     */
    constructor(factory: AttributeFactory) {
        this.attributes = mutableMapOf()
        this.attributeImpls =  mutableMapOf()
        this.currentState = arrayOfNulls(1)
        this.factory = factory /*java.util.Objects.requireNonNull<AttributeFactory>(factory, "AttributeFactory must not be null")*/
    }

    val attributeFactory: AttributeFactory
        /** returns the used AttributeFactory.  */
        get() = this.factory

    val attributeClassesIterator: Iterator<Any>
        /**
         * Returns a new iterator that iterates the attribute classes in the same order they were added
         * in.
         */
        get() = (attributes.keys).iterator()

    val attributeImplsIterator: Iterator<AttributeImpl>
        /**
         * Returns a new iterator that iterates all unique Attribute implementations. This iterator may
         * contain less entries that [.getAttributeClassesIterator], if one instance implements more
         * than one Attribute interface.
         */
        get() {
            val initState = getCurrentState()
            if (initState != null) {
                return object : MutableIterator<AttributeImpl> {
                    private var state = initState

                    override fun remove() {
                        throw UnsupportedOperationException()
                    }

                    override fun next(): AttributeImpl {
                        if (state == null) throw NoSuchElementException()
                        val att: AttributeImpl? = state!!.attribute
                        state = state!!.next
                        return att!!
                    }

                    override fun hasNext(): Boolean {
                        return state != null
                    }
                }
            } else {
                return emptySet<AttributeImpl>().iterator()
            }
        }

    /**
     * **Expert:** Adds a custom AttributeImpl instance with one or more Attribute interfaces.
     *
     *
     * **NOTE:** It is not guaranteed, that `att` is added to the `
     * AttributeSource`, because the provided attributes may already exist. You should always
     * retrieve the wanted attributes using [.getAttribute] after adding with this method and
     * cast to your class. The recommended way to use custom implementations is using an [ ].
     *
     *
     * This method will only add the Attribute interfaces directly implemented by the class and its
     * super classes.
     */
    fun addAttributeImpl(att: AttributeImpl) {
        val clazz: KClass<out AttributeImpl> = att::class
        if (attributeImpls.containsKey(clazz)) return

        // add all interfaces of this AttributeImpl to the maps
        for (curInterface in getAttributeInterfaces(clazz)) {
            // Attribute is a superclass of this interface
            if (!attributes.containsKey(curInterface)) {
                // invalidate state to force recomputation in captureState()
                currentState[0] = null
                attributes[curInterface] = att
                attributeImpls[clazz] = att
            }
        }
    }

    /**
     * The caller must pass in a Class&lt;? extends Attribute&gt; value. This method first checks if
     * an instance of that class is already in this AttributeSource and returns it. Otherwise a new
     * instance is created, added to this AttributeSource and returned.
     */
    fun <T : Attribute> addAttribute(attClass: KClass<T>): T {
        var attImpl: AttributeImpl? = attributes[attClass]
        if (attImpl == null) {
            /*require(attClass.isInterface() && Attribute::class.java.isAssignableFrom(attClass)) {
                ("addAttribute() only accepts an interface that extends Attribute, but "
                        + attClass.getName()
                        + " does not fulfil this contract.")
            }*/
            addAttributeImpl(factory.createAttributeInstance(attClass).also { attImpl = it })
        }
        return attClass.cast(attImpl)
    }

    /** Returns true, iff this AttributeSource has any attributes  */
    fun hasAttributes(): Boolean {
        return !attributes.isEmpty()
    }

    /**
     * The caller must pass in a Class&lt;? extends Attribute&gt; value. Returns true, iff this
     * AttributeSource contains the passed-in Attribute.
     */
    fun hasAttribute(attClass: KClass<out Attribute>): Boolean {
        return attributes.containsKey(attClass)
    }

    /**
     * Returns the instance of the passed in Attribute contained in this AttributeSource
     *
     *
     * The caller must pass in a Class&lt;? extends Attribute&gt; value.
     *
     * @return instance of the passed in Attribute, or `null` if this AttributeSource does not
     * contain the Attribute. It is recommended to always use [.addAttribute] even in
     * consumers of TokenStreams, because you cannot know if a specific TokenStream really uses a
     * specific Attribute. [.addAttribute] will automatically make the attribute available.
     * If you want to only use the attribute, if it is available (to optimize consuming), use
     * [.hasAttribute].
     */
    fun <T : Attribute> getAttribute(attClass: KClass<T>): T {
        return attClass.cast(attributes[attClass])
    }

    private fun getCurrentState(): State? {
        var s = currentState[0]
        if (s != null || !hasAttributes()) {
            return s
        }
        currentState[0] = State()
        s = currentState[0]
        var c = s
        val it: Iterator<AttributeImpl> = attributeImpls.values.iterator()
        c?.attribute = it.next()
        while (it.hasNext()) {
            c?.next = State()
            c = c?.next!!
            c.attribute = it.next()
        }
        return s
    }

    /**
     * Resets all Attributes in this AttributeSource by calling [AttributeImpl.clear] on each
     * Attribute implementation.
     */
    fun clearAttributes() {
        var state = getCurrentState()
        while (state != null) {
            state.attribute!!.clear()
            state = state.next
        }
    }

    /**
     * Resets all Attributes in this AttributeSource by calling [AttributeImpl.end] on each
     * Attribute implementation.
     */
    fun endAttributes() {
        var state = getCurrentState()
        while (state != null) {
            state.attribute!!.end()
            state = state.next
        }
    }

    /** Removes all attributes and their implementations from this AttributeSource.  */
    fun removeAllAttributes() {
        attributes.clear()
        attributeImpls.clear()
    }

    /**
     * Captures the state of all Attributes. The return value can be passed to [.restoreState]
     * to restore the state of this or another AttributeSource.
     *
     *
     * Be careful, this method comes with a cost of deep copying all attributes in the source.
     */
    fun captureState(): State? {
        val state = this.getCurrentState()
        return state?.clone()
    }

    /**
     * Restores this state by copying the values of all attribute implementations that this state
     * contains into the attributes implementations of the targetStream. The targetStream must contain
     * a corresponding instance for each argument contained in this state (e.g. it is not possible to
     * restore the state of an AttributeSource containing a TermAttribute into a AttributeSource using
     * a Token instance as implementation).
     *
     *
     * Note that this method does not affect attributes of the targetStream that are not contained
     * in this state. In other words, if for example the targetStream contains an OffsetAttribute, but
     * this state doesn't, then the value of the OffsetAttribute remains unchanged. It might be
     * desirable to reset its value to the default, in which case the caller should first call [ ][TokenStream.clearAttributes] on the targetStream.
     */
    fun restoreState(state: State?) {
        var state: State? = state ?: return

        do {
            val targetImpl: AttributeImpl? = attributeImpls[state!!.attribute!!::class]
            requireNotNull(targetImpl) {
                ("State contains AttributeImpl of type "
                        + state!!.attribute!!::class.qualifiedName
                        + " that is not in in this AttributeSource")
            }
            state.attribute!!.copyTo(targetImpl)
            state = state.next
        } while (state != null)
    }

    override fun hashCode(): Int {
        var code = 0
        var state = getCurrentState()
        while (state != null) {
            code = code * 31 + state.attribute.hashCode()
            state = state.next
        }
        return code
    }

    override fun equals(obj: Any?): Boolean {
        if (obj === this) {
            return true
        }

        if (obj is AttributeSource) {
            if (hasAttributes()) {
                if (!obj.hasAttributes()) {
                    return false
                }

                if (attributeImpls.size != obj.attributeImpls.size) {
                    return false
                }

                // it is only equal if all attribute impls are the same in the same order
                var thisState = this.getCurrentState()
                var otherState = obj.getCurrentState()
                while (thisState != null && otherState != null) {
                    if (otherState.attribute!!::class !== thisState.attribute!!::class
                        || !otherState.attribute!!.equals(thisState.attribute)
                    ) {
                        return false
                    }
                    thisState = thisState.next
                    otherState = otherState.next
                }
                return true
            } else {
                return !obj.hasAttributes()
            }
        } else {
            return false
        }
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
            object : AttributeReflector {
                override fun reflect(attClass: KClass<out Attribute>, key: String, value: Any) {
                    if (buffer.length > 0) {
                        buffer.append(',')
                    }
                    if (prependAttClass) {
                        buffer.append(attClass.qualifiedName).append('#')
                    }
                    buffer.append(key).append('=').append(value ?: "null")
                }
            })
        return buffer.toString()
    }

    /**
     * This method is for introspection of attributes, it should simply add the key/values this
     * AttributeSource holds to the given [AttributeReflector].
     *
     *
     * This method iterates over all Attribute implementations and calls the corresponding [ ][AttributeImpl.reflectWith] method.
     *
     * @see AttributeImpl.reflectWith
     */
    fun reflectWith(reflector: AttributeReflector) {
        var state = getCurrentState()
        while (state != null) {
            state.attribute!!.reflectWith(reflector)
            state = state.next
        }
    }

    /**
     * Performs a clone of all [AttributeImpl] instances returned in a new `AttributeSource` instance. This method can be used to e.g. create another TokenStream with
     * exactly the same attributes (using [.AttributeSource]). You can also use
     * it as a (non-performant) replacement for [.captureState], if you need to look into /
     * modify the captured state.
     */
    fun cloneAttributes(): AttributeSource {
        val clone: AttributeSource = AttributeSource(this.factory)

        if (hasAttributes()) {
            // first clone the impls
            var state = getCurrentState()
            while (state != null) {
                clone.attributeImpls[state.attribute!!::class] = state.attribute!!.clone()
                state = state.next
            }

            // now the interfaces
            this.attributes.forEach { (key, value) ->
                clone.attributes[key] = clone.attributeImpls[value::class]!!
            }
        }

        return clone
    }

    /**
     * Copies the contents of this `AttributeSource` to the given target `AttributeSource`. The given instance has to provide all [Attribute]s this instance
     * contains. The actual attribute implementations must be identical in both `AttributeSource` instances; ideally both AttributeSource instances should use the same [ ]. You can use this method as a replacement for [.restoreState], if you
     * use [.cloneAttributes] instead of [.captureState].
     */
    fun copyTo(target: AttributeSource) {
        var state = getCurrentState()
        while (state != null) {
            val targetImpl: AttributeImpl? = target.attributeImpls[state.attribute!!::class]
            requireNotNull(targetImpl) {
                ("This AttributeSource contains AttributeImpl of type "
                        + state!!.attribute!!::class.qualifiedName
                        + " that is not in the target")
            }
            state.attribute!!.copyTo(targetImpl)
            state = state.next
        }
    }

    /**
     * Returns a string consisting of the class's simple name, the hex representation of the identity
     * hash code, and the current reflection of all attributes.
     *
     * @see .reflectAsString
     */
    @OptIn(ExperimentalStdlibApi::class)
    override fun toString(): String {
        return (this::class.simpleName
                + '@'
                + this.hashCode().toHexString()
                + " "
                + reflectAsString(false))
    }

    companion object {

        fun getInterfaces(clazz: KClass<*>?): Array<KClass<*>> {

            val array =

            // TODO hardcode all mappings of class/interface names

            return array
        }

        fun getSuperclass(clazz: KClass<*>?): KClass<*>? {

            // TODO hardcode all mappings of class/superclass names

            return clazz
        }

        /**
         * a cache that stores all interfaces for known implementation classes for performance (slow
         * reflection)
         */
        private val implInterfaces: KClassValue<Array<KClass<out Attribute>>> =
            object : KClassValue<Array<KClass<out Attribute>>>() {
                override fun computeValue(clazz: KClass<*>?): Array<KClass<out Attribute>> {
                    var clazz: KClass<*>? = clazz
                    val intfSet: MutableSet<KClass<out Attribute>> = mutableSetOf()
                    // find all interfaces that this attribute instance implements
                    // and that extend the Attribute interface
                    do {
                        for (curInterface in getInterfaces(clazz)) {
                            if (curInterface != Attribute::class
                                /*&& Attribute::class.java.isAssignableFrom(curInterface)*/
                            ) {
                                intfSet.add(curInterface as KClass<out Attribute>)
                            }
                        }
                        clazz = getSuperclass(clazz)
                    } while (clazz != null)
                    val a: Array<KClass<out Attribute>> = intfSet.toTypedArray()
                    return a
                }
            }

        fun getAttributeInterfaces(
            clazz: KClass<out AttributeImpl>
        ): Array<KClass<out Attribute>> {
            return implInterfaces.get(clazz)
        }
    }
}
