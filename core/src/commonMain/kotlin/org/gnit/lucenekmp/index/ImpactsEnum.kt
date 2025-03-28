package org.gnit.lucenekmp.index

/**
 * Extension of [PostingsEnum] which also provides information about upcoming impacts.
 *
 * @lucene.experimental
 */
abstract class ImpactsEnum
/** Sole constructor.  */
protected constructor() : PostingsEnum(), ImpactsSource
