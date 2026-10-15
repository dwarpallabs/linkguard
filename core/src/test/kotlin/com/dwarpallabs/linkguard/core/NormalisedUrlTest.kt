package com.dwarpallabs.linkguard.core

import com.dwarpallabs.linkguard.core.TestSupport.data
import com.dwarpallabs.linkguard.core.TestSupport.url
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NormalisedUrlTest {

    @ParameterizedTest
    @CsvSource(
        "www.irctc.co.in, irctc.co.in, co.in",
        "www.onlinesbi.sbi, onlinesbi.sbi, sbi",
        "icici.bank-secure-login.com, bank-secure-login.com, com",
        "www.indiapost.gov.in, indiapost.gov.in, gov.in",
        "sbi.bank.in, sbi.bank.in, bank.in",
        "attacker.github.io, attacker.github.io, github.io",
        "income-tax-refund-gov.in, income-tax-refund-gov.in, in",
    )
    fun registrableDomainUsesPublicSuffixList(host: String, registrable: String, suffix: String) {
        val parsed = url("https://$host/")
        assertEquals(registrable, parsed.registrableDomain)
        assertEquals(suffix, parsed.publicSuffix)
    }

    @Test
    fun lowercasesAndStripsDefaultPortAndTrailingDot() {
        val parsed = url("HTTPS://WWW.Example.COM.:443/Path?Q=1#Frag")
        assertEquals("https", parsed.scheme)
        assertEquals("www.example.com", parsed.host)
        assertEquals(-1, parsed.port)
        assertEquals("/Path", parsed.path)
        assertEquals("https://www.example.com/Path?Q=1#Frag", parsed.toUrlString())
    }

    @Test
    fun keepsNonDefaultPort() {
        assertEquals(8080, url("http://example.com:8080/").port)
    }

    @Test
    fun convertsUnicodeHostToPunycodeAndBack() {
        val parsed = url("http://xn--phnepe-xya.com/")
        assertEquals("xn--phnepe-xya.com", parsed.host)
        assertEquals("phûnepe.com", parsed.unicodeHost)
        assertEquals("xn--irtel-3ve.com", url("http://аirtel.com/").host)
    }

    @Test
    fun detectsIpLiterals() {
        assertTrue(url("http://103.21.58.12/sbi").isIpLiteral)
        assertTrue(url("http://0x7f000001/").isIpLiteral)
        assertTrue(url("http://[2001:db8::1]:8080/").isIpLiteral)
        assertNull(url("http://103.21.58.12/").registrableDomain)
        assertFalse(url("http://1password.com/").isIpLiteral)
    }

    @Test
    fun separatesUserInfoFromHost() {
        val parsed = url("http://onlinesbi.sbi@198.51.100.7/login")
        assertEquals("onlinesbi.sbi", parsed.userInfo)
        assertEquals("198.51.100.7", parsed.host)
    }

    @Test
    fun parsesUpiLinks() {
        val parsed = url("upi://pay?pa=a@ybl&pn=Electricity%20Board&am=499")
        assertEquals("upi", parsed.scheme)
        assertEquals("Electricity Board", parsed.queryParams["pn"])
        assertFalse(parsed.isWeb)
    }

    @Test
    fun subdomainDepthCountsLabelsBeforeRegistrableDomain() {
        assertEquals(0, url("https://example.com/").subdomainDepth)
        assertEquals(4, url("https://a.b.c.d.example.co.in/").subdomainDepth)
    }

    @Test
    fun rejectsUnsupportedOrBrokenInput() {
        listOf("", "not a url", "javascript:alert(1)", "ftp://example.com/", "https://", "http://example.com:99999/")
            .forEach { assertNull(NormalisedUrl.parse(it, data.psl), it) }
    }

    @Test
    fun toStringNeverRevealsTheUrl() {
        assertFalse("secret" in url("https://example.com/secret?token=secret").toString())
    }
}
