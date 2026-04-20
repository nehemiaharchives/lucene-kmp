package org.gnit.lucenekmp.jdkport

import okio.FileHandle
import okio.FileSystem
import okio.Path

internal actual fun openReadOnlyFileHandlePlatform(path: Path, fileSystem: FileSystem): FileHandle? = null

internal actual fun newOutputStreamPlatform(
    path: Path,
    fileSystem: FileSystem,
    options: Array<out OpenOption>,
): OutputStream? = null
