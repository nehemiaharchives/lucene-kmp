package org.gnit.lucenekmp.codecs

import kotlinx.io.IOException
import org.gnit.lucenekmp.index.CorruptIndexException
import org.gnit.lucenekmp.index.IndexFormatTooNewException
import org.gnit.lucenekmp.index.IndexFormatTooOldException
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.fromByteArray
import org.gnit.lucenekmp.jdkport.toHexString
import org.gnit.lucenekmp.store.*
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import org.gnit.lucenekmp.util.StringHelper
import kotlin.experimental.and


/**
 * Utility class for reading and writing versioned headers.
 *
 *
 * Writing codec headers is useful to ensure that a file is in the format you think it is.
 *
 * @lucene.experimental
 */
object CodecUtil {
    /** Constant to identify the start of a codec header.  */
    const val CODEC_MAGIC: Int = 0x3fd76c17

    /** Constant to identify the start of a codec footer.  */
    const val FOOTER_MAGIC: Int = CODEC_MAGIC.inv()

    /**
     * Writes a codec header, which records both a string to identify the file and a version number.
     * This header can be parsed and validated with [ checkHeader()][.checkHeader].
     *
     *
     * CodecHeader --&gt; Magic,CodecName,Version
     *
     *
     *  * Magic --&gt; [Uint32][DataOutput.writeInt]. This identifies the start of the header.
     * It is always {@value #CODEC_MAGIC}.
     *  * CodecName --&gt; [String][DataOutput.writeString]. This is a string to identify this
     * file.
     *  * Version --&gt; [Uint32][DataOutput.writeInt]. Records the version of the file.
     *
     *
     *
     * Note that the length of a codec header depends only upon the name of the codec, so this
     * length can be computed at any time with [.headerLength].
     *
     * @param out Output stream
     * @param codec String to identify this file. It should be simple ASCII, less than 128 characters
     * in length.
     * @param version Version number
     * @throws IOException If there is an I/O error writing to the underlying medium.
     * @throws IllegalArgumentException If the codec name is not simple ASCII, or is more than 127
     * characters in length
     */
    @Throws(IOException::class)
    fun writeHeader(out: DataOutput, codec: String, version: Int) {
        val bytes = BytesRef(codec)
        require(!(bytes.length != codec.length || bytes.length >= 128)) { "codec must be simple ASCII, less than 128 characters in length [got $codec]" }
        writeBEInt(out, CODEC_MAGIC)
        out.writeString(codec)
        writeBEInt(out, version)
    }

    /**
     * Writes a codec header for an index file, which records both a string to identify the format of
     * the file, a version number, and data to identify the file instance (ID and auxiliary suffix
     * such as generation).
     *
     *
     * This header can be parsed and validated with [checkIndexHeader()][.checkIndexHeader].
     *
     *
     * IndexHeader --&gt; CodecHeader,ObjectID,ObjectSuffix
     *
     *
     *  * CodecHeader --&gt; [.writeHeader]
     *  * ObjectID --&gt; [byte][DataOutput.writeByte]<sup>16</sup>
     *  * ObjectSuffix --&gt; SuffixLength,SuffixBytes
     *  * SuffixLength --&gt; [byte][DataOutput.writeByte]
     *  * SuffixBytes --&gt; [byte][DataOutput.writeByte]<sup>SuffixLength</sup>
     *
     *
     *
     * Note that the length of an index header depends only upon the name of the codec and suffix,
     * so this length can be computed at any time with [.indexHeaderLength].
     *
     * @param out Output stream
     * @param codec String to identify the format of this file. It should be simple ASCII, less than
     * 128 characters in length.
     * @param id Unique identifier for this particular file instance.
     * @param suffix auxiliary suffix information for the file. It should be simple ASCII, less than
     * 256 characters in length.
     * @param version Version number
     * @throws IOException If there is an I/O error writing to the underlying medium.
     * @throws IllegalArgumentException If the codec name is not simple ASCII, or is more than 127
     * characters in length, or if id is invalid, or if the suffix is not simple ASCII, or more
     * than 255 characters in length.
     */
    @Throws(IOException::class)
    fun writeIndexHeader(
        out: DataOutput, codec: String, version: Int, id: ByteArray, suffix: String
    ) {
        require(id.size == StringHelper.ID_LENGTH) { "Invalid id: " + StringHelper.idToString(id) }
        writeHeader(out, codec, version)
        out.writeBytes(id, 0, id.size)
        val suffixBytes = BytesRef(suffix)
        require(!(suffixBytes.length != suffix.length || suffixBytes.length >= 256)) { "suffix must be simple ASCII, less than 256 characters in length [got $suffix]" }
        out.writeByte(suffixBytes.length as Byte)
        out.writeBytes(suffixBytes.bytes, suffixBytes.offset, suffixBytes.length)
    }

    /**
     * Computes the length of a codec header.
     *
     * @param codec Codec name.
     * @return length of the entire codec header.
     * @see .writeHeader
     */
    fun headerLength(codec: String): Int {
        return 9 + codec.length
    }

    /**
     * Computes the length of an index header.
     *
     * @param codec Codec name.
     * @return length of the entire index header.
     * @see .writeIndexHeader
     */
    fun indexHeaderLength(codec: String, suffix: String): Int {
        return headerLength(codec) + StringHelper.ID_LENGTH + 1 + suffix.length
    }

    /**
     * Reads and validates a header previously written with [.writeHeader].
     *
     *
     * When reading a file, supply the expected `codec` and an expected version range (
     * `minVersion to maxVersion`).
     *
     * @param in Input stream, positioned at the point where the header was previously written.
     * Typically this is located at the beginning of the file.
     * @param codec The expected codec name.
     * @param minVersion The minimum supported expected version number.
     * @param maxVersion The maximum supported expected version number.
     * @return The actual version found, when a valid header is found that matches `codec`,
     * with an actual version where `minVersion <= actual <= maxVersion`. Otherwise an
     * exception is thrown.
     * @throws CorruptIndexException If the first four bytes are not [.CODEC_MAGIC], or if the
     * actual codec found is not `codec`.
     * @throws IndexFormatTooOldException If the actual version is less than `minVersion`.
     * @throws IndexFormatTooNewException If the actual version is greater than `maxVersion
    ` * .
     * @throws IOException If there is an I/O error reading from the underlying medium.
     * @see .writeHeader
     */
    @Throws(IOException::class)
    fun checkHeader(`in`: DataInput, codec: String?, minVersion: Int, maxVersion: Int): Int {
        // Safety to guard against reading a bogus string:
        val actualHeader = readBEInt(`in`)
        if (actualHeader != CODEC_MAGIC) {
            throw CorruptIndexException(
                ("codec header mismatch: actual header="
                        + actualHeader
                        + " vs expected header="
                        + CODEC_MAGIC),
                `in`
            )
        }
        return checkHeaderNoMagic(`in`, codec, minVersion, maxVersion)
    }

    /**
     * Like [.checkHeader] except this version assumes the first int
     * has already been read and validated from the input.
     */
    @Throws(IOException::class)
    fun checkHeaderNoMagic(`in`: DataInput, codec: String?, minVersion: Int, maxVersion: Int): Int {
        val actualCodec: String = `in`.readString()
        if (actualCodec != codec) {
            throw CorruptIndexException(
                "codec mismatch: actual codec=$actualCodec vs expected codec=$codec", `in`
            )
        }

        val actualVersion = readBEInt(`in`)
        if (actualVersion < minVersion) {
            throw IndexFormatTooOldException(`in`, actualVersion, minVersion, maxVersion)
        }
        if (actualVersion > maxVersion) {
            throw IndexFormatTooNewException(`in`, actualVersion, minVersion, maxVersion)
        }

        return actualVersion
    }

    /**
     * Reads and validates a header previously written with [.writeIndexHeader].
     *
     *
     * When reading a file, supply the expected `codec`, expected version range (`
     * minVersion to maxVersion`), and object ID and suffix.
     *
     * @param in Input stream, positioned at the point where the header was previously written.
     * Typically this is located at the beginning of the file.
     * @param codec The expected codec name.
     * @param minVersion The minimum supported expected version number.
     * @param maxVersion The maximum supported expected version number.
     * @param expectedID The expected object identifier for this file.
     * @param expectedSuffix The expected auxiliary suffix for this file.
     * @return The actual version found, when a valid header is found that matches `codec`,
     * with an actual version where `minVersion <= actual <= maxVersion`, and matching
     * `expectedID` and `expectedSuffix` Otherwise an exception is thrown.
     * @throws CorruptIndexException If the first four bytes are not [.CODEC_MAGIC], or if the
     * actual codec found is not `codec`, or if the `expectedID` or `
     * expectedSuffix` do not match.
     * @throws IndexFormatTooOldException If the actual version is less than `minVersion`.
     * @throws IndexFormatTooNewException If the actual version is greater than `maxVersion
    ` * .
     * @throws IOException If there is an I/O error reading from the underlying medium.
     * @see .writeIndexHeader
     */
    @Throws(IOException::class)
    fun checkIndexHeader(
        `in`:  DataInput,
        codec: String?,
        minVersion: Int,
        maxVersion: Int,
        expectedID: ByteArray?,
        expectedSuffix: String?
    ): Int {
        val version = checkHeader(`in`, codec, minVersion, maxVersion)
        checkIndexHeaderID(`in`, expectedID)
        checkIndexHeaderSuffix(`in`, expectedSuffix)
        return version
    }

    /**
     * Expert: verifies the incoming [IndexInput] has an index header and that its segment ID
     * matches the expected one, and then copies that index header into the provided [ ]. This is useful when building compound files.
     *
     * @param in Input stream, positioned at the point where the index header was previously written.
     * Typically this is located at the beginning of the file.
     * @param out Output stream, where the header will be copied to.
     * @param expectedID Expected segment ID
     * @throws CorruptIndexException If the first four bytes are not [.CODEC_MAGIC], or if the
     * `expectedID` does not match.
     * @throws IOException If there is an I/O error reading from the underlying medium.
     * @lucene.internal
     */
    @Throws(IOException::class)
    fun verifyAndCopyIndexHeader(`in`: IndexInput, out: DataOutput, expectedID: ByteArray) {
        // make sure it's large enough to have a header and footer
        if (`in`.length() < footerLength() + headerLength("")) {
            throw CorruptIndexException(
                ("compound sub-files must have a valid codec header and footer: file is too small ("
                        + `in`.length()
                        + " bytes)"),
                `in`
            )
        }

        val actualHeader = readBEInt(`in`)
        if (actualHeader != CODEC_MAGIC) {
            throw CorruptIndexException(
                ("compound sub-files must have a valid codec header and footer: codec header mismatch: actual header="
                        + actualHeader
                        + " vs expected header="
                        + CODEC_MAGIC),
                `in`
            )
        }

        // we can't verify these, so we pass-through:
        val codec: String = `in`.readString()
        val version = readBEInt(`in`)

        // verify id:
        checkIndexHeaderID(`in`, expectedID)

        // we can't verify extension either, so we pass-through:
        val suffixLength: Int = (`in`.readByte() and 0xFF.toByte()).toInt()
        val suffixBytes = ByteArray(suffixLength)
        `in`.readBytes(suffixBytes, 0, suffixLength)

        // now write the header we just verified
        writeBEInt(out, CODEC_MAGIC)
        out.writeString(codec)
        writeBEInt(out, version)
        out.writeBytes(expectedID, 0, expectedID.size)
        out.writeByte(suffixLength.toByte())
        out.writeBytes(suffixBytes, 0, suffixLength)
    }

    /**
     * Retrieves the full index header from the provided [IndexInput]. This throws [ ] if this file does not appear to be an index file.
     */
    @Throws(IOException::class)
    fun readIndexHeader(`in`: IndexInput): ByteArray {
        `in`.seek(0)
        val actualHeader = readBEInt(`in`)
        if (actualHeader != CODEC_MAGIC) {
            throw CorruptIndexException(
                ("codec header mismatch: actual header="
                        + actualHeader
                        + " vs expected header="
                        + CODEC_MAGIC),
                `in`
            )
        }
        val codec: String = `in`.readString()
        readBEInt(`in`)
        `in`.seek(`in`.filePointer + StringHelper.ID_LENGTH)
        val suffixLength: Int = (`in`.readByte() and 0xFF.toByte()).toInt()
        val bytes = ByteArray(headerLength(codec) + StringHelper.ID_LENGTH + 1 + suffixLength)
        `in`.seek(0)
        `in`.readBytes(bytes, 0, bytes.size)
        return bytes
    }

    /**
     * Retrieves the full footer from the provided [IndexInput]. This throws [ ] if this file does not have a valid footer.
     */
    @Throws(IOException::class)
    fun readFooter(`in`: IndexInput): ByteArray {
        if (`in`.length() < footerLength()) {
            throw CorruptIndexException(
                ("misplaced codec footer (file truncated?): length="
                        + `in`.length()
                        + " but footerLength=="
                        + footerLength()),
                `in`
            )
        }
        `in`.seek(`in`.length() - footerLength())
        validateFooter(`in`)
        `in`.seek(`in`.length() - footerLength())
        val bytes = ByteArray(footerLength())
        `in`.readBytes(bytes, 0, bytes.size)
        return bytes
    }

    /** Expert: just reads and verifies the object ID of an index header  */
    @Throws(IOException::class)
    fun checkIndexHeaderID(`in`: DataInput, expectedID: ByteArray?): ByteArray {
        val id = ByteArray(StringHelper.ID_LENGTH)
        `in`.readBytes(id, 0, id.size)
        if (!id.contentEquals(expectedID)) {
            throw CorruptIndexException(
                ("file mismatch, expected id="
                        + StringHelper.idToString(expectedID)
                        + ", got="
                        + StringHelper.idToString(id)),
                `in`
            )
        }
        return id
    }

    /** Expert: just reads and verifies the suffix of an index header  */
    @Throws(IOException::class)
    fun checkIndexHeaderSuffix(`in`: DataInput, expectedSuffix: String?): String {
        val suffixLength: Int = (`in`.readByte() and 0xFF.toByte()).toInt()
        val suffixBytes = ByteArray(suffixLength)
        `in`.readBytes(suffixBytes, 0, suffixBytes.size)
        val suffix: String = String.fromByteArray(suffixBytes, StandardCharsets.UTF_8)
        if (suffix != expectedSuffix) {
            throw CorruptIndexException(
                "file mismatch, expected suffix=$expectedSuffix, got=$suffix", `in`
            )
        }
        return suffix
    }

    /**
     * Writes a codec footer, which records both a checksum algorithm ID and a checksum. This footer
     * can be parsed and validated with [checkFooter()][.checkFooter].
     *
     *
     * CodecFooter --&gt; Magic,AlgorithmID,Checksum
     *
     *
     *  * Magic --&gt; [Uint32][DataOutput.writeInt]. This identifies the start of the footer.
     * It is always {@value #FOOTER_MAGIC}.
     *  * AlgorithmID --&gt; [Uint32][DataOutput.writeInt]. This indicates the checksum
     * algorithm used. Currently this is always 0, for zlib-crc32.
     *  * Checksum --&gt; [Uint64][DataOutput.writeLong]. The actual checksum value for all
     * previous bytes in the stream, including the bytes from Magic and AlgorithmID.
     *
     *
     * @param out Output stream
     * @throws IOException If there is an I/O error writing to the underlying medium.
     */
    @Throws(IOException::class)
    fun writeFooter(out: IndexOutput) {
        writeBEInt(out, FOOTER_MAGIC)
        writeBEInt(out, 0)
        writeCRC(out)
    }

    /**
     * Computes the length of a codec footer.
     *
     * @return length of the entire codec footer.
     * @see .writeFooter
     */
    fun footerLength(): Int {
        return 16
    }

    /**
     * Validates the codec footer previously written by [.writeFooter].
     *
     * @return actual checksum value
     * @throws IOException if the footer is invalid, if the checksum does not match, or if `in`
     * is not properly positioned before the footer at the end of the stream.
     */
    @Throws(IOException::class)
    fun checkFooter(`in`: ChecksumIndexInput): Long {
        validateFooter(`in`)
        val actualChecksum: Long = `in`.checksum
        val expectedChecksum = readCRC(`in`)
        if (expectedChecksum != actualChecksum) {
            throw CorruptIndexException(
                ("checksum failed (hardware problem?) : expected="
                        + Long.toHexString(expectedChecksum)
                        + " actual="
                        + Long.toHexString(actualChecksum)),
                `in`
            )
        }
        return actualChecksum
    }

    /**
     * Validates the codec footer previously written by [.writeFooter], optionally passing an
     * unexpected exception that has already occurred.
     *
     *
     * When a `priorException` is provided, this method will add a suppressed exception
     * indicating whether the checksum for the stream passes, fails, or cannot be computed, and
     * rethrow it. Otherwise it behaves the same as [.checkFooter].
     *
     *
     * Example usage:
     *
     * <pre class="prettyprint">
     * try (ChecksumIndexInput input = ...) {
     * Throwable priorE = null;
     * try {
     * // ... read a bunch of stuff ...
     * } catch (Throwable exception) {
     * priorE = exception;
     * } finally {
     * CodecUtil.checkFooter(input, priorE);
     * }
     * }
    </pre> *
     */
    @Throws(IOException::class)
    fun checkFooter(`in`: ChecksumIndexInput, priorException: Throwable?) {
        if (priorException == null) {
            checkFooter(`in`)
        } else {
            try {
                // If we have evidence of corruption then we return the corruption as the
                // main exception and the prior exception gets suppressed. Otherwise we
                // return the prior exception with a suppressed exception that notifies
                // the user that checksums matched.
                val remaining: Long = `in`.length() - `in`.filePointer
                if (remaining < footerLength()) {
                    // corruption caused us to read into the checksum footer already: we can't proceed
                    throw CorruptIndexException(
                        ("checksum status indeterminate: remaining="
                                + remaining
                                + "; please run checkindex for more details"),
                        `in`
                    )
                } else {
                    // otherwise, skip any unread bytes.
                    `in`.skipBytes(remaining - footerLength())

                    // now check the footer
                    val checksum = checkFooter(`in`)
                    if (priorException !is IndexFormatTooOldException) {
                        // If the index format is too old and no corruption, do not add checksums
                        // matching message since this may tend to unnecessarily alarm people who
                        // see "JVM bug" in their logs
                        priorException.addSuppressed(
                            CorruptIndexException(
                                ("checksum passed ("
                                        + Long.toHexString(checksum)
                                        + "). possibly transient resource issue, or a Lucene or JVM bug"),
                                `in`
                            )
                        )
                    }
                }
            } catch (corruptException: CorruptIndexException) {
                corruptException.addSuppressed(priorException)
                throw corruptException
            } catch (t: Throwable) {
                // catch-all for things that shouldn't go wrong (e.g. OOM during readInt) but could...
                priorException.addSuppressed(
                    CorruptIndexException(
                        "checksum status indeterminate: unexpected exception", `in`, t
                    )
                )
            }
            throw IOUtils.rethrowAlways(priorException)
        }
    }

    /**
     * Returns (but does not validate) the checksum previously written by [.checkFooter].
     *
     * @return actual checksum value
     * @throws IOException if the footer is invalid
     */
    @Throws(IOException::class)
    fun retrieveChecksum(`in`: IndexInput): Long {
        if (`in`.length() < footerLength()) {
            throw CorruptIndexException(
                ("misplaced codec footer (file truncated?): length="
                        + `in`.length()
                        + " but footerLength=="
                        + footerLength()),
                `in`
            )
        }
        `in`.seek(`in`.length() - footerLength())
        validateFooter(`in`)
        return readCRC(`in`)
    }

    /**
     * Returns (but does not validate) the checksum previously written by [.checkFooter].
     *
     * @return actual checksum value
     * @throws IOException if the footer is invalid
     */
    @Throws(IOException::class)
    fun retrieveChecksum(`in`: IndexInput, expectedLength: Long): Long {
        require(expectedLength >= footerLength()) { "expectedLength cannot be less than the footer length" }
        if (`in`.length() < expectedLength) {
            throw CorruptIndexException(
                "truncated file: length=" + `in`.length() + " but expectedLength==" + expectedLength, `in`
            )
        } else if (`in`.length() > expectedLength) {
            throw CorruptIndexException(
                "file too long: length=" + `in`.length() + " but expectedLength==" + expectedLength, `in`
            )
        }

        return retrieveChecksum(`in`)
    }

    @Throws(IOException::class)
    private fun validateFooter(`in`: IndexInput) {
        val remaining: Long = `in`.length() - `in`.filePointer
        val expected = footerLength().toLong()
        if (remaining < expected) {
            throw CorruptIndexException(
                ("misplaced codec footer (file truncated?): remaining="
                        + remaining
                        + ", expected="
                        + expected
                        + ", fp="
                        + `in`.filePointer),
                `in`
            )
        } else if (remaining > expected) {
            throw CorruptIndexException(
                ("misplaced codec footer (file extended?): remaining="
                        + remaining
                        + ", expected="
                        + expected
                        + ", fp="
                        + `in`.filePointer),
                `in`
            )
        }

        val magic = readBEInt(`in`)
        if (magic != FOOTER_MAGIC) {
            throw CorruptIndexException(
                ("codec footer mismatch (file truncated?): actual footer="
                        + magic
                        + " vs expected footer="
                        + FOOTER_MAGIC),
                `in`
            )
        }

        val algorithmID = readBEInt(`in`)
        if (algorithmID != 0) {
            throw CorruptIndexException(
                "codec footer mismatch: unknown algorithmID: $algorithmID", `in`
            )
        }
    }

    /**
     * Clones the provided input, reads all bytes from the file, and calls [.checkFooter]
     *
     *
     * Note that this method may be slow, as it must process the entire file. If you just need to
     * extract the checksum value, call [.retrieveChecksum].
     */
    @Throws(IOException::class)
    fun checksumEntireFile(input: IndexInput): Long {
        val clone: IndexInput = input.clone()
        clone.seek(0)
        val `in`: ChecksumIndexInput = BufferedChecksumIndexInput(clone)
        require(`in`.filePointer == 0L)
        if (`in`.length() < footerLength()) {
            throw CorruptIndexException(
                ("misplaced codec footer (file truncated?): length="
                        + `in`.length()
                        + " but footerLength=="
                        + footerLength()),
                input
            )
        }
        `in`.seek(`in`.length() - footerLength())
        return checkFooter(`in`)
    }

    /**
     * Reads CRC32 value as a 64-bit long from the input.
     *
     * @throws CorruptIndexException if CRC is formatted incorrectly (wrong bits set)
     * @throws IOException if an i/o error occurs
     */
    @Throws(IOException::class)
    fun readCRC(input: IndexInput): Long {
        val value = readBELong(input)
        if ((value and -0x100000000L) != 0L) {
            throw CorruptIndexException("Illegal CRC-32 checksum: $value", input)
        }
        return value
    }

    /**
     * Writes CRC32 value as a 64-bit long to the output.
     *
     * @throws IllegalStateException if CRC is formatted incorrectly (wrong bits set)
     * @throws IOException if an i/o error occurs
     */
    @Throws(IOException::class)
    fun writeCRC(output: IndexOutput) {
        val value: Long = output.getChecksum()
        check((value and -0x100000000L) == 0L) { "Illegal CRC-32 checksum: $value (resource=$output)" }
        writeBELong(output, value)
    }

    /** write int value on header / footer with big endian order  */
    @Throws(IOException::class)
    fun writeBEInt(out: DataOutput, i: Int) {
        out.writeByte((i shr 24).toByte())
        out.writeByte((i shr 16).toByte())
        out.writeByte((i shr 8).toByte())
        out.writeByte(i.toByte())
    }

    /** write long value on header / footer with big endian order  */
    @Throws(IOException::class)
    fun writeBELong(out: DataOutput, l: Long) {
        writeBEInt(out, (l shr 32).toInt())
        writeBEInt(out, l.toInt())
    }

    /** read int value from header / footer with big endian order  */
    @Throws(IOException::class)
    fun readBEInt(`in`: DataInput): Int {
        return (((`in`.readByte() and 0xFF.toByte()).toLong() shl 24)
                or ((`in`.readByte() and 0xFF.toByte()).toLong() shl 16)
                or ((`in`.readByte() and 0xFF.toByte()).toLong() shl 8)
                or ((`in`.readByte() and 0xFF.toByte()).toLong())).toInt()
    }

    /** read long value from header / footer with big endian order  */
    @Throws(IOException::class)
    fun readBELong(`in`: DataInput): Long {
        val long = readBEInt(`in`).toLong()
        return (long shl 32) or (long and 0xFFFFFFFFL)
    }
}
