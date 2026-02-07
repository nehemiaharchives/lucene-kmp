package org.gnit.lucenekmp.codecs

import org.gnit.lucenekmp.codecs.lucene101.Lucene101Codec
import org.gnit.lucenekmp.jdkport.ClassLoader
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.util.NamedSPILoader
import org.gnit.lucenekmp.util.NamedSPILoader.NamedSPI


/**
 * Encodes/decodes an inverted index segment.
 *
 *
 * Note, when extending this class, the name ([.getName]) is written into the index. In
 * order for the segment to be read, the name must resolve to your implementation via [ ][.forName]. This method uses Java's [Service Provider Interface][ServiceLoader] (SPI)
 * to resolve codec names.
 *
 *
 * If you implement your own codec, make sure that it has a no-arg constructor so SPI can load
 * it.
 *
 * @see ServiceLoader
 *
 * @param name Returns this codec's name.
 */
abstract class Codec protected constructor(override val name: String) : NamedSPI {

    /**
     * This static holder class prevents classloading deadlock by delaying init of default codecs and
     * available codecs until needed.
     */
    private object Holder {
        private val LOADER: NamedSPILoader<Codec> = NamedSPILoader(Codec::class)

        val loader: NamedSPILoader<Codec>
            get() {
                /*checkNotNull(Holder.LOADER) {("You tried to lookup a Codec by name before all Codecs could be initialized. "
                        + "This likely happens if you call Codec#forName from a Codec's ctor.")}
                return Holder.LOADER*/
                TODO() // ClassLoader needs to be implemented for this
            }

        // using hardcoded default codec because we cannot use NamedSPILoader in Kotlin Multiplatform
        var defaultCodec: Codec = /*Holder.LOADER.lookup("Lucene101")*/ Lucene101Codec()
    }

    /**
     * Creates a new codec.
     *
     *
     * The provided name will be written into the index segment: in order to for the segment to be
     * read this class should be registered with Java's SPI mechanism (registered in META-INF/ of your
     * jar file, etc).
     *
     * @param name must be all ascii alphanumeric, and less than 128 characters in length.
     */
    init {
        NamedSPILoader.checkServiceName(name)
        register(this)
    }

    /** Encodes/decodes postings  */
    abstract fun postingsFormat(): PostingsFormat

    /** Encodes/decodes docvalues  */
    abstract fun docValuesFormat(): DocValuesFormat

    /** Encodes/decodes stored fields  */
    abstract fun storedFieldsFormat(): StoredFieldsFormat

    /** Encodes/decodes term vectors  */
    abstract fun termVectorsFormat(): TermVectorsFormat

    /** Encodes/decodes field infos file  */
    abstract fun fieldInfosFormat(): FieldInfosFormat

    /** Encodes/decodes segment info file  */
    abstract fun segmentInfoFormat(): SegmentInfoFormat

    /** Encodes/decodes document normalization values  */
    abstract fun normsFormat(): NormsFormat

    /** Encodes/decodes live docs  */
    abstract fun liveDocsFormat(): LiveDocsFormat

    /** Encodes/decodes compound files  */
    abstract fun compoundFormat(): CompoundFormat

    /** Encodes/decodes points index  */
    abstract fun pointsFormat(): PointsFormat

    /** Encodes/decodes numeric vector fields  */
    abstract fun knnVectorsFormat(): KnnVectorsFormat

    /**
     * returns the codec's name. Subclasses can override to provide more detail (such as parameters).
     */
    override fun toString(): String {
        return name
    }
    companion object {
        private val registry: MutableMap<String, Codec> = mutableMapOf()
        private val registryLock = ReentrantLock()

        /** Register a codec instance for lookup by name. */
        fun register(codec: Codec) {
            registryLock.lock()
            try {
                if (registry.containsKey(codec.name).not()) {
                    registry[codec.name] = codec
                }
            } finally {
                registryLock.unlock()
            }
        }

        /** looks up a codec by name  */
        fun forName(name: String): Codec {
            registryLock.lock()
            try {
                return registry[name] ?: Holder.defaultCodec
            } finally {
                registryLock.unlock()
            }
        }

        /** returns a list of all available codec names  */
        fun availableCodecs(): MutableSet<String> {
            //return Holder.loader.availableServices()
            TODO() // ClassLoader needs to be implemented for this
        }

        /**
         * Reloads the codec list from the given [ClassLoader]. Changes to the codecs are visible
         * after the method ends, all iterators ([.availableCodecs],...) stay consistent.
         *
         *
         * **NOTE:** Only new codecs are added, existing ones are never removed or replaced.
         *
         *
         * *This method is expensive and should only be called for discovery of new codecs on the
         * given classpath/classloader!*
         */
        fun reloadCodecs(classloader: ClassLoader) {
            //Holder.loader.reload(classloader)
            TODO() // ClassLoader needs to be implemented for this
        }

        var default: Codec
            /** expert: returns the default codec used for newly created [IndexWriterConfig]s.  */
            get() {
                checkNotNull(Holder.defaultCodec) {("You tried to lookup the default Codec before all Codecs could be initialized. "
                        + "This likely happens if you try to get it from a Codec's ctor.")}
                return Holder.defaultCodec
            }
            /** expert: sets the default codec used for newly created [IndexWriterConfig]s.  */
            set(codec) {
                Holder.defaultCodec = requireNotNull<Codec>(codec)
            }

    }}
