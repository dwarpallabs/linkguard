# LinkGuard

LinkGuard is an open-source Android app that sits between the links you tap in WhatsApp, Telegram or SMS and your real browser.
It checks each link on your phone in a fraction of a second. Safe links open straight away and you see nothing.
If a link looks like a scam, you get one plain-language warning in English or any of India's 22 official languages. LinkGuard never blocks: you can always choose "Open anyway".

**LinkGuard is not a browser.** Android lists it as your "browser app" only so it can see links first; it never shows web pages and opens every link in the browser you already use (Chrome or any other), so your bookmarks, history, saved passwords and tabs stay exactly as they are.

<p>
  <img src="docs/screenshots/warning_brand_en.png" width="200" alt="Warning: this website is pretending to be HDFC Bank">
  <img src="docs/screenshots/warning_brand_hi.png" width="200" alt="The same warning in Hindi">
  <img src="docs/screenshots/warning_brand_ta.png" width="200" alt="The same warning in Tamil">
  <img src="docs/screenshots/home_en.png" width="200" alt="Settings: default browser, checks, brands, report a link">
</p>

## What it catches
Fake bank and brand websites (SBI, HDFC, PhonePe, Paytm, India Post, IRCTC and more, including look-alike letters and typos), app downloads from outside the Play Store, UPI payment requests hidden behind links, raw-IP and throwaway addresses, brand-new websites, and known scam sites. Short links (bit.ly and others) are followed first so the real destination is checked.

## Install
> **v0.3.2 is a pre-release.** Text in Indian languages is machine-translated (corrections welcome, see [Languages](#languages)), the scam blocklist is still small, and community warnings need reports from several people before they appear.

1. Download `linkguard-0.3.2-foss.apk` from [Releases](https://github.com/dwarpallabs/linkguard/releases).
2. Open it and allow "Install unknown apps" when Android asks.
3. Open LinkGuard and tap **Set as default browser**. Pick the browser links should open in (Chrome is preselected).

Android 8 or newer. Apps like Telegram, Instagram, Facebook and LinkedIn often open links in their own in-app browser, where LinkGuard can't check them. Turn that off in the app's settings (Telegram: Settings → Chat Settings → In-app browser), or use the page menu's "Open in browser".

## Privacy
LinkGuard never logs, stores or sends the links you open. No accounts, no ads, no analytics, no trackers. Most checks run entirely on your phone. It goes online only to:
- ask a link shortener (bit.ly, t.co, …) where a short link leads; that service would see the same request if you opened the link anyway;
- ask a domain registry when a website was registered, sending only the domain name (`example.com`), never the full link;
- ask the LinkGuard server whether a website is a known scam, sending only a 4-byte fingerprint of the website's name, so the server cannot tell which site you opened;
- download the list of known-safe websites (as fingerprints, signed by Dwarpal Labs);
- send a report, only when you tap **Send report**, **Report** or **Yes, it was a scam**. Reports from a warning screen send only the website's name (`example.com`), never the full link. Reports are tied to an anonymous, per-website token, so your reports can't be linked together or traced back to you.

One exception to "never stores": when you tap **Open anyway** on a warning, LinkGuard keeps that one website's name (not the link) on your phone so it can ask you later whether it was a scam. It is never backed up, it is deleted as soon as you answer or after 7 days, and websites you have already answered about are kept only as a scrambled fingerprint. LinkGuard also learns two things on your phone, so it can help you more: websites you said were fine stop getting the mild "Be careful" warning, and after you open your bank's or app's real website a few times, LinkGuard warns more strongly about fakes of it. Both are kept only as scrambled fingerprints, never backed up, never sent, and you can clear them in Settings.

Full policy: [dwarpallabs.com/linkguard/privacy](https://dwarpallabs.com/linkguard/privacy).

## Check a message before you tap
Not sure about a message? Share it to LinkGuard: tap **Share** in WhatsApp, SMS or any app, then **Check with LinkGuard**. LinkGuard finds every link in the message (including `wa.me`, `t.me` and UPI payment links) and shows a verdict for each one, checked exactly as if you had tapped it. The message itself never leaves your phone.

## Using the data
Scam websites confirmed by LinkGuard are published every day, 48 hours after they are confirmed, at [dwarpallabs/linkguard-data](https://github.com/dwarpallabs/linkguard-data) under the ODbL 1.0. The newest list is always at `https://github.com/dwarpallabs/linkguard-data/releases/latest/download/confirmed.txt`. Please credit it as: *Scam website data: LinkGuard by Dwarpal Labs (ODbL 1.0), including PhishDestroy (MIT).* Reports that nobody confirmed are never published, and neither are the links people open.

## Report a scam link


On a warning screen, tap **Report this link**, then **Report**; only the website's name is sent. If you tapped **Open anyway**, LinkGuard asks once, next time you open the app, "Was that link a scam?". For a link LinkGuard missed: Settings → **Report a link**.
 Reports go privately to Dwarpal Labs and are never published. A reported website becomes public only if Dwarpal Labs confirms it is a scam (see [Using the data](#using-the-data)); weekly report counts per brand are published without any websites.
 When three or more different people report the same website within a week, other LinkGuard users automatically see a soft warning: "reported as a scam by N LinkGuard users this week". Reports alone never cause the strong red warning, and well-known sites can't be reported into a warning.

## Data sources
- Phishing and scam websites: [PhishDestroy destroylist](https://github.com/phishdestroy/destroylist) (MIT), its live root-domain list.
- Malware hosts: [URLhaus](https://urlhaus.abuse.ch) by abuse.ch, used under its terms for not-for-profit projects.
- Popular sites for the safe list: the [Tranco](https://tranco-list.eu) research ranking (Le Pochat et al., NDSS 2019). The top 10,000 open without a server check, and no site in the top 100,000 is ever blocked by a feed.

URLhaus and PhishDestroy are free for not-for-profit use. A paid LinkGuard service would need commercial threat feeds instead.
- Community reports from LinkGuard users.

## Where the lists live
The scam blocklist and the safe-site allowlist are kept in the LinkGuard server database, not in this repository, so scammers cannot read them. Phones check the blocklist by 4-byte hash prefix and receive the allowlist as signed hashes. The detection rules (look-alike brands, app downloads, unusual addresses, UPI requests) are all here in the open.

## Languages
LinkGuard follows your phone's language. On Android 13 and newer you can also pick a language just for LinkGuard in *Settings → Apps → LinkGuard → Language*.

English, Assamese, Bengali, Bodo, Dogri, Gujarati, Hindi, Kannada, Kashmiri, Konkani, Maithili, Malayalam, Manipuri (Meetei Mayek), Marathi, Nepali, Odia, Punjabi, Sanskrit, Santali (Ol Chiki), Sindhi, Tamil, Telugu and Urdu.

Every language except English is **machine-translated**, so some wording will be off. If something reads wrong, tell us the wrong words and how you would say them, whichever way is easiest:
- the in-app screen: *Settings → Wrong translation? Tell us*
- [the translation form on GitHub](https://github.com/dwarpallabs/linkguard/issues/new?template=translation.yml)
- email [support@dwarpallabs.com](mailto:support@dwarpallabs.com)

Corrections go straight into `app/src/main/res/values-<language>/strings.xml`; pull requests are welcome too. `python3 scripts/check_strings.py` checks that a language file is complete.

On Android 8–12, LinkGuard can only use a language the phone itself can be set to, and some phones don't offer every language (Bodo, Dogri or Santali, for example). Android 13 and newer can set any of these just for LinkGuard. Android has included fonts for every script used here, Ol Chiki and Meetei Mayek included, since Android 8.

## Contact
Questions, bugs or privacy concerns: [support@dwarpallabs.com](mailto:support@dwarpallabs.com) or [open an issue](https://github.com/dwarpallabs/linkguard/issues).

## Build from source
Needs JDK 17+ (Android Studio's bundled JBR works).
```
./gradlew :core:test                 # engine + corpus tests (offline)
./gradlew :app:assembleFossRelease   # unsigned unless signing properties are in ~/.gradle/gradle.properties
```
`core/` is pure Kotlin with no Android imports; `corpus/` holds the must-block / must-pass test URLs; `data/` holds the bundled lists. Every change keeps the privacy rules above and both corpus files green.

## Verifying the APK
Release APKs are signed by Dwarpal Labs. Signing certificate SHA-256:
```
3b7a7bc8b029e60667a269c9298067f64922f79ec6e8e1ebbf69569795405cee
```
Check with `apksigner verify --print-certs linkguard-0.3.2-foss.apk`.

## License
[Apache-2.0](LICENSE). Bundles the [Public Suffix List](https://publicsuffix.org) (MPL-2.0) and IANA's RDAP bootstrap data.
