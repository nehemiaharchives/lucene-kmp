package org.gnit.lucenekmp.jdkport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BreakDictionaryTest {

    @Test
    fun testGetNextStateFromCharacter() {
        val data = buildSimpleDictionaryData()
        val dictionary = BreakDictionary(data)

        assertEquals(7, dictionary.getNextStateFromCharacter(0, 1).toInt())
        assertEquals(0, dictionary.getNextStateFromCharacter(0, 0).toInt())
        assertEquals(7, dictionary.getNextState(0, 1).toInt())
        assertEquals(0, dictionary.getNextState(0, 0).toInt())
    }

    @Test
    fun testUnsupportedVersionThrows() {
        val bb = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        bb.putInt(2)
        val data = bb.array()

        assertFailsWith<Exception> {
            BreakDictionary(data)
        }
    }

    @Test
    fun testInvalidDataSizeThrows() {
        val bb = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
        bb.putInt(1)
        bb.putInt(1)
        val data = bb.array()

        assertFailsWith<Exception> {
            BreakDictionary(data)
        }
    }

    private fun buildSimpleDictionaryData(): ByteArray {
        val columnMapShorts = shortArrayOf(0, 1)
        val columnMapBytes = byteArrayOf()
        val rowIndex = shortArrayOf(0)
        val rowIndexFlagsIndex = shortArrayOf((-1).toShort())
        val rowIndexFlags = intArrayOf()
        val rowIndexShifts = byteArrayOf(0)
        val table = shortArrayOf(0, 7)
        val numCols = 2
        val numColGroups = 1

        val dataSize = 4 +
            (columnMapShorts.size * 2) +
            4 +
            columnMapBytes.size +
            4 +
            4 +
            4 +
            (rowIndex.size * 2) +
            4 +
            (rowIndexFlagsIndex.size * 2) +
            4 +
            (rowIndexFlags.size * 4) +
            4 +
            rowIndexShifts.size +
            4 +
            (table.size * 2)

        val totalSize = 4 + 4 + dataSize
        val bb = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        bb.putInt(1)
        bb.putInt(dataSize)

        bb.putInt(columnMapShorts.size)
        for (value in columnMapShorts) {
            bb.putShort(value)
        }
        bb.putInt(columnMapBytes.size)
        if (columnMapBytes.isNotEmpty()) {
            bb.put(columnMapBytes)
        }

        bb.putInt(numCols)
        bb.putInt(numColGroups)

        bb.putInt(rowIndex.size)
        for (value in rowIndex) {
            bb.putShort(value)
        }

        bb.putInt(rowIndexFlagsIndex.size)
        for (value in rowIndexFlagsIndex) {
            bb.putShort(value)
        }

        bb.putInt(rowIndexFlags.size)
        for (value in rowIndexFlags) {
            bb.putInt(value)
        }

        bb.putInt(rowIndexShifts.size)
        bb.put(rowIndexShifts)

        bb.putInt(table.size)
        for (value in table) {
            bb.putShort(value)
        }

        return bb.array()
    }
}
