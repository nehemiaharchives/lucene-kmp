package org.gnit.lucenekmp.util.automaton

import org.gnit.lucenekmp.internal.hppc.IntArrayList
import org.gnit.lucenekmp.jdkport.toBinaryString
import org.gnit.lucenekmp.util.automaton.Automaton.Builder
import kotlin.experimental.inv


// TODO
//   - do we really need the .bits...?  if not we can make util in UnicodeUtil to convert 1 char
// into a BytesRef
/**
 * Converts UTF-32 automata to the equivalent UTF-8 representation.
 *
 * @lucene.internal
 */
class UTF32ToUTF8
/** Sole constructor.  */
{
    // Represents one of the N utf8 bytes that (in sequence)
    // define a code point.  value is the byte value; bits is
    // how many bits are "used" by utf8 at that byte
    private class UTF8Byte {
        var value: Byte = 0
        var bits: Byte = 0
    }

    // Holds a single code point, as a sequence of 1-4 utf8 bytes:
    // TODO: maybe move to UnicodeUtil?
    private class UTF8Sequence {
        private val bytes: Array<UTF8Byte?>
        internal var len = 0

        init {
            bytes = kotlin.arrayOfNulls<UTF8Byte>(4)
            for (i in 0..3) {
                bytes[i] = UTF8Byte()
            }
        }

        fun byteAt(idx: Int): Int {
            return bytes[idx]!!.value.toInt() and 0xFF
        }

        fun numBits(idx: Int): Int {
            return bytes[idx]!!.bits.toInt()
        }

        fun set(code: Int) {
            if (code < 128) {
                // 0xxxxxxx
                bytes[0]!!.value = code.toByte()
                bytes[0]!!.bits = 7
                len = 1
            } else if (code < 2048) {
                // 110yyyxx 10xxxxxx
                bytes[0]!!.value = ((6 shl 5) or (code shr 6)).toByte()
                bytes[0]!!.bits = 5
                setRest(code, 1)
                len = 2
            } else if (code < 65536) {
                // 1110yyyy 10yyyyxx 10xxxxxx
                bytes[0]!!.value = ((14 shl 4) or (code shr 12)).toByte()
                bytes[0]!!.bits = 4
                setRest(code, 2)
                len = 3
            } else {
                // 11110zzz 10zzyyyy 10yyyyxx 10xxxxxx
                bytes[0]!!.value = ((30 shl 3) or (code shr 18)).toByte()
                bytes[0]!!.bits = 3
                setRest(code, 3)
                len = 4
            }
        }

        // Only set first byte value for tmp utf8.
        fun setFirstByte(code: Int) {
            if (code < 128) {
                // 0xxxxxxx
                bytes[0]!!.value = code.toByte()
                len = 1
            } else if (code < 2048) {
                // 110yyyxx 10xxxxxx
                bytes[0]!!.value = ((6 shl 5) or (code shr 6)).toByte()
                len = 2
            } else if (code < 65536) {
                // 1110yyyy 10yyyyxx 10xxxxxx
                bytes[0]!!.value = ((14 shl 4) or (code shr 12)).toByte()
                len = 3
            } else {
                // 11110zzz 10zzyyyy 10yyyyxx 10xxxxxx
                bytes[0]!!.value = ((30 shl 3) or (code shr 18)).toByte()
                len = 4
            }
        }

        fun setRest(code: Int, numBytes: Int) {
            var code = code
            for (i in 0..<numBytes) {
                bytes[numBytes - i]!!.value = (128 or (code and MASKS[6].toInt())).toByte()
                bytes[numBytes - i]!!.bits = 6
                code = code shr 6
            }
        }

        override fun toString(): String {
            val b = StringBuilder()
            for (i in 0..<len) {
                if (i > 0) {
                    b.append(' ')
                }
                b.append(Int.toBinaryString(byteAt(i)))
            }
            return b.toString()
        }
    }

    private val startUTF8 = UTF8Sequence()
    private val endUTF8 = UTF8Sequence()

    private val tmpUTF8a = UTF8Sequence()
    private val tmpUTF8b = UTF8Sequence()

    // Builds necessary utf8 edges between start & end
    fun convertOneEdge(start: Int, end: Int, startCodePoint: Int, endCodePoint: Int) {
        startUTF8.set(startCodePoint)
        endUTF8.set(endCodePoint)
        build(start, end, startUTF8, endUTF8, 0)
    }

    private fun build(start: Int, end: Int, startUTF8: UTF8Sequence, endUTF8: UTF8Sequence, upto: Int) {
        // Break into start, middle, end:

        if (startUTF8.byteAt(upto) == endUTF8.byteAt(upto)) {
            // Degen case: lead with the same byte:
            if (upto == startUTF8.len - 1 && upto == endUTF8.len - 1) {
                // Super degen: just single edge, one UTF8 byte:
                utf8!!.addTransition(start, end, startUTF8.byteAt(upto), endUTF8.byteAt(upto))
                return
            } else {
                require(startUTF8.len > upto + 1)
                require(endUTF8.len > upto + 1)
                val n = utf8!!.createState()

                // Single value leading edge
                utf8!!.addTransition(start, n, startUTF8.byteAt(upto))

                // Recurse for the rest
                build(n, end, startUTF8, endUTF8, 1 + upto)
            }
        } else if (startUTF8.len == endUTF8.len) {
            if (upto == startUTF8.len - 1) {
                utf8!!.addTransition(start, end, startUTF8.byteAt(upto), endUTF8.byteAt(upto))
            } else {
                start(start, end, startUTF8, upto, false)
                if (endUTF8.byteAt(upto) - startUTF8.byteAt(upto) > 1) {
                    // There is a middle
                    all(
                        start,
                        end,
                        startUTF8.byteAt(upto) + 1,
                        endUTF8.byteAt(upto) - 1,
                        startUTF8.len - upto - 1
                    )
                }
                end(start, end, endUTF8, upto, false)
            }
        } else {
            // start

            start(start, end, startUTF8, upto, true)

            // possibly middle, spanning multiple num bytes
            var byteCount = 1 + startUTF8.len - upto
            val limit = endUTF8.len - upto
            while (byteCount < limit) {
                tmpUTF8a.setFirstByte(startCodes[byteCount - 1])
                tmpUTF8b.setFirstByte(endCodes[byteCount - 1])
                all(start, end, tmpUTF8a.byteAt(0), tmpUTF8b.byteAt(0), tmpUTF8a.len - 1)
                byteCount++
            }

            // end
            end(start, end, endUTF8, upto, true)
        }
    }

    private fun start(start: Int, end: Int, startUTF8: UTF8Sequence, upto: Int, doAll: Boolean) {
        if (upto == startUTF8.len - 1) {
            // Done recursing
            utf8!!.addTransition(
                start,
                end,
                startUTF8.byteAt(upto),
                startUTF8.byteAt(upto) or MASKS[startUTF8.numBits(upto)].toInt()
            ) // type=start
        } else {
            val n = utf8!!.createState()
            utf8!!.addTransition(start, n, startUTF8.byteAt(upto))
            start(n, end, startUTF8, 1 + upto, true)
            val endCode = startUTF8.byteAt(upto) or MASKS[startUTF8.numBits(upto)].toInt()
            if (doAll && startUTF8.byteAt(upto) != endCode) {
                all(start, end, startUTF8.byteAt(upto) + 1, endCode, startUTF8.len - upto - 1)
            }
        }
    }

    private fun end(start: Int, end: Int, endUTF8: UTF8Sequence, upto: Int, doAll: Boolean) {
        if (upto == endUTF8.len - 1) {
            // Done recursing
            utf8!!.addTransition(
                start, end, endUTF8.byteAt(upto) and (MASKS[endUTF8.numBits(upto)].inv().toInt()), endUTF8.byteAt(upto)
            )
        } else {
            val startCode: Int
            // GH-ISSUE#12472: UTF-8 special case for the different start byte of the different
            // length=2,3,4
            if (endUTF8.len == 2) {
                require(
                    upto == 0 // the upto==1 case will be handled by the first if above
                )
                // the first length=2 UTF8 Unicode character is C2 80,
                // so we must special case 0xC2 as the 1st byte.
                startCode = 0xC2
            } else if (endUTF8.len == 3 && upto == 1 && endUTF8.byteAt(0) == 0xE0) {
                // the first length=3 UTF8 Unicode character is E0 A0 80,
                // so we must special case 0xA0 as the 2nd byte when E0 was the first byte of endUTF8.
                startCode = 0xA0
            } else if (endUTF8.len == 4 && upto == 1 && endUTF8.byteAt(0) == 0xF0) {
                // the first length=4 UTF8 Unicode character is F0 90 80 80,
                // so we must special case 0x90 as the 2nd byte when F0 was the first byte of endUTF8.
                startCode = 0x90
            } else {
                startCode = endUTF8.byteAt(upto) and (MASKS[endUTF8.numBits(upto)].inv().toInt())
            }
            if (doAll && endUTF8.byteAt(upto) != startCode) {
                all(start, end, startCode, endUTF8.byteAt(upto) - 1, endUTF8.len - upto - 1)
            }
            val n = utf8!!.createState()
            utf8!!.addTransition(start, n, endUTF8.byteAt(upto))
            end(n, end, endUTF8, 1 + upto, true)
        }
    }

    private fun all(start: Int, end: Int, startCode: Int, endCode: Int, left: Int) {
        var left = left
        if (left == 0) {
            utf8!!.addTransition(start, end, startCode, endCode)
        } else {
            var lastN = utf8!!.createState()
            utf8!!.addTransition(start, lastN, startCode, endCode)
            while (left > 1) {
                val n = utf8!!.createState()
                utf8!!.addTransition(lastN, n, 128, 191) // type=all*
                left--
                lastN = n
            }
            utf8!!.addTransition(lastN, end, 128, 191) // type = all*
        }
    }

    var utf8: Automaton.Builder? = null

    /**
     * Converts an incoming utf32 automaton to an equivalent utf8 one. The incoming automaton need not
     * be deterministic. Note that the returned automaton will not in general be deterministic, so you
     * must determinize it if that's needed.
     */
    fun convert(utf32: Automaton): Automaton {
        if (utf32.numStates === 0) {
            return utf32
        }

        val map = IntArray(utf32.numStates)
        /*java.util.Arrays.fill(map, -1)*/
        map.fill(-1)

        val pending = IntArrayList()
        var utf32State = 0
        pending.add(utf32State)
        utf8 = Builder()

        var utf8State = utf8!!.createState()

        utf8!!.setAccept(utf8State, utf32.isAccept(utf32State))

        map[utf32State] = utf8State

        val scratch = Transition()

        while (pending.size() !== 0) {
            utf32State = pending.removeLast()
            utf8State = map[utf32State]
            require(utf8State != -1)

            val numTransitions = utf32.getNumTransitions(utf32State)
            utf32.initTransition(utf32State, scratch)
            for (i in 0..<numTransitions) {
                utf32.getNextTransition(scratch)
                val destUTF32 = scratch.dest
                var destUTF8 = map[destUTF32]
                if (destUTF8 == -1) {
                    destUTF8 = utf8!!.createState()
                    utf8!!.setAccept(destUTF8, utf32.isAccept(destUTF32))
                    map[destUTF32] = destUTF8
                    pending.add(destUTF32)
                }

                // Writes new transitions into pendingTransitions:
                convertOneEdge(utf8State, destUTF8, scratch.min, scratch.max)
            }
        }

        return utf8!!.finish()
    }

    companion object {
        // Unicode boundaries for UTF8 bytes 1,2,3,4
        private val startCodes = intArrayOf(0, 128, 2048, 65536)
        private val endCodes = intArrayOf(127, 2047, 65535, 1114111)

        private val MASKS = ByteArray(8)

        init {
            for (i in 0..6) {
                MASKS[i + 1] = ((2 shl i) - 1).toByte()
            }
        }
    }
}
