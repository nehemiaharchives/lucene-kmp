import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.kover)
}

kotlin {
    jvm()

    androidLibrary {
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }
    }

    iosArm64()
    iosX64()
    iosSimulatorArm64()

    macosX64()
    macosArm64()
    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":core"))

                implementation(libs.okio)
                implementation(libs.kotlinenvvar)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlin.logging)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(project(":core"))
                implementation(project(":test-framework"))
                implementation(libs.kotlin.test)
            }
        }

        val jvmAndroidMain by creating {
            dependsOn(commonMain)
        }
        jvmMain.get().dependsOn(jvmAndroidMain)
        jvmMain.get().dependencies {
            implementation(libs.logback)
        }
        androidMain.get().dependsOn(jvmAndroidMain)

        val jvmAndroidTest by creating {
            dependsOn(commonTest)
        }
        jvmTest.get().dependsOn(jvmAndroidTest)
        getByName("androidHostTest").dependsOn(jvmAndroidTest)

        val nativeMain by creating {
            compilerOptions.suppressWarnings = true
            dependsOn(commonMain)
        }
        iosArm64Main.get().dependsOn(nativeMain)
        iosX64Main.get().dependsOn(nativeMain)
        iosSimulatorArm64Main.get().dependsOn(nativeMain)

        macosArm64Main.get().dependsOn(nativeMain)
        macosX64Main.get().dependsOn(nativeMain)
        linuxX64Main.get().dependsOn(nativeMain)

        val nativeTest by creating {
            dependsOn(commonTest)
        }
        iosArm64Test.get().dependsOn(nativeTest)
        iosX64Test.get().dependsOn(nativeTest)
        iosSimulatorArm64Test.get().dependsOn(nativeTest)

        macosArm64Test.get().dependsOn(nativeTest)
        macosX64Test.get().dependsOn(nativeTest)
        linuxX64Test.get().dependsOn(nativeTest)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    compilerOptions{
        optIn.addAll(
            "kotlin.ExperimentalStdlibApi",
        )
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
        )
    }
}
