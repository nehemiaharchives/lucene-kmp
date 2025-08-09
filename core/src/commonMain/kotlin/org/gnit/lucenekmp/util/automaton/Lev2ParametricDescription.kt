package org.gnit.lucenekmp.util.automaton

// following comment is copy of java lucene's comment:
// The following code was generated with the moman/finenight pkg
// This package is available under the MIT License, see NOTICE.txt
// for more details.
// This source file is auto-generated, Please do not modify it directly.
// You should modify the gradle/generation/moman/createAutomata.py instead.

// this kotlin code is ported from java lucene using Intellij's automatic java-to-kotlin conversion tool

import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.util.automaton.LevenshteinAutomata.ParametricDescription


/**
 * Parametric description for generating a Levenshtein automaton of degree 2. The comment in
 * Lev1ParametricDescription may be helpful for you to understand this class.
 *
 * @see Lev1ParametricDescription
 */
internal class Lev2ParametricDescription  // state map
//   0 -> [(0, 0)]
//   1 -> [(0, 1)]
//   2 -> [(0, 2)]
//   3 -> [(0, 1), (1, 1)]
//   4 -> [(0, 2), (1, 2)]
//   5 -> [(0, 1), (1, 1), (2, 1)]
//   6 -> [(0, 2), (1, 2), (2, 2)]
//   7 -> [(0, 1), (2, 1)]
//   8 -> [(0, 1), (2, 2)]
//   9 -> [(0, 2), (2, 1)]
//   10 -> [(0, 2), (2, 2)]
//   11 -> [(0, 2), (1, 2), (2, 2), (3, 2)]
//   12 -> [(0, 1), (1, 1), (3, 2)]
//   13 -> [(0, 1), (2, 2), (3, 2)]
//   14 -> [(0, 1), (3, 2)]
//   15 -> [(0, 2), (1, 2), (3, 1)]
//   16 -> [(0, 2), (1, 2), (3, 2)]
//   17 -> [(0, 2), (2, 1), (3, 1)]
//   18 -> [(0, 2), (2, 2), (3, 2)]
//   19 -> [(0, 2), (3, 1)]
//   20 -> [(0, 2), (3, 2)]
//   21 -> [(0, 2), (1, 2), (2, 2), (3, 2), (4, 2)]
//   22 -> [(0, 2), (1, 2), (2, 2), (4, 2)]
//   23 -> [(0, 2), (1, 2), (3, 2), (4, 2)]
//   24 -> [(0, 2), (1, 2), (4, 2)]
//   25 -> [(0, 2), (2, 1), (4, 2)]
//   26 -> [(0, 2), (2, 2), (3, 2), (4, 2)]
//   27 -> [(0, 2), (2, 2), (4, 2)]
//   28 -> [(0, 2), (3, 2), (4, 2)]
//   29 -> [(0, 2), (4, 2)]
    (w: Int) : ParametricDescription(
    w,
    2,
    intArrayOf(
        0, 1, 2, 0, 1, -1, 0, -1, 0, -1, 0, -1, -1, -1, -1, -2, -1, -2, -1, -2, -1, -2, -2, -2,
        -2, -2, -2, -2, -2, -2
    )
) {
    override fun transition(absState: Int, position: Int, vector: Int): Int {
        // null absState should never be passed in
        assert(absState != -1)

        // decode absState -> state, offset
        var state: Int = absState / (w + 1)
        var offset: Int = absState % (w + 1)
        assert(offset >= 0)

        if (position == w) {
            if (state < 3) {
                val loc = vector * 3 + state
                offset += unpack(offsetIncrs0, loc, 1)
                state = unpack(toStates0, loc, 2) - 1
            }
        } else if (position == w - 1) {
            if (state < 5) {
                val loc = vector * 5 + state
                offset += unpack(offsetIncrs1, loc, 1)
                state = unpack(toStates1, loc, 3) - 1
            }
        } else if (position == w - 2) {
            if (state < 11) {
                val loc = vector * 11 + state
                offset += unpack(offsetIncrs2, loc, 2)
                state = unpack(toStates2, loc, 4) - 1
            }
        } else if (position == w - 3) {
            if (state < 21) {
                val loc = vector * 21 + state
                offset += unpack(offsetIncrs3, loc, 2)
                state = unpack(toStates3, loc, 5) - 1
            }
        } else if (position == w - 4) {
            if (state < 30) {
                val loc = vector * 30 + state
                offset += unpack(offsetIncrs4, loc, 3)
                state = unpack(toStates4, loc, 5) - 1
            }
        } else {
            if (state < 30) {
                val loc = vector * 30 + state
                offset += unpack(offsetIncrs5, loc, 3)
                state = unpack(toStates5, loc, 5) - 1
            }
        }

        if (state == -1) {
            // null state
            return -1
        } else {
            // translate back to abs
            return state * (w + 1) + offset
        }
    }

    companion object {
        // 1 vectors; 3 states per vector; array length = 3
        private val toStates0 = longArrayOf(0xeL)
        private val offsetIncrs0 = longArrayOf(0x0L)

        // 2 vectors; 5 states per vector; array length = 10
        private val toStates1 = longArrayOf(0x1a688a2cL)
        private val offsetIncrs1 = longArrayOf(0x3e0L)

        // 4 vectors; 11 states per vector; array length = 44
        private val toStates2 = longArrayOf(0x3a07603570707054L, 0x522323232103773aL, 0x352254543213L)
        private val offsetIncrs2 = longArrayOf(0x5555520880080000L, 0x555555L)

        // 8 vectors; 21 states per vector; array length = 168
        private val toStates3 = longArrayOf(
            0x7000a560180380a4L, 0xc015a0180a0194aL, -0x7fcd3a7ce75cfe40L, -0x627caf2bfc67fce8L,
            0x3006028ca73a8602L, -0x3aeb9d9bf4de57f9L, 0x2310c4100c62194eL, -0x31ca77bde731db73L,
            -0x56d7a5f96e77dca8L, 0x1046b5a86b1252b5L, 0x2110a33892521483L, -0x19d6f9df729cc6b2L,
            -0x295d63b6de295b60L, 0x1aL
        )
        private val offsetIncrs3 = longArrayOf(
            -0xf3fff373ff80000L,
            0xca808822003f303L,
            0x5555553fa02f0880L,
            0x5555555555555555L,
            0x5555555555555555L,
            0x5555L
        )

        // 16 vectors; 30 states per vector; array length = 480
        private val toStates4 = longArrayOf(
            0x7000a560180380a4L, -0x5ffffffd7f1fd6b6L, 0x6c0b00e029000000L, -0x73bcaf3a63239fc7L,
            0x600ad00c03380601L, 0x2962c18c5180e00L, 0x18c4000c6028c4L, -0x75ceb9fc7fe7fd4cL,
            0x6328c4520c59c5L, 0x60d43500e600c651L, 0x280e339cea180a7L, 0x4039800000a318c6L,
            -0x2a84169fc613c2f3L, 0xc0338d6358c4352L, 0x28c4c81643500e60L, 0x3194a028c4339d8aL,
            0x590d403980018c4L, -0x3badd2a84971ceceL, 0xc4100c6510d6538L, -0x677bde731db72dcfL,
            0x318ce318c6398d83L, -0x5c9f63c8f3bcefbaL, -0x15c5296a7a970842L, 0x2d0348c411d47560L,
            -0x652bc676d6a52b6cL, 0x3104635ad431ad63L, -0x708c594adaf4bf2eL, 0x57350eab9d693956L,
            -0x731db6b7adf3bee3L, 0x294a398d85608442L, 0x5694831046318ce5L, -0x6a7b9f0849dc9f64L,
            -0x3bee2b8a9e9da72aL, 0x9243ad4941cc520L, 0x5ad4529ce39ad456L, -0x4adaf8ceb7cefb9dL,
            0x27656939460f7358L, 0x1d573516L
        )
        private val offsetIncrs4 = longArrayOf(
            0x610600010000000L, 0x2040000000001000L, 0x1044209245200L, -0x7f279279ff927f40L,
            0x2001b6030000006dL, 0x8200011b6237237L, 0x12490612400410L, 0x2449001040208000L,
            0x4d80820001044925L, 0x6da4906da400L, -0x6dadc96ffec9fdf8L, 0x24924924924911b6L,
            -0x6db6db6db6db6db7L, 0x4924924924924924L, 0x2492492492492492L, -0x6db6db6db6db6db7L,
            0x4924924924924924L, 0x2492492492492492L, -0x6db6db6db6db6db7L, 0x4924924924924924L,
            0x2492492492492492L, -0x6db6db6db6db6db7L, 0x24924924L
        )

        // 32 vectors; 30 states per vector; array length = 960
        private val toStates5 = longArrayOf(
            0x7000a560180380a4L, -0x5ffffffd7f1fd6b6L, 0x580600e029000000L, -0x7f1f9ff1ad63ffd7L,
            0x380a418c6388c631L, 0x316737180e5b02c0L, 0x300ce01806310d4L, -0x39fc693f4ff1fd70L,
            0xca328c4350c59cdL, -0x7f1ff9ff52e6b9aaL, 0x28c402962c18c51L, -0x7fd4bffe73bfff3aL,
            0xe58b06314603801L, -0x7294b7394a7f1cb8L, 0x28c5180e00600ad1L, 0x18ca31148316716L,
            0x3801802b4031944L, -0x3badf3a63a75cebaL, 0xe61956748cab38L, 0x39cea180a760d435L,
            0xa318c60280e3L, 0x6029d8350d403980L, 0x6b5a80e060d873a8L, 0xf43500e618c638dL,
            0x10d4b55efa580e7bL, 0x3980300ce358d63L, 0x57be96039ec3d0d4L, 0x4656567598c4352dL,
            -0x73b37e9bcaff19e7L, 0x194a028c4339d8a2L, 0x590d403980018c43L, -0x1cb72789d75cece0L,
            0xe618d6b4d6b1880L, 0x5eda38c4c8164350L, 0x19443594e31148b5L, 0x31320590d4039803L,
            0x7160c4522d57b68eL, -0x2dcef3bee6a98b2aL, -0x727c677bde731db8L, 0x1046318ce318c639L,
            0x2108633892348c43L, -0x21404210f09c4f0aL, -0x27d8f23cef3be085L, -0x714a5a9ea5c21058L,
            0x70c43104751d583aL, 0x58568f7bea3609c3L, 0x41f77ddb7bbeed69L, -0x6d6a52b6bd2fcb74L,
            -0x52bce529c652bc68L, 0x5250b40d23104635L, -0x31f0942f09db5a95L, 0x348c41f7b9cd7bdL,
            -0x1aa5c231652b6bd3L, 0x4755cd43aae75a4L, 0x73a6b5250b40d231L, -0x4284432296c6a971L,
            -0x1db6b7adf3be0887L, 0x4a398d856084428cL, 0x14831046318ce529L, -0x4e93deef5cc76daeL,
            0x1f7bdebe739c8f63L, -0x127727d8ea5adf3cL, 0x58589635a561183dL, 0x9c569483104751dL,
            -0x3a96a7b9f0849dcaL, 0x520c41f77ddb6719L, 0x45609243ad4941ccL, 0x4635ad4529ce39adL,
            -0x6f14adaf8ceb7cf0L, -0x298c8470942e93dcL, -0x6be33adf3be08464L, -0x6a5b1ae7c2329d2cL,
            0x483104755cd4589dL, 0x460f7358b5250731L, -0x8864298e84a96c7L
        )
        private val offsetIncrs5 = longArrayOf(
            0x610600010000000L, 0x40000000001000L, 0xb6d56da184180L, 0x824914800810000L,
            0x2002040000000411L, 0xc0000b2c5659245L, 0x6d80d86d86006d8L, 0x1b61801b60300000L,
            0x6d80c0000b5b76b6L, 0x46d88dc8dc800L, 0x6372372001b60300L, 0x400410082000b1b7L,
            0x2080000012490612L, 0x6d49241849001040L, -0x6edbffbeff7dfff5L, 0x402080004112494L,
            0xb2c49252449001L, 0x4906da4004d80820L, 0x136020800006daL, -0x7dfff4a496dbe497L,
            0x6da4948da4004d80L, 0x3690013602080004L, 0x49249249b1b69252L, 0x2492492492492492L,
            -0x6db6db6db6db6db7L, 0x4924924924924924L, 0x2492492492492492L, -0x6db6db6db6db6db7L,
            0x4924924924924924L, 0x2492492492492492L, -0x6db6db6db6db6db7L, 0x4924924924924924L,
            0x2492492492492492L, -0x6db6db6db6db6db7L, 0x4924924924924924L, 0x2492492492492492L,
            -0x6db6db6db6db6db7L, 0x4924924924924924L, 0x2492492492492492L, -0x6db6db6db6db6db7L,
            0x4924924924924924L, 0x2492492492492492L, -0x6db6db6db6db6db7L, 0x4924924924924924L,
            0x2492492492492492L
        )
    }
}
