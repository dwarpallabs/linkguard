import groovy.json.JsonSlurper
import java.net.IDN

plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

// The upstream files are committed as-is (easy to diff and refresh). At build time they are turned
// into compact, ASCII-only line files so a 2 GB phone does not parse 400 KB of text and JSON on
// every cold start. Only the compact files ship.
val upstreamDir = layout.projectDirectory.dir("src/main/resources/linkguard")
val compactDir = layout.buildDirectory.dir("generated/compactLists")

val compactLists by tasks.registering {
    val psl = upstreamDir.file("public_suffix_list.dat").asFile
    val rdap = upstreamDir.file("rdap_bootstrap.json").asFile
    val out = compactDir
    inputs.files(psl, rdap)
    outputs.dir(out)
    doLast {
        val dir = out.get().dir("linkguard").asFile.apply { mkdirs() }

        // Public suffix rules in ASCII (punycode), keeping the "!" and "*." markers.
        val rules = psl.readLines()
            .map { it.trim().substringBefore(' ') }
            .filter { it.isNotEmpty() && !it.startsWith("//") }
            .mapNotNull { rule ->
                val prefix = when {
                    rule.startsWith("!") -> "!"
                    rule.startsWith("*.") -> "*."
                    else -> ""
                }
                val body = rule.removePrefix(prefix)
                val ascii = if (body.all { it.code < 128 }) body else try {
                    IDN.toASCII(body, IDN.ALLOW_UNASSIGNED)
                } catch (_: IllegalArgumentException) {
                    null // a handful of rules java.net.IDN (IDNA2003) cannot convert
                }
                ascii?.let { prefix + it.lowercase() }
            }
        dir.resolve("public_suffixes.txt").writeText(rules.joinToString("\n", postfix = "\n"))

        // TLD<TAB>https base URL. HTTP-only registries are dropped so domains never travel unencrypted.
        @Suppress("UNCHECKED_CAST")
        val services = (JsonSlurper().parse(rdap) as Map<String, Any>)["services"] as List<List<List<String>>>
        val servers = services.flatMap { (tlds, urls) ->
            val https = urls.firstOrNull { it.startsWith("https://") } ?: return@flatMap emptyList()
            val base = if (https.endsWith("/")) https else "$https/"
            tlds.map { "${it.lowercase()}\t$base" }
        }.sorted()
        dir.resolve("rdap_servers.tsv").writeText(servers.joinToString("\n", postfix = "\n"))
    }
}

sourceSets.main {
    resources.srcDir(compactLists)
}

tasks.processResources {
    exclude("linkguard/public_suffix_list.dat", "linkguard/rdap_bootstrap.json")
}
