@file:Suppress("UnstableApiUsage")

package edu.illinois.cs.cs125.gradlegrader.plugin

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.gson.Gson
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.nio.file.Files

/**
 * The gradlegrader Gradle plugin.
 */
@Suppress("unused")
class GradleGraderPlugin : Plugin<Project> {

    /**
     * Applies the plugin to the project.
     * @param project the Gradle project to apply to
     */
    override fun apply(project: Project) {
        val config = project.extensions.create("gradlegrader", GradePolicyExtension::class.java)
        val exitManager = ExitManager(config)

        val fingerprintingError = try {
            project.checkFingerprints()
            ""
        } catch (e: Exception) {
            e.message ?: "Unknown fingerprinting error"
        }

        fun findSubprojects(): List<Project> = config.subprojects ?: listOf(project)

        val javaCompileTasks = mutableSetOf<JavaCompile>()
        val kotlinCompileTasks = mutableSetOf<Task>()

        val testTasks = mutableMapOf<Project, Test>()
        var currentCheckpoint: String? = null

        val checkstyleTask = project.tasks.register("relentlessCheckstyle", RelentlessCheckstyle::class.java).get()
        val detektTask = project.tasks.register("ourDetekt", Detekt::class.java).get()

        val gradeTask = project.tasks.register("grade").get()
        val scoreTask: ScoreTask = project.tasks.register("score", ScoreTask::class.java).get()
        scoreTask.fingerprintingError = fingerprintingError
        scoreTask.mustRunAfter(gradeTask)
        gradeTask.finalizedBy(scoreTask)

        project.tasks.register("fingerprintTests", FingerprintTask::class.java)
        project.tasks.register("checkTestFingerprints", CheckFingerprintTask::class.java).get()

        val gitRepo = try {
            val ceiling =
                project.rootProject.projectDir.parentFile // Go an extra level up to work around a JGit bug
            FileRepositoryBuilder().setMustExist(true).addCeilingDirectory(ceiling)
                .findGitDir(project.projectDir).build()
        } catch (e: Exception) {
            null
        }

        val untrackedFiles = gitRepo?.let {
            Git.open(it.workTree).status().call().untracked
        } ?: setOf()
        scoreTask.untrackedFiles = untrackedFiles

        var uncommittedChanges: Boolean? = null
        fun checkForUncommittedChanges(currentCheckpoint: String): Boolean {
            if (uncommittedChanges != null) {
                return uncommittedChanges!!
            }
            uncommittedChanges = false
            if (config.vcs.git) {
                if (gitRepo == null) {
                    exitManager.fail("Grader Git integration is enabled but the project isn't a Git repository.")
                }
                scoreTask.gitConfig = gitRepo.config
                val lastCommit = gitRepo.resolve(Constants.HEAD).name
                scoreTask.lastCommitId = lastCommit
                if (config.vcs.requireCommit) {
                    var scoreInfo = VcsScoreInfo(listOf())
                    try {
                        val loadedInfo = Gson().fromJson(
                            project.rootProject.file(".score.json").readText(),
                            VcsScoreInfo::class.java,
                        )
                        @Suppress("SENSELESS_COMPARISON") // Possible for checkpoints to be null if loaded by Gson
                        if (loadedInfo.checkpoints != null) {
                            scoreInfo = loadedInfo
                        }
                    } catch (ignored: Exception) {
                    }
                    val checkpointScoreInfo =
                        scoreInfo.getCheckpointInfo(currentCheckpoint) ?: VcsCheckpointScoreInfo(currentCheckpoint)
                    val status = Git.open(gitRepo.workTree).status().call()
                    val clean =
                        (status.added.size + status.changed.size + status.removed.size + status.modified.size + status.missing.size) == 0
                    if (checkpointScoreInfo.increased && checkpointScoreInfo.lastSeenCommit == lastCommit && !clean) {
                        uncommittedChanges = true
                    }
                    scoreTask.scoreInfo = scoreInfo
                    scoreTask.repoIsClean = clean
                }
            }
            return uncommittedChanges
        }

        fun checkContributorsFile(): List<String>? {
            // Check contributors file
            if (!config.identification.enabled) {
                return null
            }
            val txtFile = config.identification.txtFile!!
            if (!txtFile.exists()) {
                exitManager.fail("Missing contributor identification file: ${txtFile.absolutePath}")
            }
            val partners = Files.readAllLines(txtFile.toPath()).filter { it.isNotBlank() }.filterNotNull()
            if (!config.identification.countLimit.isSatisfiedBy(partners.size)) {
                if (partners.isEmpty()) {
                    exitManager.fail(
                        config.identification.message
                            ?: "Identification file is empty: ${txtFile.absolutePath}",
                    )
                } else {
                    exitManager.fail(
                        config.identification.message
                            ?: "Invalid number of contributors (${partners.size}) in identification file: ${txtFile.absolutePath}",
                    )
                }
            }
            partners.forEach {
                if (!config.identification.validate.isSatisfiedBy(it)) {
                    exitManager.fail(config.identification.message ?: "Invalid contributor format: $it")
                }
            }
            return partners
        }

        val reconfTask = project.tasks.register("prepareForGrading").get()
        reconfTask.finalizedBy(scoreTask)
        reconfTask.doLast {
            if (!config.ignoreFingerprintMismatch && fingerprintingError.isNotEmpty()) {
                return@doLast
            }
            if (config.vcs.git && untrackedFiles.isNotEmpty()) {
                exitManager.fail(
                    "The autograder will not run you add all files to your repository. " +
                        "Currently missing: ${untrackedFiles.joinToString(", ")}.",
                )
            }
            if (checkForUncommittedChanges(currentCheckpoint!!)) {
                exitManager.fail("The autograder will not run until you commit the changes that increased your score.")
            }

            checkContributorsFile()?.let { partners ->
                scoreTask.contributors = partners
            }

            // Check projects' test tasks
            findSubprojects().forEach { subproject ->
                if (!testTasks.containsKey(subproject)) {
                    exitManager.fail("Couldn't find a test task for project ${subproject.path}")
                }
            }

            // Configure checkstyle
            if (config.checkstyle.enabled) {
                val checkstyleConfig = project.extensions.getByType(CheckstyleExtension::class.java)
                checkstyleConfig.reportsDir = project.file("build/reports/checkstyle")
                checkstyleTask.ignoreFailures = true
                checkstyleTask.outputs.upToDateWhen { false }
                checkstyleTask.source(findSubprojects().map { "${it.projectDir}/src/main" })
                checkstyleTask.setIncludes(config.checkstyle.include)
                checkstyleTask.setExcludes(config.checkstyle.exclude)
                checkstyleTask.configFile =
                    checkstyleConfig.configFile ?: exitManager.fail("checkstyle.configFile not specified")
                checkstyleTask.classpath = project.files()
                scoreTask.listenTo(checkstyleTask)
            }

            if (config.detekt.enabled) {
                val detektConfig = project.extensions.getByType(DetektExtension::class.java)
                detektTask.ignoreFailures = true
                detektTask.outputs.upToDateWhen { false }
                detektTask.source(detektConfig.source)
                detektTask.buildUponDefaultConfig = detektConfig.buildUponDefaultConfig
                detektTask.config.setFrom(detektConfig.config)
            }

            // Configure the test tasks
            testTasks.values.forEach { testTask ->
                scoreTask.listenTo(testTask)
                @Suppress("SpellCheckingInspection")
                if (!project.hasProperty("grade.ignoreproperties")) {
                    config.systemProperties.forEach { (prop, value) ->
                        testTask.systemProperty(prop, value)
                    }
                }
                @Suppress("SpellCheckingInspection")
                if (project.hasProperty("grade.testfilter")) {
                    testTask.setTestNameIncludePatterns(mutableListOf(project.property("grade.testfilter") as String))
                    testTask.filter.isFailOnNoMatchingTests = false
                } else if (currentCheckpoint != null) {
                    config.checkpointing.testConfigureAction.accept(currentCheckpoint!!, testTask)
                }
                testTask.setProperty("ignoreFailures", true)
                testTask.outputs.upToDateWhen { false }
                scoreTask.gatherTestInfo(testTask)
                // Configure test logging to show more information
                testTask.testLogging.apply {
                    exceptionFormat = TestExceptionFormat.FULL
                }
            }

            // Configure compilation tasks
            javaCompileTasks.forEach {
                scoreTask.listenTo(it)
                it.options.isFailOnError = false
                it.outputs.upToDateWhen { false }
            }
            kotlinCompileTasks.forEach {
                scoreTask.listenTo(it)
                it.outputs.upToDateWhen { false }
            }
        }

        // Logic that depends on all projects having been evaluated
        fun onAllProjectsReady() {
            if (!config.ignoreFingerprintMismatch && fingerprintingError.isNotEmpty()) {
                return
            }
            if (config.vcs.git && untrackedFiles.isNotEmpty()) {
                return
            }
            if (checkForUncommittedChanges(currentCheckpoint!!)) {
                return
            }
            try {
                checkContributorsFile()
            } catch (e: Exception) {
                return
            }
            val cleanTasks = findSubprojects().map { it.tasks.getByName("clean") }
            if (config.forceClean) {
                // Require a clean first
                gradeTask.dependsOn(cleanTasks)
            }

            // Depend on checkstyle
            if (config.checkstyle.enabled) {
                checkstyleTask.mustRunAfter(reconfTask)
                checkstyleTask.mustRunAfter(cleanTasks)
                gradeTask.dependsOn(checkstyleTask)
                scoreTask.gatherCheckstyleInfo(checkstyleTask)
            }

            if (config.detekt.enabled) {
                detektTask.mustRunAfter(reconfTask)
                detektTask.mustRunAfter(cleanTasks)
                gradeTask.dependsOn(detektTask)
                scoreTask.gatherDetektInfo(detektTask)
            }

            // Get subproject tasks
            findSubprojects().forEach { subproject ->
                // Clean before compile
                subproject.tasks.withType(JavaCompile::class.java) { compile ->
                    compile.mustRunAfter(cleanTasks)
                    compile.mustRunAfter(reconfTask)
                    javaCompileTasks.add(compile)
                }
                subproject.tasks.withType(KotlinCompilationTask::class.java) { compile ->
                    compile.mustRunAfter(cleanTasks)
                    compile.mustRunAfter(reconfTask)
                    kotlinCompileTasks.add(compile)
                }
                // Depend on tests
                subproject.tasks.withType(Test::class.java) { test ->
                    if (test.name in setOf("test", "testDebugUnitTest")) {
                        testTasks[subproject] = test
                        test.mustRunAfter(cleanTasks)
                        test.mustRunAfter(reconfTask)
                        gradeTask.dependsOn(test)
                    }
                }
            }
        }

        // Finish setup once all projects have been evaluated and tasks have been created
        project.afterEvaluate {
            project.configurations.getByName("testImplementation").dependencies.find { dependency ->
                (dependency.group == "org.cs124" && dependency.name == "gradlegrader") || (dependency.group == "org.cs124.gradlegrader")
            }?.let {
                error("Found explicit gradlegrader library dependency. Please remove it, since it is automatically added by the plugin.")
            }
            project.dependencies.add("testImplementation", project.dependencies.create("org.cs124.gradlegrader:lib:$VERSION"))

            if (config.forceClean) {
                reconfTask.dependsOn(
                    project.tasks.register("clearBuildDir", Delete::class.java) { delTask ->
                        delTask.delete = setOf(project.layout.buildDirectory.get())
                    }.get(),
                )
            }
            scoreTask.dependsOn(reconfTask)

            currentCheckpoint = (
                try {
                    project.property("checkpoint")?.toString()
                } catch (_: Exception) {
                    null
                }
                    ?: config.checkpointing.yamlFile?.let { file ->
                        val configLoader = ObjectMapper(YAMLFactory()).also { it.registerKotlinModule() }
                        configLoader.readValue<CheckpointConfig>(file).checkpoint
                    }
                )?.also {
                scoreTask.currentCheckpoint = it
            }

            val evalPending = findSubprojects().toMutableList()
            evalPending.remove(project)
            if (evalPending.isEmpty()) {
                onAllProjectsReady()
            } else {
                evalPending.toList().forEach { subproject ->
                    subproject.afterEvaluate {
                        evalPending.remove(subproject)
                        if (evalPending.isEmpty()) {
                            onAllProjectsReady()
                        }
                    }
                }
            }
        }
    }
}
