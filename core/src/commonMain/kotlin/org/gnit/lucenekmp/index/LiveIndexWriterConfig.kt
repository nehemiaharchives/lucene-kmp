package org.gnit.lucenekmp.index

import kotlin.concurrent.Volatile

class LiveIndexWriterConfig {

    /** The comparator for sorting leaf readers.  */
    var leafSorter: Comparator<LeafReader>? = null


    /** [MergePolicy] for selecting merges.  */
    var mergePolicy: MergePolicy? = null

}