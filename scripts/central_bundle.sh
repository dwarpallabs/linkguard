#!/usr/bin/env bash
# Uploads the libraries staged by
#   ./gradlew :core:publishAllPublicationsToStagingRepository :data:publishAllPublicationsToStagingRepository
# (build/central-staging) to Maven Central as one deployment, then waits for Central to validate it.
# The deployment is USER_MANAGED: a maintainer presses Publish on central.sonatype.com.
#
# Needs CENTRAL_TOKEN_USER and CENTRAL_TOKEN_PASS (a Central Portal user token) and signed artifacts
# (LIB_SIGNING_KEY set when staging). Without the token, or with --dry-run, it only builds the bundle.
# Prints counts and states only, never the token.
set -euo pipefail
cd "$(dirname "$0")/.."

STAGING=build/central-staging
BUNDLE=build/central-bundle.zip
VERSION=$(sed -n 's/^libVersion=//p' gradle.properties)
[ -d "$STAGING" ] || { echo "No $STAGING: run the publish tasks first." >&2; exit 1; }

rm -f "$BUNDLE"
# Central builds maven-metadata.xml itself; it must not be in the bundle.
(cd "$STAGING" && zip -qrX "../central-bundle.zip" . -x '*maven-metadata*')
# List once: piping unzip into an early-exiting grep -q would fail under pipefail (SIGPIPE).
listing=$(unzip -Z1 "$BUNDLE")
files=$(grep -cv '/$' <<<"$listing")
unsigned=0
while read -r f; do
    grep -qxF "$f.asc" <<<"$listing" || unsigned=$((unsigned + 1))
done < <(grep -E '\.(jar|pom|module)$' <<<"$listing")
echo "Bundle for $VERSION: $files files, $unsigned unsigned."

if [ "${1:-}" = "--dry-run" ] || [ -z "${CENTRAL_TOKEN_USER:-}" ]; then
    echo "Dry run: not uploaded."
    exit 0
fi
[ "$unsigned" = 0 ] || { echo "Central rejects unsigned artifacts: set LIB_SIGNING_KEY and stage again." >&2; exit 1; }

auth="Authorization: Bearer $(printf '%s:%s' "$CENTRAL_TOKEN_USER" "$CENTRAL_TOKEN_PASS" | base64 | tr -d '\n')"
api=https://central.sonatype.com/api/v1/publisher
id=$(curl -sS --fail -H "$auth" -F "bundle=@$BUNDLE" "$api/upload?publishingType=USER_MANAGED&name=linkguard-$VERSION")
echo "Deployment $id uploaded."

for _ in $(seq 1 60); do
    state=$(curl -sS --fail -X POST -H "$auth" "$api/status?id=$id" | sed -n 's/.*"deploymentState" *: *"\([A-Z]*\)".*/\1/p')
    echo "State: $state"
    case "$state" in
        VALIDATED) echo "Validated. Press Publish on https://central.sonatype.com/publishing/deployments"; exit 0 ;;
        PUBLISHING|PUBLISHED) exit 0 ;;
        FAILED) curl -sS -X POST -H "$auth" "$api/status?id=$id" | sed -n 's/.*"errors" *: *\(.*\)}/\1/p' >&2; exit 1 ;;
    esac
    sleep 10
done
echo "Still validating after 10 minutes; check the Portal." >&2
exit 1
