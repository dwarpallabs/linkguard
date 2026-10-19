# Changelog

All notable changes to LinkGuard. Versions follow the GitHub releases.

## Unreleased

### Added
- **Report this link** on a warning screen now asks once in a sheet that says what is sent, before anything leaves the phone.
- **"Was that link a scam?"** card on the next app open after *Open anyway*, with three answers: *Yes, it was a scam* (sends a report), *No, it was fine* and *Not sure*. LinkGuard asks only once per website.

### Changed
- Reports from a warning screen send only the website's name, never the full link, its path, query or fragment. Reports typed into Settings → *Report a link* are unchanged.
- The thank-you after a report now says what the report does: "Thank you. Your report helps warn other people."

### Privacy
- After *Open anyway*, the website's name (not the link), the time and the reasons for the warning are kept on the phone until the question is answered or for 7 days at most. This file is never backed up or copied to a new phone. Websites already asked about are kept only as a scrambled fingerprint.
