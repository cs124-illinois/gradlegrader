plugins {
    kotlin("jvm") version "1.9.0" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jmailen.kotlinter") version "3.15.0" apply false
    id("com.github.ben-manes.versions") version "0.47.0"
    id("com.google.devtools.ksp").version("1.9.0-1.0.11") apply false
}
allprojects {
    repositories {
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
subprojects {
    tasks.withType<Test> {
        enableAssertions = true
    }
}
tasks.dependencyUpdates {
    resolutionStrategy {
        componentSelection {
            all {
                if (listOf("alpha", "beta", "rc", "cr", "m", "preview", "b", "ea", "eap", "pr").any { qualifier ->
                    candidate.version.matches(Regex("(?i).*[.-]$qualifier[.\\d-+]*"))
                }) {
                    reject("Release candidate")
                }
            }
        }
    }
    gradleReleaseChannel = "current"
}
