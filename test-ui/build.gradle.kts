plugins {
    kotlin("jvm")
    application
}

application {
    mainClass.set("dev.pranav.bryte.testui.MainKt")
}

dependencies {
    implementation(project(":client"))
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation(kotlin("stdlib-jdk8"))
}
repositories {
    mavenCentral()
}
