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
package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.codecs.FieldsProducer
import org.gnit.lucenekmp.document.Document
import org.gnit.lucenekmp.document.Field
import org.gnit.lucenekmp.document.StringField
import org.gnit.lucenekmp.store.ByteBuffersDirectory
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.tests.analysis.MockAnalyzer
import org.gnit.lucenekmp.tests.util.LuceneTestCase
import org.gnit.lucenekmp.util.BytesRef
import org.gnit.lucenekmp.util.IOUtils
import kotlin.test.Test

class TestMultiTermsEnum : LuceneTestCase() {
    // LUCENE-6826
    @Test
    @Throws(Exception::class)
    fun testNoTermsInField() {
        val directory: Directory = ByteBuffersDirectory()
        var writer = IndexWriter(directory, IndexWriterConfig(MockAnalyzer(random())))
        val document = Document()
        document.add(StringField("deleted", "0", Field.Store.YES))
        writer.addDocument(document)

        val reader = DirectoryReader.open(writer)
        writer.close()

        val directory2: Directory = ByteBuffersDirectory()
        writer = IndexWriter(directory2, IndexWriterConfig(MockAnalyzer(random())))

        val leaves = reader.leaves()
        val codecReaders = Array<CodecReader>(leaves.size) { i ->
            MigratingCodecReader(leaves[i].reader() as CodecReader)
        }

        writer.addIndexes(*codecReaders) // <- bang

        IOUtils.close(writer, reader, directory)
    }

    private class MigratingCodecReader(`in`: CodecReader) : FilterCodecReader(`in`) {
        override val postingsReader: FieldsProducer
            get() = MigratingFieldsProducer(super.postingsReader!!, fieldInfos)

        private class MigratingFieldsProducer(delegate: FieldsProducer, newFieldInfo: FieldInfos) :
            BaseMigratingFieldsProducer(delegate, newFieldInfo) {
            @Throws(IOException::class)
            override fun terms(field: String?): Terms? {
                return if ("deleted" == field) {
                    val deletedTerms = super.terms("deleted")
                    if (deletedTerms != null) {
                        ValueFilteredTerms(deletedTerms, BytesRef("1"))
                    } else {
                        null
                    }
                } else {
                    super.terms(field)
                }
            }

            override fun create(delegate: FieldsProducer, newFieldInfo: FieldInfos): FieldsProducer {
                return MigratingFieldsProducer(delegate, newFieldInfo)
            }

            private class ValueFilteredTerms(private val delegate: Terms, private val value: BytesRef) :
                Terms() {
                @Throws(IOException::class)
                override fun iterator(): TermsEnum {
                    return object : FilteredTermsEnum(delegate.iterator()) {
                        override fun accept(term: BytesRef): AcceptStatus {
                            val comparison = term.compareTo(value)
                            return if (comparison < 0) {
                                // I don't think it will actually get here because they are supposed to call
                                // nextSeekTerm
                                // to get the initial term to seek to.
                                AcceptStatus.NO_AND_SEEK
                            } else if (comparison > 0) {
                                AcceptStatus.END
                            } else { // comparison == 0
                                AcceptStatus.YES
                            }
                        }

                        override fun nextSeekTerm(currentTerm: BytesRef?): BytesRef? {
                            return if (currentTerm == null || currentTerm.compareTo(value) < 0) {
                                value
                            } else {
                                null
                            }
                        }
                    }
                }

                @Throws(IOException::class)
                override fun size(): Long {
                    throw UnsupportedOperationException()
                }

                override val sumTotalTermFreq: Long
                    get() = throw UnsupportedOperationException()

                override val sumDocFreq: Long
                    get() = throw UnsupportedOperationException()

                override val docCount: Int
                    get() = throw UnsupportedOperationException()

                override fun hasFreqs(): Boolean {
                    return delegate.hasFreqs()
                }

                override fun hasOffsets(): Boolean {
                    return delegate.hasOffsets()
                }

                override fun hasPositions(): Boolean {
                    return delegate.hasPositions()
                }

                override fun hasPayloads(): Boolean {
                    return delegate.hasPayloads()
                }
            }
        }

        private open class BaseMigratingFieldsProducer(
            private val delegate: FieldsProducer,
            private val newFieldInfo: FieldInfos,
        ) : FieldsProducer() {
            override fun iterator(): MutableIterator<String> {
                val fieldInfoIterator = newFieldInfo.iterator()
                return object : MutableIterator<String> {
                    override fun hasNext(): Boolean {
                        return fieldInfoIterator.hasNext()
                    }

                    override fun remove() {
                        throw UnsupportedOperationException()
                    }

                    override fun next(): String {
                        return fieldInfoIterator.next().name
                    }
                }
            }

            override fun size(): Int {
                return newFieldInfo.size()
            }

            @Throws(IOException::class)
            override fun terms(field: String?): Terms? {
                return delegate.terms(field)
            }

            override val mergeInstance: FieldsProducer
                get() = create(delegate.mergeInstance, newFieldInfo)

            protected open fun create(delegate: FieldsProducer, newFieldInfo: FieldInfos): FieldsProducer {
                return BaseMigratingFieldsProducer(delegate, newFieldInfo)
            }

            @Throws(IOException::class)
            override fun checkIntegrity() {
                delegate.checkIntegrity()
            }

            override fun close() {
                delegate.close()
            }
        }

        override val coreCacheHelper: CacheHelper?
            get() = null

        override val readerCacheHelper: CacheHelper?
            get() = null
    }
}
