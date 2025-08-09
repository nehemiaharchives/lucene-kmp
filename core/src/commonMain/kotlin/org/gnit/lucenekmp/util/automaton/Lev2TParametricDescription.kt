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
 * Parametric description for generating a Levenshtein automaton of degree 2, with transpositions as
 * primitive edits. The comment in Lev1ParametricDescription may be helpful for you to understand
 * this class.
 *
 * @see Lev1ParametricDescription
 */
internal class Lev2TParametricDescription  // state map
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
//   11 -> [t(0, 1), (0, 1), (1, 1), (2, 1)]
//   12 -> [t(0, 2), (0, 2), (1, 2), (2, 2)]
//   13 -> [(0, 2), (1, 2), (2, 2), (3, 2)]
//   14 -> [(0, 1), (1, 1), (3, 2)]
//   15 -> [(0, 1), (2, 2), (3, 2)]
//   16 -> [(0, 1), (3, 2)]
//   17 -> [(0, 1), t(1, 2), (2, 2), (3, 2)]
//   18 -> [(0, 2), (1, 2), (3, 1)]
//   19 -> [(0, 2), (1, 2), (3, 2)]
//   20 -> [(0, 2), (1, 2), t(1, 2), (2, 2), (3, 2)]
//   21 -> [(0, 2), (2, 1), (3, 1)]
//   22 -> [(0, 2), (2, 2), (3, 2)]
//   23 -> [(0, 2), (3, 1)]
//   24 -> [(0, 2), (3, 2)]
//   25 -> [(0, 2), t(1, 2), (1, 2), (2, 2), (3, 2)]
//   26 -> [t(0, 2), (0, 2), (1, 2), (2, 2), (3, 2)]
//   27 -> [t(0, 2), (0, 2), (1, 2), (3, 1)]
//   28 -> [(0, 2), (1, 2), (2, 2), (3, 2), (4, 2)]
//   29 -> [(0, 2), (1, 2), (2, 2), (4, 2)]
//   30 -> [(0, 2), (1, 2), (2, 2), t(2, 2), (3, 2), (4, 2)]
//   31 -> [(0, 2), (1, 2), (3, 2), (4, 2)]
//   32 -> [(0, 2), (1, 2), (4, 2)]
//   33 -> [(0, 2), (1, 2), t(1, 2), (2, 2), (3, 2), (4, 2)]
//   34 -> [(0, 2), (1, 2), t(2, 2), (2, 2), (3, 2), (4, 2)]
//   35 -> [(0, 2), (2, 1), (4, 2)]
//   36 -> [(0, 2), (2, 2), (3, 2), (4, 2)]
//   37 -> [(0, 2), (2, 2), (4, 2)]
//   38 -> [(0, 2), (3, 2), (4, 2)]
//   39 -> [(0, 2), (4, 2)]
//   40 -> [(0, 2), t(1, 2), (1, 2), (2, 2), (3, 2), (4, 2)]
//   41 -> [(0, 2), t(2, 2), (2, 2), (3, 2), (4, 2)]
//   42 -> [t(0, 2), (0, 2), (1, 2), (2, 2), (3, 2), (4, 2)]
//   43 -> [t(0, 2), (0, 2), (1, 2), (2, 2), (4, 2)]
//   44 -> [t(0, 2), (0, 2), (1, 2), (2, 2), t(2, 2), (3, 2), (4, 2)]
    (w: Int) : ParametricDescription(
    w,
    2,
    intArrayOf(
        0, 1, 2, 0, 1, -1, 0, -1, 0, -1, 0, -1, 0, -1, -1, -1, -1, -1, -2, -1, -1, -2, -1, -2, -1,
        -1, -1, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2
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
            if (state < 13) {
                val loc = vector * 13 + state
                offset += unpack(offsetIncrs2, loc, 2)
                state = unpack(toStates2, loc, 4) - 1
            }
        } else if (position == w - 3) {
            if (state < 28) {
                val loc = vector * 28 + state
                offset += unpack(offsetIncrs3, loc, 2)
                state = unpack(toStates3, loc, 5) - 1
            }
        } else if (position == w - 4) {
            if (state < 45) {
                val loc = vector * 45 + state
                offset += unpack(offsetIncrs4, loc, 3)
                state = unpack(toStates4, loc, 6) - 1
            }
        } else {
            if (state < 45) {
                val loc = vector * 45 + state
                offset += unpack(offsetIncrs5, loc, 3)
                state = unpack(toStates5, loc, 6) - 1
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

        // 4 vectors; 13 states per vector; array length = 52
        private val toStates2 = longArrayOf(
            0xdc0703570707054L, 0x2323213a03dd3a3aL, 0x2254543215435223L, 0x5435L
        )
        private val offsetIncrs2 = longArrayOf(0x5558208800080000L, 0x5555555555L)

        // 8 vectors; 28 states per vector; array length = 224
        private val toStates3 = longArrayOf(
            0x700a5701c0380a4L, 0x180a000ca529c0L, -0x3ab6719f57f50e80L, -0x73bcff17a5ab9c68L,
            -0x272bcafe53e739ffL, 0x51976d6a863500adL, -0x3cafe53d735fe7f6L, 0x76dda8a5b0c5be16L,
            0xc41294a018c4519L, 0x1086520ce248d231L, 0x13946358ce31ac42L, 0x6732d4942d0348c4L,
            -0x29ca52b4e52ddb5bL, 0xce24948520c4139L, 0x58ce729d22110a52L, -0x6be33adf3bec6b1dL,
            0x4729d22490e732d4L, 0x39ce35adL
        )
        private val offsetIncrs3 = longArrayOf(
            0xc0c83000080000L,
            0x2200fcff300f3c30L,
            0x3c2200a8caa00a08L,
            0x55555555a8fea00aL,
            0x5555555555555555L,
            0x5555555555555555L,
            0x5555555555555555L
        )

        // 16 vectors; 45 states per vector; array length = 720
        private val toStates4 = longArrayOf(
            0x1453803801c0144L, -0x3fffaebaeb8fffc8L, 0x1400001401L, 0x140000L,
            0x6301f00700510000L, 0xa186178301f00d1L, 0xc20c30c20ca0c3L, -0x3ff3ff32f3cffcf4L,
            0x4c054014f0c00c30L, 0x55150c34c30944c3L, 0x430c014308300550L, 0xc30850c00050c31L,
            0x50053c50c3143000L, -0x7af2cf3daecf2cffL, 0xc21441430a08608L, 0x2145003143142145L,
            0x4c1431451400c314L, 0x28014d6c32832803L, 0x1c50c76cd34a0c3L, 0x430c30c31c314014L,
            0xc30050000001431L, -0x2c92f1bf35ff2cfdL, -0x34d544d36f4f1c00L, 0x2c32ca2c70c20ca1L,
            0x31c00c00cd2c70cbL, 0x558328034c2c32cL, 0x6cd6ca14558309b7L, 0x51c51401430850c7L,
            0xc30871430c714L, 0xca00d3071451450L, -0x46f8ea9f3d923eaaL, -0x38f3debbe34d544eL,
            0x1421c70cb1c51ca1L, 0x30811c51c51c00c3L, -0x3aefce3ddbcdbcf8L, 0x5c33830d70820820L,
            0x30c30c30c33850c3L, 0x451450c30c30c31cL, 0xda0920d20c20c20L, 0x365961145145914fL,
            -0x269bc9acae69a79bL, 0x51964364365a6590L, -0x6df4dfcdbcf7eafbL, -0x28dbddb6d38e74d8L,
            0x2cb3872c35cb28b0L, -0x4f3cd34d68d3cf29L, -0x37f36f3dfb1e38a4L, 0x4504171c62ca2482L,
            0x33976585d65d9610L, 0x4b5ca5d70d95cb5dL, 0x1030813873975c36L, 0x41451031c2245105L,
            -0x3ca3cc78eb1dbdf8L, 0x1c51c51451453851L, 0x20451450c70c30c3L, 0x4f0da09214f1440cL,
            0x6533944d04513d41L, -0x1eabafaaecaf19a8L, 0x551938364365a50L, 0x2892071851030815L,
            0x714e2422441c718bL, 0x4e1c73871c35cb28L, 0x5c70c32cb28e1c51L, -0x7e39ebbf3dfb1e39L,
            -0x2fbafc31e39d35dcL, 0x39338e6585d63944L, 0x364b5ca38e154387L, 0x38739738L
        )
        private val offsetIncrs4 = longArrayOf(
            0xc0000010000000L, 0x40000060061L, -0x7ffefff800000000L, 0x8229048249248a4L,
            0x6c360300002092L, 0x6db6036db61b6c30L, 0x361b0180000db6c0L, -0x24ee48e46e48e000L,
            0x100820006db6236L, 0x2492490612480012L, -0x7fbefffdb7dfffb7L, 0x4924a48924000900L,
            0x2080012510822492L, -0x6dbe496dfffb7ca0L, 0x4000926806da4924L, 0x291b49000241b010L,
            0x494934236d249249L, 0x2492492492492492L, -0x6db6db6db6db6db7L, 0x4924924924924924L,
            0x2492492492492492L, -0x6db6db6db6db6db7L, 0x4924924924924924L, 0x2492492492492492L,
            -0x6db6db6db6db6db7L, 0x4924924924924924L, 0x2492492492492492L, -0x6db6db6db6db6db7L,
            0x4924924924924924L, 0x2492492492492492L, -0x6db6db6db6db6db7L, 0x4924924924924924L,
            0x2492492492492492L, 0x249249249249L
        )

        // 32 vectors; 45 states per vector; array length = 1440
        private val toStates5 = longArrayOf(
            0x1453803801c0144L, -0x3fffaebaeb8fffc8L, 0x1400001401L, 0x140000L,
            0x4e00e00700510000L, 0x3451451c000e0051L, 0x30cd00000d015000L, -0x3cf3cf2bf3cf3cf4L,
            0x7c01c01440c30c30L, 0x185e0c07c03458c0L, -0x7cf3cf7cd7cf3d7aL, 0x33430c00c30030L,
            0x70051030030c3003L, -0x7cfe0ff2e9cfe100L, 0xc20ca0c30a18617L, -0x4ebaf3aebcebdf3dL,
            0x4f14314514314315L, 0x4c30944c34c05401L, 0x30830055055150c3L, -0x3fffaf3cebcf3fecL,
            0xc31430000c30850L, 0x25130d30150053c5L, -0x3fcabeababcf2cf4L, 0x1cb2cd0c300d0c90L,
            0x72c30cb2c91cb0c3L, -0x3cb3fabfeb0e34d4L, -0x7de7ddebcb3cf6bcL, 0x50851430851050c2L,
            0x30c50851400c50cL, 0x150053c50c51450L, 0x8850d30c25130d3L, 0x450c21441430a086L,
            0x1c91c70c51cb1c21L, 0x34c1cb1c71c314bL, -0x3cd7feb293cd7cd8L, 0x1401c50c76cd34a0L,
            0x31430c30c31c3140L, 0x30c300500000014L, 0x535b0ca0ca00d3L, 0x514369b34d2830caL,
            0x5965965a0c500d01L, 0x6435030c30d46546L, -0x24bc6fcd7fcb39a7L, -0x355134dbd3c6ffccL,
            0xcb28b1c30832872L, 0x700300334b1c32cbL, -0x1bf35ff2cf4f34f4L, -0x4d36f4f1bff2c930L,
            -0x5d38f3df35e34d55L, 0x4315b5ce6575d95cL, 0x28034c5d95c53831L, -0x5ebaa7cf648faa7dL,
            0x401430850c76cd6cL, 0x871430c71451c51L, -0x2cf8ebaebaffff3dL, 0x560c26dc1560ca00L,
            0xc914369b35b2851L, 0x465939451a14500dL, -0x6baf8afcf34d36c7L, 0x9b70558328034c3L,
            0x72caaecae41c5583L, -0x38eb8d78e3cf7af0L, 0x1470030c50871c32L, -0x3ea9f35ff2cf8eb9L,
            -0x544d46f8ea9f3d93L, 0x38a1c70c21441cb2L, 0x314b1c938e657394L, 0x4308308139438738L,
            0x820c51031c22432L, 0x50c35c33830d7082L, -0x3ce3cf3cf3cf3cc8L, 0xc20451450c30c30L,
            0x31440c70890c90c2L, -0x15f20f3c57df7df8L, -0x5d75d75d75dcebd0L, 0x1861868a28a28a1eL,
            -0x3c97db7cb7cf7cf8L, 0x4d96584514516453L, 0x36590d94d4659619L, 0x546590d90d969964L,
            0x920d20c20c20541L, -0x69eebaeba6eb0f26L, -0x1762cae69a79ac9bL, -0x6176176166185d87L,
            -0x4dfcdbcf7e7de7d9L, 0x422492c718b28920L, 0x3872c35cb28b0d72L, 0x32cb2972c30d72cbL,
            -0x36f3dfb1e38a34f4L, 0x24b1c62ca2482c80L, -0x4f15d1bd3c576f77L, -0x5b6995d769965ce4L,
            0x8175e7a59a8a269L, 0x718b28920b203243L, 0x175976584114105cL, 0x5c36572d74ce5d96L,
            -0x1e31a28f26d28d69L, -0x35db7d37f36f3dfcL, 0x5d96104504171c62L, 0x79669533976585d6L,
            0x659689e6964965a2L, 0x24510510308175e7L, -0x1dbdf7bebaefce3eL, 0x453851c35c338714L,
            0xc30c31c51c51451L, 0x41440c20451450c7L, -0x7defaebbf38f76ecL, 0x1470ea0df1c58c90L,
            -0x75e17a179e79e79dL, 0x30818618687a8a2L, 0x5053c36824853c51L, -0x69e6b31aecbeebb1L,
            -0x6bc7aaebeabb2bc7L, 0x5415464e0d90d96L, -0xf25f6deb0ebbf3eL, 0x533944d04513d414L,
            -0x79f7de7ecaf19a7aL, 0x18277689e89e981dL, -0x76df8e7aefcf7e7eL, 0x14e2422441c718b2L,
            -0x1e38c78e3ca34d79L, -0x38f3cd34d71e3aecL, 0x1c61440c204e1c75L, -0x6f76ef8e39d35db8L,
            -0x5ce38f15d1be3a74L, -0x5d965b8a179e8a19L, 0x510308175e7a57a8L, 0xf38718b28920718L,
            0x39961758e5134114L, 0x728e38550e1ce4ceL, -0x3dfb1e31a31f26d3L, 0x1c62ca2481c61440L,
            -0x7a29c6bb2fbafc32L, 0x5d86075e75338e65L, 0x75e7657689e69647L
        )
        private val offsetIncrs5 = longArrayOf(
            0xc0000010000000L, 0x40000060061L, 0x6000000800000000L, -0x2495492494ffcf80L,
            0x80040000002db6L, 0x1148241249245240L, 0x4002000000104904L, -0x5b4da6db6dd6e000L,
            0xd80c00009649658L, -0x7f24927924f3ffe5L, -0x3f9ffffc924fe493L, 0x6db6c36d86000d86L,
            0x300001b6ddadb6edL, -0x1c8dc91bfff93ca0L, 0xdb6c46db6236L, -0x46e48dfffc9e4fe8L,
            0x6db7636dbb1b71L, 0x6124800120100820L, 0x2482000492492490L, -0x6dbfff6ff7fbf000L,
            0x555b6a4924924830L, 0x2000480402080012L, -0x7beedb6db6dadbb7L, 0x24020104000928L,
            0x5892492492922490L, 0x120d808200049456L, 0x6924924906da4800L, 0x6c041000249a01bL,
            -0x6db6db7c92dbfff7L, 0x6020800124d5adb4L, 0x2492523692000483L, 0x104000926846da49L,
            0x49291b49000241b0L, -0x6db6b6ca9c92db6eL, 0x4924924924924924L, 0x2492492492492492L,
            -0x6db6db6db6db6db7L, 0x4924924924924924L, 0x2492492492492492L, -0x6db6db6db6db6db7L,
            0x4924924924924924L, 0x2492492492492492L, -0x6db6db6db6db6db7L, 0x4924924924924924L,
            0x2492492492492492L, -0x6db6db6db6db6db7L, 0x4924924924924924L, 0x2492492492492492L,
            -0x6db6db6db6db6db7L, 0x4924924924924924L, 0x2492492492492492L, -0x6db6db6db6db6db7L,
            0x4924924924924924L, 0x2492492492492492L, -0x6db6db6db6db6db7L, 0x4924924924924924L,
            0x2492492492492492L, -0x6db6db6db6db6db7L, 0x4924924924924924L, 0x2492492492492492L,
            -0x6db6db6db6db6db7L, 0x4924924924924924L, 0x2492492492492492L, -0x6db6db6db6db6db7L,
            0x4924924924924924L, 0x2492492492492492L, -0x6db6db6db6db6db7L, 0x24924924L
        )
    }
}
