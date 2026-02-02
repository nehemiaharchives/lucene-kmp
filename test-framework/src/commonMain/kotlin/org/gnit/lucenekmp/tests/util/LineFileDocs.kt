package org.gnit.lucenekmp.tests.util

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.FileSystem
import okio.BufferedSource
import okio.Buffer
import okio.EOFException
import okio.GzipSource
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.document.IntField
import org.gnit.lucenekmp.document.IntPoint
import org.gnit.lucenekmp.document.KeywordField
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.document.TextField
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.BufferedReader
import org.gnit.lucenekmp.jdkport.CharsetDecoder
import org.gnit.lucenekmp.jdkport.CodingErrorAction
import org.gnit.lucenekmp.jdkport.Files
import org.gnit.lucenekmp.jdkport.InputStream
import org.gnit.lucenekmp.jdkport.InputStreamReader
import org.gnit.lucenekmp.jdkport.OkioSourceInputStream
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.set
import org.gnit.lucenekmp.jdkport.toRealPath
import org.gnit.lucenekmp.util.CloseableThreadLocal
import org.gnit.lucenekmp.util.IOUtils
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.random.Random

/**
 * Minimal port of benchmark's LneDocSource + DocMaker, so tests can enum docs from a line file
 * created by benchmark's WriteLineDoc task
 */
@OptIn(ExperimentalAtomicApi::class)
class LineFileDocs(
    random: Random,
    private val path: String = LuceneTestCase.TEST_LINE_DOCS_FILE
) : AutoCloseable {

    private val logger = KotlinLogging.logger{}

    private var reader: BufferedReader? = null
    private val id: AtomicInteger = AtomicInteger(0)
    private val random: Random

    /*@Synchronized*/
    override fun close() {
        IOUtils.close(reader, threadDocs)
        reader = null
    }

    private fun randomSeekPos(random: Random?, size: Long): Long {
        if (random == null || size <= 3L) {
            return 0L
        } else {
            return (random.nextLong() and Long.Companion.MAX_VALUE) % (size / 3)
        }
    }

    enum class Module { ANALYSIS, CODECS, CORE, QUERY_PARSER, TEST_FRAMEWORK }

    /*@Synchronized*/
    @Throws(IOException::class)
    private fun open() {
        var `is`: InputStream? = /*javaClass.getResourceAsStream(path)*/ null

        // true if the InputStream is not already randomly seek'd after the if/else block below:
        var needSkip: Boolean = false

        var size = 0L
        var seekTo = 0L
        val isGzip = path.endsWith(".gz")
        var file: Path? = null
        if (`is` == null) {
            // if it's not in classpath, we load it as absolute filesystem path (e.g. Jenkins' home dir)

            if(path == "europarl.lines.txt.gz"){
                val toReplace = "../test-framework/src/commonTest/resources/org/gnit/lucenekmp/tests/util/europarl.lines.txt.gz"
                val fs = FileSystem.SYSTEM
                val currentDir = "./".toPath()
                val currentLs = fs.list(currentDir).map { it.toString() }

                val moduleMd = currentLs.find { it.endsWith(".md") }
                logger.debug { "LineFileDocs.open() moduleMd: $moduleMd" }
                val module = Module.entries.find { "$it.md" == moduleMd }
                logger.debug { "LineFileDocs.open() module: $module" }

                val path = when(module){
                    Module.TEST_FRAMEWORK -> "src/commonTest/resources/org/gnit/lucenekmp/tests/util/europarl.lines.txt.gz"
                    else -> toReplace
                }

                logger.debug { "LineFileDocs.open() path: $path" }

                file = path.toPath()
            }else{
                file = path.toPath()
            }

            logger.debug { "LineFileDocs.open() file: $file" }

            size = Files.size(file)
            if (isGzip) {
                // For now, always start at the beginning of the gzip stream.
                // Okio's GzipSource does not allow arbitrary mid-stream starts.
                `is` = openInputStream(file, 0L, true)
                needSkip = false
            } else {
                // file is not compressed: just open from the start
                `is` = Files.newInputStream(file)
                needSkip = false
            }
        } else {
            // if the file comes from Classpath:
            size = `is`.available().toLong()
            needSkip = false
        }

        if (needSkip) {
            // LUCENE-9191: use the optimized (pre-computed, using
            // dev-tools/scripts/create_line_file_docs.py)
            // seek file, so we can seek in a gzip'd file

            val index = path.lastIndexOf('.')
            require(index != -1) { "could not determine extension for path \"$path\"" }

            // e.g. foo.txt --> foo.seek, foo.txt.gz --> foo.txt.seek
            val seekFilePath: String = path.take(index) + ".seek"
            var seekIS: InputStream? = /*javaClass.getResourceAsStream(seekFilePath)*/ null
            if (seekIS == null) {
                seekIS = Files.newInputStream(seekFilePath.toPath())
            }

            BufferedReader(
                InputStreamReader(
                    seekIS,
                    StandardCharsets.UTF_8
                )
            ).use { reader ->
                val skipPoints: MutableList<Long> = mutableListOf()
                // explicitly insert implicit 0 as the first skip point:
                skipPoints.add(0L)

                while (true) {
                    val line: String? = reader.readLine()
                    if (line == null) {
                        break
                    }
                    skipPoints.add(line.trim { it <= ' ' }.toLong())
                }

                seekTo = skipPoints.get(random.nextInt(skipPoints.size))

                // dev-tools/scripts/create_line_file_docs.py ensures this is a "safe" skip point, and we
                // can begin gunziping from here:
                if (`is` == null && file != null) {
                    `is` = openInputStream(file, seekTo, isGzip)
                }
                if (LuceneTestCase.VERBOSE) {
                    println("TEST: LineFileDocs: stream skip to fp=$seekTo on open")
                }
            }
        }

        if (`is` == null && file != null) {
            `is` = openInputStream(file, seekTo, isGzip)
        }

        val decoder: CharsetDecoder =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        reader = BufferedReader(InputStreamReader(`is`!!, decoder), BUFFER_SIZE)
    }

    private fun openInputStream(file: Path, seekTo: Long, gzip: Boolean): InputStream {
        val raw = FileSystem.SYSTEM.source(file).buffer()
        if (seekTo > 0L) {
            raw.skip(seekTo)
        }
        val source = if (gzip) GzipSource(raw).buffer() else raw
        return if (gzip) LenientGzipInputStream(source) else OkioSourceInputStream(source)
    }

    // Okio's GzipSource throws if the gzip member ends with trailing bytes.
    // Treat that condition as EOF to match java.util.zip.GZIPInputStream behavior.
    private class LenientGzipInputStream(private val source: BufferedSource) : InputStream() {
        private var finished = false

        override fun read(): Int {
            if (finished) return -1
            return try {
                source.readByte().toInt() and 0xFF
            } catch (e: EOFException) {
                finished = true
                -1
            } catch (e: IOException) {
                if (isTrailingGzipBytes(e)) {
                    finished = true
                    -1
                } else {
                    throw e
                }
            }
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (finished) return -1
            if (len == 0) return 0
            val bytesRead = try {
                source.read(b, off, len)
            } catch (e: EOFException) {
                finished = true
                -1
            } catch (e: IOException) {
                if (isTrailingGzipBytes(e)) {
                    finished = true
                    -1
                } else {
                    throw e
                }
            }

            if (bytesRead != -1) {
                return bytesRead
            }

            return if (source.exhausted()) {
                finished = true
                -1
            } else {
                0
            }
        }

        override fun available(): Int {
            return (source as? Buffer)?.size?.toInt() ?: 0
        }

        override fun close() {
            finished = true
            source.close()
        }

        private fun isTrailingGzipBytes(e: IOException): Boolean {
            return e.message?.contains("gzip finished without exhausting source") == true
        }
    }

    /*@Synchronized*/
    @Throws(IOException::class)
    fun reset() {
        reader!!.close()
        reader = null
        open()
        id.set(0)
    }

    private class DocState {
        val doc: Document
        val titleTokenized: Field
        val title: Field
        val body: Field
        val id: Field
        val idNum: Field
        val date: Field
        val pageViews: Field

        init {
            doc = Document()

            title = KeywordField("title", "", Field.Store.NO)
            doc.add(title)

            val ft: FieldType =
                FieldType(TextField.TYPE_STORED)
            ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS)
            ft.setStoreTermVectors(true)
            ft.setStoreTermVectorOffsets(true)
            ft.setStoreTermVectorPositions(true)

            titleTokenized = Field("titleTokenized", "", ft)
            doc.add(titleTokenized)

            body = Field("body", "", ft)
            doc.add(body)

            id = StringField(
                "docid",
                "",
                Field.Store.YES
            )
            doc.add(id)

            idNum = IntField(
                "docid_int",
                0,
                Field.Store.NO
            )
            doc.add(idNum)

            date = StringField(
                "date",
                "",
                Field.Store.YES
            )
            doc.add(date)

            // A numeric DV field that can be used for DV updates
            pageViews = NumericDocValuesField("page_views", 0L)
            doc.add(pageViews)
        }
    }

    private val threadDocs: CloseableThreadLocal<DocState> = CloseableThreadLocal<DocState>()

    /** If forever is true, we rewind the file at EOF (repeat the docs over and over)  */
    init {
        this.random = Random(random.nextLong())
        open()
    }

    /** Note: Document instance is re-used per-thread  */
    @Throws(IOException::class)
    fun nextDoc(): Document {
        var line: String?
        //synchronized(this) {
            line = reader!!.readLine()
            if (line == null) {
                // Always rewind at end:
                if (LuceneTestCase.VERBOSE) {
                    println("TEST: LineFileDocs: now rewind file...")
                }
                reader!!.close()
                reader = null
                open()
                line = reader!!.readLine()
            }
        //}

        var docState: DocState? = threadDocs.get()
        if (docState == null) {
            docState = DocState()
            threadDocs.set(docState)
        }

        val spot = line!!.indexOf(SEP)
        if (spot == -1) {
            throw RuntimeException("line: [$line] is in an invalid format !")
        }
        val spot2 = line.indexOf(SEP, 1 + spot)
        if (spot2 == -1) {
            throw RuntimeException("line: [$line] is in an invalid format !")
        }

        docState.body.setStringValue(line.substring(1 + spot2))
        val title = line.substring(0, spot)
        docState.title.setStringValue(title)
        docState.titleTokenized.setStringValue(title)
        docState.date.setStringValue(line.substring(1 + spot, spot2))
        val i: Int = id.fetchAndIncrement()
        docState.id.setStringValue(i.toString())
        docState.idNum.setIntValue(i)
        docState.pageViews.setLongValue(random.nextInt(10000).toLong())

        if (random.nextInt(5) == 4) {
            // Make some sparse fields
            val doc: Document = Document()
            for (field in docState.doc) {
                doc.add(field)
            }

            if (random.nextInt(3) == 1) {
                val x: Int = random.nextInt(4)
                doc.add(IntPoint("docLength$x", line.length))
            }

            if (random.nextInt(3) == 1) {
                val x: Int = random.nextInt(4)
                doc.add(IntPoint("docTitleLength$x", title.length))
            }

            if (random.nextInt(3) == 1) {
                val x: Int = random.nextInt(4)
                doc.add(
                    NumericDocValuesField(
                        "docLength$x",
                        line.length.toLong()
                    )
                )
            }

            // TODO: more random sparse fields here too
        }

        return docState.doc
    }

    companion object {
        /**
         * Converts date formats for europarl ("2023-02-23") and enwiki ("12-JAN-2010 12:32:45.000") into
         * [LocalDateTime].
         */
        // TODO implement if needed later
        /*val DATE_FIELD_VALUE_TO_LOCALDATETIME: java.util.function.Function<String, java.time.LocalDateTime> =
            object : java.util.function.Function<String, java.time.LocalDateTime>() {
                val euroParl: java.time.format.DateTimeFormatter =
                    java.time.format.DateTimeFormatterBuilder()
                        .parseStrict()
                        .parseCaseInsensitive()
                        .appendPattern("uuuu-MM-dd")
                        .toFormatter(java.util.Locale.ROOT)

                val enwiki: java.time.format.DateTimeFormatter =
                    java.time.format.DateTimeFormatterBuilder()
                        .parseStrict()
                        .parseCaseInsensitive()
                        .appendPattern("dd-MMM-uuuu HH:mm:ss['.'SSS]")
                        .toFormatter(java.util.Locale.ROOT)

                override fun apply(s: String): java.time.LocalDateTime {
                    if (s.matches("^[0-9]{4}-[0-9]{2}-[0-9]{2}$".toRegex())) {
                        return euroParl.parse<java.time.LocalDate>(
                            s,
                            java.time.temporal.TemporalQuery { temporal: java.time.temporal.TemporalAccessor ->
                                java.time.LocalDate.from(temporal)
                            }).atStartOfDay()
                    } else {
                        return enwiki.parse<java.time.LocalDateTime>(
                            s,
                            java.time.temporal.TemporalQuery { temporal: java.time.temporal.TemporalAccessor ->
                                java.time.LocalDateTime.from(temporal)
                            })
                    }
                }
            }*/

        private const val BUFFER_SIZE = 1 shl 16 // 64K
        private const val SEP = '\t'
    }
}
