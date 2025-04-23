package org.gnit.lucenekmp.util.fst

import org.gnit.lucenekmp.util.fst.FST.BytesReader
import org.gnit.lucenekmp.store.DataOutput
import org.gnit.lucenekmp.util.Accountable
import kotlinx.io.IOException

/** Abstraction for reading bytes necessary for FST.  */
interface FSTReader : Accountable {
    /**
     * Get the reverse BytesReader for this FST
     *
     * @return the reverse BytesReader
     */
    fun getReverseBytesReader(): BytesReader

    /**
     * Write this FST to another DataOutput
     *
     * @param out the DataOutput
     * @throws IOException if exception occurred during writing
     */
    @Throws(IOException::class)
    fun writeTo(out: DataOutput)
}
