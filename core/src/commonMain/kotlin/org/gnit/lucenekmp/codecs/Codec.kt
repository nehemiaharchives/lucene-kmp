package org.gnit.lucenekmp.codecs

import org.gnit.lucenekmp.codecs.lucene101.Lucene101Codec
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
        /*private val LOADER: NamedSPILoader<Codec> = NamedSPILoader(Codec::class)

        val loader: NamedSPILoader<Codec>
            get() {
                checkNotNull(Holder.LOADER) {("You tried to lookup a Codec by name before all Codecs could be initialized. "
                        + "This likely happens if you call Codec#forName from a Codec's ctor.")}
                return Holder.LOADER
            }*/

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
        /** looks up a codec by name  */
        fun forName(name: String): Codec {
            return /*Holder.loader.lookup(name)*/ Holder.defaultCodec
        }

        /** returns a list of all available codec names  */
        /*
        NOT_TODO Will not implemented because ClassLoader is not available in Kotlin Multiplatform

        fun availableCodecs(): MutableSet<String> {
            return Holder.loader.availableServices()
        }*/

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
        /*
        NOT_TODO Will not implemented because ClassLoader is not available in Kotlin Multiplatform

        fun reloadCodecs(classloader: java.lang.ClassLoader) {
            Holder.loader.reload(classloader)
        }*/

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
