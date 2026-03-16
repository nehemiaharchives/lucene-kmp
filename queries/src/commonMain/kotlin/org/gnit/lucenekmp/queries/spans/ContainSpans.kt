package org.gnit.lucenekmp.queries.spans

import okio.IOException

abstract class ContainSpans(
    protected var bigSpans: Spans,
    protected var littleSpans: Spans,
    protected var sourceSpans: Spans,
) : ConjunctionSpans(listOf(bigSpans, littleSpans)) {
    override fun startPosition(): Int {
        return if (atFirstInCurrentDoc) {
            -1
        } else if (oneExhaustedInCurrentDoc) {
            NO_MORE_POSITIONS
        } else {
            sourceSpans.startPosition()
        }
    }

    override fun endPosition(): Int {
        return if (atFirstInCurrentDoc) {
            -1
        } else if (oneExhaustedInCurrentDoc) {
            NO_MORE_POSITIONS
        } else {
            sourceSpans.endPosition()
        }
    }

    override fun width(): Int {
        return sourceSpans.width()
    }

    @Throws(IOException::class)
    override fun collect(collector: SpanCollector) {
        bigSpans.collect(collector)
        littleSpans.collect(collector)
    }
}
