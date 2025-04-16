package org.gnit.lucenekmp.jdkport


/**
 * ported from java.lang.System, only contains things needed for lucenekmp
 *
 * The {@code System} class contains several useful class fields
 * and methods. It cannot be instantiated.
 *
 * Among the facilities provided by the {@code System} class
 * are standard input, standard output, and error output streams;
 * access to externally defined properties and environment
 * variables; a means of loading files and libraries; and a utility
 * method for quickly copying a portion of an array.
 *
 * @since   1.0
 */
object System {
    /**
     * ported from java.lang.System.arraycopy()
     *
     * Copies an array from the specified source array, beginning at the
     * specified position, to the specified position of the destination array.
     * A subsequence of array components are copied from the source
     * array referenced by `src` to the destination array
     * referenced by `dest`. The number of components copied is
     * equal to the `length` argument. The components at
     * positions `srcPos` through
     * `srcPos+length-1` in the source array are copied into
     * positions `destPos` through
     * `destPos+length-1`, respectively, of the destination
     * array.
     *
     *
     * If the `src` and `dest` arguments refer to the
     * same array object, then the copying is performed as if the
     * components at positions `srcPos` through
     * `srcPos+length-1` were first copied to a temporary
     * array with `length` components and then the contents of
     * the temporary array were copied into positions
     * `destPos` through `destPos+length-1` of the
     * destination array.
     *
     *
     * If `dest` is `null`, then a
     * `NullPointerException` is thrown.
     *
     *
     * If `src` is `null`, then a
     * `NullPointerException` is thrown and the destination
     * array is not modified.
     *
     *
     * Otherwise, if any of the following is true, an
     * `ArrayStoreException` is thrown and the destination is
     * not modified:
     *
     *  * The `src` argument refers to an object that is not an
     * array.
     *  * The `dest` argument refers to an object that is not an
     * array.
     *  * The `src` argument and `dest` argument refer
     * to arrays whose component types are different primitive types.
     *  * The `src` argument refers to an array with a primitive
     * component type and the `dest` argument refers to an array
     * with a reference component type.
     *  * The `src` argument refers to an array with a reference
     * component type and the `dest` argument refers to an array
     * with a primitive component type.
     *
     *
     *
     * Otherwise, if any of the following is true, an
     * `IndexOutOfBoundsException` is
     * thrown and the destination is not modified:
     *
     *  * The `srcPos` argument is negative.
     *  * The `destPos` argument is negative.
     *  * The `length` argument is negative.
     *  * `srcPos+length` is greater than
     * `src.length`, the length of the source array.
     *  * `destPos+length` is greater than
     * `dest.length`, the length of the destination array.
     *
     *
     *
     * Otherwise, if any actual component of the source array from
     * position `srcPos` through
     * `srcPos+length-1` cannot be converted to the component
     * type of the destination array by assignment conversion, an
     * `ArrayStoreException` is thrown. In this case, let
     * ***k*** be the smallest nonnegative integer less than
     * length such that `src[srcPos+`*k*`]`
     * cannot be converted to the component type of the destination
     * array; when the exception is thrown, source array components from
     * positions `srcPos` through
     * `srcPos+`*k*`-1`
     * will already have been copied to destination array positions
     * `destPos` through
     * `destPos+`*k*`-1` and no other
     * positions of the destination array will have been modified.
     * (Because of the restrictions already itemized, this
     * paragraph effectively applies only to the situation where both
     * arrays have component types that are reference types.)
     *
     * @param      src      the source array.
     * @param      srcPos   starting position in the source array.
     * @param      dest     the destination array.
     * @param      destPos  starting position in the destination data.
     * @param      length   the number of array elements to be copied.
     * @throws     IndexOutOfBoundsException  if copying would cause
     * access of data outside array bounds.
     * @throws     ArrayStoreException  if an element in the `src`
     * array could not be stored into the `dest` array
     * because of a type mismatch.
     * @throws     NullPointerException if either `src` or
     * `dest` is `null`.
     */

    fun arraycopy(src: Array<Any>, srcPos: Int, dest: Array<Any>, destPos: Int, length: Int) {
        src.copyInto(
            destination = dest,
            destinationOffset = destPos,
            startIndex = srcPos,
            endIndex = srcPos + length
        )
    }

    fun arraycopy(src: Array<Any?>, srcPos: Int, dest: Array<Any?>, destPos: Int, length: Int) {
        src.copyInto(
            destination = dest,
            destinationOffset = destPos,
            startIndex = srcPos,
            endIndex = srcPos + length
        )
    }

    fun arraycopy(src: LongArray, srcPos: Int, dest: LongArray, destPos: Int, length: Int) {
        src.copyInto(
            destination = dest,
            destinationOffset = destPos,
            startIndex = srcPos,
            endIndex = srcPos + length
        )
    }
}
