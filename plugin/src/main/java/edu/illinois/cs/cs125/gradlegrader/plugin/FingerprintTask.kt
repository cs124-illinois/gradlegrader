package edu.illinois.cs.cs125.gradlegrader.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest

private const val MD5PREFIX = "// md5:"

fun File.retrieveFingerprint(): String? {
    val lines = readText().trimEnd().lines()
    return lines.find { it.startsWith(MD5PREFIX) }?.removePrefix(MD5PREFIX)?.trim()
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
    val fingerprintedLines = linesToFingerprint + "// md5: $fingerPrint\n"
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
    init {
        group = "Publishing"
        description = "Fingerprint test suites."
    }

    @InputFiles
    val inputFiles: FileTree = project.fileTree("src/test").also {
        it.include("**/*Test.java", "**/*Test.kt")
    }

    @TaskAction
    fun fingerprint() {
        inputFiles.sortedBy { it.name }.forEach {
            it.addFingerprint()
            println("Fingerprinted ${it.name}")
        }
    }
}
