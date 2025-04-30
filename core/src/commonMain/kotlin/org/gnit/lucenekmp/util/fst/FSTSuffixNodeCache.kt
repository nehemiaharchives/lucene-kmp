package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.util.fst.FST.Arc
import org.gnit.lucenekmp.util.fst.FST.BytesReader
import org.gnit.lucenekmp.util.fst.FSTCompiler.CompiledNode
import org.gnit.lucenekmp.util.fst.FSTCompiler.UnCompiledNode
import org.gnit.lucenekmp.util.ByteBlockPool
import org.gnit.lucenekmp.util.ByteBlockPool.DirectAllocator
import org.gnit.lucenekmp.util.packed.PackedInts
import org.gnit.lucenekmp.util.packed.PagedGrowableWriter
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Math
import kotlin.math.max


// TODO: any way to make a reverse suffix lookup (msokolov's idea) instead of more costly hash
// hmmm, though, hash is not so wasteful
// since it does not have to store value of each entry: the value is the node pointer in the FST.
// actually, there is much to save
// there -- we would not need any long per entry -- we'd be able to start at the FST end node and
// work backwards from the transitions
// TODO: couldn't we prune naturally back until we see a transition with an output  it's highly
// unlikely (mostly impossible) such suffixes can be shared
/**
 * This is essentially a LRU cache to maintain and lookup node suffix. Un-compiled node can be added
 * into the cache and if a similar node exists we will return its address in the FST. A node is
 * defined as similar if it has the same label, arcs, outputs & other properties that identify a
 * node.
 *
 *
 * The total size of the cache is controlled through the constructor parameter `ramLimitMB
` *  Implementation-wise, we maintain two lookup tables, a primary table where node can be
 * looked up from, and a fallback lookup table in case the lookup in the primary table fails. Nodes
 * from the fallback table can also be promoted to the primary table when that happens. When the
 * primary table is full, we swap it with the fallback table and clear out the primary table.
 *
 *
 * To lookup the node address, we build a special hash table which maps from the Node hash value
 * to the Node address in the FST, called `PagedGrowableHash`. Internally it uses [ ] to store the mapping, which allows efficient packing the hash & address long
 * values, and uses [ByteBlockPool] to store the actual node content (arcs & outputs).
 */
internal class FSTSuffixNodeCache<T>(fstCompiler: FSTCompiler<T>, ramLimitMB: Double) {
    // primary table -- we add nodes into this until it reaches the requested tableSizeLimit/2, then
    // we move it to fallback
    private var primaryTable: PagedGrowableHash

    // how many nodes are allowed to store in both primary and fallback tables; when primary gets full
    // (tableSizeLimit/2), we move it to the
    // fallback table
    private val ramLimitBytes: Long

    // fallback table.  if we fallback and find the frozen node here, we promote it to primary table,
    // for a simplistic and lowish-RAM-overhead
    // (compared to e.g. LinkedHashMap) LRU behaviour.  fallbackTable is read-only.
    private var fallbackTable: PagedGrowableHash? = null

    private val fstCompiler: FSTCompiler<T>
    private val scratchArc: Arc<T> = Arc()

    // store the last fallback table node length in getFallback()
    private var lastFallbackNodeLength = 0

    // store the last fallback table hashtable slot in getFallback()
    private var lastFallbackHashSlot: Long = 0

    /**
     * ramLimitMB is the max RAM we can use for recording suffixes. If we hit this limit, the least
     * recently used suffixes are discarded, and the FST is no longer minimalI. Still, larger
     * ramLimitMB will make the FST smaller (closer to minimal).
     */
    init {
        require(!(ramLimitMB <= 0)) { "ramLimitMB must be > 0; got: $ramLimitMB" }
        val asBytes = ramLimitMB * 1024 * 1024
        ramLimitBytes = if (asBytes >= Long.Companion.MAX_VALUE) {
            // quietly truncate to Long.MAX_VALUE in bytes too
            Long.Companion.MAX_VALUE
        } else {
            asBytes.toLong()
        }

        primaryTable = PagedGrowableHash()
        this.fstCompiler = fstCompiler
    }

    @Throws(IOException::class)
    private fun getFallback(nodeIn: UnCompiledNode<T>, hash: Long): Long {
        this.lastFallbackNodeLength = -1
        this.lastFallbackHashSlot = -1
        if (fallbackTable == null) {
            // no fallback yet (primary table is not yet large enough to swap)
            return 0
        }
        var hashSlot = hash and fallbackTable!!.mask
        var c = 0
        while (true) {
            val nodeAddress = fallbackTable!!.getNodeAddress(hashSlot)
            if (nodeAddress == 0L) {
                // not found
                return 0
            } else {
                val length = fallbackTable!!.nodesEqual(nodeIn, nodeAddress, hashSlot)
                if (length != -1) {
                    // store the node length for further use
                    this.lastFallbackNodeLength = length
                    this.lastFallbackHashSlot = hashSlot
                    // frozen version of this node is already here
                    return nodeAddress
                }
            }

            // quadratic probe (but is it, really)
            hashSlot = (hashSlot + (++c)) and fallbackTable!!.mask
        }
    }

    @Throws(IOException::class)
    fun add(nodeIn: UnCompiledNode<T>): Long {
        val hash = hash(nodeIn)

        var hashSlot = hash and primaryTable.mask
        var c = 0

        while (true) {
            var nodeAddress = primaryTable.getNodeAddress(hashSlot)
            if (nodeAddress == 0L) {
                // node is not in primary table; is it in fallback table
                nodeAddress = getFallback(nodeIn, hash)
                if (nodeAddress != 0L) {
                    require(lastFallbackHashSlot != -1L && lastFallbackNodeLength != -1)

                    // it was already in fallback -- promote to primary
                    primaryTable.setNodeAddress(hashSlot, nodeAddress)
                    primaryTable.copyFallbackNodeBytes(
                        hashSlot, fallbackTable!!, lastFallbackHashSlot, lastFallbackNodeLength
                    )
                } else {
                    // not in fallback either -- freeze & add the incoming node

                    // freeze & add

                    nodeAddress = fstCompiler.addNode(nodeIn)

                    // we use 0 as empty marker in hash table, so it better be impossible to get a frozen node
                    // at 0:
                    require(nodeAddress != FST.FINAL_END_NODE && nodeAddress != FST.NON_FINAL_END_NODE)

                    primaryTable.setNodeAddress(hashSlot, nodeAddress)
                    primaryTable.copyNodeBytes(
                        hashSlot,
                        fstCompiler.scratchBytes.bytes,
                        fstCompiler.scratchBytes.position
                    )

                    // confirm frozen hash and unfrozen hash are the same
                    require(
                        primaryTable.hash(nodeAddress, hashSlot) == hash
                    ) {
                        ("mismatch frozenHash="
                                + primaryTable.hash(nodeAddress, hashSlot)
                                + " vs hash="
                                + hash)
                    }
                }

                // how many bytes would be used if we had "perfect" hashing:
                //  - x2 for fstNodeAddress for FST node address
                //  - x2 for copiedNodeAddress for copied node address
                //  - the bytes copied out FST to the hashtable copiedNodes
                // each account for approximate hash table overhead halfway between 33.3% and 66.6%
                // note that some of the copiedNodes are shared between fallback and primary tables so this
                // computation is pessimistic
                val copiedBytes: Long = primaryTable.copiedNodes.position
                val ramBytesUsed: Long =
                    (primaryTable.count * 2 * PackedInts.bitsRequired(nodeAddress) / 8 + primaryTable.count * 2 * PackedInts.bitsRequired(
                        copiedBytes
                    ) / 8 + copiedBytes)

                // NOTE: we could instead use the more precise RAM used, but this leads to unpredictable
                // quantized behavior due to 2X rehashing where for large ranges of the RAM limit, the
                // size of the FST does not change, and then suddenly when you cross a secret threshold,
                // it drops.  With this approach (measuring "perfect" hash storage and approximating the
                // overhead), the behaviour is more strictly monotonic: larger RAM limits smoothly result
                // in smaller FSTs, even if the precise RAM used is not always under the limit.

                // divide limit by 2 because fallback gets half the RAM and primary gets the other half
                if (ramBytesUsed >= ramLimitBytes / 2) {
                    // time to fallback -- fallback is now used read-only to promote a node (suffix) to
                    // primary if we encounter it again
                    fallbackTable = primaryTable
                    // size primary table the same size to reduce rehash cost
                    // TODO: we could clear & reuse the previous fallbackTable, instead of allocating a new
                    //       to reduce GC load
                    primaryTable =
                        PagedGrowableHash(nodeAddress, max(16, primaryTable.fstNodeAddress.size()))
                } else if (primaryTable.count > primaryTable.fstNodeAddress.size() * (2f / 3)) {
                    // rehash at 2/3 occupancy
                    primaryTable.rehash(nodeAddress)
                }

                return nodeAddress
            } else if (primaryTable.nodesEqual(nodeIn, nodeAddress, hashSlot) != -1) {
                // same node (in frozen form) is already in primary table
                return nodeAddress
            }

            // quadratic probe (but is it, really)
            hashSlot = (hashSlot + (++c)) and primaryTable.mask
        }
    }

    // hash code for an unfrozen node.  This must be identical
    // to the frozen case (below)!!
    private fun hash(node: UnCompiledNode<T>): Long {
        val PRIME = 31
        var h: Long = 0
        // TODO: maybe if number of arcs is high we can safely subsample
        for (arcIdx in 0..<node.numArcs) {
            val arc: FSTCompiler.Arc<T> = node.arcs[arcIdx]
            h = PRIME * h + arc.label
            val n: Long = (arc.target as CompiledNode).node
            h = PRIME * h + (n xor (n shr 32)).toInt()
            h = PRIME * h + arc.output.hashCode()
            h = PRIME * h + arc.nextFinalOutput.hashCode()
            if (arc.isFinal) {
                h += 17
            }
        }

        return h
    }

    /** Inner class because it needs access to hash function and FST bytes.  */
    internal inner class PagedGrowableHash {
        // storing the FST node address where the position is the masked hash of the node arcs
        var fstNodeAddress: PagedGrowableWriter

        // storing the local copiedNodes address in the same position as fstNodeAddress
        // here we are effectively storing a Map<Long, Long> from the FST node address to copiedNodes
        // address
        private var copiedNodeAddress: PagedGrowableWriter
        var count: Long = 0
        var mask: Long

        // storing the byte slice from the FST for nodes we added to the hash so that we don't need to
        // look up from the FST itself, so the FST bytes can stream directly to disk as append-only
        // writes.
        // each node will be written subsequently
        val copiedNodes: ByteBlockPool

        // the {@link FST.BytesReader} to read from copiedNodes. we use this when computing a frozen
        // node hash
        // or comparing if a frozen and unfrozen nodes are equal
        private val bytesReader: ByteBlockPoolReverseBytesReader

        constructor() {
            fstNodeAddress = PagedGrowableWriter(16, BLOCK_SIZE_BYTES, 8, PackedInts.COMPACT)
            copiedNodeAddress = PagedGrowableWriter(16, BLOCK_SIZE_BYTES, 8, PackedInts.COMPACT)
            mask = 15
            copiedNodes = ByteBlockPool(DirectAllocator())
            bytesReader = ByteBlockPoolReverseBytesReader(copiedNodes)
        }

        constructor(lastNodeAddress: Long, size: Long) {
            fstNodeAddress =
                PagedGrowableWriter(
                    size, BLOCK_SIZE_BYTES, PackedInts.bitsRequired(lastNodeAddress), PackedInts.COMPACT
                )
            copiedNodeAddress = PagedGrowableWriter(size, BLOCK_SIZE_BYTES, 8, PackedInts.COMPACT)
            mask = size - 1
            require((mask and size) == 0L) { "size must be a power-of-2; got size=$size mask=$mask" }
            copiedNodes = ByteBlockPool(DirectAllocator())
            bytesReader = ByteBlockPoolReverseBytesReader(copiedNodes)
        }

        /**
         * Get the copied bytes at the provided hash slot
         *
         * @param hashSlot the hash slot to read from
         * @param length the number of bytes to read
         * @return the copied byte array
         */
        fun getBytes(hashSlot: Long, length: Int): ByteArray {
            val address: Long = copiedNodeAddress.get(hashSlot)
            require(address - length + 1 >= 0)
            val buf = ByteArray(length)
            copiedNodes.readBytes(address - length + 1, buf, 0, length)
            return buf
        }

        /**
         * Get the node address from the provided hash slot
         *
         * @param hashSlot the hash slot to read
         * @return the node address
         */
        fun getNodeAddress(hashSlot: Long): Long {
            return fstNodeAddress.get(hashSlot)
        }

        /**
         * Set the node address from the provided hash slot
         *
         * @param hashSlot the hash slot to write to
         * @param nodeAddress the node address
         */
        fun setNodeAddress(hashSlot: Long, nodeAddress: Long) {
            require(fstNodeAddress.get(hashSlot) == 0L)
            fstNodeAddress.set(hashSlot, nodeAddress)
            count++
        }

        /** copy the node bytes from the FST  */
        fun copyNodeBytes(hashSlot: Long, bytes: ByteArray, length: Int) {
            require(copiedNodeAddress.get(hashSlot) == 0L)
            copiedNodes.append(bytes, 0, length)
            // write the offset, which points to the last byte of the node we copied since we later read
            // this node in reverse
            copiedNodeAddress.set(hashSlot, copiedNodes.position - 1)
        }

        /** promote the node bytes from the fallback table  */
        fun copyFallbackNodeBytes(
            hashSlot: Long, fallbackTable: PagedGrowableHash, fallbackHashSlot: Long, nodeLength: Int
        ) {
            require(copiedNodeAddress.get(hashSlot) == 0L)
            val fallbackAddress: Long = fallbackTable.copiedNodeAddress.get(fallbackHashSlot)
            // fallbackAddress is the last offset of the node, but we need to copy the bytes from the
            // start address
            val fallbackStartAddress = fallbackAddress - nodeLength + 1
            require(fallbackStartAddress >= 0)
            copiedNodes.append(fallbackTable.copiedNodes, fallbackStartAddress, nodeLength)
            // write the offset, which points to the last byte of the node we copied since we later read
            // this node in reverse
            copiedNodeAddress.set(hashSlot, copiedNodes.position - 1)
        }

        @Throws(IOException::class)
        fun rehash(lastNodeAddress: Long) {
            // TODO: https://github.com/apache/lucene/issues/12744
            // should we always use a small startBitsPerValue here (e.g 8) instead base off of
            // lastNodeAddress

            // double hash table size on each rehash

            val newSize: Long = 2 * fstNodeAddress.size()
            val newCopiedNodeAddress =
                PagedGrowableWriter(
                    newSize,
                    BLOCK_SIZE_BYTES,
                    PackedInts.bitsRequired(copiedNodes.position),
                    PackedInts.COMPACT
                )
            val newFSTNodeAddress =
                PagedGrowableWriter(
                    newSize,
                    BLOCK_SIZE_BYTES,
                    PackedInts.bitsRequired(lastNodeAddress),
                    PackedInts.COMPACT
                )
            val newMask: Long = newFSTNodeAddress.size() - 1
            for (idx in 0..<fstNodeAddress.size()) {
                val address: Long = fstNodeAddress.get(idx)
                if (address != 0L) {
                    var hashSlot = hash(address, idx) and newMask
                    var c = 0
                    while (true) {
                        if (newFSTNodeAddress.get(hashSlot) == 0L) {
                            newFSTNodeAddress.set(hashSlot, address)
                            newCopiedNodeAddress.set(hashSlot, copiedNodeAddress.get(idx))
                            break
                        }

                        // quadratic probe
                        hashSlot = (hashSlot + (++c)) and newMask
                    }
                }
            }

            mask = newMask
            fstNodeAddress = newFSTNodeAddress
            copiedNodeAddress = newCopiedNodeAddress
        }

        // hash code for a frozen node.  this must precisely match the hash computation of an unfrozen
        // node!
        @Throws(IOException::class)
        fun hash(nodeAddress: Long, hashSlot: Long): Long {
            val `in`: BytesReader = getBytesReader(nodeAddress, hashSlot)

            val PRIME = 31

            var h: Long = 0
            fstCompiler.fst.readFirstRealTargetArc(nodeAddress, scratchArc, `in`)
            while (true) {
                h = PRIME * h + scratchArc.label()
                h = PRIME * h + (scratchArc.target() xor (scratchArc.target() shr 32)).toInt()
                h = PRIME * h + scratchArc.output().hashCode()
                h = PRIME * h + scratchArc.nextFinalOutput().hashCode()
                if (scratchArc.isFinal) {
                    h += 17
                }
                if (scratchArc.isLast) {
                    break
                }
                fstCompiler.fst.readNextRealArc(scratchArc, `in`)
            }

            return h
        }

        /**
         * Compares an unfrozen node (UnCompiledNode) with a frozen node at byte location address
         * (long), returning the node length if the two nodes are equals, or -1 otherwise
         *
         *
         * The node length will be used to promote the node from the fallback table to the primary
         * table
         */
        @Throws(IOException::class)
        fun nodesEqual(node: UnCompiledNode<T>, address: Long, hashSlot: Long): Int {
            val `in`: BytesReader = getBytesReader(address, hashSlot)
            fstCompiler.fst.readFirstRealTargetArc(address, scratchArc, `in`)

            // fail fast for a node with fixed length arcs
            if (scratchArc.bytesPerArc() != 0) {
                require(node.numArcs > 0)
                // the frozen node uses fixed-with arc encoding (same number of bytes per arc), but may be
                // sparse or dense
                when (scratchArc.nodeFlags()) {
                    FST.ARCS_FOR_BINARY_SEARCH ->             // sparse
                        if (node.numArcs != scratchArc.numArcs()) {
                            return -1
                        }

                    FST.ARCS_FOR_DIRECT_ADDRESSING ->             // dense -- compare both the number of labels allocated in the array (some of which may
                        // not actually be arcs), and the number of arcs
                        if ((node.arcs[node.numArcs - 1].label - node.arcs[0].label + 1) != scratchArc.numArcs()
                            || node.numArcs != Arc.BitTable.countBits(scratchArc, `in`)
                        ) {
                            return -1
                        }

                    FST.ARCS_FOR_CONTINUOUS -> if ((node.arcs[node.numArcs - 1].label - node.arcs[0].label + 1)
                        != scratchArc.numArcs()
                    ) {
                        return -1
                    }

                    else -> throw AssertionError("unhandled scratchArc.nodeFlag() " + scratchArc.nodeFlags())
                }
            }

            // compare arc by arc to see if there is a difference
            for (arcUpto in 0..<node.numArcs) {
                val arc: FSTCompiler.Arc<T> = node.arcs[arcUpto]
                if (arc.label != scratchArc.label() || arc.output!! != scratchArc.output() || (arc.target as CompiledNode).node != scratchArc.target() || arc.nextFinalOutput!! != scratchArc.nextFinalOutput() || arc.isFinal != scratchArc.isFinal
                ) {
                    return -1
                }

                if (scratchArc.isLast) {
                    return if (arcUpto == node.numArcs - 1) {
                        // position is 1 index past the starting address, as we are reading in backward
                        Math.toIntExact(address - `in`.position)
                    } else {
                        -1
                    }
                }

                fstCompiler.fst.readNextRealArc(scratchArc, `in`)
            }

            // unfrozen node has fewer arcs than frozen node
            return -1
        }

        private fun getBytesReader(nodeAddress: Long, hashSlot: Long): BytesReader {
            // make sure the nodeAddress and hashSlot is consistent
            require(fstNodeAddress.get(hashSlot) == nodeAddress)
            val localAddress: Long = copiedNodeAddress.get(hashSlot)
            bytesReader.setPosDelta(nodeAddress - localAddress)
            return bytesReader
        }

        // 256K blocks, but note that the final block is sized only as needed so it won't use the full
        // block size when just a few elements were written to it
        private val BLOCK_SIZE_BYTES = 1 shl 18
    }
}
