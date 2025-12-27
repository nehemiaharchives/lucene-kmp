package org.tartarus.snowball

/** Parent class of all snowball stemmers, which must implement [stem]. */
abstract class SnowballStemmer : SnowballProgram() {
    abstract fun stem(): Boolean
}
