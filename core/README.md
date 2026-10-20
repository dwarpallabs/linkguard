# linkguard-core

LinkGuard's link checks as a plain Kotlin/JVM library, for Android apps and JVM servers. It looks at a link and gives a verdict in milliseconds, on the device, with no account and no tracking. The checks are the same ones the [LinkGuard app](https://github.com/dwarpallabs/linkguard) uses:

- fake bank and brand websites (SBI, HDFC Bank, PhonePe, IRCTC and more, including look-alike letters and typos)
- app downloads from outside the Play Store
- UPI payment requests, autopay mandates and fake brand payees
- raw-IP and throwaway addresses
- short links (followed first, so the real destination is checked)
- optionally, brand-new websites and known scam websites (online, see below)

Licence: Apache-2.0. Needs Java 11+ or Android 8+ (API 26). No dependencies besides the Kotlin standard library and `linkguard-data`, which comes with it.

## Add it

```kotlin
dependencies {
    implementation("com.dwarpallabs.linkguard:linkguard-core:0.1.0")
}
```

`linkguard-data` (brands, link shorteners, file hosts, domain endings, the Public Suffix List and the RDAP bootstrap) is added automatically as a runtime dependency.

## Check a link (offline)

```kotlin
val data = EngineData.loadBundled()          // load once per process, off the main thread
val engine = LinkEngine(data, http = null, registrationDates = null)

val verdict = engine.evaluate("http://sbi-rewards-redeem.xyz/claim")
when (verdict.level) {
    Verdict.Level.SILENT -> open(link)                     // nothing found: open it
    Verdict.Level.SOFT -> showWarning(verdict.top!!)       // "be careful"
    Verdict.Level.STRONG -> showWarning(verdict.top!!)     // "this may be a scam"
}
```

The offline engine never touches the network. `evaluate` is blocking and thread-safe; call it off the main thread.

- `verdict.score` is the highest signal score (0.0–1.0). Below 0.3 is `SILENT`, 0.3–0.7 `SOFT`, above 0.7 `STRONG`. Scores are the maximum, never a sum.
- `verdict.top` is the strongest `Signal`: its `check` (`CheckId`), a machine-readable `reason` (`brand_lookalike.typo`), a `messageKey` and `details`.
- `verdict.finalUrl` is the link after normalisation and, online, after following short links.
- `LinkExtractor.extract(text, data.psl)` finds the links in a whole message, including ones glued to Hindi or Tamil words or split with invisible characters.

LinkGuard's own rule is to never block: show the warning and let the person open the link anyway.

## Messages

`Signal.messageKey` names the sentence to show. The English text is below. The LinkGuard app has machine-translated text for 27 more languages in [`app/src/main/res`](../app/src/main/res).

| `messageKey` | English | `details` |
| --- | --- | --- |
| `warn_brand_lookalike` | This website is pretending to be %1$s. It is not %1$s’s real website. | `brand` |
| `warn_brand_lookalike_mine` | You use %1$s. This website is pretending to be %1$s. It is not %1$s’s real website. | `brand` (only when you pass `myBrands`) |
| `warn_apk_download` | This link downloads an app from outside the Play Store. Fake apps like this can read your messages and steal bank OTPs. | |
| `warn_upi_intent` | This link asks you to send money by UPI. You never need to pay to get a refund, a prize or a KYC update. | `payee_name`, `payee_address`, `amount`, `note` |
| `warn_upi_mandate` | This link sets up automatic payments from your account. Only agree if you started it yourself, for a bill or plan you know. | as above, plus `kind` = `mandate` |
| `warn_upi_brand` | This payment request uses the name %1$s, but the money goes to a different account. You never need to pay to get a refund or a KYC update. | as above, plus `brand` |
| `warn_weird_host` | This link uses an unusual address that real companies rarely use. With `brand`: This link mentions %1$s but uses an unusual address that %1$s does not use. | `brand` (optional) |
| `warn_domain_age` | This website was created %d days ago. Scam websites are often brand new. | `age_days` |
| `warn_reputation` | This website is on a list of known scam or harmful websites. | `source` (optional: the bank or telecom fraud team that confirmed it) |
| `warn_campaign` | This website looks like part of a group of scam websites. %d LinkGuard users reported them this week. | `count` |
| `warn_community` | This link was reported as a scam by %d LinkGuard users this week. | `count` |
| `warn_managed_block` | Your organisation has marked this website as unsafe. | (only with a `ManagedPolicy`) |

## Online checks (optional)

Three checks get better with the network. Each one is off until you pass it in, and each sends as little as possible:

```kotlin
val http = UrlConnectionHttp(userAgent = "YourApp/1.0")
val engine = LinkEngine(
    data,
    http = http,                                                     // follow short links
    registrationDates = MemoryCachedRegistrationDates(RdapClient(http, data.rdapServers)),
    reputation = MemoryCachedReputation(
        HttpReputationLookup(http, "https://api.dwarpallabs.com", apiKey = "<your key>"),
    ),
)
val verdict = engine.evaluate(link, Budget.INTERACTIVE)            // stays within ~300 ms (1.5 s for short links)
```

| Check | What leaves the device |
| --- | --- |
| Short links | A `HEAD` request to the link shortener the link already points at (bit.ly, t.co and the others in `shorteners.txt`). Never to the final website. |
| Website age | The registrable domain only (`example.com`), over HTTPS, to the registry's RDAP server. |
| Known scam websites | The first 4 bytes of the SHA-256 of the domain (`3f9a1c2e`), never the domain itself. LinkGuard's server keeps no record of lookups. |

### The LinkGuard lookup server

`api.dwarpallabs.com` answers known-scam lookups from Dwarpal Labs' blocklist, community reports, scam-campaign clusters and partner confirmations.

- **Without a key** it is for the LinkGuard app and non-commercial use, with a per-IP limit.
- **Commercial apps need a key.** Email support@dwarpallabs.com. Keyed answers use only sources whose terms allow commercial use, and come with a higher request limit (up to 64 prefixes per request).

## Organisations

`ManagedPolicy` adds an organisation's own lists: websites to warn about (a strong warning before any other check), websites to trust, and keeping every check on.

```kotlin
val policy = ManagedPolicy(
    blocked = ManagedPolicy.parseList(adminText, data.psl),
    allowed = ManagedPolicy.parseList(intranetText, data.psl),
)
engine.evaluate(link, managed = policy)
```

## Stability

`0.x` versions may change the API between minor versions; the changes are listed in the [changelog](../CHANGELOG.md). Declarations marked `@InternalLinkGuardApi` are for LinkGuard's own tools and may change at any time.

## Licences

- linkguard-core and linkguard-data: Apache-2.0, © Dwarpal Labs.
- linkguard-data bundles data generated from the [Public Suffix List](https://publicsuffix.org) (MPL-2.0) and IANA's [RDAP bootstrap](https://data.iana.org/rdap/dns.json). See `META-INF/NOTICE` in each jar.
