package org.gnit.lucenekmp.store

import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.RandomNumbers
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.IOConsumer
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import okio.IOException

internal typealias ThrowingBiFunction<T, U, R> = (T, U) -> R

abstract class BaseDataOutputTestCase<T : DataOutput> : LuceneTestCase() {
    protected abstract fun newInstance(): T

    protected abstract fun toBytes(instance: T): ByteArray

    @Throws(IOException::class)
    open fun testRandomizedWrites() {
        val dst = newInstance()
        val baos = ByteArrayOutputStream()
        val ref: DataOutput = OutputStreamDataOutput(baos)

        val seed = random().nextLong()
        val max = 50_000
        addRandomData(dst, Random(seed), max)
        addRandomData(ref, Random(seed), max)
        assertContentEquals(baos.toByteArray(), toBytes(dst))
    }

    companion object {
        private val GENERATORS: List<ThrowingBiFunction<DataOutput, Random, IOConsumer<DataInput>>>

        init {
            val list = mutableListOf<ThrowingBiFunction<DataOutput, Random, IOConsumer<DataInput>>>()

            // writeByte/ readByte
            list.add { dst, rnd ->
                val value = rnd.nextInt().toByte()
                dst.writeByte(value)
                IOConsumer { src -> assertEquals(value, src.readByte(), "readByte()") }
            }

            // writeBytes/ readBytes (array and buffer version)
            list.add { dst, rnd ->
                val bytes = ByteArray(RandomNumbers.randomIntBetween(rnd, 0, 100))
                rnd.nextBytes(bytes)
                val rdo = if (dst is ByteBuffersDataOutput) dst else null
                if (rnd.nextBoolean() && rdo != null) {
                    rdo.writeBytes(ByteBuffer.wrap(bytes))
                } else {
                    dst.writeBytes(bytes, bytes.size)
                }
                val useBuffersForRead = rnd.nextBoolean()
                IOConsumer { src ->
                    val read = ByteArray(bytes.size)
                    if (useBuffersForRead && src is ByteBuffersDataInput) {
                        (src as ByteBuffersDataInput).readBytes(ByteBuffer.wrap(read), read.size)
                        assertContentEquals(bytes, read, "readBytes(ByteBuffer)")
                    } else {
                        src.readBytes(read, 0, read.size)
                        assertContentEquals(bytes, read, "readBytes(byte[])")
                    }
                }
            }

            // writeBytes/ readBytes (array + offset)
            list.add { dst, rnd ->
                val bytes = ByteArray(RandomNumbers.randomIntBetween(rnd, 0, 100))
                rnd.nextBytes(bytes)
                val off = RandomNumbers.randomIntBetween(rnd, 0, bytes.size)
                val len = RandomNumbers.randomIntBetween(rnd, 0, bytes.size - off)
                dst.writeBytes(bytes, off, len)
                IOConsumer { src ->
                    val read = ByteArray(bytes.size + off)
                    src.readBytes(read, off, len)
                    assertContentEquals(
                        ArrayUtil.copyOfSubArray(bytes, off, len + off),
                        ArrayUtil.copyOfSubArray(read, off, len + off),
                        "readBytes(byte[], off)"
                    )
                }
            }

            // writeInt/ readInt
            list.add { dst, rnd ->
                val v = rnd.nextInt()
                dst.writeInt(v)
                IOConsumer { src -> assertEquals(v, src.readInt(), "readInt()") }
            }

            // writeLong/ readLong
            list.add { dst, rnd ->
                val v = rnd.nextLong()
                dst.writeLong(v)
                IOConsumer { src -> assertEquals(v, src.readLong(), "readLong()") }
            }

            // writeShort/ readShort
            list.add { dst, rnd ->
                val v = rnd.nextInt().toShort()
                dst.writeShort(v)
                IOConsumer { src -> assertEquals(v, src.readShort(), "readShort()") }
            }

            // writeVInt/ readVInt
            list.add { dst, rnd ->
                val v = rnd.nextInt()
                dst.writeVInt(v)
                IOConsumer { src -> assertEquals(v, src.readVInt(), "readVInt()") }
            }

            // writeZInt/ readZInt
            list.add { dst, rnd ->
                val v = rnd.nextInt()
                dst.writeZInt(v)
                IOConsumer { src -> assertEquals(v, src.readZInt(), "readZInt()") }
            }

            // writeVLong/ readVLong
            list.add { dst, rnd ->
                val v = rnd.nextLong() ushr 1
                dst.writeVLong(v)
                IOConsumer { src -> assertEquals(v, src.readVLong(), "readVLong()") }
            }

            // writeZLong/ readZLong
            list.add { dst, rnd ->
                val v = rnd.nextLong()
                dst.writeZLong(v)
                IOConsumer { src -> assertEquals(v, src.readZLong(), "readZLong()") }
            }

            // writeString/ readString
            list.add { dst, rnd ->
                val len = if (rnd.nextInt(50) == 0) {
                    RandomNumbers.randomIntBetween(rnd, 2048, 4096)
                } else {
                    RandomNumbers.randomIntBetween(rnd, 0, 10)
                }
                val v = TestUtil.randomUnicodeString(rnd, len)
                dst.writeString(v)
                IOConsumer { src -> assertEquals(v, src.readString(), "readString()") }
            }

            GENERATORS = list
        }
    }

    @Throws(IOException::class)
    protected fun addRandomData(dst: DataOutput, rnd: Random, maxAddCalls: Int): List<IOConsumer<DataInput>> {
        try {
            val reply = mutableListOf<IOConsumer<DataInput>>()
            repeat(maxAddCalls) {
                val generator = GENERATORS[rnd.nextInt(GENERATORS.size)]
                reply.add(generator(dst, rnd))
            }
            return reply
        } catch (e: Exception) {
            throw IOException(e.toString())
        }
    }
}

