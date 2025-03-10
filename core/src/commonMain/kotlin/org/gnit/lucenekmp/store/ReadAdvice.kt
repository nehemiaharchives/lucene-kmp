package org.gnit.lucenekmp.store

/** Advice regarding the read access pattern.  */
enum class ReadAdvice {
    /**
     * Normal behavior. Data is expected to be read mostly sequentially. The system is expected to
     * cache the hottest pages.
     */
    NORMAL,

    /**
     * Data is expected to be read in a random-access fashion, either by [ seeking][IndexInput.seek] often and reading relatively short sequences of bytes at once, or by reading data
     * through the [RandomAccessInput] abstraction in random order.
     */
    RANDOM,

    /**
     * Data is expected to be read sequentially with very little seeking at most. The system may read
     * ahead aggressively and free pages soon after they are accessed.
     */
    SEQUENTIAL,

    /**
     * Data is treated as random-access memory in practice. [Directory] implementations may
     * explicitly load the content of the file in memory, or provide hints to the system so that it
     * loads the content of the file into the page cache at open time. This should only be used on
     * very small files that can be expected to fit in RAM with very high confidence.
     */
    RANDOM_PRELOAD
}
