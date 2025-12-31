package org.gnit.lucenekmp.analysis.ja.dict

import org.gnit.lucenekmp.jdkport.Locale
import okio.Path
import okio.Path.Companion.toPath

/** Tool to build dictionaries. */
object DictionaryBuilder {
    /** Format of the dictionary. */
    enum class DictionaryFormat {
        IPADIC,
        UNIDIC
    }

    fun build(
        format: DictionaryFormat,
        inputDir: Path,
        outputDir: Path,
        encoding: String,
        normalizeEntry: Boolean
    ) {
        TokenInfoDictionaryBuilder(format, encoding, normalizeEntry)
            .build(inputDir)
            .write(outputDir)

        UnknownDictionaryBuilder(encoding)
            .build(inputDir)
            .write(outputDir)

        ConnectionCostsBuilder.build(inputDir.resolve("matrix.def"))
            .write(outputDir, DictionaryConstants.CONN_COSTS_HEADER, DictionaryConstants.VERSION)
    }

    fun main(args: Array<String>) {
        val format = DictionaryFormat.valueOf(args[0].uppercase())
        val inputDirName = args[1]
        val outputDirName = args[2]
        val inputEncoding = args[3]
        val normalizeEntries = args[4].toBoolean()
        build(
            format,
            inputDirName.toPath(),
            outputDirName.toPath(),
            inputEncoding,
            normalizeEntries
        )
    }
}
