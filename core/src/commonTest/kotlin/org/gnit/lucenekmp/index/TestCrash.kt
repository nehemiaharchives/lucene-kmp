package org.gnit.lucenekmp.index

import kotlinx.coroutines.runBlocking
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.NoLockFactory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.store.MockDirectoryWrapper
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestCrash : LuceneTestCase() {

    private fun initIndex(random: Random, initialCommit: Boolean): IndexWriter {
        return initIndex(random, newMockDirectory(random, NoLockFactory.INSTANCE), initialCommit, true)
    }

    private fun initIndex(
        random: Random,
        dir: MockDirectoryWrapper,
        initialCommit: Boolean,
        commitOnClose: Boolean,
    ): IndexWriter {
        val writer =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random))
                    .setMaxBufferedDocs(10)
                    .setMergeScheduler(ConcurrentMergeScheduler())
                    .setCommitOnClose(commitOnClose),
            )
        (writer.config.mergeScheduler as ConcurrentMergeScheduler).setSuppressExceptions()
        if (initialCommit) {
            writer.commit()
        }

        val doc = Document()
        doc.add(newTextField("content", "aaa", Field.Store.NO))
        doc.add(newTextField("id", "0", Field.Store.NO))
        for (i in 0 until 157) {
            writer.addDocument(doc)
        }

        return writer
    }

    private fun crash(writer: IndexWriter) {
        val dir = writer.getDirectory() as MockDirectoryWrapper
        val cms =
            writer.config.mergeScheduler as ConcurrentMergeScheduler
        runBlocking { cms.sync() }
        dir.crash()
        runBlocking { cms.sync() }
        dir.clearCrash()
    }

    @Test
    fun testCrashWhileIndexing() {
        // This test relies on being able to open a reader before any commit
        // happened, so we must create an initial commit just to allow that, but
        // before any documents were added.
        val writer = initIndex(random(), true)
        val dir = writer.getDirectory() as MockDirectoryWrapper

        // We create leftover files because merging could be
        // running when we crash:
        dir.setAssertNoUnrefencedFilesOnClose(false)

        crash(writer)

        var reader: IndexReader = DirectoryReader.open(dir)
        assertTrue(reader.numDocs() < 157)
        reader.close()

        // Make a new dir, copying from the crashed dir, and
        // open IW on it, to confirm IW "recovers" after a
        // crash:
        val dir2: Directory = newDirectory(dir)
        dir.close()

        RandomIndexWriter(random(), dir2).close()
        dir2.close()
    }

    @Test
    fun testWriterAfterCrash() {
        // This test relies on being able to open a reader before any commit
        // happened, so we must create an initial commit just to allow that, but
        // before any documents were added.
        if (VERBOSE) {
            println("TEST: initIndex")
        }
        var writer = initIndex(random(), true)
        if (VERBOSE) {
            println("TEST: done initIndex")
        }
        val dir = writer.getDirectory() as MockDirectoryWrapper

        // We create leftover files because merging could be
        // running / store files could be open when we crash:
        dir.setAssertNoUnrefencedFilesOnClose(false)

        if (VERBOSE) {
            println("TEST: now crash")
        }
        crash(writer)
        writer = initIndex(random(), dir, false, true)
        writer.close()

        var reader: IndexReader = DirectoryReader.open(dir)
        assertTrue(reader.numDocs() < 314)
        reader.close()

        // Make a new dir, copying from the crashed dir, and
        // open IW on it, to confirm IW "recovers" after a
        // crash:
        val dir2: Directory = newDirectory(dir)
        dir.close()

        RandomIndexWriter(random(), dir2).close()
        dir2.close()
    }

    @Test
    fun testCrashAfterReopen() {
        var writer = initIndex(random(), false)
        val dir = writer.getDirectory() as MockDirectoryWrapper

        // We create leftover files because merging could be
        // running when we crash:
        dir.setAssertNoUnrefencedFilesOnClose(false)

        writer.close()
        writer = initIndex(random(), dir, false, true)
        assertEquals(314, writer.getDocStats().maxDoc)
        crash(writer)

        /*
        System.out.println("\n\nTEST: open reader");
        String[] l = dir.list();
        Arrays.sort(l);
        for(int i=0;i<l.length;i++)
          System.out.println("file " + i + " = " + l[i] + " " +
        dir.fileLength(l[i]) + " bytes");
        */

        var reader: IndexReader = DirectoryReader.open(dir)
        assertTrue(reader.numDocs() >= 157)
        reader.close()

        // Make a new dir, copying from the crashed dir, and
        // open IW on it, to confirm IW "recovers" after a
        // crash:
        val dir2: Directory = newDirectory(dir)
        dir.close()

        RandomIndexWriter(random(), dir2).close()
        dir2.close()
    }

    @Test
    fun testCrashAfterClose() {
        val writer = initIndex(random(), false)
        val dir = writer.getDirectory() as MockDirectoryWrapper

        writer.close()
        dir.crash()

        /*
        String[] l = dir.list();
        Arrays.sort(l);
        for(int i=0;i<l.length;i++)
          System.out.println("file " + i + " = " + l[i] + " " + dir.fileLength(l[i]) + " bytes");
        */

        val reader: IndexReader = DirectoryReader.open(dir)
        assertEquals(157, reader.numDocs())
        reader.close()
        dir.close()
    }

    @Test
    fun testCrashAfterCloseNoWait() {
        val random = random()
        val dir = newMockDirectory(random, NoLockFactory.INSTANCE)
        val writer = initIndex(random, dir, false, false)

        try {
            writer.commit()
        } finally {
            writer.close()
        }

        dir.crash()

        /*
        String[] l = dir.list();
        Arrays.sort(l);
        for(int i=0;i<l.length;i++)
          System.out.println("file " + i + " = " + l[i] + " " + dir.fileLength(l[i]) + " bytes");
        */
        val reader: IndexReader = DirectoryReader.open(dir)
        assertEquals(157, reader.numDocs())
        reader.close()
        dir.close()
    }
}
