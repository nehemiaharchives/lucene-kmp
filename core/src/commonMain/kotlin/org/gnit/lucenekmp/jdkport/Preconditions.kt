package org.gnit.lucenekmp.jdkport

import okio.ArrayIndexOutOfBoundsException

/**
 * Utility methods to check if state or arguments are correct.
 *
 */
@Ported(from = "jdk.internal.util.Preconditions")
object Preconditions {
    /**
     * Utility exception formatters which can be used in `Preconditions`
     * check functions below.
     *
     * These anonymous inner classes can be syntactically replaced by lambda
     * expression or method reference, but it's not feasible in practices,
     * because `Preconditions` is used in many fundamental classes such
     * as `java.lang.String`, lambda expressions or method references
     * exercise many other code at VM startup, this could lead a recursive
     * calls when fundamental classes is used in lambda expressions or method
     * references.
     */
    val SIOOBE_FORMATTER: (String, MutableList<Number>) -> StringIndexOutOfBoundsException /*BiFunction<String, MutableList<Number>, StringIndexOutOfBoundsException>*/ =
        outOfBoundsExceptionFormatter { s: String ->
            StringIndexOutOfBoundsException(s)
        }

    val AIOOBE_FORMATTER: (String, MutableList<Number>) -> ArrayIndexOutOfBoundsException /*BiFunction<String, MutableList<Number>, ArrayIndexOutOfBoundsException>*/ =
        outOfBoundsExceptionFormatter { s: String ->
            ArrayIndexOutOfBoundsException(s)
        }

    val IOOBE_FORMATTER: (String, MutableList<Number>) -> IndexOutOfBoundsException /*BiFunction<String, MutableList<Number>, IndexOutOfBoundsException>*/ =
        outOfBoundsExceptionFormatter { s: String ->
            IndexOutOfBoundsException(s)
        }

    /**
     * Maps out-of-bounds values to a runtime exception.
     *
     * @param checkKind the kind of bounds check, whose name may correspond
     * to the name of one of the range check methods, checkIndex,
     * checkFromToIndex, checkFromIndexSize
     * @param args the out-of-bounds arguments that failed the range check.
     * If the checkKind corresponds to the name of a range check method
     * then the bounds arguments are those that can be passed in order
     * to the method.
     * @param oobef the exception formatter that when applied with a checkKind
     * and a list out-of-bounds arguments returns a runtime exception.
     * If `null` then, it is as if an exception formatter was
     * supplied that returns [IndexOutOfBoundsException] for any
     * given arguments.
     * @return the runtime exception
     */
    private fun outOfBounds(
        oobef: (String, MutableList<Number>) -> RuntimeException /*BiFunction<String, MutableList<Number>, out RuntimeException>*/,
        checkKind: String,
        vararg args: Number
    ): RuntimeException {
        val largs: MutableList<Number> = args.toMutableList()
        val e: RuntimeException = oobef(checkKind, largs)

        return if (e == null)
            IndexOutOfBoundsException(outOfBoundsMessage(checkKind, largs))
        else
            e
    }

    private fun outOfBoundsCheckIndex(
        oobe: (String, MutableList<Number>) -> RuntimeException /*BiFunction<String, MutableList<Number>, out RuntimeException>*/,
        index: Int, length: Int
    ): RuntimeException {
        return outOfBounds(oobe, "checkIndex", index, length)
    }

    private fun outOfBoundsCheckFromToIndex(
        oobe: (String, MutableList<Number>) -> RuntimeException /*BiFunction<String, MutableList<Number>, out RuntimeException>*/,
        fromIndex: Int, toIndex: Int, length: Int
    ): RuntimeException {
        return outOfBounds(oobe, "checkFromToIndex", fromIndex, toIndex, length)
    }

    private fun outOfBoundsCheckFromIndexSize(
        oobe: (String, MutableList<Number>) -> RuntimeException /*BiFunction<String, MutableList<Number>, out RuntimeException>*/,
        fromIndex: Int, size: Int, length: Int
    ): RuntimeException {
        return outOfBounds(oobe, "checkFromIndexSize", fromIndex, size, length)
    }

    private fun outOfBoundsCheckIndex(
        oobe: (String, MutableList<Number>) -> RuntimeException /*BiFunction<String, MutableList<Number>, out RuntimeException>*/,
        index: Long, length: Long
    ): RuntimeException {
        return outOfBounds(oobe, "checkIndex", index, length)
    }

    private fun outOfBoundsCheckFromToIndex(
        oobe: (String, MutableList<Number>) -> RuntimeException /*BiFunction<String, MutableList<Number>, out RuntimeException>*/,
        fromIndex: Long, toIndex: Long, length: Long
    ): RuntimeException {
        return outOfBounds(oobe, "checkFromToIndex", fromIndex, toIndex, length)
    }

    private fun outOfBoundsCheckFromIndexSize(
        oobe: (String, MutableList<Number>) -> RuntimeException /*BiFunction<String, MutableList<Number>, out RuntimeException>*/,
        fromIndex: Long, size: Long, length: Long
    ): RuntimeException {
        return outOfBounds(oobe, "checkFromIndexSize", fromIndex, size, length)
    }

    /**
     * Returns an out-of-bounds exception formatter from an given exception
     * factory.  The exception formatter is a function that formats an
     * out-of-bounds message from its arguments and applies that message to the
     * given exception factory to produce and relay an exception.
     *
     *
     * The exception formatter accepts two arguments: a `String`
     * describing the out-of-bounds range check that failed, referred to as the
     * *check kind*; and a `List<Number>` containing the
     * out-of-bound integral values that failed the check.  The list of
     * out-of-bound values is not modified.
     *
     *
     * Three check kinds are supported `checkIndex`,
     * `checkFromToIndex` and `checkFromIndexSize` corresponding
     * respectively to the specified application of an exception formatter as an
     * argument to the out-of-bounds range check methods
     * [checkIndex][.checkIndex],
     * [checkFromToIndex][.checkFromToIndex], and
     * [checkFromIndexSize][.checkFromIndexSize].
     * Thus a supported check kind corresponds to a method name and the
     * out-of-bound integral values correspond to a method argument values, in
     * order, preceding the exception formatter argument (similar in many
     * respects to the form of arguments required for a reflective invocation of
     * such a range check method).
     *
     *
     * Formatter arguments conforming to such supported check kinds will
     * produce specific exception messages describing failed out-of-bounds
     * checks.  Otherwise, more generic exception messages will be produced in
     * any of the following cases: the check kind is supported but fewer
     * or more out-of-bounds values are supplied, the check kind is not
     * supported, the check kind is `null`, or the list of out-of-bound
     * values is `null`.
     *
     * @apiNote
     * This method produces an out-of-bounds exception formatter that can be
     * passed as an argument to any of the supported out-of-bounds range check
     * methods declared by `Objects`.  For example, a formatter producing
     * an `ArrayIndexOutOfBoundsException` may be produced and stored on a
     * `static final` field as follows:
     * <pre>`static final
     * BiFunction<String, List<Number>, ArrayIndexOutOfBoundsException> AIOOBEF =
     * outOfBoundsExceptionFormatter(ArrayIndexOutOfBoundsException::new);
    `</pre> *
     * The formatter instance `AIOOBEF` may be passed as an argument to an
     * out-of-bounds range check method, such as checking if an `index`
     * is within the bounds of a `limit`:
     * <pre>`checkIndex(index, limit, AIOOBEF);
    `</pre> *
     * If the bounds check fails then the range check method will throw an
     * `ArrayIndexOutOfBoundsException` with an appropriate exception
     * message that is a produced from `AIOOBEF` as follows:
     * <pre>`AIOOBEF.apply("checkIndex", List.of(index, limit));
    `</pre> *
     *
     * @param f the exception factory, that produces an exception from a message
     * where the message is produced and formatted by the returned
     * exception formatter.  If this factory is stateless and side-effect
     * free then so is the returned formatter.
     * Exceptions thrown by the factory are relayed to the caller
     * of the returned formatter.
     * @param <X> the type of runtime exception to be returned by the given
     * exception factory and relayed by the exception formatter
     * @return the out-of-bounds exception formatter
    </X> */
    fun <X : RuntimeException>
            outOfBoundsExceptionFormatter(f: (String) -> X /*java.util.function.Function<String, X>*/): (String, MutableList<Number>) -> X /*BiFunction<String, MutableList<Number>, X>*/ {
        // Use anonymous class to avoid bootstrap issues if this method is
        // used early in startup
        return { checkKind: String, args: MutableList<Number> -> f(outOfBoundsMessage(checkKind, args)) }
    }

    private fun outOfBoundsMessage(checkKind: String, args: MutableList<out Number>): String {
        if (checkKind == null && args == null) {
            return "Range check failed"
        } else if (checkKind == null) {
            return "Range check failed: $args"
        } else if (args == null) {
            return "Range check failed: $checkKind"
        }

        var argSize = 0
        when (checkKind) {
            "checkIndex" -> argSize = 2
            "checkFromToIndex", "checkFromIndexSize" -> argSize = 3
            else -> {}
        }

        // Switch to default if fewer or more arguments than required are supplied
        when (if (args.size != argSize) "" else checkKind) {
            "checkIndex" -> return "Index ${args[0]} out of bounds for length ${args[1]}"

            "checkFromToIndex" -> return "Range [${args[0]}, ${args[1]}) out of bounds for length ${args[2]}"

            "checkFromIndexSize" -> return "Range [${args[0]}, ${args[0]} + ${args[1]}) out of bounds for length ${args[2]}"

            else -> return "Range check failed: $checkKind $args"
        }
    }

    /**
     * Checks if the `index` is within the bounds of the range from
     * `0` (inclusive) to `length` (exclusive).
     *
     *
     * The `index` is defined to be out of bounds if any of the
     * following inequalities is true:
     *
     *  * `index < 0`
     *  * `index >= length`
     *  * `length < 0`, which is implied from the former inequalities
     *
     *
     *
     * If the `index` is out of bounds, then a runtime exception is
     * thrown that is the result of applying the following arguments to the
     * exception formatter: the name of this method, `checkIndex`;
     * and an unmodifiable list of integers whose values are, in order, the
     * out-of-bounds arguments `index` and `length`.
     *
     * @param <X> the type of runtime exception to throw if the arguments are
     * out of bounds
     * @param index the index
     * @param length the upper-bound (exclusive) of the range
     * @param oobef the exception formatter that when applied with this
     * method name and out-of-bounds arguments returns a runtime
     * exception.  If `null` or returns `null` then, it is as
     * if an exception formatter produced from an invocation of
     * `outOfBoundsExceptionFormatter(IndexOutOfBounds::new)` is used
     * instead (though it may be more efficient).
     * Exceptions thrown by the formatter are relayed to the caller.
     * @return `index` if it is within bounds of the range
     * @throws X if the `index` is out of bounds and the exception
     * formatter is non-`null`
     * @throws IndexOutOfBoundsException if the `index` is out of bounds
     * and the exception formatter is `null`
     * @since 9
     *
     * @implNote
     * This method is made intrinsic in optimizing compilers to guide them to
     * perform unsigned comparisons of the index and length when it is known the
     * length is a non-negative value (such as that of an array length or from
     * the upper bound of a loop)
    </X> */
    /*@IntrinsicCandidate*/
    fun <X : RuntimeException>
            checkIndex(
        index: Int, length: Int,
        oobef: (String, MutableList<Number>) -> X /*BiFunction<String, MutableList<Number>, X>*/
    ): Int {
        if (index < 0 || index >= length) throw outOfBoundsCheckIndex(oobef, index, length)
        return index
    }

    /**
     * Checks if the sub-range from `fromIndex` (inclusive) to
     * `toIndex` (exclusive) is within the bounds of range from `0`
     * (inclusive) to `length` (exclusive).
     *
     *
     * The sub-range is defined to be out of bounds if any of the following
     * inequalities is true:
     *
     *  * `fromIndex < 0`
     *  * `fromIndex > toIndex`
     *  * `toIndex > length`
     *  * `length < 0`, which is implied from the former inequalities
     *
     *
     *
     * If the sub-range is out of bounds, then a runtime exception is
     * thrown that is the result of applying the following arguments to the
     * exception formatter: the name of this method, `checkFromToIndex`;
     * and an unmodifiable list of integers whose values are, in order, the
     * out-of-bounds arguments `fromIndex`, `toIndex`, and `length`.
     *
     * @param <X> the type of runtime exception to throw if the arguments are
     * out of bounds
     * @param fromIndex the lower-bound (inclusive) of the sub-range
     * @param toIndex the upper-bound (exclusive) of the sub-range
     * @param length the upper-bound (exclusive) the range
     * @param oobef the exception formatter that when applied with this
     * method name and out-of-bounds arguments returns a runtime
     * exception.  If `null` or returns `null` then, it is as
     * if an exception formatter produced from an invocation of
     * `outOfBoundsExceptionFormatter(IndexOutOfBounds::new)` is used
     * instead (though it may be more efficient).
     * Exceptions thrown by the formatter are relayed to the caller.
     * @return `fromIndex` if the sub-range within bounds of the range
     * @throws X if the sub-range is out of bounds and the exception factory
     * function is non-`null`
     * @throws IndexOutOfBoundsException if the sub-range is out of bounds and
     * the exception factory function is `null`
     * @since 9
    </X> */
    fun <X : RuntimeException>
            checkFromToIndex(
        fromIndex: Int, toIndex: Int, length: Int,
        oobef: (String, MutableList<Number>) -> X /*BiFunction<String, MutableList<Number>, X>*/
    ): Int {
        if (fromIndex < 0 || fromIndex > toIndex || toIndex > length) throw outOfBoundsCheckFromToIndex(
            oobef,
            fromIndex,
            toIndex,
            length
        )
        return fromIndex
    }

    /**
     * Checks if the sub-range from `fromIndex` (inclusive) to
     * `fromIndex + size` (exclusive) is within the bounds of range from
     * `0` (inclusive) to `length` (exclusive).
     *
     *
     * The sub-range is defined to be out of bounds if any of the following
     * inequalities is true:
     *
     *  * `fromIndex < 0`
     *  * `size < 0`
     *  * `fromIndex + size > length`, taking into account integer overflow
     *  * `length < 0`, which is implied from the former inequalities
     *
     *
     *
     * If the sub-range is out of bounds, then a runtime exception is
     * thrown that is the result of applying the following arguments to the
     * exception formatter: the name of this method, `checkFromIndexSize`;
     * and an unmodifiable list of integers whose values are, in order, the
     * out-of-bounds arguments `fromIndex`, `size`, and
     * `length`.
     *
     * @param <X> the type of runtime exception to throw if the arguments are
     * out of bounds
     * @param fromIndex the lower-bound (inclusive) of the sub-interval
     * @param size the size of the sub-range
     * @param length the upper-bound (exclusive) of the range
     * @param oobef the exception formatter that when applied with this
     * method name and out-of-bounds arguments returns a runtime
     * exception.  If `null` or returns `null` then, it is as
     * if an exception formatter produced from an invocation of
     * `outOfBoundsExceptionFormatter(IndexOutOfBounds::new)` is used
     * instead (though it may be more efficient).
     * Exceptions thrown by the formatter are relayed to the caller.
     * @return `fromIndex` if the sub-range within bounds of the range
     * @throws X if the sub-range is out of bounds and the exception factory
     * function is non-`null`
     * @throws IndexOutOfBoundsException if the sub-range is out of bounds and
     * the exception factory function is `null`
     * @since 9
    </X> */
    fun <X : RuntimeException>
            checkFromIndexSize(
        fromIndex: Int, size: Int, length: Int,
        oobef: (String, MutableList<Number>) -> X /*BiFunction<String, MutableList<Number>, X>*/
    ): Int {
        if ((length or fromIndex or size) < 0 || size > length - fromIndex) throw outOfBoundsCheckFromIndexSize(
            oobef,
            fromIndex,
            size,
            length
        )
        return fromIndex
    }

    /**
     * Checks if the `index` is within the bounds of the range from
     * `0` (inclusive) to `length` (exclusive).
     *
     *
     * The `index` is defined to be out of bounds if any of the
     * following inequalities is true:
     *
     *  * `index < 0`
     *  * `index >= length`
     *  * `length < 0`, which is implied from the former inequalities
     *
     *
     *
     * If the `index` is out of bounds, then a runtime exception is
     * thrown that is the result of applying the following arguments to the
     * exception formatter: the name of this method, `checkIndex`;
     * and an unmodifiable list of longs whose values are, in order, the
     * out-of-bounds arguments `index` and `length`.
     *
     * @param <X> the type of runtime exception to throw if the arguments are
     * out of bounds
     * @param index the index
     * @param length the upper-bound (exclusive) of the range
     * @param oobef the exception formatter that when applied with this
     * method name and out-of-bounds arguments returns a runtime
     * exception.  If `null` or returns `null` then, it is as
     * if an exception formatter produced from an invocation of
     * `outOfBoundsExceptionFormatter(IndexOutOfBounds::new)` is used
     * instead (though it may be more efficient).
     * Exceptions thrown by the formatter are relayed to the caller.
     * @return `index` if it is within bounds of the range
     * @throws X if the `index` is out of bounds and the exception
     * formatter is non-`null`
     * @throws IndexOutOfBoundsException if the `index` is out of bounds
     * and the exception formatter is `null`
     * @since 16
     *
     * @implNote
     * This method is made intrinsic in optimizing compilers to guide them to
     * perform unsigned comparisons of the index and length when it is known the
     * length is a non-negative value (such as that of an array length or from
     * the upper bound of a loop)
    </X> */
    /*@IntrinsicCandidate*/
    fun <X : RuntimeException>
            checkIndex(
        index: Long, length: Long,
        oobef: (String, MutableList<Number>) -> X /*BiFunction<String, MutableList<Number>, X>*/
    ): Long {
        if (index < 0 || index >= length) throw outOfBoundsCheckIndex(oobef, index, length)
        return index
    }

    /**
     * Checks if the sub-range from `fromIndex` (inclusive) to
     * `toIndex` (exclusive) is within the bounds of range from `0`
     * (inclusive) to `length` (exclusive).
     *
     *
     * The sub-range is defined to be out of bounds if any of the following
     * inequalities is true:
     *
     *  * `fromIndex < 0`
     *  * `fromIndex > toIndex`
     *  * `toIndex > length`
     *  * `length < 0`, which is implied from the former inequalities
     *
     *
     *
     * If the sub-range is out of bounds, then a runtime exception is
     * thrown that is the result of applying the following arguments to the
     * exception formatter: the name of this method, `checkFromToIndex`;
     * and an unmodifiable list of longs whose values are, in order, the
     * out-of-bounds arguments `fromIndex`, `toIndex`, and `length`.
     *
     * @param <X> the type of runtime exception to throw if the arguments are
     * out of bounds
     * @param fromIndex the lower-bound (inclusive) of the sub-range
     * @param toIndex the upper-bound (exclusive) of the sub-range
     * @param length the upper-bound (exclusive) the range
     * @param oobef the exception formatter that when applied with this
     * method name and out-of-bounds arguments returns a runtime
     * exception.  If `null` or returns `null` then, it is as
     * if an exception formatter produced from an invocation of
     * `outOfBoundsExceptionFormatter(IndexOutOfBounds::new)` is used
     * instead (though it may be more efficient).
     * Exceptions thrown by the formatter are relayed to the caller.
     * @return `fromIndex` if the sub-range within bounds of the range
     * @throws X if the sub-range is out of bounds and the exception factory
     * function is non-`null`
     * @throws IndexOutOfBoundsException if the sub-range is out of bounds and
     * the exception factory function is `null`
     * @since 16
    </X> */
    fun <X : RuntimeException>
            checkFromToIndex(
        fromIndex: Long, toIndex: Long, length: Long,
        oobef: (String, MutableList<Number>) -> X /*BiFunction<String, MutableList<Number>, X>*/
    ): Long {
        if (fromIndex < 0 || fromIndex > toIndex || toIndex > length) throw outOfBoundsCheckFromToIndex(
            oobef,
            fromIndex,
            toIndex,
            length
        )
        return fromIndex
    }

    /**
     * Checks if the sub-range from `fromIndex` (inclusive) to
     * `fromIndex + size` (exclusive) is within the bounds of range from
     * `0` (inclusive) to `length` (exclusive).
     *
     *
     * The sub-range is defined to be out of bounds if any of the following
     * inequalities is true:
     *
     *  * `fromIndex < 0`
     *  * `size < 0`
     *  * `fromIndex + size > length`, taking into account integer overflow
     *  * `length < 0`, which is implied from the former inequalities
     *
     *
     *
     * If the sub-range is out of bounds, then a runtime exception is
     * thrown that is the result of applying the following arguments to the
     * exception formatter: the name of this method, `checkFromIndexSize`;
     * and an unmodifiable list of longs whose values are, in order, the
     * out-of-bounds arguments `fromIndex`, `size`, and
     * `length`.
     *
     * @param <X> the type of runtime exception to throw if the arguments are
     * out of bounds
     * @param fromIndex the lower-bound (inclusive) of the sub-interval
     * @param size the size of the sub-range
     * @param length the upper-bound (exclusive) of the range
     * @param oobef the exception formatter that when applied with this
     * method name and out-of-bounds arguments returns a runtime
     * exception.  If `null` or returns `null` then, it is as
     * if an exception formatter produced from an invocation of
     * `outOfBoundsExceptionFormatter(IndexOutOfBounds::new)` is used
     * instead (though it may be more efficient).
     * Exceptions thrown by the formatter are relayed to the caller.
     * @return `fromIndex` if the sub-range within bounds of the range
     * @throws X if the sub-range is out of bounds and the exception factory
     * function is non-`null`
     * @throws IndexOutOfBoundsException if the sub-range is out of bounds and
     * the exception factory function is `null`
     * @since 16
    </X> */
    fun <X : RuntimeException>
            checkFromIndexSize(
        fromIndex: Long, size: Long, length: Long,
        oobef: (String, MutableList<Number>) -> X /*BiFunction<String, MutableList<Number>, X>*/
    ): Long {
        if ((length or fromIndex or size) < 0 || size > length - fromIndex) throw outOfBoundsCheckFromIndexSize(
            oobef,
            fromIndex,
            size,
            length
        )
        return fromIndex
    }
}
