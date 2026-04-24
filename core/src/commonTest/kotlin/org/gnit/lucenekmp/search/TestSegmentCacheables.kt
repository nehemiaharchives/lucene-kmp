package org.gnit.lucenekmp.search

import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.NumericDocValuesField
import org.gnit.lucenekmp.index.DirectoryReader
import org.gnit.lucenekmp.index.DocValues
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.IndexWriterConfig
import org.gnit.lucenekmp.index.LeafReaderContext
import org.gnit.lucenekmp.index.NoMergePolicy
import org.gnit.lucenekmp.index.Term
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestSegmentCacheables : LuceneTestCase() {

    @Test
    fun testMultipleDocValuesDelegates() {
        val seg =
            object : SegmentCacheable {
                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return true
                }
            }
        val non =
            object : SegmentCacheable {
                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return false
                }
            }
        val dv1 =
            object : SegmentCacheable {
                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return DocValues.isCacheable(ctx, "field1")
                }
            }
        val dv2 =
            object : SegmentCacheable {
                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return DocValues.isCacheable(ctx, "field2")
                }
            }
        val dv3 =
            object : SegmentCacheable {
                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return DocValues.isCacheable(ctx, "field3")
                }
            }
        val dv34 =
            object : SegmentCacheable {
                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return DocValues.isCacheable(ctx, "field3", "field4")
                }
            }
        val dv12 =
            object : SegmentCacheable {
                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return DocValues.isCacheable(ctx, "field1", "field2")
                }
            }

        val seg_dv1 =
            object : SegmentCacheable {
                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return isCacheable(ctx, seg, dv1)
                }
            }
        val dv2_dv34 =
            object : SegmentCacheable {
                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return isCacheable(ctx, dv2, dv34)
                }
            }
        val dv2_non =
            object : SegmentCacheable {
                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return isCacheable(ctx, dv2, non)
                }
            }

        val seg_dv1_dv2_dv34 =
            object : SegmentCacheable {
                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return isCacheable(ctx, seg_dv1, dv2_dv34)
                }
            }

        val dv1_dv3 =
            object : SegmentCacheable {
                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return isCacheable(ctx, dv1, dv3)
                }
            }
        val dv12_dv1_dv3 =
            object : SegmentCacheable {
                override fun isCacheable(ctx: LeafReaderContext): Boolean {
                    return isCacheable(ctx, dv12, dv1_dv3)
                }
            }

        val dir: Directory = newDirectory()
        val iwc: IndexWriterConfig = newIndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE)
        val w = IndexWriter(dir, iwc)
        val doc = Document()
        doc.add(NumericDocValuesField("field3", 1))
        doc.add(newTextField("text", "text", Field.Store.NO))
        w.addDocument(doc)
        w.commit()
        var reader = DirectoryReader.open(w)

        var ctx = reader.leaves()[0]

        assertTrue(seg_dv1.isCacheable(ctx))
        assertTrue(dv2_dv34.isCacheable(ctx))
        assertTrue(seg_dv1_dv2_dv34.isCacheable(ctx))
        assertFalse(dv2_non.isCacheable(ctx))

        w.updateNumericDocValue(Term("text", "text"), "field3", 2L)
        w.commit()
        reader.close()
        reader = DirectoryReader.open(dir)

        // after field3 is updated, all composites referring to it should be uncacheable

        ctx = reader.leaves()[0]
        assertTrue(seg_dv1.isCacheable(ctx))
        assertFalse(dv34.isCacheable(ctx))
        assertFalse(dv2_dv34.isCacheable(ctx))
        assertFalse(dv1_dv3.isCacheable(ctx))
        assertFalse(seg_dv1_dv2_dv34.isCacheable(ctx))
        assertFalse(dv12_dv1_dv3.isCacheable(ctx))

        reader.close()
        w.close()
        dir.close()
    }

    companion object {
        private fun isCacheable(ctx: LeafReaderContext, vararg ss: SegmentCacheable): Boolean {
            for (s in ss) {
                if (s.isCacheable(ctx) == false) return false
            }
            return true
        }
    }
}
