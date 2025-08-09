import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.kover)
}

group = "org.gnit.lucenekmp"
version = "1.0.0"

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
                implementation(project(":core"))

                // TODO add following after these modules
                // implementation(project(":queries"))
                // implementation(project(":sandbox"))

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


mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "library", version.toString())

    pom {
        name = "My library"
        description = "A library."
        inceptionYear = "2024"
        url = "https://github.com/kotlin/multiplatform-library-template/"
        licenses {
            license {
                name = "XXX"
                url = "YYY"
                distribution = "ZZZ"
            }
        }
        developers {
            developer {
                id = "XXX"
                name = "YYY"
                url = "ZZZ"
            }
        }
        scm {
            url = "XXX"
            connection = "YYY"
            developerConnection = "ZZZ"
        }
    }
}
