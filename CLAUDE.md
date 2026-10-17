# LinkGuard (working name)

Open-source Android app that sits between links tapped in WhatsApp/Telegram/SMS and the user's real browser. It inspects the URL in under 300 ms and either forwards silently or shows one plain-language warning. Target users: first-time smartphone users in India. Licence: Apache-2.0.

## Non-negotiables
- Never log, store or transmit the URLs a user opens. All v1 checks run on-device. Network is used only for:
  - redirect lookups (HEAD, HTTPS) to hosts on `data/…/shorteners.txt`, never to the final destination;
  - RDAP registration-date lookups that send only the registrable domain (`example.com`), over HTTPS;
  - reputation lookups to `api.dwarpallabs.com` by hash prefix (the first 4 bytes of a domain's SHA-256, never the domain);
  - downloading the signed, hashed allowlist from `api.dwarpallabs.com`;
  - user-initiated reports to `api.dwarpallabs.com`, sent only when the user taps Send: the link, optional note, the website's registrable domain and an anonymous per-website token (HMAC of a secret that never leaves the phone, so one phone's reports cannot be linked together).
  Nothing else, and never the full URL to anyone but a shortener the link already points at. Any in-memory cache dies with the process; nothing about opened links is written to disk.
- The allowlist and blocklist live in the LinkGuard server database (`dwarpallabs/linkguard-server`), never in this repo. Only test fixtures under `core/src/test/resources` may contain list entries.
- Community reports work without human review but can only ever add a *soft* warning ("reported by N users", score ≤ 0.6, 3+ distinct reporters in 7 days). They never produce a strong warning and never add a site to the allowlist; allowlisted sites are immune.
- Never add friction to a safe link. If every check passes, the user sees nothing.
- Never block. The warning always offers "Open anyway".
- No accounts, no analytics SDKs, no ads, no third-party trackers.
- Minimum SDK 26 (Android 8). Must run well on a 2 GB RAM phone.
- The foss flavour must build and pass all tests with zero Google dependencies.

## Architecture
- `app/` — Android UI: link intercept activity, interstitial, settings. Kotlin, Jetpack Compose.
- `core/` — pure Kotlin/JVM module, no Android imports. URL normalisation, redirect unwrapping, all checks, verdict scoring. 100% unit-testable.
- `data/` — bundled public data: brands and their official domains, shortener and file-host lists, free TLDs, Public Suffix List, RDAP bootstrap. No allow/block lists.
- `corpus/` — test URLs: `must_block.txt`, `must_pass.txt`, `ambiguous.txt`. Every check change must keep both files green.
- `feeds/` — daily fetch-normalise pipeline (URLhaus malware hosts + PhishDestroy phishing/scam domains → blocklist, Tranco top 10k → allowlist, Tranco top 100k immune from blocking), run by `.github/workflows/feeds.yml`. Reuses `core` normalisation and writes to the server database only; never prints or commits list contents (CI logs are public). Local dry run: `./gradlew :feeds:run`.

## Conventions
- Kotlin 2.x, Gradle Kotlin DSL, JUnit 5 for `core/`, Compose UI tests for `app/`.
- Each check is a class implementing `Check { fun run(url: NormalisedUrl): Signal }`. Signals carry a score (0.0–1.0), a reason code and a user-facing message key.
- Verdict = max of signals, not sum. Thresholds: < 0.3 forward silently, 0.3–0.7 soft warning, > 0.7 strong warning.
- Strings in `values/` (English) plus a `values-<lang>/` folder for each of India's 22 scheduled languages (three-letter codes as `values-b+brx` etc.). Every user-facing message exists in every folder; `scripts/check_strings.py` (run in CI) enforces it. Non-English text is machine-translated; corrections come from the community (in-app feedback screen, GitHub translation form, email; Play reviews once LinkGuard is on the Play Store).
- Conventional commits. One milestone per PR.

## Workflow for Claude
- The full product spec is in `docs/build-brief.md`. Read the relevant section before starting any milestone.
- The design system is in `docs/design-system.md`. Follow it for every app screen, website page, graphic and user-facing string (colours, type, spacing, icons, wording).
- Always start in plan mode for a new milestone. Show the plan; wait for approval.
- Run `./gradlew :core:test` before declaring any check done.
- When adding a check, add at least 3 URLs to `must_block.txt` and 3 to `must_pass.txt` that exercise it.
- Prefer boring, dependency-free Kotlin over libraries. Ask before adding a dependency.
- If a task touches the intent-filter / default-browser flow, say so explicitly; it is the riskiest part of the app.
