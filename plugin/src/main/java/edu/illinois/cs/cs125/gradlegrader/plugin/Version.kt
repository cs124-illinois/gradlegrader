package edu.illinois.cs.cs125.gradlegrader.plugin

import java.util.Properties

val VERSION: String = Properties().also {
    it.load((object {}).javaClass.getResourceAsStream("/edu.illinois.cs.cs125.gradlegrader.plugin.version"))
}.getProperty("version")
