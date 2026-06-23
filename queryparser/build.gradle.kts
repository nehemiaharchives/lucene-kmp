plugins {
    alias(libs.plugins.kotlinMultiplatform)
    //alias(libs.plugins.androidLibrary)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.kover)
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":core"))
                implementation(project(":queries"))

                implementation(libs.okio)
                implementation(libs.kotlinenvvar)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlin.logging)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(project(":core"))
                implementation(project(":queries"))
                implementation(project(":test-framework"))
                implementation(libs.kotlin.test)
            }
        }


        jvmMain.get().dependencies {
            implementation(libs.logback)
        }


    }
}
