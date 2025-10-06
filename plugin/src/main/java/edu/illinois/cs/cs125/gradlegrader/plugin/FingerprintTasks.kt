package edu.illinois.cs.cs125.gradlegrader.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest

private const val MD5PREFIX = "// md5:"

private fun Project.fingerprintFiles(policy: FingerprintPolicy): FileTree {
    if (policy.patterns.isEmpty()) {
        return files().asFileTree
    }
    return fileTree(rootDir).also {
        it.include(policy.patterns)
    }
}

fun Project.checkFingerprints(policy: FingerprintPolicy, inputFiles: List<File> = fingerprintFiles(policy).toList()) = inputFiles.forEach { file -> file.checkFingerprint(rootDir) }

fun File.checkFingerprint(base: File) {
    val filename = relativeTo(base).path

    val fileFingerprint =
        retrieveFingerprint() ?: throw GradleException("Could not find fingerprint for file $filename")
    val contentFingerprint = fingerprint()
    if (fileFingerprint != contentFingerprint) {
        throw GradleException(
            "Fingerprint mismatch for test file $filename.\nUndo your changes, restore from Git, or download again.",
        )
    }
}

fun File.retrieveFingerprint(): String? {
    val lines = readText().trimEnd().lines()
    return lines.find { it.startsWith(MD5PREFIX) }?.removePrefix(MD5PREFIX)?.trim()?.split(" ")?.first()
}

fun File.linesToFingerprint(): List<String> {
    val lines = readText().trimEnd().lines()
    val hasFingerprint = lines.find { it.startsWith(MD5PREFIX) } != null
    if (hasFingerprint) {
        check(lines.last().startsWith(MD5PREFIX)) { "Fingerprint should be on the final non-blank line" }
    }
    return lines.filter { !it.startsWith(MD5PREFIX) }
}

fun File.addFingerprint() {
    val linesToFingerprint = linesToFingerprint()
    val fingerPrint = fingerprint()
    val fingerprintedLines = linesToFingerprint + "// md5: $fingerPrint // DO NOT REMOVE THIS LINE\n"
    writeText(fingerprintedLines.joinToString("\n"))
}

fun File.fingerprint(): String {
    val linesToFingerprint = linesToFingerprint()
    return BigInteger(
        1,
        MessageDigest.getInstance("MD5").digest(linesToFingerprint.joinToString("\n").toByteArray()),
    ).toString(16).padStart(32, '0')
}

abstract class FingerprintTask : DefaultTask() {
    @Internal
    lateinit var policy: FingerprintPolicy

    init {
        group = "Publishing"
        description = "Fingerprint test suites."
    }

    @InputFiles
    fun getInputFiles(): FileTree = project.fingerprintFiles(policy)

    @TaskAction
    fun fingerprint() {
        getInputFiles().sortedBy { it.name }.forEach {
            it.addFingerprint()
            println("Fingerprinted ${it.name}")
        }
    }
}

abstract class CheckFingerprintTask : DefaultTask() {
    @Internal
    lateinit var policy: FingerprintPolicy

    init {
        group = "Verification"
        description = "Check test suite fingerprints."
    }

    @InputFiles
    fun getInputFiles(): FileTree = project.fingerprintFiles(policy)

    @TaskAction
    fun check() = project.checkFingerprints(policy, getInputFiles().toList())
}
