package com.dwarpallabs.linkguard.core

import com.dwarpallabs.linkguard.core.TestSupport.data
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LinkExtractorTest {

    private fun links(text: String) = LinkExtractor.extract(text, data.psl).links

    @Test
    fun tamilMessageWithSpacedAndGluedLinks() {
        assertEquals(
            listOf("https://sbi-kyc-update.top/verify?id=12"),
            links("உங்கள் SBI கணக்கு முடக்கப்பட்டது. உடனே https://sbi-kyc-update.top/verify?id=12 ஐ திறக்கவும்"),
        )
        assertEquals(listOf("https://bit.ly/abc123"), links("இணைப்பு:https://bit.ly/abc123இல் பார்க்கவும்"))
    }

    @Test
    fun hindiMessageWithDandaAndGluedWords() {
        assertEquals(listOf("http://sbi-kyc.top/update"), links("अपना KYC अपडेट करें: sbi-kyc.top/update। धन्यवाद"))
        assertEquals(listOf("https://hdfc-reward.xyz/claim"), links("यहाँ क्लिक करें https://hdfc-reward.xyz/claimपर"))
        assertEquals(listOf("http://sbi.com"), links("क्लिक sbi.comपर"))
    }

    @Test
    fun zeroWidthCharactersInsideLinksAreRemoved() {
        assertEquals(listOf("https://sbi-kyc.top/login"), links("Open https://sbi​-kyc​.top/login now"))
        assertEquals(listOf("http://bit.ly/x"), links("bit‍.ly/x"))
        assertEquals(listOf("http://sbikyc.top/a"), links("sbi­kyc.top/a"))
        assertEquals(listOf("https://paytm-kyc.top/"), links("﻿https://paytm-kyc.top/"))
    }

    @Test
    fun whatsAppAndTelegramLinksWithoutAScheme() {
        assertEquals(
            listOf("http://wa.me/919876543210", "http://t.me/joinchat/AbCdEf"),
            links("Chat on wa.me/919876543210 or join t.me/joinchat/AbCdEf"),
        )
    }

    @Test
    fun upiLinksInsideASentence() {
        assertEquals(
            listOf("upi://pay?pa=refund.desk@ybl&pn=Electricity%20Board&am=4999"),
            links("Pay now upi://pay?pa=refund.desk@ybl&pn=Electricity%20Board&am=4999 to get your refund"),
        )
        assertEquals(listOf("UPI://pay?pa=x@ybl"), links("बिजली बिल: UPI://pay?pa=x@ybl।"))
    }

    @Test
    fun everydayTextIsNotALink() {
        assertEquals(
            emptyList<String>(),
            links("Pay Rs.500 by 5 p.m. e.g. today, see photo.jpg or report.pdf, mail ravi@gmail.com, UPI ravi@okaxis, app 1.2.3.4, No.1, Mr.Sharma"),
        )
        assertEquals(emptyList<String>(), links(""))
        assertEquals(emptyList<String>(), links("https:// and http://"))
    }

    @Test
    fun punctuationAroundLinks() {
        assertEquals(
            listOf("https://example.top/a_(b)", "http://www.irctc.co.in"),
            links("(see https://example.top/a_(b)) and www.irctc.co.in."),
        )
        assertEquals(listOf("https://sbi-kyc.top/x"), links("[click](https://sbi-kyc.top/x)"))
        assertEquals(listOf("https://sbi-kyc.top/x"), links("\"https://sbi-kyc.top/x\","))
        assertEquals(listOf("https://sbi-kyc.top/x"), links("“https://sbi-kyc.top/x”"))
    }

    @Test
    fun bareIpAddressNeedsAPath() {
        assertEquals(listOf("http://103.21.4.5/kyc"), links("Update at 103.21.4.5/kyc today"))
        assertEquals(emptyList<String>(), links("Version 103.21.4.5"))
    }

    @Test
    fun indianScriptWebsiteNamesAndFullWidthDots() {
        assertEquals(listOf("https://स्टेटबैंक.भारत/login"), links("देखें https://स्टेटबैंक.भारत/login"))
        assertEquals(listOf("http://sbi-kyc.top/x"), links("sbi-kyc．top/x"))
    }

    @Test
    fun duplicatesAreCheckedOnce() {
        assertEquals(listOf("https://bit.ly/x"), links("https://bit.ly/x again https://BIT.LY/x"))
    }

    @Test
    fun atMostTenLinks() {
        val text = (1..12).joinToString(" ") { "https://site$it.top/" }
        val result = LinkExtractor.extract(text, data.psl)
        assertEquals(LinkExtractor.MAX_LINKS, result.links.size)
        assertTrue(result.more)
        assertFalse(LinkExtractor.extract("https://one.top/", data.psl).more)
    }

    @Test
    fun everyLinkFoundIsOneTheEngineCanCheck() {
        val text = "SBI: sbi-kyc.top/u, wa.me/91, https://bit.ly/x, upi://pay?pa=a@ybl, 103.21.4.5/k, www.irctc.co.in"
        for (link in links(text)) assertTrue(NormalisedUrl.parse(link, data.psl) != null, link)
    }
}
