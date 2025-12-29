package morfologik.fsa

import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.jdkport.OutputStream

/**
 * Standard FSA file header.
 */
class FSAHeader(val version: Byte) {
    companion object {
        private const val FSA_MAGIC: Int =
            ('\\'.code shl 24) or ('f'.code shl 16) or ('s'.code shl 8) or ('a'.code)

        fun read(input: InputStream): FSAHeader {
            if (input.read() != (FSA_MAGIC ushr 24) ||
                input.read() != ((FSA_MAGIC ushr 16) and 0xFF) ||
                input.read() != ((FSA_MAGIC ushr 8) and 0xFF) ||
                input.read() != (FSA_MAGIC and 0xFF)
            ) {
                throw okio.IOException("Invalid file header, probably not an FSA.")
            }

            val version = input.read()
            if (version == -1) {
                throw okio.IOException("Truncated file, no version number.")
            }
            return FSAHeader(version.toByte())
        }

        fun write(output: OutputStream, version: Byte) {
            output.write(FSA_MAGIC ushr 24)
            output.write(FSA_MAGIC ushr 16)
            output.write(FSA_MAGIC ushr 8)
            output.write(FSA_MAGIC)
            output.write(version.toInt())
        }
    }
}
