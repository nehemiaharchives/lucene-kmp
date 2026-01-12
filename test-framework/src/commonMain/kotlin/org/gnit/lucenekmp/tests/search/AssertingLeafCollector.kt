package org.gnit.lucenekmp.tests.search

import okio.IOException
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.CheckedIntConsumer
import org.gnit.lucenekmp.search.DocIdSetIterator
import org.gnit.lucenekmp.search.DocIdStream
import org.gnit.lucenekmp.search.FilterLeafCollector
import org.gnit.lucenekmp.search.LeafCollector
import org.gnit.lucenekmp.search.Scorable
import org.gnit.lucenekmp.util.FixedBitSet


/** Wraps another Collector and checks that order is respected.  */
internal open class AssertingLeafCollector(
    collector: LeafCollector,
    private val min: Int,
    private val max: Int
) : FilterLeafCollector(collector) {
    private var lastCollected = -1
    private var finishCalled = false

    override var scorer: Scorable?
        get() {
            return super.scorer
        }
        set(value){
            super.scorer = AssertingScorable.wrap(value!!)
        }

    @Throws(IOException::class)
    override fun collect(stream: DocIdStream) {
        `in`.collect(AssertingDocIdStream(stream))
    }

    @Throws(IOException::class)
    override fun collect(doc: Int) {
        assert(doc > lastCollected) { "Out of order : $lastCollected $doc" }
        assert(doc >= min) { "Out of range: $doc < $min" }
        assert(doc < max) { "Out of range: $doc >= $max" }
        `in`.collect(doc)
        lastCollected = doc
    }

    @Throws(IOException::class)
    override fun competitiveIterator(): DocIdSetIterator? {
        val `in`: DocIdSetIterator? = this.`in`.competitiveIterator()
        if (`in` == null) {
            return null
        }
        return object : DocIdSetIterator() {
            @Throws(IOException::class)
            override fun nextDoc(): Int {
                assert(
                    `in`.docID() < max
                ) { "advancing beyond the end of the scored window: docID=" + `in`.docID() + ", max=" + max }
                return `in`.nextDoc()
            }

            override fun docID(): Int {
                return `in`.docID()
            }

            override fun cost(): Long {
                return `in`.cost()
            }

            @Throws(IOException::class)
            override fun advance(target: Int): Int {
                assert(
                    target <= max
                ) { "advancing beyond the end of the scored window: target=" + target + ", max=" + max }
                return `in`.advance(target)
            }

            @Throws(IOException::class)
            override fun intoBitSet(
                upTo: Int,
                bitSet: FixedBitSet,
                offset: Int
            ) {
                assert(
                    upTo <= max
                ) { "advancing beyond the end of the scored window: upTo=$upTo, max=$max" }
                `in`.intoBitSet(upTo, bitSet, offset)
                assert(`in`.docID() >= upTo)
            }
        }
    }

    @Throws(IOException::class)
    override fun finish() {
        assert(finishCalled == false)
        finishCalled = true
        super.finish()
    }

    private inner class AssertingDocIdStream(stream: DocIdStream) :
        DocIdStream() {
        private val stream: DocIdStream
        private var consumed = false

        init {
            this.stream = stream
        }

        @Throws(IOException::class)
        override fun forEach(consumer: CheckedIntConsumer<IOException>) {
            assert(consumed == false) { "A terminal operation has already been called" }
            stream.forEach { doc: Int ->
                assert(doc > lastCollected) { "Out of order : $lastCollected $doc" }
                assert(doc >= min) { "Out of range: $doc < $min" }
                assert(doc < max) { "Out of range: $doc >= $max" }
                consumer.accept(doc)
                lastCollected = doc
            }
            consumed = true
        }

        @Throws(IOException::class)
        override fun count(): Int {
            assert(consumed == false) { "A terminal operation has already been called" }
            val count: Int = stream.count()
            consumed = true
            return count
        }
    }
}
