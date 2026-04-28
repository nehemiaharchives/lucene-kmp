package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.FieldType
import org.gnit.lucenekmp.jdkport.AtomicInteger
import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.Thread
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.jdkport.withLock
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.search.TermQuery
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.tests.util.TestUtil
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail

class TestStressNRT : LuceneTestCase() {

    @Volatile
    lateinit var reader: DirectoryReader

    val model: MutableMap<Int, Long> = mutableMapOf()
    var committedModel: MutableMap<Int, Long> = mutableMapOf()
    var snapshotCount: Long = 0
    var committedModelClock: Long = 0

    @Volatile
    var lastId: Int = 0
    val field: String = "val_l"
    lateinit var syncArr: Array<ReentrantLock>
    private val stateLock = ReentrantLock()
    private val modelLock = ReentrantLock()

    private fun initModel(ndocs: Int) {
        snapshotCount = 0
        committedModelClock = 0
        lastId = 0

        model.clear()
        committedModel.clear()
        syncArr = Array(ndocs) { ReentrantLock() }

        for (i in 0..<ndocs) {
            model[i] = -1L
        }
        committedModel.putAll(model)
    }

    @Test
    @OptIn(ExperimentalAtomicApi::class)
    @Throws(Exception::class)
    fun test() {
        // update variables
        val commitPercent = random().nextInt(20)
        val softCommitPercent = random().nextInt(100) // what percent of the commits are soft
        val deletePercent = random().nextInt(50)
        val deleteByQueryPercent = random().nextInt(25)
        val ndocs = atLeast(50)
        val nWriteThreads = TestUtil.nextInt(random(), 1, if (TEST_NIGHTLY) 10 else 5)
        val maxConcurrentCommits =
            TestUtil.nextInt(
                random(),
                1,
                if (TEST_NIGHTLY)
                    10
                else
                    5
            ) // number of committers at a time... needed if we want to avoid commit errors
        // due to exceeding the max
        val useSoftDeletes = random().nextInt(10) < 3

        val tombstones = random().nextBoolean()

        // query variables
        val operations = AtomicLong(atLeast(10000).toLong()) // number of query operations to perform in total

        val nReadThreads = TestUtil.nextInt(random(), 1, if (TEST_NIGHTLY) 10 else 5)
        initModel(ndocs)

        val storedOnlyType = FieldType()
        storedOnlyType.setStored(true)

        if (VERBOSE) {
            println("\n")
            println("TEST: commitPercent=$commitPercent")
            println("TEST: softCommitPercent=$softCommitPercent")
            println("TEST: deletePercent=$deletePercent")
            println("TEST: deleteByQueryPercent=$deleteByQueryPercent")
            println("TEST: ndocs=$ndocs")
            println("TEST: nWriteThreads=$nWriteThreads")
            println("TEST: nReadThreads=$nReadThreads")
            println("TEST: maxConcurrentCommits=$maxConcurrentCommits")
            println("TEST: tombstones=$tombstones")
            println("TEST: operations=$operations")
            println("\n")
        }

        val numCommitting: AtomicInteger = AtomicInteger(0)

        val threads: MutableList<Thread> = mutableListOf()

        val dir: Directory = newDirectory() /*newMaybeVirusCheckingDirectory()*/

        val writer =
            RandomIndexWriter(
                random(), dir, newIndexWriterConfig(MockAnalyzer(random())), useSoftDeletes
            )
        writer.setDoRandomForceMergeAssert(false)
        writer.commit()
        if (useSoftDeletes) {
            reader = SoftDeletesDirectoryReaderWrapper(
                DirectoryReader.open(dir),
                requireNotNull(writer.w.config.softDeletesField)
            )
        } else {
            reader = DirectoryReader.open(dir)
        }

        for (i in 0..<nWriteThreads) {
            val thread: Thread =
                object : Thread() {
                    init {
                        setName("WRITER$i")
                    }

                    var rand: Random = Random(random().nextInt())

                    override fun run() {
                        try {
                            while (operations.load() > 0) {
                                val oper: Int = rand.nextInt(100)

                                if (oper < commitPercent) {
                                    if (numCommitting.incrementAndFetch() <= maxConcurrentCommits) {
                                        val (newCommittedModel, version, oldReader) = stateLock.withLock {
                                            val newCommittedModel = modelLock.withLock { HashMap(model) } // take a snapshot
                                            val version = snapshotCount++
                                            val oldReader = reader
                                            oldReader.incRef() // increment the reference since we will use this for
                                            Triple(newCommittedModel, version, oldReader)
                                        }

                                        var newReader: DirectoryReader?
                                        if (rand.nextInt(100) < softCommitPercent) {
                                            // assertU(h.commit("softCommit","true"));
                                            if (random().nextBoolean()) {
                                                if (VERBOSE) {
                                                    println(
                                                        ("TEST: "
                                                                + Thread.currentThread().getName()
                                                                + ": call writer.getReader")
                                                    )
                                                }
                                                newReader = writer.reader
                                            } else {
                                                if (VERBOSE) {
                                                    println(
                                                        ("TEST: "
                                                                + Thread.currentThread().getName()
                                                                + ": reopen reader="
                                                                + oldReader
                                                                + " version="
                                                                + version)
                                                    )
                                                }
                                                newReader = DirectoryReader.openIfChanged(oldReader, writer.w)
                                            }
                                        } else {
                                            // assertU(commit());
                                            if (VERBOSE) {
                                                println(
                                                    ("TEST: "
                                                            + Thread.currentThread().getName()
                                                            + ": commit+reopen reader="
                                                            + oldReader
                                                            + " version="
                                                            + version)
                                                )
                                            }
                                            writer.commit()
                                            if (VERBOSE) {
                                                println(
                                                    ("TEST: "
                                                            + Thread.currentThread().getName()
                                                            + ": now reopen after commit")
                                                )
                                            }
                                            newReader = DirectoryReader.openIfChanged(oldReader)
                                        }

                                        // Code below assumes newReader comes w/
                                        // extra ref:
                                        if (newReader == null) {
                                            oldReader.incRef()
                                            newReader = oldReader
                                        }

                                        oldReader.decRef()

                                        stateLock.withLock {
                                            // install the new reader if it's newest (and check the current version
                                            // since another reader may have already been installed)
                                            // System.out.println(Thread.currentThread().getName() + ": newVersion=" +
                                            // newReader.getVersion());
                                            assert(newReader.getRefCount() > 0)
                                            assert(reader.getRefCount() > 0)
                                            if (newReader.version > reader.version) {
                                                if (VERBOSE) {
                                                    println(
                                                        ("TEST: "
                                                                + Thread.currentThread().getName()
                                                                + ": install new reader="
                                                                + newReader)
                                                    )
                                                }
                                                reader.decRef()
                                                reader = newReader

                                                // Silly: forces fieldInfos to be
                                                // loaded so we don't hit IOE on later
                                                // reader.toString
                                                newReader.toString()

                                                // install this snapshot only if it's newer than the current one
                                                if (version >= committedModelClock) {
                                                    if (VERBOSE) {
                                                        println(
                                                            ("TEST: "
                                                                    + Thread.currentThread().getName()
                                                                    + ": install new model version="
                                                                    + version)
                                                        )
                                                    }
                                                    committedModel = newCommittedModel
                                                    committedModelClock = version
                                                } else {
                                                    if (VERBOSE) {
                                                        println(
                                                            ("TEST: "
                                                                    + Thread.currentThread().getName()
                                                                    + ": skip install new model version="
                                                                    + version)
                                                        )
                                                    }
                                                }
                                            } else {
                                                // if the same reader, don't decRef.
                                                if (VERBOSE) {
                                                    println(
                                                        ("TEST: "
                                                                + Thread.currentThread().getName()
                                                                + ": skip install new reader="
                                                                + newReader)
                                                    )
                                                }
                                                newReader.decRef()
                                            }
                                        }
                                    }
                                    numCommitting.decrementAndFetch()
                                } else {
                                    val id: Int = rand.nextInt(ndocs)
                                    val sync = syncArr[id]

                                    // set the lastId before we actually change it sometimes to try and
                                    // uncover more race conditions between writing and reading
                                    val before = random().nextBoolean()
                                    if (before) {
                                        lastId = id
                                    }

                                    // We can't concurrently update the same document and retain our invariants of
                                    // increasing values
                                    // since we can't guarantee what order the updates will be executed.
                                    sync.withLock {
                                        val `val`: Long = modelLock.withLock { model.getValue(id) }
                                        val nextVal = abs(`val`) + 1
                                        if (oper < commitPercent + deletePercent) {
                                            // assertU("<delete><id>" + id + "</id></delete>");

                                            // add tombstone first

                                            if (tombstones) {
                                                val d = Document()
                                                d.add(newStringField("id", "-$id", Field.Store.YES))
                                                d.add(newField(field, nextVal.toString(), storedOnlyType))
                                                writer.updateDocument(Term("id", "-$id"), d)
                                            }

                                            if (VERBOSE) {
                                                println(
                                                    ("TEST: "
                                                            + currentThread().getName()
                                                            + ": term delDocs id:"
                                                            + id
                                                            + " nextVal="
                                                            + nextVal)
                                                )
                                            }
                                            writer.deleteDocuments(Term("id", id.toString()))
                                            modelLock.withLock {
                                                model[id] = -nextVal
                                            }
                                        } else if (oper < commitPercent + deletePercent + deleteByQueryPercent) {
                                            // assertU("<delete><query>id:" + id + "</query></delete>");

                                            // add tombstone first

                                            if (tombstones) {
                                                val d = Document()
                                                d.add(newStringField("id", "-$id", Field.Store.YES))
                                                d.add(newField(field, nextVal.toString(), storedOnlyType))
                                                writer.updateDocument(Term("id", "-$id"), d)
                                            }

                                            if (VERBOSE) {
                                                println(
                                                    ("TEST: "
                                                            + Thread.currentThread().getName()
                                                            + ": query delDocs id:"
                                                            + id
                                                            + " nextVal="
                                                            + nextVal)
                                                )
                                            }
                                            writer.deleteDocuments(TermQuery(Term("id", id.toString())))
                                            modelLock.withLock {
                                                model[id] = -nextVal
                                            }
                                        } else {
                                            // assertU(adoc("id",Integer.toString(id), field, Long.toString(nextVal)));
                                            val d = Document()
                                            d.add(newStringField("id", id.toString(), Field.Store.YES))
                                            d.add(newField(field, nextVal.toString(), storedOnlyType))
                                            if (VERBOSE) {
                                                println(
                                                    ("TEST: "
                                                            + currentThread().getName()
                                                            + ": u id:"
                                                            + id
                                                            + " val="
                                                            + nextVal)
                                                )
                                            }
                                            writer.updateDocument(Term("id", id.toString()), d)
                                            if (tombstones) {
                                                // remove tombstone after new addition (this should be optional)
                                                writer.deleteDocuments(Term("id", "-$id"))
                                            }
                                            modelLock.withLock {
                                                model[id] = nextVal
                                            }
                                        }
                                    }

                                    if (!before) {
                                        lastId = id
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            println(
                                Thread.currentThread().getName() + ": FAILED: unexpected exception"
                            )
                            e.printStackTrace()
                            throw RuntimeException(e)
                        }
                    }
                }

            threads.add(thread)
        }

        for (i in 0..<nReadThreads) {
            val thread: Thread =
                object : Thread() {
                    init {
                        setName("READER$i")
                    }

                    var rand: Random = Random(random().nextInt())

                    override fun run() {
                        try {
                            var lastReader: IndexReader? = null
                            var lastSearcher: IndexSearcher? = null

                            while (operations.decrementAndFetch() >= 0) {
                                // bias toward a recently changed doc
                                val id = if (rand.nextInt(100) < 25) lastId else rand.nextInt(ndocs)

                                // when indexing, we update the index, then the model
                                // so when querying, we should first check the model, and then the index
                                val (`val`, r) = stateLock.withLock {
                                    val `val` = committedModel.getValue(id)
                                    val r = reader
                                    r.incRef()
                                    `val` to r
                                }

                                if (VERBOSE) {
                                    println(
                                        ("TEST: "
                                                + Thread.currentThread().getName()
                                                + ": s id="
                                                + id
                                                + " val="
                                                + `val`
                                                + " r="
                                                + r.version)
                                    )
                                }

                                //  sreq = req("wt","json", "q","id:"+Integer.toString(id), "omitHeader","true");
                                val searcher: IndexSearcher
                                if (r === lastReader) {
                                    // Just re-use lastSearcher, else
                                    // newSearcher may create too many thread
                                    // pools (ExecutorService):
                                    searcher = lastSearcher!!
                                } else {
                                    searcher = newSearcher(r)
                                    lastReader = r
                                    lastSearcher = searcher
                                }
                                var q: Query = TermQuery(Term("id", id.toString()))
                                var results = searcher.search(q, 10)

                                if (results.totalHits.value == 0L && tombstones) {
                                    // if we couldn't find the doc, look for its tombstone
                                    q = TermQuery(Term("id", "-$id"))
                                    results = searcher.search(q, 1)
                                    if (results.totalHits.value == 0L) {
                                        if (`val` == -1L) {
                                            // expected... no doc was added yet
                                            r.decRef()
                                            continue
                                        }
                                        fail(
                                            ("No documents or tombstones found for id "
                                                    + id
                                                    + ", expected at least "
                                                    + `val`
                                                    + " reader="
                                                    + r)
                                        )
                                    }
                                }

                                if (results.totalHits.value == 0L && !tombstones) {
                                    // nothing to do - we can't tell anything from a deleted doc without tombstones
                                } else {
                                    // we should have found the document, or its tombstone
                                    if (results.totalHits.value != 1L) {
                                        println("FAIL: hits id:$id val=$`val`")
                                        for (sd in results.scoreDocs) {
                                            val doc: Document = r.storedFields().document(sd.doc)
                                            println(
                                                ("  docID="
                                                        + sd.doc
                                                        + " id:"
                                                        + doc.get("id")
                                                        + " foundVal="
                                                        + doc.get(field))
                                            )
                                        }
                                        fail("id=" + id + " reader=" + r + " totalHits=" + results.totalHits.value)
                                    }
                                    val doc = searcher.storedFields().document(results.scoreDocs[0].doc)
                                    val foundVal = doc.get(field)!!.toLong()
                                    if (foundVal < abs(`val`)) {
                                        fail("foundVal=$foundVal val=$`val` id=$id reader=$r")
                                    }
                                }

                                r.decRef()
                            }
                        } catch (e: Throwable) {
                            operations.store(-1L)
                            println(
                                Thread.currentThread().getName() + ": FAILED: unexpected exception"
                            )
                            e.printStackTrace()
                            throw RuntimeException(e)
                        }
                    }
                }

            threads.add(thread)
        }

        for (thread in threads) {
            thread.start()
        }

        for (thread in threads) {
            thread.join()
        }

        writer.close()
        if (VERBOSE) {
            println("TEST: close reader=$reader")
        }
        reader.close()
        dir.close()
    }
}
