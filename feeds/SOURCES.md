# Data sources

Every list the feeds job uses, and each one's terms. The terms were checked on 19 October 2026. Re-check a source before changing how it is used.

"Private" means the entries go only into the LinkGuard server database. Phones query them by 4-byte hash prefix. "Published" means the entries can also appear in the public daily dataset at [dwarpallabs/linkguard-data](https://github.com/dwarpallabs/linkguard-data).

## In use

| Source | What we take | Terms | Use |
| --- | --- | --- | --- |
| [PhishDestroy destroylist](https://github.com/phishdestroy/destroylist) | Root domains of phishing and scam sites that are currently online | MIT License | Blocklist, **published** (MIT notice kept in the data repo) |
| [URLhaus](https://urlhaus.abuse.ch) (abuse.ch) | Hosts of URLs serving malware right now | Free for not-for-profit use with an Auth-Key; redistribution not clearly allowed | Blocklist, private |
| [PhishTank](https://phishtank.com) (Cisco Talos) | Hosts of verified phishing URLs that are online now (`online-valid.csv.gz`) | FAQ: "PhishTank is free to everyone, both the website and the data". Commercial and non-commercial use of the data is OK. No explicit licence for redistribution. Key registration is closed, and without a key a few downloads a day are allowed, so we fetch once a day. | Blocklist, private |
| [Tranco](https://tranco-list.eu) (Le Pochat et al., NDSS 2019) | Research ranking of popular sites | Free for research and other use, cite the paper | Top 10,000 go on the allowlist. No site in the top 100,000 can be blocked by a feed. |

Hosts on shared platforms are never blocked, because blocking them would warn about everyone's links there. That covers any Tranco-ranked site, file hosts, link shorteners, brand domains and `never_allow.txt`, so a phishing page on `sites.google.com` cannot block Google Sites. Community reports from LinkGuard users are a separate signal and are covered in the main README.

## Checked and not used (yet)

| Source | Why not | What would change it |
| --- | --- | --- |
| RBI list of bank websites (`rbi.org.in/Scripts/banklinks.aspx`) | "All Rights Reserved", and caching or framing needs permission. It lists only the new `.bank.in` domains, and the app already allows `*.bank.in` by rule. | Written permission from RBI |
| RBI Alert List of unauthorised forex platforms | Same RBI terms. Some entries are licensed abroad, so a strong scam warning would be wrong. | Permission, plus a soft "not authorised in India" message |
| NPCI UPI apps and PSP handles | The page blocks automated access, and its terms are unknown | Hand-curated list for the UPI milestone (B2), citing NPCI |
| SEBI registered intermediaries | No website field (names, emails, phone numbers only) | None |
| SEBI, NSE and BSE investor caution notices | PDFs, and mostly Telegram channels and phone numbers rather than websites | Possible later as a low-yield PDF scraper |
| State government portals (igod.gov.in) | Reproduction needs permission by email; about 620 portals sit outside `.gov.in` and `.nic.in` | Written permission from NIC |
| CERT-In advisories | Indicators are rare, written in a non-clickable form, and about enterprise threats; reproduction needs permission | A machine-readable consumer feed, or permission |
| Sanchar Saathi, Chakshu and I4C | No public bulk data | None |
| OpenPhish community feed | Personal or research use only, with no distribution or derivative works | Written permission |

Each entry keeps its source in the database's `feed` column. A warning that names who listed a site ("Listed by CERT-In") waits until an Indian authority's data is usable.
