package org.gnit.lucenekmp.search


/**
 * A [Multiset] is a set that allows for duplicate elements. Two [Multiset]s are equal
 * if they contain the same unique elements and if each unique element has as many occurrences in
 * both multisets. Iteration order is not specified.
 *
 * @lucene.internal
 */
class Multiset<T>
/** Create an empty [Multiset].  */
    : AbstractCollection<T>() {
    private val map: MutableMap<T, Int> = HashMap<T, Int>()
    override var size = 0

    override fun iterator(): MutableIterator<T> {
        val mapIterator = map.entries.iterator()
        return object : MutableIterator<T> {
            var current: T? = null
            var remaining: Int = 0

            override fun hasNext(): Boolean {
                return remaining > 0 || mapIterator.hasNext()
            }

            override fun next(): T {
                if (remaining == 0) {
                    val next = mapIterator.next()
                    current = next.key
                    remaining = next.value!!
                }
                require(remaining > 0)
                remaining -= 1
                return current!!
            }

            override fun remove() {
                if (current == null) {
                    throw IllegalStateException("next() has not been called yet")
                }
                if (remaining == 0) {
                    mapIterator.remove()
                } else {
                    // We need to store (remaining) as the new count, not the local variable
                    // which represents only the remaining items to iterate
                    val totalCount = map[current]!!
                    map[current!!] = totalCount - 1
                }
                size -= 1
                current = null
            }
        }
    }

    fun size(): Int {
        return size
    }

    fun clear() {
        map.clear()
        size = 0
    }

    fun add(e: T): Boolean {
        val number = if (map.containsKey(e)) {
            map[e]!!
        } else {
            0
        }

        map.put(e, (number + 1))
        size += 1
        return true
    }

    fun addAll(c: Collection<T>): Boolean {
        var modified = false
        for (e in c) {
            modified = add(e) || modified
        }
        return modified
    }

    fun remove(o: Any?): Boolean {
        val count = map.get(o)
        if (count == null) {
            return false
        } else if (1 == count) {
            map.remove(o)
        } else {
            map.put(o as T, count - 1)
        }
        size -= 1
        return true
    }

    override fun contains(o: T): Boolean {
        return map.containsKey(o)
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null || obj::class != this::class) {
            return false
        }
        val that = obj as Multiset<*>
        return size == that.size // not necessary but helps escaping early
                && map == that.map
    }

    override fun hashCode(): Int {
        return 31 * this::class.hashCode() + map.hashCode()
    }
}
