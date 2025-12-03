val koin_version: String by project
val koog_version: String by project
val kotlin_version: String by project
val kotlinx_html_version: String by project
val kotlinx_rpc_version: String by project
val ktor_version: String by project
val logback_version: String by project

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.rpc.plugin")
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-rpc-krpc-serialization-json:$kotlinx_rpc_version")
            api("org.jetbrains.kotlinx:kotlinx-rpc-core:$kotlinx_rpc_version")
        }
    }
}
