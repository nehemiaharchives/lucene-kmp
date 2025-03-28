package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.MergeInfo
import org.gnit.lucenekmp.util.Bits
import org.gnit.lucenekmp.util.IOConsumer
import org.gnit.lucenekmp.util.IOFunction
import org.gnit.lucenekmp.util.IOSupplier
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.InfoStream
import org.gnit.lucenekmp.util.ThreadInterruptedException

/**
 * Expert: a MergePolicy determines the sequence of primitive merge operations.
 *
 * <p>Whenever the segments in an index have been altered by {@link IndexWriter}, either the
 * addition of a newly flushed segment, addition of many segments from addIndexes* calls, or a
 * previous merge that may now need to cascade, {@link IndexWriter} invokes {@link #findMerges} to
 * give the MergePolicy a chance to pick merges that are now required. This method returns a {@link
 * MergeSpecification} instance describing the set of merges that should be done, or null if no
 * merges are necessary. When IndexWriter.forceMerge is called, it calls {@link
 * #findForcedMerges(SegmentInfos, int, Map, MergeContext)} and the MergePolicy should then return
 * the necessary merges.
 *
 * <p>Note that the policy can return more than one merge at a time. In this case, if the writer is
 * using {@link SerialMergeScheduler}, the merges will be run sequentially but if it is using {@link
 * ConcurrentMergeScheduler} they will be run concurrently.
 *
 * <p>The default MergePolicy is {@link TieredMergePolicy}.
 *
 * @lucene.experimental
 */
public abstract class MergePolicy {



}