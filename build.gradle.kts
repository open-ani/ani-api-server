buildscript {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

plugins {
    kotlin("multiplatform") apply false
    kotlin("android") apply false
    kotlin("jvm") apply false
    kotlin("plugin.serialization") version libs.versions.kotlin apply false
    kotlin("plugin.compose") version libs.versions.kotlin apply false
    id("org.jetbrains.compose") apply false
    id("com.android.library") apply false
    id("com.android.application") apply false
    id("com.google.devtools.ksp") version libs.versions.ksp apply false
    id("androidx.room") version libs.versions.room apply false
}

allprojects {
    group = "me.him188.ani"
    version = properties["version.name"].toString()

    repositories {
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://androidx.dev/storage/compose-compiler/repository/")
        google()
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