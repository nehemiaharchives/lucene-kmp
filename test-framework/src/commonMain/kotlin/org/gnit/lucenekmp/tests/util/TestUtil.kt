package org.gnit.lucenekmp.tests.util

//import org.gnit.lucenekmp.codecs.blocktreeords.BlockTreeOrdsPostingsFormat
//import org.gnit.lucenekmp.document.IntField
//import org.gnit.lucenekmp.document.KeywordField
//import org.gnit.lucenekmp.document.SortedDocValuesField
//import org.gnit.lucenekmp.index.CheckIndex
//import org.gnit.lucenekmp.index.ConcurrentMergeScheduler
//import org.gnit.lucenekmp.index.LogMergePolicy
//import org.gnit.lucenekmp.index.MergeScheduler
//import org.gnit.lucenekmp.index.SlowCodecReaderWrapper
//import org.gnit.lucenekmp.index.TieredMergePolicy
//import org.gnit.lucenekmp.store.ByteBuffersDirectory
//import org.gnit.lucenekmp.store.NoLockFactory
//import org.gnit.lucenekmp.tests.codecs.asserting.AssertingCodec
//import org.gnit.lucenekmp.tests.codecs.blockterms.LuceneFixedGap
//import org.gnit.lucenekmp.tests.mockfile.FilterFileSystem
//import org.gnit.lucenekmp.tests.mockfile.VirusCheckingFS
//import org.gnit.lucenekmp.tests.mockfile.WindowsFS
//import java.util.zip.ZipInputStream
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import okio.FileSystem
import okio.IOException
import okio.Path
import org.gnit.lucenekmp.codecs.Codec
import org.gnit.lucenekmp.codecs.DocValuesFormat
import org.gnit.lucenekmp.codecs.KnnVectorsFormat
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.codecs.lucene101.Lucene101Codec
import org.gnit.lucenekmp.codecs.lucene101.Lucene101PostingsFormat
import org.gnit.lucenekmp.codecs.lucene90.Lucene90DocValuesFormat
import org.gnit.lucenekmp.codecs.lucene99.Lucene99HnswVectorsFormat
import org.gnit.lucenekmp.codecs.perfield.PerFieldDocValuesFormat
import org.gnit.lucenekmp.codecs.perfield.PerFieldPostingsFormat
import org.gnit.lucenekmp.document.BinaryDocValuesField
import org.gnit.lucenekmp.document.BinaryPoint
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.index.CodecReader
import org.gnit.lucenekmp.index.DocValuesType
import org.gnit.lucenekmp.index.IndexFileNames
import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.LeafReader
import org.gnit.lucenekmp.index.MergePolicy
import org.gnit.lucenekmp.index.MultiTerms
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.Arrays
import org.gnit.lucenekmp.jdkport.BufferedInputStream
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.CharBuffer
import org.gnit.lucenekmp.jdkport.Character
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.jdkport.PrintStream
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.System
import org.gnit.lucenekmp.jdkport.appendCodePoint
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.codePointAt
import org.gnit.lucenekmp.jdkport.doubleToRawLongBits
import org.gnit.lucenekmp.jdkport.floatToRawIntBits
import org.gnit.lucenekmp.jdkport.fromCharArray
import org.gnit.lucenekmp.jdkport.isNaN
import org.gnit.lucenekmp.jdkport.valueOf
import org.gnit.lucenekmp.search.FieldDoc
import org.gnit.lucenekmp.search.ScoreDoc
import org.gnit.lucenekmp.search.TopDocs
import org.gnit.lucenekmp.search.TotalHits
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FSDirectory
import org.gnit.lucenekmp.store.FilterDirectory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.tests.junitport.assertArrayEquals
import org.gnit.lucenekmp.util.Attribute
import org.gnit.lucenekmp.util.AttributeImpl
import org.gnit.lucenekmp.util.AttributeReflector
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.CharsRef
import org.gnit.lucenekmp.util.UnicodeUtil
//import java.util.regex.PatternSyntaxException
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestUtil {
    companion object {

        /**
         * A comparator that compares UTF-16 strings / char sequences according to Unicode code point
         * order. This can be used to verify [BytesRef] order.
         *
         *
         * **Warning:** This comparator is rather inefficient, because it converts the strings to a
         * `int[]` array on each invocation.
         */
        val STRING_CODEPOINT_COMPARATOR: Comparator<CharSequence> =
            Comparator { a: CharSequence, b: CharSequence ->
                val aCodePoints: IntArray = /*a.codePoints().toArray()*/ a.map { it.code }.toIntArray()
                val bCodePoints: IntArray = /*b.codePoints().toArray()*/ b.map { it.code }.toIntArray()
                var i = 0
                val c = min(aCodePoints.size, bCodePoints.size)
                while (i < c) {
                    if (aCodePoints[i] < bCodePoints[i]) {
                        return@Comparator -1
                    } else if (aCodePoints[i] > bCodePoints[i]) {
                        return@Comparator 1
                    }
                    i++
                }
                aCodePoints.size - bCodePoints.size
            }

        /**
         * Convenience method unzipping zipName into destDir. You must pass it a clean destDir.
         *
         *
         * Closes the given InputStream after extracting!
         */
        /*@Throws(IOException::class)
        fun unzip(`in`: InputStream, destDir: Path) {
            var `in`: InputStream = `in`
            `in` = BufferedInputStream(`in`)

            ZipInputStream(`in`).use { zipInput ->
                var entry: ZipEntry
                val buffer = ByteArray(8192)
                while ((zipInput.getNextEntry().also { entry = it }) != null) {
                    val targetFile: Path = destDir.resolve(entry.getName())

                    // be on the safe side: do not rely on that directories are always extracted
                    // before their children (although this makes sense, but is it guaranteed)
                    Files.createDirectories(targetFile.getParent())
                    if (!entry.isDirectory()) {
                        val out: java.io.OutputStream = Files.newOutputStream(targetFile)
                        var len: Int
                        while ((zipInput.read(buffer).also { len = it }) >= 0) {
                            out.write(buffer, 0, len)
                        }
                        out.close()
                    }
                    zipInput.closeEntry()
                }
            }
        }*/

        /**
         * Checks that the provided iterator is well-formed.
         *
         *
         *  * is read-only: does not allow `remove`
         *  * returns `expectedSize` number of elements
         *  * does not return null elements, unless `allowNull` is true.
         *  * throws NoSuchElementException if `next` is called after `hasNext` returns
         * false.
         *
         */
        fun <T> checkIterator(iterator: MutableIterator<T>, expectedSize: Long, allowNull: Boolean) {
            for (i in 0..<expectedSize) {
                val hasNext = iterator.hasNext()
                assert(hasNext)
                val v = iterator.next()
                assert(allowNull || v != null)
                // for the first element, check that remove is not supported
                if (i == 0L) {
                    try {
                        iterator.remove()
                        throw AssertionError("broken iterator (supports remove): $iterator")
                    } catch (expected: UnsupportedOperationException) {
                        // ok
                    }
                }
            }
            assert(!iterator.hasNext())
            try {
                iterator.next()
                throw AssertionError("broken iterator (allows next() when hasNext==false) $iterator")
            } catch (expected: NoSuchElementException) {
                // ok
            }
        }

        /**
         * Checks that the provided iterator is well-formed.
         *
         *
         *  * is read-only: does not allow `remove`
         *  * does not return null elements.
         *  * throws NoSuchElementException if `next` is called after `hasNext` returns
         * false.
         *
         */
        fun <T> checkIterator(iterator: MutableIterator<T?>) {
            while (iterator.hasNext()) {
                val v: T = checkNotNull(iterator.next())
                try {
                    iterator.remove()
                    throw AssertionError("broken iterator (supports remove): $iterator")
                } catch (expected: UnsupportedOperationException) {
                    // ok
                }
            }
            try {
                iterator.next()
                throw AssertionError("broken iterator (allows next() when hasNext==false) $iterator")
            } catch (expected: NoSuchElementException) {
                // ok
            }
        }

        /**
         * Checks that the provided collection is read-only.
         *
         * @see .checkIterator
         */
        fun <T> checkReadOnly(coll: MutableCollection<T?>) {
            var size = 0
            for (@Suppress("unused") t in coll) {
                size += 1
            }
            if (size != coll.size) {
                throw AssertionError(
                    ("broken collection, reported size is "
                            + coll.size
                            + " but iterator has "
                            + size
                            + " elements: "
                            + coll)
                )
            }

            if (coll.isEmpty() == false) {
                try {
                    coll.remove(coll.iterator().next())
                    throw AssertionError("broken collection (supports remove): $coll")
                } catch (e: UnsupportedOperationException) {
                    // ok
                }
            }

            try {
                coll.add(null)
                throw AssertionError("broken collection (supports add): $coll")
            } catch (e: UnsupportedOperationException) {
                // ok
            }

            try {
                coll.addAll(mutableSetOf(null))
                throw AssertionError("broken collection (supports addAll): $coll")
            } catch (e: UnsupportedOperationException) {
                // ok
            }

            checkIterator<T>(coll.iterator())
        }

        /*fun syncConcurrentMerges(writer: IndexWriter) {
            syncConcurrentMerges(writer.config.mergeScheduler)
        }*/

        /*fun syncConcurrentMerges(ms: MergeScheduler) {
            if (ms is ConcurrentMergeScheduler) (ms as ConcurrentMergeScheduler).sync()
        }*/

        /**
         * This runs the CheckIndex tool on the index in. If any issues are hit, a RuntimeException is
         * thrown; else, true is returned.
         */
        /*@Throws(IOException::class)
        fun checkIndex(dir: Directory): CheckIndex.Status {
            return checkIndex(dir, CheckIndex.Level.MIN_LEVEL_FOR_SLOW_CHECKS)
        }*/

        /*@Throws(IOException::class)
        fun checkIndex(dir: Directory, level: Int): CheckIndex.Status {
            return checkIndex(dir, level, false, true, null)
        }*/

        /**
         * If failFast is true, then throw the first exception when index corruption is hit, instead of
         * moving on to other fields/segments to look for any other corruption.
         */
        /*@Throws(IOException::class)
        fun checkIndex(
            dir: Directory,
            level: Int,
            failFast: Boolean,
            concurrent: Boolean,
            output: ByteArrayOutputStream?
        ): CheckIndex.Status {
            var output: ByteArrayOutputStream? = output
            if (output == null) {
                output = ByteArrayOutputStream(1024)
            }
            CheckIndex(dir, NoLockFactory.INSTANCE.obtainLock(dir, "bogus")).use { checker ->
                checker.setLevel(level)
                checker.setFailFast(failFast)
                checker.setInfoStream(
                    PrintStream(output, false, StandardCharsets.UTF_8),
                    false
                )
                if (concurrent) {
                    checker.setThreadCount(RandomizedTest.randomIntBetween(2, 5))
                } else {
                    checker.setThreadCount(1)
                }
                val indexStatus: CheckIndex.Status = checker.checkIndex(null)
                if (indexStatus == null || indexStatus.clean == false) {
                    println("CheckIndex failed")
                    println(output.toString(StandardCharsets.UTF_8))
                    throw RuntimeException("CheckIndex failed")
                } else {
                    if (LuceneTestCase.INFOSTREAM) {
                        println(output.toString(StandardCharsets.UTF_8))
                    }
                    return indexStatus
                }
            }
        }*/

        /**
         * This runs the CheckIndex tool on the Reader. If any issues are hit, a RuntimeException is
         * thrown
         */
        /*@Throws(IOException::class)
        fun checkReader(reader: IndexReader) {
            for (context in reader.leaves()) {
                checkReader(context.reader(), CheckIndex.Level.MIN_LEVEL_FOR_SLOW_CHECKS)
            }
        }*/

        /*@Throws(IOException::class)
        fun checkReader(reader: LeafReader, level: Int) {
            val bos: ByteArrayOutputStream = ByteArrayOutputStream(1024)
            val infoStream: PrintStream =
                PrintStream(bos, false, StandardCharsets.UTF_8)

            val codecReader: CodecReader
            if (reader is CodecReader) {
                codecReader = reader as CodecReader
                reader.checkIntegrity()
            } else {
                codecReader = SlowCodecReaderWrapper.wrap(reader)
            }
            CheckIndex.testLiveDocs(codecReader, infoStream, true)
            CheckIndex.testFieldInfos(codecReader, infoStream, true)
            CheckIndex.testFieldNorms(codecReader, infoStream, true)
            CheckIndex.testPostings(codecReader, infoStream, false, level, true)
            CheckIndex.testStoredFields(codecReader, infoStream, true)
            CheckIndex.testTermVectors(codecReader, infoStream, false, level, true)
            CheckIndex.testDocValues(codecReader, infoStream, true)
            CheckIndex.testPoints(codecReader, infoStream, true)

            // some checks really against the reader API
            checkReaderSanity(reader)

            if (LuceneTestCase.INFOSTREAM) {
                println(bos.toString(StandardCharsets.UTF_8))
            }

            // FieldInfos should be cached at the reader and always return the same instance
            if (reader.getFieldInfos() !== reader.getFieldInfos()) {
                throw RuntimeException(
                    "getFieldInfos() returned different instances for class: " + reader.javaClass
                )
            }
        }*/

        // used by TestUtil.checkReader to check some things really unrelated to the index,
        // just looking for bugs in indexreader implementations.
        @Throws(IOException::class)
        private fun checkReaderSanity(reader: LeafReader) {
            for (info in reader.fieldInfos) {
                // reader shouldn't return normValues if the field does not have them

                if (!info.hasNorms()) {
                    if (reader.getNormValues(info.name) != null) {
                        throw RuntimeException("field: " + info.name + " should omit norms but has them!")
                    }
                }

                // reader shouldn't return docValues if the field does not have them
                // reader shouldn't return multiple docvalues types for the same field.
                when (info.docValuesType) {
                    DocValuesType.NONE -> if (reader.getBinaryDocValues(info.name) != null || reader.getNumericDocValues(
                            info.name
                        ) != null || reader.getSortedDocValues(info.name) != null || reader.getSortedSetDocValues(info.name) != null
                    ) {
                        throw RuntimeException(
                            "field: " + info.name + " has docvalues but should omit them!"
                        )
                    }

                    DocValuesType.SORTED -> if (reader.getBinaryDocValues(info.name) != null || reader.getNumericDocValues(
                            info.name
                        ) != null || reader.getSortedNumericDocValues(info.name) != null || reader.getSortedSetDocValues(
                            info.name
                        ) != null
                    ) {
                        throw RuntimeException(info.name + " returns multiple docvalues types!")
                    }

                    DocValuesType.SORTED_NUMERIC -> if (reader.getBinaryDocValues(info.name) != null || reader.getNumericDocValues(
                            info.name
                        ) != null || reader.getSortedSetDocValues(info.name) != null || reader.getSortedDocValues(info.name) != null
                    ) {
                        throw RuntimeException(info.name + " returns multiple docvalues types!")
                    }

                    DocValuesType.SORTED_SET -> if (reader.getBinaryDocValues(info.name) != null || reader.getNumericDocValues(
                            info.name
                        ) != null || reader.getSortedNumericDocValues(info.name) != null || reader.getSortedDocValues(
                            info.name
                        ) != null
                    ) {
                        throw RuntimeException(info.name + " returns multiple docvalues types!")
                    }

                    DocValuesType.BINARY -> if (reader.getNumericDocValues(info.name) != null || reader.getSortedDocValues(
                            info.name
                        ) != null || reader.getSortedNumericDocValues(info.name) != null || reader.getSortedSetDocValues(
                            info.name
                        ) != null
                    ) {
                        throw RuntimeException(info.name + " returns multiple docvalues types!")
                    }

                    DocValuesType.NUMERIC -> if (reader.getBinaryDocValues(info.name) != null || reader.getSortedDocValues(
                            info.name
                        ) != null || reader.getSortedNumericDocValues(info.name) != null || reader.getSortedSetDocValues(
                            info.name
                        ) != null
                    ) {
                        throw RuntimeException(info.name + " returns multiple docvalues types!")
                    }

                    else -> throw AssertionError()
                }
            }
        }

        /**
         * Returns true if the arguments are equal or within the range of allowed error (inclusive).
         * Returns `false` if either of the arguments is NaN.
         *
         *
         * Two float numbers are considered equal if there are `(maxUlps - 1)` (or fewer)
         * floating point numbers between them, i.e. two adjacent floating point numbers are considered
         * equal.
         *
         *
         * Adapted from org.apache.commons.numbers.core.Precision
         *
         *
         * github: https://github.com/apache/commons-numbers release 1.2
         *
         * @param x first value
         * @param y second value
         * @param maxUlps `(maxUlps - 1)` is the number of floating point values between `x`
         * and `y`.
         * @return `true` if there are fewer than `maxUlps` floating point values between
         * `x` and `y`.
         */
        fun floatUlpEquals(x: Float, y: Float, maxUlps: Short): Boolean {
            val xInt: Int = Float.floatToRawIntBits(x)
            val yInt: Int = Float.floatToRawIntBits(y)

            if ((xInt xor yInt) < 0) {
                // Numbers have opposite signs, take care of overflow.
                // Remove the sign bit to obtain the absolute ULP above zero.
                val deltaPlus = xInt and Int.Companion.MAX_VALUE
                val deltaMinus = yInt and Int.Companion.MAX_VALUE

                // Note:
                // If either value is NaN, the exponent bits are set to (255 << 23) and the
                // distance above 0.0 is always above a short ULP error. So omit the test
                // for NaN and return directly.

                // Avoid possible overflow from adding the deltas by splitting the comparison
                return deltaPlus <= maxUlps && deltaMinus <= (maxUlps - deltaPlus)
            }

            // Numbers have same sign, there is no risk of overflow.
            return abs(xInt - yInt) <= maxUlps && !Float.isNaN(x) && !Float.isNaN(y)
        }

        /**
         * Returns true if the arguments are equal or within the range of allowed error (inclusive).
         * Returns `false` if either of the arguments is NaN.
         *
         *
         * Two double numbers are considered equal if there are `(maxUlps - 1)` (or fewer)
         * floating point numbers between them, i.e. two adjacent floating point numbers are considered
         * equal.
         *
         *
         * Adapted from org.apache.commons.numbers.core.Precision
         *
         *
         * github: https://github.com/apache/commons-numbers release 1.2
         *
         * @param x first value
         * @param y second value
         * @param maxUlps `(maxUlps - 1)` is the number of floating point values between `x`
         * and `y`.
         * @return `true` if there are fewer than `maxUlps` floating point values between
         * `x` and `y`.
         */
        fun doubleUlpEquals(x: Double, y: Double, maxUlps: Int): Boolean {
            val xInt: Long = Double.doubleToRawLongBits(x)
            val yInt: Long = Double.doubleToRawLongBits(y)

            if ((xInt xor yInt) < 0) {
                // Numbers have opposite signs, take care of overflow.
                // Remove the sign bit to obtain the absolute ULP above zero.
                val deltaPlus = xInt and Long.Companion.MAX_VALUE
                val deltaMinus = yInt and Long.Companion.MAX_VALUE

                // Note:
                // If either value is NaN, the exponent bits are set to (2047L << 52) and the
                // distance above 0.0 is always above an integer ULP error. So omit the test
                // for NaN and return directly.

                // Avoid possible overflow from adding the deltas by splitting the comparison
                return deltaPlus <= maxUlps && deltaMinus <= (maxUlps - deltaPlus)
            }

            // Numbers have same sign, there is no risk of overflow.
            return abs(xInt - yInt) <= maxUlps && !Double.isNaN(x) && !Double.isNaN(y)
        }


        /** start and end are BOTH inclusive  */
        fun nextInt(r: Random, start: Int, end: Int): Int {
            require(end >= start)
            return if (start == end) start else r.nextInt(start, end + 1)
        }


        /** start and end are BOTH inclusive  */
        fun nextLong(r: Random, start: Long, end: Long): Long {
            assert(end >= start) { "start=$start,end=$end" }
            val range: BigInteger =
                BigInteger.valueOf(end).add(BigInteger.valueOf(1))
                    .subtract(BigInteger.valueOf(start))
            if (range.compareTo(BigInteger.valueOf(Int.Companion.MAX_VALUE.toLong())) <= 0) {
                return start + r.nextInt(range.intValue())
            } else {
                // probably not evenly distributed when range is large, but OK for tests
                val augend: BigInteger =
                    BigDecimal.fromBigInteger(range).multiply(BigDecimal.fromDouble(r.nextDouble())).toBigInteger()
                val result = BigInteger.valueOf(start).add(augend).longValue()
                assert(result >= start)
                assert(result <= end)
                return result
            }
        }

        /** Returns a randomish big integer with `1 .. maxBytes` storage.  */
        fun nextBigInteger(random: Random, maxBytes: Int): BigInteger {
            val length: Int = nextInt(random, 1, maxBytes)
            val buffer = ByteArray(length)
            random.nextBytes(buffer)
            return BigInteger.fromByteArray(buffer, Sign.POSITIVE)
        }

        fun randomSimpleString(r: Random, maxLength: Int): String {
            return randomSimpleString(r, 0, maxLength)
        }

        fun randomSimpleString(r: Random, minLength: Int, maxLength: Int): String {
            val end: Int = nextInt(r, minLength, maxLength)
            if (end == 0) {
                // allow 0 length
                return ""
            }
            val buffer = CharArray(end)
            for (i in 0..<end) {
                buffer[i] = nextInt(r, 'a'.code, 'z'.code).toChar()
            }
            return String.fromCharArray(buffer, 0, end)
        }

        fun randomSimpleStringRange(
            r: Random, minChar: Char, maxChar: Char, maxLength: Int
        ): String {
            val end: Int = nextInt(r, 0, maxLength)
            if (end == 0) {
                // allow 0 length
                return ""
            }
            val buffer = CharArray(end)
            for (i in 0..<end) {
                buffer[i] = nextInt(r, minChar.code, maxChar.code).toChar()
            }
            return String.fromCharArray(buffer, 0, end)
        }

        fun randomSimpleString(r: Random): String {
            return randomSimpleString(r, 0, 10)
        }

        /** Returns random string, including full unicode range.  */
        fun randomUnicodeString(r: Random): String {
            return randomUnicodeString(r, 20)
        }

        /** Returns a random string up to a certain length.  */
        fun randomUnicodeString(r: Random, maxLength: Int): String {
            val end = nextInt(r, 0, maxLength)
            if (end == 0) {
                // allow 0 length
                return ""
            }
            val buffer = CharArray(end)
            randomFixedLengthUnicodeString(r, buffer, 0, buffer.size)
            return buffer.concatToString(0, 0 + end)
        }

        /** Fills provided char[] with valid random unicode code unit sequence.  */
        fun randomFixedLengthUnicodeString(
            random: Random, chars: CharArray, offset: Int, length: Int
        ) {
            var i = offset
            val end = offset + length
            while (i < end) {
                val t: Int = random.nextInt(5)
                if (0 == t && i < length - 1) {
                    // Make a surrogate pair
                    // High surrogate
                    chars[i++] = nextInt(random, 0xd800, 0xdbff).toChar()
                    // Low surrogate
                    chars[i++] = nextInt(random, 0xdc00, 0xdfff).toChar()
                } else if (t <= 1) {
                    chars[i++] = random.nextInt(0x80).toChar()
                } else if (2 == t) {
                    chars[i++] = nextInt(random, 0x80, 0x7ff).toChar()
                } else if (3 == t) {
                    chars[i++] = nextInt(random, 0x800, 0xd7ff).toChar()
                } else if (4 == t) {
                    chars[i++] = nextInt(random, 0xe000, 0xffff).toChar()
                }
            }
        }


        /**
         * Returns a String that's "regexpish" (contains lots of operators typically found in regular
         * expressions) If you call this enough times, you might get a valid regex!
         */
        fun randomRegexpishString(r: Random): String {
            return randomRegexpishString(r, 20)
        }

        /**
         * Maximum recursion bound for '+' and '*' replacements in [.randomRegexpishString].
         */
        const val maxRecursionBound: Int = 5

        /** Operators for [.randomRegexpishString].  */
        val ops: MutableList<String> = mutableListOf(
            ".",
            "",
            "{0,$maxRecursionBound}",  // bounded replacement for '*'
            "{1,$maxRecursionBound}",  // bounded replacement for '+'
            "(",
            ")",
            "-",
            "[",
            "]",
            "|"
        )

        /**
         * Returns a String that's "regexpish" (contains lots of operators typically found in regular
         * expressions) If you call this enough times, you might get a valid regex!
         *
         *
         * Note: to avoid practically endless backtracking patterns we replace asterisk and plus
         * operators with bounded repetitions. See LUCENE-4111 for more info.
         *
         * @param maxLength A hint about maximum length of the regexpish string. It may be exceeded by a
         * few characters.
         */
        fun randomRegexpishString(r: Random, maxLength: Int): String {
            val regexp = StringBuilder(maxLength)
            for (i in nextInt(r, 0, maxLength) downTo 1) {
                if (r.nextBoolean()) {
                    regexp.append(RandomNumbers.randomIntBetween(r, 'a'.code, 'z'.code).toChar())
                } else {
                    regexp.append(RandomPicks.randomFrom(r, ops))
                }
            }
            return regexp.toString()
        }

        val HTML_CHAR_ENTITIES: Array<String> = arrayOf<String>(
            "AElig",
            "Aacute",
            "Acirc",
            "Agrave",
            "Alpha",
            "AMP",
            "Aring",
            "Atilde",
            "Auml",
            "Beta",
            "COPY",
            "Ccedil",
            "Chi",
            "Dagger",
            "Delta",
            "ETH",
            "Eacute",
            "Ecirc",
            "Egrave",
            "Epsilon",
            "Eta",
            "Euml",
            "Gamma",
            "GT",
            "Iacute",
            "Icirc",
            "Igrave",
            "Iota",
            "Iuml",
            "Kappa",
            "Lambda",
            "LT",
            "Mu",
            "Ntilde",
            "Nu",
            "OElig",
            "Oacute",
            "Ocirc",
            "Ograve",
            "Omega",
            "Omicron",
            "Oslash",
            "Otilde",
            "Ouml",
            "Phi",
            "Pi",
            "Prime",
            "Psi",
            "QUOT",
            "REG",
            "Rho",
            "Scaron",
            "Sigma",
            "THORN",
            "Tau",
            "Theta",
            "Uacute",
            "Ucirc",
            "Ugrave",
            "Upsilon",
            "Uuml",
            "Xi",
            "Yacute",
            "Yuml",
            "Zeta",
            "aacute",
            "acirc",
            "acute",
            "aelig",
            "agrave",
            "alefsym",
            "alpha",
            "amp",
            "and",
            "ang",
            "apos",
            "aring",
            "asymp",
            "atilde",
            "auml",
            "bdquo",
            "beta",
            "brvbar",
            "bull",
            "cap",
            "ccedil",
            "cedil",
            "cent",
            "chi",
            "circ",
            "clubs",
            "cong",
            "copy",
            "crarr",
            "cup",
            "curren",
            "dArr",
            "dagger",
            "darr",
            "deg",
            "delta",
            "diams",
            "divide",
            "eacute",
            "ecirc",
            "egrave",
            "empty",
            "emsp",
            "ensp",
            "epsilon",
            "equiv",
            "eta",
            "eth",
            "euml",
            "euro",
            "exist",
            "fnof",
            "forall",
            "frac12",
            "frac14",
            "frac34",
            "frasl",
            "gamma",
            "ge",
            "gt",
            "hArr",
            "harr",
            "hearts",
            "hellip",
            "iacute",
            "icirc",
            "iexcl",
            "igrave",
            "image",
            "infin",
            "int",
            "iota",
            "iquest",
            "isin",
            "iuml",
            "kappa",
            "lArr",
            "lambda",
            "lang",
            "laquo",
            "larr",
            "lceil",
            "ldquo",
            "le",
            "lfloor",
            "lowast",
            "loz",
            "lrm",
            "lsaquo",
            "lsquo",
            "lt",
            "macr",
            "mdash",
            "micro",
            "middot",
            "minus",
            "mu",
            "nabla",
            "nbsp",
            "ndash",
            "ne",
            "ni",
            "not",
            "notin",
            "nsub",
            "ntilde",
            "nu",
            "oacute",
            "ocirc",
            "oelig",
            "ograve",
            "oline",
            "omega",
            "omicron",
            "oplus",
            "or",
            "ordf",
            "ordm",
            "oslash",
            "otilde",
            "otimes",
            "ouml",
            "para",
            "part",
            "permil",
            "perp",
            "phi",
            "pi",
            "piv",
            "plusmn",
            "pound",
            "prime",
            "prod",
            "prop",
            "psi",
            "quot",
            "rArr",
            "radic",
            "rang",
            "raquo",
            "rarr",
            "rceil",
            "rdquo",
            "real",
            "reg",
            "rfloor",
            "rho",
            "rlm",
            "rsaquo",
            "rsquo",
            "sbquo",
            "scaron",
            "sdot",
            "sect",
            "shy",
            "sigma",
            "sigmaf",
            "sim",
            "spades",
            "sub",
            "sube",
            "sum",
            "sup",
            "sup1",
            "sup2",
            "sup3",
            "supe",
            "szlig",
            "tau",
            "there4",
            "theta",
            "thetasym",
            "thinsp",
            "thorn",
            "tilde",
            "times",
            "trade",
            "uArr",
            "uacute",
            "uarr",
            "ucirc",
            "ugrave",
            "uml",
            "upsih",
            "upsilon",
            "uuml",
            "weierp",
            "xi",
            "yacute",
            "yen",
            "yuml",
            "zeta",
            "zwj",
            "zwnj"
        )

        fun randomHtmlishString(random: Random, numElements: Int): String {
            val end: Int = nextInt(random, 0, numElements)
            if (end == 0) {
                // allow 0 length
                return ""
            }
            val sb: StringBuilder = StringBuilder()
            for (i in 0..<end) {
                val `val`: Int = random.nextInt(25)
                when (`val`) {
                    0 -> sb.append("<p>")
                    1 -> {
                        sb.append("<")
                        sb.append("    ".substring(nextInt(random, 0, 4)))
                        sb.append(randomSimpleString(random))
                        var j = 0
                        while (j < nextInt(random, 0, 10)) {
                            sb.append(' ')
                            sb.append(randomSimpleString(random))
                            sb.append(" ".substring(nextInt(random, 0, 1)))
                            sb.append('=')
                            sb.append(" ".substring(nextInt(random, 0, 1)))
                            sb.append("\"".substring(nextInt(random, 0, 1)))
                            sb.append(randomSimpleString(random))
                            sb.append("\"".substring(nextInt(random, 0, 1)))
                            ++j
                        }
                        sb.append("    ".substring(nextInt(random, 0, 4)))
                        sb.append("/".substring(nextInt(random, 0, 1)))
                        sb.append(">".substring(nextInt(random, 0, 1)))
                    }

                    2 -> {
                        sb.append("</")
                        sb.append("    ".substring(nextInt(random, 0, 4)))
                        sb.append(randomSimpleString(random))
                        sb.append("    ".substring(nextInt(random, 0, 4)))
                        sb.append(">".substring(nextInt(random, 0, 1)))
                    }

                    3 -> sb.append(">")
                    4 -> sb.append("</p>")
                    5 -> sb.append("<!--")
                    6 -> sb.append("<!--#")
                    7 -> sb.append("<script><!-- f('")
                    8 -> sb.append("</script>")
                    9 -> sb.append("<")
                    10 -> sb.append(">")
                    11 -> sb.append("\"")
                    12 -> sb.append("\\\"")
                    13 -> sb.append("'")
                    14 -> sb.append("\\'")
                    15 -> sb.append("-->")
                    16 -> {
                        sb.append("&")
                        when (nextInt(random, 0, 2)) {
                            0 -> sb.append(randomSimpleString(random))
                            1 -> sb.append(HTML_CHAR_ENTITIES[random.nextInt(HTML_CHAR_ENTITIES.size)])
                        }
                        sb.append(";".substring(nextInt(random, 0, 1)))
                    }

                    17 -> {
                        sb.append("&#")
                        if (0 == nextInt(random, 0, 1)) {
                            sb.append(
                                nextInt(
                                    random,
                                    0,
                                    Int.Companion.MAX_VALUE - 1
                                )
                            )
                            sb.append(";".substring(nextInt(random, 0, 1)))
                        }
                    }

                    18 -> {
                        sb.append("&#x")
                        if (0 == nextInt(random, 0, 1)) {
                            sb.append(
                                nextInt(
                                    random,
                                    0,
                                    Int.Companion.MAX_VALUE - 1
                                ).toString(16)
                            )
                            sb.append(";".substring(nextInt(random, 0, 1)))
                        }
                    }

                    19 -> sb.append(";")
                    20 -> sb.append(
                        nextInt(
                            random,
                            0,
                            Int.Companion.MAX_VALUE - 1
                        )
                    )

                    21 -> sb.append("\n")
                    22 -> sb.append(
                        "          ".substring(
                            nextInt(
                                random,
                                0,
                                10
                            )
                        )
                    )

                    23 -> {
                        sb.append("<")
                        if (0 == nextInt(random, 0, 3)) {
                            sb.append(
                                "          ".substring(
                                    nextInt(
                                        random,
                                        1,
                                        10
                                    )
                                )
                            )
                        }
                        if (0 == nextInt(random, 0, 1)) {
                            sb.append("/")
                            if (0 == nextInt(random, 0, 3)) {
                                sb.append(
                                    "          ".substring(
                                        nextInt(
                                            random,
                                            1,
                                            10
                                        )
                                    )
                                )
                            }
                        }
                        when (nextInt(random, 0, 3)) {
                            0 -> sb.append(randomlyRecaseCodePoints(random, "script"))
                            1 -> sb.append(randomlyRecaseCodePoints(random, "style"))
                            2 -> sb.append(randomlyRecaseCodePoints(random, "br"))
                        }
                        sb.append(">".substring(nextInt(random, 0, 1)))
                    }

                    else -> sb.append(randomSimpleString(random))
                }
            }
            return sb.toString()
        }

        /** Randomly upcases, downcases, or leaves intact each code point in the given string  */
        fun randomlyRecaseCodePoints(random: Random, str: String): String {
            val builder = StringBuilder()
            var pos = 0
            while (pos < str.length) {
                val codePoint: Int = str.codePointAt(pos)
                pos += Character.charCount(codePoint)
                when (nextInt(random, 0, 2)) {
                    0 -> builder.appendCodePoint(codePoint.toChar().uppercaseChar().code)
                    1 -> builder.appendCodePoint(codePoint.toChar().lowercaseChar().code)
                    2 -> builder.appendCodePoint(codePoint) // leave intact
                }
            }
            return builder.toString()
        }

        val blockStarts: IntArray = intArrayOf(
            0x0000, 0x0080, 0x0100, 0x0180, 0x0250, 0x02B0, 0x0300, 0x0370, 0x0400, 0x0500, 0x0530, 0x0590,
            0x0600, 0x0700, 0x0750, 0x0780, 0x07C0, 0x0800, 0x0900, 0x0980, 0x0A00, 0x0A80, 0x0B00, 0x0B80,
            0x0C00, 0x0C80, 0x0D00, 0x0D80, 0x0E00, 0x0E80, 0x0F00, 0x1000, 0x10A0, 0x1100, 0x1200, 0x1380,
            0x13A0, 0x1400, 0x1680, 0x16A0, 0x1700, 0x1720, 0x1740, 0x1760, 0x1780, 0x1800, 0x18B0, 0x1900,
            0x1950, 0x1980, 0x19E0, 0x1A00, 0x1A20, 0x1B00, 0x1B80, 0x1C00, 0x1C50, 0x1CD0, 0x1D00, 0x1D80,
            0x1DC0, 0x1E00, 0x1F00, 0x2000, 0x2070, 0x20A0, 0x20D0, 0x2100, 0x2150, 0x2190, 0x2200, 0x2300,
            0x2400, 0x2440, 0x2460, 0x2500, 0x2580, 0x25A0, 0x2600, 0x2700, 0x27C0, 0x27F0, 0x2800, 0x2900,
            0x2980, 0x2A00, 0x2B00, 0x2C00, 0x2C60, 0x2C80, 0x2D00, 0x2D30, 0x2D80, 0x2DE0, 0x2E00, 0x2E80,
            0x2F00, 0x2FF0, 0x3000, 0x3040, 0x30A0, 0x3100, 0x3130, 0x3190, 0x31A0, 0x31C0, 0x31F0, 0x3200,
            0x3300, 0x3400, 0x4DC0, 0x4E00, 0xA000, 0xA490, 0xA4D0, 0xA500, 0xA640, 0xA6A0, 0xA700, 0xA720,
            0xA800, 0xA830, 0xA840, 0xA880, 0xA8E0, 0xA900, 0xA930, 0xA960, 0xA980, 0xAA00, 0xAA60, 0xAA80,
            0xABC0, 0xAC00, 0xD7B0, 0xE000, 0xF900, 0xFB00, 0xFB50, 0xFE00, 0xFE10, 0xFE20, 0xFE30, 0xFE50,
            0xFE70, 0xFF00, 0xFFF0, 0x10000, 0x10080, 0x10100, 0x10140, 0x10190, 0x101D0, 0x10280, 0x102A0,
            0x10300, 0x10330, 0x10380, 0x103A0, 0x10400, 0x10450, 0x10480, 0x10800, 0x10840, 0x10900,
            0x10920, 0x10A00, 0x10A60, 0x10B00, 0x10B40, 0x10B60, 0x10C00, 0x10E60, 0x11080, 0x12000,
            0x12400, 0x13000, 0x1D000, 0x1D100, 0x1D200, 0x1D300, 0x1D360, 0x1D400, 0x1F000, 0x1F030,
            0x1F100, 0x1F200, 0x20000, 0x2A700, 0x2F800, 0xE0000, 0xE0100, 0xF0000, 0x100000
        )

        val blockEnds: IntArray = intArrayOf(
            0x007F, 0x00FF, 0x017F, 0x024F, 0x02AF, 0x02FF, 0x036F, 0x03FF, 0x04FF, 0x052F, 0x058F, 0x05FF,
            0x06FF, 0x074F, 0x077F, 0x07BF, 0x07FF, 0x083F, 0x097F, 0x09FF, 0x0A7F, 0x0AFF, 0x0B7F, 0x0BFF,
            0x0C7F, 0x0CFF, 0x0D7F, 0x0DFF, 0x0E7F, 0x0EFF, 0x0FFF, 0x109F, 0x10FF, 0x11FF, 0x137F, 0x139F,
            0x13FF, 0x167F, 0x169F, 0x16FF, 0x171F, 0x173F, 0x175F, 0x177F, 0x17FF, 0x18AF, 0x18FF, 0x194F,
            0x197F, 0x19DF, 0x19FF, 0x1A1F, 0x1AAF, 0x1B7F, 0x1BBF, 0x1C4F, 0x1C7F, 0x1CFF, 0x1D7F, 0x1DBF,
            0x1DFF, 0x1EFF, 0x1FFF, 0x206F, 0x209F, 0x20CF, 0x20FF, 0x214F, 0x218F, 0x21FF, 0x22FF, 0x23FF,
            0x243F, 0x245F, 0x24FF, 0x257F, 0x259F, 0x25FF, 0x26FF, 0x27BF, 0x27EF, 0x27FF, 0x28FF, 0x297F,
            0x29FF, 0x2AFF, 0x2BFF, 0x2C5F, 0x2C7F, 0x2CFF, 0x2D2F, 0x2D7F, 0x2DDF, 0x2DFF, 0x2E7F, 0x2EFF,
            0x2FDF, 0x2FFF, 0x303F, 0x309F, 0x30FF, 0x312F, 0x318F, 0x319F, 0x31BF, 0x31EF, 0x31FF, 0x32FF,
            0x33FF, 0x4DBF, 0x4DFF, 0x9FFF, 0xA48F, 0xA4CF, 0xA4FF, 0xA63F, 0xA69F, 0xA6FF, 0xA71F, 0xA7FF,
            0xA82F, 0xA83F, 0xA87F, 0xA8DF, 0xA8FF, 0xA92F, 0xA95F, 0xA97F, 0xA9DF, 0xAA5F, 0xAA7F, 0xAADF,
            0xABFF, 0xD7AF, 0xD7FF, 0xF8FF, 0xFAFF, 0xFB4F, 0xFDFF, 0xFE0F, 0xFE1F, 0xFE2F, 0xFE4F, 0xFE6F,
            0xFEFF, 0xFFEF, 0xFFFF, 0x1007F, 0x100FF, 0x1013F, 0x1018F, 0x101CF, 0x101FF, 0x1029F, 0x102DF,
            0x1032F, 0x1034F, 0x1039F, 0x103DF, 0x1044F, 0x1047F, 0x104AF, 0x1083F, 0x1085F, 0x1091F,
            0x1093F, 0x10A5F, 0x10A7F, 0x10B3F, 0x10B5F, 0x10B7F, 0x10C4F, 0x10E7F, 0x110CF, 0x123FF,
            0x1247F, 0x1342F, 0x1D0FF, 0x1D1FF, 0x1D24F, 0x1D35F, 0x1D37F, 0x1D7FF, 0x1F02F, 0x1F09F,
            0x1F1FF, 0x1F2FF, 0x2A6DF, 0x2B73F, 0x2FA1F, 0xE007F, 0xE01EF, 0xFFFFF, 0x10FFFF
        )

        /**
         * Returns random string of length between 0-20 codepoints, all codepoints within the same unicode
         * block.
         */
        fun randomRealisticUnicodeString(r: Random): String {
            return randomRealisticUnicodeString(r, 20)
        }

        /**
         * Returns random string of length up to maxLength codepoints , all codepoints within the same
         * unicode block.
         */
        fun randomRealisticUnicodeString(r: Random, maxLength: Int): String {
            return randomRealisticUnicodeString(r, 0, maxLength)
        }

        /**
         * Returns random string of length between min and max codepoints, all codepoints within the same
         * unicode block.
         */
        fun randomRealisticUnicodeString(r: Random, minLength: Int, maxLength: Int): String {
            val end: Int = nextInt(r, minLength, maxLength)
            val block: Int = r.nextInt(blockStarts.size)
            val sb = StringBuilder()
            for (i in 0..<end) sb.appendCodePoint(
                nextInt(
                    r,
                    blockStarts[block],
                    blockEnds[block]
                )
            )
            return sb.toString()
        }

        /** Returns random string, with a given UTF-8 byte length  */
        fun randomFixedByteLengthUnicodeString(r: Random, length: Int): String {
            val buffer = CharArray(length * 3)
            var bytes = length
            var i = 0
            while (i < buffer.size && bytes != 0) {
                val t: Int
                if (bytes >= 4) {
                    t = r.nextInt(5)
                } else if (bytes == 3) {
                    t = r.nextInt(4)
                } else if (bytes == 2) {
                    t = r.nextInt(2)
                } else {
                    t = 0
                }
                if (t == 0) {
                    buffer[i] = r.nextInt(0x80).toChar()
                    bytes--
                } else if (1 == t) {
                    buffer[i] = nextInt(r, 0x80, 0x7ff).toChar()
                    bytes -= 2
                } else if (2 == t) {
                    buffer[i] = nextInt(r, 0x800, 0xd7ff).toChar()
                    bytes -= 3
                } else if (3 == t) {
                    buffer[i] = nextInt(r, 0xe000, 0xffff).toChar()
                    bytes -= 3
                } else if (4 == t) {
                    // Make a surrogate pair
                    // High surrogate
                    buffer[i++] = nextInt(r, 0xd800, 0xdbff).toChar()
                    // Low surrogate
                    buffer[i] = nextInt(r, 0xdc00, 0xdfff).toChar()
                    bytes -= 4
                }
                i++
            }
            return String.fromCharArray(buffer, 0, i)
        }

        /** Returns a random binary term.  */
        fun randomBinaryTerm(r: Random): BytesRef {
            return randomBinaryTerm(r, r.nextInt(15))
        }

        /** Returns a random binary with a given length  */
        fun randomBinaryTerm(r: Random, length: Int): BytesRef {
            val b = BytesRef(length)
            r.nextBytes(b.bytes)
            b.length = length
            return b
        }

        /**
         * Return a Codec that can read any of the default codecs and formats, but always writes in the
         * specified format.
         */
        /*fun alwaysPostingsFormat(format: PostingsFormat): Codec {
            // TODO: we really need for postings impls etc to announce themselves
            // (and maybe their params, too) to infostream on flush and merge.
            // otherwise in a real debugging situation we won't know whats going on!
            if (LuceneTestCase.VERBOSE) {
                println("forcing postings format to:" + format)
            }
            return object : AssertingCodec() {
                override fun getPostingsFormatForField(field: String): PostingsFormat {
                    return format
                }
            }
        }*/

        /**
         * Return a Codec that can read any of the default codecs and formats, but always writes in the
         * specified format.
         */
        /*fun alwaysDocValuesFormat(format: DocValuesFormat): Codec {
            // TODO: we really need for docvalues impls etc to announce themselves
            // (and maybe their params, too) to infostream on flush and merge.
            // otherwise in a real debugging situation we won't know whats going on!
            if (LuceneTestCase.VERBOSE) {
                println("TestUtil: forcing docvalues format to:" + format)
            }
            return object : AssertingCodec() {
                override fun getDocValuesFormatForField(field: String): DocValuesFormat {
                    return format
                }
            }
        }*/

        /**
         * Return a Codec that can read any of the default codecs and formats, but always writes in the
         * specified format.
         */
        /*fun alwaysKnnVectorsFormat(format: KnnVectorsFormat): Codec {
            // TODO: we really need for knn vectors impls etc to announce themselves
            // (and maybe their params, too) to infostream on flush and merge.
            // otherwise in a real debugging situation we won't know whats going on!
            if (LuceneTestCase.VERBOSE) {
                println("TestUtil: forcing knn vectors format to:" + format)
            }
            return object : AssertingCodec() {
                override fun getKnnVectorsFormatForField(field: String): KnnVectorsFormat {
                    return format
                }
            }
        }*/

        /**
         * Returns the actual default codec (e.g. LuceneMNCodec) for this version of Lucene. This may be
         * different from [Codec.getDefault] because that is randomized.
         */
        fun getDefaultCodec(): Codec {
            return Lucene101Codec()
        }

        /**
         * Returns the actual default postings format (e.g. LuceneMNPostingsFormat) for this version of
         * Lucene.
         */
        fun getDefaultPostingsFormat(): PostingsFormat {
            return Lucene101PostingsFormat()
        }

        /**
         * Returns the actual default postings format (e.g. LuceneMNPostingsFormat) for this version of
         * Lucene.
         *
         * @lucene.internal this may disappear at any time
         */
        fun getDefaultPostingsFormat(
            minItemsPerBlock: Int, maxItemsPerBlock: Int
        ): PostingsFormat {
            return Lucene101PostingsFormat(minItemsPerBlock, maxItemsPerBlock)
        }

        /** Returns a random postings format that supports term ordinals  */
        /*fun getPostingsFormatWithOrds(r: Random): PostingsFormat {
            when (r.nextInt(2)) {
                0 -> return LuceneFixedGap()
                1 -> return BlockTreeOrdsPostingsFormat()
                else -> throw AssertionError()
            }
        }*/

        /**
         * Returns the actual default docvalues format (e.g. LuceneMNDocValuesFormat) for this version of
         * Lucene.
         */
        fun getDefaultDocValuesFormat(): DocValuesFormat {
            return Lucene90DocValuesFormat()
        }

        // TODO: generalize all 'test-checks-for-crazy-codecs' to
        // annotations (LUCENE-3489)
        fun getPostingsFormat(field: String): String {
            return getPostingsFormat(Codec.default, field)
        }

        fun getPostingsFormat(codec: Codec, field: String): String {
            val p: PostingsFormat = codec.postingsFormat()
            if (p is PerFieldPostingsFormat) {
                return p.getPostingsFormatForField(field)
                    .name
            } else {
                return p.name
            }
        }

        fun getDocValuesFormat(field: String): String {
            return getDocValuesFormat(Codec.default, field)
        }

        fun getDocValuesFormat(codec: Codec, field: String): String {
            val f: DocValuesFormat = codec.docValuesFormat()
            if (f is PerFieldDocValuesFormat) {
                return f.getDocValuesFormatForField(field)
                    .name
            } else {
                return f.name
            }
        }

        // TODO: remove this, push this test to Lucene40/Lucene42 codec tests
        fun fieldSupportsHugeBinaryDocValues(field: String): Boolean {
            val dvFormat = getDocValuesFormat(field)
            if (dvFormat == "Lucene40" || dvFormat == "Lucene42") {
                return false
            }
            return true
        }

        /**
         * Returns the actual default vector format (e.g. LuceneMNKnnVectorsFormat) for this version of
         * Lucene.
         */
        fun getDefaultKnnVectorsFormat(): KnnVectorsFormat {
            return Lucene99HnswVectorsFormat()
        }

        @Throws(IOException::class)
        fun anyFilesExceptWriteLock(dir: Directory): Boolean {
            val files: Array<String> = dir.listAll()
            if (files.size > 1 || (files.size == 1 && files[0] != "write.lock")) {
                return true
            } else {
                return false
            }
        }

        /*@Throws(IOException::class)
        fun addIndexesSlowly(
            writer: IndexWriter,
            vararg readers: DirectoryReader
        ) {
            val leaves: MutableList<CodecReader> =
                ArrayList<CodecReader>()
            for (reader in readers) {
                for (context in reader.leaves()) {
                    leaves.add(SlowCodecReaderWrapper.wrap(context.reader()))
                }
            }
            writer.addIndexes(*leaves.toTypedArray<CodecReader>())
        }*/

        /** just tries to configure things to keep the open file count lowish  */
        /*fun reduceOpenFiles(w: IndexWriter) {
            // keep number of open files lowish
            val mp: MergePolicy = w.getConfig().getMergePolicy()
            mp.noCFSRatio = 1.0
            if (mp is LogMergePolicy) {
                mp.setMergeFactor(min(5, mp.getMergeFactor()))
            } else if (mp is TieredMergePolicy) {
                mp.setSegmentsPerTier(min(5.0, mp.getSegmentsPerTier()))
            }
            val ms: MergeScheduler = w.getConfig().getMergeScheduler()
            if (ms is ConcurrentMergeScheduler) {
                // wtf... shouldn't it be even lower since it's 1 by default!!
                (ms as ConcurrentMergeScheduler).setMaxMergesAndThreads(3, 2)
            }
        }*/

        /**
         * Checks some basic behaviour of an AttributeImpl
         *
         * @param reflectedValues contains a map with "AttributeClass#key" as values
         */
        fun <T> assertAttributeReflection(
            att: AttributeImpl, reflectedValues: MutableMap<String, T>
        ) {
            val map: MutableMap<String, T> = HashMap()
            att.reflectWith { attClass: KClass<out Attribute>, key: String, value: Any ->
                map.put(
                    attClass.simpleName + '#' + key,
                    value as T
                )
            }
            assertEquals(reflectedValues, map, "Reflection does not produce same map")
        }

        /** Assert that the given [TopDocs] have the same top docs and consistent hit counts.  */
        /*fun assertConsistent(expected: TopDocs, actual: TopDocs) {
            assertEquals(
                expected.totalHits.value == 0L, actual.totalHits.value == 0L
            , "wrong total hits")
            if (expected.totalHits.relation == TotalHits.Relation.EQUAL_TO) {
                if (actual.totalHits.relation == TotalHits.Relation.EQUAL_TO) {
                    assertEquals(
                        expected.totalHits.value, actual.totalHits.value, "wrong total hits")
                } else {
                    assertTrue(
                        expected.totalHits.value >= actual.totalHits.value, "wrong total hits"
                    )
                }
            } else if (actual.totalHits.relation == TotalHits.Relation.EQUAL_TO) {
                assertTrue(expected.totalHits.value <= actual.totalHits.value, "wrong total hits")
            }
            assertEquals(
                expected.scoreDocs!!.size.toLong(),
                actual.scoreDocs!!.size.toLong(),
                "wrong hit count"
            )
            for (hitIDX in expected.scoreDocs!!.indices) {
                val expectedSD: ScoreDoc = expected.scoreDocs!![hitIDX]
                val actualSD: ScoreDoc = actual.scoreDocs!![hitIDX]
                assertEquals(expectedSD.doc.toLong(), actualSD.doc.toLong(), "wrong hit docID")
                assertEquals(
                    expectedSD.score.toDouble(),
                    actualSD.score.toDouble(),
                    0.0,
                    "wrong hit score"
                )
                if (expectedSD is FieldDoc) {
                    assertTrue(actualSD is FieldDoc)
                    assertArrayEquals(
                        (expectedSD as FieldDoc).fields,
                        (actualSD as FieldDoc).fields,
                        "wrong sort field values"
                    )
                } else {
                    assertFalse(actualSD is FieldDoc)
                }
            }
        }*/


        // NOTE: this is likely buggy, and cannot clone fields
        // with tokenStreamValues, etc.  Use at your own risk!!
        // TODO: is there a pre-existing way to do this!!!
        /*fun cloneDocument(doc1: Document): Document {
            val doc2 = Document()
            for (f in doc1.getFields()) {
                val field1: Field = f as Field
                val field2: Field
                val dvType: DocValuesType = field1.fieldType().docValuesType()
                val dimCount: Int = field1.fieldType().pointDimensionCount()
                if (f is KeywordField) {
                    field2 =
                        KeywordField(
                            f.name(),
                            f.stringValue(),
                            if (f.fieldType()
                                    .stored()
                            ) Field.Store.YES else Field.Store.NO
                        )
                } else if (f is IntField) {
                    field2 =
                        IntField(
                            f.name(),
                            f.numericValue().toInt(),
                            if (f.fieldType()
                                    .stored()
                            ) Field.Store.YES else Field.Store.NO
                        )
                } else if (dvType != DocValuesType.NONE) {
                    when (dvType) {
                        DocValuesType.NUMERIC -> field2 =
                            NumericDocValuesField(
                                field1.name(),
                                field1.numericValue().toLong()
                            )

                        DocValuesType.BINARY -> field2 =
                            BinaryDocValuesField(field1.name(), field1.binaryValue())

                        DocValuesType.SORTED -> field2 =
                            SortedDocValuesField(field1.name(), field1.binaryValue())

                        DocValuesType.NONE, DocValuesType.SORTED_SET, DocValuesType.SORTED_NUMERIC -> throw IllegalStateException(
                            "unknown Type: $dvType"
                        )

                        else -> throw IllegalStateException("unknown Type: $dvType")
                    }
                } else if (dimCount != 0) {
                    val br: BytesRef = field1.binaryValue()!!
                    val bytes = ByteArray(br.length)
                    System.arraycopy(br.bytes, br.offset, bytes, 0, br.length)
                    field2 = BinaryPoint(field1.name(), bytes, field1.fieldType())
                } else {
                    field2 = Field(field1.name(), field1.stringValue()!!, field1.fieldType())
                }
                doc2.add(field2)
            }

            return doc2
        }*/

        // Returns a DocsEnum, but randomly sometimes uses a
        // DocsAndFreqsEnum, DocsAndPositionsEnum.  Returns null
        // if field/term doesn't exist:
        @Throws(IOException::class)
        fun docs(
            random: Random,
            r: IndexReader,
            field: String,
            term: BytesRef,
            reuse: PostingsEnum,
            flags: Int
        ): PostingsEnum? {
            val terms: Terms? = MultiTerms.getTerms(r, field)
            if (terms == null) {
                return null
            }
            val termsEnum: TermsEnum = terms.iterator()
            if (!termsEnum.seekExact(term)) {
                return null
            }
            return docs(random, termsEnum, reuse, flags)
        }

        // Returns a PostingsEnum with random features available
        @Throws(IOException::class)
        fun docs(
            random: Random,
            termsEnum: TermsEnum,
            reuse: PostingsEnum,
            flags: Int
        ): PostingsEnum {
            // TODO: simplify this method it would be easier to randomly either use the flags passed, or do
            // the random selection,
            // FREQS should be part fo the random selection instead of outside on its own
            var flags = flags
            if (random.nextBoolean()) {
                if (random.nextBoolean()) {
                    val posFlags: Int
                    when (random.nextInt(4)) {
                        0 -> posFlags = PostingsEnum.POSITIONS.toInt()
                        1 -> posFlags = PostingsEnum.OFFSETS.toInt()
                        2 -> posFlags = PostingsEnum.PAYLOADS.toInt()
                        else -> posFlags = PostingsEnum.ALL.toInt()
                    }
                    return termsEnum.postings(null, posFlags)
                }
                flags = flags or PostingsEnum.FREQS.toInt()
            }
            return termsEnum.postings(reuse, flags)
        }

        fun stringToCharSequence(string: String, random: Random): CharSequence {
            return bytesToCharSequence(BytesRef(string), random)
        }

        fun bytesToCharSequence(ref: BytesRef, random: Random): CharSequence {
            when (random.nextInt(5)) {
                4 -> {
                    val chars = CharArray(ref.length)
                    val len: Int =
                        UnicodeUtil.UTF8toUTF16(ref.bytes, ref.offset, ref.length, chars)
                    return CharsRef(chars, 0, len)
                }

                3 -> return CharBuffer.wrap(ref.utf8ToString())
                else -> return ref.utf8ToString()
            }
        }

        /** Shutdown [ExecutorService] and wait for its.  */
        /*fun shutdownExecutorService(ex: java.util.concurrent.ExecutorService) {
            if (ex != null) {
                try {
                    ex.shutdown()
                    ex.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)
                } catch (e: java.lang.InterruptedException) {
                    // Just report it on the syserr.
                    java.lang.System.err.println("Could not properly close executor service.")
                    e.printStackTrace(java.lang.System.err)
                }
            }
        }*/

        /**
         * Returns a valid (compiling) Pattern instance with random stuff inside. Be careful when applying
         * random patterns to longer strings as certain types of patterns may explode into exponential
         * times in backtracking implementations (such as Java's).
         */
        /*fun randomPattern(random: Random): java.util.regex.Pattern {
            val nonBmpString = "AB\uD840\uDC00C"
            while (true) {
                try {
                    val p: java.util.regex.Pattern = java.util.regex.Pattern.compile(
                        randomRegexpishString(random)
                    )
                    var replacement: String? = null
                    // ignore bugs in Sun's regex impl
                    try {
                        replacement = p.matcher(nonBmpString).replaceAll("_")
                    } catch (jdkBug: StringIndexOutOfBoundsException) {
                        println("WARNING: your jdk is buggy!")
                        println(
                            ("Pattern.compile(\""
                                    + p.pattern()
                                    + "\").matcher(\"AB\\uD840\\uDC00C\").replaceAll(\"_\"); should not throw IndexOutOfBounds!")
                        )
                    }
                    // Make sure the result of applying the pattern to a string with extended
                    // unicode characters is a valid utf16 string. See LUCENE-4078 for discussion.
                    if (replacement != null && UnicodeUtil.validUTF16String(replacement)) {
                        return p
                    }
                } catch (ignored: PatternSyntaxException) {
                    // Loop trying until we hit something that compiles.
                }
            }
        }*/

        /*fun randomAnalysisString(random: Random, maxLength: Int, simple: Boolean): String {
            var maxLength = maxLength
            assert(maxLength >= 0)

            // sometimes just a purely random string
            if (random.nextInt(31) == 0) {
                return randomSubString(random, random.nextInt(maxLength), simple)
            }

            // otherwise, try to make it more realistic with 'words' since most tests use MockTokenizer
            // first decide how big the string will really be: 0..n
            maxLength = random.nextInt(maxLength)
            val avgWordLength: Int = nextInt(random, 3, 8)
            val sb: StringBuilder = StringBuilder()
            while (sb.length < maxLength) {
                if (sb.length > 0) {
                    sb.append(' ')
                }
                var wordLength = -1
                while (wordLength < 0) {
                    wordLength = (random.nextGaussian() * 3 + avgWordLength).toInt()
                }
                wordLength = min(wordLength, maxLength - sb.length)
                sb.append(randomSubString(random, wordLength, simple))
            }
            return sb.toString()
        }*/

        fun randomSubString(random: Random, wordLength: Int, simple: Boolean): String {
            if (wordLength == 0) {
                return ""
            }

            val evilness: Int = nextInt(random, 0, 20)

            val sb = StringBuilder()
            while (sb.length < wordLength) {
                if (simple) {
                    sb.append(
                        if (random.nextBoolean())
                            randomSimpleString(random, wordLength)
                        else
                            randomHtmlishString(random, wordLength)
                    )
                } else {
                    if (evilness < 10) {
                        sb.append(randomSimpleString(random, wordLength))
                    } else if (evilness < 15) {
                        assert(
                            sb.isEmpty() // we should always get wordLength back!
                        )
                        sb.append(
                            randomRealisticUnicodeString(
                                random,
                                wordLength,
                                wordLength
                            )
                        )
                    } else if (evilness == 16) {
                        sb.append(randomHtmlishString(random, wordLength))
                    } else if (evilness == 17) {
                        // gives a lot of punctuation
                        sb.append(randomRegexpishString(random, wordLength))
                    } else {
                        sb.append(randomUnicodeString(random, wordLength))
                    }
                }
            }
            if (sb.length > wordLength) {
                sb.setLength(wordLength)
                if (Character.isHighSurrogate(sb.get(wordLength - 1))) {
                    sb.setLength(wordLength - 1)
                }
            }

            if (random.nextInt(17) == 0) {
                // mix up case
                val mixedUp: String =
                    randomlyRecaseCodePoints(random, sb.toString())
                assert(mixedUp.length == sb.length)
                return mixedUp
            } else {
                return sb.toString()
            }
        }

        /**
         * For debugging: tries to include br.utf8ToString(), but if that fails (because it's not valid
         * utf8, which is fine!), just use ordinary toString.
         */
        fun bytesRefToString(br: BytesRef): String {
            if (br == null) {
                return "(null)"
            } else {
                try {
                    return br.utf8ToString() + " " + br
                } catch (t: AssertionError) {
                    // If BytesRef isn't actually UTF8, or it's e.g. a
                    // prefix of UTF8 that ends mid-unicode-char, we
                    // fall back to hex:
                    return br.toString()
                } catch (t: IllegalArgumentException) {
                    return br.toString()
                }
            }
        }

        /** Returns a copy of the source directory, with file contents stored in RAM.  */
        /*@Throws(IOException::class)
        fun ramCopyOf(dir: Directory): Directory {
            val ram: Directory = ByteBuffersDirectory()
            for (file in dir.listAll()) {
                if (file.startsWith(IndexFileNames.SEGMENTS)
                    || IndexFileNames.CODEC_FILE_PATTERN.matcher(file).matches()
                ) {
                    ram.copyFrom(dir, file, file, IOContext.DEFAULT)
                }
            }
            return ram
        }*/

        /*fun hasWindowsFS(dir: Directory): Boolean {
            var dir: Directory = dir
            dir = FilterDirectory.unwrap(dir)
            if (dir is FSDirectory) {
                val path: Path = (dir as FSDirectory).getDirectory()
                var fs: FileSystem = path.getFileSystem()
                while (fs is FilterFileSystem) {
                    val ffs: FilterFileSystem = fs as FilterFileSystem
                    if (ffs.provider() is WindowsFS) {
                        return true
                    }
                    fs = ffs.getDelegate()
                }
            }

            return false
        }*/

        /*fun hasWindowsFS(path: Path): Boolean {
            var fs: FileSystem = path.getFileSystem()
            while (fs is FilterFileSystem) {
                val ffs: FilterFileSystem = fs as FilterFileSystem
                if (ffs.provider() is WindowsFS) {
                    return true
                }
                fs = ffs.getDelegate()
            }

            return false
        }*/

        /*fun hasVirusChecker(dir: Directory): Boolean {
            var dir: Directory = dir
            dir = FilterDirectory.unwrap(dir)
            if (dir is FSDirectory) {
                return hasVirusChecker((dir as FSDirectory).directory)
            } else {
                return false
            }
        }*/

        /*fun hasVirusChecker(path: Path): Boolean {
            var fs: FileSystem = path.getFileSystem()
            while (fs is FilterFileSystem) {
                val ffs: FilterFileSystem = fs as FilterFileSystem
                if (ffs.provider() is VirusCheckingFS) {
                    return true
                }
                fs = ffs.getDelegate()
            }

            return false
        }*/

        /** Returns true if VirusCheckingFS is in use and was in fact already enabled  */
        /*fun disableVirusChecker(`in`: Directory): Boolean {
            val dir: Directory = FilterDirectory.unwrap(`in`)
            if (dir is FSDirectory) {
                var fs: FileSystem =
                    (dir as FSDirectory).getDirectory().getFileSystem()
                while (fs is FilterFileSystem) {
                    val ffs: FilterFileSystem = fs as FilterFileSystem
                    if (ffs.provider() is VirusCheckingFS) {
                        val vfs: VirusCheckingFS = ffs.provider() as VirusCheckingFS
                        val isEnabled: Boolean = vfs.isEnabled()
                        vfs.disable()
                        return isEnabled
                    }
                    fs = ffs.getDelegate()
                }
            }

            return false
        }*/

        /*fun enableVirusChecker(`in`: Directory) {
            val dir: Directory = FilterDirectory.unwrap(`in`)
            if (dir is FSDirectory) {
                var fs: FileSystem =
                    (dir as FSDirectory).getDirectory().getFileSystem()
                while (fs is FilterFileSystem) {
                    val ffs: FilterFileSystem = fs as FilterFileSystem
                    if (ffs.provider() is VirusCheckingFS) {
                        val vfs: VirusCheckingFS = ffs.provider() as VirusCheckingFS
                        vfs.enable()
                        return
                    }
                    fs = ffs.getDelegate()
                }
            }
        }*/

        // TODO this is not included in original TestUtil.java, so need to move proper place
        /** Returns true rarely (about 1 in 20 times) */
        fun rarely(random: Random): Boolean {
            return random.nextInt(20) == 0
        }

        // TODO this is not included in original TestUtil.java, so need to move proper place
        /**
         * Returns a shared random instance for test code, similar to Lucene's random().
         * This is useful for test classes that want a consistent random source.
         */
        fun random(): Random {
            return Random.Default
        }

    }
}
