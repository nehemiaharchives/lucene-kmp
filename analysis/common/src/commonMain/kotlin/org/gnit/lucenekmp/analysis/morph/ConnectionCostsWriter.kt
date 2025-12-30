package org.gnit.lucenekmp.analysis.morph

import org.gnit.lucenekmp.codecs.CodecUtil
import org.gnit.lucenekmp.jdkport.BufferedOutputStream
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.store.OutputStreamDataOutput
import okio.IOException
import okio.Path
import kotlin.reflect.KClass

/** Writes connection costs */
class ConnectionCostsWriter<T : ConnectionCosts>(
    private val implClazz: KClass<T>,
    private val forwardSize: Int,
    private val backwardSize: Int
) {
    // array is backward IDs first since get is called using the same backward ID consecutively.
    private val costs: ShortArray = ShortArray(forwardSize * backwardSize)

    fun add(forwardId: Int, backwardId: Int, cost: Int) {
        val offset = (backwardId * forwardSize + forwardId)
        costs[offset] = cost.toShort()
    }

    private fun getBaseFileName(): String {
        val name = implClazz.qualifiedName
            ?: throw IllegalStateException("Class name unavailable for ${implClazz}")
        return name.replace('.', '/')
    }

    @Throws(IOException::class)
    fun write(baseDir: Path, connectionCostsCodecHeader: String, dictCodecVersion: Int) {
        Files.createDirectories(baseDir)
        val fileName = getBaseFileName() + ConnectionCosts.FILENAME_SUFFIX
        Files.newOutputStream(baseDir.resolve(fileName)).use { os ->
            BufferedOutputStream(os).use { bos ->
                val out: DataOutput = OutputStreamDataOutput(bos)
                CodecUtil.writeHeader(out, connectionCostsCodecHeader, dictCodecVersion)
                out.writeVInt(forwardSize)
                out.writeVInt(backwardSize)
                var last = 0
                for (i in costs.indices) {
                    val cost = costs[i].toInt()
                    val delta = cost - last
                    out.writeZInt(delta)
                    last = cost
                }
            }
        }
    }
}
