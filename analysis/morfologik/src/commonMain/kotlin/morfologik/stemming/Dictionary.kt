package morfologik.stemming

import okio.Path
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.InputStream
import morfologik.fsa.FSA

/**
 * A dictionary combines [FSA] automaton and [DictionaryMetadata]
 * describing the way terms are encoded in the automaton.
 */
class Dictionary(val fsa: FSA, val metadata: DictionaryMetadata) {
    companion object {
        @Throws(okio.IOException::class)
        fun read(location: Path): Dictionary {
            val metadataPath = DictionaryMetadata.getExpectedMetadataLocation(location)
            val fsaStream = Files.newInputStream(location)
            try {
                val metadataStream = Files.newInputStream(metadataPath)
                try {
                    return read(fsaStream, metadataStream)
                } finally {
                    metadataStream.close()
                }
            } finally {
                fsaStream.close()
            }
        }

        @Throws(okio.IOException::class)
        fun read(fsaStream: InputStream, metadataStream: InputStream): Dictionary {
            return Dictionary(FSA.read(fsaStream), DictionaryMetadata.read(metadataStream))
        }
    }
}
