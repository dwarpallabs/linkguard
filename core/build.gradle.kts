import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    testImplementation(project(":data"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val corpusDir = rootProject.layout.projectDirectory.dir("corpus")

tasks.test {
    useJUnitPlatform()
    inputs.dir(corpusDir)
    systemProperty("corpus.dir", corpusDir.asFile.absolutePath)
    systemProperty("core.main.dir", layout.projectDirectory.dir("src/main").asFile.absolutePath)
    testLogging {
        events("failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
    }
}

// Manual RDAP check against real registries (network). Not part of `test`.
tasks.register<JavaExec>("rdapCheck") {
    group = "verification"
    description = "Looks up real domains over RDAP to sanity-check the bootstrap and parser."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.dwarpallabs.linkguard.core.RdapManualCheckKt")
}
