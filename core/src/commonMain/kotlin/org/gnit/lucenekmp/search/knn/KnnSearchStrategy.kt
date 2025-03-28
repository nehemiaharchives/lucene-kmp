package org.gnit.lucenekmp.search.knn

import org.gnit.lucenekmp.jdkport.Objects
import org.gnit.lucenekmp.search.DocIdSetIterator


/**
 * KnnSearchStrategy is a strategy for kNN search, providing additional search strategy
 * configuration
 *
 * @lucene.experimental
 */
abstract class KnnSearchStrategy {
    /** Override and implement search strategy instance equivalence properly in a subclass.  */
    abstract override fun equals(obj: Any?): Boolean

    /**
     * Override and implement search strategy hash code properly in a subclass.
     *
     * @see .equals
     */
    abstract override fun hashCode(): Int

    /**
     * A strategy for kNN search that uses HNSW
     *
     * @lucene.experimental
     */
    class Hnsw(filteredSearchThreshold: Int) : KnnSearchStrategy() {
        private val filteredSearchThreshold: Int

        /**
         * Create a new Hnsw strategy
         *
         * @param filteredSearchThreshold threshold for filtered search, a percentage value from 0 to
         * 100 where 0 means never use filtered search and 100 means always use filtered search.
         */
        init {
            require(!(filteredSearchThreshold < 0 || filteredSearchThreshold > 100)) { "filteredSearchThreshold must be >= 0 and <= 100" }
            this.filteredSearchThreshold = filteredSearchThreshold
        }

        fun filteredSearchThreshold(): Int {
            return filteredSearchThreshold
        }

        /**
         * Whether to use filtered search based on the ratio of vectors that pass the filter
         *
         * @param ratioPassingFilter ratio of vectors that pass the filter
         * @return true if filtered search should be used
         */
        fun useFilteredSearch(ratioPassingFilter: Float): Boolean {
            require(ratioPassingFilter >= 0 && ratioPassingFilter <= 1)
            return ratioPassingFilter * 100 < filteredSearchThreshold
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || this::class != o::class) return false
            val hnsw = o as Hnsw
            return filteredSearchThreshold == hnsw.filteredSearchThreshold
        }

        override fun hashCode(): Int {
            return Objects.hashCode(filteredSearchThreshold)
        }

        companion object {
            val DEFAULT: Hnsw = Hnsw(DEFAULT_FILTERED_SEARCH_THRESHOLD)
        }
    }

    /**
     * A strategy for kNN search that uses a set of entry points to start the search
     *
     * @lucene.experimental
     */
    class Seeded(entryPoints: DocIdSetIterator?, numberOfEntryPoints: Int, originalStrategy: KnnSearchStrategy) :
        KnnSearchStrategy() {
        private val entryPoints: DocIdSetIterator
        private val numberOfEntryPoints: Int
        private val originalStrategy: KnnSearchStrategy

        init {
            require(numberOfEntryPoints >= 0) { "numberOfEntryPoints must be >= 0" }
            this.numberOfEntryPoints = numberOfEntryPoints
            require(!(numberOfEntryPoints > 0 && entryPoints == null)) { "entryPoints must not be null" }
            this.entryPoints = if (entryPoints == null) DocIdSetIterator.empty() else entryPoints
            this.originalStrategy = originalStrategy
        }

        /**
         * Iterator of valid entry points for the kNN search
         *
         * @return DocIdSetIterator of entry points
         */
        fun entryPoints(): DocIdSetIterator {
            return entryPoints
        }

        /**
         * Number of valid entry points for the kNN search
         *
         * @return number of entry points
         */
        fun numberOfEntryPoints(): Int {
            return numberOfEntryPoints
        }

        /**
         * Original strategy to use after seeding
         *
         * @return original strategy
         */
        fun originalStrategy(): KnnSearchStrategy {
            return originalStrategy
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) return true
            if (o == null || this::class != o::class) return false
            val seeded = o as Seeded
            return numberOfEntryPoints == seeded.numberOfEntryPoints && entryPoints == seeded.entryPoints
                    && originalStrategy == seeded.originalStrategy
        }

        override fun hashCode(): Int {
            return Objects.hash(entryPoints, numberOfEntryPoints, originalStrategy)
        }
    }

    companion object {
        const val DEFAULT_FILTERED_SEARCH_THRESHOLD: Int = 60
    }
}
