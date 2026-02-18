package org.gnit.lucenekmp.document

import org.gnit.lucenekmp.index.IndexReader
import org.gnit.lucenekmp.jdkport.InetAddress
import org.gnit.lucenekmp.search.IndexSearcher
import org.gnit.lucenekmp.search.Query
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.index.RandomIndexWriter
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Simple tests for [InetAddressPoint] */
class TestInetAddressPoint : LuceneTestCase() {

    /** Add a single address and search for it */
    @Test
    fun testBasics() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        // add a doc with an address
        val document = Document()
        val address = InetAddress.getByName("1.2.3.4")
        document.add(InetAddressPoint("field", address))
        writer.addDocument(document)

        // search and verify we found our doc
        val reader: IndexReader = writer.reader
        val searcher: IndexSearcher = newSearcher(reader)
        assertEquals(1, searcher.count(InetAddressPoint.newExactQuery("field", address)))
        assertEquals(1, searcher.count(InetAddressPoint.newPrefixQuery("field", address, 24)))
        assertEquals(
            1,
            searcher.count(
                InetAddressPoint.newRangeQuery(
                    "field",
                    InetAddress.getByName("1.2.3.3"),
                    InetAddress.getByName("1.2.3.5")
                )
            )
        )
        assertEquals(1, searcher.count(InetAddressPoint.newSetQuery("field", InetAddress.getByName("1.2.3.4"))))
        assertEquals(
            1,
            searcher.count(
                InetAddressPoint.newSetQuery(
                    "field",
                    InetAddress.getByName("1.2.3.4"),
                    InetAddress.getByName("1.2.3.5")
                )
            )
        )
        assertEquals(0, searcher.count(InetAddressPoint.newSetQuery("field", InetAddress.getByName("1.2.3.3"))))
        assertEquals(0, searcher.count(InetAddressPoint.newSetQuery("field")))

        reader.close()
        writer.close()
        dir.close()
    }

    /** Add a single address and search for it */
    @Test
    fun testBasicsV6() {
        val dir: Directory = newDirectory()
        val writer = RandomIndexWriter(random(), dir)

        // add a doc with an address
        val document = Document()
        val address = InetAddress.getByName("fec0::f66d")
        document.add(InetAddressPoint("field", address))
        writer.addDocument(document)

        // search and verify we found our doc
        val reader: IndexReader = writer.reader
        val searcher: IndexSearcher = newSearcher(reader)
        assertEquals(1, searcher.count(InetAddressPoint.newExactQuery("field", address)))
        assertEquals(1, searcher.count(InetAddressPoint.newPrefixQuery("field", address, 64)))
        assertEquals(
            1,
            searcher.count(
                InetAddressPoint.newRangeQuery(
                    "field",
                    InetAddress.getByName("fec0::f66c"),
                    InetAddress.getByName("fec0::f66e")
                )
            )
        )

        reader.close()
        writer.close()
        dir.close()
    }

    @Test
    fun testToString() {
        assertEquals(
            "InetAddressPoint <field:1.2.3.4>",
            InetAddressPoint("field", InetAddress.getByName("1.2.3.4")).toString()
        )
        assertEquals(
            "InetAddressPoint <field:1.2.3.4>",
            InetAddressPoint("field", InetAddress.getByName("::FFFF:1.2.3.4")).toString()
        )
        assertEquals(
            "InetAddressPoint <field:[fdc8:57ed:f042:ad1:f66d:4ff:fe90:ce0c]>",
            InetAddressPoint("field", InetAddress.getByName("fdc8:57ed:f042:0ad1:f66d:4ff:fe90:ce0c")).toString()
        )

        assertEquals(
            "field:[1.2.3.4 TO 1.2.3.4]",
            InetAddressPoint.newExactQuery("field", InetAddress.getByName("1.2.3.4")).toString()
        )
        assertEquals(
            "field:[0:0:0:0:0:0:0:1 TO 0:0:0:0:0:0:0:1]",
            InetAddressPoint.newExactQuery("field", InetAddress.getByName("::1")).toString()
        )

        assertEquals(
            "field:[1.2.3.0 TO 1.2.3.255]",
            InetAddressPoint.newPrefixQuery("field", InetAddress.getByName("1.2.3.4"), 24).toString()
        )
        assertEquals(
            "field:[fdc8:57ed:f042:ad1:0:0:0:0 TO fdc8:57ed:f042:ad1:ffff:ffff:ffff:ffff]",
            InetAddressPoint.newPrefixQuery(
                "field",
                InetAddress.getByName("fdc8:57ed:f042:0ad1:f66d:4ff:fe90:ce0c"),
                64
            ).toString()
        )
        assertEquals(
            "field:{fdc8:57ed:f042:ad1:f66d:4ff:fe90:ce0c}",
            InetAddressPoint.newSetQuery(
                "field",
                InetAddress.getByName("fdc8:57ed:f042:0ad1:f66d:4ff:fe90:ce0c")
            ).toString()
        )
    }

    @Test
    fun testQueryEquals() {
        var q1: Query
        var q2: Query
        q1 = InetAddressPoint.newRangeQuery("a", InetAddress.getByName("1.2.3.3"), InetAddress.getByName("1.2.3.5"))
        q2 = InetAddressPoint.newRangeQuery("a", InetAddress.getByName("1.2.3.3"), InetAddress.getByName("1.2.3.5"))
        assertEquals(q1, q2)
        assertEquals(q1.hashCode(), q2.hashCode())
        assertFalse(q1.equals(InetAddressPoint.newRangeQuery("a", InetAddress.getByName("1.2.3.3"), InetAddress.getByName("1.2.3.7"))))
        assertFalse(q1.equals(InetAddressPoint.newRangeQuery("b", InetAddress.getByName("1.2.3.3"), InetAddress.getByName("1.2.3.5"))))

        q1 = InetAddressPoint.newPrefixQuery("a", InetAddress.getByName("1.2.3.3"), 16)
        q2 = InetAddressPoint.newPrefixQuery("a", InetAddress.getByName("1.2.3.3"), 16)
        assertEquals(q1, q2)
        assertEquals(q1.hashCode(), q2.hashCode())
        assertFalse(q1.equals(InetAddressPoint.newPrefixQuery("a", InetAddress.getByName("1.1.3.5"), 16)))
        assertFalse(q1.equals(InetAddressPoint.newPrefixQuery("a", InetAddress.getByName("1.2.3.5"), 24)))

        q1 = InetAddressPoint.newExactQuery("a", InetAddress.getByName("1.2.3.3"))
        q2 = InetAddressPoint.newExactQuery("a", InetAddress.getByName("1.2.3.3"))
        assertEquals(q1, q2)
        assertEquals(q1.hashCode(), q2.hashCode())
        assertFalse(q1.equals(InetAddressPoint.newExactQuery("a", InetAddress.getByName("1.2.3.5"))))

        q1 = InetAddressPoint.newSetQuery("a", InetAddress.getByName("1.2.3.3"), InetAddress.getByName("1.2.3.5"))
        q2 = InetAddressPoint.newSetQuery("a", InetAddress.getByName("1.2.3.3"), InetAddress.getByName("1.2.3.5"))
        assertEquals(q1, q2)
        assertEquals(q1.hashCode(), q2.hashCode())
        assertFalse(q1.equals(InetAddressPoint.newSetQuery("a", InetAddress.getByName("1.2.3.3"), InetAddress.getByName("1.2.3.7"))))
    }

    @Test
    fun testPrefixQuery() {
        assertEquals(
            InetAddressPoint.newRangeQuery("a", InetAddress.getByName("1.2.3.0"), InetAddress.getByName("1.2.3.255")),
            InetAddressPoint.newPrefixQuery("a", InetAddress.getByName("1.2.3.127"), 24)
        )
        assertEquals(
            InetAddressPoint.newRangeQuery("a", InetAddress.getByName("1.2.3.128"), InetAddress.getByName("1.2.3.255")),
            InetAddressPoint.newPrefixQuery("a", InetAddress.getByName("1.2.3.213"), 25)
        )
        assertEquals(
            InetAddressPoint.newRangeQuery(
                "a",
                InetAddress.getByName("2001::a000:0"),
                InetAddress.getByName("2001::afff:ffff")
            ),
            InetAddressPoint.newPrefixQuery("a", InetAddress.getByName("2001::a6bd:fc80"), 100)
        )
    }

    @Test
    fun testNextUp() {
        assertEquals(InetAddress.getByName("::1"), InetAddressPoint.nextUp(InetAddress.getByName("::")))

        assertEquals(InetAddress.getByName("::1:0"), InetAddressPoint.nextUp(InetAddress.getByName("::ffff")))

        assertEquals(InetAddress.getByName("1.2.4.0"), InetAddressPoint.nextUp(InetAddress.getByName("1.2.3.255")))

        assertEquals(InetAddress.getByName("0.0.0.0"), InetAddressPoint.nextUp(InetAddress.getByName("::fffe:ffff:ffff")))

        assertEquals(InetAddress.getByName("::1:0:0:0"), InetAddressPoint.nextUp(InetAddress.getByName("255.255.255.255")))

        val e = expectThrows(ArithmeticException::class) {
            InetAddressPoint.nextUp(InetAddress.getByName("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"))
        }
        assertEquals(
            "Overflow: there is no greater InetAddress than ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            e.message
        )
    }

    @Test
    fun testNextDown() {
        assertEquals(
            InetAddress.getByName("ffff:ffff:ffff:ffff:ffff:ffff:ffff:fffe"),
            InetAddressPoint.nextDown(InetAddress.getByName("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"))
        )

        assertEquals(InetAddress.getByName("::ffff"), InetAddressPoint.nextDown(InetAddress.getByName("::1:0")))

        assertEquals(InetAddress.getByName("1.2.3.255"), InetAddressPoint.nextDown(InetAddress.getByName("1.2.4.0")))

        assertEquals(InetAddress.getByName("::fffe:ffff:ffff"), InetAddressPoint.nextDown(InetAddress.getByName("0.0.0.0")))

        assertEquals(InetAddress.getByName("255.255.255.255"), InetAddressPoint.nextDown(InetAddress.getByName("::1:0:0:0")))

        val e = expectThrows(ArithmeticException::class) {
            InetAddressPoint.nextDown(InetAddress.getByName("::"))
        }
        assertEquals("Underflow: there is no smaller InetAddress than 0:0:0:0:0:0:0:0", e.message)
    }
}
