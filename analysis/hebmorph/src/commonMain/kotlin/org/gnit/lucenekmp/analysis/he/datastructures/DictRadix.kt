package org.gnit.lucenekmp.analysis.he.datastructures

import org.gnit.lucenekmp.analysis.he.LookupTolerators
import org.gnit.lucenekmp.analysis.he.Reference

class DictRadix<T>(private val caseSensitiveKeys: Boolean = true) : Iterable<T> {
    inner class DictNode {
        private var children: Array<DictNode>? = null
        private var key: CharArray? = null
        private var value: T? = null

        fun getValue(): T? = value

        fun setValue(value: T?) {
            this.value = value
        }

        fun setChildren(children: Array<DictNode>?) {
            this.children = children
        }

        fun getChildren(): Array<DictNode>? = children

        fun setKey(key: CharArray?) {
            this.key = key
        }

        fun getKey(): CharArray? = key

        fun clear() {
            children = null
            key = null
            value = null
        }

        override fun toString(): String {
            return "[ value=$value; children=${children?.size ?: 0} ]"
        }
    }

    protected inner class TolerantLookupCrawler(private val toleranceFunctions: Array<LookupTolerators.ToleranceFunction>) {
        protected inner class MatchCandidate(
            private var keyPos: Byte,
            private var word: String,
            private var score: Float = 1.0f,
        ) {
            fun getKeyPos(): Byte = keyPos

            fun setKeyPos(keyPos: Byte) {
                this.keyPos = keyPos
            }

            fun getWord(): String = word

            fun setWord(word: String) {
                this.word = word
            }

            fun getScore(): Float = score

            fun setScore(score: Float) {
                this.score = score
            }
        }

        private lateinit var key: CharArray

        fun lookupTolerant(strKey: String): List<LookupResult>? {
            val resultSet = ArrayList<LookupResult>()
            key = strKey.toCharArray()
            lookupTolerantImpl(getRootNode(), MatchCandidate(0, "", 1.0f), resultSet)
            if (resultSet.size > 0) {
                return resultSet
            }
            return null
        }

        private fun lookupTolerantImpl(cur: DictNode, mc: MatchCandidate, resultSet: MutableList<LookupResult>) {
            val children = cur.getChildren() ?: return

            //System.out.println("--------------------------");
            //System.out.println(String.format("Processing children for word %1$s", mc.Word));
            for (child in children) {
                doKeyMatching(child, 0, mc, resultSet)
            }
            //System.out.println(String.format("Completed processing node children for word %1$s", mc.Word));
            //System.out.println("--------------------------");
        }

        private fun doKeyMatching(node: DictNode, nodeKeyPosArg: Byte, mc: MatchCandidate, resultSet: MutableList<LookupResult>) {
            var nodeKeyPos = nodeKeyPosArg
            var currentKeyPos = mc.getKeyPos()
            val startingNodeKeyPos = nodeKeyPos
            val nodeKey = requireNotNull(node.getKey())
            while ((nodeKeyPos.toInt() < nodeKey.size) && (currentKeyPos.toInt() < key.size)) {
                // toleration
                for (tf in toleranceFunctions) {
                    var tmpKeyPos = mc.getKeyPos()
                    var tmpScore = mc.getScore()
                    val tempRefObject = Reference(tmpKeyPos)
                    val tempRefObject2 = Reference(tmpScore)
                    val tret = tf.tolerate(key, tempRefObject, mc.getWord(), tempRefObject2, nodeKey[nodeKeyPos.toInt()])
                    tmpKeyPos = tempRefObject.ref
                    tmpScore = tempRefObject2.ref
                    if (tret != null) {
                        //System.out.println(String.format("%1$s tolerated a char, attempting word %2$s", tf.getClass().getName(), mc.Word + node.getKey()[nodeKeyPos]));

                        var consumedLetters = ""
                        if ((tret > 0) && (tret <= nodeKey.size)) {
                            consumedLetters = nodeKey.concatToString(nodeKeyPos.toInt(), nodeKeyPos.toInt() + tret)
                        }
                        val nmc = MatchCandidate(tmpKeyPos, mc.getWord() + consumedLetters, tmpScore)
                        if ((nodeKeyPos + tret).toInt() == nodeKey.size) {
                            lookupTolerantImpl(node, nmc, resultSet)
                        } else {
                            doKeyMatching(node, (nodeKeyPos + tret).toByte(), nmc, resultSet)
                        }
                    }
                }

                // standard key matching
                if (nodeKey[nodeKeyPos.toInt()] != key[currentKeyPos.toInt()]) {
                    break
                }

                //System.out.println(String.format("Matched char: %1$s", key[currentKeyPos]));
                currentKeyPos++
                nodeKeyPos++
            }

            if (nodeKeyPos.toInt() == nodeKey.size) {
                if (currentKeyPos.toInt() == key.size) {
                    //System.out.println(String.format("Consumed the whole key"));
                    val value = node.getValue()
                    if (value != null) {
                        resultSet.add(
                            LookupResult(
                                mc.getWord() + nodeKey.concatToString(startingNodeKeyPos.toInt(), nodeKeyPos.toInt()),
                                value,
                                mc.getScore(),
                            ),
                        )
                    }
                } else {
                    val nmc = MatchCandidate(
                        currentKeyPos,
                        mc.getWord() + nodeKey.concatToString(startingNodeKeyPos.toInt(), nodeKeyPos.toInt()),
                        mc.getScore(),
                    )
                    lookupTolerantImpl(node, nmc, resultSet)
                }
            }
        }
    }

    protected val m_root: DictNode = DictNode()

    fun getRootNode(): DictNode = m_root

    protected var m_nCount = 0

    fun getCount(): Int = m_nCount

    private var m_bAllowValueOverride = false

    fun getAllowValueOverride(): Boolean = m_bAllowValueOverride

    fun setAllowValueOverride(`val`: Boolean) {
        m_bAllowValueOverride = `val`
    }

    fun lookup(key: String): T? {
        return lookup(key.toCharArray(), false)
    }

    fun lookup(key: String, allowPartial: Boolean): T? {
        return lookup(key.toCharArray(), allowPartial)
    }

    fun lookup(key: CharArray, allowPartial: Boolean): T? {
        return lookup(key, 0, getCharArrayLength(key), allowPartial)
    }

    fun lookup(key: CharArray, keyPos: Int, keyLength: Int, allowPartial: Boolean): T? {
        return lookup(key, keyPos, keyLength, 0, allowPartial)
    }

    fun lookup(key: CharArray, keyPos: Int, keyLength: Int, keyOffset: Int, allowPartial: Boolean): T? {
        val dn = lookupImpl(key, keyPos, keyLength, keyOffset, allowPartial)
        if (dn == null) return null

        return dn.getValue()
    }

    /**
     * Simple, efficient method for exact lookup in the radix
     *
     * @param key
     * @return
     */
    private fun lookupImpl(key: CharArray, keyPosArg: Int, keyLength: Int, keyOffset: Int, allowPartial: Boolean): DictNode? {
        var keyPos = keyPosArg
        var n: Int

        var cur: DictNode? = m_root
        while ((cur != null) && (cur.getChildren() != null)) {
            val children = cur.getChildren()!!
            var childPos = 0
            while (true) {
                val child = children[childPos]
                val childKey = requireNotNull(child.getKey())

                // Do key matching
                n = 0
                while ((n < childKey.size) &&
                    (keyPos - keyOffset < keyLength) &&
                    ((childKey[n] == key[keyPos]) || (!caseSensitiveKeys && childKey[n] == key[keyPos].lowercaseChar()))
                ) {
                    keyPos++
                    n++
                }

                if (n == childKey.size) { // We consumed the child's key, and so far it matches our key
                    // We consumed both the child's key and the requested key, meaning we found the requested node
                    if (keyLength == keyPos - keyOffset) {
                        return child
                    }
                    // We consumed this child's key, but the key we are looking for isn't over yet
                    else if (keyLength > keyPos - keyOffset) {
                        cur = child
                        break
                    }
                } else if (allowPartial && keyPos - keyOffset == keyLength) {
                    return null
                } else if ((n > 0) || (childPos + 1 == children.size)) { // We looked at all the node's children -  Incomplete match to child's key (worths nothing)
                    throw IllegalArgumentException()
                }
                childPos++
            }
        }

        if (allowPartial && keyLength == keyPos - keyOffset) return null

        throw IllegalArgumentException()
    }

    inner class LookupResult(_word: String, _data: T, _score: Float) {
        private var word: String = _word
        private var data: T = _data
        private var score: Float = _score

        fun setScore(score: Float) {
            this.score = score
        }

        fun getScore(): Float = score

        fun setData(data: T) {
            this.data = data
        }

        fun getData(): T = data

        fun setWord(word: String) {
            this.word = word
        }

        fun getWord(): String = word
    }

    fun lookupTolerant(strKey: String, tolFuncs: Array<LookupTolerators.ToleranceFunction>): List<LookupResult>? {
        val tlc = TolerantLookupCrawler(tolFuncs)
        return tlc.lookupTolerant(strKey)
    }

    fun addNode(key: String, data: T) {
        addNode(key.toCharArray(), data)
    }

    fun addNode(key: CharArray, data: T) {
        // Support case insensitive lookups if requested - this will collapse
        // same keys with the different case into one, overriding values
        if (!caseSensitiveKeys) {
            for (i in key.indices) {
                key[i] = key[i].lowercaseChar()
            }
        }

        // Since key might be a buffer array which is longer than the actual word in it, we can't
        // just use key.Length
        val keyLength = getCharArrayLength(key)

        var keyPos = 0
        var cur: DictNode? = m_root
        while (cur != null) {
            val curNode = cur
            // No children, but key is definitely a descendant
            if (curNode.getChildren() == null) {
                // TODO: This assumes cur has a value and therefore is a leaf, hence we branch
                // instead of merging keys. Is this always the case?

                val newChild = DictNode()
                newChild.setKey(CharArray(keyLength - keyPos))
                key.copyInto(requireNotNull(newChild.getKey()), 0, keyPos, keyLength)
                newChild.setValue(data)

                curNode.setChildren(Array(1) { newChild })
                m_nCount++
                return
            }

            // Iterate through all children of the current node, and either switch node based on the
            // key, find a node to split into 2, or add a new child with the remaining path
            var childPos = 0
            var bFoundChild = false
            while (childPos < curNode.getChildren()!!.size) {
                val child = curNode.getChildren()!![childPos]

                var n = 0
                val childKey = requireNotNull(child.getKey())

                // By definition, there is no such thing as a null _Key
                while ((n < childKey.size) && (keyPos < keyLength) && (childKey[n] == key[keyPos]) && (key[keyPos] != '\u0000')) {
                    keyPos++
                    n++
                }

                // If it was a match, even partial
                if (n > 0) {
                    bFoundChild = true

                    // We consumed this child's key, but the key we are looking for isn't over yet
                    if ((n == childKey.size) && (keyLength > keyPos)) {
                        cur = child
                        break
                    }
                    // We consumed none of the keys
                    else if ((childKey.size > n) && (keyLength > keyPos)) {
                        // split
                        val bridgeChild = DictNode()
                        bridgeChild.setKey(CharArray(n))
                        childKey.copyInto(requireNotNull(bridgeChild.getKey()), 0, 0, n)

                        val childNewKeyLen = childKey.size - n
                        val childNewKey = CharArray(childNewKeyLen)
                        childKey.copyInto(childNewKey, 0, n, childKey.size)
                        child.setKey(childNewKey)

                        bridgeChild.setChildren(arrayOf(child, child))

                        val newNode = DictNode()
                        newNode.setKey(CharArray(keyLength - keyPos))
                        key.copyInto(requireNotNull(newNode.getKey()), 0, keyPos, keyLength)
                        newNode.setValue(data)

                        if (requireNotNull(child.getKey())[0] - requireNotNull(newNode.getKey())[0] < 0) {
                            bridgeChild.getChildren()!![0] = child
                            bridgeChild.getChildren()!![1] = newNode
                        } else {
                            bridgeChild.getChildren()!![0] = newNode
                            bridgeChild.getChildren()!![1] = child
                        }

                        curNode.getChildren()!![childPos] = bridgeChild

                        m_nCount++

                        return
                    }
                    // We consumed the requested key, but the there's still more chars in the child's key
                    else if ((childKey.size > n) && (keyLength == keyPos)) {
                        // split
                        val newChild = DictNode()
                        newChild.setKey(CharArray(n))
                        childKey.copyInto(requireNotNull(newChild.getKey()), 0, 0, n)

                        val childNewKeyLen = childKey.size - n
                        val childNewKey = CharArray(childNewKeyLen)
                        childKey.copyInto(childNewKey, 0, n, childKey.size)
                        child.setKey(childNewKey)

                        newChild.setChildren(Array(1) { child })
                        newChild.setValue(data)

                        curNode.getChildren()!![childPos] = newChild

                        m_nCount++

                        return
                    }
                    // We consumed both the child's key and the requested key
                    else if ((n == childKey.size) && (keyLength == keyPos)) {
                        if (child.getValue() == null) {
                            child.setValue(data)
                            m_nCount++
                        } else if (m_bAllowValueOverride) {
                            // Only override data if this radix object is configured to do this
                            child.setValue(data)
                        }
                        return
                    }
                }
                childPos++
            }

            if (!bFoundChild) {
                // Dead end - add a new child and return
                val newChild = DictNode()
                newChild.setKey(CharArray(keyLength - keyPos))
                key.copyInto(requireNotNull(newChild.getKey()), 0, keyPos, keyLength)
                newChild.setValue(data)

                val children = curNode.getChildren()!!
                val newArray = Array(children.size + 1) { m_root }
                var curPos = 0
                while (curPos < children.size) {
                    if (requireNotNull(newChild.getKey())[0] - requireNotNull(children[curPos].getKey())[0] < 0) break
                    newArray[curPos] = children[curPos]
                    ++curPos
                }
                newArray[curPos] = newChild
                while (curPos < children.size) {
                    newArray[curPos + 1] = children[curPos]
                    ++curPos
                }
                curNode.setChildren(newArray)

                m_nCount++

                return
            }
        }
    }

    fun clear() {
        m_root.clear()
        m_nCount = 0
    }

    inner class RadixEnumerator(private val radix: DictRadix<T>) : Iterator<T> {
        private val nodesPath = ArrayList<DictNode>()

        init {
            nodesPath.add(radix.m_root)
        }

        fun getCurrentKey(): String {
            val sb = StringBuilder()
            for (dn in nodesPath) {
                val key = dn.getKey()
                if (key != null) {
                    sb.append(key.concatToString())
                } else {
                    check(dn == radix.m_root)
                }
            }
            return sb.toString()
        }

        override fun next(): T {
            check(nodesPath.size > 0)
            return requireNotNull(nodesPath[nodesPath.lastIndex].getValue())
        }

        override fun hasNext(): Boolean {
            var goUp = false

            while (nodesPath.size > 0) {
                val n = nodesPath[nodesPath.lastIndex]
                val children = n.getChildren()
                if (goUp || children == null || children.isEmpty()) {
                    nodesPath.removeAt(nodesPath.lastIndex)
                    if (nodesPath.isEmpty()) break
                    goUp = true
                    val parentChildren = nodesPath[nodesPath.lastIndex].getChildren()!!
                    for (i in parentChildren.indices) {
                        // Move to the next child
                        if ((parentChildren[i] == n) && (i + 1 < parentChildren.size)) {
                            nodesPath.add(parentChildren[i + 1])
                            if (nodesPath[nodesPath.lastIndex].getValue() != null) return true
                            goUp = false
                            break
                        }
                    }
                } else {
                    nodesPath.add(children[0])
                    goUp = false
                    if (children[0].getValue() != null) return true
                }
            }
            return false
        }

        override fun hashCode(): Int {
            val prime = 31
            var ret = m_root.getKey().hashCode()
            ret = prime * ret + requireNotNull(m_root.getChildren()).size
            ret = prime * ret + m_root.getValue().hashCode()
            return prime * ret + m_nCount
        }
    }

    override fun iterator(): Iterator<T> {
        return RadixEnumerator(this)
    }

    override fun equals(other: Any?): Boolean { //we consider two DictRadix to be equal if they contain exactly the same objects.
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false
        other as DictRadix<T>
        if (this.getCount() != other.getCount()) {
            return false
        }
        val en1 = this.iterator() as RadixEnumerator
        val en2 = other.iterator() as DictRadix<T>.RadixEnumerator
        while (en1.hasNext() && en2.hasNext()) {
            if (en1.getCurrentKey() != en2.getCurrentKey()) {
                return false
            }
            if (en1.next() != en2.next()) {
                return false
            }
        }
        if ((en1.hasNext() && !en2.hasNext()) || (!en1.hasNext() && en2.hasNext())) {
            return false
        }
        return true
    }

    companion object {
        private fun getCharArrayLength(ar: CharArray): Int {
            var i = 0
            while ((ar.size > i) && (ar[i] != '\u0000')) {
                i++
            }
            return i
        }
    }
}
