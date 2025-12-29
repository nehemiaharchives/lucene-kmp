plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

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

// Don't fail this module's build if no tests are discovered (it has helpers but no test classes)
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    // Avoid failure when there are test sources but no tests found.
    filter {
        isFailOnNoMatchingTests = false
    }
}
