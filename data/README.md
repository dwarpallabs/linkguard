# data

Bundled public data that ships inside the app, loaded from the classpath by `core` (`EngineData.loadBundled()`).

**The allowlist and blocklist are not here.** They live in the LinkGuard server database: phones download the allowlist as signed hashes and check the blocklist by hash prefix. The `.gov.in` / `.nic.in` / `.bank.in` / `.mil.in` rules are built into `core`.
Everything is committed, never fetched at build time, so the `foss` build stays reproducible and offline.

Files live in `src/main/resources/linkguard/`:

| File | What | Source |
|---|---|---|
| `brands.tsv` | Brands scammers imitate: keywords and official domains | Curated in-repo. **Every official domain is also allowlisted, so only add domains you have verified.** |
| `shorteners.txt` | Hosts whose redirects Unwrap follows (the only hosts it contacts) | Curated in-repo |
| `file_hosts.txt` | File-sharing hosts for the APK check | Curated in-repo |
| `free_tlds.txt` | Throwaway TLDs for the WeirdHost check | Curated in-repo |
| `public_suffix_list.dat` | Mozilla Public Suffix List, unmodified | https://publicsuffix.org/list/ (MPL-2.0) |
| `rdap_bootstrap.json` | TLD → RDAP server map | https://data.iana.org/rdap/dns.json |
