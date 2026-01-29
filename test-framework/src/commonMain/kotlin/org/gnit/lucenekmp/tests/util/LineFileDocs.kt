package org.gnit.lucenekmp.tests.util

import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
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
import org.gnit.lucenekmp.jdkport.StandardCharsets
import org.gnit.lucenekmp.jdkport.set
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

    /*@Synchronized*/
    @Throws(IOException::class)
    private fun open() {
        var `is`: InputStream? = /*javaClass.getResourceAsStream(path)*/ null

        // true if the InputStream is not already randomly seek'd after the if/else block below:
        var needSkip: Boolean = false

        var size = 0L
        var seekTo = 0L
        if (`is` == null) {
            // if it's not in classpath, we load it as absolute filesystem path (e.g. Jenkins' home dir)
            val file: Path = path.toPath()
            size = Files.size(file)
            if (path.endsWith(".gz")) {
                // if it is a gzip file, we need to use InputStream and seek to one of the pre-computed skip
                // points:
                `is` = Files.newInputStream(file)
                needSkip = true
            } else {
                // file is not compressed: optimized seek using SeekableByteChannel

                // TODO implement later if needed
                /*seekTo = randomSeekPos(random, size)
                val channel: java.nio.channels.SeekableByteChannel =
                    Files.newByteChannel(file)
                if (LuceneTestCase.VERBOSE) {
                    println("TEST: LineFileDocs: file seek to fp=$seekTo on open")
                }
                channel.position(seekTo)
                `is` = java.nio.channels.Channels.newInputStream(channel)

                // read until newline char, otherwise we may hit "java.nio.charset.MalformedInputException:
                // Input length = 1"
                // exception in readline() below, because we seeked part way through a multi-byte (in UTF-8)
                // encoded
                // unicode character:
                if (seekTo > 0L) {
                    var b: Int
                    do {
                        b = `is`.read()
                    } while (b >= 0 && b != 13 && b != 10)
                }

                needSkip = false*/
            }
        } else {
            // if the file comes from Classpath:
            size = `is`.available().toLong()
            needSkip = true
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

                // TODO implement later if needed
                /*`is`.skip(seekTo)
                `is` = java.util.zip.GZIPInputStream(`is`)
                if (LuceneTestCase.VERBOSE) {
                    println("TEST: LineFileDocs: stream skip to fp=" + seekTo + " on open")
                }*/
            }
        }

        val decoder: CharsetDecoder =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        reader = BufferedReader(InputStreamReader(`is`!!, decoder), BUFFER_SIZE)
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
