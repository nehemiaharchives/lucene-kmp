package org.gnit.lucenekmp.codecs


import okio.IOException
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.util.NamedSPILoader


/**
 * Encodes/decodes terms, postings, and proximity data.
 *
 *
 * Note, when extending this class, the name ([.getName]) may written into the index in
 * certain configurations. In order for the segment to be read, the name must resolve to your
 * implementation via [.forName]. This method uses Java's [Service][ServiceLoader] (SPI) to resolve format names.
 *
 *
 * If you implement your own format, make sure that it has a no-arg constructor so SPI can load
 * it.
 *
 * @see ServiceLoader
 *
 * @param name Returns this posting format's name.
 * Unique name that's used to retrieve this format when reading the index.
 *
 *
 * @lucene.experimental
 */
abstract class PostingsFormat protected constructor(override val name: String) : NamedSPILoader.NamedSPI {
    /**
     * This static holder class prevents classloading deadlock by delaying init of postings formats
     * until needed.
     */
    private object Holder {
        private val LOADER: NamedSPILoader<PostingsFormat> = NamedSPILoader(PostingsFormat::class)

        val loader: NamedSPILoader<PostingsFormat>
            get() {
                checkNotNull(LOADER) {
                    ("You tried to lookup a PostingsFormat by name before all formats could be initialized. "
                            + "This likely happens if you call PostingsFormat#forName from a PostingsFormat's ctor.")
                }
                return LOADER
            }
    }

    /**
     * Creates a new postings format.
     *
     *
     * The provided name will be written into the index segment in some configurations (such as
     * when using [PerFieldPostingsFormat]): in such configurations, for the segment to be read
     * this class should be registered with Java's SPI mechanism (registered in META-INF/ of your jar
     * file, etc).
     *
     * @param name must be all ascii alphanumeric, and less than 128 characters in length.
     */
    init {
        // TODO: can we somehow detect name conflicts here  Two different classes trying to claim the
        // same name  Otherwise you see confusing errors...
        NamedSPILoader.checkServiceName(name)
    }

    /** Writes a new segment  */
    @Throws(IOException::class)
    abstract fun fieldsConsumer(state: SegmentWriteState): FieldsConsumer

    /**
     * Reads a segment. NOTE: by the time this call returns, it must hold open any files it will need
     * to use; else, those files may be deleted. Additionally, required files may be deleted during
     * the execution of this call before there is a chance to open them. Under these circumstances an
     * IOException should be thrown by the implementation. IOExceptions are expected and will
     * automatically cause a retry of the segment opening logic with the newly revised segments.
     */
    @Throws(IOException::class)
    abstract fun fieldsProducer(state: SegmentReadState): FieldsProducer

    override fun toString(): String {
        return "PostingsFormat(name=$name)"
    }

    companion object {
        /** Zero-length `PostingsFormat` array.  */
        val EMPTY: Array<PostingsFormat> = emptyArray<PostingsFormat>()

        /** looks up a format by name  */
        fun forName(name: String): PostingsFormat {
            return Holder.loader.lookup(name)
        }

        /** returns a list of all available format names  */
        fun availablePostingsFormats(): MutableSet<String> {
            return Holder.loader.availableServices()
        }

        /**
         * Reloads the postings format list from the given [ClassLoader]. Changes to the postings
         * formats are visible after the method ends, all iterators ([ ][.availablePostingsFormats],...) stay consistent.
         *
         *
         * **NOTE:** Only new postings formats are added, existing ones are never removed or
         * replaced.
         *
         *
         * *This method is expensive and should only be called for discovery of new postings formats
         * on the given classpath/classloader!*
         */
        /*
        //TODO will not implemented for now

        fun reloadPostingsFormats(classloader: java.lang.ClassLoader) {
            Holder.loader.reload(classloader)
        }*/
    }
}
