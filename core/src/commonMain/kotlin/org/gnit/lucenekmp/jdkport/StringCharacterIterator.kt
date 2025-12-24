package org.gnit.lucenekmp.jdkport

import kotlin.jvm.JvmOverloads


/**
 * `StringCharacterIterator` implements the
 * `CharacterIterator` protocol for a `String`.
 * The `StringCharacterIterator` class iterates over the
 * entire `String`. All constructors throw `NullPointerException`
 * if `text` is `null`.
 *
 * @see CharacterIterator
 *
 * @since 1.1
 */
@Ported(from = "java.text.StringCharacterIterator")
class StringCharacterIterator(text: String, begin: Int, end: Int, pos: Int) : CharacterIterator {
    private var text: String

    /**
     * Implements CharacterIterator.getBeginIndex() for String.
     * @see CharacterIterator.getBeginIndex
     */
    override var beginIndex: Int

    /**
     * Implements CharacterIterator.getEndIndex() for String.
     * @see CharacterIterator.getEndIndex
     */
    override var endIndex: Int

    /**
     * Implements CharacterIterator.getIndex() for String.
     * @see CharacterIterator.getIndex
     */
    // invariant: begin <= pos <= end
    override var index: Int

    /**
     * Constructs an iterator with the specified initial index.
     *
     * @param  text   The String to be iterated over
     * @param  pos    Initial iterator position
     * @throws IllegalArgumentException if `pos` is not within the bounds of
     * range (inclusive) from `0` to the length of `text`
     */
    /**
     * Constructs an iterator with an initial index of 0.
     *
     * @param text the `String` to be iterated over
     */
    @JvmOverloads
    constructor(text: String, pos: Int = 0) : this(text, 0, text.length, pos)

    /**
     * Constructs an iterator over the given range of the given string, with the
     * index set at the specified position.
     *
     * @param  text   The String to be iterated over
     * @param  begin  Index of the first character
     * @param  end    Index of the character following the last character
     * @param  pos    Initial iterator position
     * @throws IllegalArgumentException if `begin` and `end` are not
     * within the bounds of range (inclusive) from `0` to the length of `text`,
     * `begin` is greater than `end`, or `pos` is not within
     * the bounds of range (inclusive) from `begin` to `end`
     */
    init {
        this.text = text

        require(!(begin < 0 || begin > end || end > text.length)) { "Invalid substring range" }

        require(!(pos < begin || pos > end)) { "Invalid position" }

        this.beginIndex = begin
        this.endIndex = end
        this.index = pos
    }

    /**
     * Reset this iterator to point to a new string.  This package-visible
     * method is used by other java.text classes that want to avoid allocating
     * new StringCharacterIterator objects every time their setText method
     * is called.
     *
     * @param  text   The String to be iterated over
     * @throws NullPointerException if `text` is `null`
     * @since 1.2
     */
    fun setText(text: String) {
        this.text = text
        this.beginIndex = 0
        this.endIndex = text.length
        this.index = 0
    }

    /**
     * Implements CharacterIterator.first() for String.
     * @see CharacterIterator.first
     */
    override fun first(): Char {
        this.index = this.beginIndex
        return current()
    }

    /**
     * Implements CharacterIterator.last() for String.
     * @see CharacterIterator.last
     */
    override fun last(): Char {
        if (this.endIndex != this.beginIndex) {
            this.index = this.endIndex - 1
        } else {
            this.index = this.endIndex
        }
        return current()
    }

    /**
     * Implements CharacterIterator.setIndex() for String.
     *
     * @throws IllegalArgumentException if `p` is not within the bounds
     * (inclusive) of [.getBeginIndex] to [.getEndIndex]
     * @see CharacterIterator.setIndex
     */
    override fun setIndex(p: Int): Char {
        require(!(p < this.beginIndex || p > this.endIndex)) { "Invalid index" }
        this.index = p
        return current()
    }

    /**
     * Implements CharacterIterator.current() for String.
     * @see CharacterIterator.current
     */
    override fun current(): Char {
        if (this.index >= this.beginIndex && this.index < this.endIndex) {
            return text.get(this.index)
        } else {
            return CharacterIterator.Companion.DONE
        }
    }

    /**
     * Implements CharacterIterator.next() for String.
     * @see CharacterIterator.next
     */
    override fun next(): Char {
        if (this.index < this.endIndex - 1) {
            this.index++
            return text.get(this.index)
        } else {
            this.index = this.endIndex
            return CharacterIterator.Companion.DONE
        }
    }

    /**
     * Implements CharacterIterator.previous() for String.
     * @see CharacterIterator.previous
     */
    override fun previous(): Char {
        if (this.index > this.beginIndex) {
            this.index--
            return text.get(this.index)
        } else {
            return CharacterIterator.Companion.DONE
        }
    }

    /**
     * Compares the equality of two StringCharacterIterator objects.
     * @param obj the StringCharacterIterator object to be compared with.
     * @return true if the given obj is the same as this
     * StringCharacterIterator object; false otherwise.
     */
    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj !is StringCharacterIterator) return false

        if (hashCode() != obj.hashCode()) return false
        if (text != obj.text) return false
        if (this.index != obj.index || this.beginIndex != obj.beginIndex || this.endIndex != obj.endIndex) return false
        return true
    }

    /**
     * Computes a hashcode for this iterator.
     * @return A hash code
     */
    override fun hashCode(): Int {
        return text.hashCode() xor this.index xor this.beginIndex xor this.endIndex
    }

    /**
     * Creates a copy of this iterator.
     * @return A copy of this
     */
    override fun clone(): Any {
        // String is immutable; copying fields is enough for a correct clone.
        return StringCharacterIterator(text, beginIndex, endIndex, index)
    }
}
