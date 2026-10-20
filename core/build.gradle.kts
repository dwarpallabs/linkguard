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
        // LinkGuard's own modules may use @InternalLinkGuardApi declarations; library users must opt in.
        optIn.add("com.dwarpallabs.linkguard.core.InternalLinkGuardApi")
    }
    // The published API (roadmap C4) is dumped to core/api/ and checked, so a change to it is a
    // deliberate one: run ./gradlew :core:updateLegacyAbi and commit the dump.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {
        enabled.set(true)
    }
}

apply(from = rootProject.file("gradle/publish-library.gradle.kts"))

dependencies {
    // EngineData.loadBundled() reads linkguard-data from the classpath, so library users get it too.
    runtimeOnly(project(":data"))
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

tasks.named("check") { dependsOn("checkLegacyAbi") }

// Manual RDAP check against real registries (network). Not part of `test`.
tasks.register<JavaExec>("rdapCheck") {
    group = "verification"
    description = "Looks up real domains over RDAP to sanity-check the bootstrap and parser."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.dwarpallabs.linkguard.core.RdapManualCheckKt")
}
