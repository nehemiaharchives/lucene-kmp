package org.gnit.lucenekmp.codecs


import kotlinx.io.IOException
import org.gnit.lucenekmp.index.ByteVectorValues
import org.gnit.lucenekmp.index.FloatVectorValues
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.jdkport.ClassLoader
import org.gnit.lucenekmp.search.KnnCollector
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.NamedSPILoader
import org.gnit.lucenekmp.util.NamedSPILoader.NamedSPI

/**
 * Encodes/decodes per-document vector and any associated indexing structures required to support
 * nearest-neighbor search
 */
abstract class KnnVectorsFormat protected constructor(name: String) : NamedSPI {
    /**
     * This static holder class prevents classloading deadlock by delaying init of doc values formats
     * until needed.
     */
    private object Holder {
        private val LOADER: NamedSPILoader<KnnVectorsFormat> = NamedSPILoader(KnnVectorsFormat::class)

        val loader: NamedSPILoader<KnnVectorsFormat>
            get() {
                checkNotNull(LOADER) {
                    ("You tried to lookup a KnnVectorsFormat name before all formats could be initialized. "
                            + "This likely happens if you call KnnVectorsFormat#forName from a KnnVectorsFormat's ctor.")
                }
                return LOADER
            }
    }

    override lateinit var name: String

    /** Returns a [KnnVectorsWriter] to write the vectors to the index.  */
    @Throws(IOException::class)
    abstract fun fieldsWriter(state: SegmentWriteState): KnnVectorsWriter

    /** Returns a [KnnVectorsReader] to read the vectors from the index.  */
    @Throws(IOException::class)
    abstract fun fieldsReader(state: SegmentReadState): KnnVectorsReader

    /**
     * Returns the maximum number of vector dimensions supported by this codec for the given field
     * name
     *
     *
     * Codecs implement this method to specify the maximum number of dimensions they support.
     *
     * @param fieldName the field name
     * @return the maximum number of vector dimensions.
     */
    abstract fun getMaxDimensions(fieldName: String): Int

    /** Sole constructor  */
    init {
        NamedSPILoader.checkServiceName(name)
        this.name = name
    }

    companion object {
        /** The maximum number of vector dimensions  */
        const val DEFAULT_MAX_DIMENSIONS: Int = 1024

        /**
         * Reloads the KnnVectorsFormat list from the given [ClassLoader].
         *
         *
         * **NOTE:** Only new KnnVectorsFormat are added, existing ones are never removed or
         * replaced.
         *
         *
         * *This method is expensive and should only be called for discovery of new KnnVectorsFormat
         * on the given classpath/classloader!*
         */
        fun reloadKnnVectorsFormat(classloader: ClassLoader) {
            Holder.loader.reload(classloader)
        }

        /** looks up a format by name  */
        fun forName(name: String): KnnVectorsFormat {
            return Holder.loader.lookup(name)
        }

        /** returns a list of all available format names  */
        fun availableKnnVectorsFormats(): MutableSet<String> {
            return Holder.loader.availableServices()
        }

        /**
         * EMPTY throws an exception when written. It acts as a sentinel indicating a Codec that does not
         * support vectors.
         */
        val EMPTY: KnnVectorsFormat = object : KnnVectorsFormat("EMPTY") {
            override fun fieldsWriter(state: SegmentWriteState): KnnVectorsWriter {
                throw UnsupportedOperationException("Attempt to write EMPTY vector values")
            }

            override fun fieldsReader(state: SegmentReadState): KnnVectorsReader {
                return object : KnnVectorsReader() {
                    override fun checkIntegrity() {}

                    override fun getFloatVectorValues(field: String): FloatVectorValues {
                        throw UnsupportedOperationException()
                    }

                    override fun getByteVectorValues(field: String): ByteVectorValues {
                        throw UnsupportedOperationException()
                    }

                    override fun search(
                        field: String, target: FloatArray, knnCollector: KnnCollector, acceptDocs: Bits
                    ) {
                        throw UnsupportedOperationException()
                    }

                    override fun search(
                        field: String, target: ByteArray, knnCollector: KnnCollector, acceptDocs: Bits
                    ) {
                        throw UnsupportedOperationException()
                    }

                    override fun close() {}
                }
            }

            override fun getMaxDimensions(fieldName: String): Int {
                return 0
            }
        }
    }
}
