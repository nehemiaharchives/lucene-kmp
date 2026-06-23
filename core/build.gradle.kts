import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import kotlin.apply
import java.util.concurrent.TimeUnit

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    //alias(libs.plugins.androidLibrary)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.kover)
}

apply(from = rootProject.file("gradle/generateBreakIteratorData.gradle.kts"))
apply(from = rootProject.file("gradle/generateGb2312Data.gradle.kts"))

kotlin {
    android {
        packaging {
            resources {
                excludes += "META-INF/INDEX.LIST"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated/breakiterator/kotlin"))
            kotlin.srcDir(layout.buildDirectory.dir("generated/gb2312/kotlin"))
            dependencies {
                implementation(libs.okio)
                implementation(libs.kotlinenvvar)
                implementation(libs.kotlinbignum)
                implementation(libs.kotlinx.coroutines)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlin.logging)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":codecs"))
                implementation(project(":test-framework"))
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.okio.fakefilesystem)
            }
        }


        jvmMain.get().dependencies {
            implementation(libs.kotlin.logging.jvm)
            implementation(libs.logback)
        }
        androidMain.get().dependencies {
            implementation(libs.kotlin.logging.android)
            implementation(libs.slf4j.api)
        }


        val androidDeviceTest by getting {
            dependencies {
                implementation(libs.androidx.test.runner)
            }
        }


    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn("generateBreakIteratorKotlin")
    dependsOn("generateGB2312MappingKotlin")
}

tasks.matching { it.name == "prepareKotlinIdeaImport" }.configureEach {
    dependsOn("generateBreakIteratorKotlin")
    dependsOn("generateGB2312MappingKotlin")
}

tasks.matching { it.name == "sourcesJar" || it.name.endsWith("SourcesJar") }.configureEach {
    dependsOn("generateBreakIteratorKotlin")
    dependsOn("generateGB2312MappingKotlin")
}


// To enable hang detection, run ./gradlew commands with -PenableHangDetection=true
val enableHangDetection = providers
    .gradleProperty("enableHangDetection")
    .orElse(providers.systemProperty("enableHangDetection"))
    .map { it.equals("true", ignoreCase = true) }
    .orElse(false)

// Detect if configuration cache is requested; avoid registering global listeners when it's on
@Suppress("DEPRECATION")
val configurationCacheRequested = gradle.startParameter.isConfigurationCacheRequested

// BuildService-based hang detection for tests to keep configuration cache compatible
abstract class HangDetectionService : BuildService<BuildServiceParameters.None>, AutoCloseable {
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "hang-detector-tests").apply { isDaemon = true }
    }
    private val listeners: MutableMap<String, TestListener> = ConcurrentHashMap()
    private val pending: MutableMap<String, ScheduledFuture<*>> = ConcurrentHashMap()

    fun registerFor(task: Test, slowThresholdMs: Long) {
        val key = task.path
        listeners.computeIfAbsent(key) {
            val listener = object : TestListener {
                override fun beforeTest(descriptor: TestDescriptor) {
                    val id = "${descriptor.className} > ${descriptor.displayName}"
                    val fut = scheduler.schedule({
                        println("POTENTIAL HANG TEST ($slowThresholdMs ms threshold) $id")
                    }, slowThresholdMs, TimeUnit.MILLISECONDS)
                    pending[id] = fut
                }

                override fun afterTest(descriptor: TestDescriptor, result: TestResult) {
                    val id = "${descriptor.className} > ${descriptor.displayName}"
                    pending.remove(id)?.cancel(false)
                    val dur = result.endTime - result.startTime
                    if (dur >= slowThresholdMs) {
                        println("SLOW TEST (${dur} ms) $id")
                    }
                }

                override fun beforeSuite(suite: TestDescriptor) {}
                override fun afterSuite(suite: TestDescriptor, result: TestResult) {}
            }
            task.addTestListener(listener)
            listener
        }
    }

    fun unregisterFor(task: Test) {
        val key = task.path
        val listener = listeners.remove(key) ?: return
        task.removeTestListener(listener)
        // cancel any still-pending scheduled prints
        pending.values.forEach { it.cancel(false) }
        pending.clear()
    }

    override fun close() {
        try {
            scheduler.shutdownNow()
        } finally {
            pending.values.forEach { it.cancel(false) }
            pending.clear()
            listeners.clear()
        }
    }
}

// --- Low-noise, precise hang detection for tests (via BuildService) ---
tasks.withType<Test>().configureEach {
    // Keep Gradle output short; rely on our custom listener for hangs/slow tests.
    testLogging {
        events("failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
    }

    if (enableHangDetection.get()) {
        val hangService = gradle.sharedServices.registerIfAbsent(
            "hangDetectionService",
            HangDetectionService::class
        ) {}
        usesService(hangService)
        val slowThresholdMs = 60_000L // adjust as needed
        doFirst {
            hangService.get().registerFor(this as Test, slowThresholdMs)
        }
        doLast {
            hangService.get().unregisterFor(this as Test)
        }
    }
}

// --- Low-noise, precise hang detection for Gradle tasks ---
// Global Gradle listeners are not compatible with configuration cache at configuration time.
// Only register when configuration cache is NOT requested.
if (enableHangDetection.get() && !configurationCacheRequested) {
    val slowTaskThresholdMs = 120_000L // adjust as needed

    val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "hang-detector-tasks").apply { isDaemon = true }
    }
    val taskStart: MutableMap<String, Long> = ConcurrentHashMap()
    val taskHangFuture: MutableMap<String, ScheduledFuture<*>> = ConcurrentHashMap()

    @Suppress("DEPRECATION")
    gradle.addListener(object : TaskExecutionListener {
        override fun beforeExecute(task: Task) {
            val key = task.path
            taskStart[key] = System.currentTimeMillis()

            // Schedule a one-time "potential hang" message
            val future = scheduler.schedule({
                println("POTENTIAL HANG TASK ($slowTaskThresholdMs ms threshold) $key")
            }, slowTaskThresholdMs, TimeUnit.MILLISECONDS)
            taskHangFuture[key] = future
        }

        override fun afterExecute(task: Task, state: TaskState) {
            val key = task.path
            taskHangFuture.remove(key)?.cancel(false)

            val start = taskStart.remove(key) ?: return
            val dur = System.currentTimeMillis() - start

            if (dur >= slowTaskThresholdMs || state.failure != null) {
                val status = when {
                    state.failure != null -> "FAILED"
                    state.skipped -> "SKIPPED"
                    else -> "DONE"
                }
                println("SLOW TASK (${dur} ms) $key [$status]")
            }
        }
    })

    @Suppress("DEPRECATION")
    gradle.buildFinished {
        scheduler.shutdownNow()
    }
} else if (enableHangDetection.get() && configurationCacheRequested) {
    // Informative log so users know why task-level hang detection isn't active
    logger.info("Hang detection for Gradle tasks is disabled because configuration cache is requested.")
}
