package org.gnit.lucenekmp.codecs.lucene90.blocktree

import org.gnit.lucenekmp.codecs.BlockTermState
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.TermsEnum.SeekStatus
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.fst.FST
import kotlinx.io.IOException
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.System
import kotlin.experimental.and

class SegmentTermsEnumFrame(private val ste: SegmentTermsEnum, // Our index in stack[]:
                                     val ord: Int
) {

    var hasTerms: Boolean = false
    var hasTermsOrig: Boolean = false
    var isFloor: Boolean = false

    var arc: FST.Arc<BytesRef>? = null

    // static boolean DEBUG = BlockTreeTermsWriter.DEBUG;
    // File pointer where this block was loaded from
    var fp: Long = 0
    var fpOrig: Long = 0
    var fpEnd: Long = 0
    var totalSuffixBytes: Long = 0 // for stats

    var suffixBytes: ByteArray = ByteArray(128)
    val suffixesReader: ByteArrayDataInput = ByteArrayDataInput()

    var suffixLengthBytes: ByteArray
    val suffixLengthsReader: ByteArrayDataInput

    var statBytes: ByteArray = ByteArray(64)
    var statsSingletonRunLength: Int = 0
    val statsReader: ByteArrayDataInput = ByteArrayDataInput()

    var rewindPos: Int = 0
    val floorDataReader: ByteArrayDataInput = ByteArrayDataInput()

    // Length of prefix shared by all terms in this block
    var prefixLength: Int = 0

    // Number of entries (term or sub-block) in this block
    var entCount: Int = 0

    // Which term we will next read, or -1 if the block
    // isn't loaded yet
    var nextEnt: Int = 0

    // True if this block is either not a floor block,
    // or, it's the last sub-block of a floor block
    var isLastInFloor: Boolean = false

    // True if all entries are terms
    var isLeafBlock: Boolean = false

    // True if all entries have the same length.
    var allEqual: Boolean = false

    var lastSubFP: Long = 0

    var nextFloorLabel: Int = 0
    var numFollowFloorBlocks: Int = 0

    // Next term to decode metaData; we decode metaData
    // lazily so that scanning to find the matching term is
    // fast and only if you find a match and app wants the
    // stats or docs/positions enums, will we decode the
    // metaData
    var metaDataUpto: Int = 0

    val state: BlockTermState = ste.fr.parent.postingsReader.newTermState()

    // metadata buffer
    var bytes: ByteArray = ByteArray(32)
    val bytesReader: ByteArrayDataInput = ByteArrayDataInput()

    fun setFloorData(outputAccumulator: SegmentTermsEnum.OutputAccumulator) {
        outputAccumulator.setFloorData(floorDataReader)
        rewindPos = floorDataReader.getPosition()
        numFollowFloorBlocks = floorDataReader.readVInt()
        nextFloorLabel = (floorDataReader.readByte() and 0xff.toByte()).toInt()
        // if (DEBUG) {
        // System.out.println("    setFloorData fpOrig=" + fpOrig + " bytes=" + new
        // BytesRef(source.bytes, source.offset + in.getPosition(), numBytes) + " numFollowFloorBlocks="
        // + numFollowFloorBlocks + " nextFloorLabel=" + toHex(nextFloorLabel));
        // }
    }

    fun getTermBlockOrd(): Int = if (isLeafBlock) nextEnt else state.termBlockOrd

    @Throws(IOException::class)
    fun loadNextFloorBlock() {
        // if (DEBUG) {
        // System.out.println("    loadNextFloorBlock fp=" + fp + " fpEnd=" + fpEnd);
        // }
        require(arc == null || isFloor) { "arc=$arc isFloor=$isFloor" }
        fp = fpEnd
        nextEnt = -1
        loadBlock()
    }

    @Throws(IOException::class)
    fun prefetchBlock() {
        if (nextEnt != -1) {
            // Already loaded
            return
        }

        // Clone the IndexInput lazily, so that consumers
        // that just pull a TermsEnum to
        // seekExact(TermState) don't pay this cost:
        ste.initIndexInput()

        // TODO: Could we know the number of bytes to prefetch
        ste.`in`!!.prefetch(fp, 1)
    }

    /* Does initial decode of next block of terms; this
  doesn't actually decode the docFreq, totalTermFreq,
  postings details (frq/prx offset, etc.) metadata;
  it just loads them as byte[] blobs which are then
  decoded on-demand if the metadata is ever requested
  for any term in this block.  This enables terms-only
  intensive consumes (eg certain MTQs, respelling) to
  not pay the price of decoding metadata they won't
  use. */
    @Throws(IOException::class)
    fun loadBlock() {
        // Clone the IndexInput lazily, so that consumers
        // that just pull a TermsEnum to
        // seekExact(TermState) don't pay this cost:

        ste.initIndexInput()

        if (nextEnt != -1) {
            // Already loaded
            return
        }

        // System.out.println("blc=" + blockLoadCount);
        ste.`in`!!.seek(fp)
        val code: Int = ste.`in`!!.readVInt()
        entCount = code ushr 1
        require(entCount > 0)
        isLastInFloor = (code and 1) != 0

        require(
            arc == null || (isLastInFloor || isFloor)
        ) { "fp=$fp arc=$arc isFloor=$isFloor isLastInFloor=$isLastInFloor" }

        // TODO: if suffixes were stored in random-access
        // array structure, then we could do binary search
        // instead of linear scan to find target term; eg
        // we could have simple array of offsets
        val startSuffixFP: Long = ste.`in`!!.getFilePointer()
        // term suffixes:
        val codeL: Long = ste.`in`!!.readVLong()
        isLeafBlock = (codeL and 0x04L) != 0L
        val numSuffixBytes = (codeL ushr 3).toInt()
        if (suffixBytes.size < numSuffixBytes) {
            suffixBytes = ByteArray(ArrayUtil.oversize(numSuffixBytes, 1))
        }
        try {
            compressionAlg = CompressionAlgorithm.byCode(codeL.toInt() and 0x03)
        } catch (e: IllegalArgumentException) {
            throw CorruptIndexException(e.message!!, ste.`in`!!, e)
        }
        compressionAlg.read(ste.`in`!!, suffixBytes, numSuffixBytes)
        suffixesReader.reset(suffixBytes, 0, numSuffixBytes)

        var numSuffixLengthBytes: Int = ste.`in`!!.readVInt()
        allEqual = (numSuffixLengthBytes and 0x01) != 0
        numSuffixLengthBytes = numSuffixLengthBytes ushr 1
        if (suffixLengthBytes.size < numSuffixLengthBytes) {
            suffixLengthBytes = ByteArray(ArrayUtil.oversize(numSuffixLengthBytes, 1))
        }
        if (allEqual) {
            Arrays.fill(suffixLengthBytes, 0, numSuffixLengthBytes, ste.`in`!!.readByte())
        } else {
            ste.`in`!!.readBytes(suffixLengthBytes, 0, numSuffixLengthBytes)
        }
        suffixLengthsReader.reset(suffixLengthBytes, 0, numSuffixLengthBytes)
        totalSuffixBytes = ste.`in`!!.getFilePointer() - startSuffixFP

        /*if (DEBUG) {
    if (arc == null) {
    System.out.println("    loadBlock (next) fp=" + fp + " entCount=" + entCount + " prefixLen=" + prefix + " isLastInFloor=" + isLastInFloor + " leaf=" + isLeafBlock);
    } else {
    System.out.println("    loadBlock (seek) fp=" + fp + " entCount=" + entCount + " prefixLen=" + prefix + " hasTerms=" + hasTerms + " isFloor=" + isFloor + " isLastInFloor=" + isLastInFloor + " leaf=" + isLeafBlock);
    }
    }*/

        // stats
        var numBytes: Int = ste.`in`!!.readVInt()
        if (statBytes.size < numBytes) {
            statBytes = ByteArray(ArrayUtil.oversize(numBytes, 1))
        }
        ste.`in`!!.readBytes(statBytes, 0, numBytes)
        statsReader.reset(statBytes, 0, numBytes)
        statsSingletonRunLength = 0
        metaDataUpto = 0

        state.termBlockOrd = 0
        nextEnt = 0
        lastSubFP = -1

        // TODO: we could skip this if !hasTerms; but
        // that's rare so won't help much
        // metadata
        numBytes = ste.`in`!!.readVInt()
        if (bytes.size < numBytes) {
            bytes = ByteArray(ArrayUtil.oversize(numBytes, 1))
        }
        ste.`in`!!.readBytes(bytes, 0, numBytes)
        bytesReader.reset(bytes, 0, numBytes)

        // Sub-blocks of a single floor block are always
        // written one after another -- tail recurse:
        fpEnd = ste.`in`!!.getFilePointer()
        // if (DEBUG) {
        //   System.out.println("      fpEnd=" + fpEnd);
        // }
    }

    fun rewind() {
        // Force reload:

        fp = fpOrig
        nextEnt = -1
        hasTerms = hasTermsOrig
        if (isFloor) {
            floorDataReader.setPosition(rewindPos)
            numFollowFloorBlocks = floorDataReader.readVInt()
            require(numFollowFloorBlocks > 0)
            nextFloorLabel = (floorDataReader.readByte() and 0xff.toByte()).toInt()
        }

        /*
    //System.out.println("rewind");
    // Keeps the block loaded, but rewinds its state:
    if (nextEnt > 0 || fp != fpOrig) {
    if (DEBUG) {
    System.out.println("      rewind frame ord=" + ord + " fpOrig=" + fpOrig + " fp=" + fp + " hasTerms=" + hasTerms + " isFloor=" + isFloor + " nextEnt=" + nextEnt + " prefixLen=" + prefix);
    }
    if (fp != fpOrig) {
    fp = fpOrig;
    nextEnt = -1;
    } else {
    nextEnt = 0;
    }
    hasTerms = hasTermsOrig;
    if (isFloor) {
    floorDataReader.rewind();
    numFollowFloorBlocks = floorDataReader.readVInt();
    nextFloorLabel = floorDataReader.readByte() & 0xff;
    }
    assert suffixBytes != null;
    suffixesReader.rewind();
    assert statBytes != null;
    statsReader.rewind();
    metaDataUpto = 0;
    state.termBlockOrd = 0;
    // TODO: skip this if !hasTerms  Then postings
    // impl wouldn't have to write useless 0 byte
    postingsReader.resetTermsBlock(fieldInfo, state);
    lastSubFP = -1;
    } else if (DEBUG) {
    System.out.println("      skip rewind fp=" + fp + " fpOrig=" + fpOrig + " nextEnt=" + nextEnt + " ord=" + ord);
    }
    */
    }

    // Decodes next entry; returns true if it's a sub-block
    @Throws(IOException::class)
    fun next(): Boolean {
        if (isLeafBlock) {
            nextLeaf()
            return false
        } else {
            return nextNonLeaf()
        }
    }

    fun nextLeaf() {
        // if (DEBUG) System.out.println("  frame.next ord=" + ord + " nextEnt=" + nextEnt +
        // " entCount=" + entCount);
        require(
            nextEnt != -1 && nextEnt < entCount
        ) { "nextEnt=$nextEnt entCount=$entCount fp=$fp" }
        nextEnt++
        suffixLength = suffixLengthsReader.readVInt()
        startBytePos = suffixesReader.getPosition()
        ste.term.setLength(prefixLength + suffixLength)
        ste.term.grow(ste.term.length())
        suffixesReader.readBytes(ste.term.bytes(), prefixLength, suffixLength)
        ste.termExists = true
    }

    @Throws(IOException::class)
    fun nextNonLeaf(): Boolean {
        // if (DEBUG) System.out.println("  stef.next ord=" + ord + " nextEnt=" + nextEnt + " entCount="
        // + entCount + " fp=" + suffixesReader.getPosition());
        while (true) {
            if (nextEnt == entCount) {
                require(
                    arc == null || (isFloor && !isLastInFloor)
                ) { "isFloor=$isFloor isLastInFloor=$isLastInFloor" }
                loadNextFloorBlock()
                if (isLeafBlock) {
                    nextLeaf()
                    return false
                } else {
                    continue
                }
            }

            require(
                nextEnt != -1 && nextEnt < entCount
            ) { "nextEnt=$nextEnt entCount=$entCount fp=$fp" }
            nextEnt++
            val code: Int = suffixLengthsReader.readVInt()
            suffixLength = code ushr 1
            startBytePos = suffixesReader.getPosition()
            ste.term.setLength(prefixLength + suffixLength)
            ste.term.grow(ste.term.length())
            suffixesReader.readBytes(ste.term.bytes(), prefixLength, suffixLength)
            if ((code and 1) == 0) {
                // A normal term
                ste.termExists = true
                subCode = 0
                state.termBlockOrd++
                return false
            } else {
                // A sub-block; make sub-FP absolute:
                ste.termExists = false
                subCode = suffixLengthsReader.readVLong()
                lastSubFP = fp - subCode
                // if (DEBUG) {
                // System.out.println("    lastSubFP=" + lastSubFP);
                // }
                return true
            }
        }
    }

    // TODO: make this array'd so we can do bin search
    // likely not worth it  need to measure how many
    // floor blocks we "typically" get
    fun scanToFloorFrame(target: BytesRef) {
        if (!isFloor || target.length <= prefixLength) {
            // if (DEBUG) {
            //   System.out.println("    scanToFloorFrame skip: isFloor=" + isFloor + " target.length=" +
            // target.length + " vs prefix=" + prefix);
            // }
            return
        }

        val targetLabel: Int = (target.bytes[target.offset + prefixLength] and 0xFF.toByte()).toInt()

        // if (DEBUG) {
        //   System.out.println("    scanToFloorFrame fpOrig=" + fpOrig + " targetLabel=" +
        // toHex(targetLabel) + " vs nextFloorLabel=" + toHex(nextFloorLabel) + " numFollowFloorBlocks="
        // + numFollowFloorBlocks);
        // }
        if (targetLabel < nextFloorLabel) {
            // if (DEBUG) {
            //   System.out.println("      already on correct block");
            // }
            return
        }

        require(numFollowFloorBlocks != 0)

        var newFP = fpOrig
        while (true) {
            val code: Long = floorDataReader.readVLong()
            newFP = fpOrig + (code ushr 1)
            hasTerms = (code and 1L) != 0L

            // if (DEBUG) {
            //   System.out.println("      label=" + toHex(nextFloorLabel) + " fp=" + newFP +
            // " hasTerms=" + hasTerms + " numFollowFloor=" + numFollowFloorBlocks);
            // }
            isLastInFloor = numFollowFloorBlocks == 1
            numFollowFloorBlocks--

            if (isLastInFloor) {
                nextFloorLabel = 256
                // if (DEBUG) {
                //   System.out.println("        stop!  last block nextFloorLabel=" +
                // toHex(nextFloorLabel));
                // }
                break
            } else {
                nextFloorLabel = (floorDataReader.readByte() and 0xff.toByte()).toInt()
                if (targetLabel < nextFloorLabel) {
                    // if (DEBUG) {
                    //   System.out.println("        stop!  nextFloorLabel=" + toHex(nextFloorLabel));
                    // }
                    break
                }
            }
        }

        if (newFP != fp) {
            // Force re-load of the block:
            // if (DEBUG) {
            //   System.out.println("      force switch to fp=" + newFP + " oldFP=" + fp);
            // }
            nextEnt = -1
            fp = newFP
        } else {
            // if (DEBUG) {
            //   System.out.println("      stay on same fp=" + newFP);
            // }
        }
    }

    @Throws(IOException::class)
    fun decodeMetaData() {
        // if (DEBUG) System.out.println("\nBTTR.decodeMetadata seg=" + segment + " mdUpto=" +
        // metaDataUpto + " vs termBlockOrd=" + state.termBlockOrd);

        // lazily catch up on metadata decode:

        val limit = this.getTermBlockOrd()
        var absolute = metaDataUpto == 0
        require(limit > 0)

        // TODO: better API would be "jump straight to term=N"
        while (metaDataUpto < limit) {
            // TODO: we could make "tiers" of metadata, ie,
            // decode docFreq/totalTF but don't decode postings
            // metadata; this way caller could get
            // docFreq/totalTF w/o paying decode cost for
            // postings

            // TODO: if docFreq were bulk decoded we could
            // just skipN here:

            if (statsSingletonRunLength > 0) {
                state.docFreq = 1
                state.totalTermFreq = 1
                statsSingletonRunLength--
            } else {
                val token: Int = statsReader.readVInt()
                if ((token and 1) == 1) {
                    state.docFreq = 1
                    state.totalTermFreq = 1
                    statsSingletonRunLength = token ushr 1
                } else {
                    state.docFreq = token ushr 1
                    if (ste.fr.fieldInfo.getIndexOptions() === IndexOptions.DOCS) {
                        state.totalTermFreq = state.docFreq.toLong()
                    } else {
                        state.totalTermFreq = state.docFreq + statsReader.readVLong()
                    }
                }
            }

            // metadata
            ste.fr.parent.postingsReader.decodeTerm(bytesReader, ste.fr.fieldInfo, state, absolute)

            metaDataUpto++
            absolute = false
        }
        state.termBlockOrd = metaDataUpto
    }

    // Used only by assert
    private fun prefixMatches(target: BytesRef): Boolean {
        for (bytePos in 0..<prefixLength) {
            if (target.bytes[target.offset + bytePos] != ste.term.byteAt(bytePos)) {
                return false
            }
        }

        return true
    }

    // Scans to sub-block that has this target fp; only
    // called by next(); NOTE: does not set
    // startBytePos/suffix as a side effect
    fun scanToSubBlock(subFP: Long) {
        require(!isLeafBlock)
        // if (DEBUG) System.out.println("  scanToSubBlock fp=" + fp + " subFP=" + subFP + " entCount="
        // + entCount + " lastSubFP=" + lastSubFP);
        // assert nextEnt == 0;
        if (lastSubFP == subFP) {
            // if (DEBUG) System.out.println("    already positioned");
            return
        }
        require(subFP < fp) { "fp=$fp subFP=$subFP" }
        val targetSubCode = fp - subFP
        // if (DEBUG) System.out.println("    targetSubCode=" + targetSubCode);
        while (true) {
            require(nextEnt < entCount)
            nextEnt++
            val code: Int = suffixLengthsReader.readVInt()
            suffixesReader.skipBytes(code.toLong() ushr 1)
            if ((code and 1) != 0) {
                val subCode: Long = suffixLengthsReader.readVLong()
                if (targetSubCode == subCode) {
                    // if (DEBUG) System.out.println("        match!");
                    lastSubFP = subFP
                    return
                }
            } else {
                state.termBlockOrd++
            }
        }
    }

    // NOTE: sets startBytePos/suffix as a side effect
    @Throws(IOException::class)
    fun scanToTerm(target: BytesRef, exactOnly: Boolean): SeekStatus {
        return if (isLeafBlock) {
            if (allEqual) {
                binarySearchTermLeaf(target, exactOnly)
            } else {
                scanToTermLeaf(target, exactOnly)
            }
        } else {
            scanToTermNonLeaf(target, exactOnly)
        }
    }

    private var startBytePos = 0
    private var suffixLength = 0
    private var subCode: Long = 0
    var compressionAlg: CompressionAlgorithm = CompressionAlgorithm.NO_COMPRESSION

    init {
        this.state.totalTermFreq = -1
        suffixLengthBytes = ByteArray(32)
        suffixLengthsReader = ByteArrayDataInput()
    }

    // Target's prefix matches this block's prefix; we
    // scan the entries to check if the suffix matches.
    @Throws(IOException::class)
    fun scanToTermLeaf(target: BytesRef, exactOnly: Boolean): SeekStatus {
        // if (DEBUG) System.out.println("    scanToTermLeaf: block fp=" + fp + " prefix=" + prefix +
        // " nextEnt=" + nextEnt + " (of " + entCount + ") target=" +
        // ToStringUtils.bytesRefToString(target) +
        // " term=" + ToStringUtils.bytesRefToString(term));

        require(nextEnt != -1)

        ste.termExists = true
        subCode = 0

        if (nextEnt == entCount) {
            if (exactOnly) {
                fillTerm()
            }
            return SeekStatus.END
        }

        require(prefixMatches(target))

        // Loop over each entry (term or sub-block) in this block:
        do {
            nextEnt++

            suffixLength = suffixLengthsReader.readVInt()

            // if (DEBUG) {
            //   BytesRef suffixBytesRef = new BytesRef();
            //   suffixBytesRef.bytes = suffixBytes;
            //   suffixBytesRef.offset = suffixesReader.getPosition();
            //   suffixBytesRef.length = suffix;
            //   System.out.println("      cycle: term " + (nextEnt-1) + " (of " + entCount + ") suffix="
            // + ToStringUtils.bytesRefToString(suffixBytesRef));
            // }
            startBytePos = suffixesReader.getPosition()
            suffixesReader.skipBytes(suffixLength.toLong())

            // Compare suffix and target.
            val cmp: Int =
                Arrays.compareUnsigned(
                    suffixBytes,
                    startBytePos,
                    startBytePos + suffixLength,
                    target.bytes,
                    target.offset + prefixLength,
                    target.offset + target.length
                )

            if (cmp < 0) {
                // Current entry is still before the target;
                // keep scanning
            } else if (cmp > 0) {
                // Done!  Current entry is after target --
                // return NOT_FOUND:
                fillTerm()

                // if (DEBUG) System.out.println("        not found");
                return SeekStatus.NOT_FOUND
            } else {
                // Exact match!

                // This cannot be a sub-block because we
                // would have followed the index to this
                // sub-block from the start:

                fillTerm()
                // if (DEBUG) System.out.println("        found!");
                return SeekStatus.FOUND
            }
        } while (nextEnt < entCount)

        // It is possible (and OK) that terms index pointed us
        // at this block, but, we scanned the entire block and
        // did not find the term to position to.  This happens
        // when the target is after the last term in the block
        // (but, before the next term in the index).  EG
        // target could be foozzz, and terms index pointed us
        // to the foo* block, but the last term in this block
        // was fooz (and, eg, first term in the next block will
        // bee fop).
        // if (DEBUG) System.out.println("      block end");
        if (exactOnly) {
            fillTerm()
        }

        // TODO: not consistent that in the
        // not-exact case we don't next() into the next
        // frame here
        return SeekStatus.END
    }

    // Target's prefix matches this block's prefix;
    // And all suffixes have the same length in this block,
    // we binary search the entries to check if the suffix matches.
    @Throws(IOException::class)
    fun binarySearchTermLeaf(target: BytesRef, exactOnly: Boolean): SeekStatus {
        // if (DEBUG) System.out.println("    binarySearchTermLeaf: block fp=" + fp + " prefix=" +
        // prefix + "
        // nextEnt=" + nextEnt + " (of " + entCount + ") target=" + brToString(target) + " term=" +
        // brToString(term));

        require(nextEnt != -1)

        ste.termExists = true
        subCode = 0

        if (nextEnt == entCount) {
            if (exactOnly) {
                fillTerm()
            }
            return SeekStatus.END
        }

        require(prefixMatches(target))

        suffixLength = suffixLengthsReader.readVInt()
        // TODO early terminate when target length unequals suffix + prefix.
        // But we need to keep the same status with scanToTermLeaf.
        var start = nextEnt
        var end = entCount - 1
        // Binary search the entries (terms) in this leaf block:
        var cmp = 0
        while (start <= end) {
            val mid = (start + end) ushr 1
            nextEnt = mid + 1
            startBytePos = mid * suffixLength

            // Compare suffix and target.
            cmp =
                Arrays.compareUnsigned(
                    suffixBytes,
                    startBytePos,
                    startBytePos + suffixLength,
                    target.bytes,
                    target.offset + prefixLength,
                    target.offset + target.length
                )
            if (cmp < 0) {
                start = mid + 1
            } else if (cmp > 0) {
                end = mid - 1
            } else {
                // Exact match!
                suffixesReader.setPosition(startBytePos + suffixLength)
                fillTerm()
                // if (DEBUG) System.out.println("        found!");
                return SeekStatus.FOUND
            }
        }

        // It is possible (and OK) that terms index pointed us
        // at this block, but, we searched the entire block and
        // did not find the term to position to.  This happens
        // when the target is after the last term in the block
        // (but, before the next term in the index).  EG
        // target could be foozzz, and terms index pointed us
        // to the foo* block, but the last term in this block
        // was fooz (and, eg, first term in the next block will
        // bee fop).
        // if (DEBUG) System.out.println("      block end");
        val seekStatus: SeekStatus
        if (end < entCount - 1) {
            seekStatus = SeekStatus.NOT_FOUND
            // If binary search ended at the less term, and greater term exists.
            // We need to advance to the greater term.
            if (cmp < 0) {
                startBytePos += suffixLength
                nextEnt++
            }
            suffixesReader.setPosition(startBytePos + suffixLength)
            fillTerm()
        } else {
            seekStatus = SeekStatus.END
            suffixesReader.setPosition(startBytePos + suffixLength)
            if (exactOnly) {
                fillTerm()
            }
        }
        // TODO: not consistent that in the
        // not-exact case we don't next() into the next
        // frame here
        return seekStatus
    }

    // Target's prefix matches this block's prefix; we
    // scan the entries to check if the suffix matches.
    @Throws(IOException::class)
    fun scanToTermNonLeaf(target: BytesRef, exactOnly: Boolean): SeekStatus {
        // if (DEBUG) System.out.println("    scanToTermNonLeaf: block fp=" + fp + " prefix=" + prefix +
        // " nextEnt=" + nextEnt + " (of " + entCount + ") target=" +
        // ToStringUtils.bytesRefToString(target) +
        // " term=" + ToStringUtils.bytesRefToString(term));

        require(nextEnt != -1)

        if (nextEnt == entCount) {
            if (exactOnly) {
                fillTerm()
                ste.termExists = subCode == 0L
            }
            return SeekStatus.END
        }

        require(prefixMatches(target))

        // Loop over each entry (term or sub-block) in this block:
        while (nextEnt < entCount) {
            nextEnt++

            val code: Int = suffixLengthsReader.readVInt()
            suffixLength = code ushr 1

            // if (DEBUG) {
            //  BytesRef suffixBytesRef = new BytesRef();
            //  suffixBytesRef.bytes = suffixBytes;
            //  suffixBytesRef.offset = suffixesReader.getPosition();
            //  suffixBytesRef.length = suffix;
            //  System.out.println("      cycle: " + ((code&1)==1  "sub-block" : "term") + " " +
            // (nextEnt-1) + " (of " + entCount + ") suffix=" +
            // ToStringUtils.bytesRefToString(suffixBytesRef));
            // }
            startBytePos = suffixesReader.getPosition()
            suffixesReader.skipBytes(suffixLength.toLong())
            ste.termExists = (code and 1) == 0
            if (ste.termExists) {
                state.termBlockOrd++
                subCode = 0
            } else {
                subCode = suffixLengthsReader.readVLong()
                lastSubFP = fp - subCode
            }

            // Compare suffix and target.
            val cmp: Int =
                Arrays.compareUnsigned(
                    suffixBytes,
                    startBytePos,
                    startBytePos + suffixLength,
                    target.bytes,
                    target.offset + prefixLength,
                    target.offset + target.length
                )

            if (cmp < 0) {
                // Current entry is still before the target;
                // keep scanning
            } else if (cmp > 0) {
                // Done!  Current entry is after target --
                // return NOT_FOUND:
                fillTerm()

                // if (DEBUG) System.out.println("        maybe done exactOnly=" + exactOnly +
                // " ste.termExists=" + ste.termExists);
                if (!exactOnly && !ste.termExists) {
                    // System.out.println("  now pushFrame");
                    // TODO this
                    // We are on a sub-block, and caller wants
                    // us to position to the next term after
                    // the target, so we must recurse into the
                    // sub-frame(s):
                    ste.currentFrame =
                        ste.pushFrame(null, ste.currentFrame.lastSubFP, prefixLength + suffixLength)
                    ste.currentFrame.loadBlock()
                    while (ste.currentFrame.next()) {
                        ste.currentFrame = ste.pushFrame(null, ste.currentFrame.lastSubFP, ste.term.length())
                        ste.currentFrame.loadBlock()
                    }
                }

                // if (DEBUG) System.out.println("        not found");
                return SeekStatus.NOT_FOUND
            } else {
                // Exact match!

                // This cannot be a sub-block because we
                // would have followed the index to this
                // sub-block from the start:

                require(ste.termExists)
                fillTerm()
                // if (DEBUG) System.out.println("        found!");
                return SeekStatus.FOUND
            }
        }

        // It is possible (and OK) that terms index pointed us
        // at this block, but, we scanned the entire block and
        // did not find the term to position to.  This happens
        // when the target is after the last term in the block
        // (but, before the next term in the index).  EG
        // target could be foozzz, and terms index pointed us
        // to the foo* block, but the last term in this block
        // was fooz (and, eg, first term in the next block will
        // bee fop).
        // if (DEBUG) System.out.println("      block end");
        if (exactOnly) {
            fillTerm()
        }

        // TODO: not consistent that in the
        // not-exact case we don't next() into the next
        // frame here
        return SeekStatus.END
    }

    private fun fillTerm() {
        val termLength = prefixLength + suffixLength
        ste.term.setLength(termLength)
        ste.term.grow(termLength)
        System.arraycopy(suffixBytes, startBytePos, ste.term.bytes(), prefixLength, suffixLength)
    }
}
