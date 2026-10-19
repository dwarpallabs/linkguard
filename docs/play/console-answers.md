# Google Play Console: answers for LinkGuard

Prepared answers for each Play Console form, with the reason for each. Re-check against the
console's current wording when filling them in; if a question differs, prefer the more
conservative (more disclosing) answer.

## Account
- **Type:** personal developer account. Developer name: **Dwarpal Labs**, kept plain with no "Pvt Ltd", "LLP" or "Inc", since there is no registered company yet. Google also displays the account holder's legal name, country and developer email.
- **Developer email (public):** `support@dwarpallabs.com`, forwarded to the owner's inbox via Cloudflare Email Routing.
- **Website:** https://dwarpallabs.com
- **Later:** when Dwarpal Labs is incorporated, create an organisation account and **transfer** the app. Users and reviews carry over, and the signing key stays the same.
- **New personal accounts must run a closed test first:** at least **12 testers** opted in for **14 consecutive days** before applying for production. See "Closed test" below.

## App setup
| Field | Answer | Why |
|---|---|---|
| App name | LinkGuard: Scam Link Checker | `listing-en.md` |
| Default language | English (India), en-IN | Translations: bn-BD, gu, hi-IN, kn-IN, ml-IN, mr-IN, ne-NP, pa, ta-IN, te-IN, ur (`listing-<lang>.md`). Play has no listing languages for Assamese, Bodo, Dogri, Kashmiri, Konkani, Maithili, Manipuri, Odia, Sanskrit, Santali or Sindhi; the app itself supports them |
| App or game | App | |
| Free or paid | Free | |
| Category | Tools | It's a utility that sits before the browser. It isn't a "Communication" or browser app |
| Tags | Security, Utilities (pick the closest offered) | |
| Contact email | support@dwarpallabs.com | |
| Privacy policy | https://dwarpallabs.com/linkguard/privacy | |
| Ads | **No, contains no ads** | |
| App access | **All functionality is available without special access** | No login. Reviewers: open the app → Set as default browser → tap any link |

## Content rating (IARC questionnaire)
- **Category:** "Utility, productivity, communication or other".
- Violence, sexuality, language, controlled substances, gambling: **No** to all.
- **Users interact or share content with each other:** **No.** Reports go privately to the developer and aren't visible to other users. The only thing other users see is an aggregated count ("reported by N users").
- **Shares the user's location:** No. **Digital purchases:** No. **Unrestricted internet access** (a browser): **No.** LinkGuard displays no web content; links open in the user's own browser.
- **Expected rating:** Everyone / 3+ (IARC).

## Target audience and content
- **Target age groups:** **18 and over.** The app is meant for adults receiving scam messages. Choosing 18+ keeps it outside the Families policy.
- **Appeals to children:** No.

## Data safety
**Overview:** data is encrypted in transit (HTTPS): **Yes**. Users can request deletion: **No**, because there are no accounts and nothing ties data to a person; reports are deleted automatically after 30 days. This is explained in the privacy policy.

| Data type | Collected / shared | Required? | Processed ephemerally? | Purpose | Why |
|---|---|---|---|---|---|
| **Web browsing history** | **Shared** (with third parties) | **Optional** | **Yes** | Fraud prevention, security and compliance | (1) For short links (bit.ly…), the link is sent to that shortener to see where it leads. (2) For unfamiliar sites, the site's **domain name** is sent to its official domain registry (RDAP) to check its age. Both have a switch in Settings ("Follow short links", "Brand-new websites"). Nothing is stored |
| **App activity → Other user-generated content** | **Collected** (by Dwarpal Labs) | **Optional** (only when the user taps Report / Send report) | No (kept up to 30 days) | Fraud prevention, security and compliance | From the warning screen or the "Was that link a scam?" card: the website's name (`example.com`) and the warning's reason codes. From Settings → Report a link: the link the user typed and the optional note |
| **Device or other IDs** | **Not collected** | | | | The report token is an HMAC of a phone-only secret **per website**. It can't identify the phone or link two reports together, so it isn't a device ID. Mentioned here for transparency |

**Not declared as user data:** the on-phone note kept after *Open anyway* (`after_open.xml`: one website's name, time and reason codes, never backed up, deleted on answer or after 7 days) stays on the device and is never collected, so it is not a data-safety item; it is described in the privacy policy. Likewise the 4-byte hash-prefix lookups to `api.dwarpallabs.com`. They carry only 32 bits of a website-name hash, which many websites share, so they can't identify the site or the user. They're described in the privacy policy.

**None of:** location, personal info (name, email, phone…), financial info, health, messages, photos, audio, files, calendar, contacts, app info and performance, crash logs, advertising ID.

## Other declarations
| Declaration | Answer | Why |
|---|---|---|
| Government app | No | Not affiliated with any government. The listing has a disclaimer |
| Financial features | **None** | It warns about UPI requests, but doesn't provide payments, loans, banking or investment |
| Health | No | |
| News | No | |
| COVID-19 / elections | No | |
| Advertising ID | Not used; the app declares no `AD_ID` permission | |
| Sensitive permissions | None. The app requests only `INTERNET` | |

## Policy notes for review
- **Default-browser role:** LinkGuard asks to become the default browser so it can check links first, and it says clearly in the app and the listing that it's **not a browser** and opens every link in the user's own browser. Play's restricted default-handler permissions (SMS, Call log) don't apply.
- **Brand names** (banks, wallets, couriers) appear only descriptively. The listing states that LinkGuard isn't affiliated with any of them, and the store graphics contain no third-party brand names. The screenshot with "HDFC Bank" shows LinkGuard's own warning about a **fake** HDFC site on a fictional `.example` domain. If a reviewer objects, swap in a screenshot of the APK warning.
- **Never blocks:** every warning has "Open anyway". This matters for the Device and Network Abuse policy.

## Closed test (required for new personal accounts)
1. **Create a Google Group** (for example `linkguard-testers@googlegroups.com`) and add at least **12** testers' Google account emails. Aim for 15–20 in case some drop out.
2. **Play Console → Testing → Closed testing:** create a track, add the Google Group as testers, upload the bundle, and roll it out.
3. **Share the opt-in link.** Each tester opts in and installs LinkGuard **from Play**, then keeps it installed and opted in for **14 days in a row**.
4. **On day 15 or later, apply for production access** in the Dashboard. Google asks how testing went: describe the feedback and the fixes made (for example, the Hindi and Tamil review).

Good tester sources: friends and family on Android and the r/developersIndia post. The message to send them is in `testers.md`.

## Uploading
- **Bundle:** `./gradlew :app:bundlePlayRelease` → `app/build/outputs/bundle/playRelease/app-play-release.aab`. Check it with `jarsigner -verify -verbose -certs <aab> | grep CN=` (expect `CN=Dwarpal Labs`). The first Play upload is 0.3.2 (versionCode 7).
- **Play App Signing:** choose **"Use existing app signing key from Java keystore"**. Download Google's `pepk.jar` and `encryption_public_key.pem` from that screen into one folder, then run (Android Studio's JDK; it prompts for the keystore and key passwords, which are never written anywhere):
  ```
  "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" -jar pepk.jar --keystore="$HOME/.android-keys/linkguard-release.jks" --alias=linkguard --output=linkguard-pepk.zip --include-cert --rsa-aes-encryption --encryption-key-path=encryption_public_key.pem
  ```
  Upload `linkguard-pepk.zip`. Delete it afterwards; it holds the private key, encrypted for Google.
- **Upload key:** the same certificate, `3b7a7bc8…95405cee`.
- **Graphics:** `graphics/icon-512.png`, `graphics/feature-1024x500.png`, and `graphics/screenshots/*_{en,hi,ta}.png` (four per language, in numbered order; re-take with `take_screenshots.sh`. Scene 4, the community "reported by N users" warning, is added once a test site with 3+ reports exists on the server). Other listing languages reuse the English screenshots.
- **Release notes:** the "Release notes" section of each `listing-*.md`.
