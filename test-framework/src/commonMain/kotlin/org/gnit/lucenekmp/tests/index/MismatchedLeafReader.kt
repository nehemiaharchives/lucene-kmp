package org.gnit.lucenekmp.tests.index

import okio.IOException
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.FieldInfos
import org.gnit.lucenekmp.index.FilterLeafReader
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.StoredFieldVisitor
import org.gnit.lucenekmp.index.StoredFields
import kotlin.random.Random

/**
 * Shuffles field numbers around to try to trip bugs where field numbers are assumed to always be
 * consistent across segments.
 */
class MismatchedLeafReader(`in`: LeafReader, random: Random) :
    FilterLeafReader(`in`) {
    val shuffled: FieldInfos = shuffleInfos(`in`.fieldInfos, random)

    override val fieldInfos: FieldInfos
        get() = shuffled

    @Throws(IOException::class)
    override fun storedFields(): StoredFields {
        val inStoredFields: StoredFields = `in`.storedFields()
        return object : StoredFields() {
            @Throws(IOException::class)
            override fun document(docID: Int, visitor: StoredFieldVisitor) {
                inStoredFields.document(docID, MismatchedVisitor(visitor, shuffled))
            }
        }
    }

    override val coreCacheHelper: CacheHelper?
        get() = `in`.coreCacheHelper

    override val readerCacheHelper: CacheHelper?
        get() = `in`.readerCacheHelper

    /** StoredFieldsVisitor that remaps actual field numbers to our new shuffled ones.  */ // TODO: its strange this part of our IR api exposes FieldInfo,
    // no other "user-accessible" codec apis do this
    internal class MismatchedVisitor(
        val `in`: StoredFieldVisitor,
        val shuffled: FieldInfos
    ) : StoredFieldVisitor() {

        @Throws(IOException::class)
        override fun binaryField(fieldInfo: FieldInfo, value: ByteArray) {
            `in`.binaryField(renumber(fieldInfo), value)
        }

        @Throws(IOException::class)
        override fun stringField(fieldInfo: FieldInfo, value: String) {
            `in`.stringField(
                renumber(fieldInfo),
                value
            )
        }

        @Throws(IOException::class)
        override fun intField(fieldInfo: FieldInfo, value: Int) {
            `in`.intField(renumber(fieldInfo), value)
        }

        @Throws(IOException::class)
        override fun longField(fieldInfo: FieldInfo, value: Long) {
            `in`.longField(renumber(fieldInfo), value)
        }

        @Throws(IOException::class)
        override fun floatField(fieldInfo: FieldInfo, value: Float) {
            `in`.floatField(renumber(fieldInfo), value)
        }

        @Throws(IOException::class)
        override fun doubleField(fieldInfo: FieldInfo, value: Double) {
            `in`.doubleField(renumber(fieldInfo), value)
        }

        @Throws(IOException::class)
        override fun needsField(fieldInfo: FieldInfo): Status? {
            return `in`.needsField(renumber(fieldInfo))
        }

        fun renumber(original: FieldInfo): FieldInfo {
            val renumbered: FieldInfo? = shuffled.fieldInfo(original.name)
            if (renumbered == null) {
                throw AssertionError("stored fields sending bogus infos!")
            }
            return renumbered
        }
    }

    companion object {
        fun shuffleInfos(
            infos: FieldInfos,
            random: Random
        ): FieldInfos {
            // first, shuffle the order
            val shuffled: MutableList<FieldInfo> = mutableListOf()
            for (info in infos) {
                shuffled.add(info)
            }
            shuffled.shuffle(random)

            // now renumber:
            for (i in shuffled.indices) {
                val oldInfo: FieldInfo = shuffled[i]
                // TODO: should we introduce "gaps" too
                val newInfo =
                    FieldInfo(
                        oldInfo.name,  // name
                        i,  // number
                        oldInfo.hasTermVectors(),  // storeTermVector
                        oldInfo.omitsNorms(),  // omitNorms
                        oldInfo.hasPayloads(),  // storePayloads
                        oldInfo.indexOptions,  // indexOptions
                        oldInfo.docValuesType,  // docValuesType
                        oldInfo.docValuesSkipIndexType(),  // docValuesSkipIndexType
                        oldInfo.docValuesGen,  // dvGen
                        oldInfo.attributes(),  // attributes
                        oldInfo.pointDimensionCount,  // data dimension count
                        oldInfo.pointIndexDimensionCount,  // index dimension count
                        oldInfo.pointNumBytes,  // dimension numBytes
                        oldInfo.vectorDimension,  // number of dimensions of the field's vector
                        oldInfo.vectorEncoding,  // numeric type of vector samples
                        // distance function for calculating similarity of the field's vector
                        oldInfo.vectorSimilarityFunction,
                        oldInfo.isSoftDeletesField,  // used as soft-deletes field
                        oldInfo.isParentField
                    )
                shuffled[i] = newInfo
            }

            return FieldInfos(shuffled.toTypedArray<FieldInfo>())
        }
    }
}
