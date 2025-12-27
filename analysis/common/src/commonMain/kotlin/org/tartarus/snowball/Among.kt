package org.tartarus.snowball

/** Internal class used by Snowball stemmers. */
class Among {
    val s: CharArray
    val substringI: Int
    val result: Int
    val method: ((SnowballProgram) -> Boolean)?

    constructor(s: String, substringI: Int, result: Int) {
        this.s = s.toCharArray()
        this.substringI = substringI
        this.result = result
        this.method = null
    }

    constructor(s: String, substringI: Int, result: Int, method: (SnowballProgram) -> Boolean) {
        this.s = s.toCharArray()
        this.substringI = substringI
        this.result = result
        this.method = method
    }
}
