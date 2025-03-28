package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.index.StoredFieldDataInput


/**
 * Abstraction around a stored value.
 *
 * @see IndexableField
 */
class StoredValue {
    /** Type of a [StoredValue].  */
    enum class Type {
        /** Type of integer values.  */
        INTEGER,

        /** Type of long values.  */
        LONG,

        /** Type of float values.  */
        FLOAT,

        /** Type of double values.  */
        DOUBLE,

        /** Type of binary values.  */
        BINARY,

        /** Type of data input values.  */
        DATA_INPUT,

        /** Type of string values.  */
        STRING
    }

    /** Retrieve the type of the stored value.  */
    val type: Type
    private var intValue = 0
    private var longValue: Long = 0
    private var floatValue = 0f
    private var doubleValue = 0.0
    private lateinit var dataInput: StoredFieldDataInput
    private lateinit var binaryValue: BytesRef
    private lateinit var stringValue: String

    /** Ctor for integer values.  */
    constructor(value: Int) {
        type = Type.INTEGER
        intValue = value
    }

    /** Ctor for long values.  */
    constructor(value: Long) {
        type = Type.LONG
        longValue = value
    }

    /** Ctor for float values.  */
    constructor(value: Float) {
        type = Type.FLOAT
        floatValue = value
    }

    /** Ctor for double values.  */
    constructor(value: Double) {
        type = Type.DOUBLE
        doubleValue = value
    }

    /** Ctor for binary values.  */
    constructor(value: BytesRef) {
        type = Type.BINARY
        binaryValue = value
    }

    /** Ctor for data input values.  */
    constructor(value: StoredFieldDataInput) {
        type = Type.DATA_INPUT
        dataInput = value
    }

    /** Ctor for string values.  */
    constructor(value: String) {
        type = Type.STRING
        stringValue = value
    }

    /** Set an integer value.  */
    fun setIntValue(value: Int) {
        require(type == Type.INTEGER) { "Cannot set an integer on a " + type + " value" }
        intValue = value
    }

    /** Set a long value.  */
    fun setLongValue(value: Long) {
        require(type == Type.LONG) { "Cannot set a long on a " + type + " value" }
        longValue = value
    }

    /** Set a float value.  */
    fun setFloatValue(value: Float) {
        require(type == Type.FLOAT) { "Cannot set a float on a " + type + " value" }
        floatValue = value
    }

    /** Set a double value.  */
    fun setDoubleValue(value: Double) {
        require(type == Type.DOUBLE) { "Cannot set a double on a " + type + " value" }
        doubleValue = value
    }

    /** Set a binary value.  */
    fun setBinaryValue(value: BytesRef) {
        require(type == Type.BINARY) { "Cannot set a binary value on a " + type + " value" }
        binaryValue = value
    }

    /** Set a data input value.  */
    fun setDataInputValue(value: StoredFieldDataInput) {
        require(type == Type.DATA_INPUT) { "Cannot set a data input value on a " + type + " value" }
        dataInput = value
    }

    /** Set a string value.  */
    fun setStringValue(value: String) {
        require(type == Type.STRING) { "Cannot set a string value on a " + type + " value" }
        stringValue = value
    }

    /** Retrieve an integer value.  */
    fun getIntValue(): Int {
        require(type == Type.INTEGER) { "Cannot get an integer on a " + type + " value" }
        return intValue
    }

    /** Retrieve a long value.  */
    fun getLongValue(): Long {
        require(type == Type.LONG) { "Cannot get a long on a " + type + " value" }
        return longValue
    }

    /** Retrieve a float value.  */
    fun getFloatValue(): Float {
        require(type == Type.FLOAT) { "Cannot get a float on a " + type + " value" }
        return floatValue
    }

    /** Retrieve a double value.  */
    fun getDoubleValue(): Double {
        require(type == Type.DOUBLE) { "Cannot get a double on a " + type + " value" }
        return doubleValue
    }

    /** Retrieve a binary value.  */
    fun getBinaryValue(): BytesRef? {
        require(type == Type.BINARY) { "Cannot get a binary value on a " + type + " value" }
        return binaryValue
    }

    val dataInputValue: StoredFieldDataInput?
        /** Retrieve a data input value.  */
        get() {
            require(type == Type.DATA_INPUT) { "Cannot get a data input value on a " + type + " value" }
            return dataInput
        }

    /** Retrieve a string value.  */
    fun getStringValue(): String? {
        require(type == Type.STRING) { "Cannot get a string value on a " + type + " value" }
        return stringValue
    }
}
