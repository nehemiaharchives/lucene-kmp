/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnit.lucenekmp.facet.taxonomy

import okio.Path.Companion.toPath
import org.gnit.lucenekmp.facet.taxonomy.directory.DirectoryTaxonomyReader
import org.gnit.lucenekmp.store.FSDirectory
import org.gnit.lucenekmp.jdkport.ByteArrayOutputStream
import org.gnit.lucenekmp.jdkport.PrintStream

/** Prints how many ords are under each dimension. */

// java -cp ../build/core/classes/java:../build/facet/classes/java
// org.apache.lucene.facet.util.PrintTaxonomyStats -printTree
// /s2/scratch/indices/wikibig.trunk.noparents.facets.Lucene41.nd1M/facets
class PrintTaxonomyStats {
    /** Sole constructor. */
    constructor()

    companion object {
        /** Command-line tool. */
        fun main(args: Array<String>) {
            var printTree = false
            var path: String? = null
            for (i in args.indices) {
                if (args[i] == "-printTree") {
                    printTree = true
                } else {
                    path = args[i]
                }
            }
            if (args.size != if (printTree) 2 else 1) {
                println(
                    "\nUsage: java -classpath ... org.apache.lucene.facet.util.PrintTaxonomyStats [-printTree] /path/to/taxononmy/index\n")
                // Common code cannot terminate the host process; fail the command on invalid arguments.
                throw IllegalArgumentException("Invalid command-line arguments")
            }
            val dir = FSDirectory.open(path!!.toPath())
            val r = DirectoryTaxonomyReader(dir)
            // Bridge the common PrintStream to standard output without JVM-only System.out.
            val output = ByteArrayOutputStream()
            val out = PrintStream(out = output)
            printStats(r, out, printTree)
            print(output.toByteArray().decodeToString())
            r.close()
            dir.close()
        }

        /** Recursively prints stats for all ordinals. */
        fun printStats(r: TaxonomyReader, out: PrintStream, printTree: Boolean) {
            out.println(r.getSize().toString() + " total categories.")

            val it = r.getChildren(TaxonomyReader.ROOT_ORDINAL)
            var child = it.next()
            while (child != TaxonomyReader.INVALID_ORDINAL) {
                val chilrenIt = r.getChildren(child)
                var numImmediateChildren = 0
                while (chilrenIt.next() != TaxonomyReader.INVALID_ORDINAL) {
                    numImmediateChildren++
                }
                val cp = r.getPath(child)!!
                out.println(
                    "/" + cp.components[0] + ": " + numImmediateChildren +
                        " immediate children; " + (1 + countAllChildren(r, child)) + " total categories")
                if (printTree) {
                    printAllChildren(out, r, child, "  ", 1)
                }
                child = it.next()
            }
        }

        private fun countAllChildren(r: TaxonomyReader, ord: Int): Int {
            var count = 0
            val it = r.getChildren(ord)
            var child = it.next()
            while (child != TaxonomyReader.INVALID_ORDINAL) {
                count += 1 + countAllChildren(r, child)
                child = it.next()
            }
            return count
        }

        private fun printAllChildren(out: PrintStream, r: TaxonomyReader, ord: Int, indent: String, depth: Int) {
            val it = r.getChildren(ord)
            var child = it.next()
            while (child != TaxonomyReader.INVALID_ORDINAL) {
                out.println(indent + "/" + r.getPath(child)!!.components[depth])
                printAllChildren(out, r, child, indent + "  ", depth + 1)
                child = it.next()
            }
        }
    }
}
