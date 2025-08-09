package org.gnit.lucenekmp.queryparser.classic

import okio.IOException
import org.gnit.lucenekmp.queryparser.charstream.CharStream

/** Token Manager.  */
open class QueryParserTokenManager : QueryParserConstants {
    /** Debug output.  */ // (debugStream omitted).
    /** Set debug output.  */ // (setDebugStream omitted).
    private fun jjStopStringLiteralDfa_2(pos: Int, active0: Long): Int {
        when (pos) {
            else -> return -1
        }
    }

    private fun jjStartNfa_2(pos: Int, active0: Long): Int {
        return jjMoveNfa_2(jjStopStringLiteralDfa_2(pos, active0), pos + 1)
    }

    private fun jjStopAtPos(pos: Int, kind: Int): Int {
        jjmatchedKind = kind
        jjmatchedPos = pos
        return pos + 1
    }

    private fun jjMoveStringLiteralDfa0_2(): Int {
        when (curChar) {
            40 -> return jjStopAtPos(0, 14)
            41 -> return jjStopAtPos(0, 15)
            42 -> return jjStartNfaWithStates_2(0, 17, 49)
            43 -> return jjStartNfaWithStates_2(0, 11, 15)
            45 -> return jjStartNfaWithStates_2(0, 12, 15)
            58 -> return jjStopAtPos(0, 16)
            91 -> return jjStopAtPos(0, 25)
            94 -> return jjStopAtPos(0, 18)
            123 -> return jjStopAtPos(0, 26)
            else -> return jjMoveNfa_2(0, 0)
        }
    }

    private fun jjStartNfaWithStates_2(pos: Int, kind: Int, state: Int): Int {
        jjmatchedKind = kind
        jjmatchedPos = pos
        try {
            curChar = input_stream!!.readChar().code
        } catch (e: IOException) {
            return pos + 1
        }
        return jjMoveNfa_2(state, pos + 1)
    }

    private fun jjMoveNfa_2(startState: Int, curPos: Int): Int {
        var curPos = curPos
        var startsAt = 0
        jjnewStateCnt = 49
        var i = 1
        jjstateSet[0] = startState
        var kind = 0x7fffffff
        while (true) {
            if (++jjround == 0x7fffffff) ReInitRounds()
            if (curChar < 64) {
                val l = 1L shl curChar
                do {
                    when (jjstateSet[--i]) {
                        49, 33 -> {
                            if ((-0x400830700002601L and l) == 0L) break
                            if (kind > 23) kind = 23
                            run { jjCheckNAddTwoStates(33, 34) }
                        }

                        0 -> {
                            if ((-0x400ab0700002601L and l) != 0L) {
                                if (kind > 23) kind = 23
                                run { jjCheckNAddTwoStates(33, 34) }
                            } else if ((0x100002600L and l) != 0L) {
                                if (kind > 7) kind = 7
                            } else if ((0x280200000000L and l) != 0L) jjstateSet[jjnewStateCnt++] = 15
                            else if (curChar == 47) {
                                jjCheckNAddStates(0, 2)
                            } else if (curChar == 34) {
                                jjCheckNAddStates(3, 5)
                            }
                            if ((0x7bff50f8ffffd9ffL and l) != 0L) {
                                if (kind > 20) kind = 20
                                run { jjCheckNAddStates(6, 10) }
                            } else if (curChar == 42) {
                                if (kind > 22) kind = 22
                            } else if (curChar == 33) {
                                if (kind > 10) kind = 10
                            }
                            if (curChar == 38) jjstateSet[jjnewStateCnt++] = 4
                        }

                        4 -> if (curChar == 38 && kind > 8) kind = 8
                        5 -> if (curChar == 38) jjstateSet[jjnewStateCnt++] = 4
                        13 -> if (curChar == 33 && kind > 10) kind = 10
                        14 -> if ((0x280200000000L and l) != 0L) jjstateSet[jjnewStateCnt++] = 15
                        15 -> if ((0x100002600L and l) != 0L && kind > 13) kind = 13
                        16 -> if (curChar == 34) {
                            jjCheckNAddStates(3, 5)
                        }

                        17 -> if ((-0x400000001L and l) != 0L) {
                            jjCheckNAddStates(3, 5)
                        }

                        19 -> {
                            jjCheckNAddStates(3, 5)
                        }

                        20 -> if (curChar == 34 && kind > 19) kind = 19
                        22 -> {
                            if ((0x3ff000000000000L and l) == 0L) break
                            if (kind > 21) kind = 21
                            run { jjCheckNAddStates(11, 14) }
                        }

                        23 -> if (curChar == 46) {
                            jjCheckNAdd(24)
                        }

                        24 -> {
                            if ((0x3ff000000000000L and l) == 0L) break
                            if (kind > 21) kind = 21
                            run { jjCheckNAddStates(15, 17) }
                        }

                        25 -> {
                            if ((0x7bff78f8ffffd9ffL and l) == 0L) break
                            if (kind > 21) kind = 21
                            run { jjCheckNAddTwoStates(25, 26) }
                        }

                        27 -> {
                            if (kind > 21) kind = 21
                            run { jjCheckNAddTwoStates(25, 26) }
                        }

                        28 -> {
                            if ((0x7bff78f8ffffd9ffL and l) == 0L) break
                            if (kind > 21) kind = 21
                            run { jjCheckNAddTwoStates(28, 29) }
                        }

                        30 -> {
                            if (kind > 21) kind = 21
                            run { jjCheckNAddTwoStates(28, 29) }
                        }

                        31 -> if (curChar == 42 && kind > 22) kind = 22
                        32 -> {
                            if ((-0x400ab0700002601L and l) == 0L) break
                            if (kind > 23) kind = 23
                            run { jjCheckNAddTwoStates(33, 34) }
                        }

                        35 -> {
                            if (kind > 23) kind = 23
                            run { jjCheckNAddTwoStates(33, 34) }
                        }

                        36, 38 -> if (curChar == 47) {
                            jjCheckNAddStates(0, 2)
                        }

                        37 -> if ((-0x800000000001L and l) != 0L) {
                            jjCheckNAddStates(0, 2)
                        }

                        40 -> if (curChar == 47 && kind > 24) kind = 24
                        41 -> {
                            if ((0x7bff50f8ffffd9ffL and l) == 0L) break
                            if (kind > 20) kind = 20
                            run { jjCheckNAddStates(6, 10) }
                        }

                        42 -> {
                            if ((0x7bff78f8ffffd9ffL and l) == 0L) break
                            if (kind > 20) kind = 20
                            run { jjCheckNAddTwoStates(42, 43) }
                        }

                        44 -> {
                            if (kind > 20) kind = 20
                            run { jjCheckNAddTwoStates(42, 43) }
                        }

                        45 -> if ((0x7bff78f8ffffd9ffL and l) != 0L) {
                            jjCheckNAddStates(18, 20)
                        }

                        47 -> {
                            jjCheckNAddStates(18, 20)
                        }

                        else -> {}
                    }
                } while (i != startsAt)
            } else if (curChar < 128) {
                val l = 1L shl (curChar and 63)
                do {
                    when (jjstateSet[--i]) {
                        49 -> if ((-0x6800000078000001L and l) != 0L) {
                            if (kind > 23) kind = 23
                            run { jjCheckNAddTwoStates(33, 34) }
                        } else if (curChar == 92) {
                            jjCheckNAdd(35)
                        }

                        0 -> {
                            if ((-0x6800000078000001L and l) != 0L) {
                                if (kind > 20) kind = 20
                                run { jjCheckNAddStates(6, 10) }
                            } else if (curChar == 92) {
                                jjCheckNAddStates(21, 23)
                            } else if (curChar == 126) {
                                if (kind > 21) kind = 21
                                run { jjCheckNAddStates(24, 26) }
                            }
                            if ((-0x6800000078000001L and l) != 0L) {
                                if (kind > 23) kind = 23
                                run { jjCheckNAddTwoStates(33, 34) }
                            }
                            if (curChar == 78) jjstateSet[jjnewStateCnt++] = 11
                            else if (curChar == 124) jjstateSet[jjnewStateCnt++] = 8
                            else if (curChar == 79) jjstateSet[jjnewStateCnt++] = 6
                            else if (curChar == 65) jjstateSet[jjnewStateCnt++] = 2
                        }

                        1 -> if (curChar == 68 && kind > 8) kind = 8
                        2 -> if (curChar == 78) jjstateSet[jjnewStateCnt++] = 1
                        3 -> if (curChar == 65) jjstateSet[jjnewStateCnt++] = 2
                        6 -> if (curChar == 82 && kind > 9) kind = 9
                        7 -> if (curChar == 79) jjstateSet[jjnewStateCnt++] = 6
                        8 -> if (curChar == 124 && kind > 9) kind = 9
                        9 -> if (curChar == 124) jjstateSet[jjnewStateCnt++] = 8
                        10 -> if (curChar == 84 && kind > 10) kind = 10
                        11 -> if (curChar == 79) jjstateSet[jjnewStateCnt++] = 10
                        12 -> if (curChar == 78) jjstateSet[jjnewStateCnt++] = 11
                        17 -> if ((-0x10000001L and l) != 0L) {
                            jjCheckNAddStates(3, 5)
                        }

                        18 -> if (curChar == 92) jjstateSet[jjnewStateCnt++] = 19
                        19 -> {
                            jjCheckNAddStates(3, 5)
                        }

                        21 -> {
                            if (curChar != 126) break
                            if (kind > 21) kind = 21
                            run { jjCheckNAddStates(24, 26) }
                        }

                        25 -> {
                            if ((-0x6800000078000001L and l) == 0L) break
                            if (kind > 21) kind = 21
                            run { jjCheckNAddTwoStates(25, 26) }
                        }

                        26 -> if (curChar == 92) jjstateSet[jjnewStateCnt++] = 27
                        27 -> {
                            if (kind > 21) kind = 21
                            run { jjCheckNAddTwoStates(25, 26) }
                        }

                        28 -> {
                            if ((-0x6800000078000001L and l) == 0L) break
                            if (kind > 21) kind = 21
                            run { jjCheckNAddTwoStates(28, 29) }
                        }

                        29 -> if (curChar == 92) jjstateSet[jjnewStateCnt++] = 30
                        30 -> {
                            if (kind > 21) kind = 21
                            run { jjCheckNAddTwoStates(28, 29) }
                        }

                        32 -> {
                            if ((-0x6800000078000001L and l) == 0L) break
                            if (kind > 23) kind = 23
                            run { jjCheckNAddTwoStates(33, 34) }
                        }

                        33 -> {
                            if ((-0x6800000078000001L and l) == 0L) break
                            if (kind > 23) kind = 23
                            run { jjCheckNAddTwoStates(33, 34) }
                        }

                        34 -> if (curChar == 92) {
                            jjCheckNAdd(35)
                        }

                        35 -> {
                            if (kind > 23) kind = 23
                            run { jjCheckNAddTwoStates(33, 34) }
                        }

                        37 -> {
                            jjAddStates(0, 2)
                        }

                        39 -> if (curChar == 92) jjstateSet[jjnewStateCnt++] = 38
                        41 -> {
                            if ((-0x6800000078000001L and l) == 0L) break
                            if (kind > 20) kind = 20
                            run { jjCheckNAddStates(6, 10) }
                        }

                        42 -> {
                            if ((-0x6800000078000001L and l) == 0L) break
                            if (kind > 20) kind = 20
                            run { jjCheckNAddTwoStates(42, 43) }
                        }

                        43 -> if (curChar == 92) {
                            jjCheckNAdd(44)
                        }

                        44 -> {
                            if (kind > 20) kind = 20
                            run { jjCheckNAddTwoStates(42, 43) }
                        }

                        45 -> if ((-0x6800000078000001L and l) != 0L) {
                            jjCheckNAddStates(18, 20)
                        }

                        46 -> if (curChar == 92) {
                            jjCheckNAdd(47)
                        }

                        47 -> {
                            jjCheckNAddStates(18, 20)
                        }

                        48 -> if (curChar == 92) {
                            jjCheckNAddStates(21, 23)
                        }

                        else -> {}
                    }
                } while (i != startsAt)
            } else {
                val hiByte = (curChar shr 8)
                val i1 = hiByte shr 6
                val l1 = 1L shl (hiByte and 63)
                val i2 = (curChar and 0xff) shr 6
                val l2 = 1L shl (curChar and 63)
                do {
                    when (jjstateSet[--i]) {
                        49, 33 -> {
                            if (!jjCanMove_2(hiByte, i1, i2, l1, l2)) break
                            if (kind > 23) kind = 23
                            run { jjCheckNAddTwoStates(33, 34) }
                        }

                        0 -> {
                            if (jjCanMove_0(hiByte, i1, i2, l1, l2)) {
                                if (kind > 7) kind = 7
                            }
                            if (jjCanMove_2(hiByte, i1, i2, l1, l2)) {
                                if (kind > 23) kind = 23
                                run { jjCheckNAddTwoStates(33, 34) }
                            }
                            if (jjCanMove_2(hiByte, i1, i2, l1, l2)) {
                                if (kind > 20) kind = 20
                                run { jjCheckNAddStates(6, 10) }
                            }
                        }

                        15 -> if (jjCanMove_0(hiByte, i1, i2, l1, l2) && kind > 13) kind = 13
                        17, 19 -> if (jjCanMove_1(hiByte, i1, i2, l1, l2)) {
                            jjCheckNAddStates(3, 5)
                        }

                        25 -> {
                            if (!jjCanMove_2(hiByte, i1, i2, l1, l2)) break
                            if (kind > 21) kind = 21
                            run { jjCheckNAddTwoStates(25, 26) }
                        }

                        27 -> {
                            if (!jjCanMove_1(hiByte, i1, i2, l1, l2)) break
                            if (kind > 21) kind = 21
                            run { jjCheckNAddTwoStates(25, 26) }
                        }

                        28 -> {
                            if (!jjCanMove_2(hiByte, i1, i2, l1, l2)) break
                            if (kind > 21) kind = 21
                            run { jjCheckNAddTwoStates(28, 29) }
                        }

                        30 -> {
                            if (!jjCanMove_1(hiByte, i1, i2, l1, l2)) break
                            if (kind > 21) kind = 21
                            run { jjCheckNAddTwoStates(28, 29) }
                        }

                        32 -> {
                            if (!jjCanMove_2(hiByte, i1, i2, l1, l2)) break
                            if (kind > 23) kind = 23
                            run { jjCheckNAddTwoStates(33, 34) }
                        }

                        35 -> {
                            if (!jjCanMove_1(hiByte, i1, i2, l1, l2)) break
                            if (kind > 23) kind = 23
                            run { jjCheckNAddTwoStates(33, 34) }
                        }

                        37 -> if (jjCanMove_1(hiByte, i1, i2, l1, l2)) {
                            jjAddStates(0, 2)
                        }

                        41 -> {
                            if (!jjCanMove_2(hiByte, i1, i2, l1, l2)) break
                            if (kind > 20) kind = 20
                            run { jjCheckNAddStates(6, 10) }
                        }

                        42 -> {
                            if (!jjCanMove_2(hiByte, i1, i2, l1, l2)) break
                            if (kind > 20) kind = 20
                            run { jjCheckNAddTwoStates(42, 43) }
                        }

                        44 -> {
                            if (!jjCanMove_1(hiByte, i1, i2, l1, l2)) break
                            if (kind > 20) kind = 20
                            run { jjCheckNAddTwoStates(42, 43) }
                        }

                        45 -> if (jjCanMove_2(hiByte, i1, i2, l1, l2)) {
                            jjCheckNAddStates(18, 20)
                        }

                        47 -> if (jjCanMove_1(hiByte, i1, i2, l1, l2)) {
                            jjCheckNAddStates(18, 20)
                        }

                        else -> if (i1 == 0 || l1 == 0L || i2 == 0 || l2 == 0L) break else break
                    }
                } while (i != startsAt)
            }
            if (kind != 0x7fffffff) {
                jjmatchedKind = kind
                jjmatchedPos = curPos
                kind = 0x7fffffff
            }
            ++curPos
            if ((jjnewStateCnt.also { i = it }) == ((49 - (startsAt.also { jjnewStateCnt = it })).also {
                    startsAt = it
                })) return curPos
            try {
                curChar = input_stream!!.readChar().code
            } catch (e: IOException) {
                return curPos
            }
        }
    }

    private fun jjMoveStringLiteralDfa0_0(): Int {
        return jjMoveNfa_0(0, 0)
    }

    private fun jjMoveNfa_0(startState: Int, curPos: Int): Int {
        var curPos = curPos
        var startsAt = 0
        jjnewStateCnt = 3
        var i = 1
        jjstateSet[0] = startState
        var kind = 0x7fffffff
        while (true) {
            if (++jjround == 0x7fffffff) ReInitRounds()
            if (curChar < 64) {
                val l = 1L shl curChar
                do {
                    when (jjstateSet[--i]) {
                        0 -> {
                            if ((0x3ff000000000000L and l) == 0L) break
                            if (kind > 27) kind = 27
                            run { jjAddStates(27, 28) }
                        }

                        1 -> if (curChar == 46) {
                            jjCheckNAdd(2)
                        }

                        2 -> {
                            if ((0x3ff000000000000L and l) == 0L) break
                            if (kind > 27) kind = 27
                            run { jjCheckNAdd(2) }
                        }

                        else -> {}
                    }
                } while (i != startsAt)
            } else if (curChar < 128) {
                val l = 1L shl (curChar and 63)
                do {
                    when (jjstateSet[--i]) {
                        else -> {}
                    }
                } while (i != startsAt)
            } else {
                val hiByte = (curChar shr 8)
                val i1 = hiByte shr 6
                val l1 = 1L shl (hiByte and 63)
                val i2 = (curChar and 0xff) shr 6
                val l2 = 1L shl (curChar and 63)
                do {
                    when (jjstateSet[--i]) {
                        else -> if (i1 == 0 || l1 == 0L || i2 == 0 || l2 == 0L) break else break
                    }
                } while (i != startsAt)
            }
            if (kind != 0x7fffffff) {
                jjmatchedKind = kind
                jjmatchedPos = curPos
                kind = 0x7fffffff
            }
            ++curPos
            if ((jjnewStateCnt.also { i = it }) == ((3 - (startsAt.also { jjnewStateCnt = it })).also {
                    startsAt = it
                })) return curPos
            try {
                curChar = input_stream!!.readChar().code
            } catch (e: IOException) {
                return curPos
            }
        }
    }

    private fun jjStopStringLiteralDfa_1(pos: Int, active0: Long): Int {
        when (pos) {
            0 -> {
                if ((active0 and 0x10000000L) != 0L) {
                    jjmatchedKind = 32
                    return 6
                }
                return -1
            }

            else -> return -1
        }
    }

    private fun jjStartNfa_1(pos: Int, active0: Long): Int {
        return jjMoveNfa_1(jjStopStringLiteralDfa_1(pos, active0), pos + 1)
    }

    private fun jjMoveStringLiteralDfa0_1(): Int {
        return when (curChar) {
            84 -> jjMoveStringLiteralDfa1_1(0x10000000L)
            93 -> jjStopAtPos(0, 29)
            125 -> jjStopAtPos(0, 30)
            else -> jjMoveNfa_1(0, 0)
        }
    }

    private fun jjMoveStringLiteralDfa1_1(active0: Long): Int {
        try {
            curChar = input_stream!!.readChar().code
        } catch (e: IOException) {
            jjStopStringLiteralDfa_1(0, active0)
            return 1
        }
        when (curChar) {
            79 -> if ((active0 and 0x10000000L) != 0L) return jjStartNfaWithStates_1(1, 28, 6)
            else -> {}
        }
        return jjStartNfa_1(0, active0)
    }

    private fun jjStartNfaWithStates_1(pos: Int, kind: Int, state: Int): Int {
        jjmatchedKind = kind
        jjmatchedPos = pos
        try {
            curChar = input_stream!!.readChar().code
        } catch (e: IOException) {
            return pos + 1
        }
        return jjMoveNfa_1(state, pos + 1)
    }

    private fun jjMoveNfa_1(startState: Int, curPos: Int): Int {
        var curPos = curPos
        var startsAt = 0
        jjnewStateCnt = 7
        var i = 1
        jjstateSet[0] = startState
        var kind = 0x7fffffff
        while (true) {
            if (++jjround == 0x7fffffff) ReInitRounds()
            if (curChar < 64) {
                val l = 1L shl curChar
                do {
                    when (jjstateSet[--i]) {
                        0 -> {
                            if ((-0x100000001L and l) != 0L) {
                                if (kind > 32) kind = 32
                                run { jjCheckNAdd(6) }
                            }
                            if ((0x100002600L and l) != 0L) {
                                if (kind > 7) kind = 7
                            } else if (curChar == 34) {
                                jjCheckNAddTwoStates(2, 4)
                            }
                        }

                        1 -> if (curChar == 34) {
                            jjCheckNAddTwoStates(2, 4)
                        }

                        2 -> if ((-0x400000001L and l) != 0L) {
                            jjCheckNAddStates(29, 31)
                        }

                        3 -> if (curChar == 34) {
                            jjCheckNAddStates(29, 31)
                        }

                        5 -> if (curChar == 34 && kind > 31) kind = 31
                        6 -> {
                            if ((-0x100000001L and l) == 0L) break
                            if (kind > 32) kind = 32
                            run { jjCheckNAdd(6) }
                        }

                        else -> {}
                    }
                } while (i != startsAt)
            } else if (curChar < 128) {
                val l = 1L shl (curChar and 63)
                do {
                    when (jjstateSet[--i]) {
                        0, 6 -> {
                            if ((-0x2000000020000001L and l) == 0L) break
                            if (kind > 32) kind = 32
                            run { jjCheckNAdd(6) }
                        }

                        2 -> {
                            jjAddStates(29, 31)
                        }

                        4 -> if (curChar == 92) jjstateSet[jjnewStateCnt++] = 3
                        else -> {}
                    }
                } while (i != startsAt)
            } else {
                val hiByte = (curChar shr 8)
                val i1 = hiByte shr 6
                val l1 = 1L shl (hiByte and 63)
                val i2 = (curChar and 0xff) shr 6
                val l2 = 1L shl (curChar and 63)
                do {
                    when (jjstateSet[--i]) {
                        0 -> {
                            if (jjCanMove_0(hiByte, i1, i2, l1, l2)) {
                                if (kind > 7) kind = 7
                            }
                            if (jjCanMove_1(hiByte, i1, i2, l1, l2)) {
                                if (kind > 32) kind = 32
                                run { jjCheckNAdd(6) }
                            }
                        }

                        2 -> if (jjCanMove_1(hiByte, i1, i2, l1, l2)) {
                            jjAddStates(29, 31)
                        }

                        6 -> {
                            if (!jjCanMove_1(hiByte, i1, i2, l1, l2)) break
                            if (kind > 32) kind = 32
                            run { jjCheckNAdd(6) }
                        }

                        else -> if (i1 == 0 || l1 == 0L || i2 == 0 || l2 == 0L) break else break
                    }
                } while (i != startsAt)
            }
            if (kind != 0x7fffffff) {
                jjmatchedKind = kind
                jjmatchedPos = curPos
                kind = 0x7fffffff
            }
            ++curPos
            if ((jjnewStateCnt.also { i = it }) == ((7 - (startsAt.also { jjnewStateCnt = it })).also {
                    startsAt = it
                })) return curPos
            try {
                curChar = input_stream!!.readChar().code
            } catch (e: IOException) {
                return curPos
            }
        }
    }

    protected fun jjFillToken(): Token {
        val t: Token
        val curTokenImage: String
        val im = jjstrLiteralImages[jjmatchedKind]
        curTokenImage = if (im == null) input_stream!!.GetImage()!! else im
        val beginLine: Int = input_stream!!.beginLine
        val beginColumn: Int = input_stream!!.beginColumn
        val endLine: Int = input_stream!!.endLine
        val endColumn: Int = input_stream!!.endColumn
        t = Token.newToken(jjmatchedKind, curTokenImage)

        t.beginLine = beginLine
        t.endLine = endLine
        t.beginColumn = beginColumn
        t.endColumn = endColumn

        return t
    }

    var curLexState: Int = 2
    var defaultLexState: Int = 2
    var jjnewStateCnt: Int = 0
    var jjround: Int = 0
    var jjmatchedPos: Int = 0
    var jjmatchedKind: Int = 0

    val nextToken: Token
        /** Get the next Token.  */
        get() {
            val matchedToken: Token
            var curPos = 0

            EOFLoop@ while (true) {
                try {
                    curChar = input_stream!!.BeginToken().code
                } catch (e: Exception) {
                    jjmatchedKind = 0
                    jjmatchedPos = -1
                    matchedToken = jjFillToken()
                    return matchedToken
                }

                when (curLexState) {
                    0 -> {
                        jjmatchedKind = 0x7fffffff
                        jjmatchedPos = 0
                        curPos = jjMoveStringLiteralDfa0_0()
                    }

                    1 -> {
                        jjmatchedKind = 0x7fffffff
                        jjmatchedPos = 0
                        curPos = jjMoveStringLiteralDfa0_1()
                    }

                    2 -> {
                        jjmatchedKind = 0x7fffffff
                        jjmatchedPos = 0
                        curPos = jjMoveStringLiteralDfa0_2()
                    }
                }
                if (jjmatchedKind != 0x7fffffff) {
                    if (jjmatchedPos + 1 < curPos) input_stream!!.backup(curPos - jjmatchedPos - 1)
                    if ((jjtoToken[jjmatchedKind shr 6] and (1L shl (jjmatchedKind and 63))) != 0L) {
                        matchedToken = jjFillToken()
                        if (jjnewLexState[jjmatchedKind] != -1) curLexState = jjnewLexState[jjmatchedKind]
                        return matchedToken
                    } else {
                        if (jjnewLexState[jjmatchedKind] != -1) curLexState = jjnewLexState[jjmatchedKind]
                        continue@EOFLoop
                    }
                }
                var error_line: Int = input_stream!!.endLine
                var error_column: Int = input_stream!!.endColumn
                var error_after: String? = null
                var EOFSeen = false
                try {
                    input_stream!!.readChar()
                    input_stream!!.backup(1)
                } catch (e1: IOException) {
                    EOFSeen = true
                    error_after = if (curPos <= 1) "" else input_stream!!.GetImage()
                    if (curChar == '\n'.code || curChar == '\r'.code) {
                        error_line++
                        error_column = 0
                    } else error_column++
                }
                if (!EOFSeen) {
                    input_stream!!.backup(1)
                    error_after = if (curPos <= 1) "" else input_stream!!.GetImage()
                }
                throw TokenMgrError(
                    EOFSeen,
                    curLexState,
                    error_line,
                    error_column,
                    error_after,
                    curChar,
                    TokenMgrError.LEXICAL_ERROR
                )
            }
        }

    fun SkipLexicalActions(matchedToken: Token) {
        when (jjmatchedKind) {
            else -> {}
        }
    }

    fun MoreLexicalActions() {
        jjimageLen += ((jjmatchedPos + 1).also { lengthOfMatch = it })
        when (jjmatchedKind) {
            else -> {}
        }
    }

    fun TokenLexicalActions(matchedToken: Token) {
        when (jjmatchedKind) {
            else -> {}
        }
    }

    private fun jjCheckNAdd(state: Int) {
        if (jjrounds[state] != jjround) {
            jjstateSet[jjnewStateCnt++] = state
            jjrounds[state] = jjround
        }
    }

    private fun jjAddStates(start: Int, end: Int) {
        var start = start
        do {
            jjstateSet[jjnewStateCnt++] = jjnextStates[start]
        } while (start++ != end)
    }

    private fun jjCheckNAddTwoStates(state1: Int, state2: Int) {
        jjCheckNAdd(state1)
        jjCheckNAdd(state2)
    }

    private fun jjCheckNAddStates(start: Int, end: Int) {
        var start = start
        do {
            jjCheckNAdd(jjnextStates[start])
        } while (start++ != end)
    }

    /** Constructor.  */
    constructor(stream: CharStream) {
        input_stream = stream
    }

    /** Constructor.  */
    constructor(stream: CharStream, lexState: Int) {
        ReInit(stream)
        SwitchTo(lexState)
    }

    /** Reinitialise parser.  */
    fun ReInit(stream: CharStream) {
        jjnewStateCnt =
            0
        jjmatchedPos = jjnewStateCnt
        curLexState = defaultLexState
        input_stream = stream
        ReInitRounds()
    }

    private fun ReInitRounds() {
        jjround = -0x7fffffff
        var i: Int = 49
        while (i-- > 0) {
            jjrounds[i] = -0x80000000
        }
    }

    /** Reinitialise parser.  */
    fun ReInit(stream: CharStream, lexState: Int) {
        ReInit(stream)
        SwitchTo(lexState)
    }

    /** Switch to specified lex state.  */
    fun SwitchTo(lexState: Int) {
        if (lexState !in 0..<3) throw TokenMgrError(
            "Error: Ignoring invalid lexical state : $lexState. State unchanged.",
            TokenMgrError.INVALID_LEXICAL_STATE
        )
        else curLexState = lexState
    }


    protected var input_stream: CharStream? = null

    private val jjrounds = IntArray(49)
    private val jjstateSet = IntArray(2 * 49)
    private val jjimage: StringBuilder = StringBuilder()
    private val image: StringBuilder = jjimage
    private var jjimageLen = 0
    private var lengthOfMatch = 0
    protected var curChar: Int = 0

    companion object {
        val jjbitVec0: LongArray = longArrayOf(
            0x1L, 0x0L, 0x0L, 0x0L
        )
        val jjbitVec1: LongArray = longArrayOf(
            -0x2L, -0x1L, -0x1L, -0x1L
        )
        val jjbitVec3: LongArray = longArrayOf(
            0x0L, 0x0L, -0x1L, -0x1L
        )
        val jjbitVec4: LongArray = longArrayOf(
            -0x1000000000002L, -0x1L, -0x1L, -0x1L
        )

        /** Token literal values.  */
        val jjstrLiteralImages: Array<String?> = arrayOf<String?>(
            "", null, null, null, null, null, null, null, null, null, null, "\u002b", "\u002d",
            null, "\u0028", "\u0029", "\u003a", "\u002a", "\u005e", null, null, null, null, null, null,
            "\u005b", "\u007b", null, "\u0054\u004f", "\u005d", "\u007d", null, null,
        )
        val jjnextStates: IntArray = intArrayOf(
            37, 39, 40, 17, 18, 20, 42, 43, 45, 46, 31, 22, 23, 25, 26, 24,
            25, 26, 45, 46, 31, 44, 47, 35, 22, 28, 29, 0, 1, 2, 4, 5,
        )

        private fun jjCanMove_0(hiByte: Int, i1: Int, i2: Int, l1: Long, l2: Long): Boolean {
            return when (hiByte) {
                48 -> ((jjbitVec0[i2] and l2) != 0L)
                else -> false
            }
        }

        private fun jjCanMove_1(hiByte: Int, i1: Int, i2: Int, l1: Long, l2: Long): Boolean {
            when (hiByte) {
                0 -> return ((jjbitVec3[i2] and l2) != 0L)
                else -> {
                    if ((jjbitVec1[i1] and l1) != 0L) return true
                    return false
                }
            }
        }

        private fun jjCanMove_2(hiByte: Int, i1: Int, i2: Int, l1: Long, l2: Long): Boolean {
            when (hiByte) {
                0 -> return ((jjbitVec3[i2] and l2) != 0L)
                48 -> return ((jjbitVec1[i2] and l2) != 0L)
                else -> {
                    if ((jjbitVec4[i1] and l1) != 0L) return true
                    return false
                }
            }
        }

        /** Lexer state names.  */
        val lexStateNames: Array<String> = arrayOf<String>(
            "Boost",
            "Range",
            "DEFAULT",
        )

        /** Lex State array.  */
        val jjnewLexState: IntArray = intArrayOf(
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, -1, -1, -1, -1, -1, -1,
            1, 1, 2, -1, 2, 2, -1, -1,
        )
        val jjtoToken: LongArray = longArrayOf(
            0x1ffffff01L,
        )
        val jjtoSkip: LongArray = longArrayOf(
            0x80L,
        )
        val jjtoSpecial: LongArray = longArrayOf(
            0x0L,
        )
        val jjtoMore: LongArray = longArrayOf(
            0x0L,
        )
    }
}
