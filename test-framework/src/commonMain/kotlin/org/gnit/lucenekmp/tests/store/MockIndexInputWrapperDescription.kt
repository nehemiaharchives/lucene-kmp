package org.gnit.lucenekmp.tests.store

import org.gnit.lucenekmp.store.IndexInput

internal expect fun mockIndexInputWrapperDescription(name: String, delegate: IndexInput): String
