#!/usr/bin/env -S kotlin -J--enable-native-access=ALL-UNNAMED -J--sun-misc-unsafe-memory-access=allow

@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:5.0.3")
@file:DependsOn("com.github.ajalt.mordant:mordant:3.0.2")

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import java.io.File



/** Lucene → Kotlin‑MPP progress dashboard (core + test‑framework) */

data class Source(val fqn: String, val file: File)

// ──────────────────────────────────────────────────────────────
fun File.collectSources(ext: String): List<Source> =
    walkTopDown()
        .filter { it.isFile && it.extension == ext }
        .map { f ->
            val pkgLine = f.useLines { it.firstOrNull { l -> l.startsWith("package ") } }
            val pkg = pkgLine?.removePrefix("package ")?.trim()?.trimEnd(';') ?: ""
            val fqn = if (pkg.isEmpty()) f.nameWithoutExtension else "$pkg.${f.nameWithoutExtension}"
            Source(fqn, f)
        }.toList()

// Helper functions to create markdown links for Java FQNs
fun markdownLinkForJavaFQN(javaFqn: String): String {
    return "[$javaFqn](${createGitHubLink(javaFqn)})"
}

fun markdownLinkForTestFQN(javaFqn: String): String {
    return "[$javaFqn](${createGitHubLink(javaFqn, "core", "test")})"
}

fun mapToKmp(fqn: String): String = when {
    fqn.startsWith("org.apache.lucene.") -> "org.gnit.lucenekmp." + fqn.removePrefix("org.apache.lucene.")
    fqn.startsWith("java.")              -> "org.gnit.lucenekmp.jdkport." + fqn.substringAfterLast('.')
    else                                  -> fqn
}

fun linesOf(file: File) = file.useLines { it.count() }
fun checkbox(ok: Boolean) = if (ok) "[x]" else "[ ]"

val javaLuceneCommitId = "ec75fcad5a4208c7b9e35e870229d9b703cda8f3"

/** Create a GitHub link to the source file in the Lucene repository.
 *  @param javaFqn Fully qualified name of the Java class (e.g., "org.apache.lucene.analysis.Analyzer").
 *  @param srcDir Source directory (e.g., "java" or "test" for unit tests).
 *  @param luceneModule Lucene module name (default is "core", can be "test-framework", "analysis" etc).
 *  @return URL to the source file in the Lucene GitHub repository.
 */
fun createGitHubLink(javaFqn: String, luceneModule: String = "core", srcDir: String = "java"): String {
    // for exammple: https://github.com/apache/lucene/blob/ec75fcad5a4208c7b9e35e870229d9b703cda8f3/lucene/core/src/java/org/apache/lucene/analysis/Analyzer.java
    val path = javaFqn.replace('.', '/') + ".java"
    return "https://github.com/apache/lucene/blob/$javaLuceneCommitId/lucene/$luceneModule/src/$srcDir/$path"
}

// ─── main command ───────────────────────────────────────────────────────────
class Progress : CliktCommand() {
    // Hardcoded list of classes not to port
    private val notToPort = mutableSetOf(
        "org.apache.lucene.util.HotspotVMOptions",
        "org.apache.lucene.util.NamedThreadFactory",
        "org.apache.lucene.util.SuppressForbidden",
        "org.apache.lucene.internal.tests.ConcurrentMergeSchedulerAccess",
        "org.apache.lucene.internal.tests.FilterIndexInputAccess",
        "org.apache.lucene.internal.tests.IndexPackageAccess",
        "org.apache.lucene.internal.tests.IndexWriterAccess",
        "org.apache.lucene.store.FilterIndexInput",
        // Add more FQNs here as needed
    )

    private val homeDir = System.getenv("HOME") ?: System.getProperty("user.home")
    private val javaDir by option("--java", "-j").default("$homeDir/code/lp/lucene")
    private val kmpDir  by option("--kmp", "-k").default("$homeDir/code/lp/lucene-kmp")

    private val term = Terminal()
    private val javaIndex = mutableMapOf<String, Source>()
    private val kmpIndex  = mutableMapOf<String, Source>()

    private val markDown = StringBuilder()

    fun mdTableBuilder(header: List<String>, rows: List<List<Any?>>): String {
        val sb = StringBuilder()
        // Header
        sb.append("| ").append(header.joinToString(" | ")).append(" |\n")
        // Separator (add spaces for proper markdown)
        sb.append("| ").append(header.joinToString(" | ") { "---" }).append(" |\n")
        // Rows
        for (row in rows) {
            sb.append("| ").append(row.joinToString(" | ") { it?.toString() ?: "" }).append(" |\n")
        }
        return sb.toString()
    }

    private fun index(src: List<Source>, dst: MutableMap<String, Source>) {
        src.forEach { dst[it.fqn] = it }
    }
    
    // ── test classes counter ────────────────────────────────────────────────
    private fun countTestClasses(testDir: File): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        
        if (testDir.exists() && testDir.isDirectory) {
            testDir.walkTopDown()
                .filter { it.isFile && it.name.startsWith("Test") && it.extension == "java" }
                .forEach { file ->
                    // Since we're already in org/apache/lucene, don't add the prefix again
                    val relativePath = file.parentFile.toRelativePath(testDir)
                    val packageName = if (relativePath.isEmpty()) {
                        "org.apache.lucene"
                    } else {
                        "org.apache.lucene." + relativePath.replace(File.separatorChar, '.')
                    }
                    counts[packageName] = counts.getOrDefault(packageName, 0) + 1
                }
        }

        return counts
    }

    private fun countKotlinTestClasses(testDir: File): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        
        if (testDir.exists() && testDir.isDirectory) {
            testDir.walkTopDown()
                .filter { it.isFile && it.name.startsWith("Test") && it.extension == "kt" }
                .forEach { file ->
                    // Since we're already in org/gnit/lucenekmp, don't add the prefix again
                    val relativePath = file.parentFile.toRelativePath(testDir)
                    val packageName = if (relativePath.isEmpty()) {
                        "org.gnit.lucenekmp"
                    } else {
                        "org.gnit.lucenekmp." + relativePath.replace(File.separatorChar, '.')
                    }

                    val lineCount = file.useLines { it.count() }
                    if (lineCount > 5) {
                        counts[packageName] = counts.getOrDefault(packageName, 0) + 1
                    }
                }
        }
        
        return counts
    }

    private fun File.toRelativePath(baseDir: File): String {
        val basePath = baseDir.absolutePath
        return if (this.absolutePath.startsWith(basePath)) {
            this.absolutePath.substring(basePath.length).trimStart(File.separatorChar)
        } else {
            this.absolutePath
        }
    }

    // Helper function for indentation - moved outside of renderPackageStats to be reusable
    private fun indent(pkg: String) =
        if (pkg == "org.apache.lucene") pkg else "  ".repeat(pkg.removePrefix("org.apache.lucene").count { it == '.' }) + pkg
    
    private fun renderTestClassesTable() {
        val javaTestDir = File(javaDir, "lucene/core/src/test/org/apache/lucene")
        val ktTestDir = File(kmpDir, "core/src/commonTest/kotlin/org/gnit/lucenekmp")
        
        if (!javaTestDir.exists()) {
            term.println("Java test directory not found: $javaTestDir")
            return
        }
        
        val javaCounts = countTestClasses(javaTestDir)
        val ktCounts = countKotlinTestClasses(ktTestDir)
        
        // Calculate totals
        val javaTotal = javaCounts.values.sum()
        val ktTotal = ktCounts.values.sum()
        
        val section = "## Test Classes Port Progress"
        term.println()
        term.println(bold(section))
        markDown.appendLine()
        markDown.appendLine(section)
        
        val sortedRows = javaCounts.entries
            .sortedBy { it.key }
            .map { 
                val pkg = it.key
                val javaCount = it.value
                // Find the corresponding Kotlin package
                val ktPkg = "org.gnit.lucenekmp." + pkg.removePrefix("org.apache.lucene.")
                val ktCount = ktCounts[ktPkg] ?: 0
                // Calculate percentage
                val percentage = if (javaCount == 0) 100 else ktCount * 100 / javaCount
                listOf(indent(pkg), javaCount, ktCount, "$percentage%")
            }
            .toMutableList()
        
        // Add total row with percentage
        val totalPercentage = if (javaTotal == 0) 100 else ktTotal * 100 / javaTotal
        sortedRows.add(listOf("Total", javaTotal, ktTotal, "$totalPercentage%"))
        
        val tbl = table {
            header { row("Subpackage", "Count", "Ported", "%") }
            body {
                sortedRows.forEach { row(it[0], it[1], it[2], it[3]) }
            }
        }
        
        term.println(tbl)
        
        // Markdown table
        val mdTable = mdTableBuilder(
            listOf("Subpackage", "Count", "Ported", "%"),
            sortedRows
        )
        markDown.appendLine(mdTable)
    }

    private fun renderTestsToPort() {
        val javaTestDir = File(javaDir, "lucene/core/src/test/org/apache/lucene")
        val ktTestDir = File(kmpDir, "core/src/commonTest/kotlin/org/gnit/lucenekmp")
        
        if (!javaTestDir.exists()) {
            term.println("Java test directory not found: $javaTestDir")
            return
        }
        
        // Collect all Java test files
        val javaTests = mutableListOf<Source>()
        javaTestDir.walkTopDown()
            .filter { it.isFile && it.name.startsWith("Test") && it.extension == "java" }
            .forEach { file ->
                // Read package from file content
                val pkgLine = file.useLines { it.firstOrNull { l -> l.startsWith("package ") } }
                val pkg = pkgLine?.removePrefix("package ")?.trim()?.trimEnd(';') ?: ""
                val fqn = if (pkg.isEmpty()) file.nameWithoutExtension else "$pkg.${file.nameWithoutExtension}"
                javaTests.add(Source(fqn, file))
            }
        
        // Create mapping of existing Kotlin test files
        val ktTestMap = mutableMapOf<String, Source>()
        ktTestDir.walkTopDown()
            .filter { it.isFile && it.name.startsWith("Test") && it.extension == "kt" }
            .forEach { file ->
                val pkgLine = file.useLines { it.firstOrNull { l -> l.startsWith("package ") } }
                val pkg = pkgLine?.removePrefix("package ")?.trim() ?: ""
                val fqn = if (pkg.isEmpty()) file.nameWithoutExtension else "$pkg.${file.nameWithoutExtension}"
                ktTestMap[fqn] = Source(fqn, file)
            }
        
        val section = "## Tests To Port"
        term.println()
        term.println(bold(section))
        markDown.appendLine()
        markDown.appendLine(section)
        
        // Filter out tests that are already ported
        val rows = javaTests
            .map { javaTest ->
                val javaFqn = javaTest.fqn
                val expectedKtFqn = mapToKmp(javaFqn)
                Pair(javaFqn, expectedKtFqn)
            }
            .filter { (_, ktFqn) -> ktFqn !in ktTestMap } // Only include tests that haven't been ported
            .map { (javaFqn, ktFqn) -> listOf(javaFqn, ktFqn) }
            .sortedBy { it[0] }
        
        val tbl = table {
            header { row("Java Test FQN", "Kotlin Test FQN") }
            body {
                rows.forEach { row(it[0], it[1]) }
            }
        }
        
        term.println(tbl)
        
        // Markdown table with links for test classes
        val mdRows = rows.map { row -> 
            listOf(markdownLinkForTestFQN(row[0]), row[1])
        }
        
        val mdTable = mdTableBuilder(
            listOf("Java Test FQN", "Kotlin Test FQN"),
            mdRows
        )
        markDown.appendLine(mdTable)
    }

    // ── dependency helpers ────────────────────────────────────────────────
    /** collapse inner‑class FQN like Foo\$Bar\$Baz → first existing outer found in javaIndex */
    private fun collapse(fqn: String): String {
        var cur = fqn
        while (cur !in javaIndex) {
            cur = when {
                '$' in cur -> cur.substringBeforeLast('$')
                '.' in cur -> cur.substringBeforeLast('.')
                else -> ""
            }
            if (cur.isEmpty()) break
        }
        return cur
    }

    /** depth‑1: imports + same‑package capitalised identifiers */
    private fun directDeps(fqn: String): Set<String> {
        val src = javaIndex[fqn] ?: return emptySet()
        val deps = mutableSetOf<String>()
        val pkg  = fqn.substringBeforeLast('.', "")
        // imports
        src.file.useLines { lines ->
            lines.filter { it.startsWith("import org.apache.lucene.") }
                .filterNot { it.contains("javadoc")}
                .map { it.removePrefix("import ").trim().trimEnd(';') }
                .forEach { collapse(it).takeIf { it.isNotEmpty() }?.let { deps += it } }
        }
        // same‑package tokens
        if (pkg.isNotEmpty()) {
            val simple2fqn = javaIndex.keys.filter { it.startsWith("$pkg.") }
                .associateBy { it.substringAfterLast('.') }
            val raw = src.file.readText()
            val noBlock = raw.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
            val noLine  = noBlock.replace(Regex("//.*"), " ")
            val regex = Regex("\\b[A-Z][A-Za-z0-9_]*\\b")
            regex.findAll(noLine).forEach { m ->
                simple2fqn[m.value]?.let { collapse(it).takeIf { it.isNotEmpty() }?.let { deps += it } }
            }
        }
        return deps
    }

    private fun collectTransitive(start: Set<String>): Set<String> {
        val seen = mutableSetOf<String>()
        fun dfs(c: String) {
            if (!seen.add(c)) return
            directDeps(c).forEach(::dfs)
        }
        start.forEach(::dfs)
        return seen
    }

    // ── tables ────────────────────────────────────────────────────────────
    private fun renderMissing(missing: List<String>) {
        if (missing.isEmpty()) return
        term.println()
        val section = "## KMP Deps To Port"
        term.println(bold(section))
        markDown.appendLine(section)

        val tbl = table {
            header { row("Java FQN", "Expected KMP FQN") }
            body {
                missing.forEach { dep ->
                    row(dep, mapToKmp(dep))
                }
            }
        }
        term.println(tbl)


        // Markdown table with links for Java FQNs
        val mdRows = missing.map { listOf(markdownLinkForJavaFQN(it), mapToKmp(it)) }
        val mdTable = mdTableBuilder(
            listOf("Java FQN", "Expected KMP FQN"),
            mdRows
        )
        markDown.appendLine(mdTable)
    }
    private fun renderPackageStats(depSet: Set<String>, kmpSet: Set<String>) {
        // Exclude notToPort classes from statistics
        val filteredDepSet = depSet.minus(notToPort)
        val grouped = filteredDepSet.groupBy { it.substringBeforeLast('.', "<default>") }
            .filterKeys { k -> k != "<default>" && k.substringAfterLast('.').first().isLowerCase() }

        val rows = grouped.keys.sorted().map { pkg ->
            val total  = grouped[pkg]!!.size
            val ported = grouped[pkg]!!.count { mapToKmp(it) in kmpSet }
            Triple(pkg, total, ported)
        }
        // overall counts should aggregate by *top‑level* package (analysis, codecs …)
        fun topLevel(pkg: String): String =
            if (!pkg.startsWith("org.apache.lucene.")) pkg
            else "org.apache.lucene." + pkg.removePrefix("org.apache.lucene.").substringBefore('.')

        val topTotals = mutableMapOf<String, Int>()
        val topPorted = mutableMapOf<String, Int>()
        grouped.forEach { (pkg, list) ->
            val top = topLevel(pkg)
            topTotals[top] = (topTotals[top] ?: 0) + list.size
            topPorted[top] = (topPorted[top] ?: 0) + list.count { mapToKmp(it) in kmpSet }
        }
        val overallTotal = topTotals.values.sum()
        val overallPort  = topPorted.values.sum()

        // Using the class-level indent function now
        val allRows = listOf(Triple("org.apache.lucene", overallTotal, overallPort)) + rows

        val section = "## Package statistics (priority‑1 deps)"
        term.println(bold(section))
        markDown.appendLine(section)

        val tbl = table {
            header { row("Java package", "KMP mapped", "Classes", "Ported", "%", "Done") }
            body   {
                allRows.forEach { (pkg, total, ported) ->
                    val mapped = if (pkg == "org.apache.lucene") "org.gnit.lucenekmp" else mapToKmp(pkg)
                    val pct = if (total == 0) 0 else ported * 100 / total
                    row(indent(pkg), indent(mapped), total, ported, "$pct%", checkbox(ported == total))
                }
            }
        }

        term.println(tbl)

        // Markdown table generation
        val mdRows = allRows.map { (pkg, total, ported) ->
            val mapped = if (pkg == "org.apache.lucene") "org.gnit.lucenekmp" else mapToKmp(pkg)
            val pct = if (total == 0) 0 else ported * 100 / total
            listOf(indent(pkg), indent(mapped), total, ported, "$pct%", checkbox(ported == total))
        }

        val mdTable = mdTableBuilder(
            listOf("Java package", "KMP mapped", "Classes", "Ported", "%", "Done"),
            mdRows
        )
        markDown.appendLine(mdTable)
    }

    private fun renderPriorityStats(kmpSet: Set<String>) {
        val pri1 = coreWrite + coreSearch
        val pri1Set = pri1.toSet()
        data class Row(val java: String, val kmp: String, val total: Int, val ported: Int, val remain: Int, val done: Boolean)

        // recognise inner‑class names such as Foo\$Bar → Foo
        fun isPorted(dep: String): Boolean {
            val outer = dep.substringBefore('$')
            return mapToKmp(dep) in kmpSet || mapToKmp(outer) in kmpSet
        }

        val globalDeps = mutableSetOf<String>()
        val globalPorts = mutableSetOf<String>()

        fun deps(c: String) = directDeps(c).minus(pri1Set).minus(c).minus(notToPort)

        val rows = pri1.map { clazz ->
            val mapped = mapToKmp(clazz)
            val d = deps(clazz)
            globalDeps += d
            globalPorts += d.filter(::isPorted)
            val ported = d.count(::isPorted)
            val javaLOC = javaIndex[clazz]?.let { linesOf(it.file) } ?: 0
            val kmpLOC = kmpIndex[mapped]?.let { linesOf(it.file) } ?: 0
            val ratio = if (javaLOC == 0) 0.0 else kmpLOC.toDouble() / javaLOC
            val done = ratio in 0.8..1.2 && kmpLOC > 0
            Row(clazz, mapped, d.size, ported, d.size - ported, done)
        }
        val fullyDone = rows.count { it.done }

        term.println()
        markDown.appendLine()

        val section = "## Priority-1 API progress"
        term.println(bold(section))
        markDown.appendLine(section)

        val tbl = table {
            header { row("Java class", "Mapped class", "Java Deps", "KMP Deps Ported", "KMP Deps To Port", "%", "Done") }
            body {
                rows.forEach { r ->
                    val pct = if (r.total == 0) 100 else r.ported * 100 / r.total
                    row(r.java, r.kmp, r.total, r.ported, r.remain, "$pct%", checkbox(r.done))
                }
            }
            footer {
                val total = globalDeps.size
                val port = globalPorts.size
                val pct = if (total == 0) 100 else port * 100 / total
                row("TOTAL", "", total, port, total - port, "$pct%", checkbox(fullyDone == rows.size))
            }
        }

        term.println(tbl)

        // Markdown table generation
        val mdRows = rows.map { r ->
            val pct = if (r.total == 0) 100 else r.ported * 100 / r.total
            listOf(markdownLinkForJavaFQN(r.java), r.kmp, r.total, r.ported, r.remain, "$pct%", checkbox(r.done))
        } + listOf(
            // Footer row doesn't need a link
            run {
                val total = globalDeps.size
                val port = globalPorts.size
                val pct = if (total == 0) 100 else port * 100 / total
                listOf("TOTAL", "", total, port, total - port, "$pct%", checkbox(fullyDone == rows.size))
            }
        )
        val mdTable = mdTableBuilder(
            listOf("Java class", "Mapped class", "Java Deps", "KMP Deps Ported", "KMP Deps To Port", "%", "Done"),
            mdRows
        )
        markDown.appendLine(mdTable)
    }

    // ── run ────────────────────────────────────────────────────────────────
    override fun run() {
        val javaRoot = File(javaDir).canonicalFile
        val kmpRoot  = File(kmpDir).canonicalFile
        require(javaRoot.isDirectory && kmpRoot.isDirectory)

        val coreDirs = listOf(
            File(javaRoot, "lucene/core/src/java"),
            File(javaRoot, "lucene/test-framework/src/java")
        ).filter { it.exists() }
        require(coreDirs.isNotEmpty())

        val javaSrc = coreDirs.flatMap { it.collectSources("java") }
        val kmpSrc  = kmpRoot.collectSources("kt")
        index(javaSrc, javaIndex)
        index(kmpSrc, kmpIndex)
        val kmpSet = kmpSrc.map { it.fqn }.toSet()

        // Exclude notToPort from all dependency sets
        val deps = collectTransitive(pri1.toSet()).filter { it in javaIndex && it !in notToPort }.toSet()

        val section = "# Lucene KMP Port Progress"
        term.println(bold(section))
        markDown.appendLine(section)

        renderPackageStats(deps, kmpSet)
        renderPriorityStats(kmpSet)
        val missingDeps = deps.filter { dep ->
            val outer = dep.substringBefore('$')
            val mappedDep = mapToKmp(dep)
            val mappedOuter = mapToKmp(outer)
            mappedDep !in kmpSet && mappedOuter !in kmpSet
        }.filter { it !in notToPort } // Exclude notToPort from missing list
         .sorted()
        renderMissing(missingDeps)
        
        // Add the table with Test classes count
        renderTestClassesTable()
        
        // Add the new Tests To Port table
        renderTestsToPort()

        File(kmpRoot, "PROGRESS.md").writeText(markDown.toString())
    }

    // priority‑1 API lists
    private val coreWrite = listOf(
        "org.apache.lucene.index.IndexWriter",
        "org.apache.lucene.index.IndexWriterConfig",
        "org.apache.lucene.store.FSDirectory",
        "org.apache.lucene.analysis.Analyzer",
        "org.apache.lucene.document.Document",
        "org.apache.lucene.document.Field",
        "org.apache.lucene.document.IntPoint",
        "org.apache.lucene.document.StoredField",
        "org.apache.lucene.document.TextField"
    )

    private val coreSearch = listOf(
        "org.apache.lucene.index.DirectoryReader",
        "org.apache.lucene.index.StandardDirectoryReader",
        "org.apache.lucene.queryparser.classic.QueryParser",
        "org.apache.lucene.search.IndexSearcher",
        "org.apache.lucene.store.FSLockFactory",
        "org.apache.lucene.store.NIOFSDirectory",
        "org.apache.lucene.document.IntPoint",
        "org.apache.lucene.search.Query",
        "org.apache.lucene.search.BooleanQuery",
        "org.apache.lucene.search.BooleanClause",
        "org.apache.lucene.search.Sort",
        "org.apache.lucene.search.SortField"
    )

    private val pri1 get() = coreWrite + coreSearch
}

Progress().main(args)