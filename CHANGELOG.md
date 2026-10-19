# Changelog

All notable changes to LinkGuard. Versions follow the GitHub releases.

## Unreleased

### Added
- **Check with LinkGuard** in the share sheet: share any message to LinkGuard to see a verdict for every link in it, including `wa.me`, `t.me` and UPI payment links, links glued to Hindi or Tamil words, and links broken up with invisible characters. Each result can be copied (website name only) or reported. The message itself never leaves the phone.
- A tip on the home screen explains how to check a message.
- **Trusted by me**: a website you answered *No, it was fine* about no longer gets the mild *Be careful* warning. It still gets every strong, app-download and UPI warning.
- **Your banks and apps**: after you open a brand's real website three times, a fake of it gets a stronger warning that says you use it ("You use SBI…"). Both are learned only on the phone, stored as scrambled fingerprints, never sent, and can be cleared in Settings under *Learned on this phone*.

- **Five global languages**: Chinese (Simplified), Spanish, Arabic, French and Portuguese (Brazil), machine-translated like the Indian languages. Arabic is shown right to left. Corrections are welcome through the same feedback form.

### Data
- Scam websites confirmed by LinkGuard are now published daily at [dwarpallabs/linkguard-data](https://github.com/dwarpallabs/linkguard-data) (ODbL 1.0), 48 hours after confirmation, with weekly report counts per brand. Unconfirmed reports and the links people open are never published.

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
