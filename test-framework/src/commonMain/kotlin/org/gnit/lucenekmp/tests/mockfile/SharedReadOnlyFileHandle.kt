package org.gnit.lucenekmp.tests.mockfile

import okio.FileHandle
import okio.FileSystem
import okio.Path

internal expect fun openSharedReadOnlyFileHandleOrNull(
    delegate: FileSystem,
    file: Path,
): FileHandle?
