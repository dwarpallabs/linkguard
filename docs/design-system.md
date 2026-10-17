# Design system — Dwarpal Labs › LinkGuard

> Read this before touching any screen, string, icon, colour or marketing graphic.
> The product non-negotiables still win where they overlap (never block, never add friction to a safe link, no trackers).

---

## Scope — one system, every surface

This document governs **everything a person can see or read from Dwarpal Labs or LinkGuard**:

- the Android app (every screen, notification, launcher label);
- the websites (dwarpallabs.com, the LinkGuard product page, privacy/legal pages, help pages, any future web tool);
- the Play Store listing and its graphics, GitHub README, release notes, emails and social posts.

The same colour tokens, the same typeface, the same type scale, the same spacing, the same icons and **the same words** are used on all of them. If a user reads "Open anyway" in the app, the website calls it "Open anyway" too — not "Proceed" or "Continue". A change to a token or a glossary term is made here first, then applied to app *and* web in the same PR or a linked one.

---

## 0. Who we design for

Design for the hardest user first; everyone else benefits.

| Primary user | What that means for design |
|---|---|
| 60+ year-old, first smartphone, reading glasses, maybe low literacy | Big text, high contrast, one decision per screen, words + icon together |
| Rural India, ₹8–12k phone, 2 GB RAM, 720p screen, bright sunlight | No heavy graphics, no bundled fonts, colours that survive glare |
| Reads Hindi, Tamil or another Indian language first; English second | Native-script text that doesn't clip, plain everyday words, no jargon |
| Global user who expects a polished, modern app | Calm, restrained, well-spaced — Apple/Google-level quiet, not "sarkari form" |

**One sentence brief:** *A calm, trustworthy doorkeeper. Invisible when all is well; clear and kind when it isn't.*

### Design principles

1. **Invisible when safe.** The best LinkGuard screen is the one never shown. Safe links forward with no UI, no animation, no toast.
2. **One screen, one message, one main action.** Never two warnings, never two primary buttons.
3. **Say it in words, show it in shape, then colour it.** Meaning must survive colour-blindness, glare, a cracked screen and a black-and-white photo of the phone.
4. **Plain language over precision.** "This website may steal your money" beats "Possible phishing domain".
5. **Respect, not fear.** Warn like a helpful grandchild, not a police siren. No shouting, no countdowns, no blame.
6. **The user decides.** "Open anyway" is always visible and always works.
7. **Light by default.** Every pixel ships to a 2 GB phone. Prefer vectors, system fonts and zero libraries.

---

## 1. Brand architecture

| Level | Name | Role | Where it appears |
|---|---|---|---|
| Parent (company) | **Dwarpal Labs** | Maker, trust anchor, owns the server and the reputation feeds | About screen, Play "Developer" field, website footer, legal pages, `api.dwarpallabs.com` |
| Product | **LinkGuard** | What users see, install and remember | Launcher, every in-app screen, Play listing title, marketing |

- Model: **endorsed brand** — "LinkGuard · by Dwarpal Labs". LinkGuard leads; Dwarpal Labs signs.
- Inside the app, the parent name appears **only** on About / Privacy. Never on the warning screen (it adds noise at the worst moment).
- Future Dwarpal products reuse this system unchanged except for their product mark and name. The colour palette, type and voice belong to Dwarpal Labs, not to LinkGuard alone.

### Name meaning (use in storytelling, not UI)
*Dvārapāla* (द्वारपाल) — the guardian who stands at the doorway of a temple. LinkGuard is that doorkeeper for every link: it lets friends in without fuss and stops strangers at the door.

---

## 2. Logo

### 2.1 LinkGuard product mark (exists — keep it)
`app/src/main/res/drawable/ic_launcher_foreground.xml`: **a white shield holding a chain link**, on a solid Indigo background.

- Why it works: the shield = protection and the chain link = "link" are understood worldwide without words or literacy.
- Construction: shield is a single closed path; link is two rounded rectangles, 4 dp stroke, rotated −45°.
- Colours: white shield, Indigo `#1E3A8A` link and background. Nothing else.

### 2.2 Dwarpal Labs parent mark (proposal)
A simple **doorway arch** — a rounded-top rectangle outline (like a temple doorway / torana, but geometric, no ornament), stroke weight equal to the LinkGuard link stroke, Indigo on white. Wordmark "Dwarpal Labs" set in the brand typeface (see §4), semibold, sentence case.

- The arch frames the LinkGuard shield nicely for lock-ups: shield sitting inside the doorway = "the doorkeeper's product".
- No religious iconography, no deity figures, no saffron, no flags — keeps it neutral for every Indian community and abroad.

### 2.3 Wordmark
- **"LinkGuard"** — one word, capital L and G, never "Link Guard", "LINKGUARD" or "linkguard" (except in code / package ids).
- The wordmark stays in Latin script in all languages (it's a name). The **launcher label** may be transliterated per locale if user testing shows people can't find the app (e.g. `values-hi` → लिंकगार्ड, `values-ta` → லிங்க்கார்டு). Today `app_name` is `translatable="false"`; changing that is a product decision — ask first.

### 2.4 Usage rules
| Do | Don't |
|---|---|
| Use the solid-colour mark on white or Indigo | Put the mark on photos, gradients or patterns |
| Keep clear space = ½ the shield width on all sides | Add shadows, glows, 3D, outlines, gradients |
| Minimum size 24 dp (in-app), 16 px (favicon uses shield only) | Rotate, stretch or recolour the link |
| Use the monochrome version (`ic_launcher_monochrome.xml`) for themed icons, notifications, single-colour print | Pair the mark with a tagline inside the icon |
| Lock-up: mark left, wordmark right (mirrors to mark right in RTL) | Use the red/amber warning colours in the logo — the logo must never look like a warning |

---

## 3. Colour

**Rule: one brand colour, one ink, three status colours. That's the whole palette.** Status colours are used *only* to communicate link safety — never decoration.

### 3.1 Light theme (default)

| Token | Hex | Use | Contrast |
|---|---|---|---|
| `brand` | `#1E3A8A` Indigo | Primary buttons, links, active switches, logo | 10.4 : 1 vs white |
| `onBrand` | `#FFFFFF` | Text/icons on brand | — |
| `brandTint` | `#EEF2FF` | Selected rows, info banners | `brand` on it 9.3 : 1 |
| `ink` | `#111827` | All primary text | 17.7 : 1 |
| `inkMuted` | `#4B5563` | Secondary text, descriptions | 7.6 : 1 |
| `outline` | `#6B7280` | Input borders, dividers that matter, unselected switch | 4.8 : 1 |
| `outlineSoft` | `#E5E7EB` | Decorative dividers only | decorative |
| `background` | `#FFFFFF` | Screen background | — |
| `surface` | `#F3F4F6` | Cards, list groups | — |
| `safe` | `#15803D` Green | "LinkGuard is on", "Link is safe" (settings/tests only) | 5.0 : 1 |
| `safeTint` / `onSafeTint` | `#DCFCE7` / `#166534` | Safe badge background / text | 6.5 : 1 |
| `caution` | `#B45309` Amber | **Soft warning** (score 0.3–0.7) | 5.0 : 1 |
| `cautionTint` / `onCautionTint` | `#FEF3C7` / `#92400E` | Soft warning badge / text | 6.4 : 1 |
| `danger` | `#B91C1C` Red | **Strong warning** (score > 0.7) | 6.5 : 1 |
| `dangerTint` / `onDangerTint` | `#FEE2E2` / `#991B1B` | Strong warning badge / text | 6.8 : 1 |

### 3.2 Dark theme

| Token | Hex | Contrast vs `background` |
|---|---|---|
| `background` | `#0B1220` | — |
| `surface` | `#141C2F` | — |
| `ink` | `#F3F4F6` | 17.0 : 1 |
| `inkMuted` | `#9CA3AF` | 7.4 : 1 |
| `brand` | `#A5B4FC` (text/icons) — buttons use `#A5B4FC` fill with `#0B1220` text | 9.4 : 1 |
| `safe` | `#4ADE80` | 10.7 : 1 |
| `caution` | `#FBBF24` | 11.2 : 1 |
| `danger` | `#F87171` | 6.8 : 1 |
| Tints | status colour at 16 % alpha over `surface` | — |

All pairs above were checked against WCAG 2.2: every text pair is ≥ 4.5 : 1 (AA), body text pairs are ≥ 7 : 1 (AAA).

### 3.3 Colour rules
- **Turn off Material You dynamic colour** (`dynamicColor = false`). Wallpaper-derived colours can make a warning look like decoration or drop contrast. Safety UI must look identical on every phone.
- **Never rely on colour alone.** Every status = colour + icon shape + word (see §6.2). Red-green colour blindness affects ~8 % of men.
- **Red means stop only.** In India red is also festive/bridal, so the danger state always carries the stop icon *and* the word.
- **No saffron, no tricolour combinations, no party colours** anywhere in brand or marketing. Amber appears only as the caution status.
- **No gradients, no glassmorphism, no shadows deeper than 1 elevation level.** They wash out in sunlight and cost GPU on cheap phones.
- Body text is always `ink` or `inkMuted` — never brand or status colours (they're for short labels and icons).

---

## 4. Typography

### 4.1 Typeface
- **In the app: system default (`FontFamily.Default`).** On Android this is Roboto for Latin and **Noto Sans** for every Indian script (Devanagari, Tamil, Bengali, Telugu, Kannada, Malayalam, Gujarati, Gurmukhi, Odia, Ol Chiki, Meetei Mayek…), Arabic/Urdu and almost every other script. Zero APK size, already hinted for the device, already familiar to the user. **Do not bundle fonts.**
- **Website, Play graphics, print: Noto Sans** (and the matching Noto Sans <Script> family) — SIL OFL, free, one family that looks consistent across all 22 scheduled Indian languages and global ones, and visually matches what Android shows in the app. For Urdu use Noto Nastaliq Urdu; for Arabic, Noto Sans Arabic.
- **Self-host web fonts** (`woff2`, subset per script, `font-display: swap`) from our own domain. **Do not load Google Fonts or any font CDN** — that sends every visitor's IP to a third party, which breaks the no-trackers rule. Load a script's font only on pages in that language.
- One family, two weights: **Regular 400** and **SemiBold 600**. Bold 700 only for the host name on the warning screen. No light/thin weights (they disappear on 720p screens), no italics (most Indic scripts have no true italic).

### 4.2 Type scale (larger than Material defaults on purpose)

One scale for app and web. App sizes are in `sp`; web uses the same numbers in `px` (expressed in `rem` with `html { font-size: 100% }`, so 1 rem = 16 px and browser zoom/user font settings are respected). Web adds one larger size for page headlines only.

| Token | App (sp) | Web (px → rem) | Line height | Weight | Use |
|---|---|---|---|---|---|
| `hero` | — | 40 → 2.5rem (32 → 2rem below 600 px) | 1.25 | 600 | Website page headline only, one per page |
| `display` | 28 | 28 → 1.75rem | 40 | 700 | Host on the warning screen; web section headlines |
| `title` | 24 | 24 → 1.5rem | 34 | 600 | Screen titles, warning headline |
| `heading` | 20 | 20 → 1.25rem | 30 | 600 | Section headings, card titles |
| `bodyLarge` | 18 | 18 → 1.125rem | 28 | 400 | **Default body text** in app and on web |
| `body` | 16 | 16 → 1rem | 26 | 400 | Secondary descriptions — the minimum for anything a user must read |
| `label` | 18 | 18 → 1.125rem | 26 | 600 | Buttons (app and web) |
| `caption` | 14 | 14 → 0.875rem | 22 | 400 | Version number, legal footers only. Never for anything that matters |

Nothing smaller than 14 sp / 14 px, ever, on any surface. Web body copy is 18 px, not the browser-default 16 px.

### 4.3 Multilingual text rules
- **Line height ≥ 1.45× size.** Indic scripts stack vowel signs (matras) above and below the letter; tight line height clips them.
- **Letter spacing = 0 for every style.** Positive tracking breaks Indic conjuncts and Arabic joining.
- **No ALL CAPS, no text transforms.** Indic scripts have no case, and all-caps Latin is harder for low-literacy readers. Use `textAllCaps=false` / no `.uppercase()`.
- **Plan for 40 % longer text.** Tamil and Malayalam run long. Nothing may truncate with an ellipsis; text wraps. Buttons grow to two lines rather than shrink text.
- **Never set fixed heights on text containers.** Use `heightIn(min = …)`.
- **Support font scale up to 200 %** (Android 14 non-linear scaling). Test every screen at the largest system font size.
- **Numbers:** use Western digits (0–9) in all locales, Indian grouping for rupees in Indian locales (`₹1,00,000`), via `NumberFormat`, never hand-formatted.
- **Mixed scripts:** Brand names users know in English (WhatsApp, UPI, OTP, Google Pay) stay in Latin inside Indic sentences.

---

## 5. Layout, spacing & touch

- **Grid:** 4 dp base. Spacing tokens: `xs 4`, `sm 8`, `md 12`, `lg 16`, `xl 24`, `xxl 32`, `xxxl 48`.
- **Screen gutter:** 24 dp left/right (16 dp on screens < 360 dp wide).
- **Touch targets: minimum 56 dp tall, full width for primary actions.** (Material's 48 dp is too small for shaky or arthritic hands.) At least 12 dp between two tappable things.
- **Corner radius:** `small 8` (chips, inputs), `medium 12` (buttons, cards), `large 16` (icon badges), `xl 24` (bottom sheets).
- **Elevation:** flat. Cards use `surface` colour, not shadows. One exception: the "Checking link…" card may use elevation 2.
- **Single column only.** No side-by-side buttons — stack them vertically, primary on top. (Side-by-side buttons break with long translations and confuse which is "yes".)
- **Thumb zone:** primary actions sit in the bottom half of the screen.
- **RTL ready:** always `start`/`end`, never `left`/`right`. Directional icons (back arrow) mirror; the shield, link and status icons do not.

---

## 6. Iconography

### 6.1 Style
- Rounded, **filled** style (outlined icons get lost at low resolution and low vision), 24 dp default, 2 dp stroke where strokes exist, `ink` or status colour.
- Ship icons as **individual vector drawables** in `app/src/main/res/drawable/`, copied from Material Symbols Rounded (Apache-2.0). Do not add `material-icons-extended` (multi-MB dependency).
- **Every icon has a visible text label next to it.** No icon-only buttons. Every decorative icon has `contentDescription = null`; every meaningful one has a translated description.
- Use universal metaphors only: shield, check, triangle, stop sign, door, gear, flag (report), arrow. No hand gestures (meanings vary by culture), no animals, no religious or political symbols, no cartoon mascots.

### 6.2 Status system — the heart of the product

| State | Score | Icon (shape) | Colour | Headline (en) | When shown |
|---|---|---|---|---|---|
| **Safe** | < 0.3 | ✓ in a circle | `safe` | *(nothing — forward silently)* | Only in settings/self-test, never on a real link |
| **Caution** | 0.3 – 0.7 | ! in a triangle | `caution` | "Be careful with this link" | Soft warning |
| **Danger** | > 0.7 | ✕ / hand-free stop octagon | `danger` | "This link may be a scam" | Strong warning |
| **Reported** | community ≤ 0.6 | flag | `caution` | "Reported by 47 people this week" | Soft, count not verdict |

Three distinct shapes (circle, triangle, octagon) so the states are told apart in greyscale. Icon badge = 56 dp rounded-square (`large` radius) filled with the status tint, icon 32 dp in the status colour.

---

## 7. Components

Build these once in `app/src/main/java/com/dwarpallabs/linkguard/ui/components/` and reuse them. No one-off styling in screens.

### 7.1 Buttons
| Variant | Look | Use |
|---|---|---|
| `PrimaryButton` | `brand` fill, `onBrand` text, 56 dp min height, full width, `medium` radius | The one main action per screen ("Go back", "Turn on LinkGuard") |
| `SecondaryButton` | 1.5 dp `outline` border, `ink` text, transparent fill, same size | The alternative ("Open anyway") |
| `TextButton` | `brand` text, no border, 48 dp min height | Tertiary ("Report this link", "Learn more") |

- Labels are **verbs, 1–3 words**, sentence case: "Go back", not "OK" or "Cancel". Never "Yes/No".
- Danger screens do **not** turn buttons red — the primary action (Go back) is the safe one, so it keeps brand colour.
- Pressed state: 12 % ink overlay. Disabled: 38 % opacity *plus* explanatory text (never a silently greyed button).

### 7.2 Warning screen (interstitial) — anatomy
The most important screen in the product. Top to bottom:

```
┌──────────────────────────────────┐
│  [status badge 56dp]             │
│                                  │
│  This link may be a scam         │  ← title, status-coloured text (onXTint)
│                                  │
│  paytm-kyc-update.in             │  ← display; registrable domain bold,
│                                  │     subdomains in inkMuted; always LTR-isolated
│  This website is pretending to   │  ← bodyLarge, ink, 1–2 short sentences
│  be Paytm. It is not Paytm's     │
│  real website.                   │
│                                  │
│  [optional detail card]          │  ← e.g. UPI: payee + ₹ amount, surface bg
│                                  │
│  ▓▓▓▓▓▓▓▓ Go back ▓▓▓▓▓▓▓▓▓▓▓▓▓  │  ← PrimaryButton
│  ┌──────── Open anyway ───────┐  │  ← SecondaryButton (never hidden, never delayed)
│  Report this link                │  ← TextButton
└──────────────────────────────────┘
```

Rules:
- Show the **host only**, never the full URL path/query (it's noise, and full URLs are where scammers hide tricks).
- Bold the whole registrable domain (`paytm-kyc-update.in`) and show anything to its left (`www.`, `login.`) in `inkMuted`. Scammers hide behind subdomains like `paytm.com.verify-now.in`; the bold part must be what the site really is.
- IDN / mixed-script hosts: show the `xn--` punycode form under the decoded one, labelled "Real address".
- Wrap the host in a Unicode LTR isolate (`BidiFormatter`) so it renders correctly inside Urdu/Arabic sentences.
- No countdown, no "Are you sure?" second step after "Open anyway", no shaking, no sound. One short haptic tick (`HapticFeedbackConstants.REJECT` on API 30+, fall back to none) on Danger only.
- Back gesture = "Go back".

### 7.3 Checking indicator
- Appears **only if a check takes longer than 150 ms**, so safe links on fast checks never flash anything.
- Small centred card, spinner + "Checking link…". No full-screen overlay, no logo splash.

### 7.4 Status card (Home)
- One big card answering one question: *"Is LinkGuard protecting me?"*
- On: `safeTint` background, ✓ badge, "LinkGuard is on" + one line ("Links from WhatsApp, SMS and Telegram are checked first").
- Off / not default: `cautionTint`, ! badge, "LinkGuard is off" + PrimaryButton "Turn on" leading straight to the system setting.

### 7.5 List row (Settings)
- Min 64 dp tall; title (`bodyLarge`) + optional description (`body`, `inkMuted`) + trailing control.
- Switches always paired with the row text; the whole row is tappable, not just the switch.

### 7.6 Banners & toasts
- Avoid toasts — they vanish before slow readers finish. Use inline banners (`brandTint` for info) that stay until dismissed.

---

## 8. Motion

- **Safe path: zero animation.** The intercept activity is invisible (`Theme.Linkguard.Intercept`); keep it that way.
- Elsewhere: fades and slides only, 150–200 ms, standard easing. No bouncing, parallax, confetti or looping animation.
- Respect "Remove animations" (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`) — Compose does this automatically if you use its animation APIs; don't hand-roll timers.

---

## 9. Content design — voice, tone & writing (app *and* web)

Every word on every surface follows this section: app strings, website pages, Play listing, README, release notes, emails, error pages, social posts.

### 9.1 Voice
Calm · Kind · Direct · Never technical.
Speak like a trusted grandchild explaining something to a grandparent: short, respectful, no talking down.

### 9.2 Writing rules
- **One idea per sentence, ≤ 15 words where possible.** Reading level: a 10-year-old.
- **Second person, active voice:** "This website may take your money", not "Funds may be compromised".
- **Banned words in UI:** URL, domain, phishing, malware, SSL, certificate, redirect, heuristic, score, verdict, threat actor. Say **link**, **website**, **scam**, **fake**, **steal**.
- **Say what's wrong and what to do.** Reason first, action on the button.
- **Never blame or scare.** No "You were about to be hacked!". No exclamation marks.
- **Never claim certainty we don't have.** Soft: "may", "looks like". Strong: "is very likely a scam". Never "This is 100 % safe".
- **Community reports are counts, not verdicts:** "Reported as a scam by 47 people this week."

### 9.3 Translation rules
- **Write each language natively; don't translate word-for-word.** Translators get the *intent* and the English as reference.
- **Everyday words, not formal/Sanskritised or literary vocabulary.** Hindi: "वापस जाएं" not "प्रत्यावर्तित करें"; Tamil: everyday written Tamil, not classical/literary Tamil. Common English loanwords people actually use (link, WhatsApp, UPI, OTP, app) may stay, written in the native script or Latin as people normally do.
- Every user-facing string exists in `values/`, `values-hi/`, `values-ta/` (and every future locale); `scripts/check_strings.py` enforces it.
- Use `plurals` resources for counts ("1 person" / "47 people"); Indic and Slavic languages have different plural rules.
- Never concatenate strings; use placeholders (`%1$s`) so word order can change.
- Add a translator comment to every string describing where it shows and its max comfortable length.

### 9.4 Glossary — one word per idea, everywhere

Pick the word from this table. Don't invent synonyms on the website, in the listing or in a new screen. The Hindi and Tamil terms are the ones already shipped in `values-hi` / `values-ta`. New languages add a column here first, chosen with a native reviewer.

| Idea | English | हिन्दी | தமிழ் | Never say |
|---|---|---|---|---|
| A link | link | लिंक | இணைப்பு | URL, hyperlink |
| A website | website | वेबसाइट | இணையதளம் | domain, host, site address |
| A scam | scam | धोखाधड़ी | மோசடி | phishing, fraud attempt, malicious |
| Fake | fake | नकली | போலி | spoofed, counterfeit |
| Report (verb / noun) | report | शिकायत | புகார் | flag, submit, ticket |
| Go back | Go back | वापस जाएँ | பின்செல் | Cancel, Abort, Return |
| Open anyway | Open anyway | फिर भी खोलें | இருந்தாலும் திற | Proceed, Continue, Ignore |
| Check (verb) | check | जाँच | சரிபார் | scan, analyse, inspect |
| Browser | browser | ब्राउज़र | உலாவி | user agent |
| Payment request | UPI payment request | UPI भुगतान अनुरोध | UPI கட்டணக் கோரிக்கை | UPI intent, collect request |

Product facts stay identical on every surface: LinkGuard **checks links before they open**; it **is not a browser**; it **never blocks**; safe links **open straight away**; it **never stores or sends the links you open**.

### 9.5 Message patterns

Use the pattern for each kind of message. The examples are the shipped English strings.

| Kind | Pattern | Example |
|---|---|---|
| **Warning title** | What it may be, from the user's side | "This link may be a scam" · "Be careful with this link" |
| **Warning reason** | What we noticed + why it matters, in 1–2 sentences | "This link asks you to send money by UPI. You never need to pay to get a refund, a prize or a KYC update." |
| **Community warning** | Count + time, never a verdict | "Reported as a scam by 47 people this week." |
| **Error** | What didn't work + what to do; never an error code alone | "Could not send the report. Check your internet connection and try again." |
| **Success** | Thank + what happens next | "Thank you. Your report was sent for review." |
| **Progress** | Verb + "…" | "Checking link…" · "Sending…" |
| **Setting** | Title = what it protects against; description = how, and what data leaves the phone | "Brand-new websites" — "Asks the official registry when the website was created. Only the website name is sent, never the full link." |
| **Permission / setup ask** | Why it helps the user first, then the action | "LinkGuard checks links before they open" → **Turn on** |
| **Web headline** | The benefit in ≤ 8 words, no clever wordplay (it doesn't translate) | "Check every link before it opens." |
| **Web CTA** | Same verb as the app | "Get LinkGuard on Google Play" |

### 9.6 Style details
- **Sentence case** for everything: titles, buttons, menu items, web headings. Only names get capitals (LinkGuard, Dwarpal Labs, WhatsApp, UPI).
- Use the ellipsis character `…` for progress, not three dots. Use curly apostrophes `’` in English strings.
- No exclamation marks, no emoji in UI or web body copy (emoji render differently on old phones and screen readers read them out loud).
- Rupees: `₹` + Western digits with locale grouping. Dates: written out in the user's language ("17 October 2026"), never `10/17/26`.
- Privacy statements are written in the same plain language as everything else, and say what *is* sent, not just what isn't.

### 9.7 Language roadmap & script checklist
Supported now: English, हिन्दी, தமிழ். Planned: all 22 scheduled languages (Assamese, Bengali, Bodo, Dogri, Gujarati, Hindi, Kannada, Kashmiri, Konkani, Maithili, Malayalam, Manipuri, Marathi, Nepali, Odia, Punjabi, Sanskrit, Santali, Sindhi, Tamil, Telugu, Urdu) plus global languages on demand.

Before shipping a new language:
- [ ] All strings translated natively and reviewed by a native speaker from the target region
- [ ] Warning screen checked at 100 % and 200 % font scale — no clipping, no ellipsis
- [ ] RTL languages (Urdu, Kashmiri, Sindhi, Arabic…): layout mirrored, host still LTR
- [ ] Script renders with system font on an Android 8 device (some scripts — Ol Chiki, Meetei Mayek — need newer Android; if unsupported, fall back to English, never show tofu □□□)
- [ ] Play listing (`docs/play/listing-<lang>.md`) added

---

## 10. Accessibility checklist (every PR touching app UI or the website)

- [ ] Text contrast ≥ 4.5 : 1 (body ≥ 7 : 1); tokens above already satisfy this — use them
- [ ] Works at 200 % font size and "Largest" display size (app) and 200 % browser zoom (web)
- [ ] All touch targets ≥ 56 dp (primary) / 48 dp (others)
- [ ] TalkBack reads the warning in order: status → host (spelled sensibly) → reason → buttons; all labels translated
- [ ] Meaning survives greyscale (shape + word, not just colour)
- [ ] No information only in an icon, a colour, a toast or a timeout
- [ ] Back gesture always leads somewhere safe

---

## 11. Implementation (Jetpack Compose)

Replace the template purple theme in `ui/theme/` with these tokens. Keep `MaterialTheme` for components, add status colours via a `CompositionLocal`.

```kotlin
// ui/theme/Color.kt
val Indigo = Color(0xFF1E3A8A)
val IndigoTint = Color(0xFFEEF2FF)
val Ink = Color(0xFF111827)
val InkMuted = Color(0xFF4B5563)
val Outline = Color(0xFF6B7280)
val OutlineSoft = Color(0xFFE5E7EB)
val Surface = Color(0xFFF3F4F6)

val Safe = Color(0xFF15803D);    val SafeTint = Color(0xFFDCFCE7);    val OnSafeTint = Color(0xFF166534)
val Caution = Color(0xFFB45309); val CautionTint = Color(0xFFFEF3C7); val OnCautionTint = Color(0xFF92400E)
val Danger = Color(0xFFB91C1C);  val DangerTint = Color(0xFFFEE2E2);  val OnDangerTint = Color(0xFF991B1B)

val NightBackground = Color(0xFF0B1220)
val NightSurface = Color(0xFF141C2F)
val NightInk = Color(0xFFF3F4F6)
val NightInkMuted = Color(0xFF9CA3AF)
val NightIndigo = Color(0xFFA5B4FC)
val NightSafe = Color(0xFF4ADE80)
val NightCaution = Color(0xFFFBBF24)
val NightDanger = Color(0xFFF87171)
```

```kotlin
// ui/theme/Theme.kt (sketch)
@Immutable
data class StatusColors(
    val safe: Color, val safeTint: Color, val onSafeTint: Color,
    val caution: Color, val cautionTint: Color, val onCautionTint: Color,
    val danger: Color, val dangerTint: Color, val onDangerTint: Color,
)
val LocalStatusColors = staticCompositionLocalOf<StatusColors> { error("No StatusColors") }

private val LightColors = lightColorScheme(
    primary = Indigo, onPrimary = Color.White, primaryContainer = IndigoTint, onPrimaryContainer = Indigo,
    background = Color.White, onBackground = Ink, surface = Color.White, onSurface = Ink,
    surfaceVariant = Surface, onSurfaceVariant = InkMuted, outline = Outline, outlineVariant = OutlineSoft,
    error = Danger, onError = Color.White, errorContainer = DangerTint, onErrorContainer = OnDangerTint,
)

@Composable
fun LinkguardTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    // No dynamicColor: safety colours must be identical on every phone.
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) NightStatus else DayStatus) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = Typography, content = content)
    }
}
```

```kotlin
// ui/theme/Type.kt — letterSpacing 0 everywhere, generous line height for Indic scripts
private fun style(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = FontFamily.Default, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = 0.sp)

val Typography = Typography(
    displaySmall = style(28, 40, FontWeight.Bold),     // host on warning
    headlineSmall = style(24, 34, FontWeight.SemiBold), // screen / warning title
    titleLarge = style(20, 30, FontWeight.SemiBold),
    bodyLarge = style(18, 28, FontWeight.Normal),       // default body
    bodyMedium = style(16, 26, FontWeight.Normal),
    labelLarge = style(18, 26, FontWeight.SemiBold),    // buttons
    bodySmall = style(14, 22, FontWeight.Normal),       // captions only
)
```

Also: move the hard-coded `SoftAccent` in `InterstitialScreen.kt` onto `LocalStatusColors.current.caution`, and replace the template entries in `res/values/colors.xml` with `brand` / `ink` / `background` only (the XML theme just needs the window background).

### Web / marketing tokens (CSS)
```css
:root {
  --brand:#1E3A8A; --brand-tint:#EEF2FF; --ink:#111827; --ink-muted:#4B5563;
  --outline:#6B7280; --bg:#FFFFFF; --surface:#F3F4F6;
  --safe:#15803D; --caution:#B45309; --danger:#B91C1C;
  --font:"Noto Sans","Noto Sans Devanagari","Noto Sans Tamil",system-ui,sans-serif; /* self-hosted */

  /* type: same numbers as the app (sp → px) */
  --text-hero:2.5rem; --text-display:1.75rem; --text-title:1.5rem; --text-heading:1.25rem;
  --text-body-lg:1.125rem; --text-body:1rem; --text-label:1.125rem; --text-caption:0.875rem;
  --leading:1.55; --leading-tight:1.3;

  /* spacing, radius, touch */
  --space-xs:4px; --space-sm:8px; --space-md:12px; --space-lg:16px; --space-xl:24px; --space-xxl:32px; --space-xxxl:48px;
  --radius-sm:8px; --radius-md:12px; --radius-lg:16px; --radius-xl:24px;
  --touch-min:56px; --measure:720px;
}
html { font-size:100%; }
body { font-family:var(--font); font-size:var(--text-body-lg); line-height:var(--leading); letter-spacing:0; color:var(--ink); background:var(--bg); }
@media (prefers-color-scheme: dark) {
  :root { --brand:#A5B4FC; --ink:#F3F4F6; --ink-muted:#9CA3AF; --bg:#0B1220; --surface:#141C2F;
          --safe:#4ADE80; --caution:#FBBF24; --danger:#F87171; }
}
```

---

## 12. Website

The website is the app's front door and follows every rule above: the same tokens (§3), Noto Sans with the same scale (§4), the same spacing, radius and 56 px touch targets (§5), the same icons (§6) and the same words (§9). Someone moving from the website to the app should feel no change.

### 12.1 Layout
- **Mobile first.** Most visitors arrive from a WhatsApp link on a phone. Design at 360 px wide, then enlarge.
- Single reading column, **max 720 px** for text, centred. Wider sections (hero, screenshots) max 1120 px.
- Side gutter 16 px on phones, 24 px from 600 px up. No horizontal scrolling at any width.
- Section spacing 48 px (phone) / 96 px (desktop). Generous white space is the main decoration.

### 12.2 Page anatomy (product page)
1. **Header:** LinkGuard mark + wordmark on the left (right in RTL), language picker on the right. No mega-menu; at most 3 links (How it works, Privacy, Help).
2. **Hero:** `hero` headline, one `bodyLarge` sentence, one primary button ("Get LinkGuard on Google Play"), one phone screenshot showing the warning screen.
3. **How it works:** 3 steps, each = icon + 3–6 word title + one sentence. ("You tap a link" → "LinkGuard checks it" → "Safe links open straight away").
4. **What it warns about:** the status cards from §6.2, using the real app wording.
5. **Privacy in plain words:** what is sent and what never leaves the phone, in the same words as the app's setting descriptions.
6. **Footer:** "LinkGuard · by Dwarpal Labs", Privacy, Licence (Apache-2.0), source code link, language picker. Everything in `caption` is still ≥ 14 px.

### 12.3 Web components
- Buttons are the same three variants as §7.1 (`PrimaryButton` / `SecondaryButton` / `TextButton`) with the same 56 px height, 12 px radius and 18 px semibold label. Links in body text are `brand`, underlined (not colour-only).
- Visible focus ring on every interactive element: 3 px `brand` outline, 2 px offset.
- Language picker lists each language **in its own script** ("English · हिन्दी · தமிழ்"), never flags (flags are countries, not languages). Each language gets its own URL (`/hi/`, `/ta/`) and `<html lang>`; RTL languages set `dir="rtl"`.

### 12.4 Performance & privacy (same rules as the app)
- **Page budget: ≤ 200 KB per page on first load** (HTML + CSS + fonts for that page's script + hero image). It must work on a slow 3G connection.
- Images: SVG for icons and illustrations, WebP/AVIF for screenshots with `width`/`height` set; lazy-load below the fold.
- **No analytics, no cookies, no third-party scripts, embeds, fonts or CDNs.** That also means **no cookie banner**, because there is nothing to consent to. Server logs, if any, drop IP addresses.
- Works with JavaScript off; JS only enhances.
- Respects `prefers-color-scheme` (tokens in §11) and `prefers-reduced-motion`.

---

## 13. Marketing & store graphics

- Layout: lots of white (or Indigo) space, one phone mock-up, one headline of ≤ 6 words in the listing language.
- Show real app screens (the warning screen is the hero), never stock photos of hackers, hoodies, padlocks or skulls.
- People imagery (if any): real Indian everyday users — elders, shopkeepers, students — dignified, not pitiable; illustrated in flat 2-colour style using `brand` + `ink` on white.
- Every localized listing gets graphics with text in that language (`docs/play/make_assets.py`).

---

## 14. Quick do / don't (app and web)

| Do | Don't |
|---|---|
| Show nothing for safe links | Show "✓ Safe!" toasts on every link |
| One headline, one sentence, one primary button | Paragraphs, bullet lists or technical detail on the warning |
| Shape + word + colour for every status | Red/green alone |
| System font, vectors, flat surfaces | Bundled fonts, PNG illustrations, Lottie, gradients |
| Stacked full-width buttons | Side-by-side "Yes / No" |
| Wrap long translations | Ellipsis, shrink-to-fit, fixed heights |
| "Go back" / "Open anyway" | "OK" / "Cancel" / "Proceed at your own risk" |
| Calm, kind, plain words | Fear, blame, jargon, exclamation marks |
