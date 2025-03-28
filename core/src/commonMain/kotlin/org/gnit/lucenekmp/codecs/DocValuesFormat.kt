package org.gnit.lucenekmp.codecs

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.util.NamedSPILoader


/**
 * Encodes/decodes per-document values.
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
 * @lucene.experimental
 */
abstract class DocValuesFormat protected constructor(name: String) : NamedSPILoader.NamedSPI {
    /**
     * This static holder class prevents classloading deadlock by delaying init of doc values formats
     * until needed.
     */
    private object Holder {
        private val LOADER: NamedSPILoader<DocValuesFormat> = NamedSPILoader(DocValuesFormat::class)

        val loader: NamedSPILoader<DocValuesFormat>
            get() {
                checkNotNull(LOADER) {
                    ("You tried to lookup a DocValuesFormat by name before all formats could be initialized. "
                            + "This likely happens if you call DocValuesFormat#forName from a DocValuesFormat's ctor.")
                }
                return LOADER
            }
    }

    /** Unique name that's used to retrieve this format when reading the index.  */
    val name: String

    /**
     * Creates a new docvalues format.
     *
     *
     * The provided name will be written into the index segment in some configurations (such as
     * when using `PerFieldDocValuesFormat`): in such configurations, for the segment to be read
     * this class should be registered with Java's SPI mechanism (registered in META-INF/ of your jar
     * file, etc).
     *
     * @param name must be all ascii alphanumeric, and less than 128 characters in length.
     */
    init {
        NamedSPILoader.checkServiceName(name)
        this.name = name
    }

    /** Returns a [DocValuesConsumer] to write docvalues to the index.  */
    @Throws(IOException::class)
    abstract fun fieldsConsumer(state: SegmentWriteState): DocValuesConsumer

    /**
     * Returns a [DocValuesProducer] to read docvalues from the index.
     *
     *
     * NOTE: by the time this call returns, it must hold open any files it will need to use; else,
     * those files may be deleted. Additionally, required files may be deleted during the execution of
     * this call before there is a chance to open them. Under these circumstances an IOException
     * should be thrown by the implementation. IOExceptions are expected and will automatically cause
     * a retry of the segment opening logic with the newly revised segments.
     */
    @Throws(IOException::class)
    abstract fun fieldsProducer(state: SegmentReadState): DocValuesProducer

    override fun toString(): String {
        return "DocValuesFormat(name=" + name + ")"
    }

    companion object {
        /** looks up a format by name  */
        fun forName(name: String): DocValuesFormat {
            return Holder.loader.lookup(name)
        }

        /** returns a list of all available format names  */
        fun availableDocValuesFormats(): MutableSet<String> {
            return Holder.loader.availableServices()
        }

        /**
         * Reloads the DocValues format list from the given [ClassLoader]. Changes to the docvalues
         * formats are visible after the method ends, all iterators ([ ][.availableDocValuesFormats],...) stay consistent.
         *
         *
         * **NOTE:** Only new docvalues formats are added, existing ones are never removed or
         * replaced.
         *
         *
         * *This method is expensive and should only be called for discovery of new docvalues
         * formats on the given classpath/classloader!*
         */
        /*
        NOT_TODO Will not implemented because ClassLoader is not available in Kotlin Multiplatform

        fun reloadDocValuesFormats(classloader: java.lang.ClassLoader) {
            Holder.loader.reload(classloader)
        }*/
    }
}
