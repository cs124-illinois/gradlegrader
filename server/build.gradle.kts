import org.gradle.kotlin.dsl.withType
import org.jmailen.gradle.kotlinter.tasks.ConfigurableKtLintTask
import java.io.File
import java.io.StringWriter
import java.util.Properties

plugins {
    kotlin("jvm")
    application
    id("com.gradleup.shadow")
    id("org.jmailen.kotlinter")
    id("com.google.devtools.ksp")
}
dependencies {
    val ktorVersion = "3.3.1"
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("org.mongodb:mongodb-driver:3.12.14")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("org.cs124:ktor-moshi:2025.8.0")
    implementation("ch.qos.logback:logback-classic:1.5.20")
    implementation("com.uchuhimo:konf-core:1.1.2")
    implementation("com.uchuhimo:konf-yaml:1.1.2")
    implementation("io.github.microutils:kotlin-logging:3.0.5")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-forwarded-header:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
}
application {
    mainClass.set("edu.illinois.cs.cs125.gradlegrader.server.MainKt")
}
val dockerName = "cs124/gradlegrader"
tasks.register<Copy>("dockerCopyJar") {
    from(tasks["shadowJar"].outputs)
    into(layout.buildDirectory.dir("docker"))
}
tasks.register<Copy>("dockerCopyDockerfile") {
    from("${projectDir}/Dockerfile")
    into(layout.buildDirectory.dir("docker"))
}
tasks.register<Exec>("dockerBuild") {
    dependsOn("dockerCopyJar", "dockerCopyDockerfile")
    workingDir(layout.buildDirectory.dir("docker"))
    environment("DOCKER_BUILDKIT", "1")
    commandLine(
        ("/usr/local/bin/docker build . " +
            "-t ${dockerName}:latest " +
            "-t ${dockerName}:${project.version}").split(" ")
    )
}
tasks.register<Exec>("dockerPush") {
    dependsOn("dockerCopyJar", "dockerCopyDockerfile")
    workingDir(layout.buildDirectory.dir("docker"))
    commandLine(
        ("/usr/local/bin/docker buildx build . --platform=linux/amd64,linux/arm64/v8 " +
            "--tag ${dockerName}:latest " +
            "--tag ${dockerName}:${project.version} --push").split(" ")
    )
}
tasks.register("createProperties") {
    doLast {
        val properties = Properties().also {
            it["version"] = project.version.toString()
        }
        File(projectDir, "src/main/resources/edu.illinois.cs.cs125.gradlegrader.server.version")
            .printWriter().use { printWriter ->
                printWriter.print(
                    StringWriter().also { properties.store(it, null) }.buffer.toString()
                        .lines().drop(1).joinToString(separator = "\n").trim()
                )
            }
    }
}
tasks.processResources {
    dependsOn("createProperties")
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
tasks.withType<ConfigurableKtLintTask> {
    exclude { it.file.path.contains("generated${File.separator}") }
}
