package org.gnit.lucenekmp.util

import org.gnit.lucenekmp.jdkport.Collections
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.collections.AbstractMutableList
import kotlin.test.Test
import kotlin.test.assertEquals

class TestCollectionUtil : LuceneTestCase() {

    private fun createRandomList(maxSize: Int): MutableList<Int> {
        val rnd = random()
        val size = rnd.nextInt(maxSize) + 1
        return MutableList(size) { rnd.nextInt(size) }
    }

    private class NonRandomAccessList<E> : AbstractMutableList<E>() {
        private val delegate = mutableListOf<E>()
        override val size: Int
            get() = delegate.size

        override fun add(index: Int, element: E) {
            delegate.add(index, element)
        }

        override fun get(index: Int): E = delegate[index]

        override fun set(index: Int, element: E): E {
            return delegate.set(index, element)
        }

        override fun removeAt(index: Int): E = delegate.removeAt(index)
    }

    @Test
    fun testIntroSort() {
        repeat(atLeast(100)) {
            var list1 = createRandomList(2000)
            var list2 = ArrayList(list1)
            CollectionUtil.introSort(list1)
            list2.sort()
            assertEquals(list2, list1)

            list1 = createRandomList(2000)
            list2 = ArrayList(list1)
            CollectionUtil.introSort(list1, Collections.reverseOrder<Int>())
            list2.sortWith(Collections.reverseOrder())
            assertEquals(list2, list1)

            CollectionUtil.introSort(list1)
            list2.sort()
            assertEquals(list2, list1)
        }
    }

    @Test
    fun testTimSort() {
        repeat(atLeast(100)) {
            var list1 = createRandomList(2000)
            var list2 = ArrayList(list1)
            CollectionUtil.timSort(list1)
            list2.sort()
            assertEquals(list2, list1)

            list1 = createRandomList(2000)
            list2 = ArrayList(list1)
            CollectionUtil.timSort(list1, Collections.reverseOrder<Int>())
            list2.sortWith(Collections.reverseOrder())
            assertEquals(list2, list1)

            CollectionUtil.timSort(list1)
            list2.sort()
            assertEquals(list2, list1)
        }
    }

    @Test
    fun testEmptyListSort() {
        var list: MutableList<Int> = mutableListOf()
        CollectionUtil.introSort(list)
        CollectionUtil.timSort(list)
        CollectionUtil.introSort(list, Collections.reverseOrder<Int>())
        CollectionUtil.timSort(list, Collections.reverseOrder<Int>())

        list = NonRandomAccessList()
        CollectionUtil.introSort(list)
        CollectionUtil.timSort(list)
        CollectionUtil.introSort(list, Collections.reverseOrder<Int>())
        CollectionUtil.timSort(list, Collections.reverseOrder<Int>())
    }

    @Test
    fun testOneElementListSort() {
        val list = NonRandomAccessList<Int>()
        list.add(1)
        CollectionUtil.introSort(list)
        CollectionUtil.timSort(list)
        CollectionUtil.introSort(list, Collections.reverseOrder<Int>())
        CollectionUtil.timSort(list, Collections.reverseOrder<Int>())
    }
}
