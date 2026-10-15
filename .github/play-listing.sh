#!/usr/bin/env bash
# Push the store listing in play/ to Google Play.
#
# Everything here goes through the Play Developer API with a service-account key: the icon, the
# feature graphic, the phone screenshots and the two descriptions. The account needs the app-level
# "Manage store presence" permission -- "Release to testing tracks" is not enough, and without it
# every call below comes back 403.
#
# It is deliberately not part of the release job. A release uploads *builds*; this edits the *listing*,
# and a listing edit is a deliberate act that should never ride along with a push. Run it from the
# "Play listing" workflow.
#
# Why bash and curl rather than fastlane: the repo already holds everything this needs (openssl and
# jq are on the runner), fastlane would add a Ruby toolchain and impose its own directory layout, and
# play/ is the source of truth here. The JWT below is the whole of the auth.
set -euo pipefail

SA="${PLAY_SERVICE_ACCOUNT_JSON_FILE:?path to the service-account json}"
ASSETS="${PLAY_ASSETS_DIR:-play}"
PACKAGE="${PLAY_PACKAGE:-com.bloo.bluelink}"

for f in "$SA"; do [ -f "$f" ] || { echo "::error::service account json not found at $f"; exit 1; }; done
for t in icon-512.png feature-graphic-1024x500.png screenshots; do
  [ -e "$ASSETS/$t" ] || { echo "::error::$ASSETS/$t is missing"; exit 1; }
done

work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
client_email="$(jq -r .client_email "$SA")"
jq -r .private_key "$SA" > "$work/key.pem"
echo "service account: $client_email"
echo "assets:          $(cd "$ASSETS" && pwd)"
echo "package:         $PACKAGE"
echo

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

# --- 1. Mint an access token from the service account (JWT bearer flow) --------------------------
header="$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)"
now="$(date +%s)"
claims="$(printf '{"iss":"%s","scope":"https://www.googleapis.com/auth/androidpublisher","aud":"https://oauth2.googleapis.com/token","iat":%s,"exp":%s}' \
  "$client_email" "$now" "$((now + 3600))" | b64url)"
signature="$(printf '%s.%s' "$header" "$claims" | openssl dgst -sha256 -sign "$work/key.pem" -binary | b64url)"
token="$(curl -fsS -X POST https://oauth2.googleapis.com/token \
  -d grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer \
  -d assertion="$header.$claims.$signature" | jq -r .access_token)"
[ -n "$token" ] && [ "$token" != "null" ] || { echo "::error::could not get an access token"; exit 1; }
echo "got an access token"

api="https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$PACKAGE"
upload="https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/$PACKAGE"
auth=(-H "Authorization: Bearer $token")

# `call` prints the response body and fails the script on a non-2xx, so the log always says why.
call() {
  local method="$1" url="$2"; shift 2
  local body status
  body="$(curl -sS -X "$method" "${auth[@]}" -H "Content-Type: application/json" -w $'\n%{http_code}' "$@" "$url")"
  status="${body##*$'\n'}"; body="${body%$'\n'*}"
  if [ "${status:0:1}" != "2" ]; then
    echo "::error::HTTP $status from $method $url"; echo "$body" | head -20; exit 1
  fi
  printf '%s' "$body"
}

echo; echo "--- opening an edit"
edit_id="$(call POST "$api/edits" | jq -r .id)"
echo "edit $edit_id"

echo; echo "--- what the listing looks like now"
call GET "$api/edits/$edit_id/listings" | jq -r '.listings[]? | "  \(.language): \"\(.title)\" | \(.shortDescription)"' || true
language="$(call GET "$api/edits/$edit_id/listings" | jq -r '.listings[0].language // "en-US"')"
echo "  editing language: $language"

# --- INSPECT mode: report what Play actually holds, then leave without changing anything -----------
# Written because the CI said "Finished uploading to the Play Store" and the console showed nothing new.
# When the two disagree, believe neither: list the tracks and the releases on them and read those.
if [ "${PLAY_INSPECT:-false}" = "true" ]; then
  echo; echo "--- INSPECT: what Play currently holds"
  call GET "$api/edits/$edit_id/tracks" | jq -r '
    .tracks[]? | "  track \(.track)", ( (.releases // [])[] |
      "      status=\(.status // "?")  versionCodes=\((.versionCodes // [])|join(","))  name=\(.name // "")  rollout=\(.userFraction // "n/a")" )'
  echo
  echo "  (an empty release list above means the track exists but holds no release)"
  call DELETE "$api/edits/$edit_id" >/dev/null || true
  echo "  edit abandoned; nothing was changed"
  exit 0
fi

echo; echo "--- replacing images (the API appends, so each type is emptied first)"
image_type_for() {
  case "$1" in
    icon) echo "icon" ;;
    feature) echo "featureGraphic" ;;
    shot) echo "phoneScreenshots" ;;
  esac
}
clear_type() {
  local type="$1"
  local ids
  ids="$(call GET "$api/edits/$edit_id/listings/$language/$type" | jq -r '.images[]?.id' 2>/dev/null || true)"
  for id in $ids; do
    [ -n "$id" ] || continue
    echo "  removing $type/$id"
    call DELETE "$api/edits/$edit_id/listings/$language/$type/$(printf '%s' "$id" | jq -sRr @uri)" >/dev/null
  done
}
put_image() {
  local type="$1" file="$2"
  local status
  status="$(curl -sS -X POST "${auth[@]}" -H "Content-Type: image/png" \
    --data-binary "@$file" -o "$work/resp.json" -w '%{http_code}' \
    "$upload/edits/$edit_id/listings/$language/$type?uploadType=media")"
  if [ "${status:0:1}" != "2" ]; then
    echo "::error::HTTP $status uploading $file as $type"; head -20 "$work/resp.json"; exit 1
  fi
  echo "  uploaded $type  <- $(basename "$file")"
}
for pair in "icon:$ASSETS/icon-512.png" "feature:$ASSETS/feature-graphic-1024x500.png"; do
  type="$(image_type_for "${pair%%:*}")"; file="${pair#*:}"
  clear_type "$type"; put_image "$type" "$file"
done
clear_type "$(image_type_for shot)"
for shot in "$ASSETS"/screenshots/*.png; do put_image "$(image_type_for shot)" "$shot"; done

echo; echo "--- the description text"
title="$(cat "$ASSETS/title.txt" 2>/dev/null || echo Bloo)"
jq -n --arg language "$language" --arg title "$title" \
      --rawfile shortDescription "$ASSETS/short-description.txt" \
      --rawfile fullDescription "$ASSETS/full-description.txt" \
      '{language:$language, title:$title, shortDescription:($shortDescription|rtrimstr("\n")), fullDescription:($fullDescription|rtrimstr("\n"))}' > "$work/listing.json"
call PUT "$api/edits/$edit_id/listings/$language" -d @"$work/listing.json" >/dev/null
echo "  title:              $title"
echo "  short description:  $(tr -d '\n' < "$ASSETS/short-description.txt" | head -c 60)…"

echo; echo "--- dry run?"
if [ "${PLAY_DRY_RUN:-false}" = "true" ]; then
  # Everything above really happened, but only inside this edit -- and an edit is invisible until it
  # is committed. So a dry run is a full rehearsal: it proves the key, the permission, the upload
  # endpoints and the JSON shape, and touches nothing a user can see.
  echo "    yes: abandoning the edit, nothing committed"
  call DELETE "$api/edits/$edit_id" >/dev/null || true
  exit 0
fi

echo; echo "--- committing"
call POST "$api/edits/$edit_id:commit" >/dev/null
echo "committed. The listing is live (or queued for review, if Play decides it needs one)."
