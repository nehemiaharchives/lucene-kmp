package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.internal.hppc.IntObjectHashMap
import org.gnit.lucenekmp.internal.hppc.LongArrayList
import org.gnit.lucenekmp.store.Directory
import okio.IOException
import org.gnit.lucenekmp.jdkport.assert

/** Encapsulates multiple producers when there are docvalues updates as one producer  */ // TODO: try to clean up close no-op
// TODO: add shared base class (also used by per-field-pf) to allow "punching thru" to low level
// producer
internal class SegmentDocValuesProducer(
    si: SegmentCommitInfo,
    dir: Directory,
    coreInfos: FieldInfos,
    allInfos: FieldInfos,
    segDocValues: SegmentDocValues
) : DocValuesProducer() {
    val dvProducersByField: IntObjectHashMap<DocValuesProducer> =
        IntObjectHashMap()
    val dvProducers: MutableSet<DocValuesProducer> = mutableSetOf() // for now using mutableSetOf, but if IdentityHashMap specific feature is required and caused problem, we will come back and fix.
        /*java.util.Collections.newSetFromMap<DocValuesProducer>(java.util.IdentityHashMap<DocValuesProducer, Boolean>())*/
    val dvGens: LongArrayList = LongArrayList()

    /**
     * Creates a new producer that handles updated docvalues fields
     *
     * @param si commit point
     * @param dir directory
     * @param coreInfos fieldinfos for the segment
     * @param allInfos all fieldinfos including updated ones
     * @param segDocValues producer map
     */
    init {
        try {
            var baseProducer: DocValuesProducer? = null
            for (fi in allInfos) {
                if (fi.docValuesType == DocValuesType.NONE) {
                    continue
                }
                val docValuesGen: Long = fi.docValuesGen
                if (docValuesGen == -1L) {
                    if (baseProducer == null) {
                        // the base producer gets the original fieldinfos it wrote
                        baseProducer = segDocValues.getDocValuesProducer(docValuesGen, si, dir, coreInfos)
                        dvGens.add(docValuesGen)
                        dvProducers.add(baseProducer)
                    }
                    dvProducersByField.put(fi.number, baseProducer)
                } else {
                    assert(!dvGens.contains(docValuesGen))
                    // otherwise, producer sees only the one fieldinfo it wrote
                    val dvp: DocValuesProducer =
                        segDocValues.getDocValuesProducer(
                            docValuesGen,
                            si,
                            dir,
                            FieldInfos(arrayOf(fi))
                        )
                    dvGens.add(docValuesGen)
                    dvProducers.add(dvp)
                    dvProducersByField.put(fi.number, dvp)
                }
            }
        } catch (t: Throwable) {
            try {
                segDocValues.decRef(dvGens)
            } catch (t1: Throwable) {
                t.addSuppressed(t1)
            }
            throw t
        }
    }

    @Throws(IOException::class)
    override fun getNumeric(field: FieldInfo): NumericDocValues {
        val dvProducer: DocValuesProducer = checkNotNull(dvProducersByField[field.number])
        return dvProducer.getNumeric(field)
    }

    @Throws(IOException::class)
    override fun getBinary(field: FieldInfo): BinaryDocValues {
        val dvProducer: DocValuesProducer = checkNotNull(dvProducersByField[field.number])
        return dvProducer.getBinary(field)
    }

    @Throws(IOException::class)
    override fun getSorted(field: FieldInfo): SortedDocValues {
        val dvProducer: DocValuesProducer = checkNotNull(dvProducersByField[field.number])
        return dvProducer.getSorted(field)
    }

    @Throws(IOException::class)
    override fun getSortedNumeric(field: FieldInfo): SortedNumericDocValues {
        val dvProducer: DocValuesProducer = checkNotNull(dvProducersByField[field.number])
        return dvProducer.getSortedNumeric(field)
    }

    @Throws(IOException::class)
    override fun getSortedSet(field: FieldInfo): SortedSetDocValues {
        val dvProducer: DocValuesProducer = checkNotNull(dvProducersByField[field.number])
        return dvProducer.getSortedSet(field)
    }

    @Throws(IOException::class)
    override fun getSkipper(field: FieldInfo): DocValuesSkipper? {
        val dvProducer: DocValuesProducer = checkNotNull(dvProducersByField[field.number])
        return dvProducer.getSkipper(field)
    }

    @Throws(IOException::class)
    override fun checkIntegrity() {
        for (producer in dvProducers) {
            producer.checkIntegrity()
        }
    }

    @Throws(IOException::class)
    override fun close() {
        throw UnsupportedOperationException() // there is separate ref tracking
    }

    override fun toString(): String {
        return this::class.simpleName + "(producers=" + dvProducers.size + ")"
    }
}
