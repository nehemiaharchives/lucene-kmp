package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.codecs.DocValuesFormat
import org.gnit.lucenekmp.codecs.DocValuesProducer
import org.gnit.lucenekmp.internal.hppc.LongArrayList
import org.gnit.lucenekmp.internal.hppc.LongObjectHashMap
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.RefCount
import okio.IOException
import org.gnit.lucenekmp.jdkport.Character

/**
 * Manages the [DocValuesProducer] held by [SegmentReader] and keeps track of their
 * reference counting.
 */
class SegmentDocValues {
    private val genDVProducers: LongObjectHashMap<RefCount<DocValuesProducer>> = LongObjectHashMap()

    @Throws(IOException::class)
    private fun newDocValuesProducer(
        si: SegmentCommitInfo,
        dir: Directory,
        gen: Long,
        infos: FieldInfos
    ): RefCount<DocValuesProducer> {
        var dvDir: Directory = dir
        var segmentSuffix = ""
        if (gen != -1L) {
            dvDir = si.info.dir // gen'd files are written outside CFS, so use SegInfo directory
            segmentSuffix = gen.toString(Character.MAX_RADIX.coerceIn(2, 36))
        }

        // set SegmentReadState to list only the fields that are relevant to that gen
        val srs = SegmentReadState(
            dvDir,
            si.info,
            infos,
            IOContext.DEFAULT,
            segmentSuffix
        )
        val dvFormat: DocValuesFormat = si.info.getCodec().docValuesFormat()
        return object :
            RefCount<DocValuesProducer>(dvFormat.fieldsProducer(srs)) {

            @Throws(IOException::class)
            override fun release() {
                `object`!!.close()

                // for now, we will commenting this out, if there is problem, we will implement
                //synchronized(this@SegmentDocValues) {
                genDVProducers.remove(gen)
                //}
            }
        }
    }

    /** Returns the [DocValuesProducer] for the given generation.  */
    /*@Synchronized*/
    @Throws(IOException::class)
    fun getDocValuesProducer(
        gen: Long,
        si: SegmentCommitInfo,
        dir: Directory,
        infos: FieldInfos
    ): DocValuesProducer {
        var dvp: RefCount<DocValuesProducer>? = genDVProducers.get(gen)
        if (dvp == null) {
            dvp = newDocValuesProducer(si, dir, gen, infos)
            checkNotNull(dvp)
            genDVProducers.put(gen, dvp)
        } else {
            dvp.incRef()
        }
        return dvp.get()!!
    }

    /** Decrement the reference count of the given [DocValuesProducer] generations.  */
    /*@Synchronized*/
    @Throws(IOException::class)
    fun decRef(dvProducersGens: LongArrayList) {
        IOUtils.applyToAll(
            dvProducersGens.map { it.value }
        ) { gen: Long ->
            val dvp: RefCount<DocValuesProducer> =
                checkNotNull(genDVProducers.get(gen)) { "gen=$gen" }
            dvp.decRef()
        }
    }
}
