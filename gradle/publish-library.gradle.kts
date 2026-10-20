// Publishes a library module (roadmap C4) as com.dwarpallabs.linkguard:linkguard-<module>, with
// Gradle's own maven-publish and signing plugins. Applied by core/ and data/.
//
// ./gradlew :core:publishAllPublicationsToStagingRepository :data:publishAllPublicationsToStagingRepository
// writes a Maven repository to build/central-staging; scripts/central_bundle.sh uploads it to
// Maven Central. Artifacts are signed only when LIB_SIGNING_KEY (ASCII-armoured private key) and
// LIB_SIGNING_PASSWORD are set, so local and CI builds work without keys.

apply(plugin = "maven-publish")
apply(plugin = "signing")

group = "com.dwarpallabs.linkguard"
version = providers.gradleProperty("libVersion").get()

val libraryDescriptions = mapOf(
    "core" to "LinkGuard's link checks for the JVM and Android: fake brand websites, app downloads, UPI requests, " +
        "unusual addresses and short links, decided on the device in milliseconds.",
    "data" to "Public data LinkGuard's checks need: brands and their official websites, link shorteners, " +
        "file hosts, throwaway domain endings, the Public Suffix List and the RDAP bootstrap.",
)

configure<JavaPluginExtension> {
    withSourcesJar()
}

// Central requires a javadoc jar; this one carries the library's README instead of generated docs.
val javadocJar = tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
    from(rootProject.file("core/README.md"))
}

// Licence files inside every jar, and byte-for-byte reproducible archives.
tasks.withType<Jar>().configureEach {
    from(rootProject.files("LICENSE", "NOTICE")) { into("META-INF") }
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    manifest { attributes("Automatic-Module-Name" to "com.dwarpallabs.linkguard.${project.name}") }
}

configure<PublishingExtension> {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            artifactId = "linkguard-${project.name}"
            artifact(javadocJar)
            pom {
                name.set("LinkGuard ${project.name}")
                description.set(libraryDescriptions.getValue(project.name))
                url.set("https://github.com/dwarpallabs/linkguard")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("dwarpallabs")
                        name.set("Dwarpal Labs")
                        email.set("support@dwarpallabs.com")
                        organization.set("Dwarpal Labs")
                        organizationUrl.set("https://dwarpallabs.com")
                    }
                }
                scm {
                    url.set("https://github.com/dwarpallabs/linkguard")
                    connection.set("scm:git:https://github.com/dwarpallabs/linkguard.git")
                    developerConnection.set("scm:git:ssh://git@github.com/dwarpallabs/linkguard.git")
                }
                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/dwarpallabs/linkguard/issues")
                }
            }
        }
    }
    repositories {
        maven {
            name = "staging"
            url = uri(rootProject.layout.buildDirectory.dir("central-staging"))
        }
    }
}

val signingKey = providers.environmentVariable("LIB_SIGNING_KEY")
if (signingKey.isPresent) {
    configure<SigningExtension> {
        useInMemoryPgpKeys(signingKey.get(), providers.environmentVariable("LIB_SIGNING_PASSWORD").orNull)
        sign(the<PublishingExtension>().publications["library"])
    }
}
