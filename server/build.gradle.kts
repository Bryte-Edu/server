val koin_version: String by project
val koog_version: String by project
val kotlin_version: String by project
val ktor_version: String by project
val kotlinx_html_version: String by project
val kotlinx_rpc_version: String by project
val logback_version: String by project

plugins {
    kotlin("jvm")
    id("io.ktor.plugin")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlinx.rpc.plugin")
}

application {
    mainClass = "io.ktor.server.cio.EngineMain"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":mistral"))

    implementation("io.github.jan-tennert.supabase:supabase-kt:3.3.0-beta-1")
    implementation("io.github.jan-tennert.supabase:auth-kt:3.3.0-beta-1")
    implementation("io.github.jan-tennert.supabase:storage-kt:3.3.0-beta-1")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:3.3.0-beta-1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1-0.6.x-compat")

    implementation("com.github.teamnewpipe:NewPipeExtractor:6f51a23fa5")

    implementation("io.github.flaxoos:ktor-server-rate-limiting:2.2.1")
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-websockets")
    implementation("io.ktor:ktor-server-di")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("org.jetbrains.kotlinx:kotlinx-rpc-krpc-ktor-server:$kotlinx_rpc_version")
    implementation("org.jetbrains.kotlinx:kotlinx-rpc-krpc-ktor-client:$kotlinx_rpc_version")
    implementation("ai.koog:koog-ktor:$koog_version") {
        exclude(group = "ai.koog", module = "agents-core")
    }
    implementation(project(":lib:koog:agents:agents-core"))

    implementation("io.insert-koin:koin-ktor:$koin_version")
    implementation("io.insert-koin:koin-logger-slf4j:$koin_version")
    implementation("io.ktor:ktor-server-html-builder")
    implementation("org.jetbrains.kotlinx:kotlinx-html:$kotlinx_html_version")
    implementation("org.jetbrains.kotlin-wrappers:kotlin-css-jvm:2025.6.4")
    implementation("dev.hayden:khealth:3.0.2")
    implementation("io.ktor:ktor-server-host-common")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-resources")
    implementation("io.ktor:ktor-server-request-validation")
    implementation("io.ktor:ktor-server-sessions")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-auth-jwt")
    implementation("io.ktor:ktor-server-default-headers")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-cio")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.ktor:ktor-client-cio-jvm:3.3.2")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

val generateBuildConfig by tasks.registering(DefaultTask::class) {
    val envFile = project.file(".env")
    inputs.file(envFile)

    val outputDir = project.layout.buildDirectory.dir("generated/source/env/kotlin")
    outputs.dir(outputDir)
    val envVars = mutableMapOf<String, String>()

    doLast {
        if (envFile.exists()) {
            envFile.readLines().forEach { line ->
                if (line.isNotBlank() && !line.startsWith('#')) {
                    val env = line.split('=', limit = 2)
                    envVars[env.first()] = env.last()
                }
            }
        }

        val packageName = "dev.pranav.bryte.server"
        val configFileName = "BuildConfig.kt"

        val content = buildString {
            appendLine("package $packageName")
            appendLine("")

            envVars.forEach { (key, value) ->
                appendLine("const val $key: String = \"$value\"")
            }
        }

        val outputPackageDir = outputDir.get().asFile.resolve(packageName.replace('.', '/'))
        outputPackageDir.mkdirs()
        outputPackageDir.resolve(configFileName).writeText(content)
    }
}


tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
    sourceSets.getByName("main") {
        kotlin.srcDir(generateBuildConfig.get().outputs.files.singleFile)

        dependsOn(generateBuildConfig)
    }
}
