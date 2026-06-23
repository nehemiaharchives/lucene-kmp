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
    android {
        packaging {
            resources {
                excludes += "META-INF/INDEX.LIST"
            }
        }
    }

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


        jvmMain.get().dependencies {
            implementation(libs.logback)
        }


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
