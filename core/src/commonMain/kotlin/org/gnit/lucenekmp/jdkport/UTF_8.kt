package org.gnit.lucenekmp.jdkport


/* Legal UTF-8 Byte Sequences
 *
 * #    Code Points      Bits   Bit/Byte pattern
 * 1                     7      0xxxxxxx
 *      U+0000..U+007F          00..7F
 *
 * 2                     11     110xxxxx    10xxxxxx
 *      U+0080..U+07FF          C2..DF      80..BF
 *
 * 3                     16     1110xxxx    10xxxxxx    10xxxxxx
 *      U+0800..U+0FFF          E0          A0..BF      80..BF
 *      U+1000..U+FFFF          E1..EF      80..BF      80..BF
 *
 * 4                     21     11110xxx    10xxxxxx    10xxxxxx    10xxxxxx
 *     U+10000..U+3FFFF         F0          90..BF      80..BF      80..BF
 *     U+40000..U+FFFFF         F1..F3      80..BF      80..BF      80..BF
 *    U+100000..U10FFFF         F4          80..8F      80..BF      80..BF
 *
 */
internal class UTF_8 : Unicode("UTF-8", setOf("UTF8", "unicode-1-1-utf-8")) {
    fun historicalName(): String {
        return "UTF8"
    }

    companion object {
        val INSTANCE: UTF_8 = UTF_8()
    }

}
