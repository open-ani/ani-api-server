plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    `flatten-source-sets`
    idea
}

dependencies {
    api(projects.utils.slf4jKt)

    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.auto.head.response)
    implementation(libs.ktor.server.html.builder)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.caching.headers)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.swagger)
    implementation(libs.ktor.swagger.ui)
    implementation(libs.kotlinx.datetime)
    implementation(libs.mongodb.driver.kotlin.coroutine)
    implementation(projects.protocol)
    implementation(libs.mongodb.bson.kotlinx)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(projects.utils.coroutines)

    runtimeOnly(libs.logback.classic)
}

application {
    mainClass.set("me.him188.ani.danmaku.server.ApplicationKt")
}

idea {
    module.excludeDirs.add(file("logs"))
}

kotlin {
    jvmToolchain(17)
    compilerOptions.freeCompilerArgs.add("-Xmulti-dollar-interpolation")
    compilerOptions.freeCompilerArgs.add("-Xdont-warn-on-error-suppression")
}
