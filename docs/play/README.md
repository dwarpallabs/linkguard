# Google Play listing

Everything needed to publish LinkGuard on Google Play. Store text and graphics are public anyway.

| File | What |
|---|---|
| `listing-<lang>.md` | Title, short and full description, release notes: English plus the 11 Indian languages Play offers (bn, gu, hi, kn, ml, mr, ne, pa, ta, te, ur), machine-translated. Check limits with `python3 check_listing.py` |
| `console-answers.md` | Answers for every Play Console form (data safety, content rating, target audience, declarations), the closed-test steps and upload steps |
| `graphics/` | 512×512 icon, 1024×500 feature graphic, 1080×1920 phone screenshots per language (captured by `take_screenshots.sh` on the emulator, fitted by `make_assets.py`) |
| `testers.md` | The message to send closed-test testers |

Build the bundle with `./gradlew :app:bundlePlayRelease` (signed only when the signing properties are in `~/.gradle/gradle.properties`).
