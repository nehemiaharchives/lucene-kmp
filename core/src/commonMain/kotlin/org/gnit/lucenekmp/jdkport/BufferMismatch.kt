package org.gnit.lucenekmp.jdkport

@Ported(from = "java.nio.BufferMismatch")
object BufferMismatch {

    //val SCOPED_MEMORY_ACCESS: ScopedMemoryAccess = ScopedMemoryAccess.getScopedMemoryAccess()

    fun mismatch(a: ByteBuffer, aOff: Int, b: ByteBuffer, bOff: Int, length: Int): Int {
        var i = 0
        /*if (length > 7) {
            if (a.get(aOff) != b.get(bOff)) return 0
            i = SCOPED_MEMORY_ACCESS.vectorizedMismatch(
                a.session(), b.session(),
                a.base(), a.address + aOff,
                b.base(), b.address + bOff,
                length,
                jdk.internal.util.ArraysSupport.LOG2_ARRAY_BYTE_INDEX_SCALE
            )
            if (i >= 0) return i
            i = length - i.inv()
        }*/
        while (i < length) {
            if (a.get(aOff + i) != b.get(bOff + i)) return i
            i++
        }
        return -1
    }
}
