package org.gnit.lucenekmp.jdkport

class UnmodifiableMutableIterator<T>(private val delegate: MutableIterator<T>) : MutableIterator<T> {
    override fun hasNext(): Boolean = delegate.hasNext()

    override fun next(): T = delegate.next()

    override fun remove() {
        throw UnsupportedOperationException()
    }
}

class UnmodifiableMutableCollection<T>(private val delegate: MutableCollection<T>) : MutableCollection<T> {
    override val size: Int
        get() = delegate.size

    override fun contains(element: T): Boolean = delegate.contains(element)

    override fun containsAll(elements: Collection<T>): Boolean = delegate.containsAll(elements)

    override fun isEmpty(): Boolean = delegate.isEmpty()

    override fun iterator(): MutableIterator<T> = UnmodifiableMutableIterator(delegate.iterator())

    override fun add(element: T): Boolean {
        throw UnsupportedOperationException()
    }

    override fun addAll(elements: Collection<T>): Boolean {
        throw UnsupportedOperationException()
    }

    override fun clear() {
        throw UnsupportedOperationException()
    }

    override fun remove(element: T): Boolean {
        throw UnsupportedOperationException()
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        throw UnsupportedOperationException()
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        throw UnsupportedOperationException()
    }

    override fun equals(other: Any?): Boolean = delegate == other

    override fun hashCode(): Int = delegate.hashCode()

    override fun toString(): String = delegate.toString()
}

class UnmodifiableMutableSet<T>(private val delegate: MutableSet<T>) : MutableSet<T> {
    override val size: Int
        get() = delegate.size

    override fun contains(element: T): Boolean = delegate.contains(element)

    override fun containsAll(elements: Collection<T>): Boolean = delegate.containsAll(elements)

    override fun isEmpty(): Boolean = delegate.isEmpty()

    override fun iterator(): MutableIterator<T> = UnmodifiableMutableIterator(delegate.iterator())

    override fun add(element: T): Boolean {
        throw UnsupportedOperationException()
    }

    override fun addAll(elements: Collection<T>): Boolean {
        throw UnsupportedOperationException()
    }

    override fun clear() {
        throw UnsupportedOperationException()
    }

    override fun remove(element: T): Boolean {
        throw UnsupportedOperationException()
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        throw UnsupportedOperationException()
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        throw UnsupportedOperationException()
    }

    override fun equals(other: Any?): Boolean = delegate == other

    override fun hashCode(): Int = delegate.hashCode()

    override fun toString(): String = delegate.toString()
}

class UnmodifiableMutableMapEntry<K, V>(private val delegate: MutableMap.MutableEntry<K, V>) :
    MutableMap.MutableEntry<K, V> {
    override val key: K
        get() = delegate.key

    override val value: V
        get() = delegate.value

    override fun setValue(newValue: V): V {
        throw UnsupportedOperationException()
    }

    override fun equals(other: Any?): Boolean = delegate == other

    override fun hashCode(): Int = delegate.hashCode()

    override fun toString(): String = delegate.toString()
}

class UnmodifiableMutableMapEntrySet<K, V>(private val delegate: MutableSet<MutableMap.MutableEntry<K, V>>) :
    MutableSet<MutableMap.MutableEntry<K, V>> {
    override val size: Int
        get() = delegate.size

    override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean = delegate.contains(element)

    override fun containsAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean =
        delegate.containsAll(elements)

    override fun isEmpty(): Boolean = delegate.isEmpty()

    override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
        val iter = delegate.iterator()
        return object : MutableIterator<MutableMap.MutableEntry<K, V>> {
            override fun hasNext(): Boolean = iter.hasNext()

            override fun next(): MutableMap.MutableEntry<K, V> = UnmodifiableMutableMapEntry(iter.next())

            override fun remove() {
                throw UnsupportedOperationException()
            }
        }
    }

    override fun add(element: MutableMap.MutableEntry<K, V>): Boolean {
        throw UnsupportedOperationException()
    }

    override fun addAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean {
        throw UnsupportedOperationException()
    }

    override fun clear() {
        throw UnsupportedOperationException()
    }

    override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean {
        throw UnsupportedOperationException()
    }

    override fun removeAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean {
        throw UnsupportedOperationException()
    }

    override fun retainAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean {
        throw UnsupportedOperationException()
    }

    override fun equals(other: Any?): Boolean = delegate == other

    override fun hashCode(): Int = delegate.hashCode()

    override fun toString(): String = delegate.toString()
}

class UnmodifiableMutableMap<K, V>(private val delegate: MutableMap<K, V>) : MutableMap<K, V> {
    override val size: Int
        get() = delegate.size

    override fun isEmpty(): Boolean = delegate.isEmpty()

    override fun containsKey(key: K): Boolean = delegate.containsKey(key)

    override fun containsValue(value: V): Boolean = delegate.containsValue(value)

    override fun get(key: K): V? = delegate[key]

    override val keys: MutableSet<K>
        get() = UnmodifiableMutableSet(delegate.keys)

    override val values: MutableCollection<V>
        get() = UnmodifiableMutableCollection(delegate.values)

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = UnmodifiableMutableMapEntrySet(delegate.entries)

    override fun clear() {
        throw UnsupportedOperationException()
    }

    override fun put(key: K, value: V): V? {
        throw UnsupportedOperationException()
    }

    override fun putAll(from: Map<out K, V>) {
        throw UnsupportedOperationException()
    }

    override fun remove(key: K): V? {
        throw UnsupportedOperationException()
    }

    override fun equals(other: Any?): Boolean = delegate == other

    override fun hashCode(): Int = delegate.hashCode()

    override fun toString(): String = delegate.toString()
}
