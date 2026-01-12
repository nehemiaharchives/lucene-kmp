package org.gnit.lucenekmp.tests.index

import okio.IOException
import org.gnit.lucenekmp.tests.index.MismatchedLeafReader
import org.gnit.lucenekmp.tests.index.MismatchedLeafReader.MismatchedVisitor
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.StoredFieldsReader
import org.gnit.lucenekmp.index.BinaryDocValues
import org.gnit.lucenekmp.index.CodecReader
import org.gnit.lucenekmp.index.DocValuesSkipper
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.FilterCodecReader
import org.gnit.lucenekmp.index.NumericDocValues
import org.gnit.lucenekmp.index.SortedDocValues
import org.gnit.lucenekmp.index.SortedNumericDocValues
import org.gnit.lucenekmp.index.SortedSetDocValues
import org.gnit.lucenekmp.index.StoredFieldVisitor
import org.gnit.lucenekmp.jdkport.assert
import kotlin.random.Random

/**
 * Shuffles field numbers around to try to trip bugs where field numbers are assumed to always be
 * consistent across segments.
 */
class MismatchedCodecReader(`in`: CodecReader, random: Random) :
    FilterCodecReader(`in`) {
    private val shuffled: FieldInfos

    /** Sole constructor.  */
    init {
        shuffled = MismatchedLeafReader.shuffleInfos(`in`.fieldInfos, random)
    }

    override val fieldInfos: FieldInfos
        get() = shuffled

    override val coreCacheHelper: CacheHelper?
        get() = `in`.coreCacheHelper

    override val readerCacheHelper: CacheHelper?
        get() = `in`.readerCacheHelper

    override val fieldsReader: StoredFieldsReader?
        get() {
            val `in`: StoredFieldsReader? = super.fieldsReader
            if (`in` == null) {
                return null
            }
            return MismatchedStoredFieldsReader(`in`, shuffled)
        }

    private class MismatchedStoredFieldsReader(
        `in`: StoredFieldsReader,
        shuffled: FieldInfos
    ) : StoredFieldsReader() {
        private val `in`: StoredFieldsReader
        private val shuffled: FieldInfos

        init {
            this.`in` = `in`
            this.shuffled = shuffled
        }

        @Throws(IOException::class)
        override fun close() {
            `in`.close()
        }

        override fun clone(): StoredFieldsReader {
            return MismatchedStoredFieldsReader(`in`.clone(), shuffled)
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            `in`.checkIntegrity()
        }

        @Throws(IOException::class)
        override fun document(docID: Int, visitor: StoredFieldVisitor) {
            `in`.document(docID, MismatchedVisitor(visitor, shuffled))
        }
    }

    override val docValuesReader: DocValuesProducer?
        get() {
            val `in`: DocValuesProducer? = super.docValuesReader
            if (`in` == null) {
                return null
            }
            return MismatchedDocValuesProducer(`in`, shuffled, super.fieldInfos)
        }

    private class MismatchedDocValuesProducer(
        `in`: DocValuesProducer,
        shuffled: FieldInfos,
        orig: FieldInfos
    ) : DocValuesProducer() {
        private val `in`: DocValuesProducer
        private val shuffled: FieldInfos
        private val orig: FieldInfos

        init {
            this.`in` = `in`
            this.shuffled = shuffled
            this.orig = orig
        }

        @Throws(IOException::class)
        override fun close() {
            `in`.close()
        }

        fun remapFieldInfo(field: FieldInfo): FieldInfo? {
            val fi: FieldInfo? = shuffled.fieldInfo(field.name)
            assert(fi != null && fi.number == field.number)
            return orig.fieldInfo(field.name)
        }

        @Throws(IOException::class)
        override fun getNumeric(field: FieldInfo): NumericDocValues {
            return `in`.getNumeric(remapFieldInfo(field)!!)
        }

        @Throws(IOException::class)
        override fun getBinary(field: FieldInfo): BinaryDocValues {
            return `in`.getBinary(remapFieldInfo(field)!!)
        }

        @Throws(IOException::class)
        override fun getSorted(field: FieldInfo): SortedDocValues {
            return `in`.getSorted(remapFieldInfo(field)!!)
        }

        @Throws(IOException::class)
        override fun getSortedNumeric(field: FieldInfo): SortedNumericDocValues {
            return `in`.getSortedNumeric(remapFieldInfo(field)!!)
        }

        @Throws(IOException::class)
        override fun getSortedSet(field: FieldInfo): SortedSetDocValues {
            return `in`.getSortedSet(remapFieldInfo(field)!!)
        }

        @Throws(IOException::class)
        override fun getSkipper(field: FieldInfo): DocValuesSkipper? {
            return `in`.getSkipper(remapFieldInfo(field)!!)
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            `in`.checkIntegrity()
        }
    }

    override val normsReader: NormsProducer?
        get() {
            val `in`: NormsProducer? = super.normsReader
            if (`in` == null) {
                return null
            }
            return MismatchedNormsProducer(`in`, shuffled, super.fieldInfos)
        }

    private class MismatchedNormsProducer(
        `in`: NormsProducer,
        shuffled: FieldInfos,
        orig: FieldInfos
    ) : NormsProducer() {
        private val `in`: NormsProducer
        private val shuffled: FieldInfos
        private val orig: FieldInfos

        init {
            this.`in` = `in`
            this.shuffled = shuffled
            this.orig = orig
        }

        @Throws(IOException::class)
        override fun close() {
            `in`.close()
        }

        fun remapFieldInfo(field: FieldInfo): FieldInfo? {
            val fi: FieldInfo? = shuffled.fieldInfo(field.name)
            assert(fi != null && fi.number == field.number)
            return orig.fieldInfo(field.name)
        }

        @Throws(IOException::class)
        override fun getNorms(field: FieldInfo): NumericDocValues {
            return `in`.getNorms(remapFieldInfo(field)!!)
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            `in`.checkIntegrity()
        }
    }
}
