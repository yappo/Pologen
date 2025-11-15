plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
    id("com.gradleup.shadow") version "9.1.0"
}

group = "jp.yappo.pologen"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Kotest
    val kotestVersion = "5.9.1"
    testImplementation("io.kotest:kotest-runner-junit5-jvm:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core-jvm:$kotestVersion")

    // Kotlin
    testImplementation(kotlin("test"))
    implementation(kotlin("stdlib"))

    // App deps
    implementation("org.jetbrains:markdown:0.7.3")
    implementation("org.apache.commons:commons-text:1.10.0")
    implementation("com.akuleshov7:ktoml-core:0.5.1")
    implementation("com.akuleshov7:ktoml-file:0.5.1")
    implementation("gg.jte:jte:3.2.1")
    implementation("gg.jte:jte-kotlin:3.2.1")
}

tasks.test {
    useJUnitPlatform()
    // Run Kotest Specs (Gradle default includes *Test / *Tests only)
    include("**/*Test.class", "**/*Tests.class", "**/*Spec.class")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "jp.yappo.pologen.MainKt"
    }
}

tasks.shadowJar {
    manifest {
        attributes["Main-Class"] = "jp.yappo.pologen.MainKt"
    }
}

kotlin {
    jvmToolchain(21)
}
