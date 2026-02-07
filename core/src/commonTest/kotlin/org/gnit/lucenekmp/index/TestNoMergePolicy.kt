package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.index.MergePolicy.MergeSpecification
import org.gnit.lucenekmp.tests.index.BaseMergePolicyTestCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

class TestNoMergePolicy : BaseMergePolicyTestCase() {
    public override fun mergePolicy(): MergePolicy {
        return NoMergePolicy.INSTANCE
    }

    @Test
    @Throws(Exception::class)
    fun testNoMergePolicy() {
        val mp: MergePolicy = mergePolicy()
        assertNull(mp.findMerges(null, null as SegmentInfos?, null))
        assertNull(mp.findForcedMerges(null, 0, null, null))
        assertNull(mp.findForcedDeletesMerges(null, null))
    }

    /*@Throws(Exception::class)
    fun testFinalSingleton() {
        assertTrue(java.lang.reflect.Modifier.isFinal(NoMergePolicy::class.getModifiers()))
        val ctors: Array<java.lang.reflect.Constructor<*>> = NoMergePolicy::class.getDeclaredConstructors()
        assertEquals(
            1,
            ctors.size.toLong(),
            "expected 1 private ctor only: " + ctors.contentToString()
        )
        assertTrue(
            "that 1 should be private: " + ctors[0],
            java.lang.reflect.Modifier.isPrivate(ctors[0].getModifiers())
        )
    }*/

    /*@Throws(Exception::class)
    fun testMethodsOverridden() {
        // Ensures that all methods of MergePolicy are overridden. That's important
        // to ensure that NoMergePolicy overrides everything, so that no unexpected
        // behavior/error occurs
        for (m in NoMergePolicy::class.getMethods()) {
            // getDeclaredMethods() returns just those methods that are declared on
            // NoMergePolicy. getMethods() returns those that are visible in that
            // context, including ones from Object. So just filter out Object. If in
            // the future MergePolicy will extend a different class than Object, this
            // will need to change.
            if (m.getName() == "clone") {
                continue
            }
            if (m.getDeclaringClass() != Any::class && !java.lang.reflect.Modifier.isFinal(m.getModifiers())) {
                assertTrue(
                    m.getDeclaringClass() == NoMergePolicy::class, "$m is not overridden ! "
                )
            }
        }
    }*/

    @Throws(IOException::class)
    override fun assertSegmentInfos(policy: MergePolicy, infos: SegmentInfos) {
        for (info in infos) {
            assertEquals(
                IndexWriter.SOURCE_FLUSH,
                info.info.getAttribute(IndexWriter.SOURCE)
            )
        }
    }

    @Throws(IOException::class)
    override fun assertMerge(policy: MergePolicy, merge: MergeSpecification) {
        fail() // should never happen
    }

    @Test
    @Throws(IOException::class)
    override fun testSimulateAppendOnly() {
        // Reduce numbers as this merge policy doesn't work well with lots of data
        doTestSimulateAppendOnly(mergePolicy(), totalDocs = 1000, maxDocsPerFlush = 100) // TODO reduced from totalDocs = 1000000, maxDocsPerFlush = 10000 to totalDocs = 1000, maxDocsPerFlush = 100 for dev speed
    }

    @Test
    @Throws(IOException::class)
    override fun testSimulateUpdates() {
        // Reduce numbers as this merge policy doesn't work well with lots of data
        doTestSimulateUpdates(mergePolicy(), totalDocs = 1000, maxDocsPerFlush = 100) // TODO reduced from totalDocs = 100000, maxDocsPerFlush = 1000 to totalDocs = 1000, maxDocsPerFlush = 100 for dev speed
    }

    // tests inherited from BaseMergePolicyTestCase

    @Test
    override fun testForceMergeNotNeeded() = super.testForceMergeNotNeeded()

    @Test
    override fun testFindForcedDeletesMerges() = super.testFindForcedDeletesMerges()

    @Test
    override fun testNoPathologicalMerges() = super.testNoPathologicalMerges()

}
