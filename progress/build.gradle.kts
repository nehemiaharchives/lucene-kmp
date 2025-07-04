plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

group = "org.gnit.lucenekmp"
version = "1.0.0"

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation("io.github.classgraph:classgraph:4.8.180")
                implementation("com.github.ajalt.mordant:mordant-jvm:3.0.2")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
