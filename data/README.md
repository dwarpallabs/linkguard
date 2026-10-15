# data

Bundled lists that ship inside the app, loaded from the classpath by `core` (`EngineData.loadBundled()`).
Everything is committed, never fetched at build time, so the `foss` build stays reproducible and offline.

Files live in `src/main/resources/linkguard/`:

| File | What | Source |
|---|---|---|
| `brands.tsv` | Brands scammers imitate: keywords and official domains | Curated in-repo. **Every official domain is also allowlisted, so only add domains you have verified.** |
| `allowlist.txt` | Hard allowlist (registrable domains, or `.suffix` rules like `.gov.in`) | Curated in-repo; feeds add Tranco top 10k later |
| `blocklist_sha256.txt` | SHA-256 of blocked hosts / registrable domains | Empty day-zero snapshot; feeds fill it |
| `shorteners.txt` | Hosts whose redirects Unwrap follows (the only hosts it contacts) | Curated in-repo |
| `file_hosts.txt` | File-sharing hosts for the APK check | Curated in-repo |
| `free_tlds.txt` | Throwaway TLDs for the WeirdHost check | Curated in-repo |
| `public_suffix_list.dat` | Mozilla Public Suffix List, unmodified | https://publicsuffix.org/list/ (MPL-2.0) |
| `rdap_bootstrap.json` | TLD → RDAP server map | https://data.iana.org/rdap/dns.json |
