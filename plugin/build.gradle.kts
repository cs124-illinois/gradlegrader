@file:Suppress("SpellCheckingInspection")

import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jmailen.gradle.kotlinter.tasks.ConfigurableKtLintTask
import java.io.StringWriter
import java.util.Properties

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("com.gradleup.shadow")
    id("org.jmailen.kotlinter")
}
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:2.2.20")
    implementation(gradleApi())
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    implementation("org.apache.commons:commons-text:1.14.0")
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.4.0.202509020913-r")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.20.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.1")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.8")
}
gradlePlugin {
    plugins {
        create("gradlegrader") {
            id = "org.cs124.gradlegrader"
            implementationClass = "edu.illinois.cs.cs125.gradlegrader.plugin.GradleGraderPlugin"
        }
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withJavadocJar()
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}
tasks.compileKotlin {
    dependsOn("createProperties")
}
tasks.register("createProperties") {
    dependsOn(tasks.processResources)
    doLast {
        val properties = Properties().also {
            it["version"] = project.version.toString()
        }
        File(projectDir, "src/main/resources/edu.illinois.cs.cs125.gradlegrader.plugin.version")
            .printWriter().use { printWriter ->
                printWriter.print(
                    StringWriter().also { properties.store(it, null) }.buffer.toString()
                        .lines().drop(1).joinToString(separator = "\n").trim()
                )
            }
    }
}
publishing {
    publications {
        create<MavenPublication>("lib") {
            artifactId = "lib"
            from(components["java"])
        }
        afterEvaluate {
            withType<MavenPublication> {
                pom {
                    name = "gradlegrader"
                    description = "Gradle grader plugin for CS 124."
                    url = "https://cs124.org"
                    licenses {
                        license {
                            name = "MIT License"
                            url = "https://opensource.org/license/mit/"
                        }
                    }
                    developers {
                        developer {
                            id = "gchallen"
                            name = "Geoffrey Challen"
                            email = "challen@illinois.edu"
                        }
                    }
                    scm {
                        connection = "scm:git:https://github.com/cs124-illinois/gradlegrader.git"
                        developerConnection = "scm:git:https://github.com/cs124-illinois/gradlegrader.git"
                        url = "https://github.com/cs124-illinois/gradlegrader"
                    }
                }
            }
        }
    }
}
signing {
    setRequired {
        gradle.taskGraph.allTasks.any { it.name.contains("ToSonatype") }
    }
    sign(publishing.publications)
}
tasks.withType<Sign>().configureEach {
    onlyIf {
        gradle.taskGraph.allTasks.any { it.name.contains("ToSonatype") }
    }
}
tasks.withType<AbstractPublishToMaven>().configureEach {
    dependsOn(tasks.withType<Sign>())
}
tasks.shadowJar {
    isZip64 = true
}
tasks.withType<ConfigurableKtLintTask> {
    exclude { it.file.path.contains("generated${File.separator}") }
}
