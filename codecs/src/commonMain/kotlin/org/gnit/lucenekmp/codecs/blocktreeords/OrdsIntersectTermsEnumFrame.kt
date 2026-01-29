/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gnit.lucenekmp.codecs.blocktreeords

import org.gnit.lucenekmp.codecs.BlockTermState
import org.gnit.lucenekmp.codecs.blocktreeords.FSTOrdsOutputs.Output
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.store.ByteArrayDataInput
import org.gnit.lucenekmp.util.ArrayUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.automaton.Transition
import org.gnit.lucenekmp.util.automaton.TransitionAccessor
import org.gnit.lucenekmp.util.fst.FST


// TODO: can we share this with the frame in STE?
internal class OrdsIntersectTermsEnumFrame internal constructor(
  private val ite: OrdsIntersectTermsEnum,
  val ord: Int
) {
  var fp: Long = 0L
  var fpOrig: Long = 0L
  var fpEnd: Long = 0L
  var lastSubFP: Long = 0L

  // State in automaton
  var state: Int = 0
    set(state) {
        field = state
        transitionIndex = 0
        transitionCount = (ite.transitionAccessor as TransitionAccessor).getNumTransitions(state)
        if (transitionCount != 0) {
            (ite.transitionAccessor as TransitionAccessor).initTransition(state, transition)
            (ite.transitionAccessor as TransitionAccessor).getNextTransition(transition)
            curTransitionMax = transition.max
        } else {
            curTransitionMax = -1
        }
    }

  var metaDataUpto: Int = 0

  var suffixBytes: ByteArray = ByteArray(128)
  val suffixesReader: ByteArrayDataInput = ByteArrayDataInput()

  var statBytes: ByteArray = ByteArray(64)
  val statsReader: ByteArrayDataInput = ByteArrayDataInput()

  var floorData: ByteArray = ByteArray(32)
  val floorDataReader: ByteArrayDataInput = ByteArrayDataInput()

  // Length of prefix shared by all terms in this block
  var prefix: Int = 0

  // Number of entries (term or sub-block) in this block
  var entCount: Int = 0

  // Which term we will next read
  var nextEnt: Int = 0

  // Starting termOrd for this frame, used to reset termOrd in rewind()
  var termOrdOrig: Long = 0L

  // 1 + ordinal of the current term
  var termOrd: Long = 0L

  // True if this block is either not a floor block,
  // or, it's the last sub-block of a floor block
  var isLastInFloor: Boolean = false

  // True if all entries are terms
  var isLeafBlock: Boolean = false

  var numFollowFloorBlocks: Int = 0
  var nextFloorLabel: Int = 0

  var transition: Transition = Transition()
  var curTransitionMax: Int = 0
  var transitionIndex: Int = 0
  var transitionCount: Int = 0

  var arc: FST.Arc<Output>? = null

  val termState: BlockTermState

  // metadata
  var bytes: ByteArray? = null
  var bytesReader: ByteArrayDataInput? = null

  // Cumulative output so far
  var outputPrefix: Output? = null

  var startBytePos: Int = 0
  var suffix: Int = 0

  init {
    this.termState = (ite.fr.parent as OrdsBlockTreeTermsReader).postingsReader.newTermState()
    termState.totalTermFreq = -1
  }

  fun loadNextFloorBlock() {
    check(numFollowFloorBlocks > 0)

    do {
      fp = fpOrig + (floorDataReader.readVLong() ushr 1)
      numFollowFloorBlocks--
      if (numFollowFloorBlocks != 0) {
        nextFloorLabel = floorDataReader.readByte().toInt() and 0xff
        termOrd += floorDataReader.readVLong()
      } else {
        nextFloorLabel = 256
      }
    } while (numFollowFloorBlocks != 0 && nextFloorLabel <= transition.min)

    load(null)
  }

  fun load(output: Output?) {
    if (output != null && output.bytes() != null && transitionCount != 0) {
      val frameIndexData: BytesRef = output.bytes()!!

      // Floor frame
      if (floorData.size < frameIndexData.length) {
        floorData = ByteArray(ArrayUtil.oversize(frameIndexData.length, 1))
      }
      frameIndexData.bytes.copyInto(
        floorData,
        0,
        frameIndexData.offset,
        frameIndexData.offset + frameIndexData.length
      )
      floorDataReader.reset(floorData, 0, frameIndexData.length)
      val code = floorDataReader.readVLong()
      if ((code and OrdsBlockTreeTermsWriter.OUTPUT_FLAG_IS_FLOOR.toLong()) != 0L) {
        numFollowFloorBlocks = floorDataReader.readVInt()
        nextFloorLabel = floorDataReader.readByte().toInt() and 0xff

        termOrd = termOrdOrig + floorDataReader.readVLong()

        if (!ite.byteRunnable.isAccept(state)) {
          check(transitionIndex == 0) { "transitionIndex=$transitionIndex" }
          while (numFollowFloorBlocks != 0 && nextFloorLabel <= transition.min) {
            fp = fpOrig + (floorDataReader.readVLong() ushr 1)
            numFollowFloorBlocks--
            if (numFollowFloorBlocks != 0) {
              nextFloorLabel = floorDataReader.readByte().toInt() and 0xff
              termOrd += floorDataReader.readVLong()
            } else {
              nextFloorLabel = 256
            }
          }
        }
      }
    }

    ite.`in`.seek(fp)
    var code = ite.`in`.readVInt()
    entCount = code ushr 1
    check(entCount > 0)
    isLastInFloor = (code and 1) != 0

    // term suffixes:
    code = ite.`in`.readVInt()
    isLeafBlock = (code and 1) != 0
    var numBytes = code ushr 1
    if (suffixBytes.size < numBytes) {
      suffixBytes = ByteArray(ArrayUtil.oversize(numBytes, 1))
    }
    ite.`in`.readBytes(suffixBytes, 0, numBytes)
    suffixesReader.reset(suffixBytes, 0, numBytes)

    // stats
    numBytes = ite.`in`.readVInt()
    if (statBytes.size < numBytes) {
      statBytes = ByteArray(ArrayUtil.oversize(numBytes, 1))
    }
    ite.`in`.readBytes(statBytes, 0, numBytes)
    statsReader.reset(statBytes, 0, numBytes)
    metaDataUpto = 0

    termState.termBlockOrd = 0
    nextEnt = 0

    // metadata
    numBytes = ite.`in`.readVInt()
    if (bytes == null) {
      bytes = ByteArray(ArrayUtil.oversize(numBytes, 1))
      bytesReader = ByteArrayDataInput()
    } else if (bytes!!.size < numBytes) {
      bytes = ByteArray(ArrayUtil.oversize(numBytes, 1))
    }
    ite.`in`.readBytes(bytes!!, 0, numBytes)
    bytesReader!!.reset(bytes!!, 0, numBytes)

    if (!isLastInFloor) {
      fpEnd = ite.`in`.filePointer
    }
  }

  // TODO: maybe add scanToLabel; should give perf boost

  fun next(): Boolean {
    return if (isLeafBlock) nextLeaf() else nextNonLeaf()
  }

  // Decodes next entry; returns true if it's a sub-block
  fun nextLeaf(): Boolean {
    check(nextEnt != -1 && nextEnt < entCount) { "nextEnt=$nextEnt entCount=$entCount fp=$fp" }
    nextEnt++
    suffix = suffixesReader.readVInt()
    startBytePos = suffixesReader.position
    suffixesReader.skipBytes(suffix.toLong())
    return false
  }

  fun nextNonLeaf(): Boolean {
    check(nextEnt != -1 && nextEnt < entCount) { "nextEnt=$nextEnt entCount=$entCount fp=$fp" }
    nextEnt++
    val code = suffixesReader.readVInt()
    suffix = code ushr 1
    startBytePos = suffixesReader.position
    suffixesReader.skipBytes(suffix.toLong())
    return if ((code and 1) == 0) {
      termState.termBlockOrd++
      false
    } else {
      lastSubFP = fp - suffixesReader.readVLong()
      // Skip term ord
      suffixesReader.readVLong()
      true
    }
  }

  fun getTermBlockOrd(): Int = if (isLeafBlock) nextEnt else termState.termBlockOrd

  fun decodeMetaData() {
    val limit = getTermBlockOrd()
    var absolute = metaDataUpto == 0
    check(limit > 0)

    while (metaDataUpto < limit) {
      termState.docFreq = statsReader.readVInt()
      if (ite.fr.fieldInfo.indexOptions == IndexOptions.DOCS) {
        termState.totalTermFreq = termState.docFreq.toLong()
      } else {
        termState.totalTermFreq = termState.docFreq + statsReader.readVLong()
      }
      (ite.fr.parent as OrdsBlockTreeTermsReader).postingsReader.decodeTerm(bytesReader!!, ite.fr.fieldInfo, termState, absolute)

      metaDataUpto++
      absolute = false
    }
    termState.termBlockOrd = metaDataUpto
  }
}
