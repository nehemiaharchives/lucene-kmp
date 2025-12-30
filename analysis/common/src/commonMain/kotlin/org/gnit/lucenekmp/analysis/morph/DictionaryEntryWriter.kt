package org.gnit.lucenekmp.analysis.morph

import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.jdkport.BufferedOutputStream
import org.gnit.lucenekmp.jdkport.ByteBuffer
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.OutputStream
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.OutputStreamDataOutput
import okio.IOException
import okio.Path

/** Abstract writer class to write dictionary entries. */
abstract class DictionaryEntryWriter(size: Int) {
    protected var buffer: ByteBuffer = ByteBuffer.allocate(size)
    protected val posDict: MutableList<String?> = ArrayList()

    /** Writes an entry. */
    abstract fun putEntry(entry: Array<String>): Int

    /** Flush POS dictionary data. */
    @Throws(IOException::class)
    protected abstract fun writePosDict(bos: OutputStream, out: DataOutput)

    @Throws(IOException::class)
    fun writePosDict(path: Path, posDictCodecHeader: String, dictCodecVersion: Int) {
        path.parent?.let { Files.createDirectories(it) }
        Files.newOutputStream(path).use { os ->
            BufferedOutputStream(os).use { bos ->
                val out = OutputStreamDataOutput(bos)
                CodecUtil.writeHeader(out, posDictCodecHeader, dictCodecVersion)
                writePosDict(bos, out)
            }
        }
    }

    @Throws(IOException::class)
    fun writeDictionary(path: Path, dictCodecHeader: String, dictCodecVersion: Int) {
        path.parent?.let { Files.createDirectories(it) }
        Files.newOutputStream(path).use { os ->
            BufferedOutputStream(os).use { bos ->
                val out = OutputStreamDataOutput(bos)
                CodecUtil.writeHeader(out, dictCodecHeader, dictCodecVersion)
                out.writeVInt(buffer.position)
                buffer.flip()
                val remaining = buffer.remaining()
                val bytes = ByteArray(remaining)
                buffer.get(bytes)
                bos.write(bytes)
            }
        }
    }

    /** Returns current word id. */
    fun currentPosition(): Int = buffer.position
}
