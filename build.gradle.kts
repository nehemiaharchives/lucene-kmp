import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    //alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlinMultiplatform) apply  false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
}

subprojects {
    group = providers.gradleProperty("group").get()
    version = providers.gradleProperty("version").get()

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

            extensions.configure<KotlinMultiplatformAndroidLibraryExtension>("androidLibrary") {
                namespace = "${project.group}.lucenekmp.${project.name.replace('-', '.')}"
                compileSdk = libs.versions.android.compileSdk.get().toInt()
                minSdk = libs.versions.android.minSdk.get().toInt()
            }

            targets.withType(KotlinAndroidTarget::class.java).configureEach {
                @OptIn(ExperimentalKotlinGradlePluginApi::class)
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }

            targets.withType(KotlinJvmTarget::class.java).configureEach {
                @OptIn(ExperimentalKotlinGradlePluginApi::class)
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
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

    tasks.withType<KotlinNativeTest>().configureEach {
        dependsOn(syncIndexTestResources)
        val indexResourcesDir = syncIndexTestResources.get().destinationDir.absolutePath

        // iOS simulator tests are launched via `simctl spawn`; forward env to child process.
        environment("tests.linedocsfile", testLineDocsPath)
        environment("SIMCTL_CHILD_tests.linedocsfile", testLineDocsPath)
        environment("tests.indexresourcesdir", indexResourcesDir)
        environment("SIMCTL_CHILD_tests.indexresourcesdir", indexResourcesDir)
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
