package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.jdkport.ClassLoader
import org.gnit.lucenekmp.search.SortField
import org.gnit.lucenekmp.search.SortedNumericSortField
import org.gnit.lucenekmp.search.SortedSetSortField
import org.gnit.lucenekmp.store.DataInput
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.NamedSPILoader


/** Reads/Writes a named SortField from a segment info file, used to record index sorts
 *
 * Creates a new SortFieldProvider.
 *
 *
 * The provided name will be written into the index segment: in order to for the segment to be
 * read this class should be registered with Java's SPI mechanism (registered in META-INF/ of your
 * jar file, etc).
 *
 * @param name The name this SortFieldProvider is registered under.
 * must be all ascii alphanumeric, and less than 128 characters in length.
 * */
abstract class SortFieldProvider protected constructor(override val name: String) : NamedSPILoader.NamedSPI {
    private object Holder {
        private val LOADER: NamedSPILoader<SortFieldProvider> = NamedSPILoader(SortFieldProvider::class)

        val loader: NamedSPILoader<SortFieldProvider>
            get() {
                checkNotNull(LOADER) {
                    ("You tried to lookup a SortFieldProvider by name before all SortFieldProviders could be initialized. "
                            + "This likely happens if you call SortFieldProvider#forName from a SortFieldProviders's ctor.")
                }
                return LOADER
            }
    }

    /** Reads a SortField from serialized bytes  */
    @Throws(IOException::class)
    abstract fun readSortField(`in`: DataInput): SortField

    /**
     * Writes a SortField to a DataOutput
     *
     *
     * This is used to record index sort information in segment headers
     */
    @Throws(IOException::class)
    abstract fun writeSortField(sf: SortField, out: DataOutput)

    companion object {
        /** Looks up a SortFieldProvider by name  */
        fun forName(name: String): SortFieldProvider {
            return when (name) {
                SortField.Provider.NAME -> SortField.Provider()
                SortedSetSortField.Provider.NAME -> SortedSetSortField.Provider()
                SortedNumericSortField.Provider.NAME -> SortedNumericSortField.Provider()
                else -> {
                    try {
                        Holder.loader.lookup(name)
                    } catch (e: Throwable) {
                        throw UnsupportedOperationException(
                            "SortFieldProvider '$name' is not available. Known built-ins: " +
                                "[${SortField.Provider.NAME}, ${SortedSetSortField.Provider.NAME}, ${SortedNumericSortField.Provider.NAME}].",
                            e
                        )
                    }
                }
            }
        }

        /** Lists all available SortFieldProviders  */
        fun availableSortFieldProviders(): MutableSet<String> {
            return Holder.loader.availableServices()
        }

        /**
         * Reloads the SortFieldProvider list from the given [ClassLoader]. Changes to the list are
         * visible after the method ends, all iterators ([.availableSortFieldProviders] ()},...)
         * stay consistent.
         *
         *
         * **NOTE:** Only new SortFieldProviders are added, existing ones are never removed or
         * replaced.
         *
         *
         * *This method is expensive and should only be called for discovery of new
         * SortFieldProviders on the given classpath/classloader!*
         */
        // TODO java.lang.ClassLoader can not be used in platform agnostic kotlin common, needs to walk around
        fun reloadSortFieldProviders(classLoader: ClassLoader) {
            Holder.loader.reload(classLoader)
        }

        /** Writes a SortField to a DataOutput  */
        @Throws(IOException::class)
        fun write(sf: SortField, output: DataOutput) {
            val sorter: IndexSorter = sf.indexSorter!!
            requireNotNull(sorter) { "Cannot serialize sort field $sf" }
            val provider = forName(sorter.providerName)
            provider.writeSortField(sf, output)
        }
    }
}
