plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.10"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "jp.yappo.pologen"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation(kotlin("stdlib"))
    implementation("org.jetbrains:markdown:0.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.8.0")
    implementation("org.apache.commons:commons-text:1.10.0")
    implementation("com.akuleshov7:ktoml-core:0.5.1")
    implementation("com.akuleshov7:ktoml-file:0.5.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
}

tasks.shadowJar {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
}

kotlin {
    jvmToolchain(21)
}