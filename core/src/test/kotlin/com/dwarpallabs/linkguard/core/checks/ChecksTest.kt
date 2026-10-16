package com.dwarpallabs.linkguard.core.checks

import com.dwarpallabs.linkguard.core.CheckId
import com.dwarpallabs.linkguard.core.FakeReputation
import com.dwarpallabs.linkguard.core.TestSupport.data
import com.dwarpallabs.linkguard.core.TestSupport.url
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ChecksTest {

    private val matcher = BrandMatcher(data.brands)

    @Nested
    inner class Allowlist {
        private val check = AllowlistCheck(data)

        @ParameterizedTest
        @ValueSource(strings = [
            "https://www.irctc.co.in/", "https://anything.gov.in/", "https://sbi.bank.in/", "https://onlinesbi.sbi/",
            "https://mail.google.com/", "https://sbicard.com/",
        ])
        fun allows(raw: String) = assertTrue(check.isAllowed(url(raw)), raw)

        @ParameterizedTest
        @ValueSource(strings = [
            "https://income-tax-refund-gov.in/", "https://gov.in.example.com/", "http://103.21.58.12/", "upi://pay?pa=a@b",
        ])
        fun rejects(raw: String) = assertFalse(check.isAllowed(url(raw)), raw)
    }

    @Nested
    inner class ApkDownload {
        private val check = ApkDownloadCheck(data)

        @Test
        fun apkPath() = assertEquals("apk_download.path", check.run(url("http://x.example/app/Kyc.APK")).reason)

        @Test
        fun apkInQueryFilename() =
            assertEquals("apk_download.path", check.run(url("http://x.example/dl.php?file=update.apk")).reason)

        @Test
        fun apkContentType() {
            val u = url("https://t.ly/x").withContentType("application/vnd.android.package-archive; charset=binary")
            assertEquals("apk_download.content_type", check.run(u).reason)
        }

        @Test
        fun fileHostWithApk() =
            assertEquals("apk_download.file_host", check.run(url("https://www.mediafire.com/file/x/Pink_apk/file")).reason)

        @ParameterizedTest
        @ValueSource(strings = [
            "https://play.google.com/store/apps/details?id=com.phonepe.app",
            "https://www.mediafire.com/file/x/photos.zip/file",
            "https://news.example/what-is-an-apk-file",
            "https://example.com/apkpure-review",
        ])
        fun notAnApk(raw: String) = assertFalse(check.run(url(raw)).fired, raw)
    }

    @Nested
    inner class BrandLookalike {
        private val check = BrandLookalikeCheck(matcher)

        private fun reason(raw: String) = check.run(url(raw)).reason

        @Test
        fun keywordToken() = assertEquals("brand_lookalike.keyword", reason("http://sbi-rewards.xyz/"))

        @Test
        fun brandAsSubdomain() = assertEquals("brand_lookalike.keyword", reason("http://icici.bank-secure-login.com/"))

        @Test
        fun longKeywordInsideLabel() = assertEquals("brand_lookalike.keyword", reason("http://income-tax-refund-gov.in/"))

        @Test
        fun typo() = assertEquals("brand_lookalike.typo", reason("http://phonpe-rewards.in/"))

        @Test
        fun digitSwap() = assertEquals("brand_lookalike.digit_swap", reason("http://hdfcb4nk-login.com/"))

        @Test
        fun digitOneAsI() = assertEquals("brand_lookalike.digit_swap", reason("http://1c1c1-kyc.com/"))

        @Test
        fun cyrillicHomograph() = assertEquals("brand_lookalike.homograph", reason("http://xn--irtel-3ve.com/"))

        @Test
        fun accentedHomograph() = assertEquals("brand_lookalike.homograph", reason("http://xn--phnepe-xya.com/"))

        @Test
        fun reportsBrandName() =
            assertEquals("HDFC Bank", check.run(url("http://hdfc-kyc.top/")).details["brand"])

        @ParameterizedTest
        @ValueSource(strings = [
            "https://www.axisbank.com/", "https://netbanking.hdfcbank.com/", "https://www.wsbi.org/",
            "https://www.pnbmetlife.com/", "https://lesbianhistory.example.org/", "https://www.ajio.com/",
            "https://mojito-recipes.example.com/",
        ])
        fun noFalsePositive(raw: String) = assertFalse(check.run(url(raw)).fired, raw)
    }

    @Nested
    inner class UpiIntent {
        private val check = UpiIntentCheck()

        @Test
        fun extractsPayeeAndAmount() {
            val signal = check.run(url("upi://pay?pa=fraud@ybl&pn=KBC%20Lottery&am=25000&tn=fee"))
            assertEquals(0.7, signal.score)
            assertEquals("fraud@ybl", signal.details["payee_address"])
            assertEquals("KBC Lottery", signal.details["payee_name"])
            assertEquals("25000", signal.details["amount"])
        }

        @Test
        fun webLinksAreIgnored() = assertFalse(check.run(url("https://www.npci.org.in/upi")).fired)
    }

    @Nested
    inner class WeirdHost {
        private val check = WeirdHostCheck(data, matcher)

        private fun signal(raw: String) = check.run(url(raw))

        @Test
        fun ipAloneIsWeak() = assertEquals(0.4, signal("http://192.0.2.1/router").score)

        @Test
        fun ipWithBrandIsStrong() = assertEquals("weird_host.ip_with_brand", signal("http://192.0.2.1/sbi/login").reason)

        @Test
        fun userInfoWithBrand() =
            assertEquals("weird_host.userinfo_with_brand", signal("http://onlinesbi.sbi@198.51.100.7/").reason)

        @Test
        fun deepSubdomains() = assertEquals("weird_host.deep_subdomains", signal("https://a.b.c.d.example.com/").reason)

        @Test
        fun freeTldAlone() = assertEquals(0.4, signal("https://my-blog.xyz/").score)

        @Test
        fun freeTldWithBrandInQuery() = assertEquals(0.8, signal("https://offers.top/?bank=icici").score)

        @ParameterizedTest
        @ValueSource(strings = ["https://www.thehindu.com/topic/sbi/", "https://a.b.example.com/", "https://example.in/"])
        fun ordinaryHosts(raw: String) = assertFalse(signal(raw).fired, raw)
    }

    @Nested
    inner class Reputation {
        private val server = FakeReputation.fromResource("fixture_blocklist.txt")
        private val check = ReputationCheck(data, server)

        @Test
        fun blocklistedDomainAndSubdomains() {
            assertEquals("reputation.server", check.run(url("https://lg-test-phish.com/")).reason)
            assertEquals(0.9, check.run(url("https://cdn.lg-test-scam.org/pay")).score)
        }

        @Test
        fun unlisted() = assertFalse(check.run(url("https://lg-test-phish.com.example.org/")).fired)

        @Test
        fun sendsOnlyEightHexPrefixes() {
            check.run(url("https://a.b.lg-test-phish.com/secret/path?token=1"))
            assertTrue(server.prefixesSeen.isNotEmpty())
            assertTrue(server.prefixesSeen.all { it.length == 8 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } })
        }

        @Test
        fun serverUnavailableMeansNoSignal() =
            assertFalse(ReputationCheck(data, { _, _ -> null }).run(url("https://lg-test-phish.com/")).fired)
    }

    @Test
    fun everyCheckReportsItsOwnId() {
        val u = url("https://example.com/")
        assertEquals(CheckId.APK_DOWNLOAD, ApkDownloadCheck(data).run(u).check)
        assertEquals(CheckId.BRAND_LOOKALIKE, BrandLookalikeCheck(matcher).run(u).check)
        assertEquals(CheckId.WEIRD_HOST, WeirdHostCheck(data, matcher).run(u).check)
        assertEquals(CheckId.REPUTATION, ReputationCheck(data).run(u).check)
        assertEquals(CheckId.UPI_INTENT, UpiIntentCheck().run(u).check)
    }

    @Test
    fun editDistance() {
        assertTrue(BrandMatcher.editDistanceIsOne("phonpe", "phonepe"))
        assertTrue(BrandMatcher.editDistanceIsOne("paytn", "paytm"))
        assertFalse(BrandMatcher.editDistanceIsOne("paytm", "paytm"))
        assertFalse(BrandMatcher.editDistanceIsOne("pytn", "paytm"))
    }
}
