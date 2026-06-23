plugins {
    alias(libs.plugins.kotlinMultiplatform)
    //alias(libs.plugins.androidLibrary)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.kover)
}

apply(from = rootProject.file("gradle/generateHornData.gradle.kts"))

kotlin {
    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(layout.buildDirectory.dir("generated/horn/kotlin"))
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
                implementation(project(":analysis:common"))
                implementation(project(":test-framework"))
                implementation(libs.kotlin.test)
            }
        }


        jvmMain.get().dependencies {
            implementation(libs.logback)
        }


    }
}


tasks.matching { it.name == "compileAndroidMain" || it.name.startsWith("compileKotlin") }.configureEach {
    dependsOn("generateHornDataKotlin")
}

tasks.matching { it.name == "prepareKotlinIdeaImport" }.configureEach {
    dependsOn("generateHornDataKotlin")
}

tasks.matching { it.name == "sourcesJar" || it.name.endsWith("SourcesJar") }.configureEach {
    dependsOn("generateHornDataKotlin")
}
