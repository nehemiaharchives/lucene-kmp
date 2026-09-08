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
package org.gnit.lucenekmp.facet

import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.search.*
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.CollectionUtil
import org.gnit.lucenekmp.util.FixedBitSet
import org.gnit.lucenekmp.util.PriorityQueue
import org.gnit.lucenekmp.jdkport.assert

internal class DrillSidewaysScorer : BulkScorer {

  companion object {
    private val APPROXIMATION_COMPARATOR = compareBy<DocsAndCost> { it.cost }
    private val TWO_PHASE_COMPARATOR = Comparator<DocsAndCost> { a, b -> a.matchCost.compareTo(b.matchCost) }
    private const val CHUNK = 2048
    private const val MASK = CHUNK - 1
  }

  // private static boolean DEBUG = false;

  private val dims: Array<DocsAndCost>

  // DrillDown DocsEnums:
  private val baseScorer: Scorer
  private val baseIterator: DocIdSetIterator
  private val baseApproximation: DocIdSetIterator
  private val baseTwoPhase: TwoPhaseIterator?

  private val context: LeafReaderContext

  val scoreSubDocsAtOnce: Boolean


  private var collectDocID = -1
  private var collectScore = 0f

  constructor(context: LeafReaderContext, baseScorer: Scorer, dims: Array<DocsAndCost>, scoreSubDocsAtOnce: Boolean) : super() {
    this.dims = dims
    this.context = context
    this.baseScorer = baseScorer
    this.baseIterator = baseScorer.iterator()
    this.baseTwoPhase = baseScorer.twoPhaseIterator()
    if (baseTwoPhase != null) {
      this.baseApproximation = baseTwoPhase.approximation()
    } else {
      this.baseApproximation = baseIterator
    }
    this.scoreSubDocsAtOnce = scoreSubDocsAtOnce
  }

  
  override fun cost(): Long {
    return baseIterator.cost()
  }

  
  override fun score(collector: LeafCollector, acceptDocs: Bits?, min: Int, maxDoc: Int): Int {
    if (min != 0) {
      throw IllegalArgumentException("min must be 0, got " + min)
    }
    if (maxDoc != Int.MAX_VALUE) {
      throw IllegalArgumentException("maxDoc must be Int.MAX_VALUE")
    }

    // some scorers, eg ReqExlScorer, can hit NPE if cost is called after nextDoc
    val baseQueryCost = baseIterator.cost()

    var numDims = dims.size

    var drillDownCost = 0L
    for (dim in 0 until numDims) {
      drillDownCost += dims[dim].approximation.cost()
    }

    var drillDownAdvancedCost = 0L
    if (numDims > 1) {
      drillDownAdvancedCost = dims[1].approximation.cost()
    }

    // Position dims scorers to their first matching doc:
    for (dim in dims) {
      dim.approximation.nextDoc()
    }

    /*
    System.out.println("\nbaseDocID=" + baseScorer.docID() + " est=" + estBaseHitCount);
    System.out.println("  maxDoc=" + context.reader().maxDoc());
    System.out.println("  maxCost=" + maxCost);
    System.out.println("  dims[0].freq=" + dims[0].freq);
    if (numDims > 1) {
      System.out.println("  dims[1].freq=" + dims[1].freq);
    }
    */

    try {
      if (scoreSubDocsAtOnce || baseQueryCost < drillDownCost / 10) {
        // System.out.println("queryFirst: baseScorer=" + baseScorer + " disis.length=" +
        // disis.length
        // + " bits.length=" + bits.length);
        // position base scorer to the first matching doc
        baseApproximation.nextDoc()
        doQueryFirstScoring(acceptDocs, collector, dims)
      } else if (numDims > 1 && drillDownAdvancedCost < baseQueryCost / 10) {
        // System.out.println("drillDownAdvance");
        // position base scorer to the first matching doc
        baseIterator.nextDoc()
        doDrillDownAdvanceScoring(acceptDocs, collector, dims)
      } else {
        // System.out.println("union");
        // position base scorer to the first matching doc
        baseIterator.nextDoc()
        doUnionScoring(acceptDocs, collector, dims)
      }
    } finally {
      // TODO: What's the right behavior when a collector throws CollectionTerminatedException?
      // Should we stop scoring immediately (what we're doing now), or should we keep scoring until
      // all collectors throw? Should users be able to specify somehow?
      finish(dims)
    }

    return Int.MAX_VALUE
  }

  /**
   * Query-first scoring specialization when there is only one drill-sideways dimension, which is
   * likely a common scenario.
   */
  private fun doQueryFirstScoringSingleDim(acceptDocs: Bits?, collector: LeafCollector, dim: DocsAndCost): Unit {
    var docID = baseApproximation.docID()
    while (docID != DocIdSetIterator.NO_MORE_DOCS) {
      assert(docID == baseApproximation.docID())

      if (acceptDocs != null && acceptDocs.get(docID) == false) {
        docID = baseApproximation.nextDoc()
        continue
      }

      if (baseTwoPhase != null && baseTwoPhase.matches() == false) {
        docID = baseApproximation.nextDoc()
        continue
      }

      // We have either a near-miss or full match. Check the sideways dim to see which it is:
      collectDocID = docID
      if (advanceIfBehind(docID, dim.approximation) != docID
          || (dim.twoPhase != null && dim.twoPhase!!.matches() == false)) {
        // The sideways dim missed, so we have a "near miss":
        collectNearMiss(dim.sidewaysLeafCollector)
      } else {
        // Hit passed all filters, so it's "real":
        collectHit(collector, dim)
      }

      docID = baseApproximation.nextDoc()
    }
  }

  /**
   * Used when base query is highly constraining vs the drilldowns, or when the docs must be scored
   * at once (i.e., like BooleanScorer2, not BooleanScorer).
   */
  private fun doQueryFirstScoring(acceptDocs: Bits?, collector: LeafCollector, dims: Array<DocsAndCost>): Unit {
    val collector = ScoreCachingWrappingScorer.wrap(collector)
    setScorer(collector, baseScorer)

    // Specialize the single-dim use-case as we have a more efficient implementation for that:
    if (dims.size == 1) {
      doQueryFirstScoringSingleDim(acceptDocs, collector, dims[0])
      return
    }

    // Sort our sideways dims by approximation cost so we can advance the lower cost ones first:
    val sidewaysDims = ArrayList<DocsAndCost>(dims.size)
    sidewaysDims.addAll(dims.asList())
    CollectionUtil.timSort(sidewaysDims, APPROXIMATION_COMPARATOR)

    // Maintain (optional) subset of sideways dims that support two-phase iteration, sorted by
    // matchCost:
    var sidewaysTwoPhaseDims: MutableList<DocsAndCost>? = null
    for (dim in dims) {
      if (dim.twoPhase != null) {
        if (sidewaysTwoPhaseDims == null) {
          sidewaysTwoPhaseDims = ArrayList()
        }
        sidewaysTwoPhaseDims.add(dim)
      }
    }
    if (sidewaysTwoPhaseDims != null) {
      CollectionUtil.timSort(sidewaysTwoPhaseDims, TWO_PHASE_COMPARATOR)
    }

    // We keep track of a "runaway" dimension, which is a previously "near missed" dimension that
    // has advanced beyond the docID the rest of the dimensions are positioned on. This functions
    // a bit like the "head" queue in WANDScorer's "min should match" implementation. We use a
    // single-valued PQ ordered by docID to easily determine the "closest" runaway dim we'll use
    // for advancing in the case that multiple dim approximations miss.
    val runawayDim = object : PriorityQueue<DocsAndCost>(1) {
      override fun lessThan(a: DocsAndCost, b: DocsAndCost): Boolean {
        return a.approximation.docID() < b.approximation.docID()
      }
    }

    var docID = baseApproximation.docID()

    nextDoc@ while (docID != DocIdSetIterator.NO_MORE_DOCS) {
      assert(docID == baseApproximation.docID())

      if (acceptDocs != null && acceptDocs.get(docID) == false) {
        docID = baseApproximation.nextDoc()
        continue
      }

      // If we carried a "runaway" over from the last iteration, see if we've "caught up" yet:
      var runaway: DocsAndCost? = runawayDim.topOrNull()
      if (runaway != null && runaway.approximation.docID() <= docID) {
        runawayDim.clear()
        runaway = null
      }

      // Check the sideways dim approximations. At most, one dim is allowed to miss for the doc
      // to be a near-miss or full match. If multiple sideways dims miss, we move on:
      for (dim in sidewaysDims) {
        var dimDocID = advanceIfBehind(docID, dim.approximation)
        if (dimDocID != docID && dim != runaway) {
          val evicted = runawayDim.insertWithOverflow(dim)
          if (evicted != null) {
            // More than one dim has advanced beyond docID, so we jump ahead to the "closer" of
            // the two:
            var next = evicted.approximation.docID()
            docID = baseApproximation.advance(next)
            continue@nextDoc
          }
        }
      }

      // At this point, we have an "approximate" near-miss or full match, but we still need
      // to confirm two-phase iterators. First, check the base two-phase (it's always required):
      if (baseTwoPhase != null && baseTwoPhase.matches() == false) {
        docID = baseApproximation.nextDoc()
        continue
      }

      // If we have two-phase iterators for our sideways dims, check them now. At most, one
      // sideways dim can miss for the doc to be a near-miss or full match. If more than one misses
      // we move on:
      var failedDim: DocsAndCost? = runawayDim.topOrNull()
      if (sidewaysTwoPhaseDims != null) {
        if (failedDim == null) {
          // If all sideways dims matched in their approximation phase, then we can allow one
          // second-phase check to fail:
          for (dim in sidewaysTwoPhaseDims) {
            assert(dim.approximation.docID() == docID)
            if (dim.twoPhase!!.matches() == false) {
              if (failedDim != null) {
                // Two second-phase checks have failed, so we move on:
                docID = baseApproximation.nextDoc()
                continue@nextDoc
              } else {
                failedDim = dim
              }
            }
          }
        } else {
          // If a sideways dim failed the approximate check, then no second-phase checks can fail:
          for (dim in sidewaysTwoPhaseDims) {
            if (failedDim == dim) {
              continue
            }
            assert(dim.approximation.docID() == docID)
            if (dim.twoPhase!!.matches() == false) {
              docID = baseApproximation.nextDoc()
              continue@nextDoc
            }
          }
        }
      }

      collectDocID = docID
      if (failedDim == null) {
        // Hit passed all filters, so it's "real":
        collectHit(collector, sidewaysDims)
      } else {
        // Hit missed exactly one dim:
        collectNearMiss(failedDim.sidewaysLeafCollector)
      }

      docID = baseApproximation.nextDoc()
    }
  }

  private fun advanceIfBehind(docID: Int, iterator: DocIdSetIterator): Int {
    if (iterator.docID() < docID) {
      return iterator.advance(docID)
    } else {
      return iterator.docID()
    }
  }

  /** Used when drill downs are highly constraining vs baseQuery. */
  private fun doDrillDownAdvanceScoring(acceptDocs: Bits?, collector: LeafCollector, dims: Array<DocsAndCost>): Unit {
    setScorer(collector, Score())

    var maxDoc = context.reader().maxDoc()
    var numDims = dims.size

    // if (DEBUG) {
    //  System.out.println("  doDrillDownAdvanceScoring");
    // }

    // TODO: maybe a class like BS, instead of parallel arrays
    val filledSlots = IntArray(CHUNK)
    val docIDs = IntArray(CHUNK)
    val scores = FloatArray(CHUNK)
    val missingDims = IntArray(CHUNK)
    val counts = IntArray(CHUNK)

    docIDs[0] = -1
    var nextChunkStart = CHUNK

    val seen = FixedBitSet(CHUNK)

    while (true) {
      // if (DEBUG) {
      //  System.out.println("\ncycle nextChunkStart=" + nextChunkStart + " docIds[0]=" +
      // docIDs[0]);
      // }

      // First dim:
      // if (DEBUG) {
      //  System.out.println("  dim0");
      // }
      var dc = dims[0]
      var docID = dc.approximation.docID()
      while (docID < nextChunkStart) {
        if (acceptDocs == null || acceptDocs.get(docID)) {
          var slot = docID and MASK

          if (docIDs[slot] != docID && (dc.twoPhase == null || dc.twoPhase!!.matches())) {
            seen.set(slot)
            // Mark slot as valid:
            // if (DEBUG) {
            //  System.out.println("    set docID=" + docID + " id=" +
            // context.reader().document(docID).get("id"));
            // }
            docIDs[slot] = docID
            missingDims[slot] = 1
            counts[slot] = 1
          }
        }

        docID = dc.approximation.nextDoc()
      }

      // Second dim:
      // if (DEBUG) {
      //  System.out.println("  dim1");
      // }
      dc = dims[1]
      docID = dc.approximation.docID()
      while (docID < nextChunkStart) {
        if (acceptDocs == null
            || acceptDocs.get(docID) && (dc.twoPhase == null || dc.twoPhase!!.matches())) {
          var slot = docID and MASK

          if (docIDs[slot] != docID) {
            // Mark slot as valid:
            seen.set(slot)
            // if (DEBUG) {
            //  System.out.println("    set docID=" + docID + " missingDim=0 id=" +
            // context.reader().document(docID).get("id"));
            // }
            docIDs[slot] = docID
            missingDims[slot] = 0
            counts[slot] = 1
          } else {
            // TODO: single-valued dims will always be true
            // below; we could somehow specialize
            if (missingDims[slot] >= 1) {
              missingDims[slot] = 2
              counts[slot] = 2
              // if (DEBUG) {
              //  System.out.println("    set docID=" + docID + " missingDim=2 id=" +
              // context.reader().document(docID).get("id"));
              // }
            } else {
              counts[slot] = 1
              // if (DEBUG) {
              //  System.out.println("    set docID=" + docID + " missingDim=" + missingDims[slot] +
              // " id=" + context.reader().document(docID).get("id"));
              // }
            }
          }
        }

        docID = dc.approximation.nextDoc()
      }

      // After this we can "upgrade" to conjunction, because
      // any doc not seen by either dim 0 or dim 1 cannot be
      // a hit or a near miss:

      // if (DEBUG) {
      //  System.out.println("  baseScorer");
      // }

      // Fold in baseScorer, using advance:
      var filledCount = 0
      var slot0 = 0
      while (slot0 < CHUNK) {
        slot0 = seen.nextSetBit(slot0)
        if (slot0 == DocIdSetIterator.NO_MORE_DOCS) break
        var ddDocID = docIDs[slot0]
        assert(ddDocID != -1)

        var baseDocID = baseIterator.docID()
        if (baseDocID < ddDocID) {
          baseDocID = baseIterator.advance(ddDocID)
        }
        if (baseDocID == ddDocID) {
          // if (DEBUG) {
          //  System.out.println("    keep docID=" + ddDocID + " id=" +
          // context.reader().document(ddDocID).get("id"));
          // }
          scores[slot0] = baseScorer.score()
          filledSlots[filledCount++] = slot0
          counts[slot0]++
        } else {
          // if (DEBUG) {
          //  System.out.println("    no docID=" + ddDocID + " id=" +
          // context.reader().document(ddDocID).get("id"));
          // }
          docIDs[slot0] = -1

          // TODO: we could jump slot0 forward to the
          // baseDocID ... but we'd need to set docIDs for
          // intervening slots to -1
        }
        slot0++
      }
      seen.clear(0, CHUNK)

      if (filledCount == 0) {
        if (nextChunkStart >= maxDoc) {
          break
        }
        nextChunkStart += CHUNK
        continue
      }

      // TODO: factor this out & share w/ union scorer,
      // except we start from dim=2 instead:
      for (dim in 2 until numDims) {
        // if (DEBUG) {
        //  System.out.println("  dim=" + dim + " [" + dims[dim].dim + "]");
        // }
        dc = dims[dim]
        docID = dc.approximation.docID()
        while (docID < nextChunkStart) {
          var slot = docID and MASK
          if (docIDs[slot] == docID
              && counts[slot] >= dim
              && (dc.twoPhase == null || dc.twoPhase!!.matches())) {
            // TODO: single-valued dims will always be true
            // below; we could somehow specialize
            if (missingDims[slot] >= dim) {
              // if (DEBUG) {
              //  System.out.println("      set docID=" + docID + " count=" + (dim+2));
              // }
              missingDims[slot] = dim + 1
              counts[slot] = dim + 2
            } else {
              // if (DEBUG) {
              //  System.out.println("      set docID=" + docID + " missing count=" + (dim+1));
              // }
              counts[slot] = dim + 1
            }
          }

          // TODO: sometimes use advance?
          docID = dc.approximation.nextDoc()
        }
      }

      // Collect:
      // if (DEBUG) {
      // System.out.println("  now collect: " + filledCount + " hits");
      // }
      for (i in 0 until filledCount) {
        var slot = filledSlots[i]
        collectDocID = docIDs[slot]
        collectScore = scores[slot]
        // if (DEBUG) {
        //  System.out.println("    docID=" + docIDs[slot] + " count=" + counts[slot]);
        // }
        if (counts[slot] == 1 + numDims) {
          collectHit(collector, dims)
        } else if (counts[slot] == numDims) {
          collectNearMiss(dims[missingDims[slot]].sidewaysLeafCollector)
        }
      }

      if (nextChunkStart >= maxDoc) {
        break
      }

      nextChunkStart += CHUNK
    }
  }

  private fun doUnionScoring(acceptDocs: Bits?, collector: LeafCollector, dims: Array<DocsAndCost>): Unit {
    // if (DEBUG) {
    //  System.out.println("  doUnionScoring");
    // }
    setScorer(collector, Score())

    var maxDoc = context.reader().maxDoc()
    var numDims = dims.size

    // TODO: maybe a class like BS, instead of parallel arrays
    val filledSlots = IntArray(CHUNK)
    val docIDs = IntArray(CHUNK)
    val scores = FloatArray(CHUNK)
    val missingDims = IntArray(CHUNK)
    val counts = IntArray(CHUNK)

    docIDs[0] = -1

    // NOTE: this is basically a specialized version of
    // BooleanScorer, to the minShouldMatch=N-1 case, but
    // carefully tracking which dimension failed to match

    var nextChunkStart = CHUNK

    while (true) {
      // if (DEBUG) {
      //  System.out.println("\ncycle nextChunkStart=" + nextChunkStart + " docIds[0]=" +
      // docIDs[0]);
      // }
      var filledCount = 0
      var docID = baseIterator.docID()

      // if (DEBUG) {
      //  System.out.println("  base docID=" + docID);
      // }
      while (docID < nextChunkStart) {
        if (acceptDocs == null || acceptDocs.get(docID)) {
          var slot = docID and MASK
          // if (DEBUG) {
          //  System.out.println("    docIDs[slot=" + slot + "]=" + docID + " id=" +
          // context.reader().document(docID).get("id"));
          // }

          // Mark slot as valid:
          assert(docIDs[slot] != docID) { "slot=" + slot + " docID=" + docID }
          docIDs[slot] = docID
          scores[slot] = baseScorer.score()
          filledSlots[filledCount++] = slot
          missingDims[slot] = 0
          counts[slot] = 1
        }
        docID = baseIterator.nextDoc()
      }

      if (filledCount == 0) {
        if (nextChunkStart >= maxDoc) {
          break
        }
        nextChunkStart += CHUNK
        continue
      }

      // First drill-down dim, basically adds SHOULD onto
      // the baseQuery:
      // if (DEBUG) {
      //  System.out.println("  dim=0 [" + dims[0].dim + "]");
      // }
      run {
        var dc = dims[0]
        docID = dc.approximation.docID()
        // if (DEBUG) {
        //  System.out.println("    start docID=" + docID);
        // }
        while (docID < nextChunkStart) {
          var slot = docID and MASK
          if (docIDs[slot] == docID // this also checks that the doc is not deleted
              && (dc.twoPhase == null || dc.twoPhase!!.matches())) {
            // if (DEBUG) {
            //  System.out.println("      set docID=" + docID + " count=2");
            // }
            missingDims[slot] = 1
            counts[slot] = 2
          }
          docID = dc.approximation.nextDoc()
        }
      }

      for (dim in 1 until numDims) {
        // if (DEBUG) {
        //  System.out.println("  dim=" + dim + " [" + dims[dim].dim + "]");
        // }

        var dc = dims[dim]
        docID = dc.approximation.docID()
        // if (DEBUG) {
        //  System.out.println("    start docID=" + docID);
        // }
        while (docID < nextChunkStart) {
          var slot = docID and MASK
          if (docIDs[slot] == docID // also means that the doc is not deleted
              && counts[slot] >= dim
              && (dc.twoPhase == null || dc.twoPhase!!.matches())) {
            // This doc is still in the running...
            // TODO: single-valued dims will always be true
            // below; we could somehow specialize
            if (missingDims[slot] >= dim) {
              // if (DEBUG) {
              //  System.out.println("    set docID=" + docID + " count=" + (dim+2));
              // }
              missingDims[slot] = dim + 1
              counts[slot] = dim + 2
            } else {
              // if (DEBUG) {
              //  System.out.println("    set docID=" + docID + " missing count=" + (dim+1));
              // }
              counts[slot] = dim + 1
            }
          }
          docID = dc.approximation.nextDoc()
        }
      }

      // Collect:
      //  System.out.println("  now collect: " + filledCount + " hits");
      for (i in 0 until filledCount) {
        // NOTE: This is actually in-order collection,
        // because we only accept docs originally returned by
        // the baseScorer (ie that Scorer is AND'd)
        var slot = filledSlots[i]
        collectDocID = docIDs[slot]
        collectScore = scores[slot]
        // if (DEBUG) {
        //  System.out.println("    docID=" + docIDs[slot] + " count=" + counts[slot]);
        // }
        // System.out.println("  collect doc=" + collectDocID + " main.freq=" + (counts[slot]-1) + "
        // main.doc=" + collectDocID + " exactCount=" + numDims);
        if (counts[slot] == 1 + numDims) {
          //  System.out.println("      hit");
          collectHit(collector, dims)
        } else if (counts[slot] == numDims) {
          // System.out.println("    sw");
          collectNearMiss(dims[missingDims[slot]].sidewaysLeafCollector)
        }
      }

      if (nextChunkStart >= maxDoc) {
        break
      }

      nextChunkStart += CHUNK
    }
  }

  private fun collectHit(collector: LeafCollector, dims: Array<DocsAndCost>): Unit {
    // if (DEBUG) {
    // System.out.println("    hit");
    // }

    collector.collect(collectDocID)

    // TODO: we could "fix" faceting of the sideways counts
    // to do this "union" (of the drill down hits) in the
    // end instead:

    // Tally sideways counts:
    for (dim in dims) {
      dim.sidewaysLeafCollector.collect(collectDocID)
    }
  }

  private fun collectHit(collector: LeafCollector, dim: DocsAndCost): Unit {
    collector.collect(collectDocID)

    // Tally sideways count:
    dim.sidewaysLeafCollector.collect(collectDocID)
  }

  private fun collectHit(collector: LeafCollector, dims: List<DocsAndCost>): Unit {
    collector.collect(collectDocID)

    // Tally sideways counts:
    for (dim in dims) {
      dim.sidewaysLeafCollector.collect(collectDocID)
    }
  }

  private fun collectNearMiss(sidewaysCollector: LeafCollector): Unit {
    // if (DEBUG) {
    //  System.out.println("      missingDim=" + dim);
    // }
    sidewaysCollector.collect(collectDocID)
  }

  private fun finish(dims: Array<DocsAndCost>): Unit {
    // Note: We _only_ call #finish on the facets collectors we're managing here, but not the
    // "main" collector. This is because IndexSearcher handles calling #finish on the main
    // collector.
    for (dim in dims) {
      dim.sidewaysLeafCollector.finish()
    }
  }

  private fun setScorer(mainCollector: LeafCollector, scorer: Scorable): Unit {
    mainCollector.scorer = scorer
    for (dim in dims) {
      dim.sidewaysLeafCollector.scorer = scorer
    }
  }

  private inner class Score : Scorable() {

    
    override fun score(): Float {
      return collectScore
    }

    
    override val children: MutableCollection<ChildScorable> get() {
      return mutableListOf(ChildScorable(baseScorer, "MUST"))
    }
  }

  class DocsAndCost {
    // approximation of matching docs, or the scorer itself
    val approximation: DocIdSetIterator
    val cost: Long
    // two-phase confirmation, or null if the approximation is accurate
    val twoPhase: TwoPhaseIterator?
    val matchCost: Float
    val sidewaysLeafCollector: LeafCollector

    constructor(scorer: Scorer, sidewaysLeafCollector: LeafCollector) {
      val twoPhase = scorer.twoPhaseIterator()
      if (twoPhase == null) {
        this.approximation = scorer.iterator()
        this.twoPhase = null
        this.matchCost = 0f
      } else {
        this.approximation = twoPhase.approximation()
        this.twoPhase = twoPhase
        this.matchCost = twoPhase.matchCost()
      }
      this.cost = approximation.cost()
      this.sidewaysLeafCollector = sidewaysLeafCollector
    }
  }
}
