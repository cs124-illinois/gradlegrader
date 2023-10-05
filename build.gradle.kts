plugins {
    kotlin("jvm") version "1.9.10" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jmailen.kotlinter") version "3.16.0" apply false
    id("com.github.ben-manes.versions") version "0.48.0"
    id("com.google.devtools.ksp").version("1.9.10-1.0.13") apply false
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
    rejectVersionIf {
        listOf("alpha", "beta", "rc", "cr", "m", "preview", "b", "ea", "eap", "pr").any { qualifier ->
            candidate.version.matches(Regex("(?i).*[.-]$qualifier[.\\d-+]*"))
        }
    }
    gradleReleaseChannel = "current"
}
