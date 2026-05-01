package org.gnit.lucenekmp.index

import org.gnit.lucenekmp.jdkport.ReentrantLock
import org.gnit.lucenekmp.jdkport.withLock

internal object IndexPerfDebug {
    private val lock = ReentrantLock()
    private var enabled = false

    private var openFromWriterCalls = 0L
    private var openFromWriterNrtIsCurrentMs = 0L
    private var openFromWriterGetReaderMs = 0L
    private var openFromWriterVersionCheckMs = 0L

    private var getReaderCalls = 0L
    private var getReaderBeforeFlushMs = 0L
    private var getReaderFlushAllThreadsMs = 0L
    private var getReaderPublishFlushedSegmentsMs = 0L
    private var getReaderProcessEventsMs = 0L
    private var getReaderApplyDeletesMs = 0L
    private var getReaderWriteReaderPoolMs = 0L
    private var getReaderOpenReaderMs = 0L
    private var getReaderFinishFullFlushMs = 0L
    private var getReaderMaybeMergeMs = 0L

    private var flushAllThreadsCalls = 0L
    private var flushAllThreadsMarkFullFlushMs = 0L
    private var flushAllThreadsMaybeFlushMs = 0L
    private var flushAllThreadsWaitForFlushMs = 0L
    private var flushAllThreadsFreezeGlobalBufferMs = 0L

    private var doFlushCalls = 0L
    private var doFlushNextPendingFlushMs = 0L
    private var doFlushPrepareTicketMs = 0L
    private var doFlushFlushSegmentMs = 0L
    private var doFlushDoAfterFlushMs = 0L
    private var doFlushAfterSegmentsFlushedMs = 0L

    fun enable() {
        reset()
        enabled = true
    }

    fun disable() {
        enabled = false
    }

    fun reset() {
        lock.withLock {
            openFromWriterCalls = 0L
            openFromWriterNrtIsCurrentMs = 0L
            openFromWriterGetReaderMs = 0L
            openFromWriterVersionCheckMs = 0L
            getReaderCalls = 0L
            getReaderBeforeFlushMs = 0L
            getReaderFlushAllThreadsMs = 0L
            getReaderPublishFlushedSegmentsMs = 0L
            getReaderProcessEventsMs = 0L
            getReaderApplyDeletesMs = 0L
            getReaderWriteReaderPoolMs = 0L
            getReaderOpenReaderMs = 0L
            getReaderFinishFullFlushMs = 0L
            getReaderMaybeMergeMs = 0L
            flushAllThreadsCalls = 0L
            flushAllThreadsMarkFullFlushMs = 0L
            flushAllThreadsMaybeFlushMs = 0L
            flushAllThreadsWaitForFlushMs = 0L
            flushAllThreadsFreezeGlobalBufferMs = 0L
            doFlushCalls = 0L
            doFlushNextPendingFlushMs = 0L
            doFlushPrepareTicketMs = 0L
            doFlushFlushSegmentMs = 0L
            doFlushDoAfterFlushMs = 0L
            doFlushAfterSegmentsFlushedMs = 0L
        }
    }

    fun recordOpenFromWriter(
        nrtIsCurrentMs: Long,
        getReaderMs: Long,
        versionCheckMs: Long
    ) {
        if (!enabled) return
        lock.withLock {
            openFromWriterCalls++
            openFromWriterNrtIsCurrentMs += nrtIsCurrentMs
            openFromWriterGetReaderMs += getReaderMs
            openFromWriterVersionCheckMs += versionCheckMs
        }
    }

    fun recordGetReader(
        beforeFlushMs: Long,
        flushAllThreadsMs: Long,
        publishFlushedSegmentsMs: Long,
        processEventsMs: Long,
        applyDeletesMs: Long,
        writeReaderPoolMs: Long,
        openReaderMs: Long,
        finishFullFlushMs: Long,
        maybeMergeMs: Long
    ) {
        if (!enabled) return
        lock.withLock {
            getReaderCalls++
            getReaderBeforeFlushMs += beforeFlushMs
            getReaderFlushAllThreadsMs += flushAllThreadsMs
            getReaderPublishFlushedSegmentsMs += publishFlushedSegmentsMs
            getReaderProcessEventsMs += processEventsMs
            getReaderApplyDeletesMs += applyDeletesMs
            getReaderWriteReaderPoolMs += writeReaderPoolMs
            getReaderOpenReaderMs += openReaderMs
            getReaderFinishFullFlushMs += finishFullFlushMs
            getReaderMaybeMergeMs += maybeMergeMs
        }
    }

    fun recordFlushAllThreads(
        markFullFlushMs: Long,
        maybeFlushMs: Long,
        waitForFlushMs: Long,
        freezeGlobalBufferMs: Long
    ) {
        if (!enabled) return
        lock.withLock {
            flushAllThreadsCalls++
            flushAllThreadsMarkFullFlushMs += markFullFlushMs
            flushAllThreadsMaybeFlushMs += maybeFlushMs
            flushAllThreadsWaitForFlushMs += waitForFlushMs
            flushAllThreadsFreezeGlobalBufferMs += freezeGlobalBufferMs
        }
    }

    fun recordDoFlush(
        nextPendingFlushMs: Long,
        prepareTicketMs: Long,
        flushSegmentMs: Long,
        doAfterFlushMs: Long,
        afterSegmentsFlushedMs: Long
    ) {
        if (!enabled) return
        lock.withLock {
            doFlushCalls++
            doFlushNextPendingFlushMs += nextPendingFlushMs
            doFlushPrepareTicketMs += prepareTicketMs
            doFlushFlushSegmentMs += flushSegmentMs
            doFlushDoAfterFlushMs += doAfterFlushMs
            doFlushAfterSegmentsFlushedMs += afterSegmentsFlushedMs
        }
    }

    fun snapshot(): String =
        if (!enabled) {
            "substep=index_perf_debug disabled=true"
        } else {
            lock.withLock {
                "substep=open_from_writer calls=$openFromWriterCalls nrtIsCurrentMs=$openFromWriterNrtIsCurrentMs getReaderMs=$openFromWriterGetReaderMs versionCheckMs=$openFromWriterVersionCheckMs " +
                    "substep=get_reader calls=$getReaderCalls beforeFlushMs=$getReaderBeforeFlushMs flushAllThreadsMs=$getReaderFlushAllThreadsMs publishFlushedSegmentsMs=$getReaderPublishFlushedSegmentsMs processEventsMs=$getReaderProcessEventsMs applyDeletesMs=$getReaderApplyDeletesMs writeReaderPoolMs=$getReaderWriteReaderPoolMs openReaderMs=$getReaderOpenReaderMs finishFullFlushMs=$getReaderFinishFullFlushMs maybeMergeMs=$getReaderMaybeMergeMs " +
                    "substep=flush_all_threads calls=$flushAllThreadsCalls markFullFlushMs=$flushAllThreadsMarkFullFlushMs maybeFlushMs=$flushAllThreadsMaybeFlushMs waitForFlushMs=$flushAllThreadsWaitForFlushMs freezeGlobalBufferMs=$flushAllThreadsFreezeGlobalBufferMs " +
                    "substep=do_flush calls=$doFlushCalls nextPendingFlushMs=$doFlushNextPendingFlushMs prepareTicketMs=$doFlushPrepareTicketMs flushSegmentMs=$doFlushFlushSegmentMs doAfterFlushMs=$doFlushDoAfterFlushMs afterSegmentsFlushedMs=$doFlushAfterSegmentsFlushedMs"
            }
        }
}
