plugins {
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply  false
    alias(libs.plugins.vanniktech.mavenPublish) apply false
}

subprojects {
    group = providers.gradleProperty("group").get()
    version = providers.gradleProperty("version").get()

    // Centralized publication configuration for modules that apply the Vanniktech Maven Publish plugin
    pluginManager.withPlugin("com.vanniktech.maven.publish") {
        extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension>("mavenPublishing") {
            publishToMavenCentral()
            signAllPublications()

            // Use module name as artifactId
            coordinates(group.toString(), project.name, version.toString())

            pom {
                name = "lucene-kmp (module: ${project.name})"
                description = "Kotlin Multiplatform port of Apache Lucene (module: ${project.name})"
                inceptionYear = "2025"
                url = "https://github.com/nehemiaharchives/lucene-kmp"

                issueManagement {
                    system = "GitHub"
                    url = "https://github.com/nehemiaharchives/lucene-kmp/issues"
                }

                ciManagement {
                    system = "GitHub Actions"
                    url = "https://github.com/nehemiaharchives/lucene-kmp/actions"
                }

                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                        distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }

                developers {
                    developer {
                        id = "nehemiaharchives"
                        name = "Hokuto Joel Ide"
                        url = "https://github.com/nehemiaharchives"
                    }
                }

                scm {
                    url = "https://github.com/nehemiaharchives/lucene-kmp"
                    connection = "scm:git:git://github.com/nehemiaharchives/lucene-kmp.git"
                    developerConnection = "scm:git:ssh://git@github.com/nehemiaharchives/lucene-kmp.git"
                }
            }
        }
    }
}
