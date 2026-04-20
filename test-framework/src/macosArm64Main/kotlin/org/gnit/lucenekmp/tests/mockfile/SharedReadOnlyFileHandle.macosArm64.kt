package org.gnit.lucenekmp.tests.mockfile

import okio.FileHandle
import okio.FileSystem
import okio.Path

internal actual fun openSharedReadOnlyFileHandleOrNull(
    delegate: FileSystem,
    file: Path,
): FileHandle? = null
