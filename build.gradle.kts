plugins {
    kotlin("jvm") version "2.2.20" apply false
    id("com.gradleup.shadow") version "8.3.8"
    id("org.jmailen.kotlinter") version "5.2.0" apply false
    id("com.github.ben-manes.versions") version "0.53.0"
    id("com.google.devtools.ksp").version("2.2.20-2.0.3") apply false
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

allprojects {
    group = "org.cs124.gradlegrader"
    version = "2025.10.0"

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
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
        }
    }
}
