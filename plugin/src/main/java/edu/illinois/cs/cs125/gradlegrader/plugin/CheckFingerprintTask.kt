package edu.illinois.cs.cs125.gradlegrader.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import java.io.File

fun File.checkFingerprint(base: File) {
    val filename = relativeTo(base).path

    val fileFingerprint = retrieveFingerprint() ?: throw GradleException("Could not find fingerprint for file $filename")
    val contentFingerprint = fingerprint()
    if (fileFingerprint != contentFingerprint) {
        throw GradleException(
            "Fingerprint mismatch for test file $filename. " +
                "Undo your changes, restore from Git, or download again.",
        )
    }
}

abstract class CheckFingerprintTask : DefaultTask() {
    init {
        group = "Verification"
        description = "Check test suite fingerprints."
    }

    @InputFiles
    val inputFiles: FileTree = project.fileTree("src/test").also {
        it.include("**/*Test.java", "**/*Test.kt")
    }

    @TaskAction
    fun check() {
        val base = project.rootDir
        inputFiles.forEach {
            it.checkFingerprint(base)
        }
    }
}
