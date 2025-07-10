# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GradleGrader is a Gradle plugin system for autograding Java-based assignments. It consists of two main components:

1. **Plugin Module** (`plugin/`): A Gradle plugin that provides autograding capabilities
2. **Server Module** (`server/`): A web server for collecting and storing grading reports

## Versioning

- The project uses date-based versioning, with the current year, the current month, followed by a minor version number.

## Build Commands

### Main Build Commands
- `./gradlew build` - Build all modules
- `./gradlew clean` - Clean build artifacts
- `./gradlew publishToMavenLocal` - Publish to local Maven repository
- `./gradlew publishToSonatype` - Publish to Sonatype repository
- `./gradlew shadowJar` - Create shadow JAR for distribution

### Plugin Module (`plugin/`)
- `./gradlew :plugin:build` - Build the plugin
- `./gradlew :plugin:shadowJar` - Create plugin shadow JAR
- `./gradlew :plugin:publishToMavenLocal` - Publish plugin to local Maven

### Server Module (`server/`)
- `./gradlew :server:build` - Build the server
- `./gradlew :server:run` - Run the server locally
- `./gradlew :server:shadowJar` - Create server shadow JAR
- `./gradlew :server:dockerBuild` - Build Docker image
- `./gradlew :server:dockerPush` - Build and push Docker image

## Code Quality and Linting

- `./gradlew kotlinterCheck` - Check Kotlin code style
- `./gradlew kotlinterFormat` - Format Kotlin code
- `./gradlew dependencyUpdates` - Check for dependency updates
- Use the Gradle check task to validate the project, not the build task.

## Architecture

### Plugin Architecture
The plugin (`plugin/`) is built in Kotlin and provides:
- **GradleGraderPlugin**: Main plugin class that registers tasks and configurations
- **ScoreTask**: Core task that runs tests and generates grading reports
- **Annotations**: Java annotations (@Graded, @Tag, @Tags) for marking test cases
- **Configuration DSL**: Gradle configuration block for customizing grading behavior

Key components:
- `GradleGraderPlugin.kt`: Main plugin entry point
- `ScoreTask.kt`: Core grading logic
- `GradePolicy.kt`: Policy configuration for grading
- `CheckpointConfig.kt`: Support for checkpoint-based grading
- `RelentlessCheckstyle.kt`: Checkstyle integration

### Server Architecture
The server (`server/`) is a Ktor-based web application that:
- Receives POST requests with grading reports
- Stores reports in MongoDB
- Provides status endpoints
- Uses Moshi for JSON serialization

### Version Management
Both modules use a property-based versioning system:
- Version is defined in root `build.gradle.kts` as `2025.2.2`
- Each module generates its own version properties file during build
- Plugin: `edu.illinois.cs.cs125.gradlegrader.plugin.version`
- Server: `edu.illinois.cs.cs125.gradlegrader.server.version`

## Development Notes

### Java/Kotlin Compatibility
- Plugin uses both Java (annotations) and Kotlin (implementation)
- Target Java 21 for both compilation and runtime
- Kotlin version 2.1.10

### Testing Framework Integration
- Designed to work with JUnit test frameworks
- Uses @Graded annotation to mark test cases for grading
- Supports point values and friendly names for test cases
- Can integrate with checkstyle for code style grading

### Plugin Distribution
- Published to Maven Central as `org.cs124.gradlegrader`
- Uses shadow JAR for distribution to include dependencies
- Supports both legacy and plugins {} block application