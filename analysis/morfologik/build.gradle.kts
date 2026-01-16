import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    //alias(libs.plugins.androidLibrary)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.kover)
}

apply(from = rootProject.file("gradle/generatePolishDicData.gradle.kts"))
apply(from = rootProject.file("gradle/generateUkrainianDicData.gradle.kts"))

kotlin {
    jvm()
    //jvmToolchain(23) // we run build on jdk 24, so getting INFO saying "Kotlin does not yet support 24 JDK target, falling back to Kotlin JVM_23 JVM target"
    /*androidTarget {
        publishLibraryVariants("release")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }*/

    androidLibrary {
        //withJava() // enable java compilation support
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }
    }

    iosArm64()
    iosX64()
    iosSimulatorArm64()

    macosX64() // intel mac
    macosArm64() // m1/2/3/4 mac
    linuxX64() // when you are in linux X64 machine, run ./gradlew core:compileKotlinLinuxX64 to check Kotlin/Native compilation error common to ios, macos and linux for developer convenience.

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated/morfologik/polish/kotlin"))
            kotlin.srcDir(layout.buildDirectory.dir("generated/morfologik/ukrainian/kotlin"))
            dependencies {
                implementation(project(":core"))
                implementation(project(":analysis:common"))
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
        //androidUnitTest.get().dependsOn(jvmAndroidTest)

        // shared source for ios and linux
        val nativeMain by creating {

            compilerOptions.suppressWarnings = true

            dependsOn(commonMain)
            // dependencies which are used both by ios and linux will be here
        }
        iosArm64Main.get().dependsOn(nativeMain)
        iosX64Main.get().dependsOn(nativeMain)
        iosSimulatorArm64Main.get().dependsOn(nativeMain)

        macosArm64Main.get().dependsOn(nativeMain)
        macosX64Main.get().dependsOn(nativeMain)
        linuxX64Main.get().dependsOn(nativeMain)

        val nativeTest by creating {
            dependsOn(commonTest)
            // test dependencies which are used both by ios and linux will be here
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
        //suppressWarnings = true
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
        )
    }
}

tasks.matching { it.name.startsWith("compileKotlin") }.configureEach {
    dependsOn("generatePolishDictionaryKotlin")
}

tasks.matching { it.name == "prepareKotlinIdeaImport" }.configureEach {
    dependsOn("generatePolishDictionaryKotlin")
}

tasks.matching { it.name == "sourcesJar" || it.name.endsWith("SourcesJar") }.configureEach {
    dependsOn("generatePolishDictionaryKotlin")
    dependsOn("generateUkrainianDictionaryKotlin")
}
