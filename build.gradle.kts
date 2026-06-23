import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.konan.target.Family

plugins {
    //alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.dokka.plugin)
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
}

dependencies {
    subprojects.forEach {
        add("dokka", project(it.path))
    }
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")

    group = providers.gradleProperty("group").get()
    version = providers.gradleProperty("version").get()

    val kotlinDataDir = System.getProperty("kotlin.data.dir") ?: ""
    val duplicateKlibStrategyArg = "-Xklib-duplicated-unique-name-strategy=allow-first-with-warning"
    val disableNativeCastsOptimizationArg = "-Xdisable-phases=CastsOptimization"

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
        inputs.property("kotlinDataDir", kotlinDataDir)
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
        compilerOptions {
            optIn.add("kotlin.ExperimentalStdlibApi")
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }

    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<KotlinMultiplatformExtension>("kotlin") {
            targets.withType(KotlinNativeTarget::class.java).configureEach {
                binaries.configureEach {
                    freeCompilerArgs += disableNativeCastsOptimizationArg
                }
                compilations.configureEach {
                    compileTaskProvider.configure {
                        compilerOptions.freeCompilerArgs.add(duplicateKlibStrategyArg)
                    }
                }
            }
        }
    }

    // Centralized publication configuration for modules that apply the Vanniktech Maven Publish plugin
    pluginManager.withPlugin("com.vanniktech.maven.publish") {
        extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension>("mavenPublishing") {
            publishToMavenCentral()
            signAllPublications()

            val modulePrefix = if (project.path.startsWith(":analysis:")) "analysis-" else ""
            coordinates("org.gnit.lucene-kmp", "lucene-kmp-${modulePrefix}${project.name}", version.toString())

            pom {
                name = "lucene-kmp (module: ${project.name})"
                description = "Kotlin Multiplatform port of Apache Lucene (module: ${project.name})"
                inceptionYear = "2025"
                url = "https://github.com/nehemiaharchives/lucene-kmp"

                issueManagement {
                    system = "GitHub"
                    url = "https://github.com/nehemiaharchives/lucene-kmp/issues"
                }

                ciManagement {
                    system = "GitHub Actions"
                    url = "https://github.com/nehemiaharchives/lucene-kmp/actions"
                }

                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                        distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }

                developers {
                    developer {
                        id = "nehemiaharchives"
                        name = "Hokuto Joel Ide"
                        url = "https://github.com/nehemiaharchives"
                    }
                }

                scm {
                    url = "https://github.com/nehemiaharchives/lucene-kmp"
                    connection = "scm:git:git://github.com/nehemiaharchives/lucene-kmp.git"
                    developerConnection = "scm:git:ssh://git@github.com/nehemiaharchives/lucene-kmp.git"
                }
            }
        }
    }

    // Centralized Android configuration for Android library modules
    /*pluginManager.withPlugin("com.android.library") {
        extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
            // Stable per-module namespace within the lucenekmp package
            namespace = "${project.group}.lucenekmp.${project.name.replace('-', '.')}"

            compileSdk = libs.versions.android.compileSdk.get().toInt()
            defaultConfig {
                minSdk = libs.versions.android.minSdk.get().toInt()
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }
        }
    }*/

    pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
        extensions.configure<KotlinMultiplatformExtension>("kotlin") {

            // All published modules support the same platform matrix.
            jvm()
            (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                withHostTestBuilder {}.configure {}
                withDeviceTestBuilder {
                    sourceSetTreeName = "test"
                }
            }
            iosArm64()
            iosX64()
            iosSimulatorArm64()
            @Suppress("DEPRECATION")
            macosX64()
            macosArm64()
            linuxX64()
            linuxArm64()
            mingwX64()

            // The default hierarchy template is disabled, so keep the shared source-set graph
            // in one place for every published multiplatform module.
            val commonMain = sourceSets.getByName("commonMain")
            val commonTest = sourceSets.getByName("commonTest")
            val jvmAndroidMain = sourceSets.maybeCreate("jvmAndroidMain").apply {
                dependsOn(commonMain)
            }
            val jvmAndroidTest = sourceSets.maybeCreate("jvmAndroidTest").apply {
                dependsOn(commonTest)
            }
            val nativeMain = sourceSets.maybeCreate("nativeMain").apply {
                compilerOptions.suppressWarnings = true
                dependsOn(commonMain)
            }
            val posixNativeMain = sourceSets.maybeCreate("posixNativeMain").apply {
                dependsOn(nativeMain)
            }
            val nativeTest = sourceSets.maybeCreate("nativeTest").apply {
                dependsOn(commonTest)
            }

            // Target source sets are created by each module after its plugins are applied.
            // configureEach connects both existing and subsequently-created source sets.
            sourceSets.configureEach {
                when (name) {
                    "jvmMain", "androidMain" -> dependsOn(jvmAndroidMain)
                    "jvmTest", "androidHostTest" -> dependsOn(jvmAndroidTest)
                    "iosArm64Main",
                    "iosX64Main",
                    "iosSimulatorArm64Main",
                    "macosArm64Main",
                    "macosX64Main",
                    "linuxArm64Main",
                    "linuxX64Main" -> dependsOn(posixNativeMain)

                    "mingwX64Main" -> dependsOn(nativeMain)
                    "iosArm64Test",
                    "iosX64Test",
                    "iosSimulatorArm64Test",
                    "macosArm64Test",
                    "macosX64Test",
                    "linuxArm64Test",
                    "linuxX64Test",
                    "mingwX64Test" -> dependsOn(nativeTest)
                }
            }

            extensions.configure<KotlinMultiplatformAndroidLibraryExtension>("androidLibrary") {
                namespace = "${project.group}.lucenekmp.${project.name.replace('-', '.')}"
                compileSdk = libs.versions.android.compileSdk.get().toInt()
                minSdk = libs.versions.android.minSdk.get().toInt()
            }

            targets.withType(KotlinAndroidTarget::class.java).configureEach {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }

            targets.withType(KotlinJvmTarget::class.java).configureEach {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }

            targets.withType(KotlinNativeTarget::class.java).configureEach {
                if (konanTarget.family != Family.MINGW) {
                    binaries.configureEach {
                        binaryOptions["sourceInfoType"] = "libbacktrace"
                    }
                }
            }
        }
    }

    val testLineDocsPath = rootProject.layout.projectDirectory
        .file("test-framework/src/commonTest/resources/org/gnit/lucenekmp/tests/util/europarl.lines.txt.gz")
        .asFile
        .absolutePath

    val syncIndexTestResources = tasks.register<Sync>("syncIndexTestResources") {
        from(rootProject.layout.projectDirectory.dir("test-framework/src/commonMain/resources/org/gnit/lucenekmp/tests/index"))
        into(layout.buildDirectory.dir("generated/test-resources/index"))
    }

    val syncAnalysisCommonTestResources = tasks.register<Sync>("syncAnalysisCommonTestResources") {
        from(rootProject.layout.projectDirectory.dir("analysis/common/src/commonTest/resources"))
        into(layout.buildDirectory.dir("generated/test-resources/analysis-common"))
    }

    tasks.withType<KotlinNativeTest>().configureEach {
        dependsOn(syncIndexTestResources)
        dependsOn(syncAnalysisCommonTestResources)
        val indexResourcesDir = syncIndexTestResources.get().destinationDir.absolutePath
        val analysisCommonResourcesDir = syncAnalysisCommonTestResources.get().destinationDir.absolutePath

        // iOS simulator tests are launched via `simctl spawn`; forward env to child process.
        environment("tests.linedocsfile", testLineDocsPath)
        environment("SIMCTL_CHILD_tests.linedocsfile", testLineDocsPath)
        environment("tests.indexresourcesdir", indexResourcesDir)
        environment("SIMCTL_CHILD_tests.indexresourcesdir", indexResourcesDir)
        environment("analysis.common.testresourcesdir", analysisCommonResourcesDir)
        environment("SIMCTL_CHILD_analysis.common.testresourcesdir", analysisCommonResourcesDir)
    }

    tasks.withType<AbstractTestTask>().configureEach {
        fun formatDuration(elapsedMs: Long): String {
            if (elapsedMs < 1_000L) return "${elapsedMs} ms"
            val totalSeconds = elapsedMs / 1_000L
            if (totalSeconds < 60L) {
                val tenths = (elapsedMs % 1_000L) / 100L
                return "${totalSeconds}.${tenths} s"
            }
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return "${minutes}m ${seconds}s"
        }

        testLogging {
            events = setOf(TestLogEvent.FAILED)
            exceptionFormat = TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
            showStandardStreams = false
        }
        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) = Unit

            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if (suite.className != null && result.testCount > 0 && result.failedTestCount == 0L) {
                    val elapsedMs = result.endTime - result.startTime
                    val duration = formatDuration(elapsedMs).padStart(8, ' ')
                    logger.lifecycle("PASSED SUITE: $duration | ${suite.className} (${result.testCount} tests)")
                }
            }

            override fun beforeTest(testDescriptor: TestDescriptor) = Unit

            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) = Unit
        })
    }

    tasks.matching { it.name in setOf("compileKotlinJvm", "compileTestKotlinJvm") }
        .configureEach {
            group = LifecycleBasePlugin.BUILD_GROUP // "build"
        }
}
