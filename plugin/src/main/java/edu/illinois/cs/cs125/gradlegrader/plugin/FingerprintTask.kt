package edu.illinois.cs.cs125.gradlegrader.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest

private const val md5Prefix = "// md5:"

fun File.retrieveFingerprint(): String? {
    val lines = readText().trimEnd().lines()
    return lines.find { it.startsWith(md5Prefix) }?.removePrefix(md5Prefix)?.trim()
}

fun File.linesToFingerprint(): List<String> {
    val lines = readText().trimEnd().lines()
    val hasFingerprint = lines.find { it.startsWith(md5Prefix) } != null
    if (hasFingerprint) {
        check(lines.last().startsWith(md5Prefix)) { "Fingerprint should be on the final non-blank line" }
    }
    return lines.filter { !it.startsWith(md5Prefix) }
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

fun String.addFingerprint(): String = MessageDigest.getInstance("MD5").let { md ->
    val filtered = lines().filter {
        !it.startsWith("// md5: ")
    }.joinToString("\n")
    return BigInteger(1, md.digest(filtered.toByteArray())).toString(16).padStart(32, '0')
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
