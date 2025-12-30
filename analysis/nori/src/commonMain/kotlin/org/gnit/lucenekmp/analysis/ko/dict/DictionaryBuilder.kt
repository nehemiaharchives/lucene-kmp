package org.gnit.lucenekmp.analysis.ko.dict

import org.gnit.lucenekmp.jdkport.Ported
import okio.IOException
import okio.Path

/** Tool to build dictionaries. */
@Ported(from = "org.apache.lucene.analysis.ko.dict.DictionaryBuilder")
class DictionaryBuilder private constructor() {
    companion object {
        @Throws(IOException::class)
        fun build(inputDir: Path, outputDir: Path, encoding: String, normalizeEntry: Boolean) {
            TokenInfoDictionaryBuilder(encoding, normalizeEntry).build(inputDir).write(outputDir)
            UnknownDictionaryBuilder(encoding).build(inputDir).write(outputDir)
            ConnectionCostsBuilder.build(inputDir.resolve("matrix.def"))
                .write(outputDir, DictionaryConstants.CONN_COSTS_HEADER, DictionaryConstants.VERSION)
        }
    }
}
