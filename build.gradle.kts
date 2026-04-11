plugins {
    kotlin("jvm") version "2.3.20" apply false
    kotlin("multiplatform") version "2.3.20" apply false
    id("io.ktor.plugin") version "3.4.2" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.20" apply false
    id("org.jetbrains.kotlinx.rpc.plugin") version "0.10.2" apply false
}

subprojects {
    group = "dev.pranav"
    version = "1.0.0"
}
