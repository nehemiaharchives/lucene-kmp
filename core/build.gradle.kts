import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import kotlin.apply
import java.util.concurrent.TimeUnit

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.kover)
}

kotlin {
    jvm()
    //jvmToolchain(23) // we run build on jdk 24, so getting INFO saying "Kotlin does not yet support 24 JDK target, falling back to Kotlin JVM_23 JVM target"
    androidTarget {
        publishLibraryVariants("release")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    iosArm64()
    iosX64()
    iosSimulatorArm64()

    // build target only for developer convenience
    // when you are in linux X64 machine,
    // run ./gradlew core:compileKotlinLinuxX64 to check Kotlin/Native compilation error common to ios and linux.
    linuxX64()

    sourceSets {
        val commonMain by getting {
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
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.okio.fakefilesystem)
                implementation(project(":test-framework"))
            }
        }

        // below is additional source set configurations other than default hierarchy

        // shared source for jvm and android
        val jvmAndroidMain by creating {
            dependsOn(commonMain)
            // dependencies which are used both by jvm and android will be here
            dependencies {
                implementation(libs.logback)
            }
        }
        jvmMain.get().dependsOn(jvmAndroidMain)
        androidMain.get().dependsOn(jvmAndroidMain)

        val jvmAndroidTest by creating {
            dependsOn(commonTest)
            // test dependencies which are used both by jvm and android will be here
        }
        jvmTest.get().dependsOn(jvmAndroidTest)
        androidUnitTest.get().dependsOn(jvmAndroidTest)

        // shared source for ios and linux
        val nativeMain by creating {

            compilerOptions.suppressWarnings = true

            dependsOn(commonMain)
            // dependencies which are used both by ios and linux will be here
        }
        iosArm64Main.get().dependsOn(nativeMain)
        iosX64Main.get().dependsOn(nativeMain)
        iosSimulatorArm64Main.get().dependsOn(nativeMain)

        linuxX64Main.get().dependsOn(nativeMain)

        val nativeTest by creating {
            dependsOn(commonTest)
            // test dependencies which are used both by ios and linux will be here
        }
        iosArm64Test.get().dependsOn(nativeTest)
        iosX64Test.get().dependsOn(nativeTest)
        iosSimulatorArm64Test.get().dependsOn(nativeTest)

        linuxX64Test.get().dependsOn(nativeTest)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    compilerOptions{
        optIn.addAll(
            "kotlin.ExperimentalStdlibApi",
        )
        //suppressWarnings = true
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
        )
    }
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
