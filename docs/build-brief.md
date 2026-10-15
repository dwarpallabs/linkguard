# Link Interceptor — Build Brief v1

Oct 14, 2026 · @Vijay Subramanian

## CLAUDE.md

Paste this at the repo root before writing any code. Claude Code reads it at the start of every session.

```markdown
# LinkGuard (working name)

Open-source Android app that sits between links tapped in WhatsApp/Telegram/SMS and the user's real browser. It inspects the URL in under 300 ms and either forwards silently or shows one plain-language warning. Target users: first-time smartphone users in India. Licence: Apache-2.0.

## Non-negotiables
- Never log, store or transmit the URLs a user opens. All v1 checks run on-device. Network is used only for reputation lookups by hash prefix and for feed updates, never with the full URL.
- Never add friction to a safe link. If every check passes, the user sees nothing.
- Never block. The warning always offers "Open anyway".
- No accounts, no analytics SDKs, no ads, no third-party trackers.
- Minimum SDK 26 (Android 8). Must run well on a 2 GB RAM phone.

## Architecture
- `app/` — Android UI: link intercept activity, interstitial, settings. Kotlin, Jetpack Compose.
- `core/` — pure Kotlin/JVM module, no Android imports. URL normalisation, redirect unwrapping, all checks, verdict scoring. 100% unit-testable.
- `data/` — bundled lists: Indian brand allowlist, lookalike patterns, shortener domains, feed cache.
- `corpus/` — test URLs: `must_block.txt`, `must_pass.txt`, `ambiguous.txt`. Every check change must keep both files green.

## Conventions
- Kotlin 2.x, Gradle Kotlin DSL, JUnit 5 for `core/`, Compose UI tests for `app/`.
- Each check is a class implementing `Check { fun run(url: NormalisedUrl): Signal }`. Signals carry a score (0.0–1.0), a reason code and a user-facing message key.
- Verdict = max of signals, not sum. Thresholds: < 0.3 forward silently, 0.3–0.7 soft warning, > 0.7 strong warning.
- Strings in `values/`, `values-hi/`, `values-ta/`. Every user-facing message has all three.
- Conventional commits. One milestone per PR.

## Workflow for Claude
- Always start in plan mode for a new milestone. Show the plan; wait for approval.
- Run `./gradlew :core:test` before declaring any check done.
- When adding a check, add at least 3 URLs to `must_block.txt` and 3 to `must_pass.txt` that exercise it.
- Prefer boring, dependency-free Kotlin over libraries. Ask before adding a dependency.
- If a task touches the intent-filter / default-browser flow, say so explicitly; it is the riskiest part of the app.
```

## v1 spec — on-device link check

v1 is done when a link tapped in WhatsApp on your own phone is forwarded to Chrome with no visible delay, and a known scam URL shows the warning instead.

**User flow**

1. First launch: one screen explains what the app does, asks the user to set it as default browser, and lets them pick the real browser to forward to (Chrome preselected). No account.
2. User taps a link anywhere. Android routes it to LinkGuard. Checks run. If verdict < 0.3, LinkGuard launches the chosen browser with the final URL and finishes. Nothing is shown.
3. If verdict ≥ 0.3, the interstitial appears: the final domain in large text, one sentence saying why (in the device language: English, Hindi or Tamil), and two buttons — **Go back** (primary) and **Open anyway**.
4. Settings: change forward-to browser, toggle each check on/off, view the bundled brand list, "Report a link" (v1: opens a prefilled GitHub issue).

**Checks (all in `core/`, each returns a Signal)**

| Check | What it does | Score if hit |
| --- | --- | --- |
| Unwrap | Follow redirects (HEAD, max 5 hops, 2 s budget) through bit.ly, t.co, tinyurl, cutt.ly etc. Everything downstream uses the final URL. | 0 (feeds others) |
| APK download | Final URL path ends in `.apk`, or Content-Type is `application/vnd.android.package-archive`, or host serves from a file-share. | 0.9 |
| Brand lookalike | Host contains or is one edit from a bundled Indian brand (SBI, HDFC, ICICI, Axis, PNB, PhonePe, Paytm, GPay, India Post, Delhivery, BlueDart, IRCTC, Income Tax, UIDAI, EPFO, NPCI, Airtel, Jio) but is not on that brand's real domain. Includes punycode and digit swaps. | 0.8 |
| UPI intent | Scheme is `upi://` or a web link that resolves to one; shows payee and amount on the interstitial. | 0.7 |
| Domain age | RDAP lookup; registered < 30 days. Skipped offline. | 0.5 |
| Reputation | Google Safe Browsing Lookup API by hash prefix; bundled PhishTank / OpenPhish / URLhaus snapshot refreshed daily. | 0.9 |
| Weird host | Raw IP as host, 4+ subdomain levels, free TLDs (.xyz, .top, .tk) combined with a brand keyword anywhere in the URL. | 0.4 |

Verdict is the maximum signal. Hard allowlist (Tranco top 10k plus every real domain on the brand list) short-circuits to 0 before any check runs.

**Non-goals for v1**

- No cloud rendering, no screenshots, no VLM.
- No iOS.
- No backend. Reporting is a GitHub issue.
- No detection of in-page JavaScript behaviour; a URL-level tool cannot see it and v1 does not pretend to.
- No blocking. The user can always open the link.

## v1.1 spec — community reporting

v1.1 adds a tiny backend so that scam links reported by one user warn the next, without breaking the privacy promise and without letting scammers vote their own links clean.

**Privacy rules**

- Reporting is a deliberate tap, never automatic. The report sends the normalised final URL, the reason codes that fired, the app version and a Play Integrity token. No device ID, no account, no other URLs.
- Lookups send the SHA-256 prefix (first 4 bytes) of the final host, never the URL. The server answers with every matching full hash plus its counts; the client compares locally.
- The backend stores no IPs beyond a 24-hour rate-limit window.

**Anti-poisoning rules**

- A domain on the hard allowlist can never be flipped to bad by reports. Reports can only add, never remove.
- A report counts once per attested device per domain per 30 days. Reports without a valid Play Integrity token are stored but weighted 0.
- Community signal score = min(0.6, distinct attested reports in 7 days ÷ 50). So 30 reports in a week gives 0.6, a soft warning. Only maintainer-confirmed entries score 0.9.
- Interstitial wording for community hits is a count, not a verdict: "Reported as a scam by 47 people this week."
- A confirmed-safe list, maintainer-curated, overrides community counts for legitimate sites that get brigaded.

**Asking the user at the right moment**

- On the interstitial: one button, **Report this link**, for when they already suspect it.
- Next app open after an **Open anyway**: one card, "Was that link a scam?" — Yes / No / Not sure. This is the higher-quality signal because by then they know.

**Backend (minimal)**

- Cloudflare Workers + D1 (or Supabase). Two endpoints: `GET /lookup?prefix=` and `POST /report`.
- A daily job writes `confirmed.txt` and `reported.txt` to the GitHub repo. That published dataset is the community asset: researchers, other apps and bank fraud teams can consume it directly.

**Maintainer tooling**

- A GitHub Action that opens an issue when any domain crosses 20 reports in a week, with the URL, reason codes and a screenshot from a headless fetch, so you can confirm or dismiss in one click.

## Starter test corpus

These are synthetic patterns modelled on common Indian scam shapes, not live URLs; replace them with real samples from CERT-In advisories, Sanchar Saathi reports and your own WhatsApp spam as you collect them. Each line in the repo files is `url<TAB>expected_check<TAB>note`.

**`corpus/must_block.txt`** — every one must score ≥ 0.7

```csv
url,expected_check,note
http://sbi-rewards-redeem.xyz/claim,brand_lookalike,SBI reward points scam
http://hdfc-kyc-update.top/verify,brand_lookalike,KYC update phishing
http://indiapost-parcel.in/redelivery,brand_lookalike,courier redelivery fee scam
http://icici.bank-secure-login.com/,brand_lookalike,brand as subdomain of attacker domain
http://xn--phnepe-xya.com/,brand_lookalike,punycode homograph
http://paytm-cashback.co/app/PaytmCashback.apk,apk_download,APK install scam
http://103.21.58.12/sbi/login,weird_host,raw IP host with brand keyword
http://irctc-tatkal-booking.tk/,brand_lookalike,free TLD plus brand
http://bit.ly/3xAmPl3,unwrap,shortener resolving to a must_block target
upi://pay?pa=scammer@upi&pn=Electricity&am=4999,upi_intent,UPI collect disguised as bill
http://income-tax-refund-gov.in/claim,brand_lookalike,tax refund phishing
http://epfo-balance-check.net/,brand_lookalike,EPFO lookalike
```

**`corpus/must_pass.txt`** — every one must score < 0.3 and never show the interstitial

```csv
url,expected_check,note
https://www.onlinesbi.sbi/,allowlist,real SBI net banking
https://netbanking.hdfcbank.com/,allowlist,real HDFC
https://www.indiapost.gov.in/,allowlist,real India Post
https://www.irctc.co.in/,allowlist,real IRCTC
https://www.incometax.gov.in/,allowlist,real income tax portal
https://youtu.be/dQw4w9WgXcQ,unwrap,legit shortener to YouTube
https://wa.me/919876543210,allowlist,WhatsApp click-to-chat
https://maps.app.goo.gl/abc123,unwrap,Google Maps short link
https://www.flipkart.com/offers,allowlist,shopping
https://drive.google.com/file/d/abc/view,allowlist,shared file link
https://sbi.co.in/web/personal-banking,allowlist,real SBI corporate site
https://www.thehindu.com/news/,allowlist,news
```

**`corpus/ambiguous.txt`** — logged, not asserted; use to tune thresholds

```csv
url,expected_check,note
http://newstartup-offers.xyz/,domain_age,young domain with no brand hit — should be soft at most
https://sbicard.com/,allowlist,legit SBI subsidiary on a non-obvious domain — must be on allowlist
https://pages.razorpay.com/pl_abc,allowlist,legit payment page host used by many small businesses
```

## Keeping the lists live

The test corpus stays static and in the repo; the runtime lists the app actually consults are rebuilt daily from upstream feeds plus community reports and pulled by the app as a delta. The static bundle is only the day-zero snapshot.

**Two different things**

|  | Test corpus (`corpus/`) | Runtime lists (`data/` + feed) |
| --- | --- | --- |
| Purpose | Keep the checks correct; CI fails if they drift | Decide verdicts on real taps |
| Changes | By hand, via PR, with a note per line | Automatically, daily, from feeds |
| Size | Hundreds of lines | Tens of thousands of domains |
| Lives in | Git | A published, versioned bundle the app downloads |

**Feed sources, by list**

| List | Upstream sources | Refresh |
| --- | --- | --- |
| Allowlist | Tranco top 10k; RBI list of scheduled banks' domains; NPCI UPI app and handle list; `.gov.in` / `.nic.in` zones; Indian brand domains curated in-repo | Daily |
| Blocklist | URLhaus, OpenPhish, PhishTank, Google Safe Browsing Update API (hash prefixes), CERT-In advisories, community-confirmed entries from v1.1 | Hourly for feeds, daily bundle |
| Brand lookalike patterns | Curated in-repo; new brands added via PR when a scam wave targets them | On merge |
| Shortener domains | Curated in-repo plus any host that returned ≥ 3 redirect hops in community reports | Weekly |

**Pipeline (GitHub Actions, no servers for v1)**

1. A scheduled workflow fetches every feed, normalises to one schema (`domain`, `source`, `first_seen`, `score`, `expires`), de-duplicates, and applies the allowlist override.
2. It builds a compact bundle: allowlist as a Bloom filter (\~100 KB for 10k domains), blocklist as SHA-256 prefix set plus full hashes, brand patterns as JSON. Signs it with the project key.
3. Publishes the bundle as a GitHub Release asset and a `latest.json` manifest with version and hashes.
4. The app checks the manifest on Wi-Fi once a day, downloads only if the version changed, verifies the signature, swaps atomically. Offline, it uses whatever bundle it has; the bundled day-zero snapshot guarantees it is never empty.
5. Entries expire: feed entries after 30 days unless re-seen, community entries after 14 days unless re-reported, so the blocklist does not grow forever.

**When it outgrows Actions**

The v1.1 backend takes over step 1–3 and adds the community-report ingest. Nothing in the app changes; it still pulls a signed bundle from a URL. That is the scalability seam: feeds and bundle format are the contract, and where the bundle is built is an implementation detail.

**Add to `CLAUDE.md`** under Architecture: `feeds/` — the fetch-normalise-bundle pipeline, runnable locally with `./gradlew :feeds:build`.

## Week one — milestones and first prompts

The week is done when a release APK is on your phone, both corpus files are green, and the repo is public with a README and screenshots.

- [ ] **Day 1 — Repo.** Create the GitHub repo, Apache-2.0 licence, paste `CLAUDE.md`, add `corpus/` files, write a 10-line README stating what the app does and the privacy promise. Install Android Studio and Claude Code.
- [ ] **Day 2–3 — Intercept and forward.** Milestone 1 below. Done when tapping a WhatsApp link opens Chrome via LinkGuard with no visible delay.
- [ ] **Day 4–5 — Checks engine.** Milestone 2. Done when `./gradlew :core:test` passes against both corpus files.
- [ ] **Day 6 — Interstitial and settings.** Milestone 3. Done when a must\_block URL shows the warning in English, Hindi and Tamil.
- [ ] **Day 7 — Release.** Signed APK on GitHub Releases, README screenshots, post to r/developersIndia and the Indian infosec Twitter/X community asking for scam samples.

**Milestone 1 prompt (paste into Claude Code, plan mode)**

```markdown
Read CLAUDE.md. Create the Android project skeleton with modules app/, core/, data/. In app/, implement a LinkInterceptActivity that registers as a handler for http and https (so the system offers it as a default browser), receives the VIEW intent, and immediately forwards the URL to a configured browser package (default com.android.chrome) using an explicit intent, then finishes. Add a minimal first-launch screen that explains the app and deep-links to the system default-apps settings. No checks yet. Explain how the default-browser selection works on Android 12+ and any pitfalls with Chrome Custom Tabs vs explicit intents before you write code.
```

**Milestone 2 prompt**

```markdown
Read CLAUDE.md and corpus/. In core/, implement NormalisedUrl, the Check interface, Signal, and Verdict as described. Implement these checks in this order, each with unit tests: Unwrap, Allowlist, ApkDownload, BrandLookalike, UpiIntent, WeirdHost, DomainAge, Reputation. Load the brand list and allowlist from data/. Write a corpus test that runs every must_block.txt line and asserts score >= 0.7 and every must_pass.txt line and asserts score < 0.3. Show me the plan and the Signal data class before implementing.
```

**Milestone 3 prompt**

```markdown
Read CLAUDE.md. Wire core/ into LinkInterceptActivity: run checks with a 300 ms budget (skip network checks on timeout), forward silently below 0.3, otherwise show InterstitialScreen in Compose: final host in large text, one reason sentence, Go back (primary) and Open anyway. Add strings for en, hi, ta. Add a Settings screen: choose browser, toggle checks, Report a link (opens a prefilled GitHub issue URL). Add Compose UI tests for the interstitial.
```

**Open questions to settle before Day 2**

- App name. LinkGuard is a placeholder; check Play Store and GitHub for collisions.
- Package name and signing key — keep the keystore out of the repo.
- Distribution, decided: go live on GitHub Releases plus F-Droid first; submit to Play Store in parallel and let its default-browser policy review run its course. Play listing is not a gate for launch.

**F-Droid build constraints**

F-Droid compiles from source on its own servers and rejects proprietary dependencies, so the repo must build clean with nothing Google-specific.

- Two product flavours: `foss` (F-Droid) and `play` (Play Store). Only `play` includes Play Integrity and the Google Safe Browsing Lookup API.
- In `foss`, the Reputation check uses only the open feeds in the signed bundle (URLhaus, OpenPhish, PhishTank, community-confirmed). The check interface is the same; the Google client is a flavour-specific implementation.
- In `foss`, reports are accepted without an attestation token and weighted lower (see v1.1 anti-poisoning rules).
- No Firebase, no Google Play Services, no Crashlytics in either flavour; crash reports are opt-in via ACRA or nothing.
- Reproducible build: pin Gradle, AGP and Kotlin versions; no network access during build; the bundle snapshot in `data/` is committed, not fetched at build time.
- Add to `CLAUDE.md` Non-negotiables: "The `foss` flavour must build and pass all tests with zero Google dependencies."
