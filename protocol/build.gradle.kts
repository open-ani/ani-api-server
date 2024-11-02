plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `flatten-source-sets`
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)
    implementation("io.swagger.core.v3:swagger-annotations:2.2.21")
    implementation("io.swagger.core.v3:swagger-models:2.2.21")
}