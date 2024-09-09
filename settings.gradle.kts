
rootProject.name = "ani-api-server"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "kotlinx-atomicfu") { // atomicfu is not on Gradle Plugin Portal
                useModule("org.jetbrains.kotlinx:atomicfu-gradle-plugin:${requested.version}")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

fun includeProject(projectPath: String, dir: String? = null) {
    include(projectPath)
    if (dir != null) project(projectPath).projectDir = file(dir)
}

includeProject(":server", "server") // danmaku server
includeProject(":protocol", "protocol") // danmaku server-client protocol

includeProject(":utils:slf4j-kt", "utils/slf4j-kt") // shared by client and server (targets JVM)
includeProject(":utils:coroutines", "utils/coroutines")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")