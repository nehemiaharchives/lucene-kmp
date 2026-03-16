package org.gnit.lucenekmp.tests.store

import org.gnit.lucenekmp.store.IndexInput

internal actual fun mockIndexInputWrapperDescription(name: String, delegate: IndexInput): String =
    "MockIndexInputWrapper(name=$name)"
