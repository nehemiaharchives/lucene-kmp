package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.jdkport.Character


/**
 * This class contains useful constants representing filenames and extensions used by lucene, as
 * well as convenience methods for querying whether a file name matches an extension ([ ][.matchesExtension]), as well as generating file names from a
 * segment name, generation and extension ( [ fileNameFromGeneration][.fileNameFromGeneration], [segmentFileName][.segmentFileName]).
 *
 *
 * **NOTE**: extensions used by codecs are not listed here. You must interact with the [ ] directly.
 *
 * @lucene.internal
 */
object IndexFileNames {
    /** Name of the index segment file  */
    const val SEGMENTS: String = "segments"

    /** Name of pending index segment file  */
    const val PENDING_SEGMENTS: String = "pending_segments"

    /**
     * Computes the full file name from base, extension and generation. If the generation is -1, the
     * file name is null. If it's 0, the file name is &lt;base&gt;.&lt;ext&gt;. If it's &gt; 0, the
     * file name is &lt;base&gt;_&lt;gen&gt;.&lt;ext&gt;.<br></br>
     * **NOTE:** .&lt;ext&gt; is added to the name only if `ext` is not an empty string.
     *
     * @param base main part of the file name
     * @param ext extension of the filename
     * @param gen generation
     */
    fun fileNameFromGeneration(base: String, ext: String, gen: Long): String? {
        if (gen == -1L) {
            return null
        } else if (gen == 0L) {
            return segmentFileName(base, "", ext)
        } else {
            require(gen > 0)
            // The '6' part in the length is: 1 for '.', 1 for '_' and 4 as estimate
            // to the gen length as string (hopefully an upper limit so SB won't
            // expand in the middle.
            val res: StringBuilder =
                StringBuilder(base.length + 6 + ext.length)
                    .append(base)
                    .append('_')
                    .append(gen.toString(Character.MAX_RADIX))
            if (ext.isNotEmpty()) {
                res.append('.').append(ext)
            }
            return res.toString()
        }
    }

    /**
     * Returns a file name that includes the given segment name, your own custom name and extension.
     * The format of the filename is: &lt;segmentName&gt;(_&lt;name&gt;)(.&lt;ext&gt;).
     *
     *
     * **NOTE:** .&lt;ext&gt; is added to the result file name only if `ext` is not
     * empty.
     *
     *
     * **NOTE:** _&lt;segmentSuffix&gt; is added to the result file name only if it's not the
     * empty string
     *
     *
     * **NOTE:** all custom files should be named using this method, or otherwise some
     * structures may fail to handle them properly (such as if they are added to compound files).
     */
    fun segmentFileName(segmentName: String, segmentSuffix: String, ext: String): String {
        if (ext.isNotEmpty() || segmentSuffix.isNotEmpty()) {
            require(!ext.startsWith("."))
            val sb =
                StringBuilder(segmentName.length + 2 + segmentSuffix.length + ext.length)
            sb.append(segmentName)
            if (segmentSuffix.isNotEmpty()) {
                sb.append('_').append(segmentSuffix)
            }
            if (ext.isNotEmpty()) {
                sb.append('.').append(ext)
            }
            return sb.toString()
        } else {
            return segmentName
        }
    }

    /**
     * Returns true if the given filename ends with the given extension. One should provide a
     * *pure* extension, without '.'.
     */
    fun matchesExtension(filename: String, ext: String): Boolean {
        // It doesn't make a difference whether we allocate a StringBuilder ourself
        // or not, since there's only 1 '+' operator.
        return filename.endsWith(".$ext")
    }

    /** locates the boundary of the segment name, or -1  */
    private fun indexOfSegmentName(filename: String): Int {
        // If it is a .del file, there's an '_' after the first character
        var idx = filename.indexOf('_', 1)
        if (idx == -1) {
            // If it's not, strip everything that's before the '.'
            idx = filename.indexOf('.')
        }
        return idx
    }

    /**
     * Strips the segment name out of the given file name. If you used [.segmentFileName] or
     * [.fileNameFromGeneration] to create your files, then this method simply removes whatever
     * comes before the first '.', or the second '_' (excluding both).
     *
     * @return the filename with the segment name removed, or the given filename if it does not
     * contain a '.' and '_'.
     */
    fun stripSegmentName(filename: String): String {
        var filename = filename
        val idx = indexOfSegmentName(filename)
        if (idx != -1) {
            filename = filename.substring(idx)
        }
        return filename
    }

    /** Returns the generation from this file name, or 0 if there is no generation.  */
    fun parseGeneration(filename: String): Long {
        require(filename.startsWith("_"))
        val parts: Array<String> =
            stripExtension(filename).substring(1).split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        // 4 cases:
        // segment.ext
        // segment_gen.ext
        // segment_codec_suffix.ext
        // segment_gen_codec_suffix.ext
        return if (parts.size == 2 || parts.size == 4) {
            parts[1].toLong(Character.MAX_RADIX)
        } else {
            0
        }
    }

    /**
     * Parses the segment name out of the given file name.
     *
     * @return the segment name only, or filename if it does not contain a '.' and '_'.
     */
    fun parseSegmentName(filename: String): String {
        var filename = filename
        val idx = indexOfSegmentName(filename)
        if (idx != -1) {
            filename = filename.substring(0, idx)
        }
        return filename
    }

    /**
     * Removes the extension (anything after the first '.'), otherwise returns the original filename.
     */
    fun stripExtension(filename: String): String {
        var filename = filename
        val idx = filename.indexOf('.')
        if (idx != -1) {
            filename = filename.substring(0, idx)
        }
        return filename
    }

    /**
     * Return the extension (anything after the first '.'), or null if there is no '.' in the file
     * name.
     */
    fun getExtension(filename: String): String? {
        val idx = filename.indexOf('.')
        return if (idx == -1) {
            null
        } else {
            filename.substring(idx + 1)
        }
    }


    /**
     * All files created by codecs must match this pattern (checked in SegmentInfo).
     */
    /* val CODEC_FILE_PATTERN: java.util.regex.Pattern = java.util.regex.Pattern.compile("_[a-z0-9]+(_.*)\\..*") */
    val CODEC_FILE_PATTERN: Regex = Regex("_[a-z0-9]+(_.*)?\\..*")

}
