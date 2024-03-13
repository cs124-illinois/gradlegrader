import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
    signing
    id("com.github.johnrengelman.shadow")
    id("org.jmailen.kotlinter")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:1.9.23")
    implementation(gradleApi())
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    implementation("org.apache.commons:commons-text:1.11.0")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.9.0.202403050737-r")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.0")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.5")
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
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withJavadocJar()
    withSourcesJar()
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
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
                    signing {
                        sign(this@publications)
                    }
                }
            }
        }
    }
}
tasks.withType<AbstractPublishToMaven>().configureEach {
    val signingTasks = tasks.withType<Sign>()
    mustRunAfter(signingTasks)
}