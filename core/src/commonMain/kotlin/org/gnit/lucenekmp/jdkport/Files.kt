package org.gnit.lucenekmp.jdkport

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * port of java.nio.file.Files
 */
object Files {
    fun newInputStream(path: Path): InputStream {
        SystemFileSystem.source(path).use { source ->
            return KIOSourceInputStream(source.buffered())
        }
    }

    fun newOutputStream(path: Path): OutputStream {
        SystemFileSystem.sink(path).use { sink ->
            return KIOSinkOutputStream(sink.buffered())
        }
    }

    fun newBufferedReader(path: Path, charset: Charset): Reader {
        val decoder = charset.newDecoder()
        val reader = InputStreamReader(newInputStream(path), decoder)
        return BufferedReader(reader)
    }
}
