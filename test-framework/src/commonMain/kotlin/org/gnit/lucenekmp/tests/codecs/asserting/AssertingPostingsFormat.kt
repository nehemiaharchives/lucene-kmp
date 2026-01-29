/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.tests.codecs.asserting

import okio.IOException
import org.gnit.lucenekmp.codecs.FieldsConsumer
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.codecs.NormsProducer
import org.gnit.lucenekmp.codecs.PostingsFormat
import org.gnit.lucenekmp.index.FieldInfo
import org.gnit.lucenekmp.index.Fields
import org.gnit.lucenekmp.index.IndexOptions
import org.gnit.lucenekmp.index.IndexWriter
import org.gnit.lucenekmp.index.PostingsEnum
import org.gnit.lucenekmp.index.SegmentReadState
import org.gnit.lucenekmp.index.SegmentWriteState
import org.gnit.lucenekmp.index.Terms
import org.gnit.lucenekmp.index.TermsEnum
import org.gnit.lucenekmp.jdkport.assert
import org.gnit.lucenekmp.search.DocIdSetIterator.Companion.NO_MORE_DOCS
import org.gnit.lucenekmp.tests.index.AssertingLeafReader
import org.gnit.lucenekmp.tests.util.TestUtil
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.BytesRefBuilder

/** Just like the default postings format but with additional asserts. */
class AssertingPostingsFormat : PostingsFormat("Asserting") {
    private val `in`: PostingsFormat = TestUtil.getDefaultPostingsFormat()

    @Throws(IOException::class)
    override fun fieldsConsumer(state: SegmentWriteState): FieldsConsumer {
        return AssertingFieldsConsumer(state, `in`.fieldsConsumer(state))
    }

    @Throws(IOException::class)
    override fun fieldsProducer(state: SegmentReadState): FieldsProducer {
        return AssertingFieldsProducer(`in`.fieldsProducer(state))
    }

    internal class AssertingFieldsProducer(private val `in`: FieldsProducer) : FieldsProducer() {

        override fun close() {
            `in`.close()
            `in`.close() // close again
        }

        override fun iterator(): MutableIterator<String> {
            val iterator = `in`.iterator()
            return iterator
        }

        @Throws(IOException::class)
        override fun terms(field: String?): Terms? {
            val terms = `in`.terms(field)
            return if (terms == null) null else AssertingLeafReader.AssertingTerms(terms)
        }

        override fun size(): Int {
            return `in`.size()
        }

        @Throws(IOException::class)
        override fun checkIntegrity() {
            `in`.checkIntegrity()
        }

        override val mergeInstance: FieldsProducer
            get() = AssertingFieldsProducer(`in`.mergeInstance)

        override fun toString(): String {
            return "${this::class.simpleName}($`in`)"
        }
    }

    internal class AssertingFieldsConsumer(
        private val writeState: SegmentWriteState,
        private val `in`: FieldsConsumer
    ) : FieldsConsumer() {
        @Throws(IOException::class)
        override fun write(fields: Fields, norms: NormsProducer?) {
            `in`.write(fields, norms)

            // TODO: more asserts?  can we somehow run a
            // "limited" CheckIndex here???  Or ... can we improve
            // AssertingFieldsProducer and us it also to wrap the
            // incoming Fields here?

            var lastField: String? = null

            for (field in fields) {
                val fieldInfo: FieldInfo? = writeState.fieldInfos!!.fieldInfo(field)
                assert(fieldInfo != null)
                assert(lastField == null || lastField < field)
                lastField = field

                val terms: Terms = fields.terms(field) ?: continue

                val termsEnum: TermsEnum = terms.iterator()
                var lastTerm: BytesRefBuilder? = null
                var postingsEnum: PostingsEnum? = null

                val hasFreqs = fieldInfo!!.indexOptions >= IndexOptions.DOCS_AND_FREQS
                val hasPositions = fieldInfo.indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS
                val hasOffsets =
                    fieldInfo.indexOptions >= IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS
                val hasPayloads = terms.hasPayloads()

                assert(hasPositions == terms.hasPositions())
                assert(hasOffsets == terms.hasOffsets())

                while (true) {
                    val term: BytesRef? = termsEnum.next()
                    if (term == null) {
                        break
                    }
                    assert(lastTerm == null || lastTerm.get() < term)
                    if (lastTerm == null) {
                        lastTerm = BytesRefBuilder()
                        lastTerm.append(term)
                    } else {
                        lastTerm.copyBytes(term)
                    }

                    var flags = 0
                    if (!hasPositions) {
                        if (hasFreqs) {
                            flags = flags or PostingsEnum.FREQS.toInt()
                        }
                        postingsEnum = termsEnum.postings(postingsEnum, flags)
                    } else {
                        flags = PostingsEnum.POSITIONS.toInt()
                        if (hasPayloads) {
                            flags = flags or PostingsEnum.PAYLOADS.toInt()
                        }
                        if (hasOffsets) {
                            flags = flags or PostingsEnum.OFFSETS.toInt()
                        }
                        postingsEnum = termsEnum.postings(postingsEnum, flags)
                    }

                    //assert(postingsEnum != null) { "termsEnum=$termsEnum hasPositions=$hasPositions" }

                    var lastDocID = -1

                    while (true) {
                        val docID = postingsEnum!!.nextDoc()
                        if (docID == NO_MORE_DOCS) {
                            break
                        }
                        assert(docID > lastDocID)
                        lastDocID = docID
                        if (hasFreqs) {
                            val freq = postingsEnum.freq()
                            assert(freq > 0)

                            if (hasPositions) {
                                var lastPos = -1
                                var lastStartOffset = -1
                                for (i in 0 until freq) {
                                    val pos = postingsEnum.nextPosition()
                                    assert(pos >= lastPos) {
                                        "pos=$pos vs lastPos=$lastPos i=$i freq=$freq"
                                    }
                                    assert(pos <= IndexWriter.MAX_POSITION) {
                                        "pos=$pos is > IndexWriter.MAX_POSITION=${IndexWriter.MAX_POSITION}"
                                    }
                                    lastPos = pos

                                    if (hasOffsets) {
                                        val startOffset = postingsEnum.startOffset()
                                        val endOffset = postingsEnum.endOffset()
                                        assert(endOffset >= startOffset)
                                        assert(startOffset >= lastStartOffset)
                                        lastStartOffset = startOffset
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun close() {
            `in`.close()
            `in`.close() // close again
        }
    }
}
