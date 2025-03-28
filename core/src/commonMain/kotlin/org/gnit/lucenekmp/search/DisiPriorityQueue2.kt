package org.gnit.lucenekmp.search


/** [DisiPriorityQueue] of two entries or less.  */
internal class DisiPriorityQueue2 : DisiPriorityQueue() {
    private var top: DisiWrapper? = null
    private var top2: DisiWrapper? = null

    override fun iterator(): MutableIterator<DisiWrapper?> {
        if (top2 != null) {
            return mutableListOf(top, top2).iterator()
        } else if (top != null) {
            return mutableListOf<DisiWrapper?>(top).iterator()
        } else {
            return mutableListOf<DisiWrapper?>().iterator()
        }
    }

    override fun size(): Int {
        return if (top2 == null) (if (top == null) 0 else 1) else 2
    }

    override fun top(): DisiWrapper? {
        return top
    }

    override fun top2(): DisiWrapper? {
        return top2
    }

    override fun topList(): DisiWrapper? {
        var topList: DisiWrapper? = null
        if (top != null) {
            top!!.next = null
            topList = top
            if (top2 != null && top!!.doc == top2!!.doc) {
                top2!!.next = topList
                topList = top2
            }
        }
        return topList
    }

    override fun add(entry: DisiWrapper): DisiWrapper {
        if (top == null) {
            return entry.also { top = it }
        } else if (top2 == null) {
            top2 = entry
            return updateTop()
        } else {
            throw IllegalStateException(
                "Trying to add a 3rd element to a DisiPriorityQueue configured with a max size of 2"
            )
        }
    }

    override fun pop(): DisiWrapper? {
        val ret = top
        top = top2
        top2 = null
        return ret
    }

    override fun updateTop(): DisiWrapper {
        if (top2 != null && top2!!.doc < top!!.doc) {
            val tmp = top
            top = top2
            top2 = tmp
        }
        return top!!
    }

    override fun updateTop(topReplacement: DisiWrapper): DisiWrapper {
        top = topReplacement
        return updateTop()
    }

    override fun clear() {
        top = null
        top2 = null
    }
}
