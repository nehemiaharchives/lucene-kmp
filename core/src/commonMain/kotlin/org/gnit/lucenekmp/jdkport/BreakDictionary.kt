package org.gnit.lucenekmp.jdkport

/**
 * This is the class that represents the list of known words used by
 * DictionaryBasedBreakIterator.  The conceptual data structure used
 * here is a trie: there is a node hanging off the root node for every
 * letter that can start a word.  Each of these nodes has a node hanging
 * off of it for every letter that can be the second letter of a word
 * if this node is the first letter, and so on.  The trie is represented
 * as a two-dimensional array that can be treated as a table of state
 * transitions.  Indexes are used to compress this array, taking
 * advantage of the fact that this array will always be very sparse.
 */
@Ported(from = "sun.text.BreakDictionary")
internal class BreakDictionary(/*dictionaryName: String,*/ dictionaryData: ByteArray) {
    /**
     * Maps from characters to column numbers.  The main use of this is to
     * avoid making room in the array for empty columns.
     */
    private var columnMap: /*sun.text.Compact*/ByteArray? = null
    /*private var supplementaryCharColumnMap: SupplementaryCharacterData? = null*/ // TODO implement later

    /**
     * The number of actual columns in the table
     */
    private var numCols = 0

    /**
     * Columns are organized into groups of 32.  This says how many
     * column groups.  (We could calculate this, but we store the
     * value to avoid having to repeatedly calculate it.)
     */
    private var numColGroups = 0

    /**
     * The actual compressed state table.  Each conceptual row represents
     * a state, and the cells in it contain the row numbers of the states
     * to transition to for each possible letter.  0 is used to indicate
     * an illegal combination of letters (i.e., the error state).  The
     * table is compressed by eliminating all the unpopulated (i.e., zero)
     * cells.  Multiple conceptual rows can then be doubled up in a single
     * physical row by sliding them up and possibly shifting them to one
     * side or the other so the populated cells don't collide.  Indexes
     * are used to identify unpopulated cells and to locate populated cells.
     */
    private var table: ShortArray? = null

    /**
     * This index maps logical row numbers to physical row numbers
     */
    private var rowIndex: ShortArray? = null

    /**
     * A bitmap is used to tell which cells in the comceptual table are
     * populated.  This array contains all the unique bit combinations
     * in that bitmap.  If the table is more than 32 columns wide,
     * successive entries in this array are used for a single row.
     */
    private var rowIndexFlags: IntArray? = null

    /**
     * This index maps from a logical row number into the bitmap table above.
     * (This keeps us from storing duplicate bitmap combinations.)  Since there
     * are a lot of rows with only one populated cell, instead of wasting space
     * in the bitmap table, we just store a negative number in this index for
     * rows with one populated cell.  The absolute value of that number is
     * the column number of the populated cell.
     */
    private var rowIndexFlagsIndex: ShortArray? = null

    /**
     * For each logical row, this index contains a constant that is added to
     * the logical column number to get the physical column number
     */
    private var rowIndexShifts: ByteArray? = null

    //=========================================================================
    // deserialization
    //=========================================================================
    init {
        try {
            setupDictionary(/*dictionaryName,*/ dictionaryData)
        } catch (bue: BufferUnderflowException) {
            val e: /*java.util.MissingResource*/Exception
            e = /*java.util.MissingResource*/Exception(
                "Corrupted dictionary data ${bue.message}"
                /*dictionaryName,*/
            )
            /*e.initCause(bue)*/
            throw e
        }
    }

    private fun setupDictionary(/*dictionaryName: String,*/ dictionaryData: ByteArray) {
        val bb: ByteBuffer = ByteBuffer.wrap(dictionaryData)

        // check version
        val version: Int = bb.getInt()
        if (version != supportedVersion) {
            throw /*java.util.MissingResource*/Exception(
                "Dictionary version($version) is unsupported"/*,
                dictionaryName, ""*/
            )
        }

        // Check data size
        var len: Int = bb.getInt()
        if (bb.position + len != bb.limit) {
            throw /*java.util.MissingResource*/Exception(
                "Dictionary size is wrong: " + bb.limit/*,
                dictionaryName, ""*/
            )
        }

        // read in the column map for BMP characters (this is serialized in
        // its internal form: an index array followed by a data array)
        len = bb.getInt()
        val temp = ShortArray(len)
        for (i in 0..<len) {
            temp[i] = bb.getShort()
        }
        len = bb.getInt()
        val temp2 = ByteArray(len)
        bb.get(temp2)
        columnMap = temp.map { it.toByte() }.toTypedArray().toByteArray().plus(temp2) /*sun.text.CompactByteArray(temp, temp2)*/

        // read in numCols and numColGroups
        numCols = bb.getInt()
        numColGroups = bb.getInt()

        // read in the row-number index
        len = bb.getInt()
        rowIndex = ShortArray(len)
        for (i in 0..<len) {
            rowIndex!![i] = bb.getShort()
        }

        // load in the populated-cells bitmap: index first, then bitmap list
        len = bb.getInt()
        rowIndexFlagsIndex = ShortArray(len)
        for (i in 0..<len) {
            rowIndexFlagsIndex!![i] = bb.getShort()
        }
        len = bb.getInt()
        rowIndexFlags = IntArray(len)
        for (i in 0..<len) {
            rowIndexFlags!![i] = bb.getInt()
        }

        // load in the row-shift index
        len = bb.getInt()
        rowIndexShifts = ByteArray(len)
        bb.get(rowIndexShifts!!)

        // load in the actual state table
        len = bb.getInt()
        table = ShortArray(len)
        for (i in 0..<len) {
            table!![i] = bb.getShort()
        }

        // finally, prepare the column map for supplementary characters TODO implement later
        /*len = bb.getInt()
        val temp3 = IntArray(len)
        for (i in 0..<len) {
            temp3[i] = bb.getInt()
        }
        assert(bb.position == bb.limit)

        supplementaryCharColumnMap = SupplementaryCharacterData(temp3)*/
    }

    //=========================================================================
    // access to the words
    //=========================================================================
    /**
     * Uses the column map to map the character to a column number, then
     * passes the row and column number to getNextState()
     * @param row The current state
     * @param ch The character whose column we're interested in
     * @return The new state to transition to
     */
    fun getNextStateFromCharacter(row: Int, ch: Int): Short {
        val col: Int
        if (ch < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
            col = columnMap!!.elementAt(ch).toInt()
        } else {
            throw UnsupportedOperationException("not implemented yet") //TODO implement later
            /*col = supplementaryCharColumnMap.getValue(ch)*/
        }
        return getNextState(row, col)
    }

    /**
     * Returns the value in the cell with the specified (logical) row and
     * column numbers.  In DictionaryBasedBreakIterator, the row number is
     * a state number, the column number is an input, and the return value
     * is the row number of the new state to transition to.  (0 is the
     * "error" state, and -1 is the "end of word" state in a dictionary)
     * @param row The row number of the current state
     * @param col The column number of the input character (0 means "not a
     * dictionary character")
     * @return The row number of the new state to transition to
     */
    fun getNextState(row: Int, col: Int): Short {
        if (cellIsPopulated(row, col)) {
            // we map from logical to physical row number by looking up the
            // mapping in rowIndex; we map from logical column number to
            // physical column number by looking up a shift value for this
            // logical row and offsetting the logical column number by
            // the shift amount.  Then we can use internalAt() to actually
            // get the value out of the table.
            return internalAt(rowIndex!![row].toInt(), col + rowIndexShifts!![row])
        } else {
            return 0
        }
    }

    /**
     * Given (logical) row and column numbers, returns true if the
     * cell in that position is populated
     */
    private fun cellIsPopulated(row: Int, col: Int): Boolean {
        // look up the entry in the bitmap index for the specified row.
        // If it's a negative number, it's the column number of the only
        // populated cell in the row
        if (rowIndexFlagsIndex!![row] < 0) {
            return col == -rowIndexFlagsIndex!![row]
        } else {
            val flags = rowIndexFlags!![rowIndexFlagsIndex!![row] + (col shr 5)]
            return (flags and (1 shl (col and 0x1f))) != 0
        }
    }

    /**
     * Implementation of getNextState() when we know the specified cell is
     * populated.
     * @param row The PHYSICAL row number of the cell
     * @param col The PHYSICAL column number of the cell
     * @return The value stored in the cell
     */
    private fun internalAt(row: Int, col: Int): Short {
        // the table is a one-dimensional array, so this just does the math necessary
        // to treat it as a two-dimensional array (we don't just use a two-dimensional
        // array because two-dimensional arrays are inefficient in Java)
        return table!![row * numCols + col]
    }

    companion object {
        //=========================================================================
        // data members
        //=========================================================================
        /**
         * The version of the dictionary that was read in.
         */
        private const val supportedVersion = 1
    }
}
