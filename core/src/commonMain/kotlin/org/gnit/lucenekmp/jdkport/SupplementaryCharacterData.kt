package org.gnit.lucenekmp.jdkport

@Ported(from = "sun.text.SupplementaryCharacterData")
class SupplementaryCharacterData(private val dataTable: IntArray) {
    fun getValue(index: Int): Int {
        require(index in 0x10000..0x10FFFF) { "Invalid code point: ${index.toString(16)}" }

        var i = 0
        var j = dataTable.size - 1
        while (true) {
            val k = (i + j) / 2
            val start = dataTable[k] ushr 8
            val end = dataTable[k + 1] ushr 8
            if (index < start) {
                j = k
            } else if (index > (end - 1)) {
                i = k
            } else {
                val v = dataTable[k] and 0xFF
                return if (v == 0xFF) {
                    IGNORE
                } else {
                    v
                }
            }
        }
    }

    fun getArray(): IntArray = dataTable

    private companion object {
        private const val IGNORE: Int = -1
    }
}
