rootProject.name = "bryte"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://packages.confluent.io/maven/")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-js-wrappers")
        maven("https://jitpack.io")
    }
}

include(":server")
include(":core")
include(":client")
include(":mistral")
include(":lib:koog:agents:agents-core")
