# data

Bundled lists that ship inside the app, loaded from the classpath by `core` and `app`:
Indian brand allowlist, lookalike patterns, shortener domains and the day-zero feed snapshot.

Files go in `src/main/resources/linkguard/`. The snapshot is committed, never fetched at build time,
so the `foss` build stays reproducible and offline.

Empty until Milestone 2.
