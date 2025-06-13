package org.gnit.lucenekmp.util.compress

class TestHighLZ4 : LZ4TestCase() {
    override fun newHashTable(): LZ4.HashTable {
        val hashTable: LZ4.HashTable = LZ4.HighCompressionHashTable()
        return AssertingHashTable(hashTable)
    }
}
