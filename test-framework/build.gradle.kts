import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

group = "org.gnit.lucenekmp"
version = "1.0.0"

kotlin {
    jvm()
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

    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":core"))
                implementation(libs.kotlin.test)
                implementation(libs.okio)
                implementation(libs.kotlinenvvar)
                implementation(libs.kotlinbignum)
                implementation(libs.kotlin.logging)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":core"))
                implementation(libs.kotlin.test)
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

android {
    namespace = "org.jetbrains.kotlinx.multiplatform.library.template"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
