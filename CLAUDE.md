# LinkGuard (working name)

Open-source Android app that sits between links tapped in WhatsApp/Telegram/SMS and the user's real browser. It inspects the URL in under 300 ms and either forwards silently or shows one plain-language warning. Target users: first-time smartphone users in India. Licence: Apache-2.0.

## Non-negotiables
- Never log, store or transmit the URLs a user opens. All v1 checks run on-device. Network is used only for reputation lookups by hash prefix and for feed updates, never with the full URL.
- Never add friction to a safe link. If every check passes, the user sees nothing.
- Never block. The warning always offers "Open anyway".
- No accounts, no analytics SDKs, no ads, no third-party trackers.
- Minimum SDK 26 (Android 8). Must run well on a 2 GB RAM phone.
- The foss flavour must build and pass all tests with zero Google dependencies.

## Architecture
- `app/` — Android UI: link intercept activity, interstitial, settings. Kotlin, Jetpack Compose.
- `core/` — pure Kotlin/JVM module, no Android imports. URL normalisation, redirect unwrapping, all checks, verdict scoring. 100% unit-testable.
- `data/` — bundled lists: Indian brand allowlist, lookalike patterns, shortener domains, feed cache.
- `corpus/` — test URLs: `must_block.txt`, `must_pass.txt`, `ambiguous.txt`. Every check change must keep both files green.
- `feeds/` — the fetch-normalise-bundle pipeline, runnable locally with `./gradlew :feeds:build`.

## Conventions
- Kotlin 2.x, Gradle Kotlin DSL, JUnit 5 for `core/`, Compose UI tests for `app/`.
- Each check is a class implementing `Check { fun run(url: NormalisedUrl): Signal }`. Signals carry a score (0.0–1.0), a reason code and a user-facing message key.
- Verdict = max of signals, not sum. Thresholds: < 0.3 forward silently, 0.3–0.7 soft warning, > 0.7 strong warning.
- Strings in `values/`, `values-hi/`, `values-ta/`. Every user-facing message has all three.
- Conventional commits. One milestone per PR.

## Workflow for Claude
- The full product spec is in `docs/build-brief.md`. Read the relevant section before starting any milestone.
- Always start in plan mode for a new milestone. Show the plan; wait for approval.
- Run `./gradlew :core:test` before declaring any check done.
- When adding a check, add at least 3 URLs to `must_block.txt` and 3 to `must_pass.txt` that exercise it.
- Prefer boring, dependency-free Kotlin over libraries. Ask before adding a dependency.
- If a task touches the intent-filter / default-browser flow, say so explicitly; it is the riskiest part of the app.
