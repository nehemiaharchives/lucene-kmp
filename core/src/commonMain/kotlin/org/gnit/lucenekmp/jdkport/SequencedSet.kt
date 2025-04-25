package org.gnit.lucenekmp.jdkport

/**
 * A collection that is both a [SequencedCollection] and a [Set]. As such,
 * it can be thought of either as a `Set` that also has a well-defined
 * [encounter order](SequencedCollection.html#encounter), or as a
 * `SequencedCollection` that also has unique elements.
 *
 *
 * This interface has the same requirements on the `equals` and `hashCode`
 * methods as defined by [Set.equals] and [Set.hashCode].
 * Thus, a `Set` and a `SequencedSet` will compare equals if and only
 * if they have equal elements, irrespective of ordering.
 *
 *
 * `SequencedSet` defines the [.reversed] method, which provides a
 * reverse-ordered [view](Collection.html#view) of this set. The only difference
 * from the [SequencedCollection.reversed] method is
 * that the return type of `SequencedSet.reversed` is `SequencedSet`.
 *
 *
 * This class is a member of the
 * [
 * Java Collections Framework]({@docRoot}/java.base/java/util/package-summary.html#CollectionsFramework).
 *
 * @param <E> the type of elements in this sequenced set
 * @since 21
</E> */
interface SequencedSet<E> : SequencedCollection<E>, MutableSet<E> {
    /**
     * {@inheritDoc}
     *
     * @return a reverse-ordered view of this collection, as a `SequencedSet`
     */
    override fun reversed(): SequencedSet<E>
}
