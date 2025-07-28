package org.gnit.lucenekmp.index

import okio.IOException
import org.gnit.lucenekmp.store.Directory
import org.gnit.lucenekmp.store.FilterDirectory
import org.gnit.lucenekmp.store.IOContext
import org.gnit.lucenekmp.store.IndexInput
import org.gnit.lucenekmp.store.IndexOutput

class TrackingTmpOutputDirectoryWrapper(`in`: Directory) :
    FilterDirectory(`in`) {
    val fileNames: MutableMap<String, String> = HashMap()

    @Throws(IOException::class)
    override fun createOutput(
        name: String,
        context: IOContext
    ): IndexOutput {
        val output: IndexOutput = super.createTempOutput(name, "", context)
        fileNames.put(name, output.name)
        return output
    }

    @Throws(IOException::class)
    override fun openInput(
        name: String,
        context: IOContext
    ): IndexInput {
        // keep the original file name if no match, it might be a temp file already
        val tmpName: String = fileNames[name] ?: name
        return super.openInput(tmpName, context)
    }

    fun getTemporaryFiles() = fileNames
}
