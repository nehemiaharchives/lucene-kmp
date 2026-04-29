package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.jdkport.BitSet
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.Bits
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test class to illustrate using IndexDeletionPolicy to provide multi-level rollback capability.
 * This test case creates an index of records 1 to 100, introducing a commit point every 10 records.
 *
 * <p>A "keep all" deletion policy is used to ensure we keep all commit points for testing purposes
 */
class TestTransactionRollback : LuceneTestCase() {

    private val FIELD_RECORD_ID: String = "record_id"
    private lateinit var dir: Directory

    // Rolls back index to a chosen ID
    @Throws(Exception::class)
    private fun rollBackLast(id: Int) {
        // System.out.println("Attempting to rollback to "+id);

        val ids = "-$id"
        var last: IndexCommit? = null
        val commits: MutableCollection<IndexCommit> = DirectoryReader.listCommits(dir)
        val iterator: MutableIterator<IndexCommit> = commits.iterator()
        while (iterator.hasNext()) {
            val commit: IndexCommit = iterator.next()
            val ud: MutableMap<String, String> = commit.userData
            if (ud.size > 0) {
                if (ud["index"]!!.endsWith(ids)) {
                    last = commit
                }
            }
        }

        if (last == null) {
            throw RuntimeException("Couldn't find commit point $id")
        }

        val w =
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setIndexDeletionPolicy(RollbackDeletionPolicy(id))
                    .setIndexCommit(last)
            )
        val data: MutableMap<String, String> = mutableMapOf()
        data["index"] = "Rolled back to 1-$id"
        w.setLiveCommitData(data.entries)
        w.close()
    }

    @Test
    @Throws(Exception::class)
    fun testRepeatedRollBacks() {
        var expectedLastRecordId = 100
        while (expectedLastRecordId > 10) {
            expectedLastRecordId -= 10
            rollBackLast(expectedLastRecordId)

            val expecteds = BitSet(100)
            expecteds.set(1, (expectedLastRecordId + 1), true)
            checkExpecteds(expecteds)
        }
    }

    @Throws(Exception::class)
    private fun checkExpecteds(expecteds: BitSet) {
        val r: IndexReader = DirectoryReader.open(dir)

        // Perhaps not the most efficient approach but meets our
        // needs here.
        val liveDocs: Bits? = MultiBits.getLiveDocs(r)
        val storedFields: StoredFields = r.storedFields()
        for (i in 0..<r.maxDoc()) {
            if (liveDocs == null || liveDocs.get(i)) {
                val sval: String? = storedFields.document(i).get(FIELD_RECORD_ID)
                if (sval != null) {
                    val `val` = sval.toInt()
                    assertTrue(expecteds[`val`], "Did not expect document #$`val`")
                    expecteds[`val`] = false
                }
            }
        }
        r.close()
        assertEquals(0, expecteds.cardinality(), "Should have 0 docs remaining ")
    }


    /*
    private void showAvailableCommitPoints() throws Exception {
      Collection commits = DirectoryReader.listCommits(dir);
      for (Iterator iterator = commits.iterator(); iterator.hasNext();) {
        IndexCommit comm = (IndexCommit) iterator.next();
        System.out.print("\t Available commit point:["+commuUserData+"] files=");
        Collection files = comm.getFileNames();
        for (Iterator iterator2 = files.iterator(); iterator2.hasNext();) {
          String filename = (String) iterator2.next();
          System.out.print(filename+", ");
        }
        System.out.println();
      }
    }
    */

    @BeforeTest
    @Throws(Exception::class)
    fun setUp() {
        dir = newDirectory()

        // Build index, of records 1 to 100, committing after each batch of 10
        val sdp: IndexDeletionPolicy = KeepAllDeletionPolicy()
        val w =
            IndexWriter(
                dir, newIndexWriterConfig(MockAnalyzer(random())).setIndexDeletionPolicy(sdp)
            )

        for (currentRecordId in 1..100) {
            val doc = Document()
            doc.add(newTextField(FIELD_RECORD_ID, "" + currentRecordId, Field.Store.YES))
            w.addDocument(doc)

            if (currentRecordId % 10 == 0) {
                val data: MutableMap<String, String> = mutableMapOf()
                data["index"] = "records 1-$currentRecordId"
                w.setLiveCommitData(data.entries)
                w.commit()
            }
        }

        w.close()
    }

    @AfterTest
    @Throws(Exception::class)
    fun tearDown() {
        dir.close()
    }

    // Rolls back to previous commit point
    class RollbackDeletionPolicy(private val rollbackPoint: Int) : IndexDeletionPolicy() {
        @Throws(IOException::class)
        override fun onCommit(commits: MutableList<out IndexCommit>) {
        }

        @Throws(IOException::class)
        override fun onInit(commits: MutableList<out IndexCommit>) {
            for (commit in commits) {
                val userData: MutableMap<String, String> = commit.userData
                if (userData.size > 0) {
                    // Label for a commit point is "Records 1-30"
                    // This code reads the last id ("30" in this example) and deletes it
                    // if it is after the desired rollback point
                    val x: String = userData["index"]!!
                    val lastVal = x.substring(x.lastIndexOf('-') + 1)
                    val last = lastVal.toInt()
                    if (last > rollbackPoint) {
                        /*
            System.out.print("\tRolling back commit point:" +
                             " UserData="+commituUserData +")  ("+(commits.size()-1)+" commit points left) files=");
            Collection files = commit.getFileNames();
            for (Iterator iterator2 = files.iterator(); iterator2.hasNext();) {
              System.out.print(" "+iterator2.next());
            }
            System.out.println();
            */

                        commit.delete()
                    }
                }
            }
        }
    }

    class DeleteLastCommitPolicy : IndexDeletionPolicy() {
        @Throws(IOException::class)
        override fun onCommit(commits: MutableList<out IndexCommit>) {
        }

        @Throws(IOException::class)
        override fun onInit(commits: MutableList<out IndexCommit>) {
            commits[commits.size - 1].delete()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testRollbackDeletionPolicy() {
        for (i in 0..1) {
            // Unless you specify a prior commit point, rollback
            // should not work:
            IndexWriter(
                dir,
                newIndexWriterConfig(MockAnalyzer(random()))
                    .setIndexDeletionPolicy(DeleteLastCommitPolicy())
            ).close()
            val r: IndexReader = DirectoryReader.open(dir)
            assertEquals(100, r.numDocs())
            r.close()
        }
    }

    // Keeps all commit points (used to build index)
    class KeepAllDeletionPolicy : IndexDeletionPolicy() {
        @Throws(IOException::class)
        override fun onCommit(commits: MutableList<out IndexCommit>) {
        }

        @Throws(IOException::class)
        override fun onInit(commits: MutableList<out IndexCommit>) {
        }
    }
}
