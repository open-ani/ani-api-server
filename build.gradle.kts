buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    kotlin("jvm") apply false
    kotlin("plugin.serialization") version libs.versions.kotlin apply false
}

allprojects {
    group = "me.him188.ani"
    version = properties["version.name"].toString()

    repositories {
        mavenCentral()
    }
}

extensions.findByName("buildScan")?.withGroovyBuilder {
    setProperty("termsOfServiceUrl", "https://gradle.com/terms-of-service")
    setProperty("termsOfServiceAgree", "yes")
}

subprojects {
    afterEvaluate {
        kotlin.runCatching { configureKotlinOptIns() }
        configureKotlinTestSettings()
        configureEncoding()
        configureJvmTarget()
    }
}