package org.gnit.lucenekmp.tests.util

import org.gnit.lucenekmp.jdkport.Ported

/**
 * A generator emitting simple ASCII characters from the set
 * (newlines not counted):
 * <pre>
 * abcdefghijklmnopqrstuvwxyz
 * ABCDEFGHIJKLMNOPQRSTUVWXYZ
 * </pre>
 *
 * @deprecated Use [AsciiLettersGenerator] instead.
 */
@Deprecated("Use AsciiLettersGenerator instead.")
@Ported(from = "com.carrotsearch.randomizedtesting.generators.ASCIIGenerator")
class ASCIIGenerator : AsciiLettersGenerator()
