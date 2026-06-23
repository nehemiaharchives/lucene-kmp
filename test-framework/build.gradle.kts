plugins {
    alias(libs.plugins.kotlinMultiplatform)
    //alias(libs.plugins.androidLibrary)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":core"))
                implementation(project(":codecs"))
                implementation(libs.kotlin.test)
                implementation(libs.kotlin.test.annotationsCommon)
                implementation(libs.okio)
                implementation(libs.kotlinenvvar)
                implementation(libs.kotlinbignum)
                implementation(libs.kotlinx.coroutines)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlin.logging)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":core"))
                implementation(libs.kotlin.test)
            }
        }


        jvmMain.get().dependencies {
            implementation(libs.kotlin.test.junit)
            implementation(libs.kotlin.logging.jvm)
            implementation(libs.logback)
        }
        androidMain.get().dependencies {
            implementation(libs.kotlin.test.junit)
            implementation(libs.kotlin.logging.android)
            implementation(libs.slf4j.api)
        }


    }
}
