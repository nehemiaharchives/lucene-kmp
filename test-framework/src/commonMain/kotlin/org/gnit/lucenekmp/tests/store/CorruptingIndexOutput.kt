package org.gnit.lucenekmp.tests.store

import okio.IOException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FilterIndexOutput
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput

/** Corrupts one bit of a file after close. */
open class CorruptingIndexOutput(
    protected val dir: Directory,
    private val byteToCorrupt: Long,
    out: IndexOutput
) : FilterIndexOutput("CorruptingIndexOutput($out)", out.name, out) {
    private var closed = false

    override fun close() {
        if (!closed) {
            out!!.close()
            corruptFile()
            closed = true
        }
    }

    @Throws(IOException::class)
    protected open fun corruptFile() {
        val newTempName: String
        dir.createTempOutput("tmp", "tmp", IOContext.DEFAULT).use { tmpOut ->
            dir.openInput(out!!.name!!, IOContext.DEFAULT).use { `in` ->
                newTempName = tmpOut.name!!
                if (byteToCorrupt >= `in`.length()) {
                    throw IllegalArgumentException(
                        "byteToCorrupt=$byteToCorrupt but file \"${out!!.name}\" is only length=${`in`.length()}"
                    )
                }
                tmpOut.copyBytes(`in`, byteToCorrupt)
                tmpOut.writeByte((`in`.readByte().toInt() xor 1).toByte())
                tmpOut.copyBytes(`in`, `in`.length() - byteToCorrupt - 1)
            }
        }

        dir.deleteFile(out!!.name!!)
        dir.copyFrom(dir, newTempName, out!!.name!!, IOContext.DEFAULT)
        dir.deleteFile(newTempName)
    }

    override fun getChecksum(): Long {
        return out!!.getChecksum() xor 1L
    }

    @Throws(IOException::class)
    override fun writeBytes(b: ByteArray, offset: Int, length: Int) {
        for (i in 0 until length) {
            writeByte(b[offset + i])
        }
    }
}
