plugins {
    kotlin("jvm") version "2.0.21" apply false
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jmailen.kotlinter") version "4.4.1" apply false
    id("com.github.ben-manes.versions") version "0.51.0"
    id("com.google.devtools.ksp").version("2.0.21-1.0.25") apply false
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

allprojects {
    group = "org.cs124.gradlegrader"
    version = "2024.10.0"

    repositories {
        mavenCentral()
        mavenLocal()
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
nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
}