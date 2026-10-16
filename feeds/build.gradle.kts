import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Daily feeds pipeline: fetches public feeds, normalises them with :core (same domain logic as the
// phone) and writes the results to the LinkGuard server database. Never prints or commits list contents.
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
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

application {
    mainClass.set("com.dwarpallabs.linkguard.feeds.FeedsMainKt")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":data"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
