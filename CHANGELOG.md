# Changelog

All notable changes to LinkGuard. Versions follow the GitHub releases.

## Unreleased

### Added
- **Check with LinkGuard** in the share sheet: share any message to LinkGuard to see a verdict for every link in it, including `wa.me`, `t.me` and UPI payment links, links glued to Hindi or Tamil words, and links broken up with invisible characters. Each result can be copied (website name only) or reported. The message itself never leaves the phone.
- A tip on the home screen explains how to check a message.
- After you tap *Go back* on a fake bank or brand warning, LinkGuard says how to reach the real one next time ("HDFC Bank’s real website is hdfcbank.com…"), at most once per brand a month.
- **Look without opening**: on a *Be careful* warning, see that one page in a locked view first. No JavaScript, nothing you type is sent, no downloads, no cookies, links inside it don't open, and it closes after a minute. It runs apart from the rest of LinkGuard and is wiped when it closes.
- **Listen**: a button on the warning reads it aloud with the phone's own voice, in LinkGuard's language. It appears only when the phone has an offline voice for that language, and not while TalkBack is on (TalkBack already reads the screen).
- **Check what this app can do**: on a warning about an app download, one tap downloads the app file, looks inside it and lists in plain words what it could do on your phone (read your SMS and OTPs, control your phone, read your notifications, …). An app that uses a bank's name and wants your SMS gets a clear warning. The file is deleted straight after, and LinkGuard never installs it.
- **UPI payment requests** are looked at more closely: a request that sets up **automatic payments** (a mandate) and one whose payee name uses a brand like SBI while the money goes to someone else's account now get the strong warning. Google Pay, PhonePe and Paytm payment links behind a web link are recognised too. The payment card shows the payee, the UPI ID and the amount the Indian way (₹1,25,000).
- When a warning shows for a link that came by SMS, it adds: "This link came by SMS. Be careful with links in messages from people you don’t know." LinkGuard only asks Android which app is the SMS app; it never reads messages.
- **Scam campaigns**: websites that share their hosting or page with a confirmed scam website, and that several people reported, now get the strong warning: "This website looks like part of a group of scam websites. 7 LinkGuard users reported them this week."
- **Trusted by me**: a website you answered *No, it was fine* about no longer gets the mild *Be careful* warning. It still gets every strong, app-download and UPI warning.
- **Your banks and apps**: after you open a brand's real website three times, a fake of it gets a stronger warning that says you use it ("You use SBI…"). Both are learned only on the phone, stored as scrambled fingerprints, never sent, and can be cleared in Settings under *Learned on this phone*.

- **Five global languages**: Chinese (Simplified), Spanish, Arabic, French and Portuguese (Brazil), machine-translated like the Indian languages. Arabic is shown right to left. Corrections are welcome through the same feedback form.

- **Links opened by Google Messages, Gmail and other apps that use Custom Tabs are now checked.** These apps used to open links straight in Chrome, so LinkGuard never saw them. LinkGuard now tells them it supports Custom Tabs, checks the link, then opens it in your browser.

### Data
- A **weekly scam report** is published every Monday at [linkguard-data/reports](https://github.com/dwarpallabs/linkguard-data/tree/main/reports): reports per impersonated brand, newly confirmed scam websites by ending, and active campaigns, as counts.
- PhishTank's verified phishing list now feeds the server blocklist (about 24,000 more scam websites). Every source's terms, and the Indian sources checked but not usable yet, are in `feeds/SOURCES.md`.
- Scam websites confirmed by LinkGuard are now published daily at [dwarpallabs/linkguard-data](https://github.com/dwarpallabs/linkguard-data) (ODbL 1.0), 48 hours after confirmation, with weekly report counts per brand. Unconfirmed reports and the links people open are never published.

### Fixed
- Android 15 and newer: the *Send report* button no longer hides under the keyboard on the *Report a link* screen. LinkGuard now draws edge-to-edge from the first frame on every screen and no longer uses the system-bar colour settings that Android 15 deprecated.

## 0.3.2 — 2026-10-19

### Added
- **Report this link** on a warning screen now asks once in a sheet that says what is sent, before anything leaves the phone.
- **"Was that link a scam?"** card on the next app open after *Open anyway*, with three answers: *Yes, it was a scam* (sends a report), *No, it was fine* and *Not sure*. LinkGuard asks only once per website.

### Changed
- Reports from a warning screen send only the website's name, never the full link, its path, query or fragment. Reports typed into Settings → *Report a link* are unchanged.
- The thank-you after a report now says what the report does: "Thank you. Your report helps warn other people."

### Fixed
- A brand-new install no longer shows a false warning on a popular website in the moment before the safe-site list has downloaded: the first check waits up to 3 seconds for it.
- Visual fixes across the app to match the design system: text sizes, spacing and touch targets.

### Privacy
- After *Open anyway*, the website's name (not the link), the time and the reasons for the warning are kept on the phone until the question is answered or for 7 days at most. This file is never backed up or copied to a new phone. Websites already asked about are kept only as a scrambled fingerprint.
- The per-phone reporter secret is no longer included in Google backups or phone-to-phone transfers. Existing installs move it out on first use.
