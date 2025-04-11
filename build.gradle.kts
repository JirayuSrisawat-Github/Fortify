import java.io.ByteArrayOutputStream

plugins {
    java
    alias(libs.plugins.lavalink)
}

group = "net.jirayu.fortify"
val (versionStr, isSnapshot) = getGitVersion()
version = versionStr
println("Version: $versionStr, isSnapshot: $isSnapshot")

lavalinkPlugin {
    name = "fortify-plugin"
    apiVersion = libs.versions.lavalink.api
    serverVersion = libs.versions.lavalink.server
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
}

repositories {
    mavenCentral()
    maven("https://maven.lavalink.dev/releases")
    maven("https://maven.lavalink.dev/snapshots")
    maven("https://jitpack.io")
}

dependencies {

}

fun getGitVersion(): Pair<String, Boolean> {
    val versionStr = ByteArrayOutputStream()
    val exactMatchResult = exec {
        standardOutput = versionStr
        errorOutput = versionStr
        isIgnoreExitValue = true
        commandLine("git", "describe", "--exact-match", "--tags")
    }

    if (exactMatchResult.exitValue == 0) {
        return versionStr.toString().trim() to false
    }

    val fallbackStr = ByteArrayOutputStream()
    exec {
        standardOutput = fallbackStr
        errorOutput = fallbackStr
        commandLine("git", "rev-parse", "--short", "HEAD")
    }

    return fallbackStr.toString().trim() to true
}
