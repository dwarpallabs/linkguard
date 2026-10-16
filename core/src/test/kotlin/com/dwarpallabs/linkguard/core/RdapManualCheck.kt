package com.dwarpallabs.linkguard.core

/**
 * Manual, networked sanity check of the RDAP bootstrap and parser against real registries.
 * Not a unit test, never run in CI. Run with: ./gradlew :core:rdapCheck --args="example.com nic.in"
 */
fun main(args: Array<String>) {
    val domains = args.toList().ifEmpty { listOf("example.com", "nic.in") }
    val data = EngineData.loadBundled()
    val client = RdapClient(UrlConnectionHttp(), data.rdapServers, timeoutMs = 5_000)
    for (domain in domains) {
        val tld = domain.substringAfterLast('.')
        println("$domain via ${data.rdapServers[tld] ?: "no https RDAP server"} -> registered ${client.creationDate(domain, 5_000)}")
    }
}
